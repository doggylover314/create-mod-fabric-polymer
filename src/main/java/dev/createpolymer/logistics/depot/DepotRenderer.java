package dev.createpolymer.logistics.depot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;

import dev.createpolymer.kinetics.belt.BeltHelper;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Shows the items on a depot with display entities, laid out like Create's DepotRenderer does for the
 * client. Create draws from a PoseStack every frame; this builds the same transforms once per change and
 * sends them to the viewers, interpolated over one tick.
 */
public class DepotRenderer {
	private static final Map<Item, Boolean> BLOCK_ITEMS = new ConcurrentHashMap<>();

	private final ElementHolder holder;
	private final Slot[] outputs = new Slot[8];
	private final List<Slot> main = new ArrayList<>();
	private int lastDataVersion = -1;
	private final Matrix4f transform = new Matrix4f();
	private boolean transformChanged;

	public DepotRenderer(ElementHolder holder) {
		this.holder = holder;
		for (int i = 0; i < outputs.length; i++)
			outputs[i] = new Slot();
	}

	/**
	 * Draws everything relative to this transform (block-centred), like Create's renderItemsOf does inside the
	 * pose of whatever carries the items. The ejector's moving plate uses it.
	 */
	public void setTransform(Matrix4f matrix) {
		if (transform.equals(matrix))
			return;
		transform.set(matrix);
		transformChanged = true;
	}

	/** Call once per tick. */
	public void render(DepotBehaviour depotBehaviour, int dataVersion) {
		TransportedItemStack transported = depotBehaviour.heldItem;
		boolean force = transformChanged;
		transformChanged = false;

		// Render main items
		int index = 0;
		if (transported != null)
			showMain(index++, transported, force);
		for (TransportedItemStack tis : depotBehaviour.incoming)
			showMain(index++, tis, force);
		for (; index < main.size(); index++)
			main.get(index).hide();

		// Render output items
		if (dataVersion == lastDataVersion && !force)
			return;
		lastDataVersion = dataVersion;
		for (int i = 0; i < outputs.length; i++) {
			ItemStack stack = depotBehaviour.processingOutputBuffer.getStackInSlot(i);
			if (stack.isEmpty()) {
				outputs[i].hide();
				continue;
			}
			Matrix4f m = base();

			boolean renderUpright = BeltHelper.isItemUpright(stack);
			m.rotateY((float) Math.toRadians(360 / 8f * i));
			m.translate(.35f, 0, 0);
			if (renderUpright)
				m.rotateY((float) Math.toRadians(-(360 / 8f * i)));
			Random r = new Random(i + 1);
			int angle = (int) (360 * r.nextFloat());
			outputs[i].show(stack, renderItem(m, stack, renderUpright ? angle + 90 : angle, r, false));
		}
	}

	private void showMain(int index, TransportedItemStack tis, boolean force) {
		while (main.size() <= index)
			main.add(new Slot());
		Slot slot = main.get(index);
		if (!slot.changed(tis) && !force)
			return;

		Matrix4f m = base();
		float offset = tis.beltPosition;
		float sideOffset = tis.sideOffset;

		if (tis.insertedFrom.getAxis()
			.isHorizontal()) {
			Vec3 offsetVec = Vec3.atLowerCornerOf(tis.insertedFrom.getOpposite()
				.getUnitVec3i())
				.scale(.5f - offset);
			m.translate((float) offsetVec.x, (float) offsetVec.y, (float) offsetVec.z);
			boolean alongX = tis.insertedFrom.getClockWise()
				.getAxis() == Direction.Axis.X;
			if (!alongX)
				sideOffset *= -1;
			m.translate(alongX ? sideOffset : 0, 0, alongX ? 0 : sideOffset);
		}

		slot.show(tis.stack, renderItem(m, tis.stack, tis.angle, new Random(0), false));
	}

	/** Create translates the PoseStack to (.5, 15/16, .5) of the block; display coordinates are block-centred. */
	private Matrix4f base() {
		return new Matrix4f(transform).translate(0, 15 / 16f - .5f, 0);
	}

	/**
	 * The transform of each layer a stack is drawn with (Create's DepotRenderer.renderItem). Stacks are drawn as
	 * several layers: one more for every factor of four in the count. Upright items cannot turn towards the viewer
	 * here, they keep their angle.
	 */
	public static List<Matrix4f> renderItem(Matrix4f base, ItemStack itemStack, int angle, Random r, boolean alwaysUpright) {
		int count = Mth.log2(itemStack.getCount()) / 2;
		boolean blockItem = isBlockItem(itemStack);
		boolean renderUpright = BeltHelper.isItemUpright(itemStack) || alwaysUpright && !blockItem;

		Matrix4f m = new Matrix4f(base);
		m.rotateY((float) Math.toRadians(angle));

		if (renderUpright)
			m.translate(0, 3 / 32f, -1 / 16f);

		List<Matrix4f> layers = new ArrayList<>(count + 1);
		for (int i = 0; i <= count; i++) {
			Matrix4f layer = new Matrix4f(m);
			if (blockItem && r != null)
				layer.translate(r.nextFloat() * .0625f * i, 0, r.nextFloat() * .0625f * i);

			if (blockItem && alwaysUpright) {
				layer.translate(0, 1 / 16f, 0);
				layer.scale(.755f);
			} else
				layer.scale(.5f);

			if (!blockItem && !renderUpright) {
				layer.translate(0, -3 / 16f, 0);
				layer.rotateX((float) Math.toRadians(90));
			}
			layers.add(layer);

			if (!renderUpright) {
				if (!blockItem)
					m.rotateY((float) Math.toRadians(10));
				m.translate(0, blockItem ? 1 / 64f : 1 / 16f, 0);
			} else
				m.translate(0, 0, -1 / 16f);
		}
		return layers;
	}

	/**
	 * Create asks the baked model whether it is a 3D block model. The server has no models: cubes, stairs and
	 * slabs count, everything else (torches, doors, plants, ...) has a flat item model.
	 */
	public static boolean isBlockItem(ItemStack stack) {
		return BLOCK_ITEMS.computeIfAbsent(stack.getItem(), item -> {
			if (!(item instanceof BlockItem blockItem))
				return false;
			Block block = blockItem.getBlock();
			if (block instanceof StairBlock || block instanceof SlabBlock)
				return true;
			try {
				return Block.isShapeFullBlock(block.defaultBlockState()
					.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
			} catch (RuntimeException e) {
				return false;
			}
		});
	}

	/** The display entities of one drawn stack, one per layer. */
	private class Slot {
		private final List<Part> layers = new ArrayList<>();
		private final List<Matrix4f> lastMatrices = new ArrayList<>();
		private ItemStack shown = ItemStack.EMPTY;
		private ItemStack lastStack = ItemStack.EMPTY;
		private float lastPosition, lastSide;
		private int lastAngle;
		private Direction lastFrom;
		private boolean visible;

		boolean changed(TransportedItemStack tis) {
			if (visible && ItemStack.matches(lastStack, tis.stack) && lastPosition == tis.beltPosition
				&& lastSide == tis.sideOffset && lastAngle == tis.angle && lastFrom == tis.insertedFrom)
				return false;
			lastStack = tis.stack.copy();
			lastPosition = tis.beltPosition;
			lastSide = tis.sideOffset;
			lastAngle = tis.angle;
			lastFrom = tis.insertedFrom;
			return true;
		}

		void show(ItemStack stack, List<Matrix4f> matrices) {
			ItemStack one = stack.copyWithCount(1);
			boolean reappearing = !visible;
			visible = true;
			for (int i = 0; i < matrices.size(); i++) {
				if (i == layers.size()) {
					Part part = new Part(ItemStack.EMPTY);
					part.element.setItemDisplayContext(ItemDisplayContext.FIXED);
					holder.addElement(part.element);
					layers.add(part);
					lastMatrices.add(null);
				}
				Part part = layers.get(i);
				boolean fresh = reappearing || lastMatrices.get(i) == null;
				if (fresh || !ItemStack.isSameItemSameComponents(shown, one))
					part.setItem(one);
				if (!matrices.get(i).equals(lastMatrices.get(i))) {
					part.setMatrix(matrices.get(i), fresh ? 0 : 1);
					lastMatrices.set(i, matrices.get(i));
				}
			}
			shown = one;
			for (int i = matrices.size(); i < layers.size(); i++)
				hideLayer(i);
		}

		void hide() {
			if (!visible)
				return;
			visible = false;
			lastStack = ItemStack.EMPTY;
			shown = ItemStack.EMPTY;
			for (int i = 0; i < layers.size(); i++)
				hideLayer(i);
		}

		private void hideLayer(int i) {
			if (lastMatrices.get(i) == null)
				return;
			layers.get(i).setItem(ItemStack.EMPTY);
			lastMatrices.set(i, null);
		}
	}
}

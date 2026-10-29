package dev.createpolymer.kinetics.belt;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.joml.Matrix4f;

import dev.createpolymer.kinetics.belt.transport.BeltInventory;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.logistics.depot.DepotRenderer;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Shows the items on a belt with display entities, laid out like Create's BeltRenderer.renderItem does on the
 * client (upright or lying items, stacks as several layers, tilted on slopes). The model of the belt's controller
 * owns the display entities of the whole belt: they are positioned relative to the controller block and moved
 * with a short interpolation, and only sent when an item moved.
 */
public class BeltRenderer {
	/** Ticks the client takes to move an item to a newly sent position. */
	public static final int INTERPOLATION = 2;

	private final ElementHolder holder;
	private final Map<TransportedItemStack, Slot> slots = new IdentityHashMap<>();
	private final List<Slot> free = new ArrayList<>();

	public BeltRenderer(ElementHolder holder) {
		this.holder = holder;
	}

	/** Call once per tick on the controller's model. */
	public void render(BeltBlockEntity be) {
		BeltInventory inventory = be.getInventory();
		if (inventory == null || be.beltLength == 0) {
			clear();
			return;
		}

		Direction beltFacing = be.getBeltFacing();
		Vec3i directionVec = beltFacing.getUnitVec3i();
		// Create translates to the start of the belt: .5 blocks behind the controller, 15/16 up. Display
		// coordinates are block-centred.
		Vec3 beltStartOffset = Vec3.atLowerCornerOf(directionVec)
			.scale(-.5)
			.add(0, 15 / 16f - .5f, 0);
		BeltSlope slope = be.getBlockState()
			.getValue(BeltBlock.SLOPE);

		Map<TransportedItemStack, Boolean> seen = new IdentityHashMap<>();
		for (TransportedItemStack transported : inventory.getTransportedItems())
			renderItem(be, beltFacing, directionVec, slope, transported, beltStartOffset, seen);
		// Create also draws BeltInventory.getLazyClientItem() for a tick, to hide client lag; the next block shows
		// that item right away here.

		for (Iterator<Map.Entry<TransportedItemStack, Slot>> iterator = slots.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<TransportedItemStack, Slot> entry = iterator.next();
			if (seen.containsKey(entry.getKey()))
				continue;
			entry.getValue().hide();
			free.add(entry.getValue());
			iterator.remove();
		}
	}

	/** Hides everything, e.g. when the block stops being a controller. */
	public void clear() {
		if (slots.isEmpty())
			return;
		for (Slot slot : slots.values()) {
			slot.hide();
			free.add(slot);
		}
		slots.clear();
	}

	private void renderItem(BeltBlockEntity be, Direction beltFacing, Vec3i directionVec, BeltSlope slope,
		TransportedItemStack transported, Vec3 beltStartOffset, Map<TransportedItemStack, Boolean> seen) {
		if (transported.stack.isEmpty())
			return;
		seen.put(transported, Boolean.TRUE);

		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;
		boolean slopeAlongX = beltFacing.getAxis() == Axis.X;

		float offset = transported.beltPosition;
		float sideOffset = transported.sideOffset;
		float verticalMovement;

		if (offset < .5)
			verticalMovement = 0;
		else
			verticalMovement = verticality * (Math.min(offset, be.beltLength - .5f) - .5f);
		Vec3 offsetVec = Vec3.atLowerCornerOf(directionVec)
			.scale(offset);
		if (verticalMovement != 0)
			offsetVec = offsetVec.add(0, verticalMovement, 0);
		boolean onSlope = slope != BeltSlope.HORIZONTAL && Mth.clamp(offset, .5f, be.beltLength - .5f) == offset;
		boolean tiltForward = (slope == BeltSlope.DOWNWARD
			^ beltFacing.getAxisDirection() == AxisDirection.POSITIVE) == (beltFacing.getAxis() == Axis.Z);
		float slopeAngle = onSlope ? tiltForward ? -45 : 45 : 0;

		Matrix4f m = new Matrix4f()
			.translate((float) (beltStartOffset.x + offsetVec.x), (float) (beltStartOffset.y + offsetVec.y),
				(float) (beltStartOffset.z + offsetVec.z));

		boolean alongX = beltFacing.getClockWise()
			.getAxis() == Axis.X;
		if (!alongX)
			sideOffset *= -1;
		m.translate(alongX ? sideOffset : 0, 0, alongX ? 0 : sideOffset);

		boolean renderUpright = BeltHelper.isItemUpright(transported.stack);
		boolean blockItem = DepotRenderer.isBlockItem(transported.stack);

		int count = Mth.log2(transported.stack.getCount()) / 2;
		Random r = new Random(transported.angle);

		float slopeOffset = 1 / 8f;
		if (!renderUpright) {
			if (slopeAlongX)
				m.rotateZ(Mth.DEG_TO_RAD * slopeAngle);
			else
				m.rotateX(Mth.DEG_TO_RAD * slopeAngle);
		}
		if (onSlope)
			m.translate(0, slopeOffset, 0);

		if (renderUpright) {
			// Create turns upright items towards the camera; here they face across the belt
			if (!slopeAlongX)
				m.rotateY(Mth.DEG_TO_RAD * 90);
			m.translate(0, 3 / 32f, 1 / 16f);
		}

		List<Matrix4f> layers = new ArrayList<>(count + 1);
		for (int i = 0; i <= count; i++) {
			Matrix4f layer = new Matrix4f(m);
			layer.rotateY(Mth.DEG_TO_RAD * transported.angle);
			if (!blockItem && !renderUpright) {
				layer.translate(0, -.09375f, 0);
				layer.rotateX(Mth.DEG_TO_RAD * 90);
			}

			if (blockItem)
				layer.translate(r.nextFloat() * .0625f * i, 0, r.nextFloat() * .0625f * i);

			layer.scale(.5f);
			layers.add(layer);

			if (!renderUpright) {
				if (!blockItem)
					m.rotateY(Mth.DEG_TO_RAD * 10);
				m.translate(0, blockItem ? 1 / 64f : 1 / 16f, 0);
			} else
				m.translate(0, 0, -1 / 16f);
		}

		Slot slot = slots.get(transported);
		if (slot == null) {
			slot = free.isEmpty() ? new Slot() : free.remove(free.size() - 1);
			slots.put(transported, slot);
		}
		slot.show(transported.stack, layers);
	}

	/** The display entities of one drawn stack, one per layer. */
	private class Slot {
		private final List<Part> layers = new ArrayList<>();
		private final List<Matrix4f> lastMatrices = new ArrayList<>();
		private ItemStack shown = ItemStack.EMPTY;
		private boolean visible;

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
					part.setMatrix(matrices.get(i), fresh ? 0 : INTERPOLATION);
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

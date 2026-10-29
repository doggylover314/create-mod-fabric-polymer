package dev.createpolymer.kinetics.crafter;

import static dev.createpolymer.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix4f;

import dev.createpolymer.kinetics.crafter.MechanicalCrafterBlockEntity.Phase;
import dev.createpolymer.kinetics.crafter.RecipeGridHandler.GroupedItems;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Pointing;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Shows the items inside a mechanical crafter with display entities, laid out like the item half of Create's
 * MechanicalCrafterRenderer. Create draws from a PoseStack every frame; this builds the same transforms and sends
 * them to the viewers whenever something changed. During the exporting and crafting phases (the item gathering
 * and the flying-off) they are sent every other tick and the client interpolates in between.
 * <p>
 * Create turns every stack around by 180 degrees before drawing it, because a PoseStack renders items facing
 * -Z. The display entity renderer already does that, so the turn is left out here.
 */
public class MechanicalCrafterRenderer {
	private static final int ANIMATION_STEP = 2;

	private final ElementHolder holder;
	private final List<Slot> slots = new ArrayList<>();
	private Phase lastPhase;
	private int lastVersion = -1;
	private BlockState lastState;

	public MechanicalCrafterRenderer(ElementHolder holder) {
		this.holder = holder;
	}

	/** Call once per tick. */
	public void render(MechanicalCrafterBlockEntity be, long time) {
		Phase phase = be.phase;
		int version = be.getDataVersion();
		BlockState state = be.getBlockState();
		boolean changed = phase != lastPhase || version != lastVersion || state != lastState;
		boolean animated = phase == Phase.EXPORTING || phase == Phase.CRAFTING;
		if (!changed && !(animated && time % ANIMATION_STEP == 0))
			return;
		lastPhase = phase;
		lastVersion = version;
		lastState = state;

		List<Entry> entries = new ArrayList<>();
		collect(be, entries);

		int interpolation = changed ? 0 : ANIMATION_STEP;
		for (int i = 0; i < entries.size(); i++) {
			if (slots.size() == i)
				slots.add(new Slot());
			slots.get(i).show(entries.get(i), interpolation);
		}
		for (int i = entries.size(); i < slots.size(); i++)
			slots.get(i).hide();
	}

	private record Entry(ItemStack stack, Matrix4f matrix) {
	}

	private static void collect(MechanicalCrafterBlockEntity be, List<Entry> out) {
		BlockState blockState = be.getBlockState();
		if (!blockState.hasProperty(HORIZONTAL_FACING))
			return;
		Direction facing = blockState.getValue(HORIZONTAL_FACING);
		Vec3 vec = Vec3.atLowerCornerOf(facing.getUnitVec3i())
			.scale(.58);

		if (be.phase == Phase.EXPORTING) {
			Direction targetDirection = MechanicalCrafterBlock.getTargetDirection(blockState);
			float progress =
				Mth.clamp((1000 - be.countDown) / 1000f, 0, 1);
			vec = vec.add(Vec3.atLowerCornerOf(targetDirection.getUnitVec3i())
				.scale(progress * .75f));
		}

		float yRot = AngleHelper.horizontalAngle(facing);
		Matrix4f base = new Matrix4f().translate((float) vec.x, (float) vec.y, (float) vec.z)
			.scale(1 / 2f)
			.rotateY((float) Math.toRadians(yRot));

		if (be.phase == Phase.IDLE) {
			ItemStack stack = be.getInventory()
				.getItem(0);
			if (!stack.isEmpty())
				out.add(new Entry(stack, new Matrix4f(base).translate(0, 0, -1 / 256f)));
			return;
		}

		// render grouped items
		GroupedItems items = be.groupedItems;
		float distance = .5f;
		Matrix4f m = new Matrix4f(base);
		boolean visible = true;

		if (be.phase == Phase.CRAFTING) {
			items = be.groupedItemsBeforeCraft;
			items.calcStats();
			float progress = Mth.clamp((2000 - be.countDown) / 1000f, 0, 1);
			float earlyProgress = Mth.clamp(progress * 2, 0, 1);
			float lateProgress = Mth.clamp(progress * 2 - 1, 0, 1);

			float scale = 1 - lateProgress;
			visible = scale > 0;
			m.scale(scale);
			Vec3 centering =
				new Vec3(-items.minX + (-items.width + 1) / 2f, -items.minY + (-items.height + 1) / 2f, 0)
					.scale(earlyProgress);
			m.translate((float) centering.x * .5f, (float) centering.y * .5f, 0);
			distance += (-4 * (progress - .5f) * (progress - .5f) + 1) * .25f;
		}

		boolean onlyRenderFirst = be.phase == Phase.INSERTING || be.phase == Phase.CRAFTING && be.countDown < 1000;
		final float spacing = distance;
		if (visible) {
			// HashMap order changes when the grid does; a fixed order keeps every stack in its own display entity
			List<Map.Entry<Pair<Integer, Integer>, ItemStack>> sorted = new ArrayList<>(items.grid.entrySet());
			sorted.sort(Comparator.<Map.Entry<Pair<Integer, Integer>, ItemStack>, Integer>comparing(e -> e.getKey().getValue())
				.thenComparing(e -> e.getKey().getKey()));
			for (var entry : sorted) {
				Pair<Integer, Integer> pair = entry.getKey();
				ItemStack stack = entry.getValue();
				if (stack.isEmpty())
					continue;
				if (onlyRenderFirst && (pair.getLeft()
					.intValue() != 0
					|| pair.getRight()
						.intValue() != 0))
					continue;

				int x = pair.getKey();
				int y = pair.getValue();
				int offset = 0;
				if (be.phase == Phase.EXPORTING && blockState.hasProperty(MechanicalCrafterBlock.POINTING)) {
					Pointing value = blockState.getValue(MechanicalCrafterBlock.POINTING);
					offset = value == Pointing.UP ? -1 : value == Pointing.LEFT ? 2 : value == Pointing.RIGHT ? -2 : 1;
				}

				// Create: rotateYDegrees(180).translate(0, 0, z) moves the item backwards, into the block
				float z = (x + y * 3 + offset * 9) / 1024f;
				out.add(new Entry(stack, new Matrix4f(m).translate(x * spacing, y * spacing, 0)
					.translate(0, 0, -z)));
			}
		}

		if (be.phase == Phase.CRAFTING) {
			items = be.groupedItems;
			float progress = Mth.clamp((1000 - be.countDown) / 1000f, 0, 1);
			float earlyProgress = Mth.clamp(progress * 2, 0, 1);
			float lateProgress = Mth.clamp(progress * 2 - 1, 0, 1);

			float upScaling = earlyProgress * 1.125f;
			float downScaling = 1 + (1 - lateProgress) * .125f;
			ItemStack stack = items.grid.get(Pair.of(0, 0));
			if (stack != null && !stack.isEmpty() && upScaling > 0) {
				Matrix4f result = new Matrix4f(base).rotateZ((float) Math.toRadians(earlyProgress * 2 * 360))
					.scale(upScaling)
					.scale(downScaling);
				out.add(new Entry(stack, result));
			}
		}
	}

	/** One display entity showing one stack. */
	private class Slot {
		private Part part;
		private ItemStack shown = ItemStack.EMPTY;
		private Matrix4f lastMatrix;

		void show(Entry entry, int interpolation) {
			if (part == null) {
				part = new Part(ItemStack.EMPTY);
				part.element.setItemDisplayContext(ItemDisplayContext.FIXED);
				holder.addElement(part.element);
			}
			ItemStack one = entry.stack.copyWithCount(1);
			boolean fresh = lastMatrix == null;
			if (fresh || !ItemStack.isSameItemSameComponents(shown, one)) {
				part.setItem(one);
				shown = one;
			}
			if (!entry.matrix.equals(lastMatrix)) {
				part.setMatrix(entry.matrix, fresh ? 0 : interpolation);
				lastMatrix = entry.matrix;
			}
		}

		void hide() {
			if (lastMatrix == null)
				return;
			part.setItem(ItemStack.EMPTY);
			shown = ItemStack.EMPTY;
			lastMatrix = null;
		}
	}
}

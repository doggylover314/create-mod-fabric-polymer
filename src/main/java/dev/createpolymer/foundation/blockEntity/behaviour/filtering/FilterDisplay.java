package dev.createpolymer.foundation.blockEntity.behaviour.filtering;

import java.util.EnumMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.kinetics.simple.AbstractShaftBlock;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shows the filter item of a block entity's {@link FilteringBehaviour} (or one per side for a
 * {@link SidedFilteringBehaviour}) in the value box position, like Create's filter renderer does on the client.
 * <p>
 * Usage in a block model (a {@code BaseModel}):
 * <pre>
 * private final FilterDisplay filter = new FilterDisplay(this);   // in the constructor
 * ...
 * protected void update(ServerLevel level, BlockPos pos, BlockEntity be, long time) {
 *     if (be instanceof SmartBlockEntity sbe)
 *         filter.update(sbe);                                      // every tick, it only sends changes
 * }
 * </pre>
 * The display entities are hidden (empty item) while the slot has no filter, is inactive or its
 * {@link ValueBoxTransform} says it should not render for the current block state.
 */
public class FilterDisplay {
	private final ElementHolder holder;
	private final Map<Direction, Slot> sidedSlots = new EnumMap<>(Direction.class);
	private Slot single;

	public FilterDisplay(ElementHolder holder) {
		this.holder = holder;
	}

	/** Call every tick (or whenever the block entity or its state changed). Only sends packets on change. */
	public void update(SmartBlockEntity be) {
		Level level = be.getLevel();
		FilteringBehaviour behaviour = be.getBehaviour(FilteringBehaviour.TYPE);
		if (level == null || behaviour == null) {
			hideAll();
			return;
		}

		BlockPos pos = be.getBlockPos();
		BlockState state = be.getBlockState();
		ValueBoxTransform slotPositioning = behaviour.getSlotPositioning();
		boolean active = behaviour.isActive();

		if (slotPositioning instanceof ValueBoxTransform.Sided sided) {
			Direction side = sided.getSide();
			for (Direction d : Iterate.directions) {
				ItemStack filter = active ? behaviour.getFilter(d) : ItemStack.EMPTY;
				Slot slot = sidedSlots.get(d);
				if (filter.isEmpty() && slot == null)
					continue;
				if (slot == null)
					sidedSlots.put(d, slot = new Slot());
				sided.fromSide(d);
				slot.show(level, pos, state, slotPositioning, filter);
			}
			sided.fromSide(side);
			if (single != null)
				single.show(level, pos, state, slotPositioning, ItemStack.EMPTY);
		} else {
			if (single == null)
				single = new Slot();
			single.show(level, pos, state, slotPositioning, active ? behaviour.getFilter() : ItemStack.EMPTY);
			sidedSlots.values()
				.forEach(s -> s.show(level, pos, state, slotPositioning, ItemStack.EMPTY));
		}
	}

	private void hideAll() {
		if (single != null)
			single.hide();
		sidedSlots.values()
			.forEach(Slot::hide);
	}

	/** Create's ValueBoxRenderer.customZOffset. */
	private static float customZOffset(Item item) {
		float nudge = -.1f;
		if (item instanceof BlockItem blockItem) {
			Block block = blockItem.getBlock();
			if (block instanceof AbstractShaftBlock)
				return nudge;
			if (block instanceof FenceBlock)
				return nudge;
			if (block.builtInRegistryHolder()
				.is(BlockTags.BUTTONS))
				return nudge;
			if (block == Blocks.END_ROD)
				return nudge;
		}
		return 0;
	}

	private class Slot {
		private final Part part = new Part(ItemStack.EMPTY);
		private ItemStack shown = ItemStack.EMPTY;
		@Nullable
		private BlockState shownState;

		Slot() {
			part.element.setItemDisplayContext(ItemDisplayContext.FIXED);
			holder.addElement(part.element);
		}

		void hide() {
			if (shown.isEmpty())
				return;
			shown = ItemStack.EMPTY;
			shownState = null;
			part.setItem(ItemStack.EMPTY);
		}

		void show(Level level, BlockPos pos, BlockState state, ValueBoxTransform slotPositioning, ItemStack filter) {
			if (filter.isEmpty() || !slotPositioning.shouldRender(level, pos, state)) {
				hide();
				return;
			}
			if (state == shownState && ItemStack.isSameItemSameComponents(filter, shown))
				return;
			shownState = state;
			shown = filter.copyWithCount(1);

			boolean blockItem = shown.getItem() instanceof BlockItem;
			float scale = (!blockItem ? .5f : 1f) + 1 / 64f;
			float zOffset = (!blockItem ? -.15f : 0) + customZOffset(shown.getItem());

			Matrix4f m = new Matrix4f().translate(-.5f, -.5f, -.5f);
			slotPositioning.transform(level, pos, state, m);
			m.scale(scale)
				.translate(0, 0, zOffset)
				// Display entities turn their item around before drawing it
				.rotateY((float) Math.PI);
			part.setItem(shown);
			part.setMatrix(m, 0);
		}
	}
}

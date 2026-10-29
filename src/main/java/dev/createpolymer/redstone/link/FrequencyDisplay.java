package dev.createpolymer.redstone.link;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shows the two frequency items of a {@link LinkBehaviour} in their slots, like Create's LinkRenderer does.
 * Call {@link #update} every tick; it only sends changes.
 */
public class FrequencyDisplay {
	private final Slot first;
	private final Slot second;

	public FrequencyDisplay(ElementHolder holder) {
		first = new Slot(holder);
		second = new Slot(holder);
	}

	public void update(Level level, BlockPos pos, BlockState state, LinkBehaviour link) {
		first.show(level, pos, state, link.getSlot(true), link.getFrequencyStack(true));
		second.show(level, pos, state, link.getSlot(false), link.getFrequencyStack(false));
	}

	private static class Slot {
		private final Part part = new Part(ItemStack.EMPTY);
		private ItemStack shown = ItemStack.EMPTY;
		private @Nullable BlockState shownState;

		Slot(ElementHolder holder) {
			part.element.setItemDisplayContext(ItemDisplayContext.FIXED);
			holder.addElement(part.element);
		}

		void show(Level level, BlockPos pos, BlockState state, ValueBoxTransform slotPositioning, ItemStack stack) {
			if (stack.isEmpty()) {
				if (!shown.isEmpty()) {
					shown = ItemStack.EMPTY;
					shownState = null;
					part.setItem(ItemStack.EMPTY);
				}
				return;
			}
			if (state == shownState && ItemStack.isSameItemSameComponents(stack, shown))
				return;
			shownState = state;
			shown = stack.copyWithCount(1);

			boolean blockItem = shown.getItem() instanceof BlockItem;
			float scale = (!blockItem ? .5f : 1f) + 1 / 64f;
			float zOffset = !blockItem ? -.15f : 0;

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

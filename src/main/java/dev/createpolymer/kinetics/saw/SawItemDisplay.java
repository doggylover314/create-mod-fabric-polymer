package dev.createpolymer.kinetics.saw;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import dev.createpolymer.logistics.depot.DepotRenderer;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shows the items on an upward facing saw with display entities, laid out like Create's
 * SawRenderer.renderItems: the stack slides over the blade while it is cut and the results
 * line up behind it.
 */
class SawItemDisplay {
	private final ElementHolder holder;
	private final List<Slot> slots = new ArrayList<>();

	SawItemDisplay(ElementHolder holder) {
		this.holder = holder;
	}

	void render(SawBlockEntity be, BlockState state) {
		if (state.getValue(SawBlock.FACING) != Direction.UP || be.inventory.isEmpty()) {
			slots.forEach(Slot::hide);
			return;
		}

		boolean alongZ = !state.getValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE);

		float duration = be.inventory.recipeDuration;
		boolean moving = duration != 0;
		float offset = moving ? be.inventory.remainingTime / duration : 0;
		float processingSpeed = Mth.clamp(Math.abs(be.getSpeed()) / 32, 1, 128);
		if (moving) {
			// Create evaluates this per frame with a partial tick; the display entity interpolates up to the
			// end of the current tick.
			offset = Mth.clamp(offset + ((-1 + .5f) * processingSpeed) / duration, 0.125f, 1f);
			if (!be.inventory.appliedRecipe)
				offset += 1;
			offset /= 2;
		}

		if (be.getSpeed() == 0)
			offset = .5f;
		if (be.getSpeed() < 0 ^ alongZ)
			offset = 1 - offset;

		int outputs = 0;
		for (int i = 1; i < be.inventory.getSlots(); i++)
			if (!be.inventory.getStackInSlot(i)
				.isEmpty())
				outputs++;

		// Create works in block corner space; display entities are block-centred
		Matrix4f base = new Matrix4f().translate(-.5f, -.5f, -.5f);
		if (alongZ)
			base.rotateY((float) Math.toRadians(90));
		base.translate(outputs <= 1 ? .5f : .25f, 0, offset);
		base.translate(alongZ ? -1 : 0, 0, 0);

		int renderedI = 0;
		for (int i = 0; i < be.inventory.getSlots(); i++) {
			ItemStack stack = be.inventory.getStackInSlot(i);
			if (stack.isEmpty()) {
				if (i < slots.size())
					slots.get(i).hide();
				continue;
			}
			while (slots.size() <= i)
				slots.add(new Slot());

			boolean blockItem = DepotRenderer.isBlockItem(stack);
			Matrix4f m = new Matrix4f(base);
			m.translate(0, blockItem ? .925f : 13f / 16f, 0);

			if (i > 0 && outputs > 1) {
				m.translate((0.5f / (outputs - 1)) * renderedI, 0, 0);
				nudge(m, i * 133);
			}

			m.scale(.5f);
			m.rotateX((float) Math.toRadians(90));
			// Display entities turn their item around before drawing it
			m.rotateY((float) Math.PI);

			slots.get(i).show(stack, m);
			renderedI++;
		}
		for (int i = be.inventory.getSlots(); i < slots.size(); i++)
			slots.get(i).hide();
	}

	/** Flywheel's TransformStack.nudge: a tiny offset so overlapping items do not z-fight. */
	private static void nudge(Matrix4f m, int seed) {
		long randomBits = (long) seed * 31L * 493286711L;
		randomBits = randomBits * randomBits * 4392167121L + randomBits * 98761L;
		float xNudge = (((float) (randomBits >> 16 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
		float yNudge = (((float) (randomBits >> 20 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
		float zNudge = (((float) (randomBits >> 24 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
		m.translate(xNudge, yNudge, zNudge);
	}

	private class Slot {
		private final Part part = new Part(ItemStack.EMPTY);
		private ItemStack shown = ItemStack.EMPTY;
		private Matrix4f last;

		Slot() {
			part.element.setItemDisplayContext(ItemDisplayContext.FIXED);
			holder.addElement(part.element);
		}

		void show(ItemStack stack, Matrix4f matrix) {
			ItemStack one = stack.copyWithCount(1);
			boolean fresh = last == null;
			if (fresh || !ItemStack.isSameItemSameComponents(shown, one)) {
				part.setItem(one);
				shown = one;
			}
			if (!matrix.equals(last)) {
				part.setMatrix(matrix, fresh ? 0 : 1);
				last = matrix;
			}
		}

		void hide() {
			if (last == null)
				return;
			last = null;
			shown = ItemStack.EMPTY;
			part.setItem(ItemStack.EMPTY);
		}
	}
}

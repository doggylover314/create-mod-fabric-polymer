package dev.createpolymer.fluids;

import org.joml.Matrix4f;

import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.world.item.ItemStack;

/**
 * One cuboid of fluid inside a model, drawn by an item display entity (see {@link FluidDisplay}). Coordinates are
 * block-centred like everywhere in the models: 0,0,0 is the middle of the block the holder is attached to.
 * Nothing is sent to players unless the fluid or its shape changed.
 */
public class FluidBox {
	private static final float MIN_SIZE = 1 / 64f;

	private final Part part = new Part(ItemStack.EMPTY);
	private final Matrix4f last = new Matrix4f();
	private FluidStack shown = FluidStack.EMPTY;
	private boolean visible;

	public FluidBox(ElementHolder holder) {
		holder.addElement(part.element);
	}

	/** Shows the fluid between two corners of the box, or nothing if it is empty or has no volume. */
	public void show(FluidStack fluid, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		if (fluid.isEmpty() || maxX - minX < MIN_SIZE || maxY - minY < MIN_SIZE || maxZ - minZ < MIN_SIZE) {
			hide();
			return;
		}
		boolean fresh = !visible;
		if (fresh || !FluidStack.isSameFluidSameComponents(shown, fluid)) {
			part.setItem(FluidDisplay.stack(fluid));
			shown = fluid.copyWithAmount(1);
		}
		Matrix4f matrix = new Matrix4f()
			.translate((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)
			.scale(maxX - minX, maxY - minY, maxZ - minZ);
		if (fresh || !matrix.equals(last, 1e-4f)) {
			part.setMatrix(matrix, fresh ? 0 : 1);
			last.set(matrix);
		}
		visible = true;
	}

	public void hide() {
		if (!visible)
			return;
		visible = false;
		shown = FluidStack.EMPTY;
		part.setItem(ItemStack.EMPTY);
	}
}

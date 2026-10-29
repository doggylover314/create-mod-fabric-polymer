package dev.createpolymer.trains.bogey;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The display parts of one bogey (the server's version of Create's BogeyVisual / BogeyRenderer). Every part is an
 * item display showing one of Create's bogey models; {@link #update} places them for a wheel angle.
 * <p>
 * Create poses its partial models in "pose space", where the origin is the bogey's reference point on the rail
 * and the models keep the coordinates of their OBJ files. A display is centred on the middle of its model's block,
 * so {@link #place} appends the half-block shift.
 */
public abstract class BogeyVisual {
	/** Matrix scale Create applies to the large pieces so they do not fight with the frame. */
	protected static final float SLIGHTLY_SMALLER = 1 - 1 / 512f;

	private final List<ItemDisplayElement> elements = new ArrayList<>();

	protected ItemDisplayElement add(ItemStack model) {
		ItemDisplayElement element = new ItemDisplayElement(model);
		element.setItemDisplayContext(ItemDisplayContext.NONE);
		element.setViewRange(1f);
		element.setTeleportDuration(0);
		element.setInterpolationDuration(0);
		elements.add(element);
		return element;
	}

	public List<ItemDisplayElement> elements() {
		return elements;
	}

	/**
	 * Places the parts. {@code base} maps the pose space of the bogey to the space of the holder; the same
	 * transformation is reused for every part.
	 */
	public abstract void update(CompoundTag bogeyData, float wheelAngle, Matrix4f base, int interpolation);

	/** Pose-space matrix of a part to the transformation of its display. */
	protected static void place(ItemDisplayElement element, Matrix4f base, Matrix4f pose, int interpolation) {
		Matrix4f m = new Matrix4f(base).mul(pose)
			.translate(.5f, .5f, .5f);
		element.setTransformation(m);
		if (element.isTransformationDirty()) {
			element.setInterpolationDuration(interpolation);
			element.startInterpolation();
		}
	}

	/** Shrinks the parts to nothing (a bogey inside a portal, or without a track). */
	public void hide(int interpolation) {
		for (ItemDisplayElement element : elements) {
			element.setTransformation(new Matrix4f().scale(0));
			if (element.isTransformationDirty()) {
				element.setInterpolationDuration(interpolation);
				element.startInterpolation();
			}
		}
	}
}

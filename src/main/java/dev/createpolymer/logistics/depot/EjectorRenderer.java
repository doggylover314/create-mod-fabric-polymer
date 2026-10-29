package dev.createpolymer.logistics.depot;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;

import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.IntAttached;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The moving parts of Create's EjectorRenderer: the angle of the plate and the items flying through the air.
 * The plate itself and the held item are placed by the ejector's model, using {@link #applyLidAngle}.
 */
public class EjectorRenderer {

	static final Vec3 pivot = VecHelper.voxelSpace(0, 11.25, 0.75);

	private final ElementHolder holder;
	private final Map<IntAttached<ItemStack>, Flying> flying = new IdentityHashMap<>();
	private int pass;

	public EjectorRenderer(ElementHolder holder) {
		this.holder = holder;
	}

	/** Call once per tick. */
	public void render(EjectorBlockEntity be) {
		Direction facing = be.getFacing();
		float maxTime = (float) (be.earlyTarget != null ? be.earlyTargetTime : be.launcher.getTotalFlyingTicks());
		Vec3 center = Vec3.atCenterOf(be.getBlockPos());

		pass++;
		for (IntAttached<ItemStack> intAttached : be.launchedItems) {
			float time = intAttached.getFirst();
			Flying shown = flying.get(intAttached);
			if (shown != null)
				shown.lastPass = pass;
			if (time > maxTime) {
				if (shown != null && shown.visible) {
					shown.part.setItem(ItemStack.EMPTY);
					shown.visible = false;
				}
				continue;
			}

			boolean fresh = shown == null || !shown.visible;
			if (shown == null) {
				Part part = new Part(ItemStack.EMPTY);
				part.element.setItemDisplayContext(ItemDisplayContext.FIXED);
				holder.addElement(part.element);
				shown = new Flying(part);
				shown.lastPass = pass;
				flying.put(intAttached, shown);
			}
			ItemStack one = intAttached.getSecond().copyWithCount(1);
			if (fresh || !ItemStack.isSameItemSameComponents(shown.stack, one)) {
				shown.part.setItem(one);
				shown.stack = one;
			}
			shown.visible = true;

			// Create: translate(location), translate(itemRotOffset), scale(.5), rotateY(facing), rotateX(time * 40),
			// translateBack(itemRotOffset)
			Vec3 location = be.getLaunchedItemLocation(time).subtract(center);
			Vec3 itemRotOffset = VecHelper.voxelSpace(0, 2, -1);
			Matrix4f m = new Matrix4f()
				.translate((float) location.x, (float) location.y, (float) location.z)
				.translate((float) itemRotOffset.x, (float) itemRotOffset.y, (float) itemRotOffset.z)
				.scale(.5f)
				.rotateY((float) Math.toRadians(AngleHelper.horizontalAngle(facing)))
				.rotateX((float) Math.toRadians(time * 40))
				.translate((float) -itemRotOffset.x, (float) -itemRotOffset.y, (float) -itemRotOffset.z);
			shown.part.setMatrix(m, fresh ? 0 : 1);
		}

		// Items that landed or were dropped
		for (Iterator<Flying> iterator = flying.values().iterator(); iterator.hasNext();) {
			Flying gone = iterator.next();
			if (gone.lastPass == pass)
				continue;
			holder.removeElement(gone.part.element);
			iterator.remove();
		}
	}

	/** Create's EjectorRenderer.applyLidAngle, as a matrix on block-centred coordinates. */
	public static Matrix4f applyLidAngle(Direction facing, float angle) {
		return applyLidAngle(facing, pivot, angle);
	}

	public static Matrix4f applyLidAngle(Direction facing, Vec3 rotationOffset, float angle) {
		float px = (float) rotationOffset.x - .5f;
		float py = (float) rotationOffset.y - .5f;
		float pz = (float) rotationOffset.z - .5f;
		return new Matrix4f()
			.rotateY((float) Math.toRadians(180 + AngleHelper.horizontalAngle(facing)))
			.translate(px, py, pz)
			.rotateX((float) Math.toRadians(-angle))
			.translate(-px, -py, -pz);
	}

	private static class Flying {
		final Part part;
		ItemStack stack = ItemStack.EMPTY;
		boolean visible;
		int lastPass;

		Flying(Part part) {
			this.part = part;
		}
	}

}

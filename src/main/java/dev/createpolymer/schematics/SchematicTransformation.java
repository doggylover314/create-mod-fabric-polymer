package dev.createpolymer.schematics;

import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * Create's SchematicTransformation without the animation: where the structure is held ("target", the lowest corner
 * of the box it is turned and mirrored in), how it is turned (degrees) and how it is mirrored (scale of +1 or -1
 * per axis), and the conversion between that and the anchor and settings that the schematic item stores.
 */
public class SchematicTransformation {

	private BlockPos target = BlockPos.ZERO;
	private int rotation;
	private int scaleFrontBack = 1;
	private int scaleLeftRight = 1;
	private float xOrigin;
	private float zOrigin;

	public void init(BlockPos anchor, StructurePlaceSettings settings, Vec3i size) {
		scaleLeftRight = settings.getMirror() == Mirror.LEFT_RIGHT ? -1 : 1;
		scaleFrontBack = settings.getMirror() == Mirror.FRONT_BACK ? -1 : 1;
		xOrigin = size.getX() / 2f;
		zOrigin = size.getZ() / 2f;
		rotation = -(settings.getRotation()
			.ordinal() * 90);
		target = fromAnchor(anchor);
	}

	public boolean isFlipped() {
		return getMirrorModifier(Axis.X) < 0 != getMirrorModifier(Axis.Z) < 0;
	}

	public Vec3 getRotationOffset(boolean ignoreMirrors) {
		Vec3 rotationOffset = Vec3.ZERO;
		if ((int) (zOrigin * 2) % 2 != (int) (xOrigin * 2) % 2) {
			boolean xGreaterZ = xOrigin > zOrigin;
			float xIn = (xGreaterZ ? 0 : .5f);
			float zIn = (!xGreaterZ ? 0 : .5f);
			if (!ignoreMirrors) {
				xIn *= getMirrorModifier(Axis.X);
				zIn *= getMirrorModifier(Axis.Z);
			}
			rotationOffset = new Vec3(xIn, 0, zIn);
		}
		return rotationOffset;
	}

	public StructurePlaceSettings toSettings() {
		StructurePlaceSettings settings = new StructurePlaceSettings();

		int i = rotation;

		boolean mirrorlr = scaleLeftRight < 0;
		boolean mirrorfb = scaleFrontBack < 0;
		if (mirrorlr && mirrorfb) {
			mirrorlr = mirrorfb = false;
			i += 180;
		}
		i = i % 360;
		if (i < 0)
			i += 360;

		Rotation rotation = Rotation.NONE;
		switch (i) {
		case 90:
			rotation = Rotation.COUNTERCLOCKWISE_90;
			break;
		case 180:
			rotation = Rotation.CLOCKWISE_180;
			break;
		case 270:
			rotation = Rotation.CLOCKWISE_90;
			break;
		default:
		}

		settings.setRotation(rotation);
		if (mirrorfb)
			settings.setMirror(Mirror.FRONT_BACK);
		if (mirrorlr)
			settings.setMirror(Mirror.LEFT_RIGHT);

		return settings;
	}

	public BlockPos getAnchor() {
		Vec3 vec = Vec3.ZERO.add(.5, 0, .5);
		Vec3 rotationOffset = getRotationOffset(false);
		vec = vec.subtract(xOrigin, 0, zOrigin);
		vec = vec.subtract(rotationOffset.x, 0, rotationOffset.z);
		vec = vec.multiply(scaleFrontBack, 1, scaleLeftRight);
		vec = VecHelper.rotate(vec, rotation, Axis.Y);
		vec = vec.add(xOrigin, 0, zOrigin);
		vec = vec.add(target.getX(), target.getY(), target.getZ());
		return BlockPos.containing(vec.x, vec.y, vec.z);
	}

	public BlockPos fromAnchor(BlockPos pos) {
		Vec3 vec = Vec3.ZERO.add(.5, 0, .5);
		Vec3 rotationOffset = getRotationOffset(false);
		vec = vec.subtract(xOrigin, 0, zOrigin);
		vec = vec.subtract(rotationOffset.x, 0, rotationOffset.z);
		vec = vec.multiply(scaleFrontBack, 1, scaleLeftRight);
		vec = VecHelper.rotate(vec, rotation, Axis.Y);
		vec = vec.add(xOrigin, 0, zOrigin);
		return pos.subtract(BlockPos.containing(vec.x, vec.y, vec.z));
	}

	public int getRotationTarget() {
		return rotation;
	}

	public int getMirrorModifier(Axis axis) {
		if (axis == Axis.Z)
			return scaleLeftRight;
		return scaleFrontBack;
	}

	public BlockPos getTarget() {
		return target;
	}

	public void flip(Axis axis) {
		if (axis == Axis.X)
			scaleLeftRight *= -1;
		if (axis == Axis.Z)
			scaleFrontBack *= -1;
	}

	public void rotate90(boolean clockwise) {
		rotation += clockwise ? -90 : 90;
	}

	public void move(int xIn, int yIn, int zIn) {
		moveTo(target.offset(xIn, yIn, zIn));
	}

	public void moveTo(BlockPos pos) {
		target = pos;
	}

	public void moveTo(int xIn, int yIn, int zIn) {
		moveTo(new BlockPos(xIn, yIn, zIn));
	}
}

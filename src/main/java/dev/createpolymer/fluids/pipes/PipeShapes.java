package dev.createpolymer.fluids.pipes;

import static dev.createpolymer.util.ShapeUtil.box;

import dev.createpolymer.util.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.shapes.Shapes;

/** The pipe related entries of Create's AllShapes. */
public final class PipeShapes {
	private PipeShapes() {}

	public static final VoxelShaper EIGHT_VOXEL_POLE = VoxelShaper.forAxis(box(4, 0, 4, 12, 16, 12), Axis.Y);

	public static final VoxelShaper FLUID_VALVE =
		VoxelShaper.forAxis(Shapes.or(box(3, 0, 3, 13, 16, 13), box(2, 2, 2, 14, 14, 14)), Axis.Y);

	public static final VoxelShaper SMART_FLUID_PIPE_FLOOR = VoxelShaper.forHorizontal(
		Shapes.or(box(4, 4, 0, 12, 12, 16), box(3, 3, 3, 13, 13, 13), box(5, 13, 3, 11, 14, 11),
			box(5, 14, 4, 11, 15, 10), box(5, 15, 5, 11, 16, 9), box(5, 16, 6, 11, 17, 8)),
		Direction.SOUTH);

	public static final VoxelShaper SMART_FLUID_PIPE_WALL = VoxelShaper.forHorizontal(
		Shapes.or(box(4, 0, 4, 12, 16, 12), box(3, 3, 3, 13, 13, 13), box(5, 5, 13, 11, 13, 14),
			box(5, 6, 14, 11, 12, 15), box(5, 7, 15, 11, 11, 16), box(5, 8, 16, 11, 10, 17)),
		Direction.SOUTH);

	public static final VoxelShaper SMART_FLUID_PIPE_CEILING = VoxelShaper.forHorizontal(
		Shapes.or(box(4, 4, 0, 12, 12, 16), box(3, 3, 3, 13, 13, 13), box(5, 2, 3, 11, 3, 11),
			box(5, 1, 4, 11, 2, 10), box(5, 0, 5, 11, 1, 9), box(5, -1, 6, 11, 0, 8)),
		Direction.SOUTH);

	public static final VoxelShaper PUMP = VoxelShaper.forDirectional(box(2, 0, 2, 14, 16, 14), Direction.UP);
}

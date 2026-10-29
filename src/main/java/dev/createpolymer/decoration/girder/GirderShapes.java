package dev.createpolymer.decoration.girder;

import static dev.createpolymer.util.ShapeUtil.box;

import dev.createpolymer.fluids.pipes.PipeShapes;
import dev.createpolymer.util.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The girder related entries of Create's AllShapes. */
public final class GirderShapes {
	private GirderShapes() {}

	public static final VoxelShaper SIX_VOXEL_POLE = VoxelShaper.forAxis(box(5, 0, 5, 11, 16, 11), Axis.Y);
	public static final VoxelShaper TEN_VOXEL_POLE = VoxelShaper.forAxis(box(3, 0, 3, 13, 16, 13), Axis.Y);
	public static final VoxelShaper EIGHT_VOXEL_POLE = PipeShapes.EIGHT_VOXEL_POLE;

	public static final VoxelShaper GIRDER_BEAM = VoxelShaper.forHorizontalAxis(box(4, 2, 0, 12, 14, 16), Axis.Z);
	public static final VoxelShaper GIRDER_BEAM_SHAFT = VoxelShaper.forHorizontalAxis(
		Shapes.or(GIRDER_BEAM.get(Axis.X), SIX_VOXEL_POLE.get(Axis.Z)), Axis.Z);
	public static final VoxelShape GIRDER_CROSS = Shapes.or(TEN_VOXEL_POLE.get(Axis.Y), GIRDER_BEAM.get(Axis.X),
		GIRDER_BEAM.get(Axis.Z));

	public static final VoxelShaper PLACARD = VoxelShaper.forDirectional(box(2, 0, 2, 14, 3, 14), Direction.UP);
}

package dev.createpolymer.logistics.funnel;

import static net.minecraft.world.level.block.Block.box;

import dev.createpolymer.util.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The funnel shapes of Create's AllShapes. */
public final class FunnelShapes {
	private FunnelShapes() {}

	public static final VoxelShaper FUNNEL_COLLISION =
		VoxelShaper.forDirectional(box(0, 0, 0, 16, 4, 16), Direction.UP);

	public static final VoxelShaper BELT_FUNNEL_RETRACTED = VoxelShaper.forHorizontal(
		Shapes.or(box(2, -2, 14, 14, 14, 18), box(0, -5, 8, 16, 16, 14)), Direction.NORTH);

	public static final VoxelShaper BELT_FUNNEL_EXTENDED = VoxelShaper.forHorizontal(
		Shapes.or(box(2, -2, 14, 14, 14, 18), box(3, -4, 10, 13, 13, 14), box(2, -4, 6, 14, 14, 10),
			box(0, -5, 0, 16, 16, 6)),
		Direction.NORTH);

	public static final VoxelShaper BELT_FUNNEL_PERPENDICULAR = VoxelShaper.forHorizontal(
		Shapes.or(box(2, -2, 14, 14, 14, 18), box(1, 8, 12, 15, 15, 14), box(0.1, 13, 7, 15.9, 15, 11),
			box(0.1, 9, 8, 15.9, 13, 12), box(0.1, 5, 9, 15.9, 9, 13), box(0.1, 1, 10, 15.9, 5, 14),
			box(0.1, -3, 11, 15.9, 1, 15)),
		Direction.NORTH);

	public static final VoxelShaper FUNNEL_WALL = VoxelShaper.forHorizontal(
		Shapes.or(box(2, 2, 14, 14, 14, 18), box(1, 8, 12, 15, 15, 14), box(0.1, 13, 7, 15.9, 15, 11),
			box(0.1, 9, 8, 15.9, 13, 12), box(0.1, 5, 9, 15.9, 9, 13), box(0.1, 1, 10, 15.9, 5, 14),
			box(0.1, -1, 11, 15.9, 1, 15)),
		Direction.NORTH);

	public static final VoxelShape FUNNEL_FLOOR =
		Shapes.or(box(2, -2, 2, 14, 8, 14), box(1, 1, 1, 15, 8, 15), box(0, 4, 0, 16, 10, 16));

	public static final VoxelShape FUNNEL_CEILING =
		Shapes.or(box(2, 8, 2, 14, 18, 14), box(1, 8, 1, 15, 15, 15), box(0, 6, 0, 16, 12, 16));
}

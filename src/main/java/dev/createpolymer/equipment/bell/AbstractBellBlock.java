package dev.createpolymer.equipment.bell;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.visual.PolymerModelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Create's bell base: a vanilla bell that rings through its block entity, so the bell swings with a ring. Its shapes
 * are Create's (AllShapes.BELL_*). The ring is drawn by {@link BellModel}.
 */
public abstract class AbstractBellBlock<BE extends AbstractBellBlockEntity> extends BellBlock implements IBE<BE>, PolymerModelBlock {

	private static final VoxelShaper SHAPE_FLOOR = VoxelShaper.forHorizontal(Shapes.or(
		ShapeUtil.box(0, 0, 5, 16, 11, 11), ShapeUtil.box(3, 1, 3, 13, 13, 13)), Direction.SOUTH);
	private static final VoxelShaper SHAPE_WALL = VoxelShaper.forHorizontal(Shapes.or(
		ShapeUtil.box(5, 5, 8, 11, 11, 16), ShapeUtil.box(3, 1, 3, 13, 13, 13)), Direction.SOUTH);
	private static final VoxelShaper SHAPE_DOUBLE_WALL = VoxelShaper.forHorizontal(Shapes.or(
		ShapeUtil.box(5, 5, 0, 11, 11, 16), ShapeUtil.box(3, 1, 3, 13, 13, 13)), Direction.SOUTH);
	private static final VoxelShaper SHAPE_CEILING = VoxelShaper.forHorizontal(Shapes.or(
		ShapeUtil.box(0, 5, 5, 16, 16, 11), ShapeUtil.box(3, 1, 3, 13, 13, 13)), Direction.SOUTH);

	public AbstractBellBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter reader, BlockPos pos, CollisionContext selection) {
		Direction facing = state.getValue(FACING);
		return switch (state.getValue(ATTACHMENT)) {
			case CEILING -> SHAPE_CEILING.get(facing);
			case DOUBLE_WALL -> SHAPE_DOUBLE_WALL.get(facing);
			case FLOOR -> SHAPE_FLOOR.get(facing);
			case SINGLE_WALL -> SHAPE_WALL.get(facing);
		};
	}

	@Override
	protected void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock, @Nullable Orientation orientation,
		boolean pIsMoving) {
		if (pLevel.isClientSide())
			return;
		boolean shouldPower = pLevel.hasNeighborSignal(pPos);
		if (shouldPower == pState.getValue(POWERED))
			return;
		pLevel.setBlock(pPos, pState.setValue(POWERED, shouldPower), Block.UPDATE_ALL);
		if (!shouldPower)
			return;
		Direction facing = pState.getValue(FACING);
		BellAttachType type = pState.getValue(ATTACHMENT);
		ring(pLevel, pPos,
			type == BellAttachType.CEILING || type == BellAttachType.FLOOR ? facing : facing.getClockWise(), null);
	}

	@Override
	public boolean onHit(Level world, BlockState state, BlockHitResult hit, @Nullable Player player, boolean flag) {
		BlockPos pos = hit.getBlockPos();
		Direction direction = hit.getDirection();
		if (direction == null)
			direction = world.getBlockState(pos).getValue(FACING);
		if (!this.canRingFrom(state, direction, hit.getLocation().y - pos.getY()))
			return false;
		return ring(world, pos, direction, player);
	}

	protected boolean ring(Level world, BlockPos pos, Direction direction, @Nullable Player player) {
		BE be = getBlockEntity(world, pos);
		if (world.isClientSide())
			return true;
		if (be == null || !be.ring(world, pos, direction))
			return false;
		playSound(world, pos);
		if (player != null)
			player.awardStat(Stats.BELL_RING);
		return true;
	}

	public boolean canRingFrom(BlockState state, Direction hitDir, double heightChange) {
		if (hitDir.getAxis() == Direction.Axis.Y)
			return false;
		if (heightChange > 0.8124)
			return false;

		Direction direction = state.getValue(FACING);
		BellAttachType bellAttachment = state.getValue(ATTACHMENT);
		switch (bellAttachment) {
		case FLOOR:
		case CEILING:
			return direction.getAxis() == hitDir.getAxis();
		case SINGLE_WALL:
		case DOUBLE_WALL:
			return direction.getAxis() != hitDir.getAxis();
		default:
			return false;
		}
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return IBE.super.newBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return IBE.super.getTicker(level, state, type);
	}

	public abstract void playSound(Level world, BlockPos pos);
}

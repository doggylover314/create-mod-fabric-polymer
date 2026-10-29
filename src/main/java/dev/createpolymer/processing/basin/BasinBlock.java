package dev.createpolymer.processing.basin;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.SmartBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's BasinBlock (MIT, The Create Team).
 * Output direction (wrenching the basin) and fluids are not ported yet.
 */
public class BasinBlock extends Block implements EntityBlock, PolymerBlock, BlockWithElementHolder {
	public static final VoxelShape BLOCK_SHAPE = Shapes.or(
		Shapes.join(ShapeUtil.box(0, 2, 0, 16, 16, 16), ShapeUtil.box(2, 2, 2, 14, 16, 14), BooleanOp.ONLY_FIRST),
		ShapeUtil.box(2, 0, 2, 14, 2, 14));
	public static final VoxelShape RAYTRACE_SHAPE = Shapes.or(ShapeUtil.box(0, 2, 0, 16, 16, 16), ShapeUtil.box(2, 0, 2, 14, 2, 14));
	public static final VoxelShape COLLISION_SHAPE = Shapes.or(
		Shapes.join(ShapeUtil.box(0, 2, 0, 16, 13, 16), ShapeUtil.box(2, 5, 2, 14, 16, 14), BooleanOp.ONLY_FIRST),
		ShapeUtil.box(2, 0, 2, 14, 2, 14));

	public BasinBlock(Properties properties) {
		super(properties);
	}

	public static boolean isBasin(LevelReader world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof BasinBlockEntity;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BasinBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != ModBlockEntities.BASIN)
			return null;
		return (l, p, s, be) -> ((SmartBlockEntity) be).tick();
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
		return !(world.getBlockEntity(pos.above()) instanceof BasinOperatingBlockEntity);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (level.getBlockEntity(pos) instanceof BasinBlockEntity be)
			be.takeContents(player);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return RAYTRACE_SHAPE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return BLOCK_SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter reader, BlockPos pos, CollisionContext ctx) {
		if (ctx instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof ItemEntity)
			return COLLISION_SHAPE;
		return getShape(state, reader, pos, ctx);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity be))
			return 0;
		float fullness = 0;
		int slots = be.inputInventory.size();
		for (int i = 0; i < slots; i++) {
			var stack = be.inputInventory.get(i);
			if (!stack.isEmpty())
				fullness += stack.getCount() / (float) be.inputInventory.getSlotLimit(stack);
		}
		fullness /= slots;
		return net.minecraft.util.Mth.floor(fullness * 14) + (fullness > 0 ? 1 : 0);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	// ---- Polymer ----

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return Blocks.BARRIER.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return new Model(initialBlockState);
	}

	public static class Model extends BaseModel {
		public Model(BlockState state) {
			super(state);
			addPart(ModelItems.block("basin/block")).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}
}

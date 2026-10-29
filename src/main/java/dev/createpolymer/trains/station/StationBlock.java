package dev.createpolymer.trains.station;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.logistics.depot.SharedDepotBlockMethods;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StationBlock extends Block
	implements IBE<StationBlockEntity>, IWrenchable, ProperWaterloggedBlock, PolymerModelBlock {

	public static final BooleanProperty ASSEMBLING = BooleanProperty.create("assembling");

	/** Create's AllShapes.STATION */
	private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes
		.or(ShapeUtil.box(0, 0, 0, 16, 2, 16), ShapeUtil.box(1, 0, 1, 15, 13, 15));

	public StationBlock(Properties p_54120_) {
		super(p_54120_);
		registerDefaultState(defaultBlockState().setValue(ASSEMBLING, false)
			.setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(ASSEMBLING, WATERLOGGED));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		return withWater(super.getStateForPlacement(pContext), pContext);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		updateWater(level, ticks, pState, pCurrentPos);
		return pState;
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState pState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState pState, Level pLevel, BlockPos pPos, Direction direction) {
		return getBlockEntityOptional(pLevel, pPos).map(ste -> ste.trainPresent ? 15 : 0)
			.orElse(0);
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		super.fallOn(level, state, pos, entity, fallDistance);
		SharedDepotBlockMethods.onLanded(level, entity);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player == null || player.isShiftKeyDown())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (stack.is(ModItems.WRENCH))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		InteractionResult result = onBlockEntityUse(level, pos, station -> {
			ItemStack autoSchedule = station.getAutoSchedule();
			if (autoSchedule.isEmpty())
				return InteractionResult.PASS;
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			player.getInventory()
				.placeItemBackInInventory(autoSchedule.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
			station.notifyUpdate();
			return InteractionResult.SUCCESS;
		});

		if (result == InteractionResult.PASS && player instanceof ServerPlayer sp)
			withBlockEntityDo(level, pos, be -> StationGui.open(sp, be));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return SHAPE;
	}

	@Override
	public Class<StationBlockEntity> getBlockEntityClass() {
		return StationBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends StationBlockEntity> getBlockEntityType() {
		return TrainsRegistry.TRACK_STATION_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.POLISHED_ANDESITE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new StationModel(state);
	}
}

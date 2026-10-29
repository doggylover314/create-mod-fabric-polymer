package dev.createpolymer.logistics.packagerLink;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PackagerLinkBlock extends FaceAttachedHorizontalDirectionalBlock
	implements IBE<PackagerLinkBlockEntity>, ProperWaterloggedBlock, IWrenchable, PolymerModelBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	/** Create's AllShapes.STOCK_LINK */
	public static final Map<Direction, VoxelShape> STOCK_LINK = ShapeUtil.forDirectional(ShapeUtil.box(1, 0, 1, 15, 5, 15));

	public PackagerLinkBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false)
			.setValue(WATERLOGGED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		BlockState placed = super.getStateForPlacement(context);
		if (placed == null)
			return null;
		if (placed.getValue(FACE) == AttachFace.CEILING)
			placed = placed.setValue(FACING, placed.getValue(FACING)
				.getOpposite());
		return withWater(placed.setValue(POWERED, getPower(placed, context.getLevel(), pos) > 0), context);
	}

	public static Direction getConnectedDirection(BlockState state) {
		return FaceAttachedHorizontalDirectionalBlock.getConnectedDirection(state);
	}

	@Override
	protected boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		return true;
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pPos,
		Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		updateWater(level, ticks, pState, pPos);
		return pState;
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
		@Nullable Orientation orientation, boolean isMoving) {
		if (worldIn.isClientSide())
			return;

		int power = getPower(state, worldIn, pos);
		boolean powered = power > 0;
		boolean previouslyPowered = state.getValue(POWERED);
		if (previouslyPowered != powered)
			worldIn.setBlock(pos, state.cycle(POWERED), Block.UPDATE_CLIENTS);
		withBlockEntityDo(worldIn, pos, link -> link.behaviour.redstonePowerChanged(power));
	}

	public static int getPower(BlockState state, Level worldIn, BlockPos pos) {
		int power = 0;
		for (Direction d : Iterate.directions)
			if (d.getOpposite() != getConnectedDirection(state))
				power = Math.max(power, worldIn.getSignal(pos.relative(d), d));
		return power;
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer,
		ItemStack pStack) {
		super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
		withBlockEntityDo(pLevel, pPos, plbe -> {
			if (pPlacer instanceof Player player) {
				plbe.placedBy = player.getUUID();
				plbe.notifyUpdate();
			}
		});
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return STOCK_LINK.get(getConnectedDirection(pState));
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		return STOCK_LINK.get(getConnectedDirection(pState));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(POWERED, WATERLOGGED, FACE, FACING));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public Class<PackagerLinkBlockEntity> getBlockEntityClass() {
		return PackagerLinkBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PackagerLinkBlockEntity> getBlockEntityType() {
		return PackagesRegistry.PACKAGER_LINK_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.TERRACOTTA.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part body;

		public Model(BlockState state) {
			super(state);
			body = addPart(ModelItems.block("stock_link/block_vertical"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(body, "stock_link", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}

}

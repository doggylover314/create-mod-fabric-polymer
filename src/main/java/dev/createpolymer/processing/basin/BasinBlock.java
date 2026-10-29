package dev.createpolymer.processing.basin;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.fluids.FluidBox;
import dev.createpolymer.fluids.transfer.GenericItemEmptying;
import dev.createpolymer.fluids.transfer.GenericItemFilling;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import dev.createpolymer.foundation.fluid.FluidHelper;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.belt.BeltBlockEntity;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.logistics.funnel.FunnelBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.visual.Part;
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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's BasinBlock (MIT, The Create Team).
 */
public class BasinBlock extends Block implements EntityBlock, PolymerBlock, BlockWithElementHolder, IWrenchable {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING_HOPPER;

	public static final VoxelShape BLOCK_SHAPE = Shapes.or(
		Shapes.join(ShapeUtil.box(0, 2, 0, 16, 16, 16), ShapeUtil.box(2, 2, 2, 14, 16, 14), BooleanOp.ONLY_FIRST),
		ShapeUtil.box(2, 0, 2, 14, 2, 14));
	public static final VoxelShape RAYTRACE_SHAPE = Shapes.or(ShapeUtil.box(0, 2, 0, 16, 16, 16), ShapeUtil.box(2, 0, 2, 14, 2, 14));
	public static final VoxelShape COLLISION_SHAPE = Shapes.or(
		Shapes.join(ShapeUtil.box(0, 2, 0, 16, 13, 16), ShapeUtil.box(2, 5, 2, 14, 16, 14), BooleanOp.ONLY_FIRST),
		ShapeUtil.box(2, 0, 2, 14, 2, 14));

	public BasinBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.DOWN));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(FACING));
	}

	public static boolean isBasin(LevelReader world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof BasinBlockEntity;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (!context.getLevel().isClientSide() && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof BasinBlockEntity be)
			be.onWrenched(context.getClickedFace());
		return InteractionResult.SUCCESS;
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
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hitResult) {
		if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof BasinBlockEntity be))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, be))
			return InteractionResult.SUCCESS;
		if (FluidHelper.tryFillItemFromBE(level, player, hand, stack, be))
			return InteractionResult.SUCCESS;

		if (GenericItemEmptying.canItemBeEmptied(level, stack) || GenericItemFilling.canItemBeFilled(level, stack))
			return InteractionResult.SUCCESS;
		if (stack.getItem().equals(Items.SPONGE)) {
			IFluidHandler fluidHandler = be.getFluidCapability();
			if (fluidHandler != null) {
				FluidStack drained = fluidHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
				if (!drained.isEmpty())
					return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
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

	public static boolean canOutputTo(BlockGetter world, BlockPos basinPos, Direction direction) {
		BlockPos neighbour = basinPos.relative(direction);
		BlockPos output = neighbour.below();
		BlockState blockState = world.getBlockState(neighbour);

		if (FunnelBlock.isFunnel(blockState)) {
			if (FunnelBlock.getFunnelFacing(blockState) == direction)
				return false;
		} else if (!blockState.getCollisionShape(world, neighbour)
			.isEmpty()) {
			return false;
		} else {
			BlockEntity blockEntity = world.getBlockEntity(output);
			if (blockEntity instanceof BeltBlockEntity belt) {
				return belt.getSpeed() == 0 || belt.getMovementFacing() != direction.getOpposite();
			}
		}

		DirectBeltInputBehaviour directBeltInputBehaviour =
			BlockEntityBehaviour.get(world, output, DirectBeltInputBehaviour.TYPE);
		if (directBeltInputBehaviour != null)
			return directBeltInputBehaviour.canInsertFromSide(direction);
		return false;
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
	public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return true;
	}

	@Override
	public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
		return new Model(initialBlockState);
	}

	/** The basin, and the fluids in it (Create's BasinRenderer.renderFluids): a box for each tank that holds some. */
	public static class Model extends BaseModel {
		private final FluidBox[] fluids = new FluidBox[4];

		private final Part block;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("basin/block"));
			showVariant(block, "basin", state);
			for (int i = 0; i < fluids.length; i++)
				fluids[i] = new FluidBox(this);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "basin", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof BasinBlockEntity basin)
				updateFluids(basin);
		}

		private void updateFluids(BasinBlockEntity basin) {
			int box = 0;
			float totalUnits = basin.getTotalFluidUnits(1);
			if (totalUnits >= 1) {
				float fluidLevel = Mth.clamp(totalUnits / 2000, 0, 1);

				fluidLevel = 1 - ((1 - fluidLevel) * (1 - fluidLevel));

				float xMin = 2 / 16f;
				float xMax = 2 / 16f;
				final float yMin = 2 / 16f;
				final float yMax = yMin + 12 / 16f * fluidLevel;
				final float zMin = 2 / 16f;
				final float zMax = 14 / 16f;

				for (SmartFluidTankBehaviour behaviour : basin.getTanks()) {
					if (behaviour == null)
						continue;
					for (TankSegment tankSegment : behaviour.getTanks()) {
						FluidStack renderedFluid = tankSegment.getRenderedFluid();
						if (renderedFluid.isEmpty())
							continue;
						float units = tankSegment.getTotalUnits(1);
						if (units < 1)
							continue;

						float partial = Mth.clamp(units / totalUnits, 0, 1);
						xMax += partial * 12 / 16f;
						// block-centred coordinates
						fluids[box++].show(renderedFluid, xMin - .5f, yMin - .5f, zMin - .5f, xMax - .5f, yMax - .5f,
							zMax - .5f);

						xMin = xMax;
					}
				}
			}
			for (; box < fluids.length; box++)
				fluids[box].hide();
		}
	}
}

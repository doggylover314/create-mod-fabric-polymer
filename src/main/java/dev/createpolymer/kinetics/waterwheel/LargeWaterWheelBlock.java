package dev.createpolymer.kinetics.waterwheel;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Ported from Create's LargeWaterWheelBlock (MIT, The Create Team). The wheel is 5 blocks
 * across; the 3x3 ring around the centre is filled with invisible structure blocks.
 */
public class LargeWaterWheelBlock extends RotatedPillarKineticBlock implements IWrenchable {
	public static final BooleanProperty EXTENSION = BooleanProperty.create("extension");

	public LargeWaterWheelBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(EXTENSION, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(EXTENSION));
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return ModBlockEntities.LARGE_WATER_WHEEL;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState stateForPlacement = super.getStateForPlacement(context);
		BlockPos pos = context.getClickedPos();
		Axis axis = stateForPlacement.getValue(AXIS);

		for (int x = -1; x <= 1; x++)
			for (int y = -1; y <= 1; y++)
				for (int z = -1; z <= 1; z++) {
					if (axis.choose(x, y, z) != 0)
						continue;
					BlockPos offset = new BlockPos(x, y, z);
					if (offset.equals(BlockPos.ZERO))
						continue;
					if (!context.getLevel().getBlockState(pos.offset(offset)).canBeReplaced())
						return null;
				}

		if (context.getLevel().getBlockState(pos.relative(Direction.fromAxisAndDirection(axis, AxisDirection.NEGATIVE))).is(this))
			stateForPlacement = stateForPlacement.setValue(EXTENSION, true);

		return stateForPlacement;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		return InteractionResult.PASS;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
									 Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (direction != Direction.fromAxisAndDirection(state.getValue(AXIS), AxisDirection.NEGATIVE))
			return state;
		return state.setValue(EXTENSION, neighbourState.is(this));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		if (!level.getBlockTicks().hasScheduledTick(pos, this))
			level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		Axis axis = state.getValue(AXIS);
		for (Direction side : Direction.values()) {
			if (side.getAxis() == axis)
				continue;
			for (boolean secondary : new boolean[] { false, true }) {
				Direction targetSide = secondary ? side.getClockWise(axis) : side;
				BlockPos structurePos = (secondary ? pos.relative(side) : pos).relative(targetSide);
				BlockState occupiedState = level.getBlockState(structurePos);
				BlockState requiredStructure = ModBlocks.WATER_WHEEL_STRUCTURAL.defaultBlockState()
					.setValue(WaterWheelStructuralBlock.FACING, targetSide.getOpposite());
				if (occupiedState == requiredStructure)
					continue;
				if (!occupiedState.canBeReplaced()) {
					level.destroyBlock(pos, false);
					return;
				}
				level.setBlockAndUpdate(structurePos, requiredStructure);
			}
		}
		if (level.getBlockEntity(pos) instanceof WaterWheelBlockEntity be)
			be.determineAndApplyFlowScore();
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == getRotationAxis(state);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	@Override
	public boolean hideStressImpact() {
		return true;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.SPRUCE_PLANKS.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part wheel;

		public Model(BlockState state) {
			super(state);
			// The model is authored at half size (vanilla models must fit in -16..32) and scaled up.
			wheel = addPart(ModelItems.block("large_water_wheel/polymer_wheel"));
			wheel.element.setViewRange(3f);
			wheel.element.setDisplaySize(5, 5);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Axis axis = state.getValue(AXIS);
			wheel.orientation(Orientation.forAxis(axis)).spinAxis(axis).scale(2).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof KineticBlockEntity kbe) {
				Axis axis = state.getValue(AXIS);
				wheel.spin(time, kbe.getSpeed(), Orientation.cogOffset(true, axis, pos.getX(), pos.getY(), pos.getZ()));
			}
		}
	}
}

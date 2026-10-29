package dev.createpolymer.kinetics.transmission;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.RotationPropagator;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.registry.ModBlockEntities;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Ported from Create's GearshiftBlock and ClutchBlock (MIT, The Create Team). */
public class GearshiftBlock extends RotatedPillarKineticBlock {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	private final boolean clutch;

	public GearshiftBlock(Properties properties, boolean clutch) {
		super(properties);
		this.clutch = clutch;
		registerDefaultState(defaultBlockState().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return clutch ? ModBlockEntities.CLUTCH : ModBlockEntities.GEARSHIFT;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Axis axis;
		if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())
			axis = super.getStateForPlacement(context).getValue(AXIS);
		else {
			Axis preferredAxis = getPreferredAxis(context);
			axis = preferredAxis == null ? context.getNearestLookingDirection().getAxis() : preferredAxis;
		}
		return defaultBlockState().setValue(AXIS, axis).setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
								   net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		if (worldIn.isClientSide())
			return;
		boolean previouslyPowered = state.getValue(POWERED);
		if (previouslyPowered == worldIn.hasNeighborSignal(pos))
			return;
		if (clutch) {
			worldIn.setBlock(pos, state.cycle(POWERED), 2 | 16);
			detachKinetics(worldIn, pos, previouslyPowered);
		} else {
			detachKinetics(worldIn, pos, true);
			worldIn.setBlock(pos, state.cycle(POWERED), Block.UPDATE_CLIENTS);
		}
	}

	public void detachKinetics(Level worldIn, BlockPos pos, boolean reAttachNextTick) {
		if (!(worldIn.getBlockEntity(pos) instanceof KineticBlockEntity kbe))
			return;
		RotationPropagator.handleRemoved(worldIn, pos, kbe);
		if (reAttachNextTick)
			worldIn.scheduleTick(pos, this, 1, net.minecraft.world.ticks.TickPriority.EXTREMELY_HIGH);
	}

	@Override
	protected void tick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource random) {
		if (worldIn.getBlockEntity(pos) instanceof KineticBlockEntity kbe)
			RotationPropagator.handleAdded(worldIn, pos, kbe);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(AXIS);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	@Override
	protected BlockState particleState() {
		return Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, clutch ? "clutch" : "gearshift");
	}

	public static class Model extends BaseModel {
		private final String file;
		private final Part casing;
		private final Part positive;
		private final Part negative;

		public Model(BlockState state, String file) {
			super(state);
			this.file = file;
			casing = addPart(ModelItems.block("shaft"));
			positive = addPart(ModelItems.block("shaft_half"));
			negative = addPart(ModelItems.block("shaft_half"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(casing, file, state);
			Axis axis = state.getValue(AXIS);
			Direction pos = Direction.get(AxisDirection.POSITIVE, axis);
			positive.orientation(Orientation.fromSouth(pos)).spinAxis(axis).apply(0);
			negative.orientation(Orientation.fromSouth(pos.getOpposite())).spinAxis(axis).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof SplitShaftBlockEntity split))
				return;
			Axis axis = state.getValue(AXIS);
			float offset = Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ());
			Direction p = Direction.get(AxisDirection.POSITIVE, axis);
			positive.spin(time, split.getSpeed() * split.getRotationSpeedModifier(p), offset);
			negative.spin(time, split.getSpeed() * split.getRotationSpeedModifier(p.getOpposite()), offset);
		}
	}
}

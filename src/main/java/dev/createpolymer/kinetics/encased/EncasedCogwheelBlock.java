package dev.createpolymer.kinetics.encased;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.simple.CogWheelBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModSounds;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Ported from Create's EncasedCogwheelBlock (MIT, The Create Team).
 * Wrenching an end face toggles the shaft on that side; sneak-wrenching removes the casing.
 */
public class EncasedCogwheelBlock extends RotatedPillarKineticBlock implements ICogWheel, IWrenchable, EncasedBlock {
	public static final BooleanProperty TOP_SHAFT = BooleanProperty.create("top_shaft");
	public static final BooleanProperty BOTTOM_SHAFT = BooleanProperty.create("bottom_shaft");

	protected final boolean isLarge;
	private final Supplier<Block> casing;
	private final String blockstateFile;
	private final @Nullable Supplier<BlockEntityType<? extends KineticBlockEntity>> blockEntityType;

	public EncasedCogwheelBlock(Properties properties, boolean large, Supplier<Block> casing, String blockstateFile) {
		this(properties, large, casing, blockstateFile, null);
	}

	/** {@code blockEntityType}: null for the shared encased cogwheel block entities. */
	public EncasedCogwheelBlock(Properties properties, boolean large, Supplier<Block> casing, String blockstateFile,
								@Nullable Supplier<BlockEntityType<? extends KineticBlockEntity>> blockEntityType) {
		super(properties);
		this.isLarge = large;
		this.casing = casing;
		this.blockstateFile = blockstateFile;
		this.blockEntityType = blockEntityType;
		registerDefaultState(defaultBlockState().setValue(TOP_SHAFT, false).setValue(BOTTOM_SHAFT, false));
	}

	@Override
	public Block getCasing() {
		return casing.get();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(TOP_SHAFT, BOTTOM_SHAFT));
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		if (blockEntityType != null)
			return blockEntityType.get();
		return isLarge ? ModBlockEntities.ENCASED_LARGE_COGWHEEL : ModBlockEntities.ENCASED_COGWHEEL;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState placedOn = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
		BlockState stateForPlacement = super.getStateForPlacement(context);
		if (ICogWheel.isSmallCog(placedOn))
			stateForPlacement = stateForPlacement.setValue(AXIS, ((IRotate) placedOn.getBlock()).getRotationAxis(placedOn));
		return stateForPlacement;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (context.getClickedFace().getAxis() != state.getValue(AXIS))
			return null;
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		KineticBlockEntity.switchToBlockState(level, pos, state.cycle(
			context.getClickedFace().getAxisDirection() == AxisDirection.POSITIVE ? TOP_SHAFT : BOTTOM_SHAFT));
		ModSounds.wrenchRotate(level, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		context.getLevel().levelEvent(2001, context.getClickedPos(), Block.getId(state));
		KineticBlockEntity.switchToBlockState(context.getLevel(), context.getClickedPos(),
			(isLarge ? ModBlocks.LARGE_COGWHEEL : ModBlocks.COGWHEEL).defaultBlockState().setValue(AXIS, state.getValue(AXIS)));
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(AXIS)
			&& state.getValue(face.getAxisDirection() == AxisDirection.POSITIVE ? TOP_SHAFT : BOTTOM_SHAFT);
	}

	@Override
	protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
		if (newState.getBlock() instanceof EncasedCogwheelBlock && oldState.getBlock() instanceof EncasedCogwheelBlock) {
			if (newState.getValue(TOP_SHAFT) != oldState.getValue(TOP_SHAFT))
				return false;
			if (newState.getValue(BOTTOM_SHAFT) != oldState.getValue(BOTTOM_SHAFT))
				return false;
		}
		return super.areStatesKineticallyEquivalent(oldState, newState);
	}

	@Override
	public boolean isSmallCog() {
		return !isLarge;
	}

	@Override
	public boolean isLargeCog() {
		return isLarge;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
		return CogWheelBlock.isValidCogwheelPosition(ICogWheel.isLargeCog(state), worldIn, pos, state.getValue(AXIS));
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	/** State for encasing a cogwheel at {@code pos}: shafts go wherever a neighbour has one facing in. */
	@Override
	public BlockState encasedStateFor(BlockState cog, Level level, BlockPos pos) {
		BlockState encasedState = defaultBlockState().setValue(AXIS, cog.getValue(AXIS));
		for (Direction d : new Direction[] { Direction.get(AxisDirection.POSITIVE, cog.getValue(AXIS)), Direction.get(AxisDirection.NEGATIVE, cog.getValue(AXIS)) }) {
			BlockState adjacentState = level.getBlockState(pos.relative(d));
			if (!(adjacentState.getBlock() instanceof IRotate def))
				continue;
			if (!def.hasShaftTowards(level, pos.relative(d), adjacentState, d.getOpposite()))
				continue;
			encasedState = encasedState.cycle(d.getAxisDirection() == AxisDirection.POSITIVE ? TOP_SHAFT : BOTTOM_SHAFT);
		}
		return encasedState;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, blockstateFile, isLarge);
	}

	public static class Model extends BaseModel {
		private final String file;
		private final boolean large;
		private final Part casing;
		private final Part cog;
		private final Part top;
		private final Part bottom;

		public Model(BlockState state, String file, boolean large) {
			super(state);
			this.file = file;
			this.large = large;
			casing = addPart(ModelItems.block("shaft"));
			cog = addPart(ModelItems.block(large ? "large_cogwheel_shaftless" : "cogwheel_shaftless"));
			top = addPart(ModelItems.block("shaft_half"));
			bottom = addPart(ModelItems.block("shaft_half"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(casing, file, state);
			Axis axis = state.getValue(AXIS);
			cog.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
			Direction pos = Direction.get(AxisDirection.POSITIVE, axis);
			top.orientation(Orientation.fromSouth(pos)).spinAxis(axis).scale(state.getValue(TOP_SHAFT) ? 1 : 0).apply(0);
			bottom.orientation(Orientation.fromSouth(pos.getOpposite())).spinAxis(axis).scale(state.getValue(BOTTOM_SHAFT) ? 1 : 0).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof KineticBlockEntity kbe) {
				Axis axis = state.getValue(AXIS);
				float offset = Orientation.cogOffset(large, axis, pos.getX(), pos.getY(), pos.getZ());
				cog.spin(time, kbe.getSpeed(), offset);
				top.spin(time, kbe.getSpeed(), offset);
				bottom.spin(time, kbe.getSpeed(), offset);
			}
		}
	}
}

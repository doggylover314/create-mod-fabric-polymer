package dev.createpolymer.kinetics.gantry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.item.WrenchItem;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.visual.Orientation;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GantryShaftBlock extends DirectionalKineticBlock
	implements IBE<GantryShaftBlockEntity>, IWrenchable, ContraptionDisplay {

	public static final Property<Part> PART = EnumProperty.create("part", Part.class);
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	/** Create's AllShapes.EIGHT_VOXEL_POLE */
	private static final Map<Axis, VoxelShape> EIGHT_VOXEL_POLE = ShapeUtil.forAxis(ShapeUtil.box(4, 0, 4, 12, 16, 12));

	public enum Part implements StringRepresentable {
		START, MIDDLE, END, SINGLE;

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(PART, POWERED));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return EIGHT_VOXEL_POLE.get(state.getValue(FACING)
			.getAxis());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		Direction facing = state.getValue(FACING);
		Axis axis = facing.getAxis();
		if (direction.getAxis() != axis)
			return state;
		boolean connect = neighbour.getBlock() instanceof GantryShaftBlock && neighbour.getValue(FACING) == facing;

		Part part = state.getValue(PART);
		if (direction.getAxisDirection() == facing.getAxisDirection()) {
			if (connect) {
				if (part == Part.END)
					part = Part.MIDDLE;
				if (part == Part.SINGLE)
					part = Part.START;
			} else {
				if (part == Part.MIDDLE)
					part = Part.END;
				if (part == Part.START)
					part = Part.SINGLE;
			}
		} else {
			if (connect) {
				if (part == Part.START)
					part = Part.MIDDLE;
				if (part == Part.SINGLE)
					part = Part.END;
			} else {
				if (part == Part.MIDDLE)
					part = Part.START;
				if (part == Part.END)
					part = Part.SINGLE;
			}
		}

		return state.setValue(PART, part);
	}

	public GantryShaftBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false)
			.setValue(PART, Part.SINGLE));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		BlockPos pos = context.getClickedPos();
		Level world = context.getLevel();
		Direction face = context.getClickedFace();

		BlockState neighbour = world.getBlockState(pos.relative(state.getValue(FACING)
			.getOpposite()));

		BlockState clickedState =
			neighbour.getBlock() instanceof GantryShaftBlock ? neighbour : world.getBlockState(pos.relative(face.getOpposite()));

		if (clickedState.getBlock() instanceof GantryShaftBlock && clickedState.getValue(FACING)
			.getAxis() == state.getValue(FACING)
				.getAxis()) {
			Direction facing = clickedState.getValue(FACING);
			state = state.setValue(FACING, context.getPlayer() == null || !context.getPlayer()
				.isShiftKeyDown() ? facing : facing.getOpposite());
		}

		return state.setValue(POWERED, shouldBePowered(state, world, pos));
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState rotated = WrenchItem.getRotatedBlockState(state, context.getClickedFace());
		if (rotated == state || !rotated.canSurvive(world, pos))
			return InteractionResult.SUCCESS;
		KineticBlockEntity.switchToBlockState(world, pos, Block.updateFromNeighbourShapes(rotated, world, pos));
		ModSounds.wrenchRotate(world, pos);

		neighborChanged(world.getBlockState(pos), world, pos, state.getBlock(), null, false);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, worldIn, pos, oldState, isMoving);

		if (!worldIn.isClientSide() && oldState.is(this)) {
			Part oldPart = oldState.getValue(PART), part = state.getValue(PART);
			if ((oldPart != Part.MIDDLE && part == Part.MIDDLE) || (oldPart == Part.SINGLE && part != Part.SINGLE)) {
				BlockEntity be = worldIn.getBlockEntity(pos);
				if (be instanceof GantryShaftBlockEntity)
					((GantryShaftBlockEntity) be).checkAttachedCarriageBlocks();
			}
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block block,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		if (worldIn.isClientSide())
			return;
		boolean previouslyPowered = state.getValue(POWERED);
		boolean shouldPower = worldIn.hasNeighborSignal(pos); // shouldBePowered(state, worldIn, pos);

		if (!previouslyPowered && !shouldPower && shouldBePowered(state, worldIn, pos)) {
			worldIn.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
			return;
		}

		if (previouslyPowered == shouldPower)
			return;

		// Collect affected gantry shafts
		List<BlockPos> toUpdate = new ArrayList<>();
		Direction facing = state.getValue(FACING);
		Axis axis = facing.getAxis();
		for (Direction d : Iterate.directionsInAxis(axis)) {
			BlockPos currentPos = pos.relative(d);
			while (true) {
				if (!worldIn.isLoaded(currentPos))
					break;
				BlockState currentState = worldIn.getBlockState(currentPos);
				if (!(currentState.getBlock() instanceof GantryShaftBlock))
					break;
				if (currentState.getValue(FACING) != facing)
					break;
				if (!shouldPower && currentState.getValue(POWERED) && worldIn.hasNeighborSignal(currentPos))
					return;
				if (currentState.getValue(POWERED) == shouldPower)
					break;
				toUpdate.add(currentPos);
				currentPos = currentPos.relative(d);
			}
		}

		toUpdate.add(pos);
		for (BlockPos blockPos : toUpdate) {
			BlockState blockState = worldIn.getBlockState(blockPos);
			BlockEntity be = worldIn.getBlockEntity(blockPos);
			if (be instanceof KineticBlockEntity)
				((KineticBlockEntity) be).detachKinetics();
			if (blockState.getBlock() instanceof GantryShaftBlock)
				worldIn.setBlock(blockPos, blockState.setValue(POWERED, shouldPower), Block.UPDATE_CLIENTS);
		}
	}

	protected boolean shouldBePowered(BlockState state, Level worldIn, BlockPos pos) {
		boolean shouldPower = worldIn.hasNeighborSignal(pos);

		Direction facing = state.getValue(FACING);
		for (Direction d : Iterate.directionsInAxis(facing.getAxis())) {
			BlockPos neighbourPos = pos.relative(d);
			if (!worldIn.isLoaded(neighbourPos))
				continue;
			BlockState neighbourState = worldIn.getBlockState(neighbourPos);
			if (!(neighbourState.getBlock() instanceof GantryShaftBlock))
				continue;
			if (neighbourState.getValue(FACING) != facing)
				continue;
			shouldPower |= neighbourState.getValue(POWERED);
		}

		return shouldPower;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(FACING)
			.getAxis();
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING)
			.getAxis();
	}

	@Override
	protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
		return super.areStatesKineticallyEquivalent(oldState, newState)
			&& oldState.getValue(POWERED) == newState.getValue(POWERED);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public Class<GantryShaftBlockEntity> getBlockEntityClass() {
		return GantryShaftBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends GantryShaftBlockEntity> getBlockEntityType() {
		return PulleyRegistry.GANTRY_SHAFT_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.NETHER_BRICKS.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		BlockstateModels.Variant variant = BlockstateModels.get("gantry_shaft", state);
		return variant == null ? List.of() : List.of(new Piece(variant.stack(), variant.rotation()));
	}

	/** Create's OrientedRotatingVisual.gantryShaft: the rack turns around its own axis. */
	public static class Model extends BaseModel {
		private final dev.createpolymer.visual.Part rack;

		public Model(BlockState state) {
			super(state);
			rack = addPart(ItemStack.EMPTY);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(rack, "gantry_shaft", state);
			rack.spinAxis(state.getValue(FACING)
				.getAxis())
				.apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof GantryShaftBlockEntity shaft))
				return;
			Axis axis = state.getValue(FACING)
				.getAxis();
			rack.spin(time, shaft.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
		}
	}

}

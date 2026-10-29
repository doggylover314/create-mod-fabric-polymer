package dev.createpolymer.decoration.girder;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.HorizontalAxisKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.registry.feature.DoorsRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GirderEncasedShaftBlock extends HorizontalAxisKineticBlock
	implements IBE<KineticBlockEntity>, SimpleWaterloggedBlock, IWrenchable, ContraptionDisplay {

	public static final BooleanProperty TOP = GirderBlock.TOP;
	public static final BooleanProperty BOTTOM = GirderBlock.BOTTOM;

	public GirderEncasedShaftBlock(Properties properties) {
		super(properties);
		registerDefaultState(super.defaultBlockState()
			.setValue(WATERLOGGED, false)
			.setValue(TOP, false)
			.setValue(BOTTOM, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(TOP, BOTTOM, WATERLOGGED));
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return GirderShapes.GIRDER_BEAM_SHAFT.get(pState.getValue(HORIZONTAL_AXIS));
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
		CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState pState, BlockGetter pReader, BlockPos pPos) {
		return Shapes.or(super.getBlockSupportShape(pState, pReader, pPos), GirderShapes.EIGHT_VOXEL_POLE.get(Axis.Y));
	}

	/** Create's IWrenchable.getRotatedBlockState: wrenching takes the shaft out of the girder. */
	public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
		return DoorsRegistry.METAL_GIRDER.defaultBlockState()
			.setValue(WATERLOGGED, originalState.getValue(WATERLOGGED))
			.setValue(GirderBlock.X, originalState.getValue(HORIZONTAL_AXIS) == Axis.Z)
			.setValue(GirderBlock.Z, originalState.getValue(HORIZONTAL_AXIS) == Axis.X)
			.setValue(GirderBlock.AXIS, originalState.getValue(HORIZONTAL_AXIS) == Axis.X ? Axis.Z : Axis.X)
			.setValue(GirderBlock.BOTTOM, originalState.getValue(BOTTOM))
			.setValue(GirderBlock.TOP, originalState.getValue(TOP));
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState rotated = getRotatedBlockState(state, context.getClickedFace());
		if (!rotated.canSurvive(level, pos))
			return InteractionResult.PASS;
		KineticBlockEntity.switchToBlockState(level, pos, Block.updateFromNeighbourShapes(rotated, level, pos));
		ModSounds.wrenchRotate(level, pos);
		Player player = context.getPlayer();
		if (player != null && !player.isCreative())
			player.getInventory()
				.placeItemBackInInventory(new ItemStack(ModBlocks.SHAFT), Prediction.SERVER_ONLY);
		return InteractionResult.SUCCESS;
	}

	@Override
	public Class<KineticBlockEntity> getBlockEntityClass() {
		return KineticBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return DoorsRegistry.GIRDER_ENCASED_SHAFT_BLOCK_ENTITY;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));

		Property<Boolean> updateProperty = direction == Direction.UP ? TOP : BOTTOM;
		if (direction.getAxis()
			.isVertical()) {
			if (world.getBlockState(pos.relative(direction))
				.getBlockSupportShape(world, pos.relative(direction))
				.isEmpty())
				state = state.setValue(updateProperty, false);
			return GirderBlock.updateVerticalProperty(world, pos, state, updateProperty, neighbourState, direction);
		}

		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		FluidState ifluidstate = level.getFluidState(pos);
		BlockState state = super.getStateForPlacement(context);
		return state.setValue(WATERLOGGED, Boolean.valueOf(ifluidstate.getType() == Fluids.WATER));
	}

	// ---- Polymer ----

	/** The girder and its shaft, standing still. */
	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		Axis axis = state.getValue(HORIZONTAL_AXIS);
		List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece(ModelItems.block("metal_girder_encased_shaft/block"),
			axis == Axis.Z ? new Quaternionf() : Orientation.variant(0, 90)));
		if (state.getValue(TOP))
			pieces.add(new Piece(ModelItems.block("metal_girder_encased_shaft/block_top")));
		if (state.getValue(BOTTOM))
			pieces.add(new Piece(ModelItems.block("metal_girder_encased_shaft/block_bottom")));
		pieces.add(new Piece(ModelItems.block("shaft"), Orientation.forAxis(axis)));
		return pieces;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.IRON_BLOCK.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's blockstate (the girder with a casing in its middle, with caps) and the shaft that spins in it. */
	public static class Model extends BaseModel {
		private final GirderModelParts parts = new GirderModelParts(this);
		private final Part shaft;
		private boolean dirty = true;

		public Model(BlockState state) {
			super(state);
			shaft = addPart(ModelItems.block("shaft"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Axis axis = state.getValue(HORIZONTAL_AXIS);
			parts.set("base", "metal_girder_encased_shaft/block",
				axis == Axis.Z ? new Quaternionf() : Orientation.variant(0, 90));
			parts.set("top", state.getValue(TOP) ? "metal_girder_encased_shaft/block_top" : null, new Quaternionf());
			parts.set("bottom", state.getValue(BOTTOM) ? "metal_girder_encased_shaft/block_bottom" : null,
				new Quaternionf());
			shaft.orientation(Orientation.forAxis(axis))
				.spinAxis(axis)
				.apply(0);
			dirty = true;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (be instanceof KineticBlockEntity kbe) {
				Axis axis = state.getValue(HORIZONTAL_AXIS);
				shaft.spin(time, kbe.getSpeed(),
					Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			}
			if (!dirty && time % 10 != 0)
				return;
			dirty = false;
			parts.updateBrackets(level, pos, state);
		}
	}

}

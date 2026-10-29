package dev.createpolymer.decoration.girder;

import static net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.decoration.bracket.BracketedBlockEntityBehaviour;
import dev.createpolymer.decoration.placard.PlacardBlock;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.logistics.chute.AbstractChuteBlock;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlock;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.DoorsRegistry;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.trains.display.FlapDisplayBlock;
import dev.createpolymer.trains.track.TrackBlock;
import dev.createpolymer.trains.track.TrackShape;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GirderBlock extends Block
	implements SimpleWaterloggedBlock, IWrenchable, PolymerModelBlock, ContraptionDisplay {

	private static final GirderPlacementHelper PLACEMENT_HELPER = new GirderPlacementHelper();

	public static final BooleanProperty X = BooleanProperty.create("x");
	public static final BooleanProperty Z = BooleanProperty.create("z");
	public static final BooleanProperty TOP = BooleanProperty.create("top");
	public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
	public static final EnumProperty<Axis> AXIS = BlockStateProperties.AXIS;

	public GirderBlock(Properties p_49795_) {
		super(p_49795_);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false)
			.setValue(AXIS, Axis.Y)
			.setValue(TOP, false)
			.setValue(BOTTOM, false)
			.setValue(X, false)
			.setValue(Z, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(X, Z, TOP, BOTTOM, AXIS, WATERLOGGED));
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState pState, BlockGetter pReader, BlockPos pPos) {
		return Shapes.or(super.getBlockSupportShape(pState, pReader, pPos), GirderShapes.EIGHT_VOXEL_POLE.get(Axis.Y));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (stack.is(ModItems.SHAFT)) {
			KineticBlockEntity.switchToBlockState(level, pos, DoorsRegistry.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState()
				.setValue(WATERLOGGED, state.getValue(WATERLOGGED))
				.setValue(TOP, state.getValue(TOP))
				.setValue(BOTTOM, state.getValue(BOTTOM))
				.setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS, state.getValue(X) || hitResult.getDirection()
					.getAxis() == Axis.Z ? Axis.Z : Axis.X));

			level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.BLOCKS, 0.5f, 1.25f);
			if (!level.isClientSide() && !player.isCreative()) {
				stack.shrink(1);
				if (stack.isEmpty())
					player.setItemInHand(hand, ItemStack.EMPTY);
			}

			return InteractionResult.SUCCESS;
		}

		if (stack.is(ModItems.WRENCH) && !player.isShiftKeyDown()) {
			if (GirderWrenchBehavior.handleClick(level, pos, state, hitResult))
				return InteractionResult.SUCCESS;
			return InteractionResult.FAIL;
		}

		if (PLACEMENT_HELPER.matchesItem(stack))
			return PLACEMENT_HELPER.place(player, level, state, pos, hitResult, hand, (BlockItem) stack.getItem());

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		return InteractionResult.PASS;
	}

	@Override
	protected void tick(BlockState p_60462_, ServerLevel p_60463_, BlockPos p_60464_, RandomSource p_60465_) {
		Block.updateOrDestroy(p_60462_, Block.updateFromNeighbourShapes(p_60462_, p_60463_, p_60464_), p_60463_,
			p_60464_, 3);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
		Axis axis = direction.getAxis();

		if (direction.getAxis() != Axis.Y) {
			if (state.getValue(AXIS) != direction.getAxis()) {
				Property<Boolean> updateProperty =
					axis == Axis.X ? X : axis == Axis.Z ? Z : direction == Direction.UP ? TOP : BOTTOM;
				if (!isConnected(world, pos, state, direction)
					&& !isConnected(world, pos, state, direction.getOpposite()))
					state = state.setValue(updateProperty, false);
			}
		} else if (state.getValue(AXIS) != Axis.Y) {
			if (world.getBlockState(pos.above())
				.getBlockSupportShape(world, pos.above())
				.isEmpty())
				state = state.setValue(TOP, false);
			if (world.getBlockState(pos.below())
				.getBlockSupportShape(world, pos.below())
				.isEmpty())
				state = state.setValue(BOTTOM, false);
		}

		for (Direction d : Iterate.directionsInAxis(axis))
			state = updateState(world, pos, state, d);

		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction face = context.getClickedFace();
		FluidState ifluidstate = level.getFluidState(pos);
		BlockState state = super.getStateForPlacement(context);
		state = state.setValue(X, face.getAxis() == Axis.X);
		state = state.setValue(Z, face.getAxis() == Axis.Z);
		state = state.setValue(AXIS, face.getAxis());

		for (Direction d : Iterate.directions)
			state = updateState(level, pos, state, d);

		return state.setValue(WATERLOGGED, ifluidstate.getType() == Fluids.WATER);
	}

	public static BlockState updateState(LevelReader level, BlockPos pos, BlockState state, Direction d) {
		Axis axis = d.getAxis();
		Property<Boolean> updateProperty = axis == Axis.X ? X : axis == Axis.Z ? Z : d == Direction.UP ? TOP : BOTTOM;
		BlockState sideState = level.getBlockState(pos.relative(d));

		if (axis.isVertical())
			return updateVerticalProperty(level, pos, state, updateProperty, sideState, d);

		if (state.getValue(AXIS) == axis)
			state = state.setValue(updateProperty, true);
		else if (sideState.getBlock() instanceof GirderEncasedShaftBlock
			&& sideState.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS) != axis)
			state = state.setValue(updateProperty, true);
		else if (sideState.getBlock() == state.getBlock() && sideState.getValue(updateProperty))
			state = state.setValue(updateProperty, true);
		else if (sideState.getBlock() instanceof NixieTubeBlock && NixieTubeBlock.getFacing(sideState) == d)
			state = state.setValue(updateProperty, true);
		else if (sideState.getBlock() instanceof PlacardBlock && PlacardBlock.connectedDirection(sideState) == d)
			state = state.setValue(updateProperty, true);
		else if (isFacingBracket(level, pos, d))
			state = state.setValue(updateProperty, true);

		for (Direction d2 : Iterate.directionsInAxis(axis == Axis.X ? Axis.Z : Axis.X)) {
			BlockState above = level.getBlockState(pos.above()
				.relative(d2));
			if (TrackRegistry.isTrack(above)) {
				TrackShape shape = above.getValue(TrackBlock.SHAPE);
				if (shape == (axis == Axis.X ? TrackShape.XO : TrackShape.ZO))
					state = state.setValue(updateProperty, true);
			}
		}

		return state;
	}

	public static boolean isFacingBracket(BlockGetter level, BlockPos pos, Direction d) {
		BlockEntity blockEntity = level.getBlockEntity(pos.relative(d));
		if (!(blockEntity instanceof SmartBlockEntity sbe))
			return false;
		BracketedBlockEntityBehaviour behaviour = sbe.getBehaviour(BracketedBlockEntityBehaviour.TYPE);
		if (behaviour == null)
			return false;
		BlockState bracket = behaviour.getBracket();
		if (bracket == null || !bracket.hasProperty(dev.createpolymer.decoration.bracket.BracketBlock.FACING))
			return false;
		return bracket.getValue(dev.createpolymer.decoration.bracket.BracketBlock.FACING) == d;
	}

	public static BlockState updateVerticalProperty(LevelReader level, BlockPos pos, BlockState state,
		Property<Boolean> updateProperty, BlockState sideState, Direction d) {
		boolean canAttach = false;

		if (state.hasProperty(AXIS) && state.getValue(AXIS) == Axis.Y)
			canAttach = true;
		else if (isGirder(sideState) && isXGirder(sideState) == isZGirder(sideState))
			canAttach = true;
		else if (isGirder(sideState))
			canAttach = true;
		else if (sideState.hasProperty(WallBlock.UP) && sideState.getValue(WallBlock.UP))
			canAttach = true;
		else if (sideState.getBlock() instanceof NixieTubeBlock && NixieTubeBlock.getFacing(sideState) == d)
			canAttach = true;
		else if (sideState.getBlock() instanceof FlapDisplayBlock)
			canAttach = true;
		else if (sideState.getBlock() instanceof LanternBlock
			&& (d == Direction.DOWN) == (sideState.getValue(LanternBlock.HANGING)))
			canAttach = true;
		else if (sideState.getBlock() instanceof ChainBlock && sideState.getValue(ChainBlock.AXIS) == Axis.Y)
			canAttach = true;
		else if (sideState.hasProperty(FACE)) {
			if (sideState.getValue(FACE) == AttachFace.CEILING && d == Direction.DOWN)
				canAttach = true;
			else if (sideState.getValue(FACE) == AttachFace.FLOOR && d == Direction.UP)
				canAttach = true;
		} else if (sideState.getBlock() instanceof PlacardBlock && PlacardBlock.connectedDirection(sideState) == d)
			canAttach = true;
		else if (isFacingBracket(level, pos, d))
			canAttach = true;

		if (canAttach)
			return state.setValue(updateProperty, true);
		return state;
	}

	public static boolean isGirder(BlockState state) {
		return state.getBlock() instanceof GirderBlock || state.getBlock() instanceof GirderEncasedShaftBlock;
	}

	public static boolean isXGirder(BlockState state) {
		return (state.getBlock() instanceof GirderBlock && state.getValue(X))
			|| (state.getBlock() instanceof GirderEncasedShaftBlock
				&& state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS) == Axis.Z);
	}

	public static boolean isZGirder(BlockState state) {
		return (state.getBlock() instanceof GirderBlock && state.getValue(Z))
			|| (state.getBlock() instanceof GirderEncasedShaftBlock
				&& state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS) == Axis.X);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		boolean x = state.getValue(GirderBlock.X);
		boolean z = state.getValue(GirderBlock.Z);
		return x ? z ? GirderShapes.GIRDER_CROSS : GirderShapes.GIRDER_BEAM.get(Axis.X)
			: z ? GirderShapes.GIRDER_BEAM.get(Axis.Z) : GirderShapes.EIGHT_VOXEL_POLE.get(Axis.Y);
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
		CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	public static boolean isConnected(BlockGetter world, BlockPos pos, BlockState state, Direction side) {
		Axis axis = side.getAxis();
		if (state.getBlock() instanceof GirderBlock && !state.getValue(axis == Axis.X ? X : Z))
			return false;
		if (state.getBlock() instanceof GirderEncasedShaftBlock
			&& state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS) == axis)
			return false;
		BlockPos relative = pos.relative(side);
		BlockState blockState = world.getBlockState(relative);
		if (blockState.isAir())
			return false;
		if (blockState.getBlock() instanceof NixieTubeBlock && NixieTubeBlock.getFacing(blockState) == side)
			return true;
		if (isFacingBracket(world, pos, side))
			return true;
		if (blockState.getBlock() instanceof PlacardBlock && PlacardBlock.connectedDirection(blockState) == side)
			return true;
		VoxelShape shape = blockState.getShape(world, relative);
		if (shape.isEmpty())
			return false;
		if (Block.isFaceFull(shape, side.getOpposite()) && blockState.isSolid())
			return true;
		return AbstractChuteBlock.getChuteFacing(blockState) == Direction.DOWN;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		state = state.setValue(AXIS,
			rot.rotate(Direction.fromAxisAndDirection(state.getValue(AXIS), AxisDirection.POSITIVE))
				.getAxis());
		if (rot.rotate(Direction.EAST)
			.getAxis() == Axis.X)
			return state;
		// Create swaps nothing here (both axes get Z), so every quarter turn turned a beam into a pole
		return state.setValue(X, state.getValue(Z))
			.setValue(Z, state.getValue(X));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.IRON_BLOCK.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** The multipart of Create's blockstate; contraptions cannot see what a girder holds on to, so no brackets. */
	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		boolean x = state.getValue(X);
		boolean z = state.getValue(Z);
		List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece(ModelItems.block("metal_girder/" + (x ? z ? "block_cross" : "block_x" : z ? "block_z" : "block_pole"))));
		if (x != z && state.getValue(TOP))
			pieces.add(new Piece(ModelItems.block("metal_girder/block_top")));
		if (x != z && state.getValue(BOTTOM))
			pieces.add(new Piece(ModelItems.block("metal_girder/block_bottom")));
		return pieces;
	}

	/**
	 * Create's blockstate (a pole, a beam along x or z, or a cross, with caps on the beam's top and bottom) plus the
	 * brackets of ConnectedGirderModel. The connected textures of GirderCTBehaviour are not shown.
	 */
	public static class Model extends BaseModel {
		private final GirderModelParts parts = new GirderModelParts(this);
		private boolean dirty = true;

		public Model(BlockState state) {
			super(state);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			boolean x = state.getValue(X);
			boolean z = state.getValue(Z);
			parts.set("base", "metal_girder/" + (x ? z ? "block_cross" : "block_x" : z ? "block_z" : "block_pole"),
				new Quaternionf());
			boolean cap = x != z;
			parts.set("top", cap && state.getValue(TOP) ? "metal_girder/block_top" : null, new Quaternionf());
			parts.set("bottom", cap && state.getValue(BOTTOM) ? "metal_girder/block_bottom" : null, new Quaternionf());
			dirty = true;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!dirty && time % 10 != 0)
				return;
			dirty = false;
			parts.updateBrackets(level, pos, state);
		}
	}

}

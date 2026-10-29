package dev.createpolymer.contraptions.bearing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.piston.WrenchableDirectionalBlock;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.registry.feature.BearingRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's SailBlock (MIT, The Create Team): the sail frame and the canvas sails. Players see them as a
 * display model; the barrier they stand on is a full block, a thin block's shape cannot be sent to a vanilla client.
 */
public class SailBlock extends WrenchableDirectionalBlock implements PolymerModelBlock, ContraptionDisplay {

	/** Create's AllShapes.SAIL_FRAME, SAIL_FRAME_COLLISION and SAIL (authored facing up). */
	private static final VoxelShape SAIL_FRAME_UP = ShapeUtil.box(0, 5, 0, 16, 9, 16);
	public static final Map<Direction, VoxelShape> SAIL_FRAME = ShapeUtil.forDirectional(SAIL_FRAME_UP);
	public static final Map<Direction, VoxelShape> SAIL_FRAME_COLLISION = ShapeUtil
		.forDirectional(Shapes.join(SAIL_FRAME_UP, ShapeUtil.box(2, 0, 2, 14, 16, 14), net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST)
			.optimize());
	public static final Map<Direction, VoxelShape> SAIL = ShapeUtil.forDirectional(ShapeUtil.box(0, 5, 0, 16, 10, 16));

	public static SailBlock frame(Properties properties) {
		return new SailBlock(properties, true, null);
	}

	public static SailBlock withCanvas(Properties properties, DyeColor color) {
		return new SailBlock(properties, false, color);
	}

	protected final boolean frame;
	protected final DyeColor color;

	protected SailBlock(Properties properties, boolean frame, DyeColor color) {
		super(properties);
		this.frame = frame;
		this.color = color;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state.setValue(FACING, state.getValue(FACING)
			.getOpposite());
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		// Create's placement helper: a sail or frame in hand continues the sail sideways
		if (!player.isShiftKeyDown() && player.mayBuild()) {
			if (stack.is(BearingRegistry.SAIL_ITEM) || stack.is(BearingRegistry.SAIL_FRAME_ITEM)) {
				List<Direction> directions = orderedByDistanceExceptAxis(pos, hitResult.getLocation(),
					state.getValue(FACING)
						.getAxis(),
					dir -> level.getBlockState(pos.relative(dir))
						.canBeReplaced());
				if (!directions.isEmpty()) {
					if (level.isClientSide())
						return InteractionResult.SUCCESS;
					BlockPos target = pos.relative(directions.get(0));
					BlockState placed = ((BlockItem) stack.getItem()).getBlock()
						.defaultBlockState()
						.setValue(FACING, state.getValue(FACING));
					level.setBlockAndUpdate(target, placed);
					level.playSound(null, target, placed.getSoundType()
						.getPlaceSound(), SoundSource.BLOCKS, 1, 1);
					if (!player.isCreative())
						stack.shrink(1);
				}
				return InteractionResult.SUCCESS;
			}
		}

		if (stack.is(Items.SHEARS) || stack.is(ConventionalItemTags.SHEAR_TOOLS)) {
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0f, 1.0f);
			applyDye(state, level, pos, hitResult.getLocation(), null);
			return InteractionResult.SUCCESS;
		}

		if (frame)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		DyeColor color = stack.get(DataComponents.DYE);
		if (color != null) {
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f,
				1.1f - level.getRandom().nextFloat() * .2f);
			applyDye(state, level, pos, hitResult.getLocation(), color);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/**
	 * The horizontal(ish) neighbours of a block, nearest to the hit location first, leaving out the given axis. Create
	 * keeps this in catnip's IPlacementHelper.
	 */
	public static List<Direction> orderedByDistanceExceptAxis(BlockPos pos, Vec3 hit, Axis axis,
		Predicate<Direction> filter) {
		Vec3 center = Vec3.atCenterOf(pos);
		List<Direction> directions = new ArrayList<>();
		for (Direction d : Iterate.directions)
			if (d.getAxis() != axis && filter.test(d))
				directions.add(d);
		directions.sort(Comparator.comparingDouble(
			d -> center.add(Vec3.atLowerCornerOf(d.getUnitVec3i()).scale(.5)).distanceToSqr(hit)));
		return directions;
	}

	public void applyDye(BlockState state, Level world, BlockPos pos, Vec3 hit, @Nullable DyeColor color) {
		BlockState newState = (color == null ? BearingRegistry.SAIL_FRAME : BearingRegistry.DYED_SAILS.get(color))
			.defaultBlockState();
		newState = copyProperties(state, newState);

		// Dye the block itself
		if (state != newState) {
			world.setBlockAndUpdate(pos, newState);
			return;
		}

		// Dye all adjacent
		List<Direction> directions = orderedByDistanceExceptAxis(pos, hit, state.getValue(FACING)
			.getAxis(), d -> true);
		for (Direction d : directions) {
			BlockPos offset = pos.relative(d);
			BlockState adjacentState = world.getBlockState(offset);
			Block block = adjacentState.getBlock();
			if (!(block instanceof SailBlock) || ((SailBlock) block).frame)
				continue;
			if (state.getValue(FACING) != adjacentState.getValue(FACING))
				continue;
			if (state == adjacentState)
				continue;
			world.setBlockAndUpdate(offset, newState);
			return;
		}

		// Dye all the things
		List<BlockPos> frontier = new ArrayList<>();
		frontier.add(pos);
		Set<BlockPos> visited = new HashSet<>();
		int timeout = 100;
		while (!frontier.isEmpty()) {
			if (timeout-- < 0)
				break;

			BlockPos currentPos = frontier.remove(0);
			visited.add(currentPos);

			for (Direction d : Iterate.directions) {
				if (d.getAxis() == state.getValue(FACING)
					.getAxis())
					continue;
				BlockPos offset = currentPos.relative(d);
				if (visited.contains(offset))
					continue;
				BlockState adjacentState = world.getBlockState(offset);
				Block block = adjacentState.getBlock();
				if (!(block instanceof SailBlock) || ((SailBlock) block).frame && color != null)
					continue;
				if (adjacentState.getValue(FACING) != state.getValue(FACING))
					continue;
				if (state != adjacentState)
					world.setBlockAndUpdate(offset, newState);
				frontier.add(offset);
				visited.add(offset);
			}
		}
	}

	/** Create's BlockHelper.copyProperties. */
	private static BlockState copyProperties(BlockState fromState, BlockState toState) {
		for (Property<?> property : fromState.getProperties())
			toState = copyProperty(property, fromState, toState);
		return toState;
	}

	private static <T extends Comparable<T>> BlockState copyProperty(Property<T> property, BlockState fromState,
		BlockState toState) {
		return toState.hasProperty(property) ? toState.setValue(property, fromState.getValue(property)) : toState;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (frame ? SAIL_FRAME : SAIL).get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
		CollisionContext context) {
		if (frame)
			return SAIL_FRAME_COLLISION.get(state.getValue(FACING));
		return getShape(state, level, pos, context);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	public boolean isFrame() {
		return frame;
	}

	public DyeColor getColor() {
		return color;
	}

	/** The name of Create's model for this sail. */
	public String modelName() {
		return frame ? "sail_frame" : color.getSerializedName() + "_sail";
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, modelName());
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		return List.of(new Piece(ModelItems.block(modelName()), Orientation.forFacing(state.getValue(FACING))));
	}

	public static class Model extends BaseModel {
		public Model(BlockState state, String model) {
			super(state);
			addPart(ModelItems.block(model)).orientation(Orientation.forFacing(state.getValue(FACING))).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}
}

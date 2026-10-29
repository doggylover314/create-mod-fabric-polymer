package dev.createpolymer.trains.bogey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.api.schematic.requirement.SpecialBlockItemRequirement;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.entity.Carriage;
import dev.createpolymer.trains.entity.CarriageBogey;
import dev.createpolymer.trains.entity.TravellingPoint;
import dev.createpolymer.trains.graph.TrackEdge;
import dev.createpolymer.trains.track.TrackMaterial;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.context.UseOnContext;

public abstract class AbstractBogeyBlock<T extends AbstractBogeyBlockEntity> extends Block
	implements SpecialBlockItemRequirement, IBE<T>, ProperWaterloggedBlock, IWrenchable, PolymerModelBlock, ContraptionDisplay {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	static final List<Identifier> BOGEYS = new ArrayList<>();
	public BogeySizes.BogeySize size;

	public AbstractBogeyBlock(Properties pProperties, BogeySizes.BogeySize size) {
		super(pProperties);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
		this.size = size;
	}

	public boolean isOnIncompatibleTrack(Carriage carriage, boolean leading) {
		TravellingPoint point = leading ? carriage.getLeadingPoint() : carriage.getTrailingPoint();
		CarriageBogey bogey = leading ? carriage.leadingBogey() : carriage.trailingBogey();
		TrackEdge currentEdge = point.edge;
		if (currentEdge == null)
			return false;
		return currentEdge.getTrackMaterial().trackType != getTrackType(bogey.getStyle());
	}

	public Set<TrackMaterial.TrackType> getValidPathfindingTypes(BogeyStyle style) {
		return Set.of(getTrackType(style));
	}

	public abstract TrackMaterial.TrackType getTrackType(BogeyStyle style);

	/** Only for internal use. If you have your own style set, do not call this method */
	public static void registerStandardBogey(Identifier block) {
		BOGEYS.add(block);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, WATERLOGGED);
		super.createBlockStateDefinition(builder);
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

	static final EnumSet<Direction> STICKY_X = EnumSet.of(Direction.EAST, Direction.WEST);
	static final EnumSet<Direction> STICKY_Z = EnumSet.of(Direction.SOUTH, Direction.NORTH);

	public EnumSet<Direction> getStickySurfaces(BlockGetter world, BlockPos pos, BlockState state) {
		return state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X ? STICKY_X : STICKY_Z;
	}

	public abstract double getWheelPointSpacing();

	public abstract double getWheelRadius();

	public Vec3 getConnectorAnchorOffset(boolean upsideDown) {
		return getConnectorAnchorOffset();
	}

	/** This should be implemented, but not called directly */
	protected abstract Vec3 getConnectorAnchorOffset();

	public boolean allowsSingleBogeyCarriage() {
		return true;
	}

	public abstract BogeyStyle getDefaultStyle();

	/** Legacy system doesn't capture bogey block entities when constructing a train */
	public boolean captureBlockEntityForTrain() {
		return false;
	}

	public BogeySizes.BogeySize getSize() {
		return this.size;
	}

	public Direction getBogeyUpDirection() {
		return Direction.UP;
	}

	public boolean isTrackAxisAlongFirstCoordinate(BlockState state) {
		return state.getValue(AXIS) == Direction.Axis.X;
	}

	@Nullable
	public BlockState getMatchingBogey(Direction upDirection, boolean axisAlongFirst) {
		if (upDirection != Direction.UP)
			return null;
		return defaultBlockState().setValue(AXIS, axisAlongFirst ? Direction.Axis.X : Direction.Axis.Z);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide())
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		if (!player.isShiftKeyDown() && stack.is(ModItems.WRENCH) && !player.getCooldowns()
			.isOnCooldown(stack) && AllBogeyStyles.BOGEY_STYLES.size() > 1) {

			BlockEntity be = level.getBlockEntity(pos);

			if (!(be instanceof AbstractBogeyBlockEntity sbbe))
				return InteractionResult.FAIL;

			player.getCooldowns()
				.addCooldown(stack, 20);
			BogeyStyle currentStyle = sbbe.getStyle();

			BogeySizes.BogeySize size = getSize();

			BogeyStyle style = this.getNextStyle(currentStyle);
			if (style == currentStyle)
				return InteractionResult.TRY_WITH_EMPTY_HAND;

			Set<BogeySizes.BogeySize> validSizes = style.validSizes();

			for (int i = 0; i < BogeySizes.all()
				.size(); i++) {
				if (validSizes.contains(size))
					break;
				size = size.nextBySize();
			}

			sbbe.setBogeyStyle(style);

			CompoundTag defaultData = style.defaultData;
			sbbe.setBogeyData(sbbe.getBogeyData()
				.copy()
				.merge(defaultData));

			if (size == getSize()) {
				if (state.getBlock() != style.getBlockForSize(size)) {
					CompoundTag oldData = sbbe.getBogeyData();
					level.setBlock(pos, copyProperties(state, getStateOfSize(sbbe, size)), Block.UPDATE_ALL);
					if (!(level.getBlockEntity(pos) instanceof AbstractBogeyBlockEntity bogeyBlockEntity))
						return InteractionResult.FAIL;
					bogeyBlockEntity.setBogeyData(oldData);
				}
				player.sendOverlayMessage(TrackNbt.translateDirect("bogey.style.updated_style")
					.append(": ")
					.append(style.displayName));
			} else {
				CompoundTag oldData = sbbe.getBogeyData();
				level.setBlock(pos, this.getStateOfSize(sbbe, size), Block.UPDATE_ALL);
				if (!(level.getBlockEntity(pos) instanceof AbstractBogeyBlockEntity bogeyBlockEntity))
					return InteractionResult.FAIL;
				bogeyBlockEntity.setBogeyData(oldData);
				player.sendOverlayMessage(TrackNbt.translateDirect("bogey.style.updated_style_and_size")
					.append(": ")
					.append(style.displayName));
			}

			return InteractionResult.CONSUME;
		}

		return onInteractWithBogey(state, level, pos, player, hand, hitResult);
	}

	// Allows for custom interactions with bogey block to be added simply
	protected InteractionResult onInteractWithBogey(BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/**
	 * If, instead of using the style-based cycling system you prefer to use separate blocks, return them from this
	 * method
	 */
	protected List<Identifier> getBogeyBlockCycle() {
		return BOGEYS;
	}

	/** The wrench cycles through the bogey blocks (Create's IWrenchable.getRotatedBlockState). */
	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState rotated = getRotatedBlockState(state, context.getClickedFace());
		if (rotated == state || !rotated.canSurvive(level, pos))
			return InteractionResult.SUCCESS;
		CompoundTag oldData = level.getBlockEntity(pos) instanceof AbstractBogeyBlockEntity old ? old.getBogeyData() : null;
		KineticBlockEntity.switchToBlockState(level, pos, Block.updateFromNeighbourShapes(rotated, level, pos));
		if (oldData != null && level.getBlockEntity(pos) instanceof AbstractBogeyBlockEntity newBE)
			newBE.setBogeyData(oldData);
		return InteractionResult.SUCCESS;
	}

	public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
		Block block = state.getBlock();
		List<Identifier> bogeyCycle = getBogeyBlockCycle();
		int indexOf = bogeyCycle.indexOf(BuiltInRegistries.BLOCK.getKey(block));
		if (indexOf == -1)
			return state;
		int index = (indexOf + 1) % bogeyCycle.size();
		Direction bogeyUpDirection = getBogeyUpDirection();
		boolean trackAxisAlongFirstCoordinate = isTrackAxisAlongFirstCoordinate(state);

		while (index != indexOf) {
			Identifier id = bogeyCycle.get(index);
			Block newBlock = BuiltInRegistries.BLOCK.getValue(id);
			if (newBlock instanceof AbstractBogeyBlock<?> bogey) {
				BlockState matchingBogey = bogey.getMatchingBogey(bogeyUpDirection, trackAxisAlongFirstCoordinate);
				if (matchingBogey != null)
					return copyProperties(state, matchingBogey);
			}
			index = (index + 1) % bogeyCycle.size();
		}

		return state;
	}

	public BlockState getNextSize(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof AbstractBogeyBlockEntity sbbe)
			return this.getNextSize(sbbe);
		return level.getBlockState(pos);
	}

	/** List of BlockState Properties to copy between sizes */
	public List<Property<?>> propertiesToCopy() {
		return ImmutableList.of(WATERLOGGED, AXIS);
	}

	// generic method needed to satisfy Property and BlockState's generic requirements
	private <V extends Comparable<V>> BlockState copyProperty(BlockState source, BlockState target, Property<V> property) {
		if (source.hasProperty(property) && target.hasProperty(property)) {
			return target.setValue(property, source.getValue(property));
		}
		return target;
	}

	private BlockState copyProperties(BlockState source, BlockState target) {
		for (Property<?> property : propertiesToCopy())
			target = copyProperty(source, target, property);
		return target;
	}

	public BlockState getNextSize(AbstractBogeyBlockEntity sbbe) {
		BogeySizes.BogeySize size = this.getSize();
		BogeyStyle style = sbbe.getStyle();
		BlockState nextBlock = style.getNextBlock(size)
			.defaultBlockState();
		nextBlock = copyProperties(sbbe.getBlockState(), nextBlock);
		return nextBlock;
	}

	public BlockState getStateOfSize(AbstractBogeyBlockEntity sbbe, BogeySizes.BogeySize size) {
		BogeyStyle style = sbbe.getStyle();
		BlockState state = style.getBlockForSize(size)
			.defaultBlockState();
		return copyProperties(sbbe.getBlockState(), state);
	}

	public BogeyStyle getNextStyle(Level level, BlockPos pos) {
		BlockEntity te = level.getBlockEntity(pos);
		if (te instanceof AbstractBogeyBlockEntity sbbe)
			return this.getNextStyle(sbbe.getStyle());
		return getDefaultStyle();
	}

	public BogeyStyle getNextStyle(BogeyStyle style) {
		Collection<BogeyStyle> allStyles = style.getCycleGroup()
			.values();
		if (allStyles.size() <= 1)
			return style;
		List<BogeyStyle> list = new ArrayList<>(allStyles);
		return Iterate.cycleValue(list, style);
	}

	@Override
	protected @NotNull BlockState rotate(@NotNull BlockState pState, Rotation pRotation) {
		return switch (pRotation) {
		case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> pState.cycle(AXIS);
		default -> pState;
		};
	}

	public boolean canBeUpsideDown() {
		return false;
	}

	public boolean isUpsideDown(BlockState state) {
		return false;
	}

	public BlockState getVersion(BlockState base, boolean upsideDown) {
		return base;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.POLISHED_ANDESITE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** On a train the wheels and frame are drawn by the carriage; the contraption only keeps the top plate. */
	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		return List.of(new Piece(ModelItems.block("track/bogey/top"), topOrientation(state)));
	}

	protected static org.joml.Quaternionf topOrientation(BlockState state) {
		return Orientation.variant(0, state.getValue(AXIS) == Direction.Axis.X ? 90 : 0);
	}

	/** Create's bogey block renderer: the top plate, and the bogey itself below the block at wheel angle zero. */
	public static class Model extends BaseModel {
		private final Part top;
		private BogeyStyle shownStyle;
		private BogeySizes.BogeySize shownSize;
		private BogeyVisual visual;

		public Model(BlockState state) {
			super(state);
			top = addPart(ModelItems.block("track/bogey/top"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			top.orientation(topOrientation(state))
				.apply(0);
			shownStyle = null;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(state.getBlock() instanceof AbstractBogeyBlock<?> bogey))
				return;
			BogeyStyle style = be instanceof AbstractBogeyBlockEntity bbe ? bbe.getStyle() : bogey.getDefaultStyle();
			if (style == shownStyle && bogey.getSize() == shownSize)
				return;
			shownStyle = style;
			shownSize = bogey.getSize();
			if (visual != null)
				for (ItemDisplayElement element : visual.elements())
					removeElement(element);
			visual = style.createVisual(shownSize);
			if (visual == null)
				return;
			for (ItemDisplayElement element : visual.elements())
				addElement(element);
			org.joml.Matrix4f base = new org.joml.Matrix4f()
				.rotateY(state.getValue(AXIS) == Direction.Axis.X ? (float) Math.PI / 2 : 0)
				.translate(0, -1.5f - 1 / 128f, 0);
			visual.update(new CompoundTag(), 0, base, 0);
		}
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity te) {
		return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, new ItemStack(TrainsRegistry.RAILWAY_CASING));
	}
}

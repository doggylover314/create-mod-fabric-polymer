package dev.createpolymer.redstone.nixieTube;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import dev.createpolymer.api.schematic.requirement.SpecialBlockItemRequirement;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.redstone.RedstoneWrenchable;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.schematics.requirement.ItemRequirement.ItemUseType;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import dev.createpolymer.equipment.clipboard.ClipboardBlockItem;
import dev.createpolymer.equipment.clipboard.ClipboardEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NixieTubeBlock extends DoubleFaceAttachedBlock
	implements SpecialBlockItemRequirement, IBE<NixieTubeBlockEntity>, RedstoneWrenchable, SimpleWaterloggedBlock, PolymerModelBlock {

	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	/** Create's AllShapes.NIXIE_TUBE, NIXIE_TUBE_CEILING and NIXIE_TUBE_WALL */
	private static final VoxelShaper NIXIE_TUBE = VoxelShaper.forHorizontalAxis(
		Shapes.or(Block.box(9, 0, 5, 15, 12, 11), Block.box(1, 0, 5, 7, 12, 11)), Direction.Axis.Z);
	private static final VoxelShaper NIXIE_TUBE_CEILING = VoxelShaper.forHorizontalAxis(
		Shapes.or(Block.box(9, 4, 5, 15, 16, 11), Block.box(1, 4, 5, 7, 16, 11)), Direction.Axis.Z);
	private static final VoxelShaper NIXIE_TUBE_WALL = VoxelShaper.forHorizontal(
		Shapes.or(Block.box(5, 9, 0, 11, 15, 12), Block.box(5, 1, 0, 11, 7, 12)), Direction.SOUTH);

	protected final DyeColor color;

	public NixieTubeBlock(Properties properties, DyeColor color) {
		super(properties);
		this.color = color;
		registerDefaultState(defaultBlockState().setValue(FACE, DoubleAttachFace.FLOOR)
			.setValue(WATERLOGGED, false));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player.isShiftKeyDown())
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		NixieTubeBlockEntity nixie = getBlockEntity(level, pos);

		if (nixie == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		if (stack.isEmpty()) {
			if (nixie.reactsToRedstone())
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			nixie.clearCustomText();
			updateDisplayedRedstoneValue(state, level, pos);
			return InteractionResult.SUCCESS;
		}

		boolean display = stack.getItem() == Items.NAME_TAG && stack.has(DataComponents.CUSTOM_NAME)
			|| stack.getItem() instanceof ClipboardBlockItem;
		DyeColor dye = stack.get(DataComponents.DYE);

		if (!display && dye == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		Component customName = stack.getOrDefault(DataComponents.CUSTOM_NAME, Component.empty());

		if (stack.getItem() instanceof ClipboardBlockItem) {
			List<ClipboardEntry> entries = ClipboardEntry.getLastViewedEntries(stack);
			if (!entries.isEmpty())
				customName = entries.getFirst().text;
		}
		Component component = customName;

		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		walkNixies(level, pos, (currentPos, rowPosition) -> {
			if (display)
				withBlockEntityDo(level, currentPos, be -> be.displayCustomText(component, rowPosition));
			if (dye != null)
				level.setBlockAndUpdate(currentPos, withColor(state, dye));
		});

		return InteractionResult.SUCCESS;
	}

	public static Direction getLeftNixieDirection(@NotNull BlockState state) {
		Direction left = state.getValue(FACING).getOpposite();
		if (state.getValue(FACE) == DoubleAttachFace.WALL)
			left = Direction.UP;
		if (state.getValue(FACE) == DoubleAttachFace.WALL_REVERSED)
			left = Direction.DOWN;
		return left;
	}

	public static Direction getRightNixieDirection(@NotNull BlockState state) {
		return getLeftNixieDirection(state).getOpposite();
	}

	/**
	 * Walk down a nixie tube row and execute a callback on each tube in said row.
	 * @param world The world the tubes are in.
	 * @param start Start position for the walk.
	 * @param callback Callback to run for each tube.
	 * @return True if the row was walked.
	 */
	public static boolean walkNixies(@NotNull LevelAccessor world, @NotNull BlockPos start,
									 @Nullable BiConsumer<BlockPos, Integer> callback) {
		BlockState state = world.getBlockState(start);
		if (!(state.getBlock() instanceof NixieTubeBlock))
			return false;

		BlockPos currentPos = start;
		Direction left = getLeftNixieDirection(state);
		Direction right = left.getOpposite();

		while (true) {
			BlockPos nextPos = currentPos.relative(left);
			if (!areNixieBlocksEqual(world.getBlockState(nextPos), state))
				break;
			currentPos = nextPos;
		}

		int index = 0;

		while (true) {
			final int rowPosition = index;
			if (callback != null)
				callback.accept(currentPos, rowPosition);
			BlockPos nextPos = currentPos.relative(right);
			if (!areNixieBlocksEqual(world.getBlockState(nextPos), state))
				break;
			currentPos = nextPos;
			index++;
		}

		return true;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(FACE, FACING, WATERLOGGED));
	}

	/** Dyeing a tube replaces the block by one of another colour, the block entity (and its text) stays. */
	@Override
	protected boolean shouldChangedStateKeepBlockEntity(BlockState oldState) {
		return oldState.getBlock() instanceof NixieTubeBlock;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(RedstoneRegistry.ORANGE_NIXIE_TUBE_ITEM);
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		Direction facing = pState.getValue(FACING);
		return switch (pState.getValue(FACE)) {
			case CEILING -> NIXIE_TUBE_CEILING.get(facing.getClockWise().getAxis());
			case FLOOR -> NIXIE_TUBE.get(facing.getClockWise().getAxis());
			default -> NIXIE_TUBE_WALL.get(facing);
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return getShape(pState, pLevel, pPos, pContext);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		if (state == null)
			return null;
		if (state.getValue(FACE) != DoubleAttachFace.WALL && state.getValue(FACE) != DoubleAttachFace.WALL_REVERSED)
			state = state.setValue(FACING, state.getValue(FACING)
				.getClockWise());
		return state.setValue(WATERLOGGED, Boolean.valueOf(context.getLevel()
			.getFluidState(context.getClickedPos())
			.getType() == Fluids.WATER));
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
		@Nullable Orientation orientation, boolean isMoving) {
		if (level.isClientSide())
			return;
		if (!level.getBlockTicks()
			.willTickThisTick(pos, this))
			level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void tick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource r) {
		updateDisplayedRedstoneValue(state, worldIn, pos);
	}

	@Override
	protected void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
		if (state.getBlock() == oldState.getBlock() || isMoving || oldState.getBlock() instanceof NixieTubeBlock)
			return;
		updateDisplayedRedstoneValue(state, worldIn, pos);
	}

	public static void updateDisplayedRedstoneValue(NixieTubeBlockEntity be, BlockState state, boolean force) {
		if (be.getLevel() == null || be.getLevel().isClientSide())
			return;
		if (be.reactsToRedstone() || force)
			be.updateRedstoneStrength(getPower(be.getLevel(), state, be.getBlockPos()));
	}

	private void updateDisplayedRedstoneValue(BlockState state, Level level, BlockPos pos) {
		if (level.isClientSide())
			return;
		withBlockEntityDo(level, pos, be -> NixieTubeBlock.updateDisplayedRedstoneValue(be, state, false));
	}

	private static int getPower(Level level, BlockState state, BlockPos pos) {
		int power = 0;
		for (Direction direction : Iterate.directions)
			power = Math.max(level.getSignal(pos.relative(direction), direction), power);
		for (Direction direction : Iterate.directions) {
			if (state.getValue(FACING).getOpposite() != direction)
				power = Math.max(level.getSignal(pos.relative(direction), Direction.UP), power);
		}
		return power;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction side) {
		return side != null;
	}

	@Override
	public Class<NixieTubeBlockEntity> getBlockEntityClass() {
		return NixieTubeBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends NixieTubeBlockEntity> getBlockEntityType() {
		return RedstoneRegistry.NIXIE_TUBE_BLOCK_ENTITY;
	}

	public DyeColor getColor() {
		return color;
	}

	public static boolean areNixieBlocksEqual(BlockState blockState, BlockState otherState) {
		if (!(blockState.getBlock() instanceof NixieTubeBlock))
			return false;
		if (!(otherState.getBlock() instanceof NixieTubeBlock))
			return false;
		return withColor(blockState, DyeColor.WHITE) == withColor(otherState, DyeColor.WHITE);
	}

	public static BlockState withColor(BlockState state, DyeColor color) {
		return RedstoneRegistry.NIXIE_TUBES.get(color)
			.defaultBlockState()
			.setValue(FACING, state.getValue(FACING))
			.setValue(WATERLOGGED, state.getValue(WATERLOGGED))
			.setValue(FACE, state.getValue(FACE));
	}

	public static DyeColor colorOf(BlockState blockState) {
		return blockState.getBlock() instanceof NixieTubeBlock ? ((NixieTubeBlock) blockState.getBlock()).color
			: DyeColor.ORANGE;
	}

	public static Direction getFacing(BlockState sideState) {
		return getConnectedDirection(sideState);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.GOLD_BLOCK.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's front (bright) and back (dark) colours of each dye, from its DyeHelper. */
	private static final Map<DyeColor, int[]> DYE_COLORS = new EnumMap<>(DyeColor.class);
	static {
		DYE_COLORS.put(DyeColor.BLACK, new int[] { 0x45403B, 0x21201F });
		DYE_COLORS.put(DyeColor.RED, new int[] { 0xB13937, 0x632737 });
		DYE_COLORS.put(DyeColor.GREEN, new int[] { 0x208A46, 0x1D6045 });
		DYE_COLORS.put(DyeColor.BROWN, new int[] { 0xAC855C, 0x68533E });
		DYE_COLORS.put(DyeColor.BLUE, new int[] { 0x5391E1, 0x504B90 });
		DYE_COLORS.put(DyeColor.GRAY, new int[] { 0x5D666F, 0x313538 });
		DYE_COLORS.put(DyeColor.LIGHT_GRAY, new int[] { 0x95969B, 0x707070 });
		DYE_COLORS.put(DyeColor.PURPLE, new int[] { 0x9F54AE, 0x63366C });
		DYE_COLORS.put(DyeColor.CYAN, new int[] { 0x3EABB4, 0x3C7872 });
		DYE_COLORS.put(DyeColor.PINK, new int[] { 0xD5A8CB, 0xB86B95 });
		DYE_COLORS.put(DyeColor.LIME, new int[] { 0xA3DF55, 0x4FB16F });
		DYE_COLORS.put(DyeColor.YELLOW, new int[] { 0xE6D756, 0xE9AC29 });
		DYE_COLORS.put(DyeColor.LIGHT_BLUE, new int[] { 0x69CED2, 0x508AA5 });
		DYE_COLORS.put(DyeColor.ORANGE, new int[] { 0xEE9246, 0xD94927 });
		DYE_COLORS.put(DyeColor.MAGENTA, new int[] { 0xF062B0, 0xC04488 });
		DYE_COLORS.put(DyeColor.WHITE, new int[] { 0xEDEAE5, 0xBBB6B0 });
	}

	/**
	 * Create's nixie tube model with the digits of its renderer: each of the two tubes has a bright digit for the
	 * viewer in front and a dark one, turned around, for the viewer behind it.
	 */
	public static class Model extends BaseModel {
		private static final float TEXT_SCALE = 2f; // Create's digits are 1/20 of a block per pixel, text displays 1/40
		private static final float TEXT_CENTER_SHIFT = .05f; // text displays centre their text one pixel to the right

		private final Part block;
		private final TextDisplayElement[] front = new TextDisplayElement[2];
		private final TextDisplayElement[] back = new TextDisplayElement[2];
		private String shownFirst = null;
		private String shownSecond = null;
		private BlockState shownState = null;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("nixie_tube/block"));
			showVariant(block, "nixie_tube", state);
			for (int i = 0; i < 2; i++) {
				front[i] = text();
				back[i] = text();
			}
		}

		private TextDisplayElement text() {
			TextDisplayElement text = new TextDisplayElement(Component.empty());
			text.setBackground(0);
			text.setShadow(false);
			text.setBrightness(Brightness.FULL_BRIGHT);
			text.setViewRange(1f);
			text.setTeleportDuration(0);
			text.setInterpolationDuration(0);
			addElement(text);
			return text;
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "nixie_tube", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof NixieTubeBlockEntity nixie))
				return;
			String first = nixie.getDisplayedStrings().getFirst();
			String second = nixie.getDisplayedStrings().getSecond();
			if (first.equals(shownFirst) && second.equals(shownSecond) && state == shownState)
				return;
			shownFirst = first;
			shownSecond = second;
			shownState = state;
			show(0, first);
			show(1, second);
		}

		private void show(int tube, String character) {
			DyeColor dye = colorOf(state);
			int[] colors = DYE_COLORS.getOrDefault(dye, DYE_COLORS.get(DyeColor.WHITE));
			DoubleAttachFace face = state.getValue(FACE);
			float yRot = dev.createpolymer.util.AngleHelper.horizontalAngle(state.getValue(FACING)) - 90
				+ (face == DoubleAttachFace.WALL_REVERSED ? 180 : 0);
			float xRot = face == DoubleAttachFace.WALL ? -90 : face == DoubleAttachFace.WALL_REVERSED ? 90 : 0;

			float height = face == DoubleAttachFace.CEILING ? 5 : 3;
			float x = tube == 0 ? -4 / 16f : 4 / 16f;
			float y = height / 20f - 9 * .025f * TEXT_SCALE;

			Matrix4f base = new Matrix4f()
				.rotateY((float) Math.toRadians(yRot))
				.rotateZ((float) Math.toRadians(xRot))
				.translate(x, y, 0);

			boolean empty = character.isBlank();

			front[tube].setText(empty ? Component.empty() : Component.literal(character).withColor(colors[0]));
			front[tube].setTransformation(new Matrix4f(base)
				.translate(-TEXT_CENTER_SHIFT, 0, .01f)
				.scale(TEXT_SCALE));

			back[tube].setText(empty ? Component.empty() : Component.literal(character).withColor(colors[1]));
			back[tube].setTransformation(new Matrix4f(base)
				.translate(TEXT_CENTER_SHIFT, 0, -.01f)
				.rotateY((float) Math.PI)
				.scale(TEXT_SCALE));
		}
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity be) {
		return new ItemRequirement(ItemUseType.CONSUME, RedstoneRegistry.ORANGE_NIXIE_TUBE.asItem());
	}
}

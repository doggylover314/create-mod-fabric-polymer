package dev.createpolymer.trains.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.DisplayRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.component.DataComponents;
import dev.createpolymer.equipment.clipboard.ClipboardBlockItem;
import dev.createpolymer.equipment.clipboard.ClipboardEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Create's display board: a flap display that grows into a board by placing more of them next to it. */
public class FlapDisplayBlock extends HorizontalKineticBlock
	implements IBE<FlapDisplayBlockEntity>, ICogWheel, SimpleWaterloggedBlock {

	public static final BooleanProperty UP = BooleanProperty.create("up");
	public static final BooleanProperty DOWN = BooleanProperty.create("down");

	/** Create's AllShapes.FLAP_DISPLAY: a slab 10 pixels thick, in the middle of the block. */
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
		Direction.NORTH, ShapeUtil.box(0, 0, 3, 16, 16, 13),
		Direction.SOUTH, ShapeUtil.box(0, 0, 3, 16, 16, 13),
		Direction.EAST, ShapeUtil.box(3, 0, 0, 13, 16, 16),
		Direction.WEST, ShapeUtil.box(3, 0, 0, 13, 16, 16));

	public FlapDisplayBlock(Properties p_49795_) {
		super(p_49795_);
		registerDefaultState(defaultBlockState().setValue(UP, false)
			.setValue(DOWN, false)
			.setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
		return super.areStatesKineticallyEquivalent(oldState, newState);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(HORIZONTAL_FACING)
			.getAxis();
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(UP, DOWN, BlockStateProperties.WATERLOGGED));
	}

	@Override
	public SpeedLevel getMinimumRequiredSpeedLevel() {
		return SpeedLevel.MEDIUM;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		BlockPos clickedPos = context.getClickedPos();
		BlockPos placedOnPos = clickedPos.relative(face.getOpposite());
		Level level = context.getLevel();
		BlockState blockState = level.getBlockState(placedOnPos);
		BlockState stateForPlacement = defaultBlockState();
		FluidState ifluidstate = context.getLevel()
			.getFluidState(context.getClickedPos());

		if ((blockState.getBlock() != this) || (context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown()))
			stateForPlacement = super.getStateForPlacement(context);
		else {
			Direction otherFacing = blockState.getValue(HORIZONTAL_FACING);
			stateForPlacement = stateForPlacement.setValue(HORIZONTAL_FACING, otherFacing);
		}

		return updateColumn(level, clickedPos,
			stateForPlacement.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(ifluidstate.getType() == Fluids.WATER)), true);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player.isShiftKeyDown())
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		FlapDisplayBlockEntity flapBE = getBlockEntity(level, pos);

		if (flapBE == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		flapBE = flapBE.getController();
		if (flapBE == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		double yCoord = hitResult.getLocation()
			.add(Vec3.atLowerCornerOf(hitResult.getDirection()
				.getOpposite()
				.getUnitVec3i())
				.scale(.125f)).y;

		int lineIndex = flapBE.getLineIndexAt(yCoord);

		if (stack.isEmpty()) {
			if (!flapBE.isSpeedRequirementFulfilled())
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			flapBE.applyTextManually(lineIndex, null);
			return InteractionResult.SUCCESS;
		}

		if (stack.getItem() == Items.GLOW_INK_SAC) {
			if (!level.isClientSide()) {
				level.playSound(null, pos, SoundEvents.INK_SAC_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				flapBE.setGlowing(lineIndex);
			}
			return InteractionResult.SUCCESS;
		}

		boolean display = stack.getItem() == Items.NAME_TAG && stack.has(DataComponents.CUSTOM_NAME)
			|| stack.getItem() instanceof ClipboardBlockItem;
		DyeColor dye = stack.get(DataComponents.DYE);

		if (!display && dye == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (dye == null && !flapBE.isSpeedRequirementFulfilled())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		Component customName = stack.get(DataComponents.CUSTOM_NAME);

		if (display) {
			if (stack.getItem() instanceof ClipboardBlockItem) {
				List<ClipboardEntry> entries = ClipboardEntry.getLastViewedEntries(stack);
				int line = lineIndex;
				for (ClipboardEntry entry : entries) {
					for (String string : entry.text.getString()
						.split("\n")) {
						flapBE.applyTextManually(line++, Component.literal(string));
					}
				}
				return InteractionResult.SUCCESS;
			}

			flapBE.applyTextManually(lineIndex, customName);
		}
		if (dye != null) {
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
			flapBE.setColour(lineIndex, dye);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return SHAPES.get(pState.getValue(HORIZONTAL_FACING));
	}

	@Override
	public Class<FlapDisplayBlockEntity> getBlockEntityClass() {
		return FlapDisplayBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FlapDisplayBlockEntity> getBlockEntityType() {
		return DisplayRegistry.FLAP_DISPLAY_BLOCK_ENTITY;
	}

	private BlockState updateColumn(Level level, BlockPos pos, BlockState state, boolean present) {
		MutableBlockPos currentPos = new MutableBlockPos();
		Axis axis = getConnectionAxis(state);

		for (Direction connection : Iterate.directionsInAxis(Axis.Y)) {
			boolean connect = true;

			Move:
			for (Direction movement : Iterate.directionsInAxis(axis)) {
				currentPos.set(pos);
				for (int i = 0; i < 1000; i++) {
					if (!level.isLoaded(currentPos))
						break;

					BlockState other1 = currentPos.equals(pos) ? state : level.getBlockState(currentPos);
					BlockState other2 = level.getBlockState(currentPos.relative(connection));
					boolean col1 = canConnect(state, other1);
					boolean col2 = canConnect(state, other2);
					currentPos.move(movement);

					if (!col1 && !col2)
						break;
					if (col1 && col2)
						continue;

					connect = false;
					break Move;
				}
			}
			state = setConnection(state, connection, connect);
		}
		return state;
	}

	@Override
	protected void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
		super.onPlace(pState, pLevel, pPos, pOldState, pIsMoving);
		if (pOldState.getBlock() == this)
			return;
		if (!pLevel.getBlockTicks()
			.hasScheduledTick(pPos, this))
			pLevel.scheduleTick(pPos, this, 1);
	}

	@Override
	protected void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
		if (pState.getBlock() != this)
			return;
		BlockPos belowPos =
			pPos.relative(Direction.fromAxisAndDirection(getConnectionAxis(pState), AxisDirection.NEGATIVE));
		BlockState belowState = pLevel.getBlockState(belowPos);
		if (!canConnect(pState, belowState))
			KineticBlockEntity.switchToBlockState(pLevel, pPos, updateColumn(pLevel, pPos, pState, true));
		withBlockEntityDo(pLevel, pPos, FlapDisplayBlockEntity::updateControllerStatus);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction pDirection, BlockPos neighbourPos, BlockState pNeighborState, RandomSource random) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		if (!canConnect(state, pNeighborState))
			return setConnection(state, pDirection, false);
		if (pDirection.getAxis() == getConnectionAxis(state))
			return withPropertiesOf(pNeighborState).setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
		return setConnection(state, pDirection, getConnection(pNeighborState, pDirection.getOpposite()));
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false)
			: Fluids.EMPTY.defaultFluidState();
	}

	protected boolean canConnect(BlockState state, BlockState other) {
		return other.getBlock() == this && state.getValue(HORIZONTAL_FACING) == other.getValue(HORIZONTAL_FACING);
	}

	protected Axis getConnectionAxis(BlockState state) {
		return state.getValue(HORIZONTAL_FACING)
			.getClockWise()
			.getAxis();
	}

	public static boolean getConnection(BlockState state, Direction side) {
		BooleanProperty property = side == Direction.DOWN ? DOWN : side == Direction.UP ? UP : null;
		return property != null && state.getValue(property);
	}

	public static BlockState setConnection(BlockState state, Direction side, boolean connect) {
		BooleanProperty property = side == Direction.DOWN ? DOWN : side == Direction.UP ? UP : null;
		if (property != null)
			state = state.setValue(property, connect);
		return state;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState pState, ServerLevel pLevel, BlockPos pPos, boolean pIsMoving) {
		super.affectNeighborsAfterRemoval(pState, pLevel, pPos, pIsMoving);
		if (pIsMoving)
			return;
		for (Direction d : Iterate.directionsInAxis(getConnectionAxis(pState))) {
			BlockPos relative = pPos.relative(d);
			BlockState adjacent = pLevel.getBlockState(relative);
			if (canConnect(pState, adjacent))
				KineticBlockEntity.switchToBlockState(pLevel, relative,
					updateColumn(pLevel, relative, adjacent, false));
		}
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.IRON_BLOCK.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * The board block and its cog, and for the controller of a board the text of every line (Create's
	 * FlapDisplayRenderer): each flap of a section is a text display of its own, put where the flap would be, and
	 * flaps that are spinning show the characters that go by. Nothing is drawn for a blank flap.
	 */
	public static class Model extends BaseModel {
		/** One pixel of Create's board is 1/32 of a block, the pixel of a text display 1/40. */
		private static final float TEXT_SCALE = 1.25f;
		private static final float PIXEL = 1 / 32f;
		private static final float TEXT_DISTANCE = 0.33f;

		private final Part block;
		private final Part cog;
		private final List<Line> shownLines = new ArrayList<>();
		private int shownXSize = -1;
		private boolean wasController;

		private static class Cell {
			final TextDisplayElement element = new TextDisplayElement(Component.empty());
			final FlapDisplaySection section;
			final int flap;
			final Matrix4f transformation;
			boolean attached;
			String shown = "";
			int shownColor = -1;
			boolean shownGlow;

			Cell(FlapDisplaySection section, int flap, Matrix4f transformation) {
				this.section = section;
				this.flap = flap;
				this.transformation = transformation;
				element.setBackground(0);
				element.setShadow(false);
				element.setViewRange(1f);
				element.setTeleportDuration(0);
				element.setInterpolationDuration(0);
				element.setTransformation(transformation);
			}
		}

		private static class Line {
			final String shape;
			final List<Cell> cells = new ArrayList<>();

			Line(String shape) {
				this.shape = shape;
			}
		}

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("display_board/block"));
			cog = addPart(ModelItems.block("cogwheel_shaftless"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "display_board", state);
			Axis axis = state.getValue(HORIZONTAL_FACING).getAxis();
			cog.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
			clearText();
		}

		private void clearText() {
			for (Line line : shownLines)
				for (Cell cell : line.cells)
					if (cell.attached)
						removeElement(cell.element);
			shownLines.clear();
			shownXSize = -1;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof FlapDisplayBlockEntity flap))
				return;
			Axis axis = state.getValue(HORIZONTAL_FACING).getAxis();
			cog.spin(time, flap.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));

			if (!flap.isController) {
				if (wasController) {
					wasController = false;
					clearText();
				}
				return;
			}
			wasController = true;
			updateText(flap, time);
		}

		private void updateText(FlapDisplayBlockEntity flap, long time) {
			List<FlapDisplayLayout> lines = flap.getLines();
			if (flap.xSize != shownXSize || lines.size() != shownLines.size()) {
				clearText();
				shownXSize = flap.xSize;
			}

			for (int j = 0; j < lines.size(); j++) {
				FlapDisplayLayout layout = lines.get(j);
				String shape = layout.shape();
				if (j >= shownLines.size() || !shownLines.get(j).shape.equals(shape)) {
					if (j < shownLines.size()) {
						for (Cell cell : shownLines.get(j).cells)
							if (cell.attached)
								removeElement(cell.element);
						shownLines.set(j, layoutLine(flap, layout, j, shape));
					} else {
						shownLines.add(layoutLine(flap, layout, j, shape));
					}
				}

				Line line = shownLines.get(j);
				int color = flap.getLineColor(j) & 0xFFFFFF;
				boolean glowing = flap.isLineGlowing(j);
				for (Cell cell : line.cells)
					updateCell(cell, j, color, glowing, flap.isRunning, time);
			}
		}

		/** Create's FlapDisplayRenderer: where each flap of the line sits on the face of the board. */
		private Line layoutLine(FlapDisplayBlockEntity flap, FlapDisplayLayout layout, int lineIndex, String shape) {
			Line line = new Line(shape);
			Direction facing = state.getValue(HORIZONTAL_FACING);
			Direction right = facing.getCounterClockWise();
			Quaternionf rotation = new Quaternionf().rotationY(switch (facing) {
				case EAST -> (float) (Math.PI / 2);
				case NORTH -> (float) Math.PI;
				case WEST -> (float) (-Math.PI / 2);
				default -> 0f;
			});

			float width = 0;
			for (FlapDisplaySection section : layout.getSections())
				width += section.getSize() + (section.hasGap() ? 8 : 1);
			float x = flap.xSize * 16 - width / 2 + 1;

			for (FlapDisplaySection section : layout.getSections()) {
				float cellWidth = section.isWide() ? FlapDisplaySection.WIDE_MONOSPACE : FlapDisplaySection.MONOSPACE;
				if (section.isSingleFlap()) {
					line.cells.add(cell(section, 0, x + section.getSize() / 2, lineIndex, facing, right, rotation));
				} else {
					for (int flapIndex = 0; flapIndex < section.flapCount(); flapIndex++)
						line.cells.add(cell(section, flapIndex, x + (flapIndex + .5f) * cellWidth, lineIndex, facing, right, rotation));
				}
				x += section.getSize() + (section.hasGap() ? 8 : 1);
			}
			return line;
		}

		private static Cell cell(FlapDisplaySection section, int flapIndex, float centreX, int lineIndex, Direction facing,
			Direction right, Quaternionf rotation) {
			// from the left edge of the controller's block, along the face of the board
			float u = -.5f + centreX * PIXEL - PIXEL;
			float v = .5f - (8.5f + 16 * lineIndex) * PIXEL - 4.5f * PIXEL;
			Matrix4f m = new Matrix4f()
				.translate(right.getStepX() * u + facing.getStepX() * TEXT_DISTANCE, v,
					right.getStepZ() * u + facing.getStepZ() * TEXT_DISTANCE)
				.rotate(rotation)
				.scale(TEXT_SCALE);
			return new Cell(section, flapIndex, m);
		}

		private void updateCell(Cell cell, int lineIndex, int lineColor, boolean glowing, boolean running, long time) {
			FlapDisplaySection section = cell.section;
			String[] options = section.getCyclingOptions();
			String shown;
			boolean spinning = section.isSpinning(cell.flap);
			if (section.isSingleFlap()) {
				shown = spinning ? options[(int) ((time / 3) + cell.flap * 13) % options.length] : section.getShownText();
			} else {
				String text = section.getShownText();
				shown = cell.flap < text.length() ? String.valueOf(text.charAt(cell.flap)) : " ";
				if (spinning) {
					float speed = section.getSpinningTicks() > 5 && section.getSpinningTicks() < 20 ? 1.75f : 2.5f;
					float cycle = ((time & ~1L) / speed) + cell.flap * 16.83f + lineIndex * 0.75f;
					float partial = cycle % 1;
					String cyclingGlyph = options[((int) cycle) % options.length];
					shown = !running ? cyclingGlyph : partial > 1 / 2f ? partial > 3 / 4f ? "_" : "-" : cyclingGlyph;
				}
			}

			int color = spinning && !section.isSingleFlap() ? dim(lineColor, .75f) : lineColor;
			boolean blank = shown.isBlank();
			if (blank) {
				if (cell.attached) {
					removeElement(cell.element);
					cell.attached = false;
				}
				return;
			}

			if (!shown.equals(cell.shown) || color != cell.shownColor) {
				cell.shown = shown;
				cell.shownColor = color;
				cell.element.setText(Component.literal(shown)
					.withColor(color));
			}
			if (glowing != cell.shownGlow) {
				cell.shownGlow = glowing;
				cell.element.setBrightness(glowing ? Brightness.FULL_BRIGHT : null);
				cell.element.setTextOpacity((byte) (glowing ? 249 : 217));
			}
			if (!cell.attached) {
				if (!glowing)
					cell.element.setTextOpacity((byte) 217);
				addElement(cell.element);
				cell.attached = true;
			}
		}

		private static int dim(int color, float factor) {
			int r = (int) (((color >> 16) & 0xFF) * factor);
			int g = (int) (((color >> 8) & 0xFF) * factor);
			int b = (int) ((color & 0xFF) * factor);
			return r << 16 | g << 8 | b;
		}
	}
}

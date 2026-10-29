package dev.createpolymer.contraptions.actors.trainControls;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.ContraptionWorld;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Create's controls block (MIT, The Create Team): the train controls, and the base of the contraption controls. On
 * a train carriage the block is open and the player sitting by it drives the train with the movement keys (see
 * {@link ControlsInteractionBehaviour} and {@link ControlsServerHandler}).
 */
public class ControlsBlock extends HorizontalDirectionalBlock
	implements IWrenchable, ProperWaterloggedBlock, PolymerModelBlock, ContraptionDisplay {

	public static final BooleanProperty OPEN = BooleanProperty.create("open");
	public static final BooleanProperty VIRTUAL = BooleanProperty.create("virtual");

	/** Create's AllShapes.CONTROLS and CONTROLS_COLLISION */
	public static final VoxelShaper CONTROLS = VoxelShaper.forHorizontal(
		Shapes.or(Shapes.box(0, 0, 6 / 16d, 1, 1, 1), Shapes.box(0, 0, 4 / 16d, 1, 2 / 16d, 1)), Direction.NORTH);
	public static final VoxelShaper CONTROLS_COLLISION =
		VoxelShaper.forHorizontal(Shapes.box(0, 0, 6 / 16d, 1, 1, 1), Direction.NORTH);

	public ControlsBlock(Properties p_54120_) {
		super(p_54120_);
		registerDefaultState(defaultBlockState().setValue(OPEN, false)
			.setValue(WATERLOGGED, false)
			.setValue(VIRTUAL, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACING, OPEN, WATERLOGGED, VIRTUAL));
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		updateWater(level, ticks, pState, pCurrentPos);
		return pState.setValue(OPEN, level instanceof ContraptionWorld);
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState state = withWater(super.getStateForPlacement(pContext), pContext);
		Direction horizontalDirection = pContext.getHorizontalDirection();
		Player player = pContext.getPlayer();

		state = state.setValue(FACING, horizontalDirection.getOpposite());
		if (player != null && player.isShiftKeyDown())
			state = state.setValue(FACING, horizontalDirection);

		return state;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return CONTROLS.get(pState.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		return CONTROLS_COLLISION.get(pState.getValue(FACING));
	}

	@Override
	public BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}


	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new ControlsModel(state);
	}

	/**
	 * On a contraption the controls are open, with the cover over them. Create also draws the two levers, which the
	 * train moves while it is driven; they are not shown here.
	 */
	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		Direction facing = state.getValue(FACING);
		return List.of(new Piece(ModelItems.block("controls/block_open"), Orientation.forHorizontal(facing)),
			new Piece(ModelItems.block("controls/train/cover"), new org.joml.Quaternionf().rotationY((float) Math.PI)
				.mul(Orientation.horizontalAngle(facing))));
	}

	/** The controls model of the state, as Create's blockstate file picks it. */
	public static class ControlsModel extends BaseModel {
		private final Part part;

		public ControlsModel(BlockState state) {
			super(state);
			part = addPart(ModelItems.block("controls/block_closed"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			String model = state.getValue(VIRTUAL) ? "virtual" : state.getValue(OPEN) ? "open" : "closed";
			part.setItem(ModelItems.block("controls/block_" + model));
			part.orientation(Orientation.forHorizontal(state.getValue(FACING)))
				.apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {}
	}

}

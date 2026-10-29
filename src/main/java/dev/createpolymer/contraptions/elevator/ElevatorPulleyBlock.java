package dev.createpolymer.contraptions.elevator;

import java.util.List;
import java.util.Map;

import dev.createpolymer.contraptions.pulley.PulleyModel;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ElevatorPulleyBlock extends HorizontalKineticBlock
	implements IBE<ElevatorPulleyBlockEntity>, ContraptionDisplay {

	/** Create's AllShapes.ELEVATOR_PULLEY, authored facing east. */
	private static final VoxelShape SHAPE_X = Shapes.or(ShapeUtil.box(0, 0, 0, 16, 16, 2), ShapeUtil.box(0, 0, 14, 16, 16, 16),
		ShapeUtil.box(2, 0, 2, 14, 14, 14));
	private static final Map<Axis, VoxelShape> SHAPES = Map.of(Axis.X, SHAPE_X, Axis.Z, ShapeUtil.rotateY90(SHAPE_X));

	public ElevatorPulleyBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!player.mayBuild())
			return InteractionResult.FAIL;
		if (player.isShiftKeyDown())
			return InteractionResult.FAIL;
		if (!stack.isEmpty())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		return onBlockEntityUseItemOn(level, pos, be -> {
			be.clicked();
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	public BlockEntityType<? extends ElevatorPulleyBlockEntity> getBlockEntityType() {
		return PulleyRegistry.ELEVATOR_PULLEY_BLOCK_ENTITY;
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(HORIZONTAL_FACING)
			.getClockWise()
			.getAxis();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(HORIZONTAL_FACING)
			.getAxis());
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return getRotationAxis(state) == face.getAxis();
	}

	@Override
	public Class<ElevatorPulleyBlockEntity> getBlockEntityClass() {
		return ElevatorPulleyBlockEntity.class;
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.GOLD_BLOCK.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new PulleyModel(state, PulleyModel.ELEVATOR);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		List<Piece> pieces = new java.util.ArrayList<>();
		BlockstateModels.Variant body = BlockstateModels.get("elevator_pulley", state);
		if (body != null)
			pieces.add(new Piece(body.stack(), body.rotation()));
		pieces.add(new Piece(ModelItems.block("shaft"), Orientation.forAxis(getRotationAxis(state))));
		pieces.add(new Piece(ModelItems.block("elevator_pulley/rope_coil"),
			Orientation.horizontalAngle(state.getValue(HORIZONTAL_FACING))));
		return pieces;
	}
}

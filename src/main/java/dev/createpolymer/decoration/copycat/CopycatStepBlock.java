package dev.createpolymer.decoration.copycat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.DecorationPlacement;
import dev.createpolymer.equipment.extendoGrip.ExtendoGripItem;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.util.VoxelShaper;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CopycatStepBlock extends WaterloggedCopycatBlock {

	public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** Create's AllShapes.STEP_BOTTOM / STEP_TOP */
	public static final VoxelShaper STEP_BOTTOM =
		VoxelShaper.forHorizontal(Block.box(0, 0, 8, 16, 8, 16), Direction.SOUTH);
	public static final VoxelShaper STEP_TOP =
		VoxelShaper.forHorizontal(Block.box(0, 8, 8, 16, 16, 16), Direction.SOUTH);

	public CopycatStepBlock(Properties pProperties) {
		super(pProperties);
		registerDefaultState(defaultBlockState().setValue(HALF, Half.BOTTOM)
			.setValue(FACING, Direction.SOUTH));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!player.isShiftKeyDown() && player.mayBuild()) {
			// Create's PlacementHelper (a PoleHelper): a step in hand continues the row of steps
			if (stack.is(asItem()) && stack.getItem() instanceof BlockItem blockItem)
				return placeNextInRow(level, blockItem, state, pos, player, hand, hitResult);
		}

		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}

	private static Axis rowAxis(BlockState state) {
		return state.getValue(FACING)
			.getClockWise()
			.getAxis();
	}

	private InteractionResult placeNextInRow(Level world, BlockItem blockItem, BlockState state, BlockPos pos,
		Player player, InteractionHand hand, BlockHitResult ray) {
		List<Direction> directions = CopycatPanelBlock.orderedByDistance(pos, ray.getLocation(),
			dir -> dir.getAxis() == rowAxis(state));
		for (Direction dir : directions) {
			int range = DecorationPlacement.PLACEMENT_ASSIST_RANGE;
			AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
			if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id()))
				range += 4;
			int poles = attachedPoles(world, pos, dir);
			if (poles >= range)
				continue;

			BlockPos newPos = pos.relative(dir, poles + 1);
			BlockState newState = world.getBlockState(newPos);

			if (newState.canBeReplaced())
				return DecorationPlacement.placeInWorld(world, blockItem, player, hand, newPos,
					bState -> bState.setValue(FACING, state.getValue(FACING))
						.setValue(HALF, state.getValue(HALF)));
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	private int attachedPoles(Level world, BlockPos pos, Direction direction) {
		BlockPos checkPos = pos.relative(direction);
		BlockState state = world.getBlockState(checkPos);
		int count = 0;
		while (state.is(this) && rowAxis(state) == direction.getAxis()) {
			count++;
			checkPos = checkPos.relative(direction);
			state = world.getBlockState(checkPos);
		}
		return count;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement =
			super.getStateForPlacement(pContext).setValue(FACING, pContext.getHorizontalDirection());
		Direction direction = pContext.getClickedFace();
		if (direction == Direction.UP)
			return stateForPlacement;
		if (direction == Direction.DOWN || (pContext.getClickLocation().y - pContext.getClickedPos()
			.getY() > 0.5D))
			return stateForPlacement.setValue(HALF, Half.TOP);
		return stateForPlacement;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(HALF, FACING));
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		VoxelShaper voxelShaper = pState.getValue(HALF) == Half.BOTTOM ? STEP_BOTTOM : STEP_TOP;
		return voxelShaper.get(pState.getValue(FACING));
	}

	@Override
	protected BlockState rotate(BlockState pState, Rotation pRot) {
		return pState.setValue(FACING, pRot.rotate(pState.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState pState, Mirror pMirror) {
		return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
	}

	/**
	 * Vanilla has no quarter block, so players bump into an invisible half block (bottom or top half): never smaller
	 * than the step, which keeps the client from walking where the server says there is a block.
	 */
	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return RedstoneClientStates.slab(state.getValue(HALF) == Half.TOP, state.getValue(WATERLOGGED));
	}

	@Override
	public eu.pb4.polymer.virtualentity.api.ElementHolder createModel(ServerLevel world, BlockPos pos,
		BlockState state) {
		return copycatModel(state, CopycatBlock.getMaterial(world, pos));
	}

	@Override
	protected CopycatModel copycatModel(BlockState state, BlockState material) {
		return new CopycatStepModel(state, material);
	}

}

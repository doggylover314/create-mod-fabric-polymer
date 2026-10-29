package dev.createpolymer.decoration;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.item.IWrenchable;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Ladders of andesite, brass and copper. A ladder has no spare vanilla block state Polymer could re-skin, so players
 * see the vanilla ladder.
 */
public class MetalLadderBlock extends LadderBlock implements IWrenchable, PolymerBlock {

	public MetalLadderBlock(Properties p_54345_) {
		super(p_54345_);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return Blocks.LADDER.withPropertiesOf(state);
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.LADDER.defaultBlockState();
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader pLevel, ScheduledTickAccess ticks, BlockPos pCurrentPos,
		Direction pFacing, BlockPos pFacingPos, BlockState pFacingState, RandomSource random) {
		if (!pState.canSurvive(pLevel, pCurrentPos))
			return Blocks.AIR.defaultBlockState();
		return super.updateShape(pState, pLevel, ticks, pCurrentPos, pFacing, pFacingPos, pFacingState, random);
	}

	@Override
	protected boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		BlockState otherState = pLevel.getBlockState(pPos.relative(Direction.UP));
		return super.canSurvive(pState, pLevel, pPos) ||
			(otherState.is(this) && pState.getValue(FACING).equals(otherState.getValue(FACING)));
	}

	/** Using a ladder on a ladder puts the new one at the end of the column, up or down depending on where you look. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof MetalLadderBlock))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		Direction dir = player.getXRot() < 0 ? Direction.UP : Direction.DOWN;
		int ladders = attachedLadders(level, pos, dir);
		if (ladders >= DecorationPlacement.PLACEMENT_ASSIST_RANGE)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		BlockPos newPos = pos.relative(dir, ladders + 1);
		BlockState newState = level.getBlockState(newPos);
		if (!state.canSurvive(level, newPos) || !newState.canBeReplaced())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		return DecorationPlacement.placeInWorld(level, blockItem, player, hand, newPos,
			bState -> bState.setValue(FACING, state.getValue(FACING)));
	}

	private int attachedLadders(Level world, BlockPos pos, Direction direction) {
		BlockPos checkPos = pos.relative(direction);
		BlockState state = world.getBlockState(checkPos);
		int count = 0;
		while (state.getBlock() instanceof LadderBlock) {
			count++;
			checkPos = checkPos.relative(direction);
			state = world.getBlockState(checkPos);
		}
		return count;
	}

}

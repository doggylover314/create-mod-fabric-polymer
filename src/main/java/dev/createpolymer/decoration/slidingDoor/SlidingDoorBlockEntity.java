package dev.createpolymer.decoration.slidingDoor;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lives in the lower half of a sliding door and runs its opening animation: {@link #animation} chases 1 while the
 * door is open and 0 while it is closed. The door's display model reads it to place the panels.
 */
public class SlidingDoorBlockEntity extends SmartBlockEntity {

	LerpedFloat animation;
	boolean deferUpdate;

	public SlidingDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		animation = LerpedFloat.linear()
			.startWithValue(isOpen(state) ? 1 : 0);
	}

	@Override
	public void tick() {
		if (deferUpdate && !level.isClientSide()) {
			deferUpdate = false;
			BlockState blockState = getBlockState();
			blockState.handleNeighborChanged(level, worldPosition, Blocks.AIR, null, false);
		}

		super.tick();
		boolean open = isOpen(getBlockState());
		boolean wasSettled = animation.settled();
		animation.chase(open ? 1 : 0, .15f, Chaser.LINEAR);
		animation.tickChaser();

		if (!open && !wasSettled && animation.settled() && !isVisible(getBlockState()))
			showBlockModel();
	}

	/** 0 for a closed door up to 1 for a fully open one. */
	public float getAnimationValue() {
		return animation.getValue();
	}

	protected boolean isVisible(BlockState state) {
		return state.getOptionalValue(SlidingDoorBlock.VISIBLE)
			.orElse(true);
	}

	protected void showBlockModel() {
		level.setBlock(worldPosition, getBlockState().setValue(SlidingDoorBlock.VISIBLE, true), Block.UPDATE_ALL);
		level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, .5f, 1);
	}

	public static boolean isOpen(BlockState state) {
		return state.getOptionalValue(DoorBlock.OPEN)
			.orElse(false);
	}

}

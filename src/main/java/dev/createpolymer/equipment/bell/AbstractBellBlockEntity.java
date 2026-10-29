package dev.createpolymer.equipment.bell;

import java.util.List;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Create's bell block entity: the swing of the bell for {@link #RING_DURATION} ticks after a ring. */
public abstract class AbstractBellBlockEntity extends SmartBlockEntity {

	public static final int RING_DURATION = 74;

	public boolean isRinging;
	public int ringingTicks;
	public Direction ringDirection;

	public AbstractBellBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

	public boolean ring(Level world, BlockPos pos, Direction direction) {
		isRinging = true;
		ringingTicks = 0;
		ringDirection = direction;
		sendData();
		return true;
	}

	@Override
	public void tick() {
		super.tick();

		if (isRinging) {
			++ringingTicks;
		}

		if (ringingTicks >= RING_DURATION) {
			isRinging = false;
			ringingTicks = 0;
		}
	}
}

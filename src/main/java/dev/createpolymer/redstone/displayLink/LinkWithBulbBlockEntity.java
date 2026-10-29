package dev.createpolymer.redstone.displayLink;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public abstract class LinkWithBulbBlockEntity extends SmartBlockEntity {

	private LerpedFloat glow;

	public LinkWithBulbBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		glow = LerpedFloat.linear()
			.startWithValue(0);
		glow.chase(0, 0.5f, Chaser.EXP);
	}

	@Override
	public void tick() {
		super.tick();
		glow.tickChaser();
	}

	public float getGlow(float partialTicks) {
		return glow.getValue(partialTicks);
	}

	/** Create sends the pulse to the clients with the next sync; here the model reads the glow, so it starts now. */
	public void sendPulseNextSync() {
		pulse();
	}

	public void pulse() {
		glow.setValue(2);
	}

	public Vec3 getBulbOffset(BlockState state) {
		return Vec3.ZERO;
	}

	public Direction getBulbFacing(BlockState state) {
		return state.getValue(DisplayLinkBlock.FACING);
	}

}

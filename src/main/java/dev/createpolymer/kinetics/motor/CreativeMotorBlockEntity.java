package dev.createpolymer.kinetics.motor;

import dev.createpolymer.kinetics.base.GeneratingKineticBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Ported from Create's CreativeMotorBlockEntity (MIT, The Create Team).
 * Create sets the speed with a scroll box; here it is set by right-clicking (see the block).
 */
public class CreativeMotorBlockEntity extends GeneratingKineticBlockEntity {
	public static final int DEFAULT_SPEED = 16;
	public static final int MAX_SPEED = 256;

	private int generatedSpeed = DEFAULT_SPEED;

	public CreativeMotorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CREATIVE_MOTOR, pos, state);
	}

	@Override
	public void initialize() {
		super.initialize();
		if (!hasSource() || getGeneratedSpeed() > getTheoreticalSpeed())
			updateGeneratedRotation();
	}

	public int getSetting() {
		return generatedSpeed;
	}

	public void setSetting(int speed) {
		speed = Mth.clamp(speed, -MAX_SPEED, MAX_SPEED);
		if (speed == generatedSpeed)
			return;
		generatedSpeed = speed;
		setChanged();
		updateGeneratedRotation();
	}

	@Override
	public float getGeneratedSpeed() {
		if (!getBlockState().is(ModBlocks.CREATIVE_MOTOR))
			return 0;
		return convertToDirection(generatedSpeed, getBlockState().getValue(CreativeMotorBlock.FACING));
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putInt("GeneratedSpeed", generatedSpeed);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		generatedSpeed = compound.getIntOr("GeneratedSpeed", DEFAULT_SPEED);
		super.read(compound);
	}
}

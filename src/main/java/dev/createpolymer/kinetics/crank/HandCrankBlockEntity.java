package dev.createpolymer.kinetics.crank;

import dev.createpolymer.kinetics.base.GeneratingKineticBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Ported from Create's HandCrankBlockEntity (MIT, The Create Team). */
public class HandCrankBlockEntity extends GeneratingKineticBlockEntity {

	public int inUse;
	public boolean backwards;

	public HandCrankBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.HAND_CRANK, pos, state);
	}

	public void turn(boolean back) {
		boolean update = false;

		if (getGeneratedSpeed() == 0 || back != backwards)
			update = true;

		inUse = 10;
		this.backwards = back;
		if (update && !level.isClientSide())
			updateGeneratedRotation();
	}

	@Override
	public float getGeneratedSpeed() {
		Block block = getBlockState().getBlock();
		if (!(block instanceof HandCrankBlock crank))
			return 0;
		int speed = (inUse == 0 ? 0 : clockwise() ? -1 : 1) * crank.getRotationSpeed();
		return convertToDirection(speed, getBlockState().getValue(HandCrankBlock.FACING));
	}

	protected boolean clockwise() {
		return backwards;
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putInt("InUse", inUse);
		compound.putBoolean("Backwards", backwards);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		inUse = compound.getIntOr("InUse", 0);
		backwards = compound.getBooleanOr("Backwards", false);
		super.read(compound);
	}

	@Override
	public void tick() {
		super.tick();

		if (inUse > 0) {
			if (level.getGameTime() % 10 == 0)
				ModSounds.cranking(level, worldPosition, inUse / 2.5f, .65f + (10 - inUse) / 10f);
			inUse--;
			if (inUse == 0 && !level.isClientSide())
				updateGeneratedRotation();
		}
	}
}

package dev.createpolymer.kinetics.steamEngine;

import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PoweredShaftBlockEntity extends GeneratingKineticBlockEntity {

	public BlockPos enginePos;
	public float engineEfficiency;
	public int movementDirection;
	public int initialTicks;
	public Block capacityKey;

	public PoweredShaftBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		movementDirection = 1;
		initialTicks = 3;
	}

	@Override
	public void tick() {
		super.tick();
		if (initialTicks > 0)
			initialTicks--;
	}

	public void update(BlockPos sourcePos, int direction, float efficiency) {
		BlockPos key = worldPosition.subtract(sourcePos);
		enginePos = key;
		float prev = engineEfficiency;
		engineEfficiency = efficiency;
		int prevDirection = this.movementDirection;
		if (Mth.equal(efficiency, prev) && prevDirection == direction)
			return;

		capacityKey = level.getBlockState(sourcePos)
			.getBlock();
		this.movementDirection = direction;
		updateGeneratedRotation();
	}

	public void remove(BlockPos sourcePos) {
		if (!isPoweredBy(sourcePos))
			return;

		enginePos = null;
		engineEfficiency = 0;
		movementDirection = 0;
		capacityKey = null;
		updateGeneratedRotation();
	}

	public boolean canBePoweredBy(BlockPos globalPos) {
		return initialTicks == 0 && (enginePos == null || isPoweredBy(globalPos));
	}

	public boolean isPoweredBy(BlockPos globalPos) {
		BlockPos key = worldPosition.subtract(globalPos);
		return key.equals(enginePos);
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putInt("Direction", movementDirection);
		if (initialTicks > 0)
			compound.putInt("Warmup", initialTicks);
		if (enginePos != null && capacityKey != null) {
			compound.store("EnginePos", BlockPos.CODEC, enginePos);
			compound.putFloat("EnginePower", engineEfficiency);
			compound.putString("EngineType", BuiltInRegistries.BLOCK.getKey(capacityKey)
				.toString());
		}
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		movementDirection = compound.getIntOr("Direction", 0);
		initialTicks = compound.getIntOr("Warmup", 0);
		enginePos = null;
		engineEfficiency = 0;

		enginePos = compound.read("EnginePos", BlockPos.CODEC).orElse(null);
		if (enginePos != null) {
			engineEfficiency = compound.getFloatOr("EnginePower", 0);
			capacityKey = BuiltInRegistries.BLOCK.getValue(Identifier.parse(compound.getStringOr("EngineType", "minecraft:air")));
		}
	}

	@Override
	public float getGeneratedSpeed() {
		return getCombinedCapacity() > 0 ? movementDirection * 16 * getSpeedModifier() : 0;
	}

	private float getCombinedCapacity() {
		return capacityKey == null ? 0 : (float) (engineEfficiency * BlockStressValues.getCapacity(capacityKey));
	}

	private int getSpeedModifier() {
		return (int) (1 + (engineEfficiency >= 1 ? 3 : Math.min(2, Math.floor(engineEfficiency * 4))));
	}

	@Override
	public float calculateAddedStressCapacity() {
		float capacity = getCombinedCapacity() / getSpeedModifier();
		this.lastCapacityProvided = capacity;
		return capacity;
	}

	@Override
	public int getRotationAngleOffset(Axis axis) {
		int combinedCoords = axis.choose(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
		return super.getRotationAngleOffset(axis) + (combinedCoords % 2 == 0 ? 180 : 0);
	}

}

package dev.createpolymer.kinetics.fan;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.logistics.chute.ChuteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class EncasedFanBlockEntity extends KineticBlockEntity implements IAirCurrentSource {

	public AirCurrent airCurrent;
	protected int airCurrentUpdateCooldown;
	protected int entitySearchCooldown;
	protected boolean updateAirFlow;

	public EncasedFanBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		airCurrent = new AirCurrent(this);
		updateAirFlow = true;
	}

	@Override
	public AirCurrent getAirCurrent() {
		return airCurrent;
	}

	@Nullable
	@Override
	public Level getAirCurrentWorld() {
		return level;
	}

	@Override
	public BlockPos getAirCurrentPos() {
		return worldPosition;
	}

	@Override
	public Direction getAirflowOriginSide() {
		return this.getBlockState()
			.getValue(EncasedFanBlock.FACING);
	}

	@Override
	public Direction getAirFlowDirection() {
		float speed = getSpeed();
		if (speed == 0)
			return null;
		Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
		speed = convertToDirection(speed, facing);
		return speed > 0 ? facing : facing.getOpposite();
	}

	@Override
	public void remove() {
		super.remove();
		updateChute();
	}

	@Override
	public boolean isSourceRemoved() {
		return isRemoved();
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		updateAirFlow = true;
		updateChute();
	}

	public void updateChute() {
		Direction direction = getBlockState().getValue(EncasedFanBlock.FACING);
		if (!direction.getAxis()
			.isVertical())
			return;
		BlockEntity poweredChute = level.getBlockEntity(worldPosition.relative(direction));
		if (!(poweredChute instanceof ChuteBlockEntity chuteBE))
			return;
		if (direction == Direction.DOWN)
			chuteBE.updatePull();
		else
			chuteBE.updatePush(1);
	}

	public void blockInFrontChanged() {
		updateAirFlow = true;
	}

	@Override
	public void tick() {
		super.tick();

		if (airCurrentUpdateCooldown-- <= 0) {
			airCurrentUpdateCooldown = FanConfig.fanBlockCheckRate;
			updateAirFlow = true;
		}

		if (updateAirFlow) {
			updateAirFlow = false;
			airCurrent.rebuild();
			sendData();
		}

		if (getSpeed() == 0)
			return;

		if (entitySearchCooldown-- <= 0) {
			entitySearchCooldown = 5;
			airCurrent.findEntities();
		}

		airCurrent.tick();
	}

}

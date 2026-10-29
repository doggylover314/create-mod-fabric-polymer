package dev.createpolymer.logistics.tunnel;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import com.mojang.serialization.Codec;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.belt.BeltBlock;
import dev.createpolymer.logistics.funnel.BeltFunnelBlock;
import dev.createpolymer.logistics.tunnel.BeltTunnelBlock.Shape;
import dev.createpolymer.registry.feature.BeltRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BeltTunnelBlockEntity extends SmartBlockEntity {

	public Map<Direction, LerpedFloat> flaps;
	public Set<Direction> sides;

	public BeltTunnelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		flaps = new EnumMap<>(Direction.class);
		sides = new HashSet<>();
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(BeltRegistry.ANDESITE_TUNNEL_BLOCK_ENTITY, (be, side) -> {
			// the tunnel passes items to the belt below it
			if (be.level.getBlockState(be.worldPosition.below()).getBlock() instanceof BeltBlock)
				return ItemHandlerLookup.get(be.level, be.worldPosition.below(), Direction.UP);
			return null;
		});
	}

	protected void writeFlapsAndSides(ValueOutput compound) {
		ValueOutput.TypedOutputList<Integer> flapsNBT = compound.list("Flaps", Codec.INT);
		for (Direction direction : flaps.keySet())
			flapsNBT.add(direction.get3DDataValue());

		ValueOutput.TypedOutputList<Integer> sidesNBT = compound.list("Sides", Codec.INT);
		for (Direction direction : sides)
			sidesNBT.add(direction.get3DDataValue());
	}

	@Override
	protected void write(ValueOutput compound) {
		writeFlapsAndSides(compound);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		Set<Direction> newFlaps = new HashSet<>(6);
		for (Integer value : compound.listOrEmpty("Flaps", Codec.INT))
			newFlaps.add(Direction.from3DDataValue(value));

		sides.clear();
		for (Integer value : compound.listOrEmpty("Sides", Codec.INT))
			sides.add(Direction.from3DDataValue(value));

		for (Direction d : Iterate.directions)
			if (!newFlaps.contains(d))
				flaps.remove(d);
			else if (!flaps.containsKey(d))
				flaps.put(d, createChasingFlap());

		// Backwards compat
		if (compound.list("Sides", Codec.INT).isEmpty() && !newFlaps.isEmpty())
			sides.addAll(flaps.keySet());
		super.read(compound);
	}

	private LerpedFloat createChasingFlap() {
		return LerpedFloat.linear()
			.startWithValue(.25f)
			.chase(0, .05f, Chaser.EXP);
	}

	public void updateTunnelConnections() {
		flaps.clear();
		sides.clear();
		BlockState tunnelState = getBlockState();
		for (Direction direction : Iterate.horizontalDirections) {
			if (direction.getAxis() != tunnelState.getValue(BlockStateProperties.HORIZONTAL_AXIS)) {
				boolean positive =
					direction.getAxisDirection() == AxisDirection.POSITIVE ^ direction.getAxis() == Axis.Z;
				Shape shape = tunnelState.getValue(BeltTunnelBlock.SHAPE);
				if (BeltTunnelBlock.isStraight(tunnelState))
					continue;
				if (positive && shape == Shape.T_LEFT)
					continue;
				if (!positive && shape == Shape.T_RIGHT)
					continue;
			}

			sides.add(direction);

			// Flap might be occluded
			if (level == null)
				continue;
			BlockState nextState = level.getBlockState(worldPosition.relative(direction));
			if (nextState.getBlock() instanceof BeltTunnelBlock)
				continue;
			if (nextState.getBlock() instanceof BeltFunnelBlock)
				if (nextState.getValue(BeltFunnelBlock.SHAPE) == BeltFunnelBlock.Shape.EXTENDED
					&& nextState.getValue(BeltFunnelBlock.HORIZONTAL_FACING) == direction.getOpposite())
					continue;

			flaps.put(direction, createChasingFlap());
		}
		sendData();
	}

	/** Swings the flap towards the inside of the tunnel (when an item enters) or away from it. */
	public void flap(Direction side, boolean inward) {
		if (flaps.containsKey(side))
			flaps.get(side)
				.setValue(inward ? -1 : 1);
	}

	@Override
	public void initialize() {
		super.initialize();
		updateTunnelConnections();
	}

	@Override
	public void tick() {
		super.tick();
		flaps.forEach((d, value) -> value.tickChaser());
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
}

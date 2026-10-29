package dev.createpolymer.kinetics.belt.transport;

import dev.createpolymer.redstone.displayLink.DisplayLinkBlock;
import dev.createpolymer.redstone.displayLink.source.AccumulatedItemCountDisplaySource;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.kinetics.belt.BeltBlock;
import dev.createpolymer.kinetics.belt.BeltBlockEntity;
import dev.createpolymer.kinetics.belt.BeltHelper;
import dev.createpolymer.kinetics.belt.BeltSlope;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.logistics.tunnel.BeltTunnelBlock;
import dev.createpolymer.logistics.tunnel.BeltTunnelBlockEntity;
import dev.createpolymer.logistics.tunnel.BrassTunnelBlock;
import dev.createpolymer.logistics.tunnel.BrassTunnelBlockEntity;
import dev.createpolymer.registry.feature.BeltRegistry;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BeltTunnelInteractionHandler {

	public static boolean flapTunnelsAndCheckIfStuck(BeltInventory beltInventory, TransportedItemStack current,
													 float nextOffset) {

		int currentSegment = (int) current.beltPosition;
		int upcomingSegment = (int) nextOffset;

		Direction movementFacing = beltInventory.belt.getMovementFacing();
		if (!beltInventory.beltMovementPositive && nextOffset == 0)
			upcomingSegment = -1;
		if (currentSegment == upcomingSegment)
			return false;

		if (stuckAtTunnel(beltInventory, upcomingSegment, current.stack, movementFacing)) {
			current.beltPosition = currentSegment + (beltInventory.beltMovementPositive ? .99f : .01f);
			return true;
		}

		Level world = beltInventory.belt.getLevel();
		boolean removed = false;
		BeltTunnelBlockEntity nextTunnel = getTunnelOnSegment(beltInventory, upcomingSegment);
		int transferred = current.stack.getCount();

		if (nextTunnel instanceof BrassTunnelBlockEntity brassTunnel) {
			if (brassTunnel.hasDistributionBehaviour()) {
				if (!brassTunnel.canTakeItems())
					return true;
				brassTunnel.setStackToDistribute(current.stack, movementFacing.getOpposite());
				current.stack = ItemStack.EMPTY;
				beltInventory.belt.notifyUpdate();
				removed = true;
			}
		} else if (nextTunnel != null) {
			BlockState blockState = nextTunnel.getBlockState();
			if (current.stack.getCount() > 1 && blockState.is(BeltRegistry.ANDESITE_TUNNEL)
				&& BeltTunnelBlock.isJunction(blockState)
				&& movementFacing.getAxis() == blockState.getValue(BeltTunnelBlock.HORIZONTAL_AXIS)) {

				for (Direction d : Iterate.horizontalDirections) {
					if (d.getAxis() == blockState.getValue(BeltTunnelBlock.HORIZONTAL_AXIS))
						continue;
					if (!nextTunnel.flaps.containsKey(d))
						continue;
					BlockPos outpos = nextTunnel.getBlockPos()
						.below()
						.relative(d);
					if (!world.isLoaded(outpos))
						return true;
					DirectBeltInputBehaviour behaviour =
						BlockEntityBehaviour.get(world, outpos, DirectBeltInputBehaviour.TYPE);
					if (behaviour == null)
						continue;
					if (!behaviour.canInsertFromSide(d))
						continue;

					ItemStack toinsert = current.stack.copyWithCount(1);
					if (!behaviour.handleInsertion(toinsert, d, false)
						.isEmpty())
						return true;
					flapTunnel(beltInventory, upcomingSegment, d, false);

					current.stack.shrink(1);
					beltInventory.belt.notifyUpdate();
					if (current.stack.getCount() <= 1)
						break;
				}
			}
		}

		flapTunnel(beltInventory, currentSegment, movementFacing, false);
		flapTunnel(beltInventory, upcomingSegment, movementFacing.getOpposite(), true);

		if (nextTunnel != null)
			DisplayLinkBlock.sendToGatherers(world, nextTunnel.getBlockPos(),
				(dgte, b) -> b.itemReceived(dgte, transferred), AccumulatedItemCountDisplaySource.class);

		if (removed)
			return true;

		return false;
	}

	public static boolean stuckAtTunnel(BeltInventory beltInventory, int offset, ItemStack stack,
										Direction movementDirection) {
		BeltBlockEntity belt = beltInventory.belt;
		BlockPos pos = BeltHelper.getPositionForOffset(belt, offset)
			.above();
		if (!(belt.getLevel()
			.getBlockState(pos)
			.getBlock() instanceof BrassTunnelBlock))
			return false;
		BlockEntity be = belt.getLevel()
			.getBlockEntity(pos);
		if (be == null || !(be instanceof BrassTunnelBlockEntity tunnel))
			return false;
		return !tunnel.canInsert(movementDirection.getOpposite(), stack);
	}

	public static void flapTunnel(BeltInventory beltInventory, int offset, Direction side, boolean inward) {
		BeltTunnelBlockEntity be = getTunnelOnSegment(beltInventory, offset);
		if (be == null)
			return;
		be.flap(side, inward);
	}

	@Nullable
	protected static BeltTunnelBlockEntity getTunnelOnSegment(BeltInventory beltInventory, int offset) {
		BeltBlockEntity belt = beltInventory.belt;
		if (belt.getBlockState()
			.getValue(BeltBlock.SLOPE) != BeltSlope.HORIZONTAL)
			return null;
		return getTunnelOnPosition(belt.getLevel(), BeltHelper.getPositionForOffset(belt, offset));
	}

	@Nullable
	public static BeltTunnelBlockEntity getTunnelOnPosition(Level world, BlockPos pos) {
		pos = pos.above();
		if (!(world.getBlockState(pos)
			.getBlock() instanceof BeltTunnelBlock))
			return null;
		BlockEntity be = world.getBlockEntity(pos);
		if (be == null || !(be instanceof BeltTunnelBlockEntity))
			return null;
		return ((BeltTunnelBlockEntity) be);
	}

}

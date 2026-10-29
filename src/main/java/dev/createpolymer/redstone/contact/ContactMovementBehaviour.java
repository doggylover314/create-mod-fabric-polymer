package dev.createpolymer.redstone.contact;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.TickPriority;

/**
 * Ported from Create's ContactMovementBehaviour (MIT, The Create Team). The elevator contact is not ported yet; it
 * goes next to the redstone contact check in {@link #visitNewPosition}.
 */
public class ContactMovementBehaviour implements MovementBehaviour {

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		return Vec3.atLowerCornerOf(context.state.getValue(RedstoneContactBlock.FACING)
			.getUnitVec3i())
			.scale(.65f);
	}

	@Override
	public void visitNewPosition(MovementContext context, BlockPos pos) {
		BlockState block = context.state;
		Level world = context.world;

		if (world.isClientSide())
			return;
		if (context.firstMovement)
			return;

		deactivateLastVisitedContact(context);
		BlockState visitedState = world.getBlockState(pos);
		if (!visitedState.is(RedstoneRegistry.REDSTONE_CONTACT))
			return;

		Vec3 contact = Vec3.atLowerCornerOf(block.getValue(RedstoneContactBlock.FACING)
			.getUnitVec3i());
		contact = context.rotation.apply(contact);
		Direction direction = Direction.getApproximateNearest(contact.x, contact.y, contact.z);

		if (visitedState.getValue(RedstoneContactBlock.FACING) != direction.getOpposite())
			return;

		world.setBlockAndUpdate(pos, visitedState.setValue(RedstoneContactBlock.POWERED, true));

		context.data.store("lastContact", BlockPos.CODEC, pos);
		return;
	}

	@Override
	public void stopMoving(MovementContext context) {
		deactivateLastVisitedContact(context);
	}

	@Override
	public void cancelStall(MovementContext context) {
		MovementBehaviour.super.cancelStall(context);
		deactivateLastVisitedContact(context);
	}

	public void deactivateLastVisitedContact(MovementContext context) {
		if (!context.data.contains("lastContact"))
			return;

		BlockPos last = context.data.read("lastContact", BlockPos.CODEC)
			.orElse(BlockPos.ZERO);
		context.data.remove("lastContact");
		BlockState blockState = context.world.getBlockState(last);

		if (blockState.is(RedstoneRegistry.REDSTONE_CONTACT))
			context.world.scheduleTick(last, RedstoneRegistry.REDSTONE_CONTACT, 1, TickPriority.NORMAL);
	}

}

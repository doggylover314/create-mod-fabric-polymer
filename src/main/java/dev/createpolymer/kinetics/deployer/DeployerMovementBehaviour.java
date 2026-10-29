package dev.createpolymer.kinetics.deployer;

import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.OrientedContraptionEntity;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.kinetics.deployer.DeployerBlockEntity.Mode;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.util.NBTHelper;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's DeployerMovementBehaviour (MIT, The Create Team). The schematic printer mode and the track
 * placement advancement belong to schematics and trains, which are not ported.
 */
public class DeployerMovementBehaviour implements MovementBehaviour {

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		return Vec3.atLowerCornerOf(context.state.getValue(DeployerBlock.FACING)
			.getUnitVec3i())
			.scale(2);
	}

	@Override
	public void visitNewPosition(MovementContext context, BlockPos pos) {
		if (context.world.isClientSide())
			return;

		tryGrabbingItem(context);
		DeployerFakePlayer player = getPlayer(context);
		if (player == null)
			return;
		Mode mode = getMode(context);
		if (mode == Mode.USE && !DeployerHandler.shouldActivate(player.getMainHandItem(), context.world, pos, null))
			return;

		activate(context, pos, player, mode);
		tryDisposeOfExcess(context);
		context.stall = player.blockBreakingProgress != null;
	}

	public void activate(MovementContext context, BlockPos pos, DeployerFakePlayer player, Mode mode) {
		Vec3 facingVec = Vec3.atLowerCornerOf(context.state.getValue(DeployerBlock.FACING)
			.getUnitVec3i());
		facingVec = context.rotation.apply(facingVec);
		Vec3 vec = context.position.subtract(facingVec.scale(2));

		float xRot = AbstractContraptionEntity.pitchFromVector(facingVec) - 90;
		if (Math.abs(xRot) > 89) {
			Vec3 initial = new Vec3(0, 0, 1);
			if (context.contraption.entity instanceof OrientedContraptionEntity oce)
				initial = VecHelper.rotate(initial, oce.getInitialYaw(), Axis.Y);
			facingVec = context.rotation.apply(initial);
		}

		player.setYRot(AbstractContraptionEntity.yawFromVector(facingVec));
		player.setXRot(xRot);

		DeployerHandler.activate(player, vec, pos, facingVec, mode);
	}

	@Override
	public void tick(MovementContext context) {
		if (context.world.isClientSide())
			return;
		if (!context.stall)
			return;

		DeployerFakePlayer player = getPlayer(context);
		if (player == null)
			return;
		Mode mode = getMode(context);

		Pair<BlockPos, Float> blockBreakingProgress = player.blockBreakingProgress;
		if (blockBreakingProgress != null) {
			int timer = context.data.getIntOr("Timer", 0);
			if (timer < 20) {
				timer++;
				context.data.putInt("Timer", timer);
				return;
			}

			context.data.remove("Timer");
			activate(context, blockBreakingProgress.getKey(), player, mode);
			tryDisposeOfExcess(context);
		}

		context.stall = player.blockBreakingProgress != null;
	}

	@Override
	public void cancelStall(MovementContext context) {
		if (context.world.isClientSide())
			return;

		MovementBehaviour.super.cancelStall(context);
		DeployerFakePlayer player = getPlayer(context);
		if (player == null)
			return;
		if (player.blockBreakingProgress == null)
			return;
		context.world.destroyBlockProgress(player.getId(), player.blockBreakingProgress.getKey(), -1);
		player.blockBreakingProgress = null;
	}

	@Override
	public void stopMoving(MovementContext context) {
		if (context.world.isClientSide())
			return;

		DeployerFakePlayer player = getPlayer(context);
		if (player == null)
			return;

		cancelStall(context);
		// the block entity reads its inventory back when the contraption is disassembled
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, context.world.registryAccess());
		player.getInventory()
			.save(out.list("Inventory", ItemStackWithSlot.CODEC));
		context.blockEntityData.merge(out.buildResult());
		player.discard();
	}

	private void tryGrabbingItem(MovementContext context) {
		DeployerFakePlayer player = getPlayer(context);
		if (player == null)
			return;
		if (player.getMainHandItem()
			.isEmpty()) {
			FilterItemStack filter = context.getFilterFromBE();
			ItemStack held = dev.createpolymer.foundation.item.ItemHelper.extract(context.contraption.getStorage().getAllItems(),
				stack -> filter.test(context.world, stack), 1, false);
			player.setItemInHand(InteractionHand.MAIN_HAND, held);
		}
	}

	private void tryDisposeOfExcess(MovementContext context) {
		DeployerFakePlayer player = getPlayer(context);
		if (player == null)
			return;
		Inventory inv = player.getInventory();
		FilterItemStack filter = context.getFilterFromBE();

		// Create walks the armor, offhand and main lists of the old inventory; here that is slot by slot
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			ItemStack itemstack = inv.getItem(slot);
			if (itemstack.isEmpty())
				continue;

			if (slot == inv.getSelectedSlot() && filter.test(context.world, itemstack))
				continue;

			collectOrDropItem(context, itemstack);
			inv.setItem(slot, ItemStack.EMPTY);
		}
	}

	@Override
	public void writeExtraData(MovementContext context) {
		DeployerFakePlayer player = getPlayer(context);
		if (player == null)
			return;
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, context.world.registryAccess());
		out.store("HeldItem", ItemStack.OPTIONAL_CODEC, player.getMainHandItem());
		context.data.merge(out.buildResult());
	}

	@Nullable
	static DeployerFakePlayer getPlayer(MovementContext context) {
		if (!(context.temporaryData instanceof DeployerFakePlayer) && context.world instanceof ServerLevel serverLevel) {
			CompoundTag blockEntityData = context.blockEntityData == null ? new CompoundTag() : context.blockEntityData;
			UUID owner = blockEntityData.read("Owner", UUIDUtil.CODEC)
				.orElse(null);
			DeployerFakePlayer deployerFakePlayer = new DeployerFakePlayer(serverLevel, owner);
			ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, serverLevel.registryAccess(), blockEntityData);
			deployerFakePlayer.getInventory()
				.load(input.listOrEmpty("Inventory", ItemStackWithSlot.CODEC));
			ValueInput dataInput = TagValueInput.create(ProblemReporter.DISCARDING, serverLevel.registryAccess(), context.data);
			dataInput.read("HeldItem", ItemStack.OPTIONAL_CODEC)
				.ifPresent(held -> deployerFakePlayer.setItemInHand(InteractionHand.MAIN_HAND, held));
			blockEntityData.remove("Inventory");
			context.temporaryData = deployerFakePlayer;
		}
		return context.temporaryData instanceof DeployerFakePlayer fake ? fake : null;
	}

	private Mode getMode(MovementContext context) {
		return NBTHelper.readEnum(context.blockEntityData == null ? new CompoundTag() : context.blockEntityData, "Mode",
			Mode.class);
	}
}

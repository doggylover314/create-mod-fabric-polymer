package dev.createpolymer.redstone.link.controller;

import java.util.List;
import java.util.UUID;

import dev.createpolymer.equipment.ToolsSounds;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.registry.feature.ToolsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class LecternControllerBlockEntity extends SmartBlockEntity {
	private ItemContainerContents controllerData = ItemContainerContents.EMPTY;
	private UUID user;
	private boolean deactivatedThisTick;    // used only on server

	public LecternControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	@Override
	public void writeSafe(ValueOutput output) {
		super.writeSafe(output);
		output.store("ControllerData", ItemContainerContents.CODEC, controllerData);
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);
		output.store("ControllerData", ItemContainerContents.CODEC, controllerData);
		if (user != null)
			output.store("User", UUIDUtil.CODEC, user);
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);
		controllerData = input.read("ControllerData", ItemContainerContents.CODEC)
			.orElse(ItemContainerContents.EMPTY);
		user = input.read("User", UUIDUtil.CODEC)
			.orElse(null);
	}

	public ItemStack getController() {
		return createLinkedController();
	}

	public UUID getUser() {
		return user;
	}

	public boolean hasUser() {
		return user != null;
	}

	public boolean isUsedBy(Player player) {
		return hasUser() && user.equals(player.getUUID());
	}

	public void tryStartUsing(Player player) {
		if (!deactivatedThisTick && !hasUser() && !LinkedControllerHandler.isUsingLectern(player) && playerInRange(player, level, worldPosition))
			startUsing(player);
	}

	public void tryStopUsing(Player player) {
		if (isUsedBy(player))
			stopUsing(player);
	}

	private void startUsing(Player player) {
		user = player.getUUID();
		if (player instanceof ServerPlayer serverPlayer)
			LinkedControllerHandler.activateInLectern(serverPlayer, worldPosition);
		sendData();
		setChanged();
	}

	private void stopUsing(Player player) {
		user = null;
		deactivatedThisTick = true;
		sendData();
		setChanged();
	}

	public static boolean playerIsUsingLectern(Player player) {
		return LinkedControllerHandler.isUsingLectern(player);
	}

	@Override
	public void tick() {
		super.tick();

		deactivatedThisTick = false;

		if (!(level instanceof ServerLevel serverLevel))
			return;
		if (user == null)
			return;

		Entity entity = serverLevel.getEntity(user);
		if (!(entity instanceof ServerPlayer player)) {
			user = null;
			sendData();
			setChanged();
			return;
		}

		if (!playerInRange(player, level, worldPosition) || !playerIsUsingLectern(player)) {
			stopUsing(player);
			LinkedControllerHandler.deactivateInLectern(player);
		}
	}

	public void setController(ItemStack newController) {
		if (newController != null) {
			controllerData = newController.getOrDefault(ToolsRegistry.LINKED_CONTROLLER_ITEMS, ItemContainerContents.EMPTY);
			ToolsSounds.controllerPut(level, worldPosition);
			setChanged();
			sendData();
		}
	}

	public void swapControllers(ItemStack stack, Player player, InteractionHand hand, BlockState state) {
		ItemStack newController = stack.copy();
		stack.setCount(0);
		if (player.getItemInHand(hand).isEmpty()) {
			player.setItemInHand(hand, createLinkedController());
		} else {
			dropController(state);
		}
		setController(newController);
	}

	public void dropController(BlockState state) {
		if (user != null && level instanceof ServerLevel serverLevel && serverLevel.getEntity(user) instanceof ServerPlayer player) {
			stopUsing(player);
			LinkedControllerHandler.deactivateInLectern(player);
		}

		Direction dir = state.getValue(LecternControllerBlock.FACING);
		double x = worldPosition.getX() + 0.5 + 0.25 * dir.getStepX();
		double y = worldPosition.getY() + 1;
		double z = worldPosition.getZ() + 0.5 + 0.25 * dir.getStepZ();
		ItemEntity itementity = new ItemEntity(level, x, y, z, createLinkedController());
		itementity.setDefaultPickUpDelay();
		level.addFreshEntity(itementity);
		controllerData = ItemContainerContents.EMPTY;
	}

	@Override
	public void destroy() {
		super.destroy();
		if (!level.isClientSide())
			dropController(getBlockState());
	}

	public static boolean playerInRange(Player player, Level world, BlockPos pos) {
		double reach = 0.4 * player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
		return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < reach * reach;
	}

	private ItemStack createLinkedController() {
		ItemStack stack = new ItemStack(ToolsRegistry.LINKED_CONTROLLER);
		stack.set(ToolsRegistry.LINKED_CONTROLLER_ITEMS, controllerData);
		return stack;
	}
}

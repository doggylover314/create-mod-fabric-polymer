package dev.createpolymer.redstone.link.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.contraptions.actors.trainControls.ControlsServerHandler;
import dev.createpolymer.equipment.ToolsSounds;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.redstone.link.LinkBehaviour;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.registry.feature.ToolsRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Create's LinkedControllerClientHandler and the linked controller packets, on the server. A vanilla client sends no
 * controller packets, but it always reports which movement keys are held ({@link ServerPlayer#getLastClientInput()}),
 * so the six buttons are read from there every tick, the same way the train controls are:
 * <ul>
 * <li>0 forward (W), 1 back (S), 2 left (A), 3 right (D), 4 jump (space), 5 sneak (shift): the order of Create's
 * controls</li>
 * </ul>
 * Create stops the keys from moving the player while the controller is on. A vanilla client cannot be told that, so
 * the player is held still instead: movement speed and jump strength are set to zero for as long as the controller
 * is on (transient attribute modifiers, which are never saved).
 * <p>
 * The controller is on from the click that toggles it until it is toggled off, put away, or something else (a
 * screen, a death, the lectern being left) interrupts it.
 */
public class LinkedControllerHandler {

	public enum Mode {
		IDLE, ACTIVE, BIND
	}

	public static final int PACKET_RATE = 5;
	private static final Identifier FREEZE = CreatePolymer.id("linked_controller_freeze");

	/** What Create keeps in the static fields of its client handler, for one player. */
	public static class Session {
		public Mode mode = Mode.IDLE;
		public Set<Integer> currentlyPressed = new HashSet<>();
		public @Nullable BlockPos lecternPos;
		public BlockPos selectedLocation = BlockPos.ZERO;
		public int packetCooldown;
		/** the keys held in the previous tick */
		Set<Integer> lastKeys = new HashSet<>();

		public boolean inLectern() {
			return lecternPos != null;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static boolean registered;

	public static void init() {
		if (registered)
			return;
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(LinkedControllerHandler::tick);
		ServerTickEvents.END_LEVEL_TICK.register(LinkedControllerServerHandler::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer()));
		UseBlockCallback.EVENT.register(LinkedControllerHandler::onItemUseFirst);
	}

	public static Session session(Player player) {
		return SESSIONS.computeIfAbsent(player.getUUID(), $ -> new Session());
	}

	public static Mode getMode(Player player) {
		Session session = SESSIONS.get(player.getUUID());
		return session == null ? Mode.IDLE : session.mode;
	}

	/** Whether the player has this controller (or a lectern) switched on. */
	public static boolean isActive(Player player, ItemStack stack) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || session.mode == Mode.IDLE || session.inLectern())
			return false;
		ItemStack main = player.getMainHandItem();
		if (main.getItem() instanceof LinkedControllerItem)
			return ItemStack.matches(main, stack);
		return ItemStack.matches(player.getOffhandItem(), stack);
	}

	public static void toggleBindMode(ServerPlayer player, BlockPos location) {
		Session session = session(player);
		if (session.mode == Mode.IDLE) {
			session.mode = Mode.BIND;
			session.selectedLocation = location;
			freeze(player);
			player.sendOverlayMessage(Component.translatable("create.linked_controller.bind_mode")
				.withStyle(ChatFormatting.GOLD));
			player.sendSystemMessage(Component.translatable("create.linked_controller.press_keybind", (Object[]) keyNames())
				.withStyle(ChatFormatting.GRAY));
		} else {
			session.mode = Mode.IDLE;
			onReset(player, session);
		}
		refreshHeldItems(player);
	}

	public static void toggle(ServerPlayer player) {
		Session session = session(player);
		if (session.mode == Mode.IDLE) {
			session.mode = Mode.ACTIVE;
			session.lecternPos = null;
			freeze(player);
		} else {
			session.mode = Mode.IDLE;
			onReset(player, session);
		}
		refreshHeldItems(player);
	}

	public static void activateInLectern(ServerPlayer player, BlockPos lecternAt) {
		Session session = session(player);
		if (session.mode == Mode.IDLE) {
			session.mode = Mode.ACTIVE;
			session.lecternPos = lecternAt;
			freeze(player);
		}
	}

	public static void deactivateInLectern(ServerPlayer player) {
		Session session = session(player);
		if (session.mode == Mode.ACTIVE && session.inLectern()) {
			session.mode = Mode.IDLE;
			onReset(player, session);
		}
	}

	/** Switches the player's controller off, whatever it was doing. */
	public static void stop(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || session.mode == Mode.IDLE)
			return;
		session.mode = Mode.IDLE;
		onReset(player, session);
	}

	public static boolean isUsingLectern(Player player) {
		Session session = SESSIONS.get(player.getUUID());
		return session != null && session.mode == Mode.ACTIVE && session.inLectern();
	}

	/** Create: onReset. Lets go of the keys and tells the lectern the player stopped. */
	protected static void onReset(ServerPlayer player, Session session) {
		unfreeze(player);
		session.packetCooldown = 0;
		session.selectedLocation = BlockPos.ZERO;

		BlockPos lecternPos = session.lecternPos;
		if (lecternPos != null && player.level().getBlockEntity(lecternPos) instanceof LecternControllerBlockEntity lectern)
			lectern.tryStopUsing(player);
		session.lecternPos = null;

		// the controller may be gone already (put away, taken from the lectern), so every button is let go
		if (!session.currentlyPressed.isEmpty())
			LinkedControllerServerHandler.releaseAll(player.level(), player.getUUID());
		session.currentlyPressed.clear();
		session.lastKeys.clear();
	}

	private static void forget(ServerPlayer player) {
		Session session = SESSIONS.remove(player.getUUID());
		if (session == null)
			return;
		session.mode = Mode.IDLE;
		onReset(player, session);
	}

	private static void freeze(ServerPlayer player) {
		freeze(player, Attributes.MOVEMENT_SPEED);
		freeze(player, Attributes.JUMP_STRENGTH);
	}

	private static void freeze(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null && !instance.hasModifier(FREEZE))
			instance.addTransientModifier(new AttributeModifier(FREEZE, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void unfreeze(ServerPlayer player) {
		for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance != null)
				instance.removeModifier(FREEZE);
		}
	}

	/** The item model changes with the controller being on. */
	private static void refreshHeldItems(ServerPlayer player) {
		player.inventoryMenu.sendAllDataToRemote();
	}

	private static Component[] keyNames() {
		return new Component[] { Component.translatable("key.forward"), Component.translatable("key.back"),
			Component.translatable("key.left"), Component.translatable("key.right"), Component.translatable("key.jump"),
			Component.translatable("key.sneak") };
	}

	public static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty())
			return;
		for (UUID id : List.copyOf(SESSIONS.keySet())) {
			ServerPlayer player = server.getPlayerList()
				.getPlayer(id);
			Session session = SESSIONS.get(id);
			if (session == null)
				continue;
			if (player == null) {
				SESSIONS.remove(id);
				continue;
			}
			if (session.mode == Mode.IDLE)
				continue;
			tick(player, session);
		}
	}

	/** Create: LinkedControllerClientHandler.tick */
	public static void tick(ServerPlayer player, Session session) {
		if (session.mode == Mode.IDLE)
			return;
		if (session.packetCooldown > 0)
			session.packetCooldown--;

		if (player.isSpectator() || !player.isAlive()) {
			session.mode = Mode.IDLE;
			onReset(player, session);
			return;
		}

		ItemStack heldItem = ItemStack.EMPTY;
		if (!session.inLectern()) {
			heldItem = player.getMainHandItem();
			if (!(heldItem.getItem() instanceof LinkedControllerItem)) {
				heldItem = player.getOffhandItem();
				if (!(heldItem.getItem() instanceof LinkedControllerItem)) {
					session.mode = Mode.IDLE;
					onReset(player, session);
					refreshHeldItems(player);
					return;
				}
			}
		}

		LecternControllerBlockEntity lectern = null;
		if (session.inLectern()) {
			if (player.level().getBlockEntity(session.lecternPos) instanceof LecternControllerBlockEntity be && be.isUsedBy(player)) {
				lectern = be;
				heldItem = be.getController();
			} else {
				deactivateInLectern(player);
				return;
			}
		}

		// a screen, in Create's words: anything but the inventory being open
		if (player.containerMenu != player.inventoryMenu) {
			session.mode = Mode.IDLE;
			onReset(player, session);
			refreshHeldItems(player);
			return;
		}

		Set<Integer> pressedKeys = new HashSet<>(ControlsServerHandler.keysOf(player.getLastClientInput()));

		Set<Integer> newKeys = new HashSet<>(pressedKeys);
		Set<Integer> releasedKeys = new HashSet<>(session.currentlyPressed);
		newKeys.removeAll(session.currentlyPressed);
		releasedKeys.removeAll(pressedKeys);

		if (session.mode == Mode.ACTIVE) {
			// Released Keys
			if (!releasedKeys.isEmpty()) {
				handleInput(player, session.lecternPos, heldItem, releasedKeys, false);
				ToolsSounds.controllerClick(player, 1f, .5f);
			}

			// Newly Pressed Keys
			if (!newKeys.isEmpty()) {
				handleInput(player, session.lecternPos, heldItem, newKeys, true);
				session.packetCooldown = PACKET_RATE;
				ToolsSounds.controllerClick(player, 1f, .75f);
			}

			// Keepalive Pressed Keys
			if (session.packetCooldown == 0) {
				if (!pressedKeys.isEmpty()) {
					handleInput(player, session.lecternPos, heldItem, pressedKeys, true);
					session.packetCooldown = PACKET_RATE;
				}
			}
		}

		if (session.mode == Mode.BIND) {
			for (Integer integer : newKeys) {
				LinkBehaviour linkBehaviour =
					BlockEntityBehaviour.get(player.level(), session.selectedLocation, LinkBehaviour.TYPE);
				if (linkBehaviour != null) {
					bind(player, heldItem, integer, session.selectedLocation);
					player.sendOverlayMessage(Component.translatable("create.linked_controller.key_bound", keyNames()[integer]));
				}
				session.mode = Mode.IDLE;
				onReset(player, session);
				refreshHeldItems(player);
				return;
			}
		}

		session.currentlyPressed = pressedKeys;
	}

	/** Press or release buttons of a controller, as Create's LinkedControllerInputPacket does. */
	public static void handleInput(ServerPlayer player, @Nullable BlockPos lecternPos, ItemStack controller,
		Collection<Integer> buttons, boolean press) {
		Level world = player.level();
		UUID uniqueID = player.getUUID();
		BlockPos pos = player.blockPosition();

		if (player.isSpectator() && press)
			return;

		LinkedControllerServerHandler.receivePressed(world, pos, uniqueID, buttons.stream()
			.map(i -> LinkedControllerItem.toFrequency(controller, i))
			.collect(Collectors.toList()), press);
	}

	/** Create: LinkedControllerBindPacket.handleItem */
	public static void bind(ServerPlayer player, ItemStack heldItem, int button, BlockPos linkLocation) {
		if (player.isSpectator())
			return;

		ItemStackHandler frequencyItems = LinkedControllerItem.getFrequencyItems(heldItem);
		LinkBehaviour linkBehaviour = BlockEntityBehaviour.get(player.level(), linkLocation, LinkBehaviour.TYPE);
		if (linkBehaviour == null)
			return;

		linkBehaviour.getNetworkKey()
			.forEachWithContext((f, first) -> frequencyItems.setStackInSlot(button * 2 + (first ? 0 : 1), f.getStack()
				.copy()));

		heldItem.set(ToolsRegistry.LINKED_CONTROLLER_ITEMS,
			dev.createpolymer.foundation.item.ItemHelper.containerContentsFromHandler(frequencyItems));
	}

	/**
	 * Create's LinkedControllerItem.onItemUseFirst for the clicks on blocks: bind mode on a redstone link, a controller
	 * onto an empty lectern, and swapping the controller of a lectern (sneaking).
	 */
	private static InteractionResult onItemUseFirst(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof LinkedControllerItem))
			return InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		BlockState hitState = world.getBlockState(pos);

		if (!player.mayBuild())
			return InteractionResult.PASS;

		if (player.isShiftKeyDown()) {
			if (hitState.is(ToolsRegistry.LECTERN_CONTROLLER)) {
				if (!world.isClientSide())
					ToolsRegistry.LECTERN_CONTROLLER.withBlockEntityDo(world, pos, be -> be.swapControllers(stack, player, hand, hitState));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}

		if (hitState.is(RedstoneRegistry.REDSTONE_LINK)) {
			if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer)
				toggleBindMode(serverPlayer, pos);
			player.getCooldowns()
				.addCooldown(stack, 2);
			return InteractionResult.SUCCESS;
		}

		if (hitState.is(Blocks.LECTERN) && !hitState.getValue(LecternBlock.HAS_BOOK)) {
			if (!world.isClientSide()) {
				ItemStack lecternStack = player.isCreative() ? stack.copy() : stack.split(1);
				ToolsRegistry.LECTERN_CONTROLLER.replaceLectern(hitState, world, pos, lecternStack);
			}
			return InteractionResult.SUCCESS;
		}

		if (hitState.is(ToolsRegistry.LECTERN_CONTROLLER))
			return InteractionResult.PASS;

		return InteractionResult.PASS;
	}
}

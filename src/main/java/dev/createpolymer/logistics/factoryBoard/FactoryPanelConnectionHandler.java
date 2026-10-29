package dev.createpolymer.logistics.factoryBoard;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import dev.createpolymer.registry.feature.FactoryGaugeRegistry;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.VecHelper;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of Create's FactoryPanelConnectionHandler. Create keeps the gauge a player is connecting from in the
 * client, outlines it, and sends a packet when the second gauge is clicked. Here the choice is kept per player on the
 * server: the buttons "Add new connection" and "Move this gauge" of the gauge's GUI start it, the next right-click
 * on a block decides it, and particles take the place of the outline.
 * <ul>
 * <li>Connecting: right-click another gauge to use its item as an ingredient of the first one, or a redstone
 * link or display link to connect it to the first gauge.</li>
 * <li>Relocating: right-click the surface the gauge should move to.</li>
 * <li>Sneaking, right-clicking anything else, walking more than 16 blocks away: the selection is dropped.</li>
 * </ul>
 */
public class FactoryPanelConnectionHandler {

	private record Selection(FactoryPanelPosition from, boolean relocating, Identifier dimension) {
	}

	private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
	private static boolean initialized;

	public static void init() {
		if (initialized)
			return;
		initialized = true;

		// before value boxes and the gauge's own interactions, as the client cancels the event first in Create
		Identifier early = Identifier.fromNamespaceAndPath(CreatePolymer.MOD_ID, "factory_gauge_connect_early");
		UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		UseBlockCallback.EVENT.register(early, FactoryPanelConnectionHandler::onRightClick);
		ServerTickEvents.END_SERVER_TICK.register(FactoryPanelConnectionHandler::tick);
	}

	public static void startRelocating(ServerPlayer player, FactoryPanelBehaviour behaviour) {
		start(player, behaviour, true);
	}

	public static void startConnection(ServerPlayer player, FactoryPanelBehaviour behaviour) {
		start(player, behaviour, false);
	}

	private static void start(ServerPlayer player, FactoryPanelBehaviour behaviour, boolean relocating) {
		SELECTIONS.put(player.getUUID(), new Selection(behaviour.getPanelPosition(), relocating,
			player.level()
				.dimension()
				.identifier()));
		showPrompt(player, relocating);
	}

	public static boolean isConnecting(Player player) {
		return SELECTIONS.containsKey(player.getUUID());
	}

	@Nullable
	public static FactoryPanelPosition connectingFrom(Player player) {
		Selection selection = SELECTIONS.get(player.getUUID());
		return selection == null ? null : selection.from();
	}

	public static void abort(Player player) {
		SELECTIONS.remove(player.getUUID());
	}

	private static void showPrompt(ServerPlayer player, boolean relocating) {
		player.sendOverlayMessage(translate(
			relocating ? "factory_panel.click_to_relocate" : "factory_panel.click_second_panel"));
	}

	private static void clear(ServerPlayer player, @Nullable Component message) {
		SELECTIONS.remove(player.getUUID());
		player.sendOverlayMessage(message == null ? Component.empty() : message);
	}

	public static InteractionResult onRightClick(Player player, Level level, InteractionHand hand,
		BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.PASS;
		Selection selection = SELECTIONS.get(player.getUUID());
		if (selection == null)
			return InteractionResult.PASS;
		if (!selection.dimension()
			.equals(level.dimension()
				.identifier())) {
			clear(serverPlayer, null);
			return InteractionResult.PASS;
		}

		FactoryPanelPosition connectingFrom = selection.from();
		FactoryPanelBehaviour at = FactoryPanelBehaviour.at(level, connectingFrom);
		if (at == null) {
			clear(serverPlayer, null);
			return InteractionResult.PASS;
		}

		if (player.isShiftKeyDown()) {
			if (selection.relocating())
				clear(serverPlayer, translate("factory_panel.relocation_aborted"));
			else
				clear(serverPlayer, translate("factory_panel.connection_aborted"));
			return InteractionResult.SUCCESS;
		}

		BlockPos pos = hit.getBlockPos();
		BlockState blockState = level.getBlockState(pos);

		if (selection.relocating())
			return relocate(serverPlayer, level, hit, at, connectingFrom);

		// Connecting two gauges
		if (blockState.getBlock() instanceof FactoryPanelBlock) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (!(blockEntity instanceof FactoryPanelBlockEntity fpbe))
				return InteractionResult.PASS;
			Vec3 location = FactoryPanelBlock.pickLocation(player, level, pos, blockState, hit.getLocation());
			PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, blockState, location);
			FactoryPanelBehaviour panel = fpbe.panels.get(slot);
			// a free slot is for placing a gauge, not for connecting
			if (panel == null || !panel.isActive())
				return InteractionResult.PASS;
			panelClicked(serverPlayer, level, panel, at, connectingFrom);
			return InteractionResult.SUCCESS;
		}

		// Connecting redstone or display links
		FactoryPanelSupportBehaviour behaviour =
			BlockEntityBehaviour.get(level, pos, FactoryPanelSupportBehaviour.TYPE);
		if (behaviour != null) {
			String checkForIssues = checkForIssues(at, behaviour);
			if (checkForIssues != null) {
				clear(serverPlayer, translate(checkForIssues).withStyle(ChatFormatting.RED));
				FactoryGaugeRegistry.deny(level, player.blockPosition());
				return InteractionResult.SUCCESS;
			}

			FactoryPanelPosition bestPosition = null;
			double bestDistance = Double.POSITIVE_INFINITY;

			for (PanelSlot slot : PanelSlot.values()) {
				FactoryPanelPosition panelPosition = new FactoryPanelPosition(pos, slot);
				FactoryPanelConnection connection = new FactoryPanelConnection(panelPosition, 1);
				Vec3 diff = connection.calculatePathDiff(level.getBlockState(connectingFrom.pos()), connectingFrom);
				if (bestDistance < diff.lengthSqr())
					continue;
				bestDistance = diff.lengthSqr();
				bestPosition = panelPosition;
			}

			new FactoryPanelConnectionPacket(bestPosition, connectingFrom, false).applySettings(serverPlayer);

			SELECTIONS.remove(player.getUUID());
			serverPlayer.sendOverlayMessage(translate("factory_panel.link_connected", blockState.getBlock()
				.getName())
				.withStyle(ChatFormatting.GREEN));
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS,
				0.5f, 0.5f);
			return InteractionResult.SUCCESS;
		}

		// Anything else drops the connection
		clear(serverPlayer, translate("factory_panel.connection_aborted"));
		return InteractionResult.SUCCESS;
	}

	/** Create's panelClicked: the clicked gauge becomes an ingredient of the one the player started from. */
	private static void panelClicked(ServerPlayer player, Level level, FactoryPanelBehaviour panel,
		FactoryPanelBehaviour at, FactoryPanelPosition connectingFrom) {
		if (panel.getPanelPosition()
			.equals(connectingFrom)) {
			clear(player, null);
			return;
		}

		String checkForIssues = checkForIssues(at, panel);
		if (checkForIssues != null) {
			clear(player, translate(checkForIssues).withStyle(ChatFormatting.RED));
			FactoryGaugeRegistry.deny(level, player.blockPosition());
			return;
		}

		ItemStack filterFrom = panel.getFilter();
		ItemStack filterTo = at.getFilter();

		new FactoryPanelConnectionPacket(panel.getPanelPosition(), connectingFrom, false).applySettings(player);

		SELECTIONS.remove(player.getUUID());
		player.sendOverlayMessage(translate("factory_panel.panels_connected", filterFrom.getHoverName()
			.getString(),
			filterTo.getHoverName()
				.getString())
			.withStyle(ChatFormatting.GREEN));
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f,
			0.5f);
	}

	/** Create's clientTick finds the target under the cursor as the player looks; here it is the clicked spot. */
	private static InteractionResult relocate(ServerPlayer player, Level level, BlockHitResult hit,
		FactoryPanelBehaviour at, FactoryPanelPosition connectingFrom) {
		BlockPos clicked = hit.getBlockPos();
		BlockState blockState = at.blockEntity.getBlockState();
		BlockState clickedState = level.getBlockState(clicked);

		BlockPos pos;
		Vec3 offsetPos;
		if (clickedState.is(FactoryGaugeRegistry.FACTORY_GAUGE)) {
			// another panel slot of a block with gauges: the player sees a barrier, so cast the ray again
			pos = clicked;
			offsetPos = FactoryPanelBlock.pickLocation(player, level, pos, clickedState, hit.getLocation());
		} else {
			offsetPos = hit.getLocation()
				.add(Vec3.atLowerCornerOf(hit.getDirection()
					.getUnitVec3i())
					.scale(1 / 32f));
			pos = BlockPos.containing(offsetPos);
		}
		PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, blockState, offsetPos);
		BlockPos diff = pos.subtract(connectingFrom.pos());
		Direction facing = FactoryPanelBlock.connectedDirection(blockState);

		boolean valid = facing.getAxis()
			.choose(diff.getX(), diff.getY(), diff.getZ()) == 0
			&& canSurviveAt(blockState, level, pos)
			&& !level.getBlockState(pos.relative(facing.getOpposite()))
				.is(PackagesRegistry.PACKAGER);

		if (!valid) {
			clear(player, translate("factory_panel.relocation_aborted"));
			return InteractionResult.SUCCESS;
		}

		SELECTIONS.remove(player.getUUID());
		new FactoryPanelConnectionPacket(new FactoryPanelPosition(pos, slot), connectingFrom, true)
			.applySettings(player);
		return InteractionResult.SUCCESS;
	}

	private static boolean canSurviveAt(BlockState gaugeState, Level level, BlockPos pos) {
		return FactoryPanelBlock.canAttachLenient(level, pos, FactoryPanelBlock.connectedDirection(gaugeState)
			.getOpposite());
	}

	@Nullable
	private static String checkForIssues(FactoryPanelBehaviour from, FactoryPanelBehaviour to) {
		if (from == null)
			return "factory_panel.connection_aborted";
		if (from.targetedBy.containsKey(to.getPanelPosition()))
			return "factory_panel.already_connected";
		if (from.targetedBy.size() >= 9)
			return "factory_panel.cannot_add_more_inputs";

		BlockState state1 = to.blockEntity.getBlockState();
		BlockState state2 = from.blockEntity.getBlockState();
		BlockPos diff = to.getPos()
			.subtract(from.getPos());

		if (state1.setValue(FactoryPanelBlock.WATERLOGGED, false)
			.setValue(FactoryPanelBlock.POWERED, false) != state2.setValue(FactoryPanelBlock.WATERLOGGED, false)
				.setValue(FactoryPanelBlock.POWERED, false))
			return "factory_panel.same_orientation";

		if (FactoryPanelBlock.connectedDirection(state1)
			.getAxis()
			.choose(diff.getX(), diff.getY(), diff.getZ()) != 0)
			return "factory_panel.same_surface";

		if (!diff.closerThan(BlockPos.ZERO, 16))
			return "factory_panel.too_far_apart";

		if (to.panelBE().restocker)
			return "factory_panel.input_in_restock_mode";

		if (to.getFilter()
			.isEmpty()
			|| from.getFilter()
				.isEmpty())
			return "factory_panel.no_item";

		return null;
	}

	@Nullable
	private static String checkForIssues(FactoryPanelBehaviour from, FactoryPanelSupportBehaviour to) {
		if (from == null)
			return "factory_panel.connection_aborted";

		BlockState state1 = from.blockEntity.getBlockState();
		BlockState state2 = to.blockEntity.getBlockState();
		BlockPos diff = to.getPos()
			.subtract(from.getPos());
		Direction connectedDirection = FactoryPanelBlock.connectedDirection(state1);

		if (connectedDirection != state2.getOptionalValue(BlockStateProperties.FACING)
			.orElse(connectedDirection))
			return "factory_panel.same_orientation";

		if (connectedDirection.getAxis()
			.choose(diff.getX(), diff.getY(), diff.getZ()) != 0)
			return "factory_panel.same_surface";

		if (!diff.closerThan(BlockPos.ZERO, 16))
			return "factory_panel.too_far_apart";

		return null;
	}

	private static void tick(MinecraftServer server) {
		if (SELECTIONS.isEmpty())
			return;
		long time = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Selection>> iterator = SELECTIONS.entrySet()
			.iterator(); iterator.hasNext();) {
			Map.Entry<UUID, Selection> entry = iterator.next();
			ServerPlayer player = server.getPlayerList()
				.getPlayer(entry.getKey());
			if (player == null) {
				iterator.remove();
				continue;
			}

			Selection selection = entry.getValue();
			ServerLevel level = player.level();
			FactoryPanelBehaviour at = level.dimension()
				.identifier()
				.equals(selection.dimension()) ? FactoryPanelBehaviour.at(level, selection.from()) : null;
			if (at == null || !selection.from()
				.pos()
				.closerThan(player.blockPosition(), 16)) {
				iterator.remove();
				player.sendOverlayMessage(Component.empty());
				continue;
			}

			// Create outlines the gauge and keeps the hint in the action bar
			if (time % 20 == 0)
				showPrompt(player, selection.relocating());
			if (time % 4 == 0)
				drawOutline(player, level, getBB(at.blockEntity.getBlockState(), selection.from()), time);
		}
	}

	private static void drawOutline(ServerPlayer player, ServerLevel level, AABB box, long time) {
		DustParticleOptions particle = new DustParticleOptions(time % 16 > 8 ? 0x38b764 : 0xa7f070, .6f);
		for (int corner = 0; corner < 8; corner++) {
			double x = (corner & 1) == 0 ? box.minX : box.maxX;
			double y = (corner & 2) == 0 ? box.minY : box.maxY;
			double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
			level.sendParticles(player, particle, false, true, x, y, z, 1, 0, 0, 0, 0);
		}
	}

	public static AABB getBB(BlockState blockState, FactoryPanelPosition factoryPanelPosition) {
		Vec3 location = FactoryPanelSlotPositioning.getCenterOfSlot(blockState, factoryPanelPosition.slot())
			.add(Vec3.atLowerCornerOf(factoryPanelPosition.pos()));
		Vec3 plane = VecHelper.axisAlingedPlaneOf(FactoryPanelBlock.connectedDirection(blockState));
		return new AABB(location, location).inflate(plane.x * 3 / 16f, plane.y * 3 / 16f, plane.z * 3 / 16f);
	}

	private static MutableComponent translate(String key, Object... args) {
		return Lang.builder(CreatePolymer.MOD_ID)
			.translate(key, args)
			.component();
	}

}

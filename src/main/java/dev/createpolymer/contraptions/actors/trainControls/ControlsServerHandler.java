package dev.createpolymer.contraptions.actors.trainControls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.trains.TrackNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;

/**
 * Drives the contraption a player controls. Create's client sends the pressed keys in packets and this handler
 * receives them; a vanilla client has no such packets, but it always tells the server which movement keys are held
 * ({@link ServerPlayer#getLastClientInput()}), so the held keys are read from there every tick instead:
 * <ul>
 * <li>0 forward (W), 1 back (S), 2 left (A), 3 right (D)</li>
 * <li>4 jump (space): approach the next station, as in Create</li>
 * <li>5 sneak (shift): let go of the controls</li>
 * </ul>
 * The player drives while sitting in a seat next to the controls (a standing player would walk with the keys too).
 */
public class ControlsServerHandler {

	private static final Map<UUID, ControlsContext> controllers = new HashMap<>();

	/** The held keys of a vanilla client input, numbered like Create's control keys. */
	public static List<Integer> keysOf(Input input) {
		List<Integer> keys = new ArrayList<>(6);
		if (input.forward())
			keys.add(0);
		if (input.backward())
			keys.add(1);
		if (input.left())
			keys.add(2);
		if (input.right())
			keys.add(3);
		if (input.jump())
			keys.add(4);
		if (input.shift())
			keys.add(5);
		return keys;
	}

	public static void startControlling(AbstractContraptionEntity entity, BlockPos controlsLocalPos, ServerPlayer player) {
		controllers.put(player.getUUID(), new ControlsContext(entity, controlsLocalPos));
		player.sendOverlayMessage(
			TrackNbt.translateDirect("contraption.controls.start_controlling", entity.getContraptionName()));
	}

	public static boolean isControlling(UUID player) {
		return controllers.containsKey(player);
	}

	public static void tick(MinecraftServer server) {
		for (Iterator<Entry<UUID, ControlsContext>> iterator = controllers.entrySet()
			.iterator(); iterator.hasNext();) {
			Entry<UUID, ControlsContext> entry = iterator.next();
			ControlsContext ctx = entry.getValue();
			ServerPlayer player = server.getPlayerList()
				.getPlayer(entry.getKey());

			if (ctx.entity.isRemoved()) {
				iterator.remove();
				if (player != null)
					notifyStopped(player);
				continue;
			}

			if (player == null) {
				ctx.entity.stopControlling(ctx.controlsLocalPos);
				iterator.remove();
				continue;
			}

			// somebody else took the controls, or the contraption let go
			if (!ctx.entity.getControllingPlayer()
				.map(entry.getKey()::equals)
				.orElse(false)) {
				iterator.remove();
				notifyStopped(player);
				continue;
			}

			if (!ctx.entity.control(ctx.controlsLocalPos, keysOf(player.getLastClientInput()), player)) {
				ctx.entity.stopControlling(ctx.controlsLocalPos);
				iterator.remove();
				notifyStopped(player);
			}
		}
	}

	private static void notifyStopped(ServerPlayer player) {
		player.sendOverlayMessage(TrackNbt.translateDirect("contraption.controls.stop_controlling"));
	}

	public static void clear() {
		controllers.clear();
	}

	static class ControlsContext {

		AbstractContraptionEntity entity;
		BlockPos controlsLocalPos;

		public ControlsContext(AbstractContraptionEntity entity, BlockPos controlsPos) {
			this.entity = entity;
			controlsLocalPos = controlsPos;
		}

	}

}

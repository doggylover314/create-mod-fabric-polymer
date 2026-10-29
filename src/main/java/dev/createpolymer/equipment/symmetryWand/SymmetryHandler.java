package dev.createpolymer.equipment.symmetryWand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.equipment.symmetryWand.mirror.EmptyMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.SymmetryMirror;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import org.joml.Quaternionf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's SymmetryHandler (MIT, The Create Team): reacts to the blocks players place and break, and
 * shows the mirrors. Fabric has no event for placed blocks, which Create gets from NeoForge's EntityPlaceEvent:
 * <ul>
 * <li>Placing: {@link #onItemUseOn} is registered on Fabric's ItemEvents.USE_ON, which wraps the call of
 * Item#useOn in ItemStack#useOn. When a player with a wand in the hotbar uses a block item, it runs the use itself
 * (what the wrapped call would have done) and then asks the wand to mirror the block that appeared. Blocks that
 * players place any other way than by using an item on a block do not mirror.</li>
 * <li>Breaking: PlayerBlockBreakEvents.AFTER, see {@link #onBlockDestroyed}.</li>
 * <li>The mirror is shown to the holder, and only to them, by a display entity above the block of the mirror with
 * Create's model; the particles that rise from it are sent to the holder as well.</li>
 * </ul>
 */
public class SymmetryHandler {

	private static final Map<UUID, Map<Integer, MirrorDisplay>> DISPLAYS = new HashMap<>();
	private static int tickCounter = 0;

	/**
	 * Fabric's ItemEvents.USE_ON callback: returns null to leave the use to the game, or the result of the use that
	 * was run here.
	 */
	public static @Nullable InteractionResult onItemUseOn(UseOnContext context) {
		if (!(context.getPlayer() instanceof ServerPlayer player) || context.getLevel().isClientSide())
			return null;
		ItemStack stack = context.getItemInHand();
		if (!(stack.getItem() instanceof BlockItem))
			return null;
		if (!SymmetryWandItem.presentInHotbar(player))
			return null;

		Level level = context.getLevel();
		BlockPos pos = new BlockPlaceContext(context).getClickedPos();
		BlockState before = level.getBlockState(pos);
		InteractionResult result = stack.getItem()
			.useOn(context);
		if (result.consumesAction()) {
			BlockState placed = level.getBlockState(pos);
			if (placed != before && !placed.isAir())
				onBlockPlaced(player, pos, placed);
		}
		return result;
	}

	/** Create's onBlockPlaced: every wand in the hotbar mirrors the block */
	public static void onBlockPlaced(Player player, BlockPos pos, BlockState placed) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.getSelectionSize(); i++)
			if (SymmetryWandItem.isWand(inv.getItem(i)))
				SymmetryWandItem.apply(player.level(), inv.getItem(i), player, pos, placed);
	}

	/**
	 * Create's onBlockDestroyed, from Fabric's PlayerBlockBreakEvents.AFTER: every wand in the hotbar breaks the mirrored
	 * blocks.
	 */
	public static void onBlockDestroyed(Level level, Player player, BlockPos pos, BlockState state,
		@Nullable BlockEntity blockEntity) {
		if (level.isClientSide())
			return;

		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.getSelectionSize(); i++)
			if (SymmetryWandItem.isWand(inv.getItem(i)))
				SymmetryWandItem.remove(level, inv.getItem(i), player, pos, state);
	}

	/**
	 * Create's onRenderWorld and onClientTick: the display entities of the mirrors that are active in a player's
	 * hotbar, and the particles that rise from them every ten ticks.
	 */
	public static void tick(MinecraftServer server) {
		tickCounter++;
		for (ServerPlayer player : server.getPlayerList()
			.getPlayers()) {
			Inventory inv = player.getInventory();
			Map<Integer, MirrorDisplay> mine = DISPLAYS.get(player.getUUID());
			ServerLevel level = player.level();

			for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
				ItemStack stack = inv.getItem(slot);
				SymmetryMirror mirror = null;
				if (SymmetryWandItem.isWand(stack) && SymmetryWandItem.isEnabled(stack)) {
					mirror = SymmetryWandItem.getMirror(stack);
					if (mirror instanceof EmptyMirror)
						mirror = null;
				}

				MirrorDisplay display = mine == null ? null : mine.get(slot);
				if (mirror == null) {
					if (display != null) {
						display.destroy();
						mine.remove(slot);
					}
					continue;
				}

				if (display == null || !display.matches(player, level, mirror)) {
					if (display != null)
						display.destroy();
					if (mine == null)
						mine = DISPLAYS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
					display = new MirrorDisplay(player, level, mirror);
					mine.put(slot, display);
				}
				display.update(level.getGameTime());

				if (tickCounter % 10 == 0) {
					RandomSource random = level.getRandom();
					double offsetX = (random.nextDouble() - 0.5) * 0.3;
					double offsetZ = (random.nextDouble() - 0.5) * 0.3;

					Vec3 pos = mirror.getPosition()
						.add(0.5 + offsetX, 1 / 4d, 0.5 + offsetZ);
					level.sendParticles(player, ParticleTypes.END_ROD, false, false, pos.x, pos.y, pos.z, 0, 0,
						random.nextDouble() * 1 / 8f, 0, 1);
				}
			}

			if (mine != null && mine.isEmpty())
				DISPLAYS.remove(player.getUUID());
		}
	}

	public static void remove(ServerPlayer player) {
		Map<Integer, MirrorDisplay> mine = DISPLAYS.remove(player.getUUID());
		if (mine != null)
			mine.values()
				.forEach(MirrorDisplay::destroy);
	}

	/** The mirror displays of a player, for tests */
	public static int displayCount(ServerPlayer player) {
		Map<Integer, MirrorDisplay> mine = DISPLAYS.get(player.getUUID());
		return mine == null ? 0 : mine.size();
	}

	/** Create's SymmetryEffectPacket: lines of particles from the mirror to the blocks that were placed or broken */
	public static void sendEffect(Level level, Player player, BlockPos mirror, List<BlockPos> positions) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		if (player.position()
			.distanceTo(Vec3.atLowerCornerOf(mirror)) > 100)
			return;
		for (BlockPos to : positions)
			drawEffect(serverLevel, mirror, to);
	}

	public static void drawEffect(ServerLevel level, BlockPos from, BlockPos to) {
		RandomSource random = level.getRandom();

		double density = 0.8f;
		Vec3 start = Vec3.atLowerCornerOf(from)
			.add(0.5, 0.5, 0.5);
		Vec3 end = Vec3.atLowerCornerOf(to)
			.add(0.5, 0.5, 0.5);
		Vec3 diff = end.subtract(start);
		if (diff.length() < 1e-6)
			return;

		Vec3 step = diff.normalize()
			.scale(density);
		int steps = (int) (diff.length() / step.length());

		DustParticleOptions dust = new DustParticleOptions(0xFFFFFF, 1);
		for (int i = 3; i < steps - 1; i++) {
			Vec3 pos = start.add(step.scale(i));
			level.sendParticles(dust, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		}

		Vec3 pos = start.add(step.scale(2));
		level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0, 0, random.nextDouble() * 1 / 32f, 0, 1);

		pos = start.add(step.scale(steps));
		level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0, 0, random.nextDouble() * 1 / 32f, 0, 1);
	}

	/**
	 * The model of a mirror, floating over its block: Create's model (block/symmetry_effect/...) turned the way the
	 * orientation of the mirror says, bobbing like Create's mirror does. Only the holder is a watcher.
	 */
	private static class MirrorDisplay extends ElementHolder {
		private final ServerPlayer player;
		private final ServerLevel level;
		private final SymmetryMirror mirror;
		private final Vec3 center;
		private final Part part;

		MirrorDisplay(ServerPlayer player, ServerLevel level, SymmetryMirror mirror) {
			this.player = player;
			this.level = level;
			this.mirror = mirror.copy();
			this.center = Vec3.atCenterOf(BlockPos.containing(mirror.getPosition()));

			part = new Part(ModelItems.block(mirror.getModelPath()));
			part.orientation(new Quaternionf().rotationY((float) Math.toRadians(mirror.getModelRotationDegrees())));
			part.translation(0, .2f, 0)
				.apply(0);
			addElement(part.element);

			new ManualAttachment(this, level, () -> center);
			startWatching(player);
		}

		boolean matches(ServerPlayer player, ServerLevel level, SymmetryMirror mirror) {
			return this.player == player && this.level == level && this.mirror.equals(mirror)
				&& this.mirror.typeName()
					.equals(mirror.typeName());
		}

		void update(long time) {
			if (time % 2 != 0) {
				tick();
				return;
			}
			float yShift = Mth.sin(time * (1 / 16f)) / 5f;
			part.translation(0, yShift + .2f, 0)
				.apply(2);
			tick();
		}
	}

}

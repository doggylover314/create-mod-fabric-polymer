package dev.createpolymer.equipment.zapper.terrainzapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.createpolymer.registry.feature.WandsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Create's WorldshaperRenderHandler shows the region the shaper would change as an outline cluster drawn by the
 * client. A vanilla client cannot draw outlines, so the edges of the region are sent to the holder, and only to
 * them, as small grey dust particles (Create's colour for the outline, 0xbfbfbf). The region is worked out every
 * {@value #INTERVAL} ticks, which is also how often the particles are refreshed (dust lives a few ticks to two
 * seconds); at most {@value #MAX_PARTICLES} particles are sent at a time, so a big region shows a sample of its edges.
 */
public class WorldshaperRenderHandler {

	public static final int INTERVAL = 5;
	public static final int MAX_PARTICLES = 300;
	private static final DustParticleOptions DUST = new DustParticleOptions(0xbfbfbf, .8f);

	private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();

	private record Key(ResourceKey<Level> dimension, BlockPos pos, Direction face, TerrainBrushes brush, BlockPos params,
		TerrainTools tool, PlacementOptions placement) {
	}

	private static class Preview {
		Key key;
		List<Vec3> points = List.of();
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList()
			.getPlayers()) {
			if (!(player.getMainHandItem()
				.getItem() instanceof WorldshaperItem)
				&& !(player.getOffhandItem()
					.getItem() instanceof WorldshaperItem)) {
				if (!PREVIEWS.isEmpty())
					PREVIEWS.remove(player.getUUID());
				continue;
			}
			if (player.tickCount % INTERVAL != 0)
				continue;
			gatherSelectedBlocks(player);
		}
	}

	public static void remove(ServerPlayer player) {
		PREVIEWS.remove(player.getUUID());
	}

	protected static void gatherSelectedBlocks(ServerPlayer player) {
		ItemStack heldMain = player.getMainHandItem();
		ItemStack heldOff = player.getOffhandItem();
		boolean zapperInMain = heldMain.getItem() instanceof WorldshaperItem;
		boolean zapperInOff = heldOff.getItem() instanceof WorldshaperItem;

		if (zapperInMain) {
			if (!heldMain.has(WandsRegistry.SHAPER_SWAP) || !zapperInOff) {
				createBrushOutline(player, heldMain);
				return;
			}
		}

		if (zapperInOff) {
			createBrushOutline(player, heldOff);
			return;
		}

		PREVIEWS.remove(player.getUUID());
	}

	public static void createBrushOutline(ServerPlayer player, ItemStack zapper) {
		if (!zapper.has(WandsRegistry.SHAPER_BRUSH_PARAMS)) {
			PREVIEWS.remove(player.getUUID());
			return;
		}

		TerrainBrushes brushType = zapper.getOrDefault(WandsRegistry.SHAPER_BRUSH, TerrainBrushes.Cuboid);
		Brush brush = brushType.get();
		PlacementOptions placement = zapper.getOrDefault(WandsRegistry.SHAPER_PLACEMENT_OPTIONS, PlacementOptions.Merged);
		TerrainTools tool = zapper.getOrDefault(WandsRegistry.SHAPER_TOOL, TerrainTools.Fill);
		BlockPos params = zapper.get(WandsRegistry.SHAPER_BRUSH_PARAMS);
		brush.set(brush.clampParam(0, params.getX()), brush.clampParam(1, params.getY()),
			brush.clampParam(2, params.getZ()));

		ServerLevel level = player.level();
		Vec3 start = player.position()
			.add(0, player.getEyeHeight(), 0);
		Vec3 range = player.getLookAngle()
			.scale(128);
		BlockHitResult raytrace =
			level.clip(new ClipContext(start, start.add(range), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (raytrace == null || raytrace.getType() == HitResult.Type.MISS) {
			PREVIEWS.remove(player.getUUID());
			return;
		}

		BlockPos pos = raytrace.getBlockPos()
			.offset(brush.getOffset(player.getLookAngle(), raytrace.getDirection(), placement));
		Key key = new Key(level.dimension(), pos, raytrace.getDirection(), brushType, params, tool, placement);

		Preview preview = PREVIEWS.computeIfAbsent(player.getUUID(), id -> new Preview());
		if (!key.equals(preview.key)) {
			preview.key = key;
			preview.points = sample(outline(
				brush.addToGlobalPositions(level, pos, raytrace.getDirection(), new ArrayList<>(), tool)));
		}

		for (Vec3 point : preview.points)
			level.sendParticles(player, DUST, true, true, point.x, point.y, point.z, 1, 0, 0, 0, 0);
	}

	/** The middle of every edge of the cluster of blocks, which is where its outline runs. */
	public static List<Vec3> outline(Collection<BlockPos> positions) {
		Set<BlockPos> blocks = new HashSet<>(positions);
		// edges are found from each of their faces, and counted once: keys are the doubled coordinates of the middle
		Set<BlockPos> edges = new HashSet<>();
		List<Vec3> points = new ArrayList<>();
		for (BlockPos p : blocks) {
			for (Direction face : Direction.values()) {
				if (blocks.contains(p.relative(face)))
					continue;
				for (Direction side : Direction.values()) {
					if (side.getAxis() == face.getAxis())
						continue;
					BlockPos neighbour = p.relative(side);
					// a corner of the cluster, or a concave one
					if (blocks.contains(neighbour) && !blocks.contains(neighbour.relative(face)))
						continue;
					BlockPos key = new BlockPos(2 * p.getX() + 1 + face.getStepX() + side.getStepX(),
						2 * p.getY() + 1 + face.getStepY() + side.getStepY(),
						2 * p.getZ() + 1 + face.getStepZ() + side.getStepZ());
					if (edges.add(key))
						points.add(new Vec3(key.getX() / 2d, key.getY() / 2d, key.getZ() / 2d));
				}
			}
		}
		return points;
	}

	private static List<Vec3> sample(List<Vec3> points) {
		if (points.size() <= MAX_PARTICLES)
			return points;
		List<Vec3> sampled = new ArrayList<>(MAX_PARTICLES);
		double stride = points.size() / (double) MAX_PARTICLES;
		for (int i = 0; i < MAX_PARTICLES; i++)
			sampled.add(points.get((int) (i * stride)));
		return sampled;
	}

	/** The points of the preview that the player's holder currently shows (for tests). */
	public static List<Vec3> previewPoints(ServerPlayer player) {
		Preview preview = PREVIEWS.get(player.getUUID());
		return preview == null ? List.of() : preview.points;
	}
}

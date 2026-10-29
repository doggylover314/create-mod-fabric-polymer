package dev.createpolymer.equipment.bell;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Create's soul pulse: a ring of positions that grows outwards from a haunted bell, a layer every few ticks. Create
 * draws it on the client; here the server spawns the soul particles on the dark spots of each layer.
 */
public class SoulPulseEffect {

	public static final int MAX_DISTANCE = 11;
	private static final List<List<BlockPos>> LAYERS = genLayers();

	private static final int WAITING_TICKS = 100;
	public static final int TICKS_PER_LAYER = 6;
	private int ticks;
	public final BlockPos pos;
	public final int distance;
	public final List<BlockPos> added;

	public SoulPulseEffect(BlockPos pos, int distance, boolean canOverlap) {
		this.ticks = TICKS_PER_LAYER * distance;
		this.pos = pos;
		this.distance = distance;
		this.added = canOverlap ? null : new ArrayList<>();
	}

	public boolean finished() {
		return ticks <= -WAITING_TICKS;
	}

	public boolean canOverlap() {
		return added == null;
	}

	public List<BlockPos> tick(Level world) {
		if (finished())
			return null;

		ticks--;
		if (ticks < 0 || ticks % TICKS_PER_LAYER != 0)
			return null;

		List<BlockPos> spawns = getPotentialSoulSpawns(world);
		while (spawns.isEmpty() && ticks > 0) {
			ticks -= TICKS_PER_LAYER;
			spawns.addAll(getPotentialSoulSpawns(world));
		}
		return spawns;
	}

	public int currentLayerIdx() {
		return distance - ticks / TICKS_PER_LAYER - 1;
	}

	public List<BlockPos> getPotentialSoulSpawns(Level world) {
		List<BlockPos> spawns = new ArrayList<>();
		getLayer(currentLayerIdx()).map(p -> p.offset(pos))
			.filter(p -> canSpawnSoulAt(world, p, true))
			.forEach(spawns::add);
		return spawns;
	}

	public static boolean isDark(Level world, BlockPos at) {
		return world.getBrightness(LightLayer.BLOCK, at) < 1;
	}

	public static boolean canSpawnSoulAt(Level world, BlockPos at, boolean ignoreLight) {
		EntityType<?> dummy = EntityTypes.ZOMBIE;
		double dummyWidth = 0.2, dummyHeight = 0.75;
		double w2 = dummyWidth / 2;

		if (world == null || !SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(world, at, dummy))
			return false;
		if (!ignoreLight && !isDark(world, at))
			return false;
		for (VoxelShape shape : world.getBlockCollisions(null,
			new AABB(at.getX() + 0.5 - w2, at.getY(), at.getZ() + 0.5 - w2, at.getX() + 0.5 + w2,
				at.getY() + dummyHeight, at.getZ() + 0.5 + w2))) {
			if (!shape.isEmpty())
				return false;
		}
		return true;
	}

	/** Create's spawnParticles, for the server: the overlapping pulses spawn flames, the others the dark spots only. */
	public void spawnParticles(ServerLevel world, BlockPos at) {
		var p = VecHelper.getCenterOf(at);
		if (canOverlap())
			world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		if (SoulPulseEffect.isDark(world, at))
			world.sendParticles(ParticleTypes.SOUL, p.x, p.y + 0.01, p.z, 1, 0, 0, 0, 0);
	}

	private static List<List<BlockPos>> genLayers() {
		List<List<BlockPos>> layers = new ArrayList<>();
		for (int i = 0; i < MAX_DISTANCE; i++)
			layers.add(new ArrayList<>());

		for (int x = 0; x < MAX_DISTANCE; x++) {
			for (int y = 0; y < MAX_DISTANCE; y++) {
				for (int z = 0; z < MAX_DISTANCE; z++) {
					BlockPos candidate = new BlockPos(x, y, z);

					int dist = (int) Math.round(Math.sqrt(candidate.distSqr(BlockPos.ZERO)));
					if (dist > MAX_DISTANCE)
						continue;
					if (dist <= 0)
						dist = 1;

					List<BlockPos> layer = layers.get(dist - 1);
					int start = layer.size(), end = start + 1;
					layer.add(candidate);

					if (candidate.getX() != 0) {
						layer.add(new BlockPos(-candidate.getX(), candidate.getY(), candidate.getZ()));
						end += 1;
					}
					if (candidate.getY() != 0) {
						for (int i = start; i < end; i++) {
							BlockPos prev = layer.get(i);
							layer.add(new BlockPos(prev.getX(), -prev.getY(), prev.getZ()));
						}
						end += end - start;
					}
					if (candidate.getZ() != 0) {
						for (int i = start; i < end; i++) {
							BlockPos prev = layer.get(i);
							layer.add(new BlockPos(prev.getX(), prev.getY(), -prev.getZ()));
						}
					}
				}
			}
		}

		return layers;
	}

	public static Stream<BlockPos> getLayer(int idx) {
		if (idx < 0 || idx >= MAX_DISTANCE)
			return Stream.empty();
		return LAYERS.get(idx).stream();
	}
}

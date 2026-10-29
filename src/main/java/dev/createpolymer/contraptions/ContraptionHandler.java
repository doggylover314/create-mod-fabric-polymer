package dev.createpolymer.contraptions;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import dev.createpolymer.util.WorldAttached;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public class ContraptionHandler {

	/* Global map of loaded contraptions */

	public static WorldAttached<Map<Integer, WeakReference<AbstractContraptionEntity>>> loadedContraptions;
	static WorldAttached<List<AbstractContraptionEntity>> queuedAdditions;

	static {
		loadedContraptions = new WorldAttached<>($ -> new HashMap<>());
		queuedAdditions = new WorldAttached<>($ -> ObjectLists.synchronize(new ObjectArrayList<>()));
	}

	/** Create hooks these up through NeoForge events; here they are Fabric's. */
	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(ContraptionHandler::tick);
		ServerTickEvents.END_LEVEL_TICK.register(ContraptionPlayerSupport::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> addSpawnedContraptionsToCollisionList(entity, world));
	}

	public static void tick(Level world) {
		Map<Integer, WeakReference<AbstractContraptionEntity>> map = loadedContraptions.get(world);
		List<AbstractContraptionEntity> queued = queuedAdditions.get(world);

		for (AbstractContraptionEntity contraptionEntity : queued)
			map.put(contraptionEntity.getId(), new WeakReference<>(contraptionEntity));
		queued.clear();

		Collection<WeakReference<AbstractContraptionEntity>> values = map.values();
		for (Iterator<WeakReference<AbstractContraptionEntity>> iterator = values.iterator(); iterator.hasNext();) {
			WeakReference<AbstractContraptionEntity> weakReference = iterator.next();
			AbstractContraptionEntity contraptionEntity = weakReference.get();
			if (contraptionEntity == null || !contraptionEntity.isAliveOrStale()) {
				iterator.remove();
				continue;
			}
			if (!contraptionEntity.isAlive()) {
				contraptionEntity.staleTicks--;
				continue;
			}

			ContraptionCollider.collideEntities(contraptionEntity);
		}
	}

	public static void addSpawnedContraptionsToCollisionList(Entity entity, Level world) {
		if (entity instanceof AbstractContraptionEntity)
			queuedAdditions.get(world)
				.add((AbstractContraptionEntity) entity);
	}

}

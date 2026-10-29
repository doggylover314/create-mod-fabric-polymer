package dev.createpolymer.registry.feature;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.logistics.depot.EjectorBlock;
import dev.createpolymer.logistics.depot.EjectorBlockEntity;
import dev.createpolymer.logistics.depot.EjectorItem;
import dev.createpolymer.logistics.depot.EjectorTargetHandler;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

/**
 * The weighted ejector. Its target selection is stored in the item as a server-only data component.
 */
public final class EjectorRegistry {
	private EjectorRegistry() {}

	/** Create's CKinetics.maxEjectorDistance: max distance in blocks a weighted ejector can throw. */
	public static int maxEjectorDistance = 32;
	/** Create's CKinetics.ejectorScanInterval: ticks until the next item launched by an ejector scans for collisions. */
	public static int ejectorScanInterval = 120;

	/** The block an ejector item was pointed at (sneak and use). Create keeps this in a client-side field. */
	public static final DataComponentType<GlobalPos> EJECTOR_TARGET = component("ejector_target",
		DataComponentType.<GlobalPos>builder()
			.persistent(GlobalPos.CODEC)
			.networkSynchronized(GlobalPos.STREAM_CODEC)
			.build());

	public static final EjectorBlock WEIGHTED_EJECTOR = ModBlocks.register("weighted_ejector", EjectorBlock::new,
		ModBlocks.stone().mapColor(MapColor.COLOR_GRAY));

	public static final BlockEntityType<EjectorBlockEntity> WEIGHTED_EJECTOR_BLOCK_ENTITY = ModBlockEntities.register("weighted_ejector",
		(pos, state) -> new EjectorBlockEntity(EjectorRegistry.WEIGHTED_EJECTOR_BLOCK_ENTITY, pos, state), WEIGHTED_EJECTOR);

	public static final Item WEIGHTED_EJECTOR_ITEM = ModItems.register("weighted_ejector",
		p -> new EjectorItem(WEIGHTED_EJECTOR, p), new Item.Properties().useBlockDescriptionPrefix());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	public static void init() {
		// Create: CStress.setImpact(2.0)
		BlockStressValues.setImpact(WEIGHTED_EJECTOR, 2.0);
		EjectorBlockEntity.registerCapabilities();
		EjectorTargetHandler.init();
	}
}

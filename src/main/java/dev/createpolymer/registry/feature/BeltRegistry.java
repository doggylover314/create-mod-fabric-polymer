package dev.createpolymer.registry.feature;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.kinetics.belt.BeltBlock;
import dev.createpolymer.kinetics.belt.BeltBlockEntity;
import dev.createpolymer.kinetics.belt.BeltHelper;
import dev.createpolymer.kinetics.belt.item.BeltConnectorItem;
import dev.createpolymer.logistics.tunnel.BeltTunnelBlock;
import dev.createpolymer.logistics.tunnel.BeltTunnelBlockEntity;
import dev.createpolymer.logistics.tunnel.BeltTunnelItem;
import dev.createpolymer.logistics.tunnel.BrassTunnelBlock;
import dev.createpolymer.logistics.tunnel.BrassTunnelBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Mechanical belts, the belt connector and the andesite and brass tunnels.
 * <p>
 * The belt connector stores the first clicked shaft in a data component under Create's id; like the filter's
 * components it is server-only (Polymer strips it from everything sent to players).
 */
public final class BeltRegistry {
	private BeltRegistry() {}

	public static final BeltBlock BELT = ModBlocks.register("belt", BeltBlock::new,
		BlockBehaviour.Properties.of()
			.sound(SoundType.WOOL)
			.strength(0.8f)
			.mapColor(MapColor.COLOR_GRAY));

	public static final BeltTunnelBlock ANDESITE_TUNNEL = ModBlocks.register("andesite_tunnel", BeltTunnelBlock::new,
		ModBlocks.stone().mapColor(MapColor.STONE));

	public static final BrassTunnelBlock BRASS_TUNNEL = ModBlocks.register("brass_tunnel", BrassTunnelBlock::new,
		ModBlocks.stone().mapColor(MapColor.TERRACOTTA_YELLOW));

	public static final BlockEntityType<BeltBlockEntity> BELT_BLOCK_ENTITY = ModBlockEntities.register("belt",
		(pos, state) -> new BeltBlockEntity(BeltRegistry.BELT_BLOCK_ENTITY, pos, state), BELT);

	public static final BlockEntityType<BeltTunnelBlockEntity> ANDESITE_TUNNEL_BLOCK_ENTITY = ModBlockEntities.register("andesite_tunnel",
		(pos, state) -> new BeltTunnelBlockEntity(BeltRegistry.ANDESITE_TUNNEL_BLOCK_ENTITY, pos, state), ANDESITE_TUNNEL);

	public static final BlockEntityType<BrassTunnelBlockEntity> BRASS_TUNNEL_BLOCK_ENTITY = ModBlockEntities.register("brass_tunnel",
		(pos, state) -> new BrassTunnelBlockEntity(BeltRegistry.BRASS_TUNNEL_BLOCK_ENTITY, pos, state), BRASS_TUNNEL);

	public static final DataComponentType<BlockPos> BELT_FIRST_SHAFT = component("belt_first_shaft",
		DataComponentType.<BlockPos>builder()
			.persistent(BlockPos.CODEC)
			.networkSynchronized(BlockPos.STREAM_CODEC)
			.build());

	public static final BeltConnectorItem BELT_CONNECTOR = ModItems.register("belt_connector", BeltConnectorItem::new,
		new Item.Properties());

	public static final Item ANDESITE_TUNNEL_ITEM = ModItems.register("andesite_tunnel",
		p -> new BeltTunnelItem(ANDESITE_TUNNEL, p), new Item.Properties().useBlockDescriptionPrefix());

	public static final Item BRASS_TUNNEL_ITEM = ModItems.register("brass_tunnel",
		p -> new BeltTunnelItem(BRASS_TUNNEL, p), new Item.Properties().useBlockDescriptionPrefix());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	public static void init() {
		BeltBlock.requestClientStates();
		BeltBlockEntity.registerCapabilities();
		BeltTunnelBlockEntity.registerCapabilities();
		BrassTunnelBlockEntity.registerCapabilities();
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> BeltHelper.clearUprightCache());
	}
}

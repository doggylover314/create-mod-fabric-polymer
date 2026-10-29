package dev.createpolymer.registry.feature;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsInputHandler;
import dev.createpolymer.logistics.filter.ListFilterItem;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Filter items, their data components and the value box interaction handler.
 * <p>
 * The components are registered under Create's ids and are server-only (Polymer strips them from everything sent to
 * players), so unmodded clients never see unknown components.
 */
public final class FilterRegistry {
	private FilterRegistry() {}

	public static final DataComponentType<ItemContainerContents> FILTER_ITEMS = component("filter_items",
		DataComponentType.<ItemContainerContents>builder()
			.persistent(ItemContainerContents.CODEC)
			.networkSynchronized(ItemContainerContents.STREAM_CODEC)
			.build());

	// These 2 are placed on items inside filters and not the filter itself
	public static final DataComponentType<Boolean> FILTER_ITEMS_RESPECT_NBT = component("filter_items_respect_nbt",
		DataComponentType.<Boolean>builder()
			.persistent(Codec.BOOL)
			.networkSynchronized(ByteBufCodecs.BOOL)
			.build());

	public static final DataComponentType<Boolean> FILTER_ITEMS_BLACKLIST = component("filter_items_blacklist",
		DataComponentType.<Boolean>builder()
			.persistent(Codec.BOOL)
			.networkSynchronized(ByteBufCodecs.BOOL)
			.build());

	public static final ListFilterItem FILTER = ModItems.register("filter", ListFilterItem::new, new Item.Properties());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	public static void init() {
		ValueSettingsInputHandler.init();
	}
}

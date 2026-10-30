package dev.createpolymer.registry.feature;

import java.util.List;
import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.equipment.blueprint.BlueprintEntity;
import dev.createpolymer.equipment.blueprint.BlueprintItem;
import dev.createpolymer.equipment.clipboard.ClipboardBlock;
import dev.createpolymer.equipment.clipboard.ClipboardBlockEntity;
import dev.createpolymer.equipment.clipboard.ClipboardBlockItem;
import dev.createpolymer.equipment.clipboard.ClipboardContent;
import dev.createpolymer.equipment.clipboard.ClipboardValueSettingsHandler;
import dev.createpolymer.logistics.filter.AttributeFilterItem;
import dev.createpolymer.logistics.filter.AttributeFilterWhitelistMode;
import dev.createpolymer.logistics.filter.FilterItem;
import dev.createpolymer.logistics.filter.PackageFilterItem;
import dev.createpolymer.logistics.item.filter.attribute.AllItemAttributeTypes;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.redstone.link.controller.LecternControllerBlock;
import dev.createpolymer.redstone.link.controller.LecternControllerBlockEntity;
import dev.createpolymer.redstone.link.controller.LinkedControllerHandler;
import dev.createpolymer.redstone.link.controller.LinkedControllerItem;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.schematics.cannon.MaterialChecklist;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Create's attribute and package filters, clipboard, linked controller, lectern controller and crafting blueprint.
 * The data components are server-only (Polymer strips them from everything sent to players).
 */
public final class ToolsRegistry {
	private ToolsRegistry() {}

	// ---- data components ----

	public static final DataComponentType<AttributeFilterWhitelistMode> ATTRIBUTE_FILTER_WHITELIST_MODE = component(
		"attribute_filter_whitelist_mode", DataComponentType.<AttributeFilterWhitelistMode>builder()
			.persistent(AttributeFilterWhitelistMode.CODEC)
			.networkSynchronized(AttributeFilterWhitelistMode.STREAM_CODEC)
			.build());

	public static final DataComponentType<List<ItemAttributeEntry>> ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES = component(
		"attribute_filter_matched_attributes", DataComponentType.<List<ItemAttributeEntry>>builder()
			.persistent(ItemAttributeEntry.CODEC.listOf())
			.networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ItemAttributeEntry.CODEC.listOf()))
			.build());

	public static final DataComponentType<ClipboardContent> CLIPBOARD_CONTENT = component(
		"clipboard_content", DataComponentType.<ClipboardContent>builder()
			.persistent(ClipboardContent.CODEC)
			.networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ClipboardContent.CODEC))
			.build());

	public static final DataComponentType<ItemContainerContents> LINKED_CONTROLLER_ITEMS = component(
		"linked_controller_items", DataComponentType.<ItemContainerContents>builder()
			.persistent(ItemContainerContents.CODEC)
			.networkSynchronized(ItemContainerContents.STREAM_CODEC)
			.build());

	// ---- blocks ----

	public static final ClipboardBlock CLIPBOARD = ModBlocks.register("clipboard", ClipboardBlock::new,
		ModBlocks.wooden().forceSolidOn());

	// Create: initialProperties(() -> Blocks.LECTERN)
	public static final LecternControllerBlock LECTERN_CONTROLLER = ModBlocks.register("lectern_controller",
		LecternControllerBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN));

	// ---- block entities ----

	public static final BlockEntityType<ClipboardBlockEntity> CLIPBOARD_BLOCK_ENTITY = ModBlockEntities.register("clipboard",
		(pos, state) -> new ClipboardBlockEntity(ToolsRegistry.CLIPBOARD_BLOCK_ENTITY, pos, state), CLIPBOARD);

	public static final BlockEntityType<LecternControllerBlockEntity> LECTERN_CONTROLLER_BLOCK_ENTITY = ModBlockEntities.register(
		"lectern_controller", (pos, state) -> new LecternControllerBlockEntity(ToolsRegistry.LECTERN_CONTROLLER_BLOCK_ENTITY, pos, state),
		LECTERN_CONTROLLER);

	// ---- items ----

	public static final BlueprintItem CRAFTING_BLUEPRINT = ModItems.register("crafting_blueprint", BlueprintItem::new,
		new Item.Properties());

	public static final LinkedControllerItem LINKED_CONTROLLER = ModItems.register("linked_controller",
		LinkedControllerItem::new, new Item.Properties().stacksTo(1));

	public static final ClipboardBlockItem CLIPBOARD_ITEM = ModItems.register("clipboard",
		p -> new ClipboardBlockItem(CLIPBOARD, p), new Item.Properties().useBlockDescriptionPrefix());

	public static final AttributeFilterItem ATTRIBUTE_FILTER = ModItems.register("attribute_filter", FilterItem::attribute,
		new Item.Properties());
	public static final PackageFilterItem PACKAGE_FILTER = ModItems.register("package_filter", FilterItem::address,
		new Item.Properties());

	// ---- entities ----

	public static final EntityType<BlueprintEntity> CRAFTING_BLUEPRINT_ENTITY = entity("crafting_blueprint",
		BlueprintEntity::new, BlueprintEntity::build);

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.EntityFactory<T> factory,
		UnaryOperator<EntityType.Builder<?>> build) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, CreatePolymer.id(name));
		@SuppressWarnings("unchecked")
		EntityType<T> type = ((EntityType.Builder<T>) build.apply(EntityType.Builder.of(factory, MobCategory.MISC))).build(key);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
		PolymerEntityUtils.registerType(type);
		return type;
	}

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	public static void init() {
		AllItemAttributeTypes.init();
		RedstoneClientStates.init();
		ClipboardValueSettingsHandler.init();
		LinkedControllerHandler.init();

		// Create's schematicannon also prints its checklist onto a clipboard
		SchematicsRegistry.registerChecklistPaper(stack -> stack.is(CLIPBOARD_ITEM));
		MaterialChecklist.clipboardWriter = MaterialChecklist::createWrittenClipboard;
	}
}

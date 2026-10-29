package dev.createpolymer.registry.feature;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.contraption.ContraptionType;
import dev.createpolymer.contraptions.minecart.MinecartCouplingItem;
import dev.createpolymer.contraptions.minecart.capability.CapabilityMinecartController;
import dev.createpolymer.contraptions.minecart.capability.MinecartController;
import dev.createpolymer.contraptions.mounted.CartAssemblerBlock;
import dev.createpolymer.contraptions.mounted.CartAssemblerBlock.MinecartAnchorBlock;
import dev.createpolymer.contraptions.mounted.CartAssemblerBlockEntity;
import dev.createpolymer.contraptions.mounted.CartAssemblerBlockItem;
import dev.createpolymer.contraptions.mounted.MinecartContraptionItem;
import dev.createpolymer.contraptions.mounted.MountedContraption;
import dev.createpolymer.redstone.rail.ControllerRailBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Minecart contraptions: the cart assembler and its mounted contraption, minecart contraption items, the controller
 * rail and minecart couplings.
 */
public final class MinecartRegistry {
	private MinecartRegistry() {}

	// Create's CKinetics defaults
	public static int maxCartCouplingLength = 32;
	public static boolean survivalContraptionPickup = true;
	public static boolean minecartContraptionInContainers = false;

	// ---- contraption type (Create's AllContraptionTypes.MOUNTED) ----

	public static final ContraptionType MOUNTED = ContraptionType.register("mounted", MountedContraption::new);

	// ---- data (Create's AllAttachmentTypes / AllDataComponents) ----

	/** The coupling and stall state of a minecart. */
	public static final AttachmentType<MinecartController> MINECART_CONTROLLER = AttachmentRegistry
		.createPersistent(CreatePolymer.id("minecart_controller"), MinecartController.CODEC);

	/** Holds contraption data when a minecart contraption is picked up. */
	public static final DataComponentType<CompoundTag> MINECART_CONTRAPTION_DATA = component("minecart_contraption_data",
		DataComponentType.<CompoundTag>builder()
			.persistent(CompoundTag.CODEC)
			.networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
			.build());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	// ---- blocks ----

	public static final CartAssemblerBlock CART_ASSEMBLER = ModBlocks.register("cart_assembler", CartAssemblerBlock::new,
		ModBlocks.stone().mapColor(MapColor.COLOR_GRAY).pushReaction(PushReaction.IMMOVEABLE));
	public static final ControllerRailBlock CONTROLLER_RAIL = ModBlocks.register("controller_rail",
		ControllerRailBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.POWERED_RAIL));
	public static final MinecartAnchorBlock MINECART_ANCHOR = ModBlocks.register("minecart_anchor",
		MinecartAnchorBlock::new, ModBlocks.stone());

	// ---- block entities ----

	public static final BlockEntityType<CartAssemblerBlockEntity> CART_ASSEMBLER_BLOCK_ENTITY = ModBlockEntities
		.register("cart_assembler", (pos, state) -> new CartAssemblerBlockEntity(
			MinecartRegistry.CART_ASSEMBLER_BLOCK_ENTITY, pos, state), CART_ASSEMBLER);

	// ---- items ----

	public static final Item CART_ASSEMBLER_ITEM = ModItems.register("cart_assembler",
		p -> new CartAssemblerBlockItem(CART_ASSEMBLER, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item CONTROLLER_RAIL_ITEM = ModItems.block(CONTROLLER_RAIL);
	public static final MinecartCouplingItem MINECART_COUPLING = ModItems.register("minecart_coupling",
		MinecartCouplingItem::new, new Item.Properties());

	public static final MinecartContraptionItem MINECART_CONTRAPTION = ModItems.register("minecart_contraption",
		MinecartContraptionItem::rideable,
		new Item.Properties().overrideDescription("item.create.minecart_contraption"));
	public static final MinecartContraptionItem FURNACE_MINECART_CONTRAPTION = ModItems.register(
		"furnace_minecart_contraption", MinecartContraptionItem::furnace,
		new Item.Properties().overrideDescription("item.create.minecart_contraption"));
	public static final MinecartContraptionItem CHEST_MINECART_CONTRAPTION = ModItems.register(
		"chest_minecart_contraption", MinecartContraptionItem::chest,
		new Item.Properties().overrideDescription("item.create.minecart_contraption"));

	public static void init() {
		CapabilityMinecartController.init();
		MinecartCouplingItem.init();
		MinecartContraptionItem.init();
	}
}

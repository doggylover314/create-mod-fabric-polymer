package dev.createpolymer.registry.feature;

import dev.createpolymer.logistics.vault.ItemVaultBlock;
import dev.createpolymer.logistics.vault.ItemVaultBlockEntity;
import dev.createpolymer.logistics.vault.ItemVaultItem;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class VaultRegistry {
	private VaultRegistry() {}

	// Create: SharedProperties.softMetal (a gold block) with a blue terracotta colour, netherite sound and 1200 blast resistance
	public static final ItemVaultBlock ITEM_VAULT = ModBlocks.register("item_vault", ItemVaultBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
			.mapColor(MapColor.TERRACOTTA_BLUE)
			.sound(SoundType.NETHERITE_BLOCK)
			// the model is drawn by display entities: an opaque block would put them in darkness
			.noOcclusion()
			.explosionResistance(1200));

	public static final BlockEntityType<ItemVaultBlockEntity> ITEM_VAULT_BLOCK_ENTITY = ModBlockEntities.register("item_vault",
		(pos, state) -> new ItemVaultBlockEntity(VaultRegistry.ITEM_VAULT_BLOCK_ENTITY, pos, state), ITEM_VAULT);

	public static final Item ITEM_VAULT_ITEM = ModItems.register("item_vault",
		p -> new ItemVaultItem(ITEM_VAULT, p), new Item.Properties().useBlockDescriptionPrefix());

	public static void init() {
		ItemVaultBlockEntity.registerCapabilities();
	}
}

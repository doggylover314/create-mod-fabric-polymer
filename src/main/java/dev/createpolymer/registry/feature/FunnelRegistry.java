package dev.createpolymer.registry.feature;

import dev.createpolymer.logistics.funnel.AndesiteFunnelBlock;
import dev.createpolymer.logistics.funnel.BeltFunnelBlock;
import dev.createpolymer.logistics.funnel.BrassFunnelBlock;
import dev.createpolymer.logistics.funnel.FunnelBlockEntity;
import dev.createpolymer.logistics.funnel.FunnelItem;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Andesite and brass funnels and their belt funnel variants (which are not items of their own). */
public final class FunnelRegistry {
	private FunnelRegistry() {}

	public static final AndesiteFunnelBlock ANDESITE_FUNNEL = ModBlocks.register("andesite_funnel",
		AndesiteFunnelBlock::new, ModBlocks.stone().mapColor(MapColor.STONE));

	public static final BeltFunnelBlock ANDESITE_BELT_FUNNEL = ModBlocks.register("andesite_belt_funnel",
		p -> new BeltFunnelBlock(() -> FunnelRegistry.ANDESITE_FUNNEL, p), ModBlocks.stone().mapColor(MapColor.STONE));

	// Create: SharedProperties.softMetal (a gold block), netherite sound
	public static final BrassFunnelBlock BRASS_FUNNEL = ModBlocks.register("brass_funnel", BrassFunnelBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
			.mapColor(MapColor.TERRACOTTA_YELLOW)
			.sound(SoundType.NETHERITE_BLOCK)
			.noOcclusion());

	public static final BeltFunnelBlock BRASS_BELT_FUNNEL = ModBlocks.register("brass_belt_funnel",
		p -> new BeltFunnelBlock(() -> FunnelRegistry.BRASS_FUNNEL, p),
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
			.mapColor(MapColor.TERRACOTTA_YELLOW)
			.sound(SoundType.NETHERITE_BLOCK)
			.noOcclusion());

	public static final BlockEntityType<FunnelBlockEntity> FUNNEL_BLOCK_ENTITY = ModBlockEntities.register("funnel",
		(pos, state) -> new FunnelBlockEntity(FunnelRegistry.FUNNEL_BLOCK_ENTITY, pos, state),
		ANDESITE_FUNNEL, ANDESITE_BELT_FUNNEL, BRASS_FUNNEL, BRASS_BELT_FUNNEL);

	public static final Item ANDESITE_FUNNEL_ITEM = ModItems.register("andesite_funnel",
		p -> new FunnelItem(ANDESITE_FUNNEL, p), new Item.Properties().useBlockDescriptionPrefix());

	public static final Item BRASS_FUNNEL_ITEM = ModItems.register("brass_funnel",
		p -> new FunnelItem(BRASS_FUNNEL, p), new Item.Properties().useBlockDescriptionPrefix());

	public static void init() {
		FunnelBlockEntity.registerCapabilities();
		FunnelItem.registerEvents();
	}
}

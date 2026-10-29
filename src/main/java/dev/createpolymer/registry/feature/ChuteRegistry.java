package dev.createpolymer.registry.feature;

import dev.createpolymer.logistics.chute.ChuteBlock;
import dev.createpolymer.logistics.chute.ChuteBlockEntity;
import dev.createpolymer.logistics.chute.ChuteItem;
import dev.createpolymer.logistics.chute.SmartChuteBlock;
import dev.createpolymer.logistics.chute.SmartChuteBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ChuteRegistry {
	private ChuteRegistry() {}

	// Create: SharedProperties.softMetal (a gold block), grey, netherite sound, not suffocating
	public static final ChuteBlock CHUTE = ModBlocks.register("chute", ChuteBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
			.mapColor(MapColor.COLOR_GRAY)
			.sound(SoundType.NETHERITE_BLOCK)
			.noOcclusion()
			.isSuffocating((state, level, pos) -> false));

	public static final SmartChuteBlock SMART_CHUTE = ModBlocks.register("smart_chute", SmartChuteBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
			.mapColor(MapColor.COLOR_GRAY)
			.sound(SoundType.NETHERITE_BLOCK)
			.noOcclusion()
			.isSuffocating((state, level, pos) -> false)
			.isRedstoneConductor((state, level, pos) -> false));

	public static final BlockEntityType<ChuteBlockEntity> CHUTE_BLOCK_ENTITY = ModBlockEntities.register("chute",
		(pos, state) -> new ChuteBlockEntity(ChuteRegistry.CHUTE_BLOCK_ENTITY, pos, state), CHUTE);

	public static final BlockEntityType<SmartChuteBlockEntity> SMART_CHUTE_BLOCK_ENTITY = ModBlockEntities.register("smart_chute",
		(pos, state) -> new SmartChuteBlockEntity(ChuteRegistry.SMART_CHUTE_BLOCK_ENTITY, pos, state), SMART_CHUTE);

	public static final Item CHUTE_ITEM = ModItems.register("chute", p -> new ChuteItem(CHUTE, p),
		new Item.Properties().useBlockDescriptionPrefix());
	public static final Item SMART_CHUTE_ITEM = ModItems.block(SMART_CHUTE);

	public static void init() {
		ChuteBlockEntity.registerCapabilities();
		SmartChuteBlockEntity.registerCapabilities();
	}
}

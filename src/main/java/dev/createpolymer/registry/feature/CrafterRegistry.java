package dev.createpolymer.registry.feature;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.blockEntity.behaviour.edgeInteraction.EdgeInteractionHandler;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.crafter.MechanicalCrafterBlock;
import dev.createpolymer.kinetics.crafter.MechanicalCrafterBlockEntity;
import dev.createpolymer.kinetics.crafter.MechanicalCraftingRecipe;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Mechanical crafter, the crafter slot cover and the create:mechanical_crafting recipe type. */
public final class CrafterRegistry {
	private CrafterRegistry() {}

	// Create: SharedProperties.softMetal (a gold block), noOcclusion, terracotta yellow
	public static final MechanicalCrafterBlock MECHANICAL_CRAFTER = ModBlocks.register("mechanical_crafter",
		MechanicalCrafterBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).noOcclusion().mapColor(MapColor.TERRACOTTA_YELLOW));

	public static final BlockEntityType<MechanicalCrafterBlockEntity> MECHANICAL_CRAFTER_BE = ModBlockEntities.register(
		"mechanical_crafter",
		(pos, state) -> new MechanicalCrafterBlockEntity(CrafterRegistry.MECHANICAL_CRAFTER_BE, pos, state),
		MECHANICAL_CRAFTER);

	public static final Item MECHANICAL_CRAFTER_ITEM = ModItems.block(MECHANICAL_CRAFTER);
	public static final Item CRAFTER_SLOT_COVER = ModItems.simple("crafter_slot_cover");

	public static final RecipeType<MechanicalCraftingRecipe> MECHANICAL_CRAFTING = type("mechanical_crafting");
	public static final RecipeSerializer<MechanicalCraftingRecipe> MECHANICAL_CRAFTING_SERIALIZER = serializer(
		"mechanical_crafting", MechanicalCraftingRecipe.Serializer.CODEC);

	// ModRecipeTypes' registration, for the same server-only recipe type
	private static <T extends Recipe<?>> RecipeType<T> type(String name) {
		RecipeType<T> type = new RecipeType<>() {
			@Override
			public String toString() {
				return CreatePolymer.MOD_ID + ":" + name;
			}
		};
		Registry.register(BuiltInRegistries.RECIPE_TYPE, CreatePolymer.id(name), type);
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_TYPE, type);
		return type;
	}

	private static <T extends Recipe<?>> RecipeSerializer<T> serializer(String name, MapCodec<T> codec) {
		RecipeSerializer<T> serializer = new RecipeSerializer<>(codec, ByteBufCodecs.fromCodecWithRegistries(codec.codec()));
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, CreatePolymer.id(name), serializer);
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_SERIALIZER, serializer);
		return serializer;
	}

	public static void init() {
		// Create: CStress.setImpact(2.0)
		BlockStressValues.setImpact(MECHANICAL_CRAFTER, 2);
		MechanicalCrafterBlockEntity.registerCapabilities();
		// wrench on a crafter's edge connects it to its neighbour (EdgeInteractionBehaviour)
		EdgeInteractionHandler.register();
	}
}

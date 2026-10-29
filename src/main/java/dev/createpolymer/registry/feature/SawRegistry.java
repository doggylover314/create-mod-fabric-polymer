package dev.createpolymer.registry.feature;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.saw.SawBlock;
import dev.createpolymer.kinetics.saw.SawBlockEntity;
import dev.createpolymer.processing.recipe.CuttingRecipe;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

/** The mechanical saw and the create:cutting recipe type. */
public final class SawRegistry {
	private SawRegistry() {}

	/** Create's config: CRecipes.bulkCutting and CRecipes.allowStonecuttingOnSaw */
	public static boolean bulkCutting = false;
	public static boolean allowStonecuttingOnSaw = true;

	public static final RecipeType<CuttingRecipe> CUTTING = type("cutting");
	public static final RecipeSerializer<CuttingRecipe> CUTTING_SERIALIZER =
		serializer("cutting", ProcessingRecipe.codec(CuttingRecipe::new));

	public static final SawBlock MECHANICAL_SAW = ModBlocks.register("mechanical_saw", SawBlock::new,
		ModBlocks.stone().mapColor(MapColor.PODZOL));

	public static final BlockEntityType<SawBlockEntity> SAW = ModBlockEntities.register("saw",
		(pos, state) -> new SawBlockEntity(SawRegistry.SAW, pos, state), MECHANICAL_SAW);

	public static final Item MECHANICAL_SAW_ITEM = ModItems.block(MECHANICAL_SAW);

	public static final ResourceKey<DamageType> SAW_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, CreatePolymer.id("mechanical_saw"));

	/** Create's CreateDamageSources.saw */
	public static DamageSource sawDamage(LevelReader level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SAW_DAMAGE));
	}

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
		// Create's BlockStressDefaults: CStress.setImpact(4.0)
		BlockStressValues.setImpact(MECHANICAL_SAW, 4);
		SawBlockEntity.registerCapabilities();
	}
}

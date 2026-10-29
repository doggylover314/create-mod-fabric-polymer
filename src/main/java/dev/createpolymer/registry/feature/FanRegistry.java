package dev.createpolymer.registry.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.fan.EncasedFanBlock;
import dev.createpolymer.kinetics.fan.EncasedFanBlockEntity;
import dev.createpolymer.kinetics.fan.NozzleBlock;
import dev.createpolymer.kinetics.fan.NozzleBlockEntity;
import dev.createpolymer.kinetics.fan.processing.AllFanProcessingTypes;
import dev.createpolymer.kinetics.fan.processing.FanProcessing;
import dev.createpolymer.processing.recipe.HauntingRecipe;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.processing.recipe.SplashingRecipe;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

public final class FanRegistry {
	private FanRegistry() {}

	public static final EncasedFanBlock ENCASED_FAN = ModBlocks.register("encased_fan", EncasedFanBlock::new,
		ModBlocks.stone().mapColor(MapColor.PODZOL));
	public static final NozzleBlock NOZZLE = ModBlocks.register("nozzle", NozzleBlock::new,
		ModBlocks.stone().mapColor(MapColor.COLOR_LIGHT_GRAY));

	public static final BlockEntityType<EncasedFanBlockEntity> ENCASED_FAN_BLOCK_ENTITY = ModBlockEntities.register("encased_fan",
		(pos, state) -> new EncasedFanBlockEntity(FanRegistry.ENCASED_FAN_BLOCK_ENTITY, pos, state), ENCASED_FAN);
	public static final BlockEntityType<NozzleBlockEntity> NOZZLE_BLOCK_ENTITY = ModBlockEntities.register("nozzle",
		(pos, state) -> new NozzleBlockEntity(FanRegistry.NOZZLE_BLOCK_ENTITY, pos, state), NOZZLE);

	public static final Item ENCASED_FAN_ITEM = ModItems.block(ENCASED_FAN);
	public static final Item NOZZLE_ITEM = ModItems.block(NOZZLE);
	public static final Item PROPELLER = MaterialsRegistry.PROPELLER;

	public static final RecipeType<SplashingRecipe> SPLASHING = recipeType("splashing");
	public static final RecipeType<HauntingRecipe> HAUNTING = recipeType("haunting");
	public static final RecipeSerializer<SplashingRecipe> SPLASHING_SERIALIZER = recipeSerializer("splashing", ProcessingRecipe.codec(SplashingRecipe::new));
	public static final RecipeSerializer<HauntingRecipe> HAUNTING_SERIALIZER = recipeSerializer("haunting", ProcessingRecipe.codec(HauntingRecipe::new));

	/** Create's CreateData/Processing entry in the persistent data of an item entity in a fan's air flow. */
	public static final AttachmentType<FanProcessing.Data> PROCESSING_DATA = AttachmentRegistry.createPersistent(
		CreatePolymer.id("fan_processing"), FanProcessing.Data.CODEC);
	/** Create's CreateHaunting entry in the persistent data of a horse being haunted. */
	public static final AttachmentType<Integer> HAUNTING_PROGRESS = AttachmentRegistry.createPersistent(
		CreatePolymer.id("haunting_progress"), Codec.INT);

	private static <T extends Recipe<?>> RecipeType<T> recipeType(String name) {
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

	private static <T extends Recipe<?>> RecipeSerializer<T> recipeSerializer(String name, MapCodec<T> codec) {
		RecipeSerializer<T> serializer = new RecipeSerializer<>(codec, ByteBufCodecs.fromCodecWithRegistries(codec.codec()));
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, CreatePolymer.id(name), serializer);
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_SERIALIZER, serializer);
		return serializer;
	}

	public static void init() {
		// Create: CStress.setImpact(2.0)
		BlockStressValues.setImpact(ENCASED_FAN, 2.0);
		AllFanProcessingTypes.init();
	}
}

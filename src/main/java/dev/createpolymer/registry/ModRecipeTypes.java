package dev.createpolymer.registry;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.processing.recipe.BasinRecipe;
import dev.createpolymer.processing.recipe.MillingRecipe;
import dev.createpolymer.processing.recipe.PressingRecipe;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

public final class ModRecipeTypes {
	private ModRecipeTypes() {}

	public static final RecipeType<MillingRecipe> MILLING = type("milling");
	public static final RecipeType<PressingRecipe> PRESSING = type("pressing");
	public static final RecipeType<BasinRecipe> MIXING = type("mixing");
	public static final RecipeType<BasinRecipe> COMPACTING = type("compacting");

	public static final RecipeSerializer<MillingRecipe> MILLING_SERIALIZER = serializer("milling", ProcessingRecipe.codec(MillingRecipe::new));
	public static final RecipeSerializer<PressingRecipe> PRESSING_SERIALIZER = serializer("pressing", ProcessingRecipe.codec(PressingRecipe::new));
	public static final RecipeSerializer<BasinRecipe> MIXING_SERIALIZER = serializer("mixing", ProcessingRecipe.codec(BasinRecipe::mixing));
	public static final RecipeSerializer<BasinRecipe> COMPACTING_SERIALIZER = serializer("compacting", ProcessingRecipe.codec(BasinRecipe::compacting));

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
	}
}

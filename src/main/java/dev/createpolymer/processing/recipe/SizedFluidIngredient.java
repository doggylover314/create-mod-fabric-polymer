package dev.createpolymer.processing.recipe;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.foundation.fluid.FluidStack;
import net.minecraft.util.ExtraCodecs;

/** A {@link FluidIngredient} and the amount of it that a recipe uses up, in millibuckets. */
public record SizedFluidIngredient(FluidIngredient ingredient, int amount) {

	public static final Codec<SizedFluidIngredient> CODEC = RecordCodecBuilder.create(i -> i.group(
		FluidIngredient.MAP_CODEC.forGetter(SizedFluidIngredient::ingredient),
		ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(SizedFluidIngredient::amount)
	).apply(i, SizedFluidIngredient::new));

	public static SizedFluidIngredient of(FluidStack stack) {
		return new SizedFluidIngredient(FluidIngredient.of(stack), stack.getAmount());
	}

	public boolean test(FluidStack stack) {
		return ingredient.test(stack) && stack.getAmount() >= amount;
	}

	/** Every fluid that matches, each with the required amount. */
	public List<FluidStack> getFluids() {
		return ingredient.getFluids().stream()
			.map(stack -> stack.copyWithAmount(amount))
			.toList();
	}
}

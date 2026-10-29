package dev.createpolymer.processing.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.foundation.fluid.FluidStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

/**
 * Stands in for NeoForge's FluidIngredient (single fluid, fluid tag, and fluid with data components).
 * JSON: {@code {"fluid": "minecraft:lava"}} or {@code {"tag": "c:milk"}}, with an optional
 * {@code "components"} patch that the matching stack must contain.
 */
public final class FluidIngredient {

	public static final MapCodec<FluidIngredient> MAP_CODEC = RecordCodecBuilder.<FluidIngredient>mapCodec(i -> i.group(
		BuiltInRegistries.FLUID.byNameCodec().optionalFieldOf("fluid").forGetter(f -> Optional.ofNullable(f.fluid)),
		TagKey.codec(Registries.FLUID).optionalFieldOf("tag").forGetter(f -> Optional.ofNullable(f.tag)),
		DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(f -> f.components)
	).apply(i, (fluid, tag, components) -> new FluidIngredient(fluid.orElse(null), tag.orElse(null), components)))
		.validate(f -> f.fluid != null == (f.tag == null) ? DataResult.success(f)
			: DataResult.error(() -> "A fluid ingredient needs exactly one of \"fluid\" and \"tag\""));

	public static final Codec<FluidIngredient> CODEC = MAP_CODEC.codec();

	@Nullable
	private final Fluid fluid;
	@Nullable
	private final TagKey<Fluid> tag;
	private final DataComponentPatch components;

	private FluidIngredient(@Nullable Fluid fluid, @Nullable TagKey<Fluid> tag, DataComponentPatch components) {
		this.fluid = fluid;
		this.tag = tag;
		this.components = components;
	}

	public static FluidIngredient of(Fluid fluid) {
		return new FluidIngredient(fluid, null, DataComponentPatch.EMPTY);
	}

	public static FluidIngredient of(Fluid fluid, DataComponentPatch components) {
		return new FluidIngredient(fluid, null, components);
	}

	public static FluidIngredient of(TagKey<Fluid> tag) {
		return new FluidIngredient(null, tag, DataComponentPatch.EMPTY);
	}

	/** Matches the fluid and every component of {@code stack}. */
	public static FluidIngredient of(FluidStack stack) {
		return new FluidIngredient(stack.getFluid(), null, stack.getComponentsPatch());
	}

	public boolean test(FluidStack stack) {
		if (stack.isEmpty())
			return false;
		if (fluid != null ? !stack.getFluid().isSame(fluid) : !stack.is(tag))
			return false;
		DataComponentPatch.SplitResult required = components.split();
		for (TypedDataComponent<?> component : required.added())
			if (!Objects.equals(component.value(), stack.get(component.type())))
				return false;
		for (DataComponentType<?> removed : required.removed())
			if (stack.get(removed) != null)
				return false;
		return true;
	}

	/** Every fluid this can match, with an amount of 1; a tag lists its current members. */
	public List<FluidStack> getFluids() {
		List<FluidStack> fluids = new ArrayList<>();
		if (fluid != null) {
			fluids.add(new FluidStack(fluid, 1, components));
		} else {
			for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(tag))
				if (holder.value().defaultFluidState().isSource())
					fluids.add(new FluidStack(holder, 1, components));
		}
		return fluids;
	}
}

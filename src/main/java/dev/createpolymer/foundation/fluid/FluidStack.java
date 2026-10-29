package dev.createpolymer.foundation.fluid;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * An amount of fluid in millibuckets (1000 per bucket) with data components, like NeoForge's FluidStack that
 * Create is written against. Converted to Fabric's droplets (81 per millibucket) only at the transfer API boundary.
 */
public final class FluidStack implements DataComponentHolder {
	public static final FluidStack EMPTY = new FluidStack(null);

	public static final Codec<Holder<Fluid>> FLUID_NON_EMPTY_CODEC = BuiltInRegistries.FLUID.holderByNameCodec()
		.validate(h -> h.value() == Fluids.EMPTY ? com.mojang.serialization.DataResult.error(() -> "Fluid must not be minecraft:empty")
			: com.mojang.serialization.DataResult.success(h));

	public static final Codec<FluidStack> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(i -> i.group(
		FLUID_NON_EMPTY_CODEC.fieldOf("id").forGetter(FluidStack::getFluidHolder),
		ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidStack::getAmount),
		DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(FluidStack::getComponentsPatch)
	).apply(i, FluidStack::new)));

	public static final Codec<FluidStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
		.xmap(o -> o.orElse(EMPTY), s -> s.isEmpty() ? Optional.empty() : Optional.of(s));

	public static final MapCodec<FluidStack> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		FLUID_NON_EMPTY_CODEC.fieldOf("id").forGetter(FluidStack::getFluidHolder),
		ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidStack::getAmount),
		DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(FluidStack::getComponentsPatch)
	).apply(i, FluidStack::new));

	private final @Nullable Fluid fluid;
	private int amount;
	private final PatchedDataComponentMap components;

	private FluidStack(@Nullable Void empty) {
		this.fluid = null;
		this.amount = 0;
		this.components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
	}

	public FluidStack(Fluid fluid, int amount) {
		this(fluid, amount, DataComponentPatch.EMPTY);
	}

	public FluidStack(Holder<Fluid> fluid, int amount) {
		this(fluid.value(), amount, DataComponentPatch.EMPTY);
	}

	public FluidStack(Holder<Fluid> fluid, int amount, DataComponentPatch patch) {
		this(fluid.value(), amount, patch);
	}

	public FluidStack(Fluid fluid, int amount, DataComponentPatch patch) {
		this.fluid = fluid;
		this.amount = amount;
		this.components = PatchedDataComponentMap.fromPatch(DataComponentMap.EMPTY, patch);
	}

	public static FluidStack fromVariant(FluidVariant variant, int amount) {
		if (variant.isBlank() || amount <= 0)
			return EMPTY;
		return new FluidStack(variant.getFluid(), amount, variant.getComponentsPatch());
	}

	public FluidVariant toVariant() {
		return isEmpty() ? FluidVariant.blank() : FluidVariant.of(getFluid(), getComponentsPatch());
	}

	public boolean isEmpty() {
		return this == EMPTY || fluid == null || fluid == Fluids.EMPTY || amount <= 0;
	}

	public Fluid getFluid() {
		return isEmpty() ? Fluids.EMPTY : fluid;
	}

	public Holder<Fluid> getFluidHolder() {
		return getFluid().builtInRegistryHolder();
	}

	public boolean is(Fluid other) {
		return getFluid() == other;
	}

	public boolean is(TagKey<Fluid> tag) {
		return getFluidHolder().is(tag);
	}

	public boolean is(Holder<Fluid> holder) {
		return getFluid() == holder.value();
	}

	public int getAmount() {
		return isEmpty() ? 0 : amount;
	}

	public void setAmount(int amount) {
		if (this == EMPTY)
			throw new IllegalStateException("Can't modify the empty stack.");
		this.amount = amount;
	}

	public void grow(int addedAmount) {
		setAmount(amount + addedAmount);
	}

	public void shrink(int removedAmount) {
		setAmount(amount - removedAmount);
	}

	public void limitSize(int maxSize) {
		if (!isEmpty() && amount > maxSize)
			setAmount(maxSize);
	}

	@Override
	public DataComponentMap getComponents() {
		return components;
	}

	public DataComponentPatch getComponentsPatch() {
		return isEmpty() ? DataComponentPatch.EMPTY : components.asPatch();
	}

	public <T> @Nullable T set(DataComponentType<T> type, @Nullable T value) {
		if (this == EMPTY)
			throw new IllegalStateException("Can't modify the empty stack.");
		return components.set(type, value);
	}

	public <T> @Nullable T remove(DataComponentType<? extends T> type) {
		if (this == EMPTY)
			throw new IllegalStateException("Can't modify the empty stack.");
		return components.remove(type);
	}

	public void applyComponents(DataComponentPatch patch) {
		if (this == EMPTY)
			throw new IllegalStateException("Can't modify the empty stack.");
		components.applyPatch(patch);
	}

	public FluidStack copy() {
		if (isEmpty())
			return EMPTY;
		return new FluidStack(fluid, amount, components.asPatch());
	}

	public FluidStack copyWithAmount(int amount) {
		if (isEmpty() || amount <= 0)
			return EMPTY;
		FluidStack copy = copy();
		copy.setAmount(amount);
		return copy;
	}

	public FluidStack split(int amount) {
		int moved = Math.min(amount, getAmount());
		FluidStack result = copyWithAmount(moved);
		if (!isEmpty())
			shrink(moved);
		return result;
	}

	public Component getHoverName() {
		return FluidVariantAttributes.getName(toVariant());
	}

	public static boolean isSameFluid(FluidStack first, FluidStack second) {
		return first.getFluid() == second.getFluid();
	}

	public static boolean isSameFluidSameComponents(FluidStack first, FluidStack second) {
		if (!isSameFluid(first, second))
			return false;
		if (first.isEmpty() && second.isEmpty())
			return true;
		return first.components.equals(second.components);
	}

	public static boolean matches(FluidStack first, FluidStack second) {
		return first.getAmount() == second.getAmount() && isSameFluidSameComponents(first, second);
	}

	@Override
	public String toString() {
		return getAmount() + " " + BuiltInRegistries.FLUID.getKey(getFluid());
	}
}

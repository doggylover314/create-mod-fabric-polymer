package dev.createpolymer.logistics.item.filter.attribute.attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.foundation.fluid.FluidItemHandlers;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandlerItem;
import dev.createpolymer.logistics.item.filter.attribute.AllItemAttributeTypes;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttributeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

public record FluidContentsAttribute(@Nullable Fluid fluid) implements ItemAttribute {
	public static final MapCodec<FluidContentsAttribute> CODEC = BuiltInRegistries.FLUID.byNameCodec()
			.xmap(FluidContentsAttribute::new, FluidContentsAttribute::fluid)
			.fieldOf("value");

	private static List<Fluid> extractFluids(ItemStack stack) {
		List<Fluid> fluids = new ArrayList<>();

		Optional<IFluidHandlerItem> capability = FluidItemHandlers.get(stack.copyWithCount(1));

		if (capability.isPresent()) {
			for (int i = 0; i < capability.get().getTanks(); i++) {
				fluids.add(capability.get().getFluidInTank(i).getFluid());
			}
		}

		return fluids;
	}

	@Override
	public boolean appliesTo(ItemStack itemStack, Level level) {
		return extractFluids(itemStack).contains(fluid);
	}

	@Override
	public String getTranslationKey() {
		return "has_fluid";
	}

	@Override
	public Object[] getTranslationParameters() {
		Object parameter = "";
		if (fluid != null)
			parameter = new FluidStack(fluid, 1000).getHoverName();
		return new Object[]{parameter};
	}

	@Override
	public ItemAttributeType getType() {
		return AllItemAttributeTypes.HAS_FLUID;
	}

	public static class Type implements ItemAttributeType {
		@Override
		public @NotNull ItemAttribute createAttribute() {
			return new FluidContentsAttribute(null);
		}

		@Override
		public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
			List<ItemAttribute> list = new ArrayList<>();

			for (Fluid fluid : extractFluids(stack)) {
				list.add(new FluidContentsAttribute(fluid));
			}

			return list;
		}

		@Override
		public MapCodec<? extends ItemAttribute> codec() {
			return CODEC;
		}
	}
}

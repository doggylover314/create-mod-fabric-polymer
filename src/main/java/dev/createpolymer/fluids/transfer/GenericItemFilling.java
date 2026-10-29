package dev.createpolymer.fluids.transfer;

import java.util.Optional;

import dev.createpolymer.fluids.potion.PotionFluidHandler;
import dev.createpolymer.foundation.fluid.FluidHelper;
import dev.createpolymer.foundation.fluid.FluidItemHandlers;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.foundation.fluid.IFluidHandlerItem;
import dev.createpolymer.registry.feature.FluidsRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class GenericItemFilling {

	public static boolean canItemBeFilled(Level world, ItemStack stack) {
		if (stack.getItem() == Items.GLASS_BOTTLE)
			return true;
		if (stack.getItem() == Items.MILK_BUCKET)
			return false;

		Optional<IFluidHandlerItem> capability = FluidItemHandlers.get(stack.copyWithCount(1));
		if (capability.isEmpty())
			return false;
		for (int i = 0; i < capability.get().getTanks(); i++) {
			if (capability.get().getFluidInTank(i)
				.getAmount() < capability.get().getTankCapacity(i))
				return true;
		}
		return false;
	}

	public static int getRequiredAmountForItem(Level world, ItemStack stack, FluidStack availableFluid) {
		if (stack.getItem() == Items.GLASS_BOTTLE && canFillGlassBottleInternally(availableFluid))
			return PotionFluidHandler.getRequiredAmountForFilledBottle(stack, availableFluid);
		if (stack.getItem() == Items.BUCKET && canFillBucketInternally(availableFluid))
			return 1000;

		Optional<IFluidHandlerItem> capability = FluidItemHandlers.get(stack.copyWithCount(1));
		if (capability.isEmpty())
			return -1;

		int filled = capability.get().fill(availableFluid, FluidAction.SIMULATE);
		return filled == 0 ? -1 : filled;
	}

	private static boolean canFillGlassBottleInternally(FluidStack availableFluid) {
		Fluid fluid = availableFluid.getFluid();
		if (fluid.isSame(Fluids.WATER))
			return true;
		if (fluid.isSame(FluidsRegistry.POTION))
			return true;
		if (fluid.isSame(FluidsRegistry.TEA) && FluidsRegistry.buildersTea() != null)
			return true;
		return false;
	}

	private static boolean canFillBucketInternally(FluidStack availableFluid) {
		return false;
	}

	public static ItemStack fillItem(Level world, int requiredAmount, ItemStack stack, FluidStack availableFluid) {
		FluidStack toFill = availableFluid.copy();
		toFill.setAmount(requiredAmount);
		availableFluid.shrink(requiredAmount);

		if (stack.getItem() == Items.GLASS_BOTTLE && canFillGlassBottleInternally(toFill)) {
			ItemStack fillBottle;
			Fluid fluid = toFill.getFluid();
			if (FluidHelper.isWater(fluid))
				fillBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
			else if (fluid.isSame(FluidsRegistry.TEA))
				fillBottle = new ItemStack(FluidsRegistry.buildersTea());
			else
				fillBottle = PotionFluidHandler.fillBottle(stack, toFill);
			stack.shrink(1);
			return fillBottle;
		}

		ItemStack split = stack.copy();
		split.setCount(1);
		Optional<IFluidHandlerItem> capability = FluidItemHandlers.get(split);
		if (capability.isEmpty())
			return ItemStack.EMPTY;
		capability.get().fill(toFill, FluidAction.EXECUTE);
		ItemStack container = capability.get().getContainer()
			.copy();
		stack.shrink(1);
		return container;
	}

}

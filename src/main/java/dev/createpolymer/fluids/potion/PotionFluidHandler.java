package dev.createpolymer.fluids.potion;

import dev.createpolymer.fluids.potion.PotionFluid.BottleType;
import dev.createpolymer.foundation.fluid.FluidHelper;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.processing.recipe.FluidIngredient;
import dev.createpolymer.processing.recipe.SizedFluidIngredient;
import dev.createpolymer.registry.feature.FluidsRegistry;
import dev.createpolymer.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;

public class PotionFluidHandler {

	public static boolean isPotionItem(ItemStack stack) {
		if (!(stack.getItem() instanceof PotionItem) || stack.is(FluidsRegistry.NOT_POTION))
			return false;
		ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
		return remainder == null || !(remainder.item().value() instanceof BucketItem);
	}

	public static Pair<FluidStack, ItemStack> emptyPotion(ItemStack stack, boolean simulate) {
		FluidStack fluid = getFluidFromPotionItem(stack);
		if (!simulate)
			stack.shrink(1);
		return Pair.of(fluid, new ItemStack(Items.GLASS_BOTTLE));
	}

	public static SizedFluidIngredient potionIngredient(Holder<Potion> potion, int amount) {
		FluidStack stack = FluidHelper.copyStackWithAmount(PotionFluidHandler
			.getFluidFromPotionItem(PotionContents.createItemStack(Items.POTION, potion)), amount);
		return new SizedFluidIngredient(FluidIngredient.of(stack), amount);
	}

	public static FluidStack getFluidFromPotionItem(ItemStack stack) {
		PotionContents potion = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		BottleType bottleTypeFromItem = bottleTypeFromItem(stack.getItem());
		if (potion.is(Potions.WATER) && potion.customEffects().isEmpty() && bottleTypeFromItem == BottleType.REGULAR)
			return new FluidStack(Fluids.WATER, 250);
		FluidStack fluid = getFluidFromPotion(potion, bottleTypeFromItem, 250);
		fluid.set(FluidsRegistry.POTION_FLUID_BOTTLE_TYPE, bottleTypeFromItem);
		return fluid;
	}

	public static FluidStack getFluidFromPotion(PotionContents potionContents, BottleType bottleType, int amount) {
		if (potionContents.is(Potions.WATER) && bottleType == BottleType.REGULAR)
			return new FluidStack(Fluids.WATER, amount);
		return PotionFluid.of(amount, potionContents, bottleType);
	}

	public static BottleType bottleTypeFromItem(Item item) {
		if (item == Items.LINGERING_POTION)
			return BottleType.LINGERING;
		if (item == Items.SPLASH_POTION)
			return BottleType.SPLASH;
		return BottleType.REGULAR;
	}

	public static ItemLike itemFromBottleType(BottleType type) {
		return switch (type) {
			case LINGERING -> Items.LINGERING_POTION;
			case SPLASH -> Items.SPLASH_POTION;
			default -> Items.POTION;
		};
	}

	public static int getRequiredAmountForFilledBottle(ItemStack stack, FluidStack availableFluid) {
		return 250;
	}

	public static ItemStack fillBottle(ItemStack stack, FluidStack availableFluid) {
		ItemStack potionStack = new ItemStack(itemFromBottleType(
			availableFluid.getOrDefault(FluidsRegistry.POTION_FLUID_BOTTLE_TYPE, BottleType.REGULAR)));
		potionStack.set(DataComponents.POTION_CONTENTS, availableFluid.get(DataComponents.POTION_CONTENTS));
		return potionStack;
	}
}

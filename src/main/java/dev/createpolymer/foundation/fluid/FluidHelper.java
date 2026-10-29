package dev.createpolymer.foundation.fluid;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.fluids.tank.CreativeFluidTankBlockEntity;
import dev.createpolymer.fluids.transfer.GenericItemEmptying;
import dev.createpolymer.fluids.transfer.GenericItemFilling;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.util.Pair;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class FluidHelper {

	public static enum FluidExchange {
		ITEM_TO_TANK, TANK_TO_ITEM;
	}

	public static boolean isWater(Fluid fluid) {
		return convertToStill(fluid) == Fluids.WATER;
	}

	public static boolean isLava(Fluid fluid) {
		return convertToStill(fluid) == Fluids.LAVA;
	}

	public static boolean isSame(FluidStack fluidStack, FluidStack fluidStack2) {
		return fluidStack.getFluid() == fluidStack2.getFluid();
	}

	public static boolean isSame(FluidStack fluidStack, Fluid fluid) {
		return fluidStack.getFluid() == fluid;
	}

	@SuppressWarnings("deprecation")
	public static boolean isTag(Fluid fluid, TagKey<Fluid> tag) {
		return fluid.is(tag);
	}

	public static boolean isTag(FluidState fluid, TagKey<Fluid> tag) {
		return fluid.is(tag);
	}

	public static boolean isTag(FluidStack fluid, TagKey<Fluid> tag) {
		return isTag(fluid.getFluid(), tag);
	}

	/** True for fluids that float upwards in tanks (NeoForge's FluidType#isLighterThanAir). */
	public static boolean isLighterThanAir(FluidStack fluid) {
		return FluidVariantAttributes.isLighterThanAir(fluid.toVariant());
	}

	/** Light level of the fluid (NeoForge's FluidType#getLightLevel). */
	public static int getLightLevel(FluidStack fluid) {
		return fluid.isEmpty() ? 0 : FluidVariantAttributes.getLuminance(fluid.toVariant());
	}

	public static SoundEvent getFillSound(FluidStack fluid) {
		FluidVariant variant = fluid.toVariant();
		Optional<SoundEvent> sound = FluidVariantAttributes.getHandlerOrDefault(fluid.getFluid())
			.getFillSound(variant);
		return sound.orElseGet(() -> FluidHelper.isTag(fluid, FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA
			: SoundEvents.BUCKET_FILL);
	}

	public static SoundEvent getEmptySound(FluidStack fluid) {
		FluidVariant variant = fluid.toVariant();
		Optional<SoundEvent> sound = FluidVariantAttributes.getHandlerOrDefault(fluid.getFluid())
			.getEmptySound(variant);
		return sound.orElseGet(() -> FluidHelper.isTag(fluid, FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA
			: SoundEvents.BUCKET_EMPTY);
	}

	public static boolean hasBlockState(Fluid fluid) {
		BlockState blockState = fluid.defaultFluidState()
			.createLegacyBlock();
		return blockState != null && blockState != Blocks.AIR.defaultBlockState();
	}

	public static FluidStack copyStackWithAmount(FluidStack fs, int amount) {
		if (amount <= 0)
			return FluidStack.EMPTY;
		if (fs.isEmpty())
			return FluidStack.EMPTY;
		FluidStack copy = fs.copy();
		copy.setAmount(amount);
		return copy;
	}

	public static Fluid convertToFlowing(Fluid fluid) {
		if (fluid instanceof FlowingFluid flowing)
			return flowing.getFlowing();
		return fluid;
	}

	public static Fluid convertToStill(Fluid fluid) {
		if (fluid instanceof FlowingFluid flowing)
			return flowing.getSource();
		return fluid;
	}

	public static boolean tryEmptyItemIntoBE(Level worldIn, Player player, InteractionHand handIn, ItemStack heldItem,
		SmartBlockEntity be) {
		if (!GenericItemEmptying.canItemBeEmptied(worldIn, heldItem))
			return false;

		Pair<FluidStack, ItemStack> emptyingResult = GenericItemEmptying.emptyItem(worldIn, heldItem, true);
		IFluidHandler capability = FluidHandlerLookup.get(worldIn, be.getBlockPos(), null);
		FluidStack fluidStack = emptyingResult.getFirst();

		if (capability == null || fluidStack.getAmount() != capability.fill(fluidStack, FluidAction.SIMULATE))
			return false;
		if (worldIn.isClientSide())
			return true;

		ItemStack copyOfHeld = heldItem.copy();
		emptyingResult = GenericItemEmptying.emptyItem(worldIn, copyOfHeld, false);
		capability.fill(fluidStack, FluidAction.EXECUTE);

		if (!player.isCreative() && !(be instanceof CreativeFluidTankBlockEntity)) {
			if (copyOfHeld.isEmpty())
				player.setItemInHand(handIn, emptyingResult.getSecond());
			else {
				player.setItemInHand(handIn, copyOfHeld);
				player.getInventory()
					.placeItemBackInInventory(emptyingResult.getSecond(), Prediction.SERVER_ONLY);
			}
		}
		return true;
	}

	public static boolean tryFillItemFromBE(Level world, Player player, InteractionHand handIn, ItemStack heldItem,
		SmartBlockEntity be) {
		if (!GenericItemFilling.canItemBeFilled(world, heldItem))
			return false;

		IFluidHandler capability = FluidHandlerLookup.get(world, be.getBlockPos(), null);

		if (capability == null)
			return false;

		for (int i = 0; i < capability.getTanks(); i++) {
			FluidStack fluid = capability.getFluidInTank(i);
			if (fluid.isEmpty())
				continue;
			int requiredAmountForItem = GenericItemFilling.getRequiredAmountForItem(world, heldItem.copyWithCount(1), fluid.copy());
			if (requiredAmountForItem == -1)
				continue;
			if (requiredAmountForItem > fluid.getAmount())
				continue;

			if (world.isClientSide())
				return true;

			if (player.isCreative() || be instanceof CreativeFluidTankBlockEntity)
				heldItem = heldItem.copy();
			ItemStack out = GenericItemFilling.fillItem(world, requiredAmountForItem, heldItem, fluid.copy());

			FluidStack copy = fluid.copy();
			copy.setAmount(requiredAmountForItem);
			capability.drain(copy, FluidAction.EXECUTE);

			if (!player.isCreative())
				player.getInventory()
					.placeItemBackInInventory(out, Prediction.SERVER_ONLY);
			be.notifyUpdate();
			return true;
		}

		return false;
	}

	@Nullable
	public static FluidExchange exchange(IFluidHandler fluidTank, IFluidHandlerItem fluidItem, FluidExchange preferred,
		int maxAmount) {
		return exchange(fluidTank, fluidItem, preferred, true, maxAmount);
	}

	@Nullable
	public static FluidExchange exchangeAll(IFluidHandler fluidTank, IFluidHandlerItem fluidItem,
		FluidExchange preferred) {
		return exchange(fluidTank, fluidItem, preferred, false, Integer.MAX_VALUE);
	}

	@Nullable
	private static FluidExchange exchange(IFluidHandler fluidTank, IFluidHandlerItem fluidItem, FluidExchange preferred,
		boolean singleOp, int maxTransferAmountPerTank) {

		// Locks in the transfer direction of this operation
		FluidExchange lockedExchange = null;

		for (int tankSlot = 0; tankSlot < fluidTank.getTanks(); tankSlot++) {
			for (int slot = 0; slot < fluidItem.getTanks(); slot++) {

				FluidStack fluidInTank = fluidTank.getFluidInTank(tankSlot);
				int tankCapacity = fluidTank.getTankCapacity(tankSlot) - fluidInTank.getAmount();
				boolean tankEmpty = fluidInTank.isEmpty();

				FluidStack fluidInItem = fluidItem.getFluidInTank(tankSlot);
				int itemCapacity = fluidItem.getTankCapacity(tankSlot) - fluidInItem.getAmount();
				boolean itemEmpty = fluidInItem.isEmpty();

				boolean undecided = lockedExchange == null;
				boolean canMoveToTank = (undecided || lockedExchange == FluidExchange.ITEM_TO_TANK) && tankCapacity > 0;
				boolean canMoveToItem = (undecided || lockedExchange == FluidExchange.TANK_TO_ITEM) && itemCapacity > 0;

				// Incompatible Liquids
				if (!tankEmpty && !itemEmpty && !FluidStack.isSameFluidSameComponents(fluidInItem, fluidInTank))
					continue;

				// Transfer liquid to tank
				if (((tankEmpty || itemCapacity <= 0) && canMoveToTank)
					|| undecided && preferred == FluidExchange.ITEM_TO_TANK) {

					int amount = fluidTank.fill(
						fluidItem.drain(Math.min(maxTransferAmountPerTank, tankCapacity), FluidAction.EXECUTE),
						FluidAction.EXECUTE);
					if (amount > 0) {
						lockedExchange = FluidExchange.ITEM_TO_TANK;
						if (singleOp)
							return lockedExchange;
						continue;
					}
				}

				// Transfer liquid from tank
				if (((itemEmpty || tankCapacity <= 0) && canMoveToItem)
					|| undecided && preferred == FluidExchange.TANK_TO_ITEM) {

					int amount = fluidItem.fill(
						fluidTank.drain(Math.min(maxTransferAmountPerTank, itemCapacity), FluidAction.EXECUTE),
						FluidAction.EXECUTE);
					if (amount > 0) {
						lockedExchange = FluidExchange.TANK_TO_ITEM;
						if (singleOp)
							return lockedExchange;
						continue;
					}

				}

			}
		}

		return null;
	}

}

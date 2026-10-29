package dev.createpolymer.trains.schedule.condition;

import java.util.List;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.fluids.transfer.GenericItemEmptying;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.logistics.filter.FilterItemStack.ListFilterItemStack;
import dev.createpolymer.trains.entity.Carriage;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.schedule.ScheduleConfigBuilder;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FluidThresholdCondition extends CargoThresholdCondition {

	private FilterItemStack compareStack = FilterItemStack.empty();

	@Override
	protected Component getUnit() {
		return Component.literal("b");
	}

	@Override
	protected ItemStack getIcon() {
		return compareStack.item();
	}

	@Override
	protected boolean test(Level level, Train train, CompoundTag context) {
		Ops operator = getOperator();
		int target = getThreshold();

		int foundFluid = 0;
		for (Carriage carriage : train.carriages) {
			IFluidHandler fluids = carriage.storage.getFluids();
			for (int i = 0; i < fluids.getTanks(); i++) {
				FluidStack fluidInTank = fluids.getFluidInTank(i);
				if (!testFluid(level, compareStack, fluidInTank, false))
					continue;
				foundFluid += fluidInTank.getAmount();
			}
		}

		requestStatusToUpdate(foundFluid / 1000, context);
		return operator.test(foundFluid, target * 1000);
	}

	/**
	 * Create's FilterItemStack#test(Level, FluidStack): the filter is an item that holds a fluid, or a list filter
	 * of such items. Filters do not match fluids in this port, so the condition does it.
	 */
	private static boolean testFluid(Level level, FilterItemStack filter, FluidStack stack, boolean matchNBT) {
		if (filter.isEmpty())
			return true;
		if (filter instanceof ListFilterItemStack list) {
			for (FilterItemStack contained : list.containedItems)
				if (testFluid(level, contained, stack, list.shouldRespectNBT))
					return !list.isBlacklist;
			return list.isBlacklist;
		}
		if (filter.isFilterItem())
			return false;

		FluidStack filterFluid = GenericItemEmptying.emptyItem(level, filter.item(), true)
			.getFirst();
		if (filterFluid.isEmpty())
			return false;
		if (!matchNBT)
			return filterFluid.getFluid()
				.isSame(stack.getFluid());
		return FluidStack.isSameFluidSameComponents(filterFluid, stack);
	}

	@Override
	protected void writeAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.writeAdditional(registries, tag);
		tag.put("Bucket", writeStack(registries, compareStack.item()));
	}

	@Override
	protected void readAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.readAdditional(registries, tag);
		if (tag.contains("Bucket"))
			compareStack = FilterItemStack.of(readStack(registries, tag.getCompoundOrEmpty("Bucket")));
	}

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		return super.tickCompletion(level, train, context);
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(
			ScheduleLang.translate("schedule.condition.threshold.train_holds",
				ScheduleLang.translate("schedule.condition.threshold." + Lang.asId(getOperator().name()))),
			ScheduleLang.translate("schedule.condition.threshold.x_units_of_item", getThreshold(),
				ScheduleLang.translate("schedule.condition.threshold.buckets"),
				compareStack.isEmpty() ? ScheduleLang.translate("schedule.condition.threshold.anything")
					: compareStack.isFilterItem()
						? ScheduleLang.translate("schedule.condition.threshold.matching_content")
						: compareStack.item().getHoverName())
				.withStyle(ChatFormatting.DARK_AQUA));
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		compareStack = FilterItemStack.of(stack);
	}

	@Override
	public ItemStack getItem(int slot) {
		return compareStack.item();
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("fluid_threshold");
	}

	@Override
	public void initConfigurationWidgets(ScheduleConfigBuilder builder) {
		super.initConfigurationWidgets(builder);
	}

	@Override
	public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
		int lastDisplaySnapshot = getLastDisplaySnapshot(tag);
		if (lastDisplaySnapshot == -1)
			return Component.empty();
		int offset = getOperator() == Ops.LESS ? -1 : getOperator() == Ops.GREATER ? 1 : 0;
		return ScheduleLang.translate("schedule.condition.threshold.status", lastDisplaySnapshot,
			Math.max(0, getThreshold() + offset), ScheduleLang.translate("schedule.condition.threshold.buckets"));
	}

}

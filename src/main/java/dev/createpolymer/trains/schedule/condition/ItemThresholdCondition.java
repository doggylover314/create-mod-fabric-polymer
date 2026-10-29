package dev.createpolymer.trains.schedule.condition;

import java.util.List;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.logistics.filter.FilterItemStack;
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

public class ItemThresholdCondition extends CargoThresholdCondition {

	private FilterItemStack stack = FilterItemStack.empty();

	@Override
	protected Component getUnit() {
		return Component.literal(inStacks() ? "▤" : "");
	}

	@Override
	protected ItemStack getIcon() {
		return stack.item();
	}

	@Override
	protected boolean test(Level level, Train train, CompoundTag context) {
		Ops operator = getOperator();
		int target = getThreshold();
		boolean stacks = inStacks();

		int foundItems = 0;
		for (Carriage carriage : train.carriages) {
			IItemHandlerModifiable items = carriage.storage.getAllItems();
			for (int i = 0; i < items.getSlots(); i++) {
				ItemStack stackInSlot = items.getStackInSlot(i);
				if (!stack.test(level, stackInSlot))
					continue;

				if (stacks)
					foundItems += stackInSlot.getCount() == stackInSlot.getMaxStackSize() ? 1 : 0;
				else
					foundItems += stackInSlot.getCount();
			}
		}

		requestStatusToUpdate(foundItems, context);
		return operator.test(foundItems, target);
	}

	@Override
	protected void writeAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.writeAdditional(registries, tag);
		tag.put("Item", writeStack(registries, stack.item()));
	}

	@Override
	protected void readAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		super.readAdditional(registries, tag);
		if (tag.contains("Item"))
			stack = FilterItemStack.of(readStack(registries, tag.getCompoundOrEmpty("Item")));
	}

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		return super.tickCompletion(level, train, context);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.stack = FilterItemStack.of(stack);
	}

	@Override
	public ItemStack getItem(int slot) {
		return stack.item();
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(
			ScheduleLang.translate("schedule.condition.threshold.train_holds",
				ScheduleLang.translate("schedule.condition.threshold." + Lang.asId(getOperator().name()))),
			ScheduleLang.translate("schedule.condition.threshold.x_units_of_item", getThreshold(),
				ScheduleLang.translate("schedule.condition.threshold." + (inStacks() ? "stacks" : "items")),
				stack.isEmpty() ? ScheduleLang.translate("schedule.condition.threshold.anything")
					: stack.isFilterItem()
						? ScheduleLang.translate("schedule.condition.threshold.matching_content")
						: stack.item().getHoverName())
				.withStyle(ChatFormatting.DARK_AQUA));
	}

	private boolean inStacks() {
		return intData("Measure") == 1;
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("item_threshold");
	}

	@Override
	public void initConfigurationWidgets(ScheduleConfigBuilder builder) {
		super.initConfigurationWidgets(builder);
		builder.addSelectionScrollInput("Measure", ScheduleLang.translate("schedule.condition.threshold.item_measure"),
			ImmutableList.of(ScheduleLang.translate("schedule.condition.threshold.items"),
				ScheduleLang.translate("schedule.condition.threshold.stacks")));
	}

	@Override
	public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
		int lastDisplaySnapshot = getLastDisplaySnapshot(tag);
		if (lastDisplaySnapshot == -1)
			return Component.empty();
		int offset = getOperator() == Ops.LESS ? -1 : getOperator() == Ops.GREATER ? 1 : 0;
		return ScheduleLang.translate("schedule.condition.threshold.status", lastDisplaySnapshot,
			Math.max(0, getThreshold() + offset),
			ScheduleLang.translate("schedule.condition.threshold." + (inStacks() ? "stacks" : "items")));
	}
}

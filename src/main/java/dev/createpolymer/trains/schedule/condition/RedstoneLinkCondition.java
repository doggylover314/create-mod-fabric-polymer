package dev.createpolymer.trains.schedule.condition;

import java.util.List;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.redstone.link.RedstoneLinkNetworkHandler;
import dev.createpolymer.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.schedule.ScheduleConfigBuilder;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class RedstoneLinkCondition extends ScheduleWaitCondition {

	public Couple<Frequency> freq;

	public RedstoneLinkCondition() {
		freq = Couple.create(() -> Frequency.EMPTY);
	}

	@Override
	public int slotsTargeted() {
		return 2;
	}

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(new ItemStack(RedstoneRegistry.REDSTONE_LINK_ITEM),
			lowActivation() ? ScheduleLang.translate("schedule.condition.redstone_link_off")
				: ScheduleLang.translate("schedule.condition.redstone_link_on"));
	}

	@Override
	public List<Component> getSecondLineTooltip(int slot) {
		return ImmutableList.of(ScheduleLang.translate(slot == 0 ? "logistics.firstFrequency" : "logistics.secondFrequency")
			.withStyle(ChatFormatting.RED));
	}

	@Override
	public List<Component> getTitleAs(String type) {
		return ImmutableList.of(
			ScheduleLang.translate("schedule.condition.redstone_link.frequency_" + (lowActivation() ? "unpowered" : "powered")),
			Component.literal(" #1 ").withStyle(ChatFormatting.GRAY)
				.append(freq.getFirst()
					.getStack()
					.getHoverName()
					.copy()
					.withStyle(ChatFormatting.DARK_AQUA)),
			Component.literal(" #2 ").withStyle(ChatFormatting.GRAY)
				.append(freq.getSecond()
					.getStack()
					.getHoverName()
					.copy()
					.withStyle(ChatFormatting.DARK_AQUA)));
	}

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		int lastChecked = context.getIntOr("LastChecked", -1);
		int status = RedstoneLinkNetworkHandler.INSTANCE.globalPowerVersion.get();
		if (status == lastChecked)
			return false;
		context.putInt("LastChecked", status);
		return RedstoneLinkNetworkHandler.INSTANCE.hasAnyLoadedPower(freq) != lowActivation();
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		freq.set(slot == 0, Frequency.of(stack));
		super.setItem(slot, stack);
	}

	@Override
	public ItemStack getItem(int slot) {
		return freq.get(slot == 0)
			.getStack();
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("redstone_link");
	}

	@Override
	protected void writeAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		tag.put("Frequency", freq.serializeEach(f -> {
			var saved = writeStack(registries, f.getStack());
			return saved instanceof CompoundTag compound ? compound : new CompoundTag();
		}));
	}

	public boolean lowActivation() {
		return intData("Inverted") == 1;
	}

	@Override
	protected void readAdditional(HolderLookup.Provider registries, CompoundTag tag) {
		if (tag.contains("Frequency"))
			freq = Couple.deserializeEach(tag.getListOrEmpty("Frequency"),
				c -> Frequency.of(readStack(registries, c)));
	}

	@Override
	public void initConfigurationWidgets(ScheduleConfigBuilder builder) {
		builder.addSelectionScrollInput("Inverted", ScheduleLang.translate("schedule.condition.redstone_link.frequency_state"),
			ScheduleLang.translatedOptions("schedule.condition.redstone_link", "powered", "unpowered"));
	}

	@Override
	public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
		return ScheduleLang.translate("schedule.condition.redstone_link.status");
	}

}

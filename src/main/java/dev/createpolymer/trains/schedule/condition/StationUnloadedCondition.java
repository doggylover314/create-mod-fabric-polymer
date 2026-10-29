package dev.createpolymer.trains.schedule.condition;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.trains.station.GlobalStation;
import dev.createpolymer.util.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class StationUnloadedCondition extends ScheduleWaitCondition {
	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(ItemStack.EMPTY, ScheduleLang.translate("schedule.condition.unloaded"));
	}

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		GlobalStation currentStation = train.getCurrentStation();
		if (currentStation == null)
			return false;
		ResourceKey<Level> stationDim = currentStation.getBlockEntityDimension();
		MinecraftServer server = level.getServer();
		if (server == null)
			return false;
		ServerLevel stationLevel = server.getLevel(stationDim);
		if (stationLevel == null) {
			return false;
		}
		return !stationLevel.isPositionEntityTicking(currentStation.getBlockEntityPos());
	}

	@Override
	protected void writeAdditional(HolderLookup.Provider registries, CompoundTag tag) {}

	@Override
	protected void readAdditional(HolderLookup.Provider registries, CompoundTag tag) {}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("unloaded");
	}

	@Override
	public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
		return ScheduleLang.translate("schedule.condition.unloaded.status");
	}
}

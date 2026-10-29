package dev.createpolymer.trains.schedule.condition;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.trains.entity.Carriage;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.util.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class IdleCargoCondition extends TimedWaitCondition {

	@Override
	public Pair<ItemStack, Component> getSummary() {
		return Pair.of(ItemStack.EMPTY, ScheduleLang.translate("schedule.condition.idle_short", formatTime(true)));
	}

	@Override
	public Identifier getId() {
		return CreatePolymer.id("idle");
	}

	@Override
	public boolean tickCompletion(Level level, Train train, CompoundTag context) {
		int idleTime = Integer.MAX_VALUE;
		for (Carriage carriage : train.carriages)
			idleTime = Math.min(idleTime, carriage.storage.getTicksSinceLastExchange());
		context.putInt("Time", idleTime);
		requestDisplayIfNecessary(context, idleTime);
		return idleTime > totalWaitTicks();
	}

}

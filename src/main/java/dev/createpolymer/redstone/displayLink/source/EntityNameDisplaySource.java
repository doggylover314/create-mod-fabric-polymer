package dev.createpolymer.redstone.displayLink.source;

import java.util.List;

import dev.createpolymer.contraptions.actors.seat.SeatEntity;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public class EntityNameDisplaySource extends SingleLineDisplaySource {
	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		List<SeatEntity> seats = context.level().getEntitiesOfClass(SeatEntity.class, new AABB(context.getSourcePos()));

		if (seats.isEmpty())
			return EMPTY_LINE;

		SeatEntity seatEntity = seats.get(0);
		List<Entity> passengers = seatEntity.getPassengers();

		if (passengers.isEmpty())
			return EMPTY_LINE;

		return Component.literal(passengers.get(0).getDisplayName().getString());
	}

	@Override
	protected String getTranslationKey() {
		return "entity_name";
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}
}

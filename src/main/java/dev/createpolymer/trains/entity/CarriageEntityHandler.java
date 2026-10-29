package dev.createpolymer.trains.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Carriage entities only exist in chunks that tick entities: when a carriage leaves them, the train removes and
 * saves its entity (Create does the same from an entity section event; here the train checks every tick).
 */
public class CarriageEntityHandler {

	public static void validateCarriageEntity(CarriageContraptionEntity entity) {
		if (!entity.isAlive())
			return;
		Level level = entity.level();
		if (level.isClientSide())
			return;
		if (!isActiveChunk(level, entity.blockPosition()))
			entity.leftTickingChunks = true;
	}

	public static boolean isActiveChunk(Level level, BlockPos pos) {
		if (level instanceof ServerLevel serverLevel)
			return serverLevel.isPositionEntityTicking(pos);
		return false;
	}

}

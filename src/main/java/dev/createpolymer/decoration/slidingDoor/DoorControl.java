package dev.createpolymer.decoration.slidingDoor;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import dev.createpolymer.util.Lang;

/** Which side of a train or elevator opens its doors (the setting of Create's station and elevator contact screens). */
public enum DoorControl {

	ALL, NORTH, EAST, SOUTH, WEST, NONE;

	public boolean matches(Direction doorDirection) {
		return switch (this) {
		case ALL -> true;
		case NORTH -> doorDirection == Direction.NORTH;
		case EAST -> doorDirection == Direction.EAST;
		case SOUTH -> doorDirection == Direction.SOUTH;
		case WEST -> doorDirection == Direction.WEST;
		default -> false;
		};
	}

	public DoorControl next() {
		return values()[(ordinal() + 1) % values().length];
	}

	/** Create's "contraption.door_control.<mode>" lang entry: the long name. */
	public Component translate() {
		return Component.translatable("create.contraption.door_control." + Lang.asId(name()));
	}

	/** Create's "contraption.door_control.<mode>.short" lang entry. */
	public Component translateShort() {
		return Component.translatable("create.contraption.door_control." + Lang.asId(name()) + ".short");
	}

	/** The mode that matches the way a player looks, for {@link #NONE} if they look up or down. */
	public static DoorControl facing(Direction direction) {
		return switch (direction) {
		case EAST -> EAST;
		case WEST -> WEST;
		case NORTH -> NORTH;
		case SOUTH -> SOUTH;
		default -> NONE;
		};
	}

}

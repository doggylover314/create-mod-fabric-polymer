package dev.createpolymer.schematics.cannon;

import dev.createpolymer.schematics.cannon.SchematicannonBlockEntity.State;

/**
 * Create's ConfigureSchematicannonPacket, without the packet: the GUI calls {@link #apply} for the buttons the
 * client screen sends it for.
 */
public final class ConfigureSchematicannon {
	private ConfigureSchematicannon() {}

	public enum Option {
		DONT_REPLACE, REPLACE_SOLID, REPLACE_ANY, REPLACE_EMPTY, SKIP_MISSING, SKIP_BLOCK_ENTITIES, PLAY, PAUSE, STOP;
	}

	public static void apply(SchematicannonBlockEntity be, Option option, boolean set) {
		switch (option) {
			case DONT_REPLACE:
			case REPLACE_ANY:
			case REPLACE_EMPTY:
			case REPLACE_SOLID:
				be.replaceMode = option.ordinal();
				break;
			case SKIP_MISSING:
				be.skipMissing = set;
				break;
			case SKIP_BLOCK_ENTITIES:
				be.replaceBlockEntities = set;
				break;

			case PLAY:
				be.state = State.RUNNING;
				be.statusMsg = "running";
				break;
			case PAUSE:
				be.state = State.PAUSED;
				be.statusMsg = "paused";
				break;
			case STOP:
				be.state = State.STOPPED;
				be.statusMsg = "stopped";
				break;
			default:
				break;
		}

		be.sendUpdate = true;
		be.setChanged();
	}
}

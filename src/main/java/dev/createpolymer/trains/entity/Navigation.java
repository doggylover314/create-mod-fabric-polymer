package dev.createpolymer.trains.entity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.util.Pair;

/**
 * Create's Navigation. The railway manager only reads which signal a train waits for.
 */
public class Navigation {

	@Nullable
	public Pair<UUID, Boolean> waitingForSignal;

}

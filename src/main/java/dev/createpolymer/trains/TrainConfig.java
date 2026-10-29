package dev.createpolymer.trains;

/**
 * Create's CTrains server settings, with Create's defaults.
 */
public final class TrainConfig {
	private TrainConfig() {}

	public static int maxTrackPlacementLength = 32;
	public static boolean trainsCauseDamage = true;
	public static int maxAssemblyLength = 128;
	public static int maxBogeyCount = 20;
}

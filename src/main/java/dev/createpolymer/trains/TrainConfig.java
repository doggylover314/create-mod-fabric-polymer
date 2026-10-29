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
	public static float manualTrainSpeedModifier = .75f;

	// Standard trains (blocks per second, blocks per second squared)
	public static float trainTopSpeed = 28;
	public static float trainTurningTopSpeed = 14;
	public static float trainAcceleration = 3;

	// Powered trains
	public static float poweredTrainTopSpeed = 40;
	public static float poweredTrainTurningTopSpeed = 20;
	public static float poweredTrainAcceleration = 3;
}

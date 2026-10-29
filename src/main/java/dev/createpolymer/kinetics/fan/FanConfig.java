package dev.createpolymer.kinetics.fan;

/**
 * Create's fan settings (CKinetics), with Create's defaults.
 */
public final class FanConfig {
	private FanConfig() {}

	/** Maximum distance in blocks Fans can push entities. */
	public static int fanPushDistance = 20;
	/** Maximum distance in blocks from where Fans can pull entities. */
	public static int fanPullDistance = 20;
	/** Game ticks between Fans checking for anything blocking their air flow. */
	public static int fanBlockCheckRate = 30;
	/** Rotation speed at which the maximum stats of fans are reached. */
	public static int fanRotationArgmax = 256;
	/** Game ticks required for a Fan-based processing recipe to take effect. */
	public static int fanProcessingTime = 150;
	/** Chance per tick that a fan sends one air flow particle to nearby players (Create: client option fanParticleDensity). */
	public static float fanParticleDensity = .25f;
}

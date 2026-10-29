package dev.createpolymer;

/**
 * Server settings. Defaults match Create's own config (CKinetics / CRecipes).
 */
public final class Config {
	private Config() {}

	public static int maxRotationSpeed = 256;
	public static int kineticValidationFrequency = 60;
	public static float crankHungerMultiplier = .01f;
	public static float mediumSpeed = 30;
	public static float fastSpeed = 100;
	public static boolean disableStress = false;

	public static boolean bulkPressing = false;
	public static boolean allowShapelessInMixer = true;
	public static boolean allowShapedSquareInPress = true;
}

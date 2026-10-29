package dev.createpolymer.equipment;

/**
 * Create's CEquipment server config, with Create's defaults.
 */
public final class EquipmentConfig {
	private EquipmentConfig() {}

	/** The maximum distance at which a toolbox interacts with players' inventories. */
	public static int toolboxRange = 10;
	/** The maximum volume of air a backtank holds = seconds of underwater breathing. */
	public static int airInBacktank = 900;
	/** The volume of air added by each level of the backtank's capacity enchantment. */
	public static int enchantedBacktankCapacity = 300;
	/** Free extendo grip actions provided by one filled copper backtank. 0 makes extendo grips unbreakable. */
	public static int maxExtendoGripActions = 1000;
	/** Free potato cannon shots provided by one filled copper backtank. 0 makes potato cannons unbreakable. */
	public static int maxPotatoCannonShots = 200;
}

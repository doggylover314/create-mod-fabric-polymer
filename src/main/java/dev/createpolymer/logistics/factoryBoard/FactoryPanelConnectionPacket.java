package dev.createpolymer.logistics.factoryBoard;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Create's FactoryPanelConnectionPacket, applied directly by {@link FactoryPanelConnectionHandler} instead of being
 * sent to the server.
 */
public class FactoryPanelConnectionPacket {

	/** Create's BlockEntityConfigurationPacket.maxRange is 20 and this packet doubles it. */
	private static final int MAX_RANGE = 40;

	private final FactoryPanelPosition fromPos;
	private final FactoryPanelPosition toPos;
	private final boolean relocate;

	public FactoryPanelConnectionPacket(FactoryPanelPosition fromPos, FactoryPanelPosition toPos, boolean relocate) {
		this.fromPos = fromPos;
		this.toPos = toPos;
		this.relocate = relocate;
	}

	public void applySettings(ServerPlayer player) {
		Level level = player.level();
		if (!level.isLoaded(toPos.pos()) || !player.blockPosition()
			.closerThan(toPos.pos(), MAX_RANGE))
			return;
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(level, toPos);
		if (behaviour != null)
			if (relocate)
				behaviour.moveTo(fromPos, player);
			else
				behaviour.addConnection(fromPos);
	}

}

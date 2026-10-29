package dev.createpolymer.logistics.factoryBoard;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Create's FactoryPanelConfigurationPacket. Create's screen sends it to the server when it closes; here the GUI
 * builds it and applies it itself, so there is no packet and no range check beyond the GUI's own.
 */
public class FactoryPanelConfigurationPacket {

	private final FactoryPanelPosition position;
	private final String address;
	private final Map<FactoryPanelPosition, Integer> inputAmounts;
	private final List<ItemStack> craftingArrangement;
	private final int outputAmount;
	private final int promiseClearingInterval;
	private final FactoryPanelPosition removeConnection;
	private final boolean clearPromises;
	private final boolean reset;
	private final boolean redstoneReset;

	public FactoryPanelConfigurationPacket(FactoryPanelPosition position, String address,
		Map<FactoryPanelPosition, Integer> inputAmounts, List<ItemStack> craftingArrangement, int outputAmount,
		int promiseClearingInterval, @Nullable FactoryPanelPosition removeConnection, boolean clearPromises,
		boolean reset, boolean sendRedstoneReset) {
		this.position = position;
		this.address = address;
		this.inputAmounts = inputAmounts;
		this.craftingArrangement = craftingArrangement;
		this.outputAmount = outputAmount;
		this.promiseClearingInterval = promiseClearingInterval;
		this.removeConnection = removeConnection;
		this.clearPromises = clearPromises;
		this.reset = reset;
		this.redstoneReset = sendRedstoneReset;
	}

	public void applySettings(ServerPlayer player, FactoryPanelBlockEntity be) {
		FactoryPanelBehaviour behaviour = be.panels.get(position.slot());
		if (behaviour == null)
			return;

		behaviour.recipeAddress = reset ? "" : address;
		behaviour.recipeOutput = reset ? 1 : outputAmount;
		behaviour.promiseClearingInterval = reset ? -1 : promiseClearingInterval;
		behaviour.activeCraftingArrangement = reset ? List.of() : craftingArrangement;

		if (reset) {
			behaviour.forceClearPromises = true;
			behaviour.disconnectAll();
			behaviour.setFilter(ItemStack.EMPTY);
			behaviour.count = 0;
			be.redraw = true;
			be.notifyUpdate();
			return;
		}

		if (redstoneReset) {
			behaviour.disconnectAllLinks();
			be.notifyUpdate();
			return;
		}

		for (Entry<FactoryPanelPosition, Integer> entry : inputAmounts.entrySet()) {
			FactoryPanelPosition key = entry.getKey();
			FactoryPanelConnection connection = behaviour.targetedBy.get(key);
			if (connection != null)
				connection.amount = entry.getValue();
		}

		if (removeConnection != null) {
			behaviour.targetedBy.remove(removeConnection);
			FactoryPanelBehaviour source = FactoryPanelBehaviour.at(be.getLevel(), removeConnection);
			if (source != null) {
				source.targeting.remove(behaviour.getPanelPosition());
				source.blockEntity.sendData();
			}
		}

		if (clearPromises)
			behaviour.forceClearPromises = true;

		be.notifyUpdate();
	}
}

package dev.createpolymer.logistics.packagePort;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Create's AnimatedContainerBehaviour: counts the players that have the block's GUI open so the block can animate
 * (the frogport opens its mouth, the postbox its lid). Create counts by looking for players whose open menu is of a
 * given class; here the GUI reports in with {@link #startOpen} and {@link #stopOpen}, and players that went away
 * without closing are dropped on the lazy tick.
 */
public class AnimatedContainerBehaviour extends BlockEntityBehaviour {

	public static final BehaviourType<AnimatedContainerBehaviour> TYPE = new BehaviourType<>();

	public int openCount;

	private final Set<UUID> viewers = new HashSet<>();
	private Consumer<Boolean> openChanged;

	public AnimatedContainerBehaviour(SmartBlockEntity be) {
		super(be);
		openCount = 0;
	}

	public void onOpenChanged(Consumer<Boolean> openChanged) {
		this.openChanged = openChanged;
	}

	@Override
	public void lazyTick() {
		updateOpenCount();
		super.lazyTick();
	}

	void updateOpenCount() {
		if (viewers.isEmpty())
			return;
		for (Iterator<UUID> iterator = viewers.iterator(); iterator.hasNext();) {
			Player player = getWorld().getPlayerByUUID(iterator.next());
			if (player == null || !player.isAlive() || player instanceof ServerPlayer sp && sp.hasDisconnected())
				iterator.remove();
		}
		changeTo(viewers.size());
	}

	private void changeTo(int count) {
		int prevOpenCount = openCount;
		openCount = count;
		if (prevOpenCount == count)
			return;
		if (openChanged != null && prevOpenCount == 0 && count > 0)
			openChanged.accept(true);
		if (openChanged != null && prevOpenCount > 0 && count == 0)
			openChanged.accept(false);
		blockEntity.sendData();
	}

	public void startOpen(Player player) {
		if (player.isSpectator())
			return;
		if (getWorld().isClientSide())
			return;
		viewers.add(player.getUUID());
		changeTo(viewers.size());
	}

	public void stopOpen(Player player) {
		if (player.isSpectator())
			return;
		if (getWorld().isClientSide())
			return;
		viewers.remove(player.getUUID());
		changeTo(viewers.size());
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

}

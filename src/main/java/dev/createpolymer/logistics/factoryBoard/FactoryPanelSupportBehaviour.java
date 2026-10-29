package dev.createpolymer.logistics.factoryBoard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class FactoryPanelSupportBehaviour extends BlockEntityBehaviour {

	public static final BehaviourType<FactoryPanelSupportBehaviour> TYPE = new BehaviourType<>();

	private static final Codec<List<FactoryPanelPosition>> PANELS_CODEC = Codec.list(FactoryPanelPosition.CODEC);

	private List<FactoryPanelPosition> linkedPanels;
	private boolean changed;

	private Supplier<Boolean> outputPower;
	private Supplier<Boolean> isOutput;
	private Runnable onNotify;

	public FactoryPanelSupportBehaviour(SmartBlockEntity be, Supplier<Boolean> isOutput, Supplier<Boolean> outputPower,
										Runnable onNotify) {
		super(be);
		this.isOutput = isOutput;
		this.outputPower = outputPower;
		this.onNotify = onNotify;
		linkedPanels = new ArrayList<>();
	}

	public boolean shouldPanelBePowered() {
		return isOutput() && outputPower.get();
	}

	public boolean isOutput() {
		return isOutput.get();
	}

	public void notifyLink() {
		onNotify.run();
	}

	@Override
	public void destroy() {
		for (FactoryPanelPosition panelPos : linkedPanels) {
			if (!getWorld().isLoaded(panelPos.pos()))
				continue;
			FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(getWorld(), panelPos);
			if (behaviour == null)
				continue;
			behaviour.targetedByLinks.remove(getPos());
			behaviour.blockEntity.notifyUpdate();
		}
		super.destroy();
	}

	public void notifyPanels() {
		if (getWorld().isClientSide())
			return;
		for (Iterator<FactoryPanelPosition> iterator = linkedPanels.iterator(); iterator.hasNext(); ) {
			FactoryPanelPosition panelPos = iterator.next();
			if (!getWorld().isLoaded(panelPos.pos()))
				continue;
			FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(getWorld(), panelPos);
			if (behaviour == null) {
				iterator.remove();
				changed = true;
				continue;
			}
			behaviour.checkForRedstoneInput();
		}
	}

	@Nullable
	public Boolean shouldBePoweredTristate() {
		for (Iterator<FactoryPanelPosition> iterator = linkedPanels.iterator(); iterator.hasNext(); ) {
			FactoryPanelPosition panelPos = iterator.next();
			if (!getWorld().isLoaded(panelPos.pos()))
				return null;
			FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(getWorld(), panelPos);
			if (behaviour == null) {
				iterator.remove();
				changed = true;
				continue;
			}
			if (behaviour.isActive() && behaviour.satisfied && behaviour.count != 0)
				return true;
		}
		return false;
	}

	public List<FactoryPanelPosition> getLinkedPanels() {
		return linkedPanels;
	}

	public void connect(FactoryPanelBehaviour panel) {
		FactoryPanelPosition panelPosition = panel.getPanelPosition();
		if (linkedPanels.contains(panelPosition))
			return;
		linkedPanels.add(panelPosition);
		changed = true;
	}

	public void disconnect(FactoryPanelBehaviour panel) {
		linkedPanels.remove(panel.getPanelPosition());
		changed = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (changed) {
			changed = false;
			if (!isOutput())
				notifyLink();
			blockEntity.setChanged();
		}
	}

	@Override
	public void write(ValueOutput nbt) {
		nbt.store("LinkedGauges", PANELS_CODEC, linkedPanels);
	}

	@Override
	public void read(ValueInput nbt) {
		linkedPanels.clear();
		nbt.read("LinkedGauges", PANELS_CODEC)
			.ifPresent(linkedPanels::addAll);
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

}

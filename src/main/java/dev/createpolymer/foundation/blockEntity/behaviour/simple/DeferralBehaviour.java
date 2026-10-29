package dev.createpolymer.foundation.blockEntity.behaviour.simple;

import java.util.function.Supplier;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class DeferralBehaviour extends BlockEntityBehaviour {

	public static final BehaviourType<DeferralBehaviour> TYPE = new BehaviourType<>();

	private boolean needsUpdate;
	private Supplier<Boolean> callback;

	public DeferralBehaviour(SmartBlockEntity be, Supplier<Boolean> callback) {
		super(be);
		this.callback = callback;
	}

	@Override
	public void write(ValueOutput nbt) {
		nbt.putBoolean("NeedsUpdate", needsUpdate);
		super.write(nbt);
	}

	@Override
	public void read(ValueInput nbt) {
		needsUpdate = nbt.getBooleanOr("NeedsUpdate", false);
		super.read(nbt);
	}

	@Override
	public void tick() {
		super.tick();
		if (needsUpdate && callback.get())
			needsUpdate = false;
	}

	public void scheduleUpdate() {
		needsUpdate = true;
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

}

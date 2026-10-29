package dev.createpolymer.decoration.slidingDoor;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class DoorControlBehaviour extends BlockEntityBehaviour {

	public static final BehaviourType<DoorControlBehaviour> TYPE = new BehaviourType<>();

	public DoorControl mode;

	public DoorControlBehaviour(SmartBlockEntity be) {
		super(be);
		mode = DoorControl.ALL;
	}

	public void set(DoorControl mode) {
		if (this.mode == mode)
			return;
		this.mode = mode;
		blockEntity.notifyUpdate();
	}

	@Override
	public void write(ValueOutput nbt) {
		NBTHelper.writeEnum(nbt, "DoorControl", mode);
		super.write(nbt);
	}

	@Override
	public void read(ValueInput nbt) {
		mode = NBTHelper.readEnum(nbt, "DoorControl", DoorControl.class);
		super.read(nbt);
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

}

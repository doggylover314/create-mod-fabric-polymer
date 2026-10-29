package dev.createpolymer.logistics.tunnel;

import dev.createpolymer.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;

import net.minecraft.core.Direction;

public class BrassTunnelModeSlot extends CenteredSideValueBoxTransform {

	public BrassTunnelModeSlot() {
		super((state, d) -> d == Direction.UP);
	}
	
	@Override
	public int getOverrideColor() {
		return 0x592424;
	}
	
}

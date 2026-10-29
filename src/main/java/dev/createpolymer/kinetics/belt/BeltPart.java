package dev.createpolymer.kinetics.belt;

import dev.createpolymer.util.Lang;
import net.minecraft.util.StringRepresentable;

public enum BeltPart implements StringRepresentable {
	START, MIDDLE, END, PULLEY;

	@Override
	public String getSerializedName() {
		return Lang.asId(name());
	}
}

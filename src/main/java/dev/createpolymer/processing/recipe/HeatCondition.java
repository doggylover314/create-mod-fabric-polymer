package dev.createpolymer.processing.recipe;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Ported from Create's HeatCondition (MIT, The Create Team).
 * Blaze burners are not ported yet, so heated recipes never run.
 */
public enum HeatCondition implements StringRepresentable {
	NONE, HEATED, SUPERHEATED;

	public static final Codec<HeatCondition> CODEC = StringRepresentable.fromEnum(HeatCondition::values);

	/** Only "no heat" is available until the blaze burner is ported. */
	public boolean isMetWithoutHeat() {
		return this == NONE;
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}

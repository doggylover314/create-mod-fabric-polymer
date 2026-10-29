package dev.createpolymer.processing.recipe;

import com.mojang.serialization.Codec;

import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.util.Lang;
import net.minecraft.util.StringRepresentable;

/** Ported from Create's HeatCondition (MIT, The Create Team). */
public enum HeatCondition implements StringRepresentable {

	NONE(0xffffff), HEATED(0xE88300), SUPERHEATED(0x5C93E8),

	;

	private int color;

	public static final Codec<HeatCondition> CODEC = StringRepresentable.fromEnum(HeatCondition::values);

	HeatCondition(int color) {
		this.color = color;
	}

	public boolean testBlazeBurner(BlazeBurnerBlock.HeatLevel level) {
		if (this == SUPERHEATED)
			return level == HeatLevel.SEETHING;
		if (this == HEATED)
			return level != HeatLevel.NONE && level != HeatLevel.SMOULDERING;
		return true;
	}

	public BlazeBurnerBlock.HeatLevel visualizeAsBlazeBurner() {
		if (this == SUPERHEATED)
			return HeatLevel.SEETHING;
		if (this == HEATED)
			return HeatLevel.KINDLED;
		return HeatLevel.NONE;
	}

	@Override
	public String getSerializedName() {
		return Lang.asId(name());
	}

	public String getTranslationKey() {
		return "recipe.heat_requirement." + getSerializedName();
	}

	public int getColor() {
		return color;
	}

}

package dev.createpolymer.equipment.zapper.terrainzapper;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.Codec;

import dev.createpolymer.util.Lang;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum PlacementOptions implements StringRepresentable {
	Merged(Items.ENDER_EYE),
	Attached(Items.STICKY_PISTON),
	Inserted(Items.HOPPER);

	public static final Codec<PlacementOptions> CODEC = StringRepresentable.fromValues(PlacementOptions::values);

	public final String translationKey;
	/** Create's AllIcons are textures of a client screen; the GUI shows this item instead. */
	public final Item icon;

	PlacementOptions(Item icon) {
		this.translationKey = Lang.asId(name());
		this.icon = icon;
	}

	@Override
	public @NotNull String getSerializedName() {
		return Lang.asId(name());
	}
}

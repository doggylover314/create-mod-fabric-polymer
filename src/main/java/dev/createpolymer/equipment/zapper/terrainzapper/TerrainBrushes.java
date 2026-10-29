package dev.createpolymer.equipment.zapper.terrainzapper;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.Codec;

import dev.createpolymer.util.Lang;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum TerrainBrushes implements StringRepresentable {
	Cuboid(new CuboidBrush(), Items.BRICKS),
	Sphere(new SphereBrush(), Items.SNOWBALL),
	Cylinder(new CylinderBrush(), Items.BARREL),
	Surface(new DynamicBrush(true), Items.GRASS_BLOCK),
	Cluster(new DynamicBrush(false), Items.GRAVEL);

	public static final Codec<TerrainBrushes> CODEC = StringRepresentable.fromValues(TerrainBrushes::values);

	private Brush brush;
	/** The item that stands for the brush in the configuration GUI */
	public final Item icon;

	TerrainBrushes(Brush brush, Item icon) {
		this.brush = brush;
		this.icon = icon;
	}

	public Brush get() {
		return brush;
	}

	@Override
	public @NotNull String getSerializedName() {
		return Lang.asId(name());
	}
}

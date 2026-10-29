package dev.createpolymer.trains.graph;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.util.NBTHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class DimensionPalette {

	private final List<ResourceKey<Level>> gatheredDims;

	public DimensionPalette() {
		gatheredDims = new ArrayList<>();
	}

	public DimensionPalette(List<ResourceKey<Level>> gatheredDims) {
		this.gatheredDims = gatheredDims;
	}

	public int encode(ResourceKey<Level> dimension) {
		int indexOf = gatheredDims.indexOf(dimension);
		if (indexOf == -1) {
			indexOf = gatheredDims.size();
			gatheredDims.add(dimension);
		}
		return indexOf;
	}

	public ResourceKey<Level> decode(int index) {
		if (gatheredDims.size() <= index || index < 0)
			return Level.OVERWORLD;
		return gatheredDims.get(index);
	}

	public void write(CompoundTag tag) {
		tag.put("DimensionPalette", NBTHelper.writeCompoundList(gatheredDims, rk -> {
			CompoundTag c = new CompoundTag();
			c.putString("Id", rk.identifier()
				.toString());
			return c;
		}));
	}

	public static DimensionPalette read(CompoundTag tag) {
		DimensionPalette palette = new DimensionPalette();
		NBTHelper.iterateCompoundList(tag.getListOrEmpty("DimensionPalette"), c -> palette.gatheredDims
			.add(ResourceKey.create(Registries.DIMENSION, Identifier.parse(c.getStringOr("Id", "minecraft:overworld")))));
		return palette;
	}

}

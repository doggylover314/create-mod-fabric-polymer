package dev.createpolymer.trains.track;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.trains.track.TrackMaterial.TrackType.TrackBlockFactory;
import net.minecraft.resources.Identifier;

public class TrackMaterialFactory {
	private final Identifier id;
	private String langName;
	private Supplier<? extends TrackBlock> trackBlock;
	private Identifier particle;
	private TrackMaterial.TrackType trackType = TrackMaterial.TrackType.STANDARD;

	@Nullable
	private TrackBlockFactory customFactory = null;

	public TrackMaterialFactory(Identifier id) {
		this.id = id;
	}

	public static TrackMaterialFactory make(Identifier id) { // Convenience function for static import
		return new TrackMaterialFactory(id);
	}

	public TrackMaterialFactory lang(String langName) {
		this.langName = langName;
		return this;
	}

	public TrackMaterialFactory block(Supplier<? extends TrackBlock> trackBlock) {
		this.trackBlock = trackBlock;
		return this;
	}

	public TrackMaterialFactory particle(Identifier particle) {
		this.particle = particle;
		return this;
	}

	public TrackMaterialFactory trackType(TrackMaterial.TrackType trackType) {
		this.trackType = trackType;
		return this;
	}

	public TrackMaterialFactory customBlockFactory(TrackBlockFactory factory) {
		this.customFactory = factory;
		return this;
	}

	public TrackMaterial build() {
		assert trackBlock != null;
		assert langName != null;
		assert particle != null;
		assert trackType != null;
		assert id != null;
		return new TrackMaterial(id, langName, trackBlock, particle, trackType, customFactory);
	}
}

package dev.createpolymer.trains.track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.track.TrackMaterial.TrackType.TrackBlockFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A kind of track. Create's base mod only has the andesite one; the model and recipe ingredient parts of Create's
 * material are client resources and datagen, which this port does not need.
 */
public class TrackMaterial {
	public static final Map<Identifier, TrackMaterial> ALL = new HashMap<>();

	public static final TrackMaterial ANDESITE = TrackMaterialFactory.make(CreatePolymer.id("andesite"))
		.lang("Andesite")
		.block(() -> TrackRegistry.TRACK)
		.particle(CreatePolymer.id("block/palettes/stone_types/polished/andesite_cut_polished"))
		.build();

	public final Identifier id;
	public final String langName;
	public final Supplier<? extends TrackBlock> trackBlock;
	public final Identifier particle;
	public final TrackType trackType;

	@Nullable
	private final TrackBlockFactory customFactory;

	public TrackMaterial(Identifier id, String langName, Supplier<? extends TrackBlock> trackBlock,
		Identifier particle, TrackType trackType) {
		this(id, langName, trackBlock, particle, trackType, null);
	}

	public TrackMaterial(Identifier id, String langName, Supplier<? extends TrackBlock> trackBlock,
		Identifier particle, TrackType trackType, @Nullable TrackBlockFactory customFactory) {
		this.id = id;
		this.langName = langName;
		this.trackBlock = trackBlock;
		this.particle = particle;
		this.trackType = trackType;
		this.customFactory = customFactory;
		ALL.put(this.id, this);
	}

	public Supplier<? extends TrackBlock> getBlockSupplier() {
		return this.trackBlock;
	}

	public TrackBlock getBlock() {
		return getBlockSupplier().get();
	}

	public ItemStack asStack() {
		return asStack(1);
	}

	public ItemStack asStack(int count) {
		return new ItemStack(getBlock(), count);
	}

	public TrackBlock createBlock(BlockBehaviour.Properties properties) {
		return (this.customFactory != null ? this.customFactory : this.trackType.factory)
			.create(properties, this);
	}

	public boolean isFromMod(String modId) {
		return this.id.getNamespace()
			.equals(modId);
	}

	public static List<TrackMaterial> allFromMod(String modid) {
		return ALL.values()
			.stream()
			.filter(tm -> tm.isFromMod(modid))
			.toList();
	}

	public static List<Supplier<? extends Block>> allBlocksFromMod(String modid) {
		List<Supplier<? extends Block>> list = new ArrayList<>();
		for (TrackMaterial material : allFromMod(modid)) {
			list.add(material.getBlockSupplier());
		}
		return list;
	}

	public static List<Supplier<? extends Block>> allBlocks() {
		List<Supplier<? extends Block>> list = new ArrayList<>();
		for (TrackMaterial material : ALL.values()) {
			list.add(material.getBlockSupplier());
		}
		return list;
	}

	public String resourceName() {
		return this.id.getPath();
	}

	public static TrackMaterial deserialize(String serializedName) {
		if (serializedName.isBlank()) // Data migrating from 0.5
			return ANDESITE;

		Identifier id = Identifier.tryParse(serializedName);
		if (ALL.containsKey(id))
			return ALL.get(id);

		CreatePolymer.LOGGER.error("Failed to locate serialized track material: " + serializedName);
		return ANDESITE;
	}

	public static class TrackType {
		@FunctionalInterface
		public interface TrackBlockFactory {
			TrackBlock create(BlockBehaviour.Properties properties, TrackMaterial material);
		}

		public static final TrackType STANDARD = new TrackType(CreatePolymer.id("standard"), TrackBlock::new);

		public final Identifier id;
		protected final TrackBlockFactory factory;

		public TrackType(Identifier id, TrackBlockFactory factory) {
			this.id = id;
			this.factory = factory;
		}
	}

	public static TrackMaterial fromItem(Item item) {
		if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ITrackBlock trackBlock)
			return trackBlock.getMaterial();
		return TrackMaterial.ANDESITE;
	}

}

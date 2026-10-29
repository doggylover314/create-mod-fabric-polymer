package dev.createpolymer.trains.bogey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import dev.createpolymer.trains.TrackNbt;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * A look for bogeys (Create's BogeyStyle). The renderers of Create are display-entity visuals here: the style
 * creates the {@link BogeyVisual} that draws one bogey of a size.
 */
public class BogeyStyle {
	public final Identifier id;
	public final Identifier cycleGroup;
	public final Component displayName;
	public final Supplier<SoundEvent> soundEvent;
	public final ParticleOptions contactParticle;
	public final ParticleOptions smokeParticle;
	public final CompoundTag defaultData;
	private final Map<BogeySizes.BogeySize, Supplier<? extends AbstractBogeyBlock<?>>> sizes;
	private final Map<BogeySizes.BogeySize, Supplier<? extends BogeyVisual>> visuals;

	public BogeyStyle(Identifier id, Identifier cycleGroup, Component displayName, Supplier<SoundEvent> soundEvent,
		ParticleOptions contactParticle, ParticleOptions smokeParticle, CompoundTag defaultData,
		Map<BogeySizes.BogeySize, Supplier<? extends AbstractBogeyBlock<?>>> sizes,
		Map<BogeySizes.BogeySize, Supplier<? extends BogeyVisual>> visuals) {

		this.id = id;
		this.cycleGroup = cycleGroup;
		this.displayName = displayName;
		this.soundEvent = soundEvent;
		this.contactParticle = contactParticle;
		this.smokeParticle = smokeParticle;
		this.defaultData = defaultData;
		this.sizes = sizes;
		this.visuals = visuals;
	}

	public Map<Identifier, BogeyStyle> getCycleGroup() {
		return AllBogeyStyles.getCycleGroup(cycleGroup);
	}

	public Set<BogeySizes.BogeySize> validSizes() {
		return sizes.keySet();
	}

	public AbstractBogeyBlock<?> getBlockForSize(BogeySizes.BogeySize size) {
		return sizes.get(size)
			.get();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public AbstractBogeyBlock<?> getNextBlock(BogeySizes.BogeySize currentSize) {
		return Stream.iterate(currentSize.nextBySize(), BogeySizes.BogeySize::nextBySize)
			.filter(sizes::containsKey)
			.findFirst()
			.map(this::getBlockForSize)
			.orElse((AbstractBogeyBlock) getBlockForSize(currentSize));
	}

	/** A new visual (a set of display parts) for a bogey of the given size, or null if the style has none. */
	public BogeyVisual createVisual(BogeySizes.BogeySize size) {
		Supplier<? extends BogeyVisual> factory = visuals.get(size);
		return factory == null ? null : factory.get();
	}

	public static class Builder {
		protected final Identifier id;
		protected final Identifier cycleGroup;
		protected final Map<BogeySizes.BogeySize, Supplier<? extends AbstractBogeyBlock<?>>> sizes = new HashMap<>();
		protected final Map<BogeySizes.BogeySize, Supplier<? extends BogeyVisual>> visuals = new HashMap<>();

		protected Component displayName = TrackNbt.translateDirect("bogey.style.invalid");
		protected Supplier<SoundEvent> soundEvent = () -> SoundEvents.MINECART_RIDING;
		protected ParticleOptions contactParticle = ParticleTypes.CRIT;
		protected ParticleOptions smokeParticle = ParticleTypes.POOF;
		protected CompoundTag defaultData = new CompoundTag();

		public Builder(Identifier id, Identifier cycleGroup) {
			this.id = id;
			this.cycleGroup = cycleGroup;
		}

		public Builder displayName(Component displayName) {
			this.displayName = displayName;
			return this;
		}

		public Builder soundEvent(Supplier<SoundEvent> soundEvent) {
			this.soundEvent = soundEvent;
			return this;
		}

		public Builder contactParticle(ParticleOptions contactParticle) {
			this.contactParticle = contactParticle;
			return this;
		}

		public Builder smokeParticle(ParticleOptions smokeParticle) {
			this.smokeParticle = smokeParticle;
			return this;
		}

		public Builder defaultData(CompoundTag defaultData) {
			this.defaultData = defaultData;
			return this;
		}

		public Builder size(BogeySizes.BogeySize size, Supplier<? extends AbstractBogeyBlock<?>> block,
			Supplier<? extends BogeyVisual> visual) {
			this.sizes.put(size, block);
			this.visuals.put(size, visual);
			return this;
		}

		public BogeyStyle build() {
			BogeyStyle entry = new BogeyStyle(id, cycleGroup, displayName, soundEvent, contactParticle, smokeParticle,
				defaultData, sizes, visuals);
			AllBogeyStyles.BOGEY_STYLES.put(id, entry);
			AllBogeyStyles.CYCLE_GROUPS.computeIfAbsent(cycleGroup, l -> new HashMap<>())
				.put(id, entry);
			return entry;
		}
	}
}

package dev.createpolymer.api.registry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Ported from Create's SimpleRegistry: a simple registry mapping between objects with identity semantics.
 * Provides simple registration functionality, as well as lazy providers.
 * This class is thread-safe, and may be safely used during parallel mod init.
 */
public interface SimpleRegistry<K, V> {
	/**
	 * Register an association between a key and a value.
	 * Direct registrations here always take priority over providers.
	 * @throws IllegalArgumentException if the object already has an associated value
	 */
	void register(K object, V value);

	/**
	 * Add a new provider to this registry. For information on providers, see {@link Provider}.
	 * @throws IllegalArgumentException if the provider has already been registered to this registry
	 */
	void registerProvider(Provider<K, V> provider);

	/**
	 * Invalidate the cached values provided by all providers, so they get re-computed on the next query.
	 */
	void invalidate();

	/**
	 * Query the value associated with the given object. May be null if no association is present.
	 */
	@Nullable
	V get(K object);

	/**
	 * Shortcut for {@link #get(Object)} that accepts a BlockState or FluidState.
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	default V get(StateHolder<K, ?> state) {
		Objects.requireNonNull(state, "state");
		if (state instanceof BlockState blockState)
			return get((K) blockState.getBlock());
		if (state instanceof FluidState fluidState)
			return get((K) fluidState.getType());
		throw new IllegalArgumentException("Unsupported state holder " + state.getClass());
	}

	static <K, V> SimpleRegistry<K, V> create() {
		return new Single<>();
	}

	/**
	 * A provider can provide values to the registry in a lazy fashion. When a key does not have an
	 * associated value, all providers will be queried in reverse-registration order (newest first).
	 * <p>
	 * The values returned by providers are cached so that repeated queries always return the same value.
	 * To invalidate the cache of a registry, call {@link SimpleRegistry#invalidate()}.
	 */
	@FunctionalInterface
	interface Provider<K, V> {
		@Nullable
		V get(K object);

		/**
		 * Called by the SimpleRegistry this provider is registered to after it's registered.
		 */
		default void onRegister(Runnable invalidate) {
		}

		/**
		 * Create a provider that will return the same value for all entries in a tag.
		 * The Provider will invalidate itself when tags are reloaded.
		 */
		static <K, V> Provider<K, V> forTag(TagKey<K> tag, Function<K, Holder<K>> holderGetter, V value) {
			return new Provider<>() {
				@Override
				public @Nullable V get(K object) {
					return holderGetter.apply(object).is(tag) ? value : null;
				}

				@Override
				public void onRegister(Runnable invalidate) {
					CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> invalidate.run());
				}
			};
		}

		/**
		 * Shortcut for {@link #forTag} when the registry's type is Block.
		 */
		@SuppressWarnings("deprecation")
		static <V> Provider<Block, V> forBlockTag(TagKey<Block> tag, V value) {
			return forTag(tag, Block::builtInRegistryHolder, value);
		}

		/**
		 * Shortcut for {@link #forTag} when the registry's type is Fluid.
		 */
		@SuppressWarnings("deprecation")
		static <V> Provider<Fluid, V> forFluidTag(TagKey<Fluid> tag, V value) {
			return forTag(tag, Fluid::builtInRegistryHolder, value);
		}
	}

	/**
	 * An extension of SimpleRegistry that handles multiple registrations per object.
	 */
	interface Multi<K, V> extends SimpleRegistry<K, List<V>> {
		void add(K object, V value);

		/**
		 * Never returns null, will return an empty list if no registrations are present
		 */
		@Override
		@NotNull
		List<V> get(K object);

		static <K, V> Multi<K, V> create() {
			return new MultiImpl<>();
		}
	}

	final class Single<K, V> implements SimpleRegistry<K, V> {
		private static final Object NULL_MARKER = new Object();

		private final Map<K, V> registrations = new IdentityHashMap<>();
		private final Map<K, Object> providedValues = new IdentityHashMap<>();
		private final List<Provider<K, V>> providers = new ArrayList<>();

		@Override
		public synchronized void register(K object, V value) {
			Objects.requireNonNull(object, "object");
			Objects.requireNonNull(value, "value");
			V existing = registrations.get(object);
			if (existing != null)
				throw new IllegalArgumentException("Tried to register duplicate values for object " + object + ": old="
					+ existing + ", new=" + value);
			registrations.put(object, value);
		}

		@Override
		public synchronized void registerProvider(Provider<K, V> provider) {
			Objects.requireNonNull(provider);
			if (providers.contains(provider))
				throw new IllegalArgumentException("Tried to register provider twice: " + provider);
			// add to start of list so it's queried first
			providers.add(0, provider);
			provider.onRegister(this::invalidate);
		}

		@Override
		@SuppressWarnings("unchecked")
		public synchronized @Nullable V get(K object) {
			Objects.requireNonNull(object, "object");
			if (registrations.containsKey(object))
				return registrations.get(object);
			Object cached = providedValues.get(object);
			if (cached != null)
				return cached == NULL_MARKER ? null : (V) cached;
			for (Provider<K, V> provider : providers) {
				V value = provider.get(object);
				if (value != null) {
					providedValues.put(object, value);
					return value;
				}
			}
			providedValues.put(object, NULL_MARKER);
			return null;
		}

		@Override
		public synchronized void invalidate() {
			providedValues.clear();
		}
	}

	final class MultiImpl<K, V> implements Multi<K, V> {
		private final Map<K, List<V>> registrations = new IdentityHashMap<>();
		private final List<Provider<K, List<V>>> providers = new ArrayList<>();

		@Override
		public synchronized void register(K object, List<V> value) {
			registrations.put(object, new ArrayList<>(value));
		}

		@Override
		public synchronized void add(K object, V value) {
			registrations.computeIfAbsent(object, $ -> new ArrayList<>()).add(value);
		}

		@Override
		public synchronized void registerProvider(Provider<K, List<V>> provider) {
			providers.add(0, provider);
			provider.onRegister(this::invalidate);
		}

		@Override
		public void invalidate() {
		}

		@Override
		public synchronized @NotNull List<V> get(K object) {
			List<V> total = new ArrayList<>(registrations.getOrDefault(object, List.of()));
			for (Provider<K, List<V>> provider : providers) {
				List<V> provided = provider.get(object);
				if (provided != null)
					total.addAll(provided);
			}
			return total;
		}
	}
}

package dev.createpolymer.api.registry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/**
 * A simple registry mapping between objects with identity semantics, like Create's SimpleRegistry.
 * Provides simple registration functionality, as well as lazy providers.
 * <p>
 * Direct registrations take priority over providers. When an object has no registration, providers are queried
 * newest first and their answer is cached until {@link #invalidate()}, which tag based providers need after a reload.
 */
public class SimpleRegistry<K, V> {
	private static final Object NULL_MARKER = new Object();

	private final Map<K, V> registrations = new IdentityHashMap<>();
	private final List<Provider<K, V>> providers = new ArrayList<>();
	private final Map<K, Object> providedValues = new IdentityHashMap<>();

	public static <K, V> SimpleRegistry<K, V> create() {
		return new SimpleRegistry<>();
	}

	/**
	 * Register an association between a key and a value.
	 * @throws IllegalArgumentException if the object already has an associated value
	 */
	public synchronized void register(K object, V value) {
		Objects.requireNonNull(object, "object");
		Objects.requireNonNull(value, "value");

		V existing = registrations.get(object);
		if (existing != null)
			throw new IllegalArgumentException(String.format(
				"Tried to register duplicate values for object %s (%s): old=%s, new=%s", object, object.getClass(),
				existing, value));

		registrations.put(object, value);
	}

	/** Providers are queried when a key has no registration, newest first. */
	public synchronized void registerProvider(Provider<K, V> provider) {
		Objects.requireNonNull(provider);
		if (providers.contains(provider))
			throw new IllegalArgumentException("Tried to register provider twice: " + provider);
		providers.add(0, provider);
		provider.onRegister(this::invalidate);
		invalidate();
	}

	/** Forget the values the providers gave, so they are asked again. Call after tags reload. */
	public synchronized void invalidate() {
		providedValues.clear();
	}

	@Nullable
	@SuppressWarnings("unchecked")
	public synchronized V get(K object) {
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

	/** Shortcut for {@link #get(Object)} with a block state, when the keys are blocks. */
	@Nullable
	@SuppressWarnings("unchecked")
	public V get(BlockState state) {
		return get((K) state.getBlock());
	}

	@FunctionalInterface
	public interface Provider<K, V> {
		@Nullable
		V get(K object);

		/** Called by the registry after this provider was added; useful to hook up cache invalidation. */
		default void onRegister(Runnable invalidate) {
		}

		/** A provider that returns the same value for all entries in a tag. */
		static <K, V> Provider<K, V> forTag(TagKey<K> tag, Function<K, Holder<K>> holderGetter, V value) {
			return object -> holderGetter.apply(object).is(tag) ? value : null;
		}

		@SuppressWarnings("deprecation")
		static <V> Provider<Block, V> forBlockTag(TagKey<Block> tag, V value) {
			return forTag(tag, Block::builtInRegistryHolder, value);
		}

		@SuppressWarnings("deprecation")
		static <V> Provider<Fluid, V> forFluidTag(TagKey<Fluid> tag, V value) {
			return forTag(tag, Fluid::builtInRegistryHolder, value);
		}
	}
}

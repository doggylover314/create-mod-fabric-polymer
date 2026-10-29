package dev.createpolymer.fluids;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Create's ICapabilityProvider, reduced to what the fluid network needs: a handler that is looked up when it is used
 * (there is no capability cache to invalidate on Fabric).
 */
public interface ICapabilityProvider<T> {
	@Nullable
	T getCapability();

	static <T> ICapabilityProvider<T> of(Supplier<T> supplier) {
		return supplier::get;
	}

	static <T> ICapabilityProvider<T> of(T cap) {
		return () -> cap;
	}
}

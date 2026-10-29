package dev.createpolymer.logistics.packager;

import dev.createpolymer.api.packager.unpacking.UnpackingHandler;
import dev.createpolymer.impl.unpacking.BasinUnpackingHandler;
import dev.createpolymer.impl.unpacking.CrafterUnpackingHandler;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.CrafterRegistry;

public class AllUnpackingHandlers {
	public static void registerDefaults() {
		UnpackingHandler.REGISTRY.register(ModBlocks.BASIN, BasinUnpackingHandler.INSTANCE);
		UnpackingHandler.REGISTRY.register(CrafterRegistry.MECHANICAL_CRAFTER, CrafterUnpackingHandler.INSTANCE);
		// The creative crate (VoidingUnpackingHandler) is not ported yet.
	}
}

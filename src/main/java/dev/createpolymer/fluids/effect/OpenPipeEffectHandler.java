package dev.createpolymer.fluids.effect;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.fluid.FluidStack;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;

/**
 * Behaviour for fluids spilling out of open pipes (Create's OpenPipeEffectHandler). Water extinguishes fire and
 * lava ignites entities; the potion, milk and tea handlers of Create are not ported with the fluids they belong to.
 */
@FunctionalInterface
public interface OpenPipeEffectHandler {
	Registry REGISTRY = new Registry();

	/**
	 * @param area the area to apply effects in
	 * @param fluid the fluid in the pipe. Do not modify, it will do nothing
	 */
	void apply(Level level, AABB area, FluidStack fluid);

	static void registerDefaults() {
		REGISTRY.registerForTag(FluidTags.WATER, new WaterEffectHandler());
		REGISTRY.registerForTag(FluidTags.LAVA, new LavaEffectHandler());
	}

	/** Maps fluids, or fluid tags, to handlers. Direct registrations win over tags. */
	class Registry {
		private final Map<Fluid, OpenPipeEffectHandler> byFluid = new IdentityHashMap<>();
		private final List<Map.Entry<TagKey<Fluid>, OpenPipeEffectHandler>> byTag = new ArrayList<>();

		public void register(Fluid fluid, OpenPipeEffectHandler handler) {
			byFluid.put(fluid, handler);
		}

		public void registerForTag(TagKey<Fluid> tag, OpenPipeEffectHandler handler) {
			byTag.add(Map.entry(tag, handler));
		}

		@Nullable
		public OpenPipeEffectHandler get(Fluid fluid) {
			OpenPipeEffectHandler handler = byFluid.get(fluid);
			if (handler != null)
				return handler;
			for (Map.Entry<TagKey<Fluid>, OpenPipeEffectHandler> entry : byTag)
				if (fluid.is(entry.getKey()))
					return entry.getValue();
			return null;
		}
	}
}

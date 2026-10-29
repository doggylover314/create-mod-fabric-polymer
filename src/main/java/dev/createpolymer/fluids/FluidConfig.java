package dev.createpolymer.fluids;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;

/** Create's CFluids config with its default values. */
public final class FluidConfig {
	private FluidConfig() {}

	/** The maximum distance a mechanical pump can push or pull liquids on either side [in Blocks]. */
	public static int mechanicalPumpRange = 16;
	/** The maximum distance a hose pulley can draw fluid blocks from [in Blocks]. */
	public static int hosePulleyRange = 128;
	/** The minimum amount of fluid blocks the hose pulley needs to find before deeming it an infinite source [-1 to disable]. */
	public static int hosePulleyBlockThreshold = 10000;
	/** Whether hose pulleys should continue filling up above-threshold sources. */
	public static boolean fillInfinite = false;
	/** Configure which fluids can be drained infinitely. */
	public static BottomlessFluidMode bottomlessFluidMode = BottomlessFluidMode.ALLOW_BY_TAG;
	/** Whether hose pulleys should be allowed to place fluid sources. */
	public static boolean fluidFillPlaceFluidSourceBlocks = true;
	/** Whether open-ended pipes should be allowed to place fluid sources. */
	public static boolean pipesPlaceFluidSourceBlocks = true;

	public static final TagKey<Fluid> BOTTOMLESS_ALLOW =
		TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(CreatePolymer.MOD_ID, "bottomless/allow"));
	public static final TagKey<Fluid> BOTTOMLESS_DENY =
		TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(CreatePolymer.MOD_ID, "bottomless/deny"));
}

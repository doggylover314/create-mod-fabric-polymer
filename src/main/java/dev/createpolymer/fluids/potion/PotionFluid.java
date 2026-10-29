package dev.createpolymer.fluids.potion;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.Codec;

import dev.createpolymer.fluids.VirtualFluid;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.registry.feature.FluidsRegistry;
import dev.createpolymer.util.Lang;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.alchemy.PotionContents;

public class PotionFluid extends VirtualFluid {

	public static PotionFluid createSource(Properties properties) {
		return new PotionFluid(properties, true);
	}

	public static PotionFluid createFlowing(Properties properties) {
		return new PotionFluid(properties, false);
	}

	public PotionFluid(Properties properties, boolean source) {
		super(properties, source);
	}

	public static FluidStack of(int amount, PotionContents potionContents, BottleType bottleType) {
		FluidStack fluidStack;
		fluidStack = new FluidStack(FluidsRegistry.POTION.getSource(), amount);
		addPotionToFluidStack(fluidStack, potionContents);
		fluidStack.set(FluidsRegistry.POTION_FLUID_BOTTLE_TYPE, bottleType);
		return fluidStack;
	}

	public static FluidStack addPotionToFluidStack(FluidStack fs, PotionContents potionContents) {
		if (potionContents == PotionContents.EMPTY) {
			fs.remove(DataComponents.POTION_CONTENTS);
			return fs;
		}
		fs.set(DataComponents.POTION_CONTENTS, potionContents);
		return fs;
	}

	public enum BottleType implements StringRepresentable {
		REGULAR, SPLASH, LINGERING;

		public static final Codec<BottleType> CODEC = StringRepresentable.fromEnum(BottleType::values);

		@Override
		public @NotNull String getSerializedName() {
			return Lang.asId(name());
		}
	}

}

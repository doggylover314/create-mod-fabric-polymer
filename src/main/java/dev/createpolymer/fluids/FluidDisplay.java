package dev.createpolymer.fluids;

import java.util.List;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.fluid.FluidHelper;
import dev.createpolymer.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import dev.createpolymer.registry.feature.FluidsRegistry;

/**
 * Item stacks for display entities that show a block of fluid. Players cannot see fluids drawn by a server, so a
 * fluid is shown as a cuboid with the fluid's texture: the item model {@code create:fluid_box_<name>} is a unit cube
 * that the display entity scales to the shape of the fluid (see {@link FluidBox}). Water, lava, potions, honey,
 * chocolate and tea have their own texture; every other fluid is shown with the water texture tinted by the fluid's
 * associated colour (Fabric's {@code FluidVariantAttributes#getAssociatedColor}, blue if it has none).
 */
public final class FluidDisplay {
	private FluidDisplay() {}

	/** Colour of vanilla water without a biome tint, also the default for fluids without a colour. */
	public static final int DEFAULT_COLOR = 0x3F76E4;

	public static ItemStack stack(FluidStack fluid) {
		ItemStack stack = new ItemStack(Items.PAPER);
		Fluid still = FluidHelper.convertToStill(fluid.getFluid());
		String name;
		if (still == Fluids.WATER)
			name = "water";
		else if (still == Fluids.LAVA)
			name = "lava";
		else if (still == FluidsRegistry.POTION) {
			name = "potion";
			stack.set(DataComponents.POTION_CONTENTS, fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY));
		} else if (still == FluidsRegistry.HONEY)
			name = "honey";
		else if (still == FluidsRegistry.CHOCOLATE)
			name = "chocolate";
		else if (still == FluidsRegistry.TEA)
			name = "tea";
		else {
			name = "generic";
			int color = FluidVariantAttributes.getAssociatedColor(fluid.toVariant());
			stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(),
				List.of(color == -1 ? DEFAULT_COLOR : color & 0xFFFFFF)));
		}
		stack.set(DataComponents.ITEM_MODEL, CreatePolymer.id("fluid_box_" + name));
		return stack;
	}
}

package dev.createpolymer.schematics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Small helpers that Create takes from Catnip (ComponentProcessors and BBHelper), kept next to the code that uses them.
 */
public final class SchematicHelper {
	private SchematicHelper() {}

	public static ItemStack withUnsafeComponentsDiscarded(ItemStack stack) {
		if (stack.getComponentsPatch()
			.isEmpty())
			return stack;
		ItemStack copy = stack.copy();
		stack.getComponents()
			.stream()
			.filter(SchematicHelper::isUnsafeItemComponent)
			.map(TypedDataComponent::type)
			.forEach(copy::remove);
		return copy;
	}

	public static boolean isUnsafeItemComponent(TypedDataComponent<?> component) {
		return isUnsafeItemComponent(component.type());
	}

	public static boolean isUnsafeItemComponent(DataComponentType<?> component) {
		if (component.equals(DataComponents.ENCHANTMENTS))
			return false;
		if (component.equals(DataComponents.POTION_CONTENTS))
			return false;
		if (component.equals(DataComponents.DAMAGE))
			return false;
		if (component.equals(DataComponents.CUSTOM_NAME))
			return false;
		return true;
	}

	public static BoundingBox encapsulate(BoundingBox bb, BlockPos pos) {
		return new BoundingBox(Math.min(bb.minX(), pos.getX()), Math.min(bb.minY(), pos.getY()),
			Math.min(bb.minZ(), pos.getZ()), Math.max(bb.maxX(), pos.getX()), Math.max(bb.maxY(), pos.getY()),
			Math.max(bb.maxZ(), pos.getZ()));
	}

	public static BoundingBox encapsulate(BoundingBox bb, BoundingBox bb2) {
		return new BoundingBox(Math.min(bb.minX(), bb2.minX()), Math.min(bb.minY(), bb2.minY()),
			Math.min(bb.minZ(), bb2.minZ()), Math.max(bb.maxX(), bb2.maxX()), Math.max(bb.maxY(), bb2.maxY()),
			Math.max(bb.maxZ(), bb2.maxZ()));
	}
}

package dev.createpolymer.logistics.item.filter.attribute;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface ItemAttributeType {
	@NotNull ItemAttribute createAttribute();

	List<ItemAttribute> getAllAttributes(ItemStack stack, Level level);

	MapCodec<? extends ItemAttribute> codec();
}

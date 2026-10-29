package dev.createpolymer.logistics.item.filter.attribute.attributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.logistics.item.filter.attribute.AllItemAttributeTypes;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttributeType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;

public record ColorAttribute(DyeColor color) implements ItemAttribute {
	public static final MapCodec<ColorAttribute> CODEC = DyeColor.CODEC
			.xmap(ColorAttribute::new, ColorAttribute::color)
			.fieldOf("value");

	private static Collection<DyeColor> findMatchingDyeColors(ItemStack stack) {
		DyeColor color = stack.get(DataComponents.DYE);
		if (color != null)
			return Collections.singletonList(color);

		Set<DyeColor> colors = new HashSet<>();
		Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
		if (fireworks != null) {
			for (FireworkExplosion explosion : fireworks.explosions()) {
				colors.addAll(getFireworkStarColors(explosion));
			}
		}
		FireworkExplosion star = stack.get(DataComponents.FIREWORK_EXPLOSION);
		if (star != null)
			colors.addAll(getFireworkStarColors(star));

		String path = BuiltInRegistries.ITEM.getKey(stack.getItem())
			.getPath();
		Arrays.stream(DyeColor.values())
			.filter(c -> path.startsWith(c.getName() + "_"))
			.forEach(colors::add);

		return colors;
	}

	private static Collection<DyeColor> getFireworkStarColors(FireworkExplosion explosion) {
		Set<DyeColor> colors = new HashSet<>();
		Arrays.stream(explosion.colors().toIntArray()).mapToObj(DyeColor::byFireworkColor).filter(c -> c != null).forEach(colors::add);
		Arrays.stream(explosion.fadeColors().toIntArray()).mapToObj(DyeColor::byFireworkColor).filter(c -> c != null).forEach(colors::add);
		return colors;
	}

	@Override
	public boolean appliesTo(ItemStack itemStack, Level level) {
		return findMatchingDyeColors(itemStack).stream().anyMatch(color::equals);
	}

	@Override
	public String getTranslationKey() {
		return "color";
	}

	@Override
	public Object[] getTranslationParameters() {
		return new Object[]{Component.translatable("color.minecraft." + color.getName())};
	}

	@Override
	public ItemAttributeType getType() {
		return AllItemAttributeTypes.HAS_COLOR;
	}

	public static class Type implements ItemAttributeType {
		@Override
		public @NotNull ItemAttribute createAttribute() {
			return new ColorAttribute(DyeColor.PURPLE);
		}

		@Override
		public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
			List<ItemAttribute> list = new ArrayList<>();

			for (DyeColor color : ColorAttribute.findMatchingDyeColors(stack)) {
				list.add(new ColorAttribute(color));
			}

			return list;
		}

		@Override
		public MapCodec<? extends ItemAttribute> codec() {
			return CODEC;
		}
	}
}

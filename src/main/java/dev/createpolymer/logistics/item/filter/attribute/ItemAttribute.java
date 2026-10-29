package dev.createpolymer.logistics.item.filter.attribute;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface ItemAttribute {
	Codec<ItemAttribute> CODEC = AllItemAttributeTypes.REGISTRY.byNameCodec()
		.dispatch(ItemAttribute::getType, ItemAttributeType::codec);

	static List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
		List<ItemAttribute> attributes = new ArrayList<>();
		for (ItemAttributeType type : AllItemAttributeTypes.REGISTRY) {
			attributes.addAll(type.getAllAttributes(stack, level));
		}
		return attributes;
	}

	boolean appliesTo(ItemStack stack, Level world);

	ItemAttributeType getType();

	default MutableComponent format(boolean inverted) {
		return Component.translatable("create.item_attributes." + getTranslationKey() + (inverted ? ".inverted" : ""),
			getTranslationParameters());
	}

	String getTranslationKey();

	default Object[] getTranslationParameters() {
		return new String[0];
	}

	record ItemAttributeEntry(ItemAttribute attribute, boolean inverted) {
		public static final Codec<ItemAttributeEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemAttribute.CODEC.fieldOf("attribute").forGetter(ItemAttributeEntry::attribute),
			Codec.BOOL.fieldOf("inverted").forGetter(ItemAttributeEntry::inverted)
		).apply(i, ItemAttributeEntry::new));
	}
}

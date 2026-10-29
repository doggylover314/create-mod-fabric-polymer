package dev.createpolymer.processing.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Ported from Create's ProcessingOutput (MIT, The Create Team). Same JSON shape: {id, count, components, chance}. */
public record ProcessingOutput(Item item, int count, DataComponentPatch components, float chance) {

	public static final Codec<ProcessingOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
		BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ProcessingOutput::item),
		ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(ProcessingOutput::count),
		DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ProcessingOutput::components),
		ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("chance", 1F).forGetter(ProcessingOutput::chance)
	).apply(i, ProcessingOutput::new));

	public ItemStack getStack() {
		return getStack(count);
	}

	private ItemStack getStack(int count) {
		ItemStack stack = new ItemStack(item, count);
		if (!components.isEmpty())
			stack.applyComponents(components);
		return stack;
	}

	public ItemStack rollOutput(RandomSource randomSource) {
		if (chance < 1F) {
			int count = this.count;
			for (int roll = 0; roll < this.count; roll++)
				if (randomSource.nextFloat() > chance)
					count--;
			if (count == 0)
				return ItemStack.EMPTY;
			return getStack(count);
		}
		return getStack();
	}
}

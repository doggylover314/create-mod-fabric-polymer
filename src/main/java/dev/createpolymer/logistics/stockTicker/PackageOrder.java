package dev.createpolymer.logistics.stockTicker;

import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.logistics.BigItemStack;

public record PackageOrder(List<BigItemStack> stacks) {
	public static final Codec<PackageOrder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.list(BigItemStack.CODEC).fieldOf("entries").forGetter(PackageOrder::stacks)
	).apply(instance, PackageOrder::new));

	public static PackageOrder empty() {
		return new PackageOrder(Collections.emptyList());
	}

	public boolean isEmpty() {
		return stacks.isEmpty();
	}
}

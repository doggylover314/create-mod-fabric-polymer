package dev.createpolymer.logistics.factoryBoard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import net.minecraft.core.BlockPos;

public record FactoryPanelPosition(BlockPos pos, PanelSlot slot) {
	public static final Codec<FactoryPanelPosition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BlockPos.CODEC.fieldOf("pos").forGetter(FactoryPanelPosition::pos),
		PanelSlot.CODEC.fieldOf("slot").forGetter(FactoryPanelPosition::slot)
	).apply(instance, FactoryPanelPosition::new));
}

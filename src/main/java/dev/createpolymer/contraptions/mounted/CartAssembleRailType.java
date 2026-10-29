package dev.createpolymer.contraptions.mounted;

import java.util.function.Supplier;

import dev.createpolymer.registry.feature.MinecartRegistry;
import dev.createpolymer.util.Lang;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum CartAssembleRailType implements StringRepresentable {

	REGULAR(() -> Blocks.RAIL),
	POWERED_RAIL(() -> Blocks.POWERED_RAIL),
	DETECTOR_RAIL(() -> Blocks.DETECTOR_RAIL),
	ACTIVATOR_RAIL(() -> Blocks.ACTIVATOR_RAIL),
	CONTROLLER_RAIL(() -> MinecartRegistry.CONTROLLER_RAIL)

	;

	private final Supplier<Block> railBlockSupplier;

	CartAssembleRailType(Supplier<Block> block) {
		this.railBlockSupplier = block;
	}

	public Block getBlock() {
		return railBlockSupplier.get();
	}

	public Item getItem() {
		return railBlockSupplier.get()
			.asItem();
	}

	public boolean matches(BlockState rail) {
		return rail.getBlock() == railBlockSupplier.get();
	}

	@Override
	public String getSerializedName() {
		return Lang.asId(name());
	}

}

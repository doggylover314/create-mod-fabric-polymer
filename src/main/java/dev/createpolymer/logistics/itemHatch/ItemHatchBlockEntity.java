package dev.createpolymer.logistics.itemHatch;

import java.util.List;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's ItemHatchBlockEntity (MIT, The Create Team). */
public class ItemHatchBlockEntity extends SmartBlockEntity implements Clearable {
	public FilteringBehaviour filtering;

	public ItemHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(filtering = new FilteringBehaviour(this, new HatchFilterSlot()));
	}

	@Override
	public void clearContent() {
		filtering.setFilter(ItemStack.EMPTY);
	}
}

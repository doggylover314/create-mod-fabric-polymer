package dev.createpolymer.logistics.depot;

import java.util.List;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.logistics.box.PackageEntity;
import dev.createpolymer.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class DepotBlockEntity extends SmartBlockEntity implements Clearable {
	public DepotBehaviour depotBehaviour;

	public DepotBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(ModBlockEntities.DEPOT, (be, side) -> be.depotBehaviour.itemHandler);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(depotBehaviour = new DepotBehaviour(this));
		depotBehaviour.addSubBehaviours(behaviours);
	}

	/**
	 * Entities that rest on top of the depot are taken in. Create does this from Block.updateEntityAfterFallOn,
	 * which every tick an entity stands on the block; Minecraft 26.3 only has Block.fallOn, so poll for the rest.
	 */
	@Override
	public void tick() {
		super.tick();
		// Packages have to be centred on the depot over several ticks (PackageEntity.centerPackage), so they are
		// looked at every tick, like Create's updateEntityAfterFallOn
		collectItemEntities(level.getGameTime() % 4 == 0);
	}

	private void collectItemEntities(boolean items) {
		AABB top = new AABB(worldPosition.getX(), worldPosition.getY() + 13 / 16d, worldPosition.getZ(),
			worldPosition.getX() + 1, worldPosition.getY() + 1, worldPosition.getZ() + 1);
		for (Entity entity : level.getEntitiesOfClass(Entity.class, top,
			e -> e instanceof PackageEntity || items && e instanceof ItemEntity))
			SharedDepotBlockMethods.onLanded(level, entity);
	}

	@Override
	public void clearContent() {
		depotBehaviour.clearContent();
	}

	public ItemStack getHeldItem() {
		return depotBehaviour.getHeldItemStack();
	}

	public void setHeldItem(ItemStack item) {
		TransportedItemStack newStack = new TransportedItemStack(item);
		if (depotBehaviour.heldItem != null)
			newStack.angle = depotBehaviour.heldItem.angle;
		depotBehaviour.setHeldItem(newStack);
	}
}

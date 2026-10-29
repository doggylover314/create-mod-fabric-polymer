package dev.createpolymer.legacy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Create's Item.onEntityItemUpdate, which vanilla 26.3 no longer calls. Items that implement this get their hook once
 * per server tick, for every item entity of theirs in a loaded level.
 */
public interface ItemEntityUpdate {

	boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity);

	/** Server tick: calls the hook of every item entity whose item implements {@link ItemEntityUpdate}. */
	static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			List<ItemEntity> items = new ArrayList<>();
			level.getEntities(EntityTypeTest.forClass(ItemEntity.class), e -> e.getItem().getItem() instanceof ItemEntityUpdate, items);
			for (ItemEntity entity : items) {
				ItemStack stack = entity.getItem();
				if (stack.getItem() instanceof ItemEntityUpdate update && entity.isAlive())
					update.onEntityItemUpdate(stack, entity);
			}
		}
	}
}

package dev.createpolymer.kinetics.saw;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Ported from Create's AbstractBlockBreakQueue (MIT, The Create Team). Blocks are broken without a player
 * (no tool damage, no break events), the way a machine breaks them.
 */
public abstract class AbstractBlockBreakQueue {
	protected Consumer<BlockPos> makeCallbackFor(Level world, float effectChance, ItemStack toDamage,
		@Nullable Player playerEntity, BiConsumer<BlockPos, ItemStack> drop) {
		return pos -> BlockBreakingKineticBlockEntity.destroyBlock(world, pos, effectChance,
			stack -> drop.accept(pos, stack));
	}

	public void destroyBlocks(Level world, @Nullable LivingEntity entity, BiConsumer<BlockPos, ItemStack> drop) {
		Player playerEntity = entity instanceof Player ? ((Player) entity) : null;
		ItemStack toDamage =
			playerEntity != null && !playerEntity.isCreative() ? playerEntity.getMainHandItem() : ItemStack.EMPTY;
		destroyBlocks(world, toDamage, playerEntity, drop);
	}

	public abstract void destroyBlocks(Level world, ItemStack toDamage, @Nullable Player playerEntity,
		BiConsumer<BlockPos, ItemStack> drop);
}

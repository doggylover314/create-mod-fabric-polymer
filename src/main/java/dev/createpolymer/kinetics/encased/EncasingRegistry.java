package dev.createpolymer.kinetics.encased;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Create's EncasingRegistry and EncasableBlock.tryEncase: which encased blocks a plain block turns into
 * when the matching casing is used on it. The andesite casing is handled by the shaft and cogwheel blocks
 * themselves; variants added here (brass) are handled by a use-block callback, so sneaking is the only
 * thing that stops it, as in Create.
 */
public final class EncasingRegistry {
	private static final Map<Block, List<Block>> ENCASED_VARIANTS = new IdentityHashMap<>();

	private EncasingRegistry() {}

	public static void addVariant(Block block, Block variant) {
		ENCASED_VARIANTS.computeIfAbsent(block, b -> new ArrayList<>()).add(variant);
	}

	public static List<Block> getVariants(Block block) {
		return ENCASED_VARIANTS.getOrDefault(block, List.of());
	}

	public static void registerEvents() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
			tryEncase(level, player, player.getItemInHand(hand), hit));
	}

	public static InteractionResult tryEncase(Level level, Player player, ItemStack heldItem, BlockHitResult hit) {
		if (heldItem.isEmpty() || player.isSpectator() || player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		for (Block block : getVariants(state.getBlock())) {
			if (!(block instanceof EncasedBlock encased))
				continue;
			if (encased.getCasing().asItem() != heldItem.getItem())
				continue;

			if (level.isClientSide())
				return InteractionResult.SUCCESS;

			KineticBlockEntity.switchToBlockState(level, pos, encased.encasedStateFor(state, level, pos));
			playEncaseSound(level, pos);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private static void playEncaseSound(Level level, BlockPos pos) {
		BlockState newState = level.getBlockState(pos);
		SoundType soundType = newState.getSoundType();
		level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
	}
}

package dev.createpolymer.item;

import dev.createpolymer.registry.ModBlocks;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Andesite alloy. Using it on a stripped log or stripped wood turns that block
 * into andesite casing, like Create's "item application" recipe.
 */
public class AndesiteAlloyItem extends SimplePolymerItem {
	private static final TagKey<Block> STRIPPED_LOGS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "stripped_logs"));
	private static final TagKey<Block> STRIPPED_WOODS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "stripped_woods"));

	public AndesiteAlloyItem(Properties settings) {
		super(settings, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		Player player = context.getPlayer();
		if (!(state.is(STRIPPED_LOGS) || state.is(STRIPPED_WOODS)))
			return super.useOn(context);
		if (player != null && !player.mayBuild())
			return InteractionResult.PASS;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		level.setBlockAndUpdate(pos, ModBlocks.ANDESITE_CASING.defaultBlockState());
		level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1, 1);
		level.levelEvent(2001, pos, Block.getId(state));
		if (player == null || !player.isCreative())
			context.getItemInHand().shrink(1);
		return InteractionResult.SUCCESS;
	}
}

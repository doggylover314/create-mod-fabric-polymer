package dev.createpolymer.equipment;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Ported from Create's TreeFertilizerItem (MIT, The Create Team). Create grows the tree in a simulated copy of the
 * world with unlimited room above the sapling and then merges the result into the real world. There is no simulated
 * level here, so the tree grows in place and fails like a regular bone meal does when it has no room.
 */
public class TreeFertilizerItem extends SimplePolymerItem {

	public TreeFertilizerItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		BlockState state = context.getLevel()
			.getBlockState(context.getClickedPos());
		Block block = state.getBlock();
		if (block instanceof BonemealableBlock bonemealableBlock && state.is(BlockTags.SAPLINGS)) {

			if (state.getOptionalValue(MangrovePropaguleBlock.HANGING)
				.orElse(false))
				return InteractionResult.PASS;

			if (!(context.getLevel() instanceof ServerLevel level))
				return InteractionResult.SUCCESS;

			BlockPos saplingPos = context.getClickedPos();
			bonemealableBlock.performBonemeal(level, level.getRandom(), saplingPos, withStage(state, 1), BonemealSource.INTERACTION);

			if (level.getBlockState(saplingPos)
				.is(BlockTags.SAPLINGS)) {
				// no room: the sapling is put back the way it was
				level.setBlock(saplingPos, state, Block.UPDATE_ALL);
				return InteractionResult.FAIL;
			}

			level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, saplingPos, 15);
			if (context.getPlayer() != null && !context.getPlayer()
				.isCreative())
				context.getItemInHand()
					.shrink(1);
			return InteractionResult.SUCCESS;

		}

		return super.useOn(context);
	}

	private BlockState withStage(BlockState original, int stage) {
		if (!original.hasProperty(BlockStateProperties.STAGE))
			return original;
		return original.setValue(BlockStateProperties.STAGE, stage);
	}

}

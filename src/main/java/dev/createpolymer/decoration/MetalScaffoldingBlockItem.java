package dev.createpolymer.decoration;

import org.jspecify.annotations.Nullable;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ScaffoldingBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerLevel;

public class MetalScaffoldingBlockItem extends ScaffoldingBlockItem implements PolymerItem {

	public MetalScaffoldingBlockItem(Block pBlock, Properties pProperties) {
		super(pBlock, pProperties);
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return Items.TRIAL_KEY;
	}

	@Override
	public boolean isPolymerBlockInteraction(BlockState state, ServerPlayer player, InteractionHand hand, ItemStack stack,
		ServerLevel world, BlockHitResult blockHitResult, InteractionResult actionResult) {
		return true;
	}

	@Override
	public boolean isIgnoringBlockInteractionPlaySoundExceptedEntity(BlockState state, ServerPlayer player,
		InteractionHand hand, ItemStack stack, ServerLevel world, BlockHitResult blockHitResult) {
		return true;
	}

	/**
	 * Unlike vanilla scaffolding this can start anywhere: placing on a scaffold continues the stack upwards, or along
	 * the horizontal direction you face when clicking its top.
	 */
	@Override
	public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext pContext) {
		BlockPos blockpos = pContext.getClickedPos();
		Level level = pContext.getLevel();
		BlockState blockstate = level.getBlockState(blockpos);
		Block block = this.getBlock();
		if (!blockstate.is(block))
			return pContext;

		Direction direction;
		if (pContext.isSecondaryUseActive()) {
			direction = pContext.isInside() ? pContext.getClickedFace()
				.getOpposite() : pContext.getClickedFace();
		} else {
			direction = pContext.getClickedFace() == Direction.UP ? pContext.getHorizontalDirection() : Direction.UP;
		}

		int i = 0;
		BlockPos.MutableBlockPos blockpos$mutableblockpos = blockpos.mutable()
			.move(direction);

		while (i < 7) {
			if (!level.isClientSide() && !level.isInWorldBounds(blockpos$mutableblockpos)) {
				Player player = pContext.getPlayer();
				int j = level.getMaxY();
				if (player instanceof ServerPlayer sp && blockpos$mutableblockpos.getY() > j)
					sp.sendBuildLimitMessage(true, j);
				break;
			}

			blockstate = level.getBlockState(blockpos$mutableblockpos);
			if (!blockstate.is(this.getBlock())) {
				if (blockstate.canBeReplaced(pContext)) {
					return BlockPlaceContext.at(pContext, blockpos$mutableblockpos, direction);
				}
				break;
			}

			blockpos$mutableblockpos.move(direction);
			if (direction.getAxis()
				.isHorizontal()) {
				++i;
			}
		}

		return null;
	}

}

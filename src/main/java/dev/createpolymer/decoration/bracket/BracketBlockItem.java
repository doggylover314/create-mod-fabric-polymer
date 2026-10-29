package dev.createpolymer.decoration.bracket;

import java.util.Optional;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BracketBlockItem extends BlockItem implements PolymerItem {

	public BracketBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return Items.TRIAL_KEY;
	}

	@Override
	public boolean isPolymerBlockInteraction(BlockState state, ServerPlayer player, InteractionHand hand,
		ItemStack stack, ServerLevel world, BlockHitResult blockHitResult, InteractionResult actionResult) {
		return true;
	}

	@Override
	public boolean isIgnoringBlockInteractionPlaySoundExceptedEntity(BlockState state, ServerPlayer player,
		InteractionHand hand, ItemStack stack, ServerLevel world, BlockHitResult blockHitResult) {
		return true;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = world.getBlockState(pos);
		BracketBlock bracketBlock = getBracketBlock();
		Player player = context.getPlayer();

		BracketedBlockEntityBehaviour behaviour =
			BlockEntityBehaviour.get(world, pos, BracketedBlockEntityBehaviour.TYPE);

		if (behaviour == null)
			return InteractionResult.FAIL;
		if (!behaviour.canHaveBracket())
			return InteractionResult.FAIL;
		if (world.isClientSide())
			return InteractionResult.SUCCESS;

		Optional<BlockState> suitableBracket = bracketBlock.getSuitableBracket(state, context.getClickedFace());
		if (!suitableBracket.isPresent() && player != null)
			suitableBracket =
				bracketBlock.getSuitableBracket(state, Direction.orderedByNearest(player)[0].getOpposite());
		if (!suitableBracket.isPresent())
			return InteractionResult.SUCCESS;

		BlockState bracket = behaviour.getBracket();
		BlockState newBracket = suitableBracket.get();

		if (bracket == newBracket)
			return InteractionResult.SUCCESS;

		world.playSound(null, pos, newBracket.getSoundType()
			.getPlaceSound(), SoundSource.BLOCKS, 0.75f, 1);
		behaviour.applyBracket(newBracket);

		if (player == null || !player.isCreative()) {
			context.getItemInHand()
				.shrink(1);
			if (bracket != null) {
				ItemStack returnedStack = new ItemStack(bracket.getBlock());
				if (player == null)
					Block.popResource(world, pos, returnedStack);
				else
					player.getInventory()
						.placeItemBackInInventory(returnedStack, Prediction.SERVER_ONLY);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private BracketBlock getBracketBlock() {
		return (BracketBlock) getBlock();
	}

}

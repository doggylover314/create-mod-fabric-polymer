package dev.createpolymer.redstone.link;

import java.util.Arrays;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.registry.ModItems;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-clicking one of the two slots of a redstone link with an item sets that item as the frequency (an empty
 * hand clears it). The slot is picked by where the click hit the link, as the value boxes of Create's links are.
 */
public class LinkHandler {

	private static boolean registered;

	public static void init() {
		if (registered)
			return;
		registered = true;
		UseBlockCallback.EVENT.register(LinkHandler::onBlockActivated);
	}

	public static InteractionResult onBlockActivated(Player player, Level world, InteractionHand hand, BlockHitResult ray) {
		if (world.isClientSide())
			return InteractionResult.PASS;
		BlockPos pos = ray.getBlockPos();

		if (player.isShiftKeyDown() || player.isSpectator())
			return InteractionResult.PASS;

		LinkBehaviour behaviour = BlockEntityBehaviour.get(world, pos, LinkBehaviour.TYPE);
		if (behaviour == null)
			return InteractionResult.PASS;

		ItemStack heldItem = player.getItemInHand(hand);
		if (heldItem.is(ModItems.WRENCH))
			return InteractionResult.PASS;

		boolean fakePlayer = player instanceof FakePlayer;
		boolean fakePlayerChoice = false;

		if (fakePlayer) {
			BlockState blockState = world.getBlockState(pos);
			Vec3 localHit = ray.getLocation()
				.subtract(Vec3.atLowerCornerOf(pos))
				.add(Vec3.atLowerCornerOf(ray.getDirection()
					.getUnitVec3i())
					.scale(.25f));
			fakePlayerChoice = localHit.distanceToSqr(behaviour.firstSlot.getLocalOffset(world, pos, blockState)) > localHit
				.distanceToSqr(behaviour.secondSlot.getLocalOffset(world, pos, blockState));
		}

		for (boolean first : Arrays.asList(false, true)) {
			if (behaviour.testHit(first, ray.getLocation()) || fakePlayer && fakePlayerChoice == first) {
				behaviour.setFrequency(first, heldItem);
				world.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25f, .1f);
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

}

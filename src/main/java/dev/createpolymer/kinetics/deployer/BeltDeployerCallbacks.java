package dev.createpolymer.kinetics.deployer;

import static dev.createpolymer.kinetics.deployer.DeployerBlock.FACING;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import dev.createpolymer.kinetics.belt.BeltHelper;
import dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.kinetics.deployer.DeployerBlockEntity.Mode;
import dev.createpolymer.kinetics.deployer.DeployerBlockEntity.State;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.processing.recipe.RecipeApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Ported from Create's BeltDeployerCallbacks (MIT, The Create Team). */
public class BeltDeployerCallbacks {

	private static final Random RANDOM = new Random();

	public static ProcessingResult onItemReceived(TransportedItemStack s, TransportedItemStackHandlerBehaviour i,
		DeployerBlockEntity blockEntity) {

		if (blockEntity.getSpeed() == 0)
			return ProcessingResult.PASS;
		if (blockEntity.mode == Mode.PUNCH)
			return ProcessingResult.PASS;
		BlockState blockState = blockEntity.getBlockState();
		if (!blockState.hasProperty(FACING) || blockState.getValue(FACING) != Direction.DOWN)
			return ProcessingResult.PASS;
		if (blockEntity.state != State.WAITING)
			return ProcessingResult.HOLD;
		if (blockEntity.redstoneLocked)
			return ProcessingResult.PASS;

		DeployerFakePlayer player = blockEntity.getPlayer();
		ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();

		if (held.isEmpty())
			return ProcessingResult.HOLD;
		if (blockEntity.getRecipe(s.stack) == null)
			return ProcessingResult.PASS;

		blockEntity.start();
		return ProcessingResult.HOLD;
	}

	public static ProcessingResult whenItemHeld(TransportedItemStack s, TransportedItemStackHandlerBehaviour i,
		DeployerBlockEntity blockEntity) {

		if (blockEntity.getSpeed() == 0)
			return ProcessingResult.PASS;
		BlockState blockState = blockEntity.getBlockState();
		if (!blockState.hasProperty(FACING) || blockState.getValue(FACING) != Direction.DOWN)
			return ProcessingResult.PASS;

		DeployerFakePlayer player = blockEntity.getPlayer();
		ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
		if (held.isEmpty())
			return ProcessingResult.HOLD;

		RecipeHolder<? extends Recipe<?>> recipeHolder = blockEntity.getRecipe(s.stack);
		if (recipeHolder == null)
			return ProcessingResult.PASS;

		if (blockEntity.state == State.RETRACTING && blockEntity.timer == 1000) {
			activate(s, i, blockEntity, recipeHolder.value());
			return ProcessingResult.HOLD;
		}

		if (blockEntity.state == State.WAITING) {
			if (blockEntity.redstoneLocked)
				return ProcessingResult.PASS;
			blockEntity.start();
		}

		return ProcessingResult.HOLD;
	}

	public static void activate(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler,
		DeployerBlockEntity blockEntity, Recipe<?> recipe) {

		// Deployer recipes are processing recipes; others are not supported on belts here
		if (!(recipe instanceof ProcessingRecipe<?> processingRecipe))
			return;

		List<TransportedItemStack> collect =
			RecipeApplier.applyRecipeOn(blockEntity.getLevel(), transported.stack.copyWithCount(1), processingRecipe, true)
				.stream()
				.map(stack -> {
					TransportedItemStack copy = transported.copy();
					boolean centered = BeltHelper.isItemUpright(stack);
					copy.stack = stack;
					copy.locked = true;
					copy.angle = centered ? 180 : RANDOM.nextInt(360);
					return copy;
				})
				.map(t -> {
					t.locked = false;
					return t;
				})
				.collect(Collectors.toList());

		transported.clearFanProcessingData();

		TransportedItemStack left = transported.copy();
		blockEntity.player.spawnedItemEffects = transported.stack.copy();
		left.stack.shrink(1);

		if (collect.isEmpty()) {
			handler.handleProcessingOnItem(transported, TransportedResult.convertTo(left));
		} else {
			handler.handleProcessingOnItem(transported, TransportedResult.convertToAndLeaveHeld(collect, left));
		}

		ItemStack heldItem = blockEntity.player.getMainHandItem();
		boolean keepHeld =
			recipe instanceof ItemApplicationRecipe && ((ItemApplicationRecipe) recipe).shouldKeepHeldItem();

		if (!keepHeld) {
			if (heldItem.getMaxDamage() > 0) {
				heldItem.hurtAndBreak(1, blockEntity.player, EquipmentSlot.MAINHAND);
			} else {
				Player player = blockEntity.player;
				ItemStackTemplate remainder = heldItem.getItem()
					.getCraftingRemainder();
				ItemStack leftover = remainder == null ? ItemStack.EMPTY : remainder.create();
				heldItem.shrink(1);
				if (heldItem.isEmpty()) {
					player.setItemInHand(InteractionHand.MAIN_HAND, leftover);
				} else {
					if (!player.getInventory()
						.add(leftover)) {
						player.drop(leftover, false, Prediction.SERVER_ONLY);
					}
				}
			}
		}

		BlockPos pos = blockEntity.getBlockPos();
		Level world = blockEntity.getLevel();
		if (heldItem.isEmpty())
			world.playSound(null, pos, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, .25f, 1);
		world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, .25f, .75f);

		blockEntity.spawnEffectParticles();
		blockEntity.notifyUpdate();
	}

}

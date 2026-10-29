package dev.createpolymer.fluids;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.fluid.FluidStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * The few bits of Create's FluidHelper and BlockHelper that the pipe network needs
 * (kept apart from the foundation so that the fluid containers can bring their own).
 */
public final class PipeHelper {
	private PipeHelper() {}

	public static boolean isWater(Fluid fluid) {
		return convertToStill(fluid) == Fluids.WATER;
	}

	public static boolean isLava(Fluid fluid) {
		return convertToStill(fluid) == Fluids.LAVA;
	}

	public static boolean isTag(Fluid fluid, TagKey<Fluid> tag) {
		return fluid.is(tag);
	}

	public static boolean isTag(FluidStack fluid, TagKey<Fluid> tag) {
		return isTag(fluid.getFluid(), tag);
	}

	public static SoundEvent getFillSound(FluidStack fluid) {
		return isTag(fluid, FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL;
	}

	public static SoundEvent getEmptySound(FluidStack fluid) {
		return isTag(fluid, FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
	}

	public static boolean hasBlockState(Fluid fluid) {
		BlockState blockState = fluid.defaultFluidState()
			.createLegacyBlock();
		return blockState != null && blockState != Blocks.AIR.defaultBlockState();
	}

	public static FluidStack copyStackWithAmount(FluidStack fs, int amount) {
		if (amount <= 0)
			return FluidStack.EMPTY;
		if (fs.isEmpty())
			return FluidStack.EMPTY;
		FluidStack copy = fs.copy();
		copy.setAmount(amount);
		return copy;
	}

	public static Fluid convertToFlowing(Fluid fluid) {
		if (fluid instanceof FlowingFluid flowing)
			return flowing.getFlowing();
		return fluid;
	}

	public static Fluid convertToStill(Fluid fluid) {
		if (fluid instanceof FlowingFluid flowing)
			return flowing.getSource();
		return fluid;
	}

	/** Create's BlockHelper.destroyBlock: break effect, drops, and the block is replaced by its fluid. */
	public static void destroyBlock(Level world, BlockPos pos, float effectChance) {
		destroyBlock(world, pos, effectChance, stack -> Block.popResource(world, pos, stack));
	}

	public static void destroyBlock(Level world, BlockPos pos, float effectChance, Consumer<ItemStack> droppedItemCallback) {
		destroyBlockAs(world, pos, null, ItemStack.EMPTY, effectChance, droppedItemCallback);
	}

	public static void destroyBlockAs(Level level, BlockPos pos, @Nullable Player player, ItemStack usedTool,
		float effectChance, Consumer<ItemStack> droppedItemCallback) {
		FluidState fluidState = level.getFluidState(pos);
		BlockState state = level.getBlockState(pos);

		if (level.getRandom().nextFloat() < effectChance)
			level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

		if (level instanceof ServerLevel serverLevel && serverLevel.getGameRules()
			.get(GameRules.BLOCK_DROPS) && (player == null || !player.isCreative())) {
			for (ItemStack itemStack : Block.getDrops(state, serverLevel, pos, blockEntity, player, usedTool))
				droppedItemCallback.accept(itemStack);
			state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, true);
		}

		level.setBlockAndUpdate(pos, fluidState.createLegacyBlock());
	}
}

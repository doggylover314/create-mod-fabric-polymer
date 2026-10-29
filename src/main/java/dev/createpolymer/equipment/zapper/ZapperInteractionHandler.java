package dev.createpolymer.equipment.zapper;

import java.util.Objects;

import dev.createpolymer.equipment.EquipmentSounds;
import dev.createpolymer.registry.feature.WandsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's ZapperInteractionHandler (MIT, The Create Team). Create cancels NeoForge's LeftClickBlock event;
 * here the handler is registered on Fabric's AttackBlockCallback, which fires on the server when a client starts
 * mining a block (the zapper never lets the block break, see {@link ZapperItem#canDestroyBlock}).
 */
public class ZapperInteractionHandler {

	/** Fabric's AttackBlockCallback: returns FAIL to cancel the attack, PASS to leave it alone. */
	public static InteractionResult leftClickingBlocksWithTheZapperSelectsTheBlock(Player player, Level level,
		InteractionHand hand, BlockPos pos, Direction direction) {
		if (level.isClientSide())
			return InteractionResult.PASS;
		ItemStack heldItem = player.getMainHandItem();
		if (heldItem.getItem() instanceof ZapperItem && trySelect(heldItem, player)) {
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	public static boolean trySelect(ItemStack stack, Player player) {
		if (player.isShiftKeyDown())
			return false;

		Vec3 start = player.position()
			.add(0, player.getEyeHeight(), 0);
		Vec3 range = player.getLookAngle()
			.scale(getRange(stack));
		BlockHitResult raytrace = player.level()
			.clip(new ClipContext(start, start.add(range), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		BlockPos pos = raytrace.getBlockPos();
		if (pos == null)
			return false;

		player.level().destroyBlockProgress(player.getId(), pos, -1);
		BlockState newState = player.level().getBlockState(pos);

		if (ZapperBlockHelper.getRequiredItem(newState)
			.isEmpty())
			return false;
		if (newState.hasBlockEntity() && !ZapperBlockHelper.isSafeNbt(newState))
			return false;
		if (newState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
			return false;
		if (newState.hasProperty(BlockStateProperties.ATTACHED))
			return false;
		if (newState.hasProperty(BlockStateProperties.HANGING))
			return false;
		if (newState.hasProperty(BlockStateProperties.BED_PART))
			return false;
		if (newState.hasProperty(BlockStateProperties.STAIRS_SHAPE))
			newState = newState.setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
		if (newState.hasProperty(BlockStateProperties.PERSISTENT))
			newState = newState.setValue(BlockStateProperties.PERSISTENT, true);
		if (newState.hasProperty(BlockStateProperties.WATERLOGGED))
			newState = newState.setValue(BlockStateProperties.WATERLOGGED, false);

		CompoundTag data = null;
		BlockEntity blockEntity = player.level().getBlockEntity(pos);
		if (blockEntity != null) {
			data = blockEntity.saveWithFullMetadata(player.registryAccess());
			data.remove("x");
			data.remove("y");
			data.remove("z");
			data.remove("id");
		}

		if (stack.has(WandsRegistry.SHAPER_BLOCK_USED) && stack.get(WandsRegistry.SHAPER_BLOCK_USED) == newState && Objects.equals(data, stack.get(WandsRegistry.SHAPER_BLOCK_DATA))) {
			return false;
		}

		stack.set(WandsRegistry.SHAPER_BLOCK_USED, newState);
		if (data == null)
			stack.remove(WandsRegistry.SHAPER_BLOCK_DATA);
		else
			stack.set(WandsRegistry.SHAPER_BLOCK_DATA, data);

		EquipmentSounds.confirm(player.level(), player.blockPosition(), 1, 1);
		return true;
	}

	public static int getRange(ItemStack stack) {
		if (stack.getItem() instanceof ZapperItem)
			return ((ZapperItem) stack.getItem()).getZappingRange(stack);
		return 0;
	}
}

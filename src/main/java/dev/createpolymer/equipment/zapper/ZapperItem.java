package dev.createpolymer.equipment.zapper;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.equipment.EquipmentSounds;
import dev.createpolymer.foundation.block.BlockHelper;
import dev.createpolymer.registry.feature.WandsRegistry;
import dev.createpolymer.schematics.NBTProcessors;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's ZapperItem (MIT, The Create Team). The configuration screen is a server side GUI, the beam is
 * sent as particles (see {@link ZapperBeamEffect}); the arm pose and the re-equip animation are client side in Create
 * and are not ported. The item shows as a vanilla item with Create's model.
 */
public abstract class ZapperItem extends SimplePolymerItem {

	public ZapperItem(Properties properties) {
		super(properties.stacksTo(1), Items.TRIAL_KEY, true);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
		Consumer<Component> builder, TooltipFlag flag) {
		if (stack.has(WandsRegistry.SHAPER_BLOCK_USED)) {
			Component usedBlock = stack.get(WandsRegistry.SHAPER_BLOCK_USED)
				.getBlock()
				.getName()
				.withStyle(ChatFormatting.GRAY);
			builder.accept(Component.translatable("create.terrainzapper.usingBlock", usedBlock)
				.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	public boolean isZapper(ItemStack newStack) {
		return newStack.getItem() instanceof ZapperItem;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		// Shift -> open GUI
		if (context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown()) {
			if (context.getPlayer() instanceof ServerPlayer player) {
				openHandgunGUI(player, context.getItemInHand(), context.getHand());
				player.getCooldowns()
					.addCooldown(context.getItemInHand(), 10);
			}
			return InteractionResult.SUCCESS;
		}
		return super.useOn(context);
	}

	@Override
	public InteractionResult use(Level world, Player player, InteractionHand hand) {
		ItemStack item = player.getItemInHand(hand);
		boolean mainHand = hand == InteractionHand.MAIN_HAND;

		// Shift -> Open GUI
		if (player.isShiftKeyDown()) {
			if (player instanceof ServerPlayer serverPlayer) {
				openHandgunGUI(serverPlayer, item, hand);
				player.getCooldowns()
					.addCooldown(item, 10);
			}
			return InteractionResult.SUCCESS;
		}

		if (ShootableGadgetItemMethods.shouldSwap(player, item, hand, this::isZapper))
			return InteractionResult.FAIL;

		if (!(world instanceof ServerLevel level))
			return InteractionResult.SUCCESS;

		// Check if can be used
		Component msg = validateUsage(item);
		if (msg != null) {
			EquipmentSounds.deny(world, player.blockPosition(), 1, 1);
			player.sendOverlayMessage(msg.copy()
				.withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}

		BlockState stateToUse = Blocks.AIR.defaultBlockState();
		if (item.has(WandsRegistry.SHAPER_BLOCK_USED))
			stateToUse = item.get(WandsRegistry.SHAPER_BLOCK_USED);
		stateToUse = BlockHelper.setZeroAge(stateToUse);
		CompoundTag data = null;
		if (BlockHelper.isSafeNbt(stateToUse) && item.has(WandsRegistry.SHAPER_BLOCK_DATA)) {
			data = item.get(WandsRegistry.SHAPER_BLOCK_DATA);
		}

		// Raytrace - Find the target
		Vec3 start = player.position()
			.add(0, player.getEyeHeight(), 0);
		Vec3 range = player.getLookAngle()
			.scale(getZappingRange(item));
		BlockHitResult raytrace =
			world.clip(new ClipContext(start, start.add(range), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		BlockPos pos = raytrace.getBlockPos();
		BlockState stateReplaced = world.getBlockState(pos);

		// No target
		if (pos == null || stateReplaced.getBlock() == Blocks.AIR) {
			ShootableGadgetItemMethods.applyCooldown(player, item, hand, this::isZapper, getCooldownDelay(item));
			return InteractionResult.SUCCESS;
		}

		// Find exact position of gun barrel for VFX
		Vec3 barrelPos = ShootableGadgetItemMethods.getGunBarrelVec(player, mainHand, new Vec3(.35f, -0.1f, 1));

		// Server side
		if (activate(world, player, item, stateToUse, raytrace, data)) {
			ShootableGadgetItemMethods.applyCooldown(player, item, hand, this::isZapper, getCooldownDelay(item));
			ZapperBeamEffect.send(level, barrelPos, raytrace.getLocation(), hand);
		}

		return InteractionResult.SUCCESS;
	}

	public @Nullable Component validateUsage(ItemStack item) {
		if (!canActivateWithoutSelectedBlock(item) && !item.has(WandsRegistry.SHAPER_BLOCK_USED))
			return Component.translatable("create.terrainzapper.leftClickToSet");
		return null;
	}

	protected abstract boolean activate(Level world, Player player, ItemStack item, BlockState stateToUse,
		BlockHitResult raytrace, CompoundTag data);

	/** Create's openHandgunGUI: the configuration screen, here a server side GUI */
	protected abstract void openHandgunGUI(ServerPlayer player, ItemStack item, InteractionHand hand);

	protected abstract int getCooldownDelay(ItemStack item);

	protected abstract int getZappingRange(ItemStack stack);

	protected boolean canActivateWithoutSelectedBlock(ItemStack stack) {
		return false;
	}

	@Override
	public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
		return false;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	public static void setBlockEntityData(Level world, BlockPos pos, BlockState state, CompoundTag data, Player player) {
		if (data != null && BlockHelper.isSafeNbt(state)) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity != null) {
				data = NBTProcessors.process(state, blockEntity, data.copy(), !player.isCreative(), world.registryAccess());
				if (data == null)
					return;
				data.putInt("x", pos.getX());
				data.putInt("y", pos.getY());
				data.putInt("z", pos.getZ());
				blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), data));
				blockEntity.setChanged();
			}
		}
	}

}

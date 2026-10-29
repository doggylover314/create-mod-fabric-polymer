package dev.createpolymer.trains.schedule;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.foundation.recipe.ItemCopyingRecipe.SupportsItemCopying;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.registry.feature.SchedulesRegistry;
import dev.createpolymer.trains.entity.CarriageContraption;
import dev.createpolymer.trains.entity.CarriageContraptionEntity;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.schedule.destination.DestinationInstruction;
import dev.createpolymer.util.Couple;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Create's train schedule. Players see it as a vanilla item with Create's item model; the schedule lives in a data
 * component that is never sent to clients, and is edited in a chest GUI ({@link ScheduleGui}) instead of Create's
 * screen.
 */
public class ScheduleItem extends SimplePolymerItem implements SupportsItemCopying {

	public ScheduleItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() == null)
			return InteractionResult.PASS;
		return use(context.getLevel(), context.getPlayer(), context.getHand());
	}

	@Override
	public InteractionResult use(Level world, Player player, InteractionHand hand) {
		ItemStack heldItem = player.getItemInHand(hand);

		if (!player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
			if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer)
				ScheduleGui.open(serverPlayer, heldItem);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	public InteractionResult handScheduleTo(ItemStack pStack, Player pPlayer, LivingEntity pInteractionTarget,
		InteractionHand pUsedHand) {
		InteractionResult pass = InteractionResult.PASS;

		Schedule schedule = getSchedule(pPlayer.registryAccess(), pStack);
		if (schedule == null)
			return pass;
		if (pInteractionTarget == null)
			return pass;
		Entity rootVehicle = pInteractionTarget.getRootVehicle();
		if (!(rootVehicle instanceof CarriageContraptionEntity entity))
			return pass;
		if (pPlayer.level()
			.isClientSide())
			return InteractionResult.SUCCESS;

		Contraption contraption = entity.getContraption();
		if (contraption instanceof CarriageContraption cc) {

			Train train = entity.getCarriage().train;
			if (train == null)
				return InteractionResult.SUCCESS;

			Integer seatIndex = contraption.getSeatMapping()
				.get(pInteractionTarget.getUUID());
			if (seatIndex == null)
				return InteractionResult.SUCCESS;
			BlockPos seatPos = contraption.getSeats()
				.get(seatIndex);
			Couple<Boolean> directions = cc.conductorSeats.get(seatPos);
			if (directions == null) {
				pPlayer.sendOverlayMessage(ScheduleLang.translate("schedule.non_controlling_seat"));
				ScheduleSounds.deny(pPlayer.level(), pPlayer);
				return InteractionResult.SUCCESS;
			}

			if (train.runtime.getSchedule() != null) {
				ScheduleSounds.deny(pPlayer.level(), pPlayer);
				pPlayer.sendOverlayMessage(ScheduleLang.translate("schedule.remove_with_empty_hand"));
				return InteractionResult.SUCCESS;
			}

			if (schedule.entries.isEmpty()) {
				ScheduleSounds.deny(pPlayer.level(), pPlayer);
				pPlayer.sendOverlayMessage(ScheduleLang.translate("schedule.no_stops"));
				return InteractionResult.SUCCESS;
			}

			train.runtime.setSchedule(schedule, false);
			ScheduleSounds.confirm(pPlayer.level(), pPlayer);
			pPlayer.sendOverlayMessage(ScheduleLang.translate("schedule.applied_to_train")
				.withStyle(ChatFormatting.GREEN));
			pStack.shrink(1);
			pPlayer.setItemInHand(pUsedHand, pStack.isEmpty() ? ItemStack.EMPTY : pStack);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
		Consumer<Component> builder, TooltipFlag flag) {
		HolderLookup.Provider registries = context.registries();
		if (registries == null)
			return;
		Schedule schedule = getSchedule(registries, stack);
		if (schedule == null || schedule.entries.isEmpty())
			return;

		MutableComponent caret = Component.literal("> ")
			.withStyle(ChatFormatting.GRAY);
		MutableComponent arrow = Component.literal("-> ")
			.withStyle(ChatFormatting.GRAY);

		List<ScheduleEntry> entries = schedule.entries;
		for (int i = 0; i < entries.size(); i++) {
			boolean current = i == schedule.savedProgress && schedule.entries.size() > 1;
			ScheduleEntry entry = entries.get(i);
			if (!(entry.instruction instanceof DestinationInstruction destination))
				continue;
			ChatFormatting format = current ? ChatFormatting.YELLOW : ChatFormatting.GOLD;
			MutableComponent prefix = current ? arrow : caret;
			builder.accept(prefix.copy()
				.append(Component.literal(destination.getFilter())
					.withStyle(format)));
		}
	}

	@Nullable
	public static Schedule getSchedule(HolderLookup.Provider registries, ItemStack pStack) {
		if (!pStack.has(SchedulesRegistry.TRAIN_SCHEDULE))
			return null;
		return Schedule.fromTag(registries, pStack.get(SchedulesRegistry.TRAIN_SCHEDULE));
	}

	@Override
	public DataComponentType<?> getComponentType() {
		return SchedulesRegistry.TRAIN_SCHEDULE;
	}

}

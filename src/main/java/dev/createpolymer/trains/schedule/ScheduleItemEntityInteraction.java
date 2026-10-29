package dev.createpolymer.trains.schedule;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.registry.feature.SchedulesRegistry;
import dev.createpolymer.trains.entity.CarriageContraption;
import dev.createpolymer.trains.entity.CarriageContraptionEntity;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.util.Couple;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Right-clicking a conductor (a mob sitting on a seat in front of the controls of a train) with a schedule hands the
 * schedule over; right-clicking it with an empty hand takes the schedule back, or lets a train continue that stopped
 * at the end of its schedule or because the player drove it by hand. Create listens for the interaction event of
 * the entity, Fabric has the same event.
 */
public class ScheduleItemEntityInteraction {

	public static void init() {
		UseEntityCallback.EVENT.register(ScheduleItemEntityInteraction::interactWithConductor);
	}

	public static InteractionResult interactWithConductor(Player player, Level level, InteractionHand hand,
		Entity entity, @Nullable EntityHitResult hitResult) {
		if (player == null || entity == null)
			return InteractionResult.PASS;
		if (player.isSpectator())
			return InteractionResult.PASS;

		Entity rootVehicle = entity.getRootVehicle();
		if (!(rootVehicle instanceof CarriageContraptionEntity cce))
			return InteractionResult.PASS;
		if (!(entity instanceof LivingEntity living))
			return InteractionResult.PASS;
		if (player.getCooldowns()
			.isOnCooldown(new ItemStack(SchedulesRegistry.SCHEDULE)))
			return InteractionResult.PASS;

		ItemStack itemStack = player.getItemInHand(hand);
		if (itemStack.getItem() instanceof ScheduleItem si) {
			InteractionResult result = si.handScheduleTo(itemStack, player, living, hand);
			if (result.consumesAction()) {
				player.getCooldowns()
					.addCooldown(new ItemStack(SchedulesRegistry.SCHEDULE), 5);
				return result;
			}
		}

		if (hand == InteractionHand.OFF_HAND)
			return InteractionResult.PASS;

		Contraption contraption = cce.getContraption();
		if (!(contraption instanceof CarriageContraption cc))
			return InteractionResult.PASS;

		Train train = cce.getCarriage().train;
		if (train == null)
			return InteractionResult.PASS;
		if (train.runtime.getSchedule() == null)
			return InteractionResult.PASS;

		Integer seatIndex = contraption.getSeatMapping()
			.get(entity.getUUID());
		if (seatIndex == null)
			return InteractionResult.PASS;
		BlockPos seatPos = contraption.getSeats()
			.get(seatIndex);
		Couple<Boolean> directions = cc.conductorSeats.get(seatPos);
		if (directions == null)
			return InteractionResult.PASS;

		boolean onServer = !level.isClientSide();

		if (train.runtime.paused && !train.runtime.completed) {
			if (onServer) {
				train.runtime.paused = false;
				ScheduleSounds.confirm(player.level(), player);
				player.sendOverlayMessage(ScheduleLang.translate("schedule.continued"));
			}

			player.getCooldowns()
				.addCooldown(new ItemStack(SchedulesRegistry.SCHEDULE), 5);
			return InteractionResult.SUCCESS;
		}

		ItemStack itemInHand = player.getItemInHand(hand);
		if (!itemInHand.isEmpty()) {
			if (onServer) {
				ScheduleSounds.deny(player.level(), player);
				player.sendOverlayMessage(ScheduleLang.translate("schedule.remove_with_empty_hand"));
			}
			return InteractionResult.SUCCESS;
		}

		if (onServer) {
			ScheduleSounds.itemPickup(player);
			player.sendOverlayMessage(ScheduleLang.translate(
				train.runtime.isAutoSchedule ? "schedule.auto_removed_from_train" : "schedule.removed_from_train"));

			player.getInventory()
				.placeItemBackInInventory(train.runtime.returnSchedule(player.registryAccess()), Prediction.SERVER_ONLY);
		}

		player.getCooldowns()
			.addCooldown(new ItemStack(SchedulesRegistry.SCHEDULE), 5);
		return InteractionResult.SUCCESS;
	}

}

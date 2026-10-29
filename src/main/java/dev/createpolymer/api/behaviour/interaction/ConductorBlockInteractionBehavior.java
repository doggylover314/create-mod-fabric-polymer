package dev.createpolymer.api.behaviour.interaction;

import java.util.function.Consumer;

import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.registry.feature.SchedulesRegistry;
import dev.createpolymer.trains.entity.CarriageContraption;
import dev.createpolymer.trains.entity.CarriageContraptionEntity;
import dev.createpolymer.trains.entity.Train;
import dev.createpolymer.trains.schedule.Schedule;
import dev.createpolymer.trains.schedule.ScheduleItem;
import dev.createpolymer.trains.schedule.ScheduleLang;
import dev.createpolymer.trains.schedule.ScheduleSounds;
import dev.createpolymer.util.Iterate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Partial interaction behavior implementation that allows blocks to act as conductors on trains, like Blaze Burners.
 */
public abstract class ConductorBlockInteractionBehavior extends MovingInteractionBehaviour {
	/**
	 * Check if the given state is capable of being a conductor.
	 */
	public abstract boolean isValidConductor(BlockState state);

	/**
	 * Called when the conductor's schedule has changed.
	 * @param hasSchedule true if the schedule was set, false if it was removed
	 * @param blockStateSetter a consumer that will change the BlockState of this conductor on the contraption
	 */
	protected void onScheduleUpdate(boolean hasSchedule, BlockState currentBlockState, Consumer<BlockState> blockStateSetter) {
	}

	@Override
	public final boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
		AbstractContraptionEntity contraptionEntity) {
		ItemStack itemInHand = player.getItemInHand(activeHand);

		if (!(contraptionEntity instanceof CarriageContraptionEntity carriageEntity))
			return false;
		if (activeHand == InteractionHand.OFF_HAND)
			return false;
		Contraption contraption = carriageEntity.getContraption();
		if (!(contraption instanceof CarriageContraption carriageContraption))
			return false;

		StructureBlockInfo info = carriageContraption.getBlocks()
			.get(localPos);
		if (info == null || !this.isValidConductor(info.state()))
			return false;

		Direction assemblyDirection = carriageContraption.getAssemblyDirection();
		for (Direction direction : Iterate.directionsInAxis(assemblyDirection.getAxis())) {
			if (!carriageContraption.inControl(localPos, direction))
				continue;

			Train train = carriageEntity.getCarriage().train;
			if (train == null)
				return false;
			if (player.level()
				.isClientSide())
				return true;

			if (train.runtime.getSchedule() != null) {
				if (train.runtime.paused && !train.runtime.completed) {
					train.runtime.paused = false;
					ScheduleSounds.confirm(player.level(), player);
					player.sendOverlayMessage(ScheduleLang.translate("schedule.continued"));
					return true;
				}

				if (!itemInHand.isEmpty()) {
					ScheduleSounds.deny(player.level(), player);
					player.sendOverlayMessage(ScheduleLang.translate("schedule.remove_with_empty_hand"));
					return true;
				}

				ScheduleSounds.itemPickup(player);
				player.sendOverlayMessage(ScheduleLang.translate(
					train.runtime.isAutoSchedule ? "schedule.auto_removed_from_train" : "schedule.removed_from_train"));
				player.setItemInHand(activeHand, train.runtime.returnSchedule(player.registryAccess()));
				this.onScheduleUpdate(false, info.state(), newBlockState -> setBlockState(localPos, contraptionEntity, newBlockState));
				return true;
			}

			if (!itemInHand.is(SchedulesRegistry.SCHEDULE))
				return true;

			Schedule schedule = ScheduleItem.getSchedule(player.registryAccess(), itemInHand);
			if (schedule == null)
				return false;

			if (schedule.entries.isEmpty()) {
				ScheduleSounds.deny(player.level(), player);
				player.sendOverlayMessage(ScheduleLang.translate("schedule.no_stops"));
				return true;
			}
			this.onScheduleUpdate(true, info.state(), newBlockState -> setBlockState(localPos, contraptionEntity, newBlockState));
			train.runtime.setSchedule(schedule, false);
			ScheduleSounds.confirm(player.level(), player);
			player.sendOverlayMessage(ScheduleLang.translate("schedule.applied_to_train")
				.withStyle(ChatFormatting.GREEN));
			itemInHand.shrink(1);
			player.setItemInHand(activeHand, itemInHand.isEmpty() ? ItemStack.EMPTY : itemInHand);
			return true;
		}

		player.sendOverlayMessage(ScheduleLang.translate("schedule.non_controlling_seat"));
		ScheduleSounds.deny(player.level(), player);
		return true;
	}

	private void setBlockState(BlockPos localPos, AbstractContraptionEntity contraption, BlockState newState) {
		StructureBlockInfo info = contraption.getContraption().getBlocks().get(localPos);
		if (info != null) {
			setContraptionBlockData(contraption, localPos, new StructureBlockInfo(info.pos(), newState, info.nbt()));
		}
	}

	/**
	 * Implementation used for Blaze Burners. May be reused by addons if applicable.
	 */
	public static class BlazeBurner extends ConductorBlockInteractionBehavior {
		@Override
		public boolean isValidConductor(BlockState state) {
			return state.getValue(BlazeBurnerBlock.HEAT_LEVEL) != BlazeBurnerBlock.HeatLevel.NONE;
		}
	}
}

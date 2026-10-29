package dev.createpolymer.contraptions.actors.trainControls;

import java.util.UUID;

import com.google.common.base.Objects;

import dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public class ControlsInteractionBehaviour extends MovingInteractionBehaviour {

	@Override
	public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
		AbstractContraptionEntity contraptionEntity) {
		if (player.getItemInHand(activeHand)
			.is(ModItems.WRENCH))
			return false;

		UUID currentlyControlling = contraptionEntity.getControllingPlayer()
			.orElse(null);

		if (currentlyControlling != null) {
			contraptionEntity.stopControlling(localPos);
			if (Objects.equal(currentlyControlling, player.getUUID()))
				return true;
		}

		if (!contraptionEntity.startControlling(localPos, player))
			return false;

		contraptionEntity.setControllingPlayer(player.getUUID());
		if (player instanceof ServerPlayer sp)
			ControlsServerHandler.startControlling(contraptionEntity, localPos, sp);
		return true;
	}

}

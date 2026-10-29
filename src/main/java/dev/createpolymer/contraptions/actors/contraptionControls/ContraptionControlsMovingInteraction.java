package dev.createpolymer.contraptions.actors.contraptionControls;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.tuple.MutablePair;

import dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.Contraption;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's ContraptionControlsMovingInteraction (MIT, The Create Team). Elevator and train carriage
 * handling is not ported.
 */
public class ContraptionControlsMovingInteraction extends MovingInteractionBehaviour {
	@Override
	public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
		AbstractContraptionEntity contraptionEntity) {
		Contraption contraption = contraptionEntity.getContraption();

		MutablePair<StructureBlockInfo, MovementContext> actor = contraption.getActorAt(localPos);
		if (actor == null)
			return false;
		MovementContext ctx = actor.right;
		if (ctx == null)
			return false;
		if (contraptionEntity.level().isClientSide())
			return true;

		ItemStack filter = ContraptionControlsMovement.getFilter(ctx);
		if (filter == null)
			return false;
		boolean disable = true;
		boolean invert = false;

		List<ItemStack> disabledActors = contraption.getDisabledActors();
		for (Iterator<ItemStack> iterator = disabledActors.iterator(); iterator.hasNext();) {
			ItemStack presentFilter = iterator.next();
			boolean sameFilter = ContraptionControlsMovement.isSameFilter(presentFilter, filter);
			if (presentFilter.isEmpty()) {
				iterator.remove();
				disable = false;
				if (!sameFilter)
					invert = true;
				continue;
			}
			if (!sameFilter)
				continue;
			iterator.remove();
			disable = false;
			break;
		}

		if (invert) {
			for (MutablePair<StructureBlockInfo, MovementContext> pair : contraption.getActors()) {
				MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(pair.left.state());
				if (behaviour == null)
					continue;
				ItemStack behaviourStack = behaviour.canBeDisabledVia(pair.right);
				if (behaviourStack == null)
					continue;
				if (ContraptionControlsMovement.isSameFilter(behaviourStack, filter))
					continue;
				if (contraption.isActorTypeDisabled(behaviourStack))
					continue;
				disabledActors.add(behaviourStack);
			}
		}

		if (filter.isEmpty())
			disabledActors.clear();
		if (disable)
			disabledActors.add(filter);

		contraption.setActorsActive(filter, !disable);
		ContraptionControlsBlockEntity.sendStatus(player, filter, !disable);

		ContraptionControlsBlockEntity.playClick(player.level(),
			BlockPos.containing(contraptionEntity.toGlobalVector(Vec3.atCenterOf(localPos), 1)), disable ? 0.8f : 1.5f);

		return true;
	}

}

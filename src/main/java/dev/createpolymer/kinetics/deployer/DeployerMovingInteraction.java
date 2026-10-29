package dev.createpolymer.kinetics.deployer;

import org.apache.commons.lang3.tuple.MutablePair;

import dev.createpolymer.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.createpolymer.contraptions.AbstractContraptionEntity;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.util.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.storage.TagValueOutput;

/** Ported from Create's DeployerMovingInteraction (MIT, The Create Team). */
public class DeployerMovingInteraction extends MovingInteractionBehaviour {

	@Override
	public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
		AbstractContraptionEntity contraptionEntity) {
		MutablePair<StructureBlockInfo, MovementContext> actor = contraptionEntity.getContraption()
			.getActorAt(localPos);
		if (actor == null || actor.right == null)
			return false;

		MovementContext ctx = actor.right;
		ItemStack heldStack = player.getItemInHand(activeHand);
		if (heldStack.getItem()
			.equals(ModItems.WRENCH)) {
			if (ctx.blockEntityData == null)
				return false;
			DeployerBlockEntity.Mode mode = NBTHelper.readEnum(ctx.blockEntityData, "Mode", DeployerBlockEntity.Mode.class);
			NBTHelper.writeEnum(ctx.blockEntityData, "Mode",
				mode == DeployerBlockEntity.Mode.PUNCH ? DeployerBlockEntity.Mode.USE : DeployerBlockEntity.Mode.PUNCH);

		} else {
			if (ctx.world.isClientSide())
				return true; // we'll try again on the server side
			DeployerFakePlayer fake = DeployerMovementBehaviour.getPlayer(ctx);
			if (fake == null)
				return false;

			ItemStack deployerItem = fake.getMainHandItem();
			player.setItemInHand(activeHand, deployerItem.copy());
			fake.setItemInHand(InteractionHand.MAIN_HAND, heldStack.copy());
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
			out.store("HeldItem", ItemStack.OPTIONAL_CODEC, heldStack);
			CompoundTag held = out.buildResult();
			if (ctx.blockEntityData != null)
				ctx.blockEntityData.merge(held);
			ctx.data.merge(held);
		}
		return true;
	}
}

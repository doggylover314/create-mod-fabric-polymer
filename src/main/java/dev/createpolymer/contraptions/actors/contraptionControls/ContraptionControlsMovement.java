package dev.createpolymer.contraptions.actors.contraptionControls;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Ported from Create's ContraptionControlsMovement (MIT, The Create Team). The client animation of the indicator and
 * the elevator floor selection are not ported.
 */
public class ContraptionControlsMovement implements MovementBehaviour {

	@Override
	public @Nullable ItemStack canBeDisabledVia(MovementContext context) {
		return null;
	}

	@Override
	public void stopMoving(MovementContext context) {
		ItemStack filter = getFilter(context);
		if (filter != null)
			context.blockEntityData.putBoolean("Disabled", context.contraption.isActorTypeDisabled(filter)
				|| context.contraption.isActorTypeDisabled(ItemStack.EMPTY));
	}

	public static boolean isSameFilter(ItemStack stack1, ItemStack stack2) {
		if (stack1.isEmpty() && stack2.isEmpty())
			return true;
		return ItemStack.isSameItemSameComponents(stack1, stack2);
	}

	@Nullable
	public static ItemStack getFilter(MovementContext ctx) {
		CompoundTag blockEntityData = ctx.blockEntityData;
		if (blockEntityData == null)
			return null;
		return TagValueInput.create(ProblemReporter.DISCARDING, ctx.world.registryAccess(), blockEntityData)
			.read("Filter", ItemStack.OPTIONAL_CODEC)
			.orElse(ItemStack.EMPTY);
	}

	public static boolean isDisabledInitially(MovementContext ctx) {
		return ctx.blockEntityData != null && ctx.blockEntityData.getBooleanOr("Disabled", false);
	}

}

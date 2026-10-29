package dev.createpolymer.api.behaviour.movement;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.registry.SimpleRegistry;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * MovementBehaviors, also known as Actors, provide behavior to blocks mounted on contraptions.
 * Blocks may be associated with a behavior through {@link #REGISTRY}.
 * <p>
 * All of this runs on the server only; there is no client side (no renderInContraption / createVisual).
 */
public interface MovementBehaviour {
	SimpleRegistry<Block, MovementBehaviour> REGISTRY = SimpleRegistry.create();

	/**
	 * Registers a behavior to a block (Create's {@code movementBehaviour(...)} registrate transformer).
	 */
	static void movementBehaviour(Block block, MovementBehaviour behaviour) {
		REGISTRY.register(block, behaviour);
	}

	default boolean isActive(MovementContext context) {
		return !context.disabled;
	}

	default void tick(MovementContext context) {}

	default void startMoving(MovementContext context) {}

	default void visitNewPosition(MovementContext context, BlockPos pos) {}

	default Vec3 getActiveAreaOffset(MovementContext context) {
		return Vec3.ZERO;
	}

	@Nullable
	default ItemStack canBeDisabledVia(MovementContext context) {
		Block block = context.state.getBlock();
		if (block == null)
			return null;
		return new ItemStack(block);
	}

	default void onDisabledByControls(MovementContext context) {
		cancelStall(context);
	}

	default boolean mustTickWhileDisabled() {
		return false;
	}

	default void collectOrDropItem(MovementContext context, ItemStack stack) {
		ItemStack remainder;
		if (ContraptionRegistry.moveItemsToStorage)
			remainder = ItemHandlerHelper.insertItem(context.contraption.getStorage().getAllItems(), stack, false);
		else
			remainder = stack;
		if (remainder.isEmpty())
			return;

		// Actors might void items if their positions is undefined
		Vec3 vec = context.position;
		if (vec == null)
			return;

		ItemEntity itemEntity = new ItemEntity(context.world, vec.x, vec.y, vec.z, remainder);
		itemEntity.setDeltaMovement(context.motion.add(0, 0.5f, 0)
			.scale(context.world.getRandom().nextFloat() * .3f));
		context.world.addFreshEntity(itemEntity);
	}

	default void onSpeedChanged(MovementContext context, Vec3 oldMotion, Vec3 motion) {}

	default void stopMoving(MovementContext context) {}

	default void cancelStall(MovementContext context) {
		context.stall = false;
	}

	default void writeExtraData(MovementContext context) {}

	default boolean disableBlockEntityRendering() {
		return false;
	}
}

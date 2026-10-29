package dev.createpolymer.processing.basin;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.util.ItemSlots;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's BasinMovementBehaviour (MIT, The Create Team): a basin turned upside down on a contraption
 * drops what it holds. The inventories are read from the saved block entity data.
 */
public class BasinMovementBehaviour implements MovementBehaviour {
	public Map<String, ItemSlots> getOrReadInventory(MovementContext context) {
		Map<String, ItemSlots> map = new LinkedHashMap<>();
		map.put("InputItems", new ItemSlots(9, 64, () -> {}));
		map.put("OutputItems", new ItemSlots(9, 64, () -> {}));
		if (context.blockEntityData == null)
			return map;
		ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, context.world.registryAccess(),
			context.blockEntityData);
		map.forEach((s, h) -> h.load(input.childOrEmpty(s)));
		return map;
	}

	@Override
	public void tick(MovementContext context) {
		MovementBehaviour.super.tick(context);
		if (context.position == null)
			return;
		if (context.temporaryData == null || (boolean) context.temporaryData) {
			Vec3 facingVec = context.rotation.apply(Vec3.atLowerCornerOf(Direction.UP.getUnitVec3i()));
			facingVec.normalize();
			if (Direction.getApproximateNearest(facingVec.x, facingVec.y, facingVec.z) == Direction.DOWN)
				dump(context, facingVec);
		}
	}

	private void dump(MovementContext context, Vec3 facingVec) {
		if (context.blockEntityData == null)
			return;
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, context.world.registryAccess());
		getOrReadInventory(context).forEach((key, slots) -> {
			for (int i = 0; i < slots.size(); i++) {
				if (slots.get(i)
					.isEmpty())
					continue;
				ItemEntity itemEntity = new ItemEntity(context.world, context.position.x, context.position.y,
					context.position.z, slots.get(i));
				itemEntity.setDeltaMovement(facingVec.scale(.05));
				context.world.addFreshEntity(itemEntity);
				slots.stacks().set(i, ItemStack.EMPTY);
			}
			slots.save(out.child(key));
		});
		CompoundTag emptied = out.buildResult();
		context.blockEntityData.merge(emptied);
		context.temporaryData = false; // did already dump, so can't anymore
	}
}

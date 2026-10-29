package dev.createpolymer.legacy;

import net.minecraft.world.entity.item.ItemEntity;

public class ShadowSteelItem extends NoGravMagicalDohickyItem {

	public ShadowSteelItem(Properties properties) {
		super(properties);
	}

	@Override
	protected void onCreated(ItemEntity entity) {
		super.onCreated(entity);
		float yMotion = (float) (entity.fallDistance + 3) / 50f;
		entity.setDeltaMovement(0, yMotion, 0);
	}

}

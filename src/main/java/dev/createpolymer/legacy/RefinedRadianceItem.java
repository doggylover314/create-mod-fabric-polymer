package dev.createpolymer.legacy;

import net.minecraft.world.entity.item.ItemEntity;

public class RefinedRadianceItem extends NoGravMagicalDohickyItem {

	public RefinedRadianceItem(Properties properties) {
		super(properties);
	}

	@Override
	protected void onCreated(ItemEntity entity) {
		super.onCreated(entity);
		entity.setDeltaMovement(entity.getDeltaMovement()
			.add(0, .25f, 0));
	}
}

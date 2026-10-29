package dev.createpolymer.legacy;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/**
 * Create's NoGravMagicalDohickyItem: an item that floats in the air and runs {@link #onCreated} once after it was
 * made. The particles and the glint Create shows on the client are not ported.
 */
public class NoGravMagicalDohickyItem extends SimplePolymerItem implements ItemEntityUpdate {

	/** Entity tag that marks an item entity as just created (Create's "JustCreated" persistent data). */
	public static final String JUST_CREATED = "create:just_created";

	public NoGravMagicalDohickyItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	@Override
	public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
		entity.setNoGravity(true);

		if (!entity.entityTags().contains(JUST_CREATED))
			return false;
		onCreated(entity);
		return false;
	}

	protected void onCreated(ItemEntity entity) {
		entity.removeTag(JUST_CREATED);
	}
}

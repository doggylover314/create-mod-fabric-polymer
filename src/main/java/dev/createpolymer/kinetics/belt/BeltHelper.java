package dev.createpolymer.kinetics.belt;

import dev.createpolymer.CreatePolymer;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Only {@link #isItemUpright} so far; the rest of Create's BeltHelper comes with the belt itself.
 */
public class BeltHelper {

	public static final TagKey<Item> UPRIGHT_ON_BELT = TagKey.create(Registries.ITEM, CreatePolymer.id("upright_on_belt"));
	public static final TagKey<Item> NOT_UPRIGHT_ON_BELT = TagKey.create(Registries.ITEM, CreatePolymer.id("not_upright_on_belt"));

	public static boolean isItemUpright(ItemStack stack) {
		// Create also asks for an item fluid handler; buckets are the fluid containers we know of.
		boolean isFluidHandler = stack.getItem() instanceof BucketItem || stack.is(Items.MILK_BUCKET);
		boolean useUpright = stack.is(UPRIGHT_ON_BELT);
		boolean forceDisableUpright = !stack.is(NOT_UPRIGHT_ON_BELT);

		return (isFluidHandler || useUpright) && forceDisableUpright;
	}

}

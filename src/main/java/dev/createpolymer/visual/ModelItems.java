package dev.createpolymer.visual;

import dev.createpolymer.CreatePolymer;
import eu.pb4.polymer.resourcepack.extras.api.ResourcePackExtras;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item stacks that show one of Create's block models. They only exist inside
 * display entities; the item model id points at a "bridged" model generated
 * by polymer-resource-pack-extras for every file in assets/create/models/block.
 */
public final class ModelItems {
	private ModelItems() {}

	public static ItemStack block(String path) {
		return of(Identifier.fromNamespaceAndPath(CreatePolymer.MOD_ID, "block/" + path));
	}

	public static ItemStack of(Identifier modelId) {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, ResourcePackExtras.bridgeModel(modelId));
		return stack;
	}
}

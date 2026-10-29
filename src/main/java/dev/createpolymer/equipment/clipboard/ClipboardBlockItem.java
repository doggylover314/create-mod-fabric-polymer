package dev.createpolymer.equipment.clipboard;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.recipe.ItemCopyingRecipe.SupportsItemCopying;
import dev.createpolymer.CreatePolymer;
import dev.createpolymer.equipment.clipboard.ClipboardOverrides.ClipboardType;
import dev.createpolymer.registry.feature.ToolsRegistry;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The clipboard item. Right-click opens the editor ({@link ClipboardGui}), sneak-right-click on a block places it.
 * The item model follows the {@link ClipboardType} of the content: empty, written, or with a quill while the editor
 * is open (Create's model predicate).
 */
public class ClipboardBlockItem extends PolymerBlockItem implements SupportsItemCopying {

	public ClipboardBlockItem(Block pBlock, Properties pProperties) {
		super(pBlock, pProperties, Items.TRIAL_KEY, true);
	}

	@NotNull
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.PASS;
		if (player.isShiftKeyDown())
			return super.useOn(context);
		return use(context.getLevel(), player, context.getHand());
	}

	@Override
	public InteractionResult use(Level world, Player player, InteractionHand hand) {
		ItemStack heldItem = player.getItemInHand(hand);
		if (hand == InteractionHand.OFF_HAND)
			return InteractionResult.PASS;

		player.getCooldowns()
			.addCooldown(heldItem, 10);
		if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.SUCCESS;

		ClipboardContent content = heldItem.getOrDefault(ToolsRegistry.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
		heldItem.set(ToolsRegistry.CLIPBOARD_CONTENT, content.setType(ClipboardType.EDITING));
		ClipboardGui.open(serverPlayer, ClipboardGui.Target.item(serverPlayer, serverPlayer.getInventory()
			.getSelectedSlot()));

		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		ClipboardType type = stack.getOrDefault(ToolsRegistry.CLIPBOARD_CONTENT, ClipboardContent.EMPTY)
			.type();
		return CreatePolymer.id(switch (type) {
			case EMPTY -> "clipboard_0";
			case WRITTEN -> "clipboard";
			case EDITING -> "clipboard_2";
		});
	}

	@Override
	public DataComponentType<?> getComponentType() {
		return ToolsRegistry.CLIPBOARD_CONTENT;
	}

}

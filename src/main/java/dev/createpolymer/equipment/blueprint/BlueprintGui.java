package dev.createpolymer.equipment.blueprint;

import java.util.Optional;

import dev.createpolymer.equipment.blueprint.BlueprintEntity.BlueprintSection;
import dev.createpolymer.foundation.gui.GhostItemGui;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.registry.feature.ToolsRegistry;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.core.NonNullList;

/**
 * Create's BlueprintMenu / BlueprintScreen as an sgui chest: a 3x3 grid of ghost ingredients (any item, filters
 * included), the display slot with the result and a secondary display slot. When the grid is a crafting recipe the
 * display slot is filled with its result, until something else is put there by hand.
 */
public class BlueprintGui extends GhostItemGui {
	private static final String PREFIX = "create.crafting_blueprint.";

	private static final int RESULT_SLOT = 23;
	private static final int SECONDARY_SLOT = 34;
	private static final int HELP_SLOT = 4;
	private static final int ARROW_SLOT = 22;

	private final BlueprintSection section;

	public BlueprintGui(ServerPlayer player, BlueprintSection section) {
		super(MenuType.GENERIC_9x5, player, new ItemStack(ToolsRegistry.CRAFTING_BLUEPRINT).getItemName(), section.getItems());
		this.section = section;
		for (int i = 0; i < 45; i++)
			if (ghostSlot(i) < 0)
				setSlot(i, filler());
		setSlot(HELP_SLOT, new GuiElementBuilder(Items.PAPER)
			.setName(new ItemStack(ToolsRegistry.CRAFTING_BLUEPRINT).getItemName()
				.copy()
				.withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.translatable(PREFIX + "crafting_slot")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatable(PREFIX + "display_slot")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatable(PREFIX + "secondary_display_slot")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatable(PREFIX + "filter_items_viable")
				.withStyle(ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip());
		setSlot(ARROW_SLOT, new GuiElementBuilder(Items.ARROW)
			.setName(Component.translatable(PREFIX + "display_slot")
				.withStyle(ChatFormatting.GRAY))
			.hideDefaultTooltip());
	}

	private static GuiElementBuilder filler() {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)).setName(Component.empty())
			.hideTooltip();
	}

	@Override
	protected boolean allowRepeats() {
		return true;
	}

	/** The crafting grid in the middle left, then the display slots. */
	@Override
	protected int guiSlot(int ghostSlot) {
		if (ghostSlot < 9)
			return (1 + ghostSlot / 3) * 9 + 1 + ghostSlot % 3;
		return ghostSlot == 9 ? RESULT_SLOT : SECONDARY_SLOT;
	}

	@Override
	protected int ghostSlot(int guiSlot) {
		if (guiSlot == RESULT_SLOT)
			return 9;
		if (guiSlot == SECONDARY_SLOT)
			return 10;
		int row = guiSlot / 9;
		int column = guiSlot % 9;
		if (guiSlot < 0 || row < 1 || row > 3 || column < 1 || column > 3)
			return -1;
		return (row - 1) * 3 + column - 1;
	}

	@Override
	protected void onGhostChanged() {
		section.save(ghostInventory);
	}

	@Override
	public boolean onClick(int index, ClickType type, ContainerInput action, GuiElement element) {
		int ghost = ghostSlot(index);
		boolean result = super.onClick(index, type, action, element);
		if (ghost >= 0 && ghost < 9)
			onCraftMatrixChanged();
		else if (ghost == 9 && !ghostInventory.getStackInSlot(9)
			.isEmpty()) {
			// a result chosen by hand stays
			section.setInferredIcon(false);
			section.save(ghostInventory);
		}
		return result;
	}

	@Override
	public ItemStack quickMove(int index) {
		ItemStack result = super.quickMove(index);
		onCraftMatrixChanged();
		return result;
	}

	/** Create: BlueprintMenu.onCraftMatrixChanged. The display slot shows what the grid crafts. */
	public void onCraftMatrixChanged() {
		Level level = section.getBlueprintWorld();
		if (!(level instanceof ServerLevel serverLevel))
			return;

		CraftingInput craftingInput = craftingInput(ghostInventory);
		Optional<RecipeHolder<CraftingRecipe>> optional = serverLevel.recipeAccess()
			.getRecipeFor(RecipeType.CRAFTING, craftingInput, level);

		if (optional.isEmpty()) {
			if (ghostInventory.getStackInSlot(9)
				.isEmpty())
				return;
			if (!section.isInferredIcon())
				return;

			ghostInventory.setStackInSlot(9, ItemStack.EMPTY);
			section.setInferredIcon(false);
			refreshGhostSlot(9);
			section.save(ghostInventory);
			return;
		}

		CraftingRecipe icraftingrecipe = optional.get()
			.value();
		ItemStack itemstack = icraftingrecipe.assemble(craftingInput);
		ghostInventory.setStackInSlot(9, itemstack);
		section.setInferredIcon(true);
		refreshGhostSlot(9);
		section.save(ghostInventory);
	}

	private static CraftingInput craftingInput(ItemStackHandler items) {
		NonNullList<ItemStack> list = NonNullList.withSize(9, ItemStack.EMPTY);
		for (int i = 0; i < 9; i++)
			list.set(i, items.getStackInSlot(i)
				.copy());
		return CraftingInput.of(3, 3, list);
	}

	@Override
	public boolean stillValid() {
		return section.isEntityAlive() && section.canPlayerUse(player);
	}
}

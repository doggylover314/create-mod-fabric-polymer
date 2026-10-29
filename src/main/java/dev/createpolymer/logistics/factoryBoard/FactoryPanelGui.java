package dev.createpolymer.logistics.factoryBoard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.box.PackageStyles;
import dev.createpolymer.registry.feature.FactoryGaugeRegistry;
import dev.createpolymer.util.Lang;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * Create's FactoryPanelScreen as an sgui chest. Differences from the screen:
 * <ul>
 * <li>Scrolling an ingredient or the output changes the amount: left-click adds one and right-click removes one
 * (shift: ten); dropping an ingredient (Q) disconnects it.</li>
 * <li>The target amount, which Create sets by holding right-click on the gauge, is part of this GUI.</li>
 * <li>The address is typed in an anvil.</li>
 * <li>Every change is applied at once; Create sends the settings when the screen closes.</li>
 * <li>"Add new connection" and "Move this gauge" close the GUI and wait for the next right-click on a block
 * ({@link FactoryPanelConnectionHandler}).</li>
 * </ul>
 */
public class FactoryPanelGui extends SimpleGui {
	private static final String PREFIX = "gui.factory_panel.";

	private static final int OVERVIEW = 8;
	private static final int CRAFTING_BUTTON = 9;
	private static final int NEW_INPUT_BUTTON = 18;
	private static final int RELOCATE_BUTTON = 27;
	private static final int INPUT_START = 11; // 3x3, row stride 9
	private static final int OUTPUT = 24;
	private static final int UNIT_BUTTON = 36;
	private static final int MINUS_10 = 37;
	private static final int MINUS_1 = 38;
	private static final int AMOUNT = 39;
	private static final int PLUS_1 = 40;
	private static final int PLUS_10 = 41;
	private static final int LINKS_BUTTON = 45;
	private static final int ADDRESS_BUTTON = 46;
	private static final int PROMISES_BUTTON = 48;
	private static final int EXPIRY_BUTTON = 49;
	private static final int RESET_BUTTON = 52;
	private static final int DONE_BUTTON = 53;

	private static final int MAX_AMOUNT = 100;

	private final FactoryPanelBehaviour behaviour;
	private final boolean restocker;
	private boolean closed;

	private String address;
	private int promiseExpiration;

	private BigItemStack outputConfig;
	private List<BigItemStack> inputConfig;
	private List<FactoryPanelConnection> connections;

	private CraftingRecipe availableCraftingRecipe;
	private boolean craftingActive;
	private List<BigItemStack> craftingIngredients;

	public FactoryPanelGui(ServerPlayer player, FactoryPanelBehaviour behaviour) {
		super(MenuType.GENERIC_9x6, player, false);
		this.behaviour = behaviour;
		this.restocker = behaviour.panelBE().restocker;
		this.address = behaviour.recipeAddress;
		this.promiseExpiration = behaviour.promiseClearingInterval;
		this.availableCraftingRecipe = null;
		this.craftingActive = !behaviour.activeCraftingArrangement.isEmpty();
		setTitle(tr(PREFIX + (restocker ? "title_as_restocker" : "title_as_recipe")));
		setLockPlayerInventory(true);
		updateConfigs();
		refresh();
	}

	// ---- data ----

	private void updateConfigs() {
		connections = new ArrayList<>(behaviour.targetedBy.values());
		outputConfig = new BigItemStack(behaviour.getFilter(), behaviour.recipeOutput);
		inputConfig = connections.stream()
			.map(c -> {
				FactoryPanelBehaviour b = FactoryPanelBehaviour.at(player.level(), c.from);
				return b == null ? new BigItemStack(ItemStack.EMPTY, 0) : new BigItemStack(b.getFilter(), c.amount);
			})
			.collect(Collectors.toCollection(ArrayList::new));

		searchForCraftingRecipe();

		if (availableCraftingRecipe == null) {
			craftingActive = false;
			return;
		}

		craftingIngredients = convertRecipeToPackageOrderContext(availableCraftingRecipe, inputConfig, false);
	}

	/** The ingredients of a crafting recipe with its gaps, row by row for shaped recipes. */
	private static List<Optional<Ingredient>> ingredientsOf(CraftingRecipe recipe) {
		if (recipe instanceof ShapedRecipe shaped)
			return shaped.getIngredients();
		return recipe.placementInfo()
			.ingredients()
			.stream()
			.map(Optional::of)
			.toList();
	}

	/**
	 * Lays the recipe out in a 3x3 grid with the items of the given inputs, as the crafting arrangement of a gauge
	 * (and the stock keeper's crafting patterns).
	 */
	public static List<BigItemStack> convertRecipeToPackageOrderContext(CraftingRecipe availableCraftingRecipe,
		List<BigItemStack> inputs, boolean respectAmounts) {
		List<BigItemStack> craftingIngredients = new ArrayList<>();
		BigItemStack emptyIngredient = new BigItemStack(ItemStack.EMPTY, 1);
		List<Optional<Ingredient>> ingredients = ingredientsOf(availableCraftingRecipe);
		List<BigItemStack> mutableInputs = BigItemStack.duplicateWrappers(inputs);

		int width = Math.min(3, ingredients.size());
		int height = Math.min(3, ingredients.size() / 3 + 1);

		if (availableCraftingRecipe instanceof ShapedRecipe shaped) {
			width = shaped.getWidth();
			height = shaped.getHeight();
		}

		if (height == 1)
			for (int i = 0; i < 3; i++)
				craftingIngredients.add(emptyIngredient);
		if (width == 1)
			craftingIngredients.add(emptyIngredient);

		for (int i = 0; i < ingredients.size(); i++) {
			Optional<Ingredient> ingredient = ingredients.get(i);
			BigItemStack craftingIngredient = emptyIngredient;

			if (ingredient.isPresent())
				for (BigItemStack bigItemStack : mutableInputs)
					if (bigItemStack.count > 0 && ingredient.get()
						.test(bigItemStack.stack)) {
						craftingIngredient = new BigItemStack(bigItemStack.stack, 1);
						if (respectAmounts)
							bigItemStack.count -= 1;
						break;
					}

			craftingIngredients.add(craftingIngredient);

			if (width < 3 && (i + 1) % width == 0)
				for (int j = 0; j < 3 - width; j++)
					if (craftingIngredients.size() < 9)
						craftingIngredients.add(emptyIngredient);
		}

		while (craftingIngredients.size() < 9)
			craftingIngredients.add(emptyIngredient);

		return craftingIngredients;
	}

	/** Create's AllRecipeTypes.shouldIgnoreInAutomation: recipes whose id ends in "_manual_only". */
	private static boolean shouldIgnoreInAutomation(RecipeHolder<?> recipe) {
		return recipe.id()
			.identifier()
			.getPath()
			.endsWith("_manual_only");
	}

	private static boolean isUsableCraftingRecipe(RecipeHolder<?> holder) {
		return holder.value() instanceof ShapedRecipe || holder.value() instanceof ShapelessRecipe;
	}

	private void searchForCraftingRecipe() {
		availableCraftingRecipe = null;
		ItemStack output = outputConfig.stack;
		if (output.isEmpty())
			return;
		if (behaviour.targetedBy.isEmpty())
			return;

		Set<Item> itemsToUse = inputConfig.stream()
			.map(b -> b.stack)
			.filter(i -> !i.isEmpty())
			.map(i -> i.getItem())
			.collect(Collectors.toSet());

		ServerLevel level = player.level();

		for (RecipeHolder<?> holder : level.recipeAccess()
			.getRecipes()) {
			if (!isUsableCraftingRecipe(holder))
				continue;
			CraftingRecipe recipe = (CraftingRecipe) holder.value();
			if (output.getItem() != recipe.assemble(CraftingInput.EMPTY)
				.getItem())
				continue;
			if (shouldIgnoreInAutomation(holder))
				continue;

			Set<Item> itemsUsed = new HashSet<>();
			boolean allAvailable = true;
			for (Optional<Ingredient> optional : ingredientsOf(recipe)) {
				if (optional.isEmpty())
					continue;
				Ingredient ingredient = optional.get();
				boolean available = false;
				for (BigItemStack bis : inputConfig) {
					if (!bis.stack.isEmpty() && ingredient.test(bis.stack)) {
						available = true;
						itemsUsed.add(bis.stack.getItem());
						break;
					}
				}
				if (!available) {
					allAvailable = false;
					break;
				}
			}
			if (!allAvailable || itemsUsed.size() < itemsToUse.size())
				continue;

			availableCraftingRecipe = recipe;
			return;
		}
	}

	private void sendIt(@Nullable FactoryPanelPosition toRemove, boolean clearPromises, boolean reset,
		boolean redstoneReset) {
		if (!behaviour.isActive() || behaviour.blockEntity.isRemoved())
			return;
		Map<FactoryPanelPosition, Integer> inputs = new HashMap<>();

		if (inputConfig.size() == connections.size())
			for (int i = 0; i < inputConfig.size(); i++) {
				BigItemStack stackInConfig = inputConfig.get(i);
				inputs.put(connections.get(i).from, craftingActive ? (int) craftingIngredients.stream()
					.filter(
						b -> !b.stack.isEmpty() && ItemStack.isSameItemSameComponents(b.stack, stackInConfig.stack))
					.count() : stackInConfig.count);
			}

		List<ItemStack> craftingArrangement = craftingActive ? craftingIngredients.stream()
			.map(b -> b.stack)
			.toList() : List.of();

		FactoryPanelPosition pos = behaviour.getPanelPosition();
		new FactoryPanelConfigurationPacket(pos, address, inputs, craftingArrangement, outputConfig.count,
			promiseExpiration, toRemove, clearPromises, reset, redstoneReset).applySettings(player,
				behaviour.panelBE());
	}

	private void apply() {
		sendIt(null, false, false, false);
		updateConfigs();
		refresh();
	}

	// ---- layout ----

	private static MutableComponent tr(String key, Object... args) {
		return Component.translatable("create." + key, args);
	}

	private static MutableComponent trf(String key, String fallback, Object... args) {
		return Component.translatableWithFallback("create." + key, fallback, args);
	}

	private static Component gray(String key) {
		return tr(key).withStyle(ChatFormatting.GRAY);
	}

	private static Component hint(String key, String fallback) {
		return trf(key, fallback).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
	}

	private static GuiElementBuilder filler() {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)).setName(Component.empty())
			.hideTooltip();
	}

	private static int inputSlot(int index) {
		return INPUT_START + index / 3 * 9 + index % 3;
	}

	private void refresh() {
		for (int i = 0; i < 54; i++)
			setSlot(i, filler());

		ItemStack filter = behaviour.getFilter();

		// the gauge and what it is doing
		GuiElementBuilder overview = new GuiElementBuilder(filter.isEmpty() ? new ItemStack(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM) : filter.copyWithCount(1))
			.setName(behaviour.getLabel())
			.addLoreLine(behaviour.getCountLabelForValueBox())
			.hideDefaultTooltip();
		setSlot(OVERVIEW, overview);

		// crafting
		if (availableCraftingRecipe != null) {
			setSlot(CRAFTING_BUTTON, new GuiElementBuilder(Items.CRAFTING_TABLE)
				.setName(tr(PREFIX + "activate_crafting").withStyle(craftingActive ? ChatFormatting.GREEN : ChatFormatting.GOLD))
				.addLoreLine(gray(PREFIX + "crafting_input_tip"))
				.addLoreLine(gray(PREFIX + "crafting_input_tip_1"))
				.glow(craftingActive)
				.hideDefaultTooltip()
				.setCallback(() -> {
					craftingActive = !craftingActive;
					if (craftingActive)
						outputConfig.count = availableCraftingRecipe.assemble(CraftingInput.EMPTY)
							.getCount();
					apply();
				}));
		}

		if (!restocker) {
			setSlot(NEW_INPUT_BUTTON, new GuiElementBuilder(Items.LEAD)
				.setName(tr(PREFIX + "connect_input").withStyle(ChatFormatting.GOLD))
				.addLoreLine(gray(PREFIX + "unconfigured_input_tip"))
				.addLoreLine(gray(PREFIX + "unconfigured_input_tip_1"))
				.hideDefaultTooltip()
				.setCallback(() -> {
					sendIt(null, false, false, false);
					close();
					FactoryPanelConnectionHandler.startConnection(player, behaviour);
				}));
			setSlot(RELOCATE_BUTTON, new GuiElementBuilder(Items.PISTON)
				.setName(tr(PREFIX + "relocate").withStyle(ChatFormatting.GOLD))
				.hideDefaultTooltip()
				.setCallback(() -> {
					sendIt(null, false, false, false);
					close();
					FactoryPanelConnectionHandler.startRelocating(player, behaviour);
				}));
		}

		// inputs
		if (restocker) {
			if (!filter.isEmpty())
				setSlot(inputSlot(4), new GuiElementBuilder(filter.copyWithCount(1))
					.setName(tr(PREFIX + "sending_item", filter.getHoverName())
						.withStyle(ChatFormatting.GOLD))
					.addLoreLine(gray(PREFIX + "sending_item_tip"))
					.addLoreLine(gray(PREFIX + "sending_item_tip_1"))
					.hideDefaultTooltip());
		} else if (craftingActive) {
			for (int i = 0; i < craftingIngredients.size() && i < 9; i++) {
				BigItemStack ingredient = craftingIngredients.get(i);
				if (ingredient.stack.isEmpty())
					continue;
				setSlot(inputSlot(i), new GuiElementBuilder(ingredient.stack.copyWithCount(1))
					.setName(tr(PREFIX + "crafting_input").withStyle(ChatFormatting.GOLD))
					.addLoreLine(ingredient.stack.getHoverName())
					.addLoreLine(gray(PREFIX + "crafting_input_tip"))
					.addLoreLine(gray(PREFIX + "crafting_input_tip_1"))
					.hideDefaultTooltip());
			}
		} else {
			for (int i = 0; i < inputConfig.size() && i < 9; i++)
				setSlot(inputSlot(i), inputElement(i));
			if (inputConfig.isEmpty())
				setSlot(inputSlot(4), new GuiElementBuilder(Items.STRUCTURE_VOID)
					.setName(tr(PREFIX + "unconfigured_input").withStyle(ChatFormatting.GOLD))
					.addLoreLine(gray(PREFIX + "unconfigured_input_tip"))
					.addLoreLine(gray(PREFIX + "unconfigured_input_tip_1"))
					.hideDefaultTooltip());
		}

		// output
		if (!restocker && !filter.isEmpty()) {
			List<Component> lore = new ArrayList<>(List.of(gray(PREFIX + "expected_output_tip"),
				gray(PREFIX + "expected_output_tip_1")));
			if (!craftingActive)
				lore.add(hint(PREFIX + "click_to_change_amount", "Left-click: +1, right-click: -1 (shift: 10)"));
			GuiElementBuilder output = new GuiElementBuilder(filter.copyWithCount(1))
				.setName(tr(PREFIX + "expected_output", filter.getHoverName()
					.copy()
					.append(" x" + outputConfig.count))
					.withStyle(ChatFormatting.GOLD))
				.setCount(Mth.clamp(outputConfig.count, 1, 64))
				.hideDefaultTooltip();
			lore.forEach(output::addLoreLine);
			output.setCallback(click -> {
				if (craftingActive)
					return;
				int delta = amountDelta(click);
				if (delta == 0)
					return;
				outputConfig.count = Mth.clamp(outputConfig.count + delta, 1, 64);
				apply();
			});
			setSlot(OUTPUT, output);
		}

		// target amount
		if (!filter.isEmpty()) {
			boolean items = behaviour.upTo;
			setSlot(UNIT_BUTTON, new GuiElementBuilder(items ? Items.PAPER : Items.CHEST)
				.setName(tr("factory_panel.target_amount").withStyle(ChatFormatting.GOLD))
				.addLoreLine(tr("schedule.condition.threshold." + (items ? "items" : "stacks"))
					.withStyle(ChatFormatting.YELLOW))
				.addLoreLine(hint(PREFIX + "click_to_switch_unit", "Click to switch between items and stacks"))
				.hideDefaultTooltip()
				.setCallback(() -> setAmount(items ? 1 : 0, behaviour.count)));
			setSlot(MINUS_10, amountStep(-10));
			setSlot(MINUS_1, amountStep(-1));
			setSlot(AMOUNT, new GuiElementBuilder(filter.copyWithCount(1))
				.setName(behaviour.formatValue(behaviour.getValueSettings())
					.withStyle(ChatFormatting.GOLD))
				.setCount(Mth.clamp(behaviour.count, 1, 64))
				.addLoreLine(behaviour.getCountLabelForValueBox())
				.hideDefaultTooltip());
			setSlot(PLUS_1, amountStep(1));
			setSlot(PLUS_10, amountStep(10));
		}

		// redstone links
		if (!behaviour.targetedByLinks.isEmpty())
			setSlot(LINKS_BUTTON, new GuiElementBuilder(Items.REDSTONE)
				.setName(tr(PREFIX + "has_link_connections").withStyle(ChatFormatting.GOLD))
				.addLoreLine(hint(PREFIX + "left_click_disconnect", "Click to disconnect"))
				.hideDefaultTooltip()
				.setCallback(() -> {
					sendIt(null, false, false, true);
					updateConfigs();
					refresh();
				}));

		// address
		setSlot(ADDRESS_BUTTON, addressElement());

		// promises
		int promised = behaviour.getPromised();
		GuiElementBuilder promises = new GuiElementBuilder(PackageStyles.getDefaultBox())
			.setCount(Mth.clamp(promised, 1, 64))
			.hideDefaultTooltip();
		if (promised == 0) {
			promises.setName(tr(PREFIX + "no_open_promises").withStyle(ChatFormatting.GOLD))
				.addLoreLine(gray(PREFIX + (restocker ? "restocker_promises_tip" : "recipe_promises_tip")))
				.addLoreLine(gray(PREFIX + (restocker ? "restocker_promises_tip_1" : "recipe_promises_tip_1")))
				.addLoreLine(gray(PREFIX + "promise_prevents_oversending"));
		} else {
			promises.setName(tr(PREFIX + "promised_items").withStyle(ChatFormatting.GOLD))
				.addLoreLine(Component.literal(filter.getHoverName()
					.getString() + " x" + promised))
				.addLoreLine(hint(PREFIX + "left_click_reset", "Click to reset"));
		}
		promises.setCallback(() -> {
			sendIt(null, true, false, false);
			updateConfigs();
			refresh();
		});
		setSlot(PROMISES_BUTTON, promises);

		setSlot(EXPIRY_BUTTON, new GuiElementBuilder(Items.CLOCK)
			.setName(tr(PREFIX + (promiseExpiration == -1 ? "promises_do_not_expire" : "promises_expire_title"))
				.withStyle(ChatFormatting.GOLD))
			.addLoreLine(Component.literal(promiseExpiration == -1 ? "/" : promiseExpiration == 0 ? "30s" : promiseExpiration + "m")
				.withStyle(ChatFormatting.YELLOW))
			.addLoreLine(hint(PREFIX + "click_to_change_expiry", "Left-click: +1, right-click: -1 (shift: 5)"))
			.hideDefaultTooltip()
			.setCallback(click -> {
				int delta = amountDelta(click);
				if (delta == 0)
					return;
				if (click.shift)
					delta = delta > 0 ? 5 : -5;
				promiseExpiration = Mth.clamp(promiseExpiration + delta, -1, 31);
				apply();
			}));

		setSlot(RESET_BUTTON, new GuiElementBuilder(Items.BARRIER)
			.setName(tr(PREFIX + "reset").withStyle(ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(() -> {
				closed = true;
				sendIt(null, false, true, false);
				close();
			}));

		setSlot(DONE_BUTTON, new GuiElementBuilder(Items.EMERALD)
			.setName(tr(PREFIX + "save_and_close").withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	private GuiElementBuilder inputElement(int index) {
		BigItemStack itemStack = inputConfig.get(index);
		if (itemStack.stack.isEmpty())
			return new GuiElementBuilder(Items.STRUCTURE_VOID)
				.setName(tr(PREFIX + "empty_panel").withStyle(ChatFormatting.GOLD))
				.addLoreLine(hint(PREFIX + "drop_to_disconnect", "Drop (Q) to disconnect"))
				.hideDefaultTooltip()
				.setCallback(click -> disconnectIfDropped(click, index));

		return new GuiElementBuilder(itemStack.stack.copyWithCount(1))
			.setName(tr(PREFIX + "sending_item", itemStack.stack.getHoverName()
				.copy()
				.append(" x" + itemStack.count))
				.withStyle(ChatFormatting.GOLD))
			.setCount(Mth.clamp(itemStack.count, 1, 64))
			.addLoreLine(hint(PREFIX + "click_to_change_amount", "Left-click: +1, right-click: -1 (shift: 10)"))
			.addLoreLine(hint(PREFIX + "drop_to_disconnect", "Drop (Q) to disconnect"))
			.hideDefaultTooltip()
			.setCallback(click -> {
				if (disconnectIfDropped(click, index))
					return;
				int delta = amountDelta(click);
				if (delta == 0)
					return;
				itemStack.count = Mth.clamp(itemStack.count + delta, 1, 64);
				apply();
			});
	}

	private boolean disconnectIfDropped(ClickType click, int index) {
		if (click != ClickType.DROP && click != ClickType.CTRL_DROP)
			return false;
		if (index >= connections.size())
			return true;
		sendIt(connections.get(index).from, false, false, false);
		updateConfigs();
		refresh();
		return true;
	}

	private static int amountDelta(ClickType click) {
		int step = click.shift ? 10 : 1;
		return click.isLeft ? step : click.isRight ? -step : 0;
	}

	private GuiElementBuilder amountStep(int delta) {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(delta > 0 ? DyeColor.LIME : DyeColor.RED))
			.setName(Component.literal((delta > 0 ? "+" : "") + delta)
				.withStyle(delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED))
			.hideDefaultTooltip()
			.setCallback(() -> setAmount(behaviour.upTo ? 0 : 1, behaviour.count + delta));
	}

	private void setAmount(int row, int value) {
		behaviour.setValueSettings(player, new FactoryPanelBehaviour.ValueSettings(row, Mth.clamp(value, 0, MAX_AMOUNT)),
			false);
		updateConfigs();
		refresh();
	}

	private GuiElementBuilder addressElement() {
		boolean blank = address.isBlank();
		GuiElementBuilder element = new GuiElementBuilder(Items.NAME_TAG).hideDefaultTooltip();
		if (blank) {
			element.setName(tr(PREFIX + (restocker ? "restocker_address" : "recipe_address")).withStyle(ChatFormatting.GOLD))
				.addLoreLine(gray(PREFIX + (restocker ? "restocker_address_tip" : "recipe_address_tip")))
				.addLoreLine(gray(PREFIX + (restocker ? "restocker_address_tip_1" : "recipe_address_tip_1")));
			if (behaviour.isMissingAddress())
				element.addLoreLine(tr(PREFIX + "address_missing").withStyle(ChatFormatting.RED));
		} else {
			element.setName(tr(PREFIX + (restocker ? "restocker_address_given" : "recipe_address_given"))
				.withStyle(ChatFormatting.GOLD))
				.addLoreLine(Component.literal("'" + address + "'")
					.withStyle(ChatFormatting.GRAY));
		}
		element.addLoreLine(tr("gui.schedule.lmb_edit").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		element.setCallback(this::editAddress);
		return element;
	}

	/** Create edits the address in a text box on the screen; here an anvil takes its place. */
	private void editAddress() {
		ServerPlayer player = this.player;
		FactoryPanelBehaviour behaviour = this.behaviour;
		String frogAddress = behaviour.getFrogAddress();
		String initial = address.isBlank() && frogAddress != null ? frogAddress : address;

		AnvilInputGui anvil = new AnvilInputGui(player, false);
		anvil.setTitle(tr(PREFIX + (restocker ? "restocker_address" : "recipe_address")));
		anvil.setDefaultInputValue(initial);
		anvil.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
			.setName(tr(PREFIX + "save_and_close").withStyle(ChatFormatting.GREEN))
			.setCallback(() -> {
				String input = anvil.getInput();
				if (input.length() > 25)
					input = input.substring(0, 25);
				if (behaviour.isActive() && !behaviour.blockEntity.isRemoved()) {
					behaviour.recipeAddress = input;
					behaviour.blockEntity.notifyUpdate();
					new FactoryPanelGui(player, behaviour).open();
				} else {
					anvil.close();
				}
			}));
		anvil.open();
	}

	// ---- life cycle ----

	@Override
	public void onTick() {
		if (behaviour.targetedBy.size() != inputConfig.size() || behaviour.getFilter().isEmpty() != outputConfig.stack.isEmpty()) {
			updateConfigs();
			refresh();
		}
	}

	@Override
	public void onRemoved() {
		if (closed)
			return;
		closed = true;
		sendIt(null, false, false, false);
	}

	@Override
	public boolean stillValid() {
		return behaviour.isActive() && !behaviour.blockEntity.isRemoved() && behaviour.blockEntity.canPlayerUse(player);
	}
}

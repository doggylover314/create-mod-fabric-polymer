package dev.createpolymer.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;

import dev.createpolymer.equipment.blueprint.BlueprintEntity;
import dev.createpolymer.equipment.blueprint.BlueprintEntity.BlueprintSection;
import dev.createpolymer.equipment.blueprint.BlueprintGui;
import dev.createpolymer.equipment.blueprint.BlueprintItem;
import dev.createpolymer.equipment.blueprint.BlueprintModel;
import dev.createpolymer.equipment.clipboard.ClipboardBlock;
import dev.createpolymer.equipment.clipboard.ClipboardBlockEntity;
import dev.createpolymer.equipment.clipboard.ClipboardBlockItem;
import dev.createpolymer.equipment.clipboard.ClipboardContent;
import dev.createpolymer.equipment.clipboard.ClipboardEntry;
import dev.createpolymer.equipment.clipboard.ClipboardGui;
import dev.createpolymer.equipment.clipboard.ClipboardOverrides.ClipboardType;
import dev.createpolymer.equipment.clipboard.ClipboardValueSettingsHandler;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.gui.AttributeFilterGui;
import dev.createpolymer.foundation.gui.PackageFilterGui;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.filter.AttributeFilterWhitelistMode;
import dev.createpolymer.logistics.filter.FilterItem;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.logistics.funnel.FunnelBlock;
import dev.createpolymer.logistics.funnel.FunnelBlockEntity;
import dev.createpolymer.logistics.item.filter.attribute.AllItemAttributeTypes;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry;
import dev.createpolymer.logistics.item.filter.attribute.attributes.BookAuthorAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.BookCopyAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.ColorAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.EnchantAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.FluidContentsAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.InTagAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.ItemNameAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.ShulkerFillLevelAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.ShulkerFillLevelAttribute.ShulkerLevels;
import dev.createpolymer.logistics.packagePort.frogport.FrogportBlockEntity;
import dev.createpolymer.redstone.link.LinkBehaviour;
import dev.createpolymer.redstone.link.RedstoneLinkBlock;
import dev.createpolymer.redstone.link.RedstoneLinkBlockEntity;
import dev.createpolymer.redstone.link.controller.LecternControllerBlockEntity;
import dev.createpolymer.redstone.link.controller.LinkedControllerGui;
import dev.createpolymer.redstone.link.controller.LinkedControllerHandler;
import dev.createpolymer.redstone.link.controller.LinkedControllerHandler.Mode;
import dev.createpolymer.redstone.link.controller.LinkedControllerItem;
import dev.createpolymer.redstone.link.controller.LinkedControllerServerHandler;
import dev.createpolymer.registry.feature.FilterRegistry;
import dev.createpolymer.registry.feature.FunnelRegistry;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.registry.feature.ToolsRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import eu.pb4.sgui.api.ClickType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ToolsGameTests {

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	/**
	 * The players of each running test, taken out of the server again by {@link #done}. The tests run side by side, so
	 * a test may only remove its own players: removing another test's player ends its controller session mid-test.
	 */
	private static final java.util.Map<GameTestHelper, List<ServerPlayer>> JOINED =
		new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * A survival player with a connection (an embedded channel), like the one the chain conveyor tests use:
	 * the code under test sends messages, container updates and block updates to its player.
	 */
	private static ServerPlayer player(GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		// the structures are far from the spawn, and links only reach so far
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		player.setPos(at.getX() + .5, at.getY(), at.getZ() + .5);
		JOINED.computeIfAbsent(helper, h -> new ArrayList<>())
			.add(player);
		return player;
	}

	@SuppressWarnings("removal")
	private static void done(GameTestHelper helper) {
		for (ServerPlayer player : JOINED.getOrDefault(helper, List.of())) {
			LinkedControllerHandler.stop(player);
			player.connection.disconnect(Component.literal("test over"));
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		JOINED.remove(helper);
		helper.succeed();
	}

	private static ItemStack attributeFilter(AttributeFilterWhitelistMode mode, ItemAttributeEntry... entries) {
		ItemStack filter = new ItemStack(ToolsRegistry.ATTRIBUTE_FILTER);
		filter.set(ToolsRegistry.ATTRIBUTE_FILTER_WHITELIST_MODE, mode);
		filter.set(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES, List.of(entries));
		return filter;
	}

	private static ItemAttributeEntry entry(ItemAttribute attribute, boolean inverted) {
		return new ItemAttributeEntry(attribute, inverted);
	}

	private static ItemAttribute singleton(dev.createpolymer.logistics.item.filter.attribute.ItemAttributeType type) {
		return type.createAttribute();
	}

	private static ItemStack damaged(Level level) {
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		pickaxe.setDamageValue(10);
		return pickaxe;
	}

	private static Holder<Enchantment> enchantment(GameTestHelper helper, net.minecraft.resources.ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	// ---------------------------------------------------------------------------------------------------------
	// attribute filter
	// ---------------------------------------------------------------------------------------------------------

	@GameTest
	public void attributeFilterMatchesByAttribute(GameTestHelper helper) {
		Level level = helper.getLevel();
		FilterItemStack damagedOnly = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(singleton(AllItemAttributeTypes.DAMAGED), false)));
		check(helper, damagedOnly.test(level, damaged(level)), "a damaged pickaxe passes a filter for damaged items");
		check(helper, !damagedOnly.test(level, new ItemStack(Items.IRON_PICKAXE)), "a new pickaxe does not");
		check(helper, !damagedOnly.test(level, new ItemStack(Items.APPLE)), "an apple does not");

		FilterItemStack undamaged = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(singleton(AllItemAttributeTypes.DAMAGED), true)));
		check(helper, !undamaged.test(level, damaged(level)), "the opposite attribute rejects the damaged pickaxe");
		check(helper, undamaged.test(level, new ItemStack(Items.IRON_PICKAXE)), "and takes the new one");

		FilterItemStack placeable = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(singleton(AllItemAttributeTypes.PLACEABLE), false)));
		check(helper, placeable.test(level, new ItemStack(Items.STONE)), "stone is placeable");
		check(helper, !placeable.test(level, new ItemStack(Items.STICK)), "a stick is not");

		FilterItemStack edible = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(singleton(AllItemAttributeTypes.CONSUMABLE), false)));
		check(helper, edible.test(level, new ItemStack(Items.BREAD)) && !edible.test(level, new ItemStack(Items.STONE)), "consumable");

		FilterItemStack stackable = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(singleton(AllItemAttributeTypes.NOT_STACKABLE), false)));
		check(helper, stackable.test(level, new ItemStack(Items.IRON_PICKAXE)) && !stackable.test(level, new ItemStack(Items.STONE)),
			"not stackable");

		FilterItemStack fuel = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(singleton(AllItemAttributeTypes.FURNACE_FUEL), false)));
		check(helper, fuel.test(level, new ItemStack(Items.COAL)) && !fuel.test(level, new ItemStack(Items.IRON_INGOT)), "furnace fuel");

		FilterItemStack compost = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(singleton(AllItemAttributeTypes.COMPOSTABLE), false)));
		check(helper, compost.test(level, new ItemStack(Items.WHEAT)) && !compost.test(level, new ItemStack(Items.IRON_INGOT)),
			"compostable");

		FilterItemStack smeltable = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(singleton(AllItemAttributeTypes.SMELTABLE), false)));
		check(helper, smeltable.test(level, new ItemStack(Items.RAW_IRON)) && !smeltable.test(level, new ItemStack(Items.STICK)),
			"smeltable through the recipe manager");

		FilterItemStack renamed = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(singleton(AllItemAttributeTypes.RENAMED), false)));
		ItemStack named = new ItemStack(Items.STICK);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Wand"));
		check(helper, renamed.test(level, named) && !renamed.test(level, new ItemStack(Items.STICK)), "renamed");

		FilterItemStack nameIs = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(new ItemNameAttribute("Wand"), false)));
		check(helper, nameIs.test(level, named) && !nameIs.test(level, new ItemStack(Items.STICK)), "the custom name attribute");
		done(helper);
	}

	@GameTest
	public void attributeFilterModes(GameTestHelper helper) {
		Level level = helper.getLevel();
		ItemAttributeEntry damaged = entry(singleton(AllItemAttributeTypes.DAMAGED), false);
		ItemAttributeEntry enchanted = entry(singleton(AllItemAttributeTypes.ENCHANTED), false);

		ItemStack damagedPickaxe = damaged(level);
		ItemStack both = damaged(level);
		both.enchant(enchantment(helper, Enchantments.EFFICIENCY), 1);
		ItemStack enchantedOnly = new ItemStack(Items.IRON_PICKAXE);
		enchantedOnly.enchant(enchantment(helper, Enchantments.EFFICIENCY), 1);
		ItemStack neither = new ItemStack(Items.IRON_PICKAXE);

		FilterItemStack any = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ, damaged, enchanted));
		check(helper, any.test(level, damagedPickaxe) && any.test(level, enchantedOnly) && any.test(level, both), "any: one is enough");
		check(helper, !any.test(level, neither), "any: none is not");

		FilterItemStack all = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_CONJ, damaged, enchanted));
		check(helper, all.test(level, both), "all: both attributes");
		check(helper, !all.test(level, damagedPickaxe) && !all.test(level, enchantedOnly) && !all.test(level, neither),
			"all: not with one missing");

		FilterItemStack none = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.BLACKLIST, damaged, enchanted));
		check(helper, none.test(level, neither), "deny: an item without the attributes");
		check(helper, !none.test(level, damagedPickaxe) && !none.test(level, enchantedOnly) && !none.test(level, both),
			"deny: any attribute rejects");

		// an empty list: allow-lists in "any" mode reject, the others accept (Create's final switch)
		FilterItemStack emptyAny = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ));
		check(helper, !emptyAny.test(level, neither), "an empty any-list rejects everything");
		FilterItemStack emptyDeny = FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.BLACKLIST));
		check(helper, emptyDeny.test(level, neither), "an empty deny-list accepts everything");

		// fluids are never matched by an attribute filter
		check(helper, !any.test(level, new FluidStack(Fluids.WATER, 1000)), "no fluid passes an attribute filter");
		done(helper);
	}

	@GameTest
	public void attributeTypesTestTheirData(GameTestHelper helper) {
		Level level = helper.getLevel();
		check(helper, new InTagAttribute(ItemTags.PLANKS).appliesTo(new ItemStack(Items.OAK_PLANKS), level), "planks tag");
		check(helper, !new InTagAttribute(ItemTags.PLANKS).appliesTo(new ItemStack(Items.STONE), level), "stone is no plank");
		check(helper, new ColorAttribute(DyeColor.RED).appliesTo(new ItemStack(Items.WOOL.pick(DyeColor.RED)), level), "red wool by its name");
		check(helper, new ColorAttribute(DyeColor.RED).appliesTo(new ItemStack(Items.DYE.pick(DyeColor.RED)), level), "red dye");
		check(helper, !new ColorAttribute(DyeColor.RED).appliesTo(new ItemStack(Items.WOOL.pick(DyeColor.BLUE)), level), "blue wool is not red");

		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
			new net.minecraft.server.network.Filterable<>("Title", java.util.Optional.empty()), "Steve", 1, List.of(), true));
		check(helper, new BookAuthorAttribute("Steve").appliesTo(book, level) && !new BookAuthorAttribute("Alex").appliesTo(book, level),
			"book author");
		check(helper, new BookCopyAttribute(1).appliesTo(book, level) && !new BookCopyAttribute(0).appliesTo(book, level),
			"book generation");

		ItemStack enchanted = new ItemStack(Items.IRON_PICKAXE);
		enchanted.enchant(enchantment(helper, Enchantments.EFFICIENCY), 1);
		check(helper, new EnchantAttribute(enchantment(helper, Enchantments.EFFICIENCY)).appliesTo(enchanted, level), "has enchant");
		check(helper, !new EnchantAttribute(enchantment(helper, Enchantments.UNBREAKING)).appliesTo(enchanted, level), "other enchant");

		ItemStack water = new ItemStack(Items.WATER_BUCKET);
		check(helper, new FluidContentsAttribute(Fluids.WATER).appliesTo(water, level), "a bucket of water contains water");
		check(helper, !new FluidContentsAttribute(Fluids.LAVA).appliesTo(water, level), "and no lava");

		ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
		check(helper, new ShulkerFillLevelAttribute(ShulkerLevels.EMPTY).appliesTo(shulker, level), "empty shulker box");
		shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE))));
		check(helper, new ShulkerFillLevelAttribute(ShulkerLevels.PARTIAL).appliesTo(shulker, level)
			&& !new ShulkerFillLevelAttribute(ShulkerLevels.EMPTY).appliesTo(shulker, level), "partially filled");
		check(helper, !new ShulkerFillLevelAttribute(ShulkerLevels.FULL).appliesTo(shulker, level), "not full");

		check(helper, AllItemAttributeTypes.WASHABLE.createAttribute().appliesTo(new ItemStack(Items.SAND), level)
			|| !AllItemAttributeTypes.WASHABLE.createAttribute().appliesTo(new ItemStack(Items.STICK), level),
			"washable only through the splashing recipes");
		check(helper, AllItemAttributeTypes.CRUSHABLE.createAttribute().appliesTo(new ItemStack(Items.COBBLESTONE), level)
			|| !AllItemAttributeTypes.CRUSHABLE.createAttribute().appliesTo(new ItemStack(Items.DIAMOND_SWORD), level),
			"crushable only through the crushing and milling recipes");
		done(helper);
	}

	@GameTest
	public void attributesOfAnItemListTheApplicableOnes(GameTestHelper helper) {
		Level level = helper.getLevel();
		ItemStack pickaxe = damaged(level);
		pickaxe.enchant(enchantment(helper, Enchantments.EFFICIENCY), 1);
		pickaxe.set(DataComponents.CUSTOM_NAME, Component.literal("Digger"));
		List<ItemAttribute> attributes = ItemAttribute.getAllAttributes(pickaxe, level);

		check(helper, attributes.contains(AllItemAttributeTypes.DAMAGED.createAttribute()), "damaged");
		check(helper, attributes.contains(AllItemAttributeTypes.ENCHANTED.createAttribute()), "enchanted");
		check(helper, attributes.contains(AllItemAttributeTypes.RENAMED.createAttribute()), "renamed");
		check(helper, attributes.contains(AllItemAttributeTypes.NOT_STACKABLE.createAttribute()), "not stackable");
		check(helper, !attributes.contains(AllItemAttributeTypes.PLACEABLE.createAttribute()), "a pickaxe is no block");
		check(helper, attributes.contains(new ItemNameAttribute("Digger")), "the custom name");
		check(helper, attributes.stream().anyMatch(a -> a instanceof EnchantAttribute), "the enchantment");
		check(helper, attributes.stream().anyMatch(a -> a instanceof InTagAttribute t && t.tag().equals(ItemTags.PICKAXES)),
			"the pickaxes tag");
		check(helper, attributes.stream().anyMatch(a -> a.getType() == AllItemAttributeTypes.ADDED_BY), "the mod it comes from");

		List<ItemAttribute> planks = ItemAttribute.getAllAttributes(new ItemStack(Items.OAK_PLANKS), level);
		check(helper, planks.contains(AllItemAttributeTypes.PLACEABLE.createAttribute()), "planks are placeable");
		check(helper, planks.contains(AllItemAttributeTypes.FURNACE_FUEL.createAttribute()), "planks burn");
		check(helper, planks.stream().anyMatch(a -> a instanceof InTagAttribute t && t.tag().equals(ItemTags.PLANKS)), "the planks tag");
		check(helper, planks.stream().anyMatch(a -> a.getType() == AllItemAttributeTypes.IN_ITEM_GROUP), "a creative tab of planks");
		check(helper, attributes.stream().noneMatch(a -> !a.appliesTo(pickaxe, level)), "all of them apply to the item");
		done(helper);
	}

	@GameTest
	public void attributeEntriesSurviveSerialization(GameTestHelper helper) {
		Level level = helper.getLevel();
		var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		List<ItemAttribute> samples = List.of(AllItemAttributeTypes.DAMAGED.createAttribute(), new InTagAttribute(ItemTags.PLANKS),
			new ColorAttribute(DyeColor.CYAN), new ItemNameAttribute("Digger"), new BookAuthorAttribute("Steve"),
			new BookCopyAttribute(2), new ShulkerFillLevelAttribute(ShulkerLevels.FULL), new FluidContentsAttribute(Fluids.WATER),
			new EnchantAttribute(enchantment(helper, Enchantments.EFFICIENCY)),
			new dev.createpolymer.logistics.item.filter.attribute.attributes.AddedByAttribute("minecraft"));
		for (ItemAttribute sample : samples) {
			ItemAttributeEntry original = entry(sample, sample instanceof ColorAttribute);
			DataResult<Tag> encoded = ItemAttributeEntry.CODEC.encodeStart(ops, original);
			check(helper, encoded.result().isPresent(), "encoding " + sample.getTranslationKey());
			ItemAttributeEntry back = ItemAttributeEntry.CODEC.parse(ops, encoded.result().get()).result().orElse(null);
			check(helper, back != null, "decoding " + sample.getTranslationKey());
			check(helper, back.inverted() == original.inverted(), "the inverted flag of " + sample.getTranslationKey());
			check(helper, back.attribute().getType() == sample.getType(), "the type of " + sample.getTranslationKey());
			check(helper, back.attribute().format(false).getString().equals(sample.format(false).getString()),
				"the text of " + sample.getTranslationKey());
		}

		// the data components of a filter item keep the list
		ItemStack filter = attributeFilter(AttributeFilterWhitelistMode.BLACKLIST, entry(new InTagAttribute(ItemTags.PLANKS), true));
		var stackCodec = ItemStack.CODEC;
		ItemStack back = stackCodec.parse(ops, stackCodec.encodeStart(ops, filter).result().get()).result().orElseThrow();
		FilterItemStack wrapped = FilterItemStack.of(back);
		check(helper, wrapped.test(level, new ItemStack(Items.OAK_PLANKS)), "a deny-list for 'not planks' lets planks through");
		check(helper, !wrapped.test(level, new ItemStack(Items.STONE)), "and rejects the rest");
		done(helper);
	}

	@GameTest
	public void attributeFilterSummaryAndRegistration(GameTestHelper helper) {
		ItemStack filter = attributeFilter(AttributeFilterWhitelistMode.WHITELIST_CONJ, entry(new InTagAttribute(ItemTags.PLANKS), false),
			entry(singleton(AllItemAttributeTypes.DAMAGED), true));
		List<Component> summary = ((FilterItem) filter.getItem()).makeSummary(filter);
		check(helper, summary.size() == 3, "the mode and both attributes: " + summary.size());
		check(helper, summary.get(0).getString().length() > 0, "the mode is named");
		check(helper, ToolsRegistry.ATTRIBUTE_FILTER.getFilterItems(attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ,
			entry(new InTagAttribute(ItemTags.PLANKS), false))).length > 3, "a tag filter lists the items of its tag");
		check(helper, ToolsRegistry.ATTRIBUTE_FILTER.getFilterItems(filter).length == 0, "other filters list nothing");
		check(helper, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ToolsRegistry.ATTRIBUTE_FILTER).getPath()
			.equals("attribute_filter"), "the id is Create's");
		done(helper);
	}

	@GameTest
	public void attributeFilterGuiPicksAttributes(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack filter = new ItemStack(ToolsRegistry.ATTRIBUTE_FILTER);
		player.setItemInHand(InteractionHand.MAIN_HAND, filter);
		AttributeFilterGui gui = new AttributeFilterGui(player, filter);

		check(helper, gui.getAttributesOfItem().isEmpty(), "nothing to choose from without a reference item");
		// the reference item goes into the ghost slot
		player.containerMenu.setCarried(damaged(helper.getLevel()));
		gui.onClick(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, !gui.getAttributesOfItem().isEmpty(), "the reference item has attributes");
		check(helper, player.containerMenu.getCarried().getItem() == Items.IRON_PICKAXE, "the cursor stack is untouched");
		check(helper, !filter.has(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES)
			|| filter.get(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES).isEmpty(), "the reference item is not part of the filter");

		// cycling goes round: the button moves forward on left clicks and back on right clicks
		int count = gui.getAttributesOfItem().size();
		check(helper, gui.getSelectedIndex() == 0, "starts at the first attribute");
		gui.getGuiElement(2).getGuiCallback().click(2, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
		check(helper, gui.getSelectedIndex() == 1, "left click goes forward");
		gui.getGuiElement(2).getGuiCallback().click(2, ClickType.MOUSE_RIGHT, ContainerInput.PICKUP, gui);
		check(helper, gui.getSelectedIndex() == 0, "right click goes back");
		gui.getGuiElement(2).getGuiCallback().click(2, ClickType.MOUSE_RIGHT, ContainerInput.PICKUP, gui);
		check(helper, gui.getSelectedIndex() == count - 1, "and round to the last");
		gui.selectAttribute(0);

		int damagedIndex = -1;
		for (int i = 0; i < count; i++)
			if (gui.getAttributesOfItem().get(i).getType() == AllItemAttributeTypes.DAMAGED)
				damagedIndex = i;
		check(helper, damagedIndex >= 0, "the pickaxe is damaged");
		gui.selectAttribute(damagedIndex);

		check(helper, gui.addSelectedAttribute(false), "the attribute is added");
		check(helper, !gui.addSelectedAttribute(true), "it cannot be added a second time");
		List<ItemAttributeEntry> saved = filter.get(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES);
		check(helper, saved != null && saved.size() == 1 && saved.get(0).attribute().getType() == AllItemAttributeTypes.DAMAGED
			&& !saved.get(0).inverted(), "saved into the filter item");
		check(helper, filter.get(ToolsRegistry.ATTRIBUTE_FILTER_WHITELIST_MODE) == AttributeFilterWhitelistMode.WHITELIST_DISJ,
			"the default mode");

		// the opposite of another attribute
		int enchantedIndex = -1;
		gui.selectAttribute(0);
		for (int i = 0; i < count; i++)
			if (gui.getAttributesOfItem().get(i).getType() == AllItemAttributeTypes.NOT_STACKABLE)
				enchantedIndex = i;
		gui.selectAttribute(enchantedIndex);
		check(helper, gui.addSelectedAttribute(true), "an opposite attribute");
		saved = filter.get(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES);
		check(helper, saved.size() == 2 && saved.get(1).inverted(), "with the inverted flag");

		FilterItemStack wrapped = FilterItemStack.of(filter);
		check(helper, wrapped.test(helper.getLevel(), damaged(helper.getLevel())), "a damaged pickaxe has one of the attributes");
		check(helper, !wrapped.test(helper.getLevel(), new ItemStack(Items.IRON_PICKAXE)), "a new pickaxe is not damaged and not stackable");
		check(helper, wrapped.test(helper.getLevel(), new ItemStack(Items.STONE)), "stone is not 'not stackable'");
		gui.setWhitelistMode(AttributeFilterWhitelistMode.BLACKLIST);
		check(helper, filter.get(ToolsRegistry.ATTRIBUTE_FILTER_WHITELIST_MODE) == AttributeFilterWhitelistMode.BLACKLIST, "mode saved");
		wrapped = FilterItemStack.of(filter);
		check(helper, !wrapped.test(helper.getLevel(), damaged(helper.getLevel())), "a damaged pickaxe is denied");

		gui.getGuiElement(26).getGuiCallback().click(26, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
		check(helper, filter.get(ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES).isEmpty(), "reset empties the list");
		done(helper);
	}

	// ---------------------------------------------------------------------------------------------------------
	// package filter
	// ---------------------------------------------------------------------------------------------------------

	private static ItemStack addressed(String address) {
		ItemStack box = PackageItem.containing(List.of(new ItemStack(Items.APPLE)));
		if (address != null)
			PackageItem.addAddress(box, address);
		return box;
	}

	private static ItemStack packageFilter(String address) {
		ItemStack filter = new ItemStack(ToolsRegistry.PACKAGE_FILTER);
		filter.set(PackagesRegistry.PACKAGE_ADDRESS, address);
		return filter;
	}

	@GameTest
	public void packageFilterMatchesByAddress(GameTestHelper helper) {
		Level level = helper.getLevel();
		FilterItemStack home = FilterItemStack.of(packageFilter("home"));
		check(helper, home.test(level, addressed("home")), "a package for home");
		check(helper, !home.test(level, addressed("work")), "a package for work");
		check(helper, !home.test(level, addressed(null)), "a package without an address");
		check(helper, !home.test(level, new ItemStack(Items.APPLE)), "an apple is no package");
		check(helper, !home.test(level, new FluidStack(Fluids.WATER, 1000)), "no fluid");

		FilterItemStack wildcard = FilterItemStack.of(packageFilter("ho*"));
		check(helper, wildcard.test(level, addressed("home")) && wildcard.test(level, addressed("hold")), "wildcards");
		check(helper, !wildcard.test(level, addressed("work")), "wildcards do not match everything");

		FilterItemStack any = FilterItemStack.of(packageFilter("*"));
		check(helper, any.test(level, addressed("anything")), "a lone star matches any address");

		FilterItemStack blank = FilterItemStack.of(packageFilter(" "));
		check(helper, blank.test(level, addressed(null)), "a blank address matches unaddressed packages");
		check(helper, !blank.test(level, addressed("home")), "and no others");

		// a list filter holding a package filter
		ItemStack list = new ItemStack(FilterRegistry.FILTER);
		list.set(FilterRegistry.FILTER_ITEMS, ItemContainerContents.fromItems(List.of(packageFilter("home"))));
		FilterItemStack wrapped = FilterItemStack.of(list);
		check(helper, wrapped.test(level, addressed("home")) && !wrapped.test(level, addressed("work")), "inside a list filter");
		done(helper);
	}

	@GameTest
	public void packageFilterGuiWritesTheAddress(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack filter = new ItemStack(ToolsRegistry.PACKAGE_FILTER);
		player.setItemInHand(InteractionHand.MAIN_HAND, filter);
		PackageFilterGui gui = new PackageFilterGui(player, filter);
		check(helper, gui.address.isEmpty(), "starts empty");
		gui.setAddress("storage");
		check(helper, PackageItem.getAddress(filter).equals("storage"), "written into the item");
		check(helper, FilterItemStack.of(filter).test(helper.getLevel(), addressed("storage")), "and used");
		check(helper, ((FilterItem) filter.getItem()).makeSummary(filter).size() == 1, "the summary shows it");
		gui.getGuiElement(26).getGuiCallback().click(26, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
		check(helper, PackageItem.getAddress(filter).isEmpty(), "reset clears it");
		check(helper, ((FilterItem) filter.getItem()).makeSummary(filter).isEmpty(), "and the summary");
		done(helper);
	}

	@GameTest
	public void packagesAreFilteredByTheirData(GameTestHelper helper) {
		Level level = helper.getLevel();
		// a plain filter holding a package matches any package, unless it respects the data
		ItemStack box = addressed("home");
		FilterItemStack plain = FilterItemStack.of(box);
		check(helper, plain.test(level, addressed("work")), "any package matches a package without data respect");
		check(helper, FilterItem.testDirect(box, addressed("work"), false), "testDirect without data");
		check(helper, !FilterItem.testDirect(box, addressed("work"), true), "testDirect with data");
		check(helper, FilterItem.testDirect(box, box.copy(), true), "an identical package");
		done(helper);
	}

	// ---------------------------------------------------------------------------------------------------------
	// fluid filter test API
	// ---------------------------------------------------------------------------------------------------------

	@GameTest
	public void filterItemStacksTestFluids(GameTestHelper helper) {
		Level level = helper.getLevel();
		FluidStack water = new FluidStack(Fluids.WATER, 1000);
		FluidStack lava = new FluidStack(Fluids.LAVA, 1000);

		check(helper, FilterItemStack.empty().test(level, water), "an empty filter matches any fluid");
		FilterItemStack bucket = FilterItemStack.of(new ItemStack(Items.WATER_BUCKET));
		check(helper, bucket.test(level, water), "a water bucket matches water");
		check(helper, !bucket.test(level, lava), "but not lava");
		check(helper, !bucket.test(level, FluidStack.EMPTY), "a filter matches no empty fluid");
		check(helper, bucket.fluid(level).getFluid() == Fluids.WATER, "the fluid it stands for");
		check(helper, !FilterItemStack.of(new ItemStack(Items.APPLE)).test(level, water), "an apple holds no fluid");

		// list filters: allow and deny
		ItemStack allow = new ItemStack(FilterRegistry.FILTER);
		allow.set(FilterRegistry.FILTER_ITEMS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.WATER_BUCKET))));
		FilterItemStack allowList = FilterItemStack.of(allow);
		check(helper, allowList.test(level, water) && !allowList.test(level, lava), "an allow-list of water");
		ItemStack deny = allow.copy();
		deny.set(FilterRegistry.FILTER_ITEMS_BLACKLIST, true);
		FilterItemStack denyList = FilterItemStack.of(deny);
		check(helper, !denyList.test(level, water) && denyList.test(level, lava), "a deny-list of water");

		// data: the fluid-aware test with and without matching components
		check(helper, bucket.test(level, water, false) && bucket.test(level, water, true), "plain fluid in both modes");

		// the other filters never match fluids
		check(helper, !FilterItemStack.of(attributeFilter(AttributeFilterWhitelistMode.BLACKLIST)).test(level, water),
			"attribute filter");
		check(helper, !FilterItemStack.of(packageFilter("a")).test(level, water), "package filter");
		done(helper);
	}

	// ---------------------------------------------------------------------------------------------------------
	// clipboard
	// ---------------------------------------------------------------------------------------------------------

	private static ClipboardContent content(boolean readOnly, ClipboardEntry... entries) {
		List<List<ClipboardEntry>> pages = new ArrayList<>();
		pages.add(new ArrayList<>(List.of(entries)));
		return new ClipboardContent(ClipboardType.WRITTEN, pages, readOnly);
	}

	private static ItemStack clipboardWith(ClipboardEntry... entries) {
		ItemStack clipboard = new ItemStack(ToolsRegistry.CLIPBOARD_ITEM);
		clipboard.set(ToolsRegistry.CLIPBOARD_CONTENT, content(false, entries));
		return clipboard;
	}

	@GameTest
	public void clipboardContentSerializes(GameTestHelper helper) {
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ClipboardEntry plain = new ClipboardEntry(true, Component.literal("buy iron"));
		ClipboardEntry icon = new ClipboardEntry(false, Component.literal("64 x Stone")).displayItem(new ItemStack(Items.STONE), 64);
		ClipboardContent content = content(true, plain, icon).setPreviouslyOpenedPage(0);
		net.minecraft.nbt.CompoundTag values = new net.minecraft.nbt.CompoundTag();
		values.putInt("Value", 3);
		ClipboardContent copied = content.setCopiedValues(values);
		Tag encoded = ClipboardContent.CODEC.encodeStart(ops, copied).result().orElseThrow();
		ClipboardContent back = ClipboardContent.CODEC.parse(ops, encoded).result().orElseThrow();
		check(helper, back.pages().size() == 1 && back.pages().get(0).size() == 2, "both entries");
		check(helper, back.readOnly() && back.type() == ClipboardType.WRITTEN, "read-only and written");
		check(helper, back.pages().get(0).get(0).equals(plain), "the plain entry");
		check(helper, back.pages().get(0).get(1).icon.is(Items.STONE) && back.pages().get(0).get(1).itemAmount == 64, "the icon");
		check(helper, back.pages().get(0).get(0).checked && !back.pages().get(0).get(1).checked, "the checks");
		check(helper, back.copiedValues().isPresent() && back.copiedValues().get().getIntOr("Value", 0) == 3, "the copied values");
		done(helper);
	}

	@GameTest
	public void clipboardItemModelFollowsItsType(GameTestHelper helper) {
		ClipboardBlockItem item = ToolsRegistry.CLIPBOARD_ITEM;
		ItemStack empty = new ItemStack(item);
		check(helper, item.getPolymerItemModel(empty, null, helper.getLevel().registryAccess()).getPath().equals("clipboard_0"),
			"empty");
		ItemStack written = clipboardWith(new ClipboardEntry(false, Component.literal("a")));
		check(helper, item.getPolymerItemModel(written, null, helper.getLevel().registryAccess()).getPath().equals("clipboard"),
			"written");
		written.set(ToolsRegistry.CLIPBOARD_CONTENT, written.get(ToolsRegistry.CLIPBOARD_CONTENT).setType(ClipboardType.EDITING));
		check(helper, item.getPolymerItemModel(written, null, helper.getLevel().registryAccess()).getPath().equals("clipboard_2"),
			"editing");
		done(helper);
	}

	@GameTest
	public void clipboardIsPlacedWithItsContent(GameTestHelper helper) {
		TestUtil.floor(helper);
		ServerPlayer player = player(helper);
		ItemStack clipboard = clipboardWith(new ClipboardEntry(false, Component.literal("note")));
		player.setItemInHand(InteractionHand.MAIN_HAND, clipboard);
		player.setShiftKeyDown(true);

		BlockPos floor = helper.absolutePos(new BlockPos(3, 0, 3));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false);
		InteractionResult result = clipboard.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, clipboard, hit));
		check(helper, result.consumesAction(), "sneaking places the clipboard: " + result);

		BlockPos pos = new BlockPos(3, 1, 3);
		BlockState state = helper.getBlockState(pos);
		check(helper, state.is(ToolsRegistry.CLIPBOARD), "the block is there: " + state);
		check(helper, state.getValue(ClipboardBlock.FACE) == AttachFace.FLOOR, "on the floor");
		check(helper, state.getValue(ClipboardBlock.WRITTEN), "written, as it has content");
		ClipboardBlockEntity be = helper.getBlockEntity(pos, ClipboardBlockEntity.class);
		List<List<ClipboardEntry>> pages = ClipboardEntry.readAll(be.components());
		check(helper, pages.size() == 1 && pages.get(0).get(0).text.getString().equals("note"), "the block entity has the entries");

		// taking it back keeps them
		ItemStack clone = state.getCloneItemStack(helper.getLevel(), helper.absolutePos(pos), true);
		check(helper, ClipboardEntry.readAll(clone).get(0).get(0).text.getString().equals("note"), "the picked item has the entries");
		List<ItemStack> drops = state.getDrops(new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(pos)))
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, ItemStack.EMPTY)
			.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, state)
			.withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY, be));
		check(helper, drops.size() == 1 && ClipboardEntry.readAll(drops.get(0)).size() == 1, "the drop has the entries");

		// shown as a plate model
		helper.runAfterDelay(4, () -> {
			var attachment = eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
			check(helper, attachment != null, "the clipboard has a model");
			boolean display = false;
			for (VirtualElement element : attachment.holder().getElements())
				display |= element instanceof ItemDisplayElement;
			check(helper, display, "an item display shows it");
			done(helper);
		});
	}

	@GameTest
	public void clipboardBlockCountsAsWrittenOnlyWithContent(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ToolsRegistry.CLIPBOARD.defaultBlockState().setValue(ClipboardBlock.FACE, AttachFace.FLOOR));
		ClipboardBlockEntity be = helper.getBlockEntity(pos, ClipboardBlockEntity.class);
		ServerPlayer player = player(helper);
		check(helper, !helper.getBlockState(pos).getValue(ClipboardBlock.WRITTEN), "an empty clipboard is not written");

		var map = new net.minecraft.core.component.PatchedDataComponentMap(be.components());
		map.set(ToolsRegistry.CLIPBOARD_CONTENT, content(false, new ClipboardEntry(false, Component.literal("x"))));
		be.setComponents(map);
		be.onEditedBy(player);
		check(helper, helper.getBlockState(pos).getValue(ClipboardBlock.WRITTEN), "written once it has content");
		check(helper, be.getLastEdit().equals(player.getUUID()), "remembers the editor");

		map = new net.minecraft.core.component.PatchedDataComponentMap(be.components());
		map.remove(ToolsRegistry.CLIPBOARD_CONTENT);
		be.setComponents(map);
		be.onEditedBy(player);
		check(helper, !helper.getBlockState(pos).getValue(ClipboardBlock.WRITTEN), "and not any more when cleared");
		done(helper);
	}

	@GameTest
	public void clipboardGuiChecksAndErasesEntries(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack clipboard = clipboardWith(new ClipboardEntry(false, Component.literal("one")),
			new ClipboardEntry(false, Component.literal("two")), new ClipboardEntry(false, Component.literal("  ")));
		player.getInventory().setItem(0, clipboard);
		player.getInventory().setSelectedSlot(0);

		ClipboardGui gui = ClipboardGui.open(player, ClipboardGui.Target.item(player, 0));
		check(helper, gui.isOpen(), "the editor opened");

		// first entry: checkbox in slot 0, text in slot 1
		gui.getGuiElement(0).getGuiCallback().click(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
		check(helper, ClipboardEntry.readAll(clipboard).get(0).get(0).checked, "the first entry is checked");
		check(helper, clipboard.get(ToolsRegistry.CLIPBOARD_CONTENT).type() == ClipboardType.EDITING, "the quill is out while editing");
		// right click on the text removes the second entry
		gui.getGuiElement(10).getGuiCallback().click(10, ClickType.MOUSE_RIGHT, ContainerInput.PICKUP, gui);
		List<ClipboardEntry> entries = ClipboardEntry.readAll(clipboard).get(0);
		check(helper, entries.size() == 2 && entries.get(1).text.getString().isBlank(), "the second entry is gone: " + entries.size());
		// erase checked: the sponge
		gui.getGuiElement(34).getGuiCallback().click(34, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
		entries = ClipboardEntry.readAll(clipboard).get(0);
		check(helper, entries.size() == 1, "the checked entry was erased");

		// closing drops the blank entry, and with nothing left the clipboard is empty again
		gui.onRemoved();
		check(helper, !clipboard.has(ToolsRegistry.CLIPBOARD_CONTENT), "nothing left: no content");
		done(helper);
	}

	@GameTest
	public void clipboardGuiKeepsWrittenContent(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack clipboard = clipboardWith(new ClipboardEntry(false, Component.literal("one")));
		player.getInventory().setItem(0, clipboard);
		player.getInventory().setSelectedSlot(0);
		ClipboardGui gui = ClipboardGui.open(player, ClipboardGui.Target.item(player, 0));
		gui.getGuiElement(0).getGuiCallback().click(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
		gui.onRemoved();
		ClipboardContent content = clipboard.get(ToolsRegistry.CLIPBOARD_CONTENT);
		check(helper, content != null && content.type() == ClipboardType.WRITTEN, "written again after editing");
		check(helper, content.pages().get(0).get(0).checked, "the check stays");
		done(helper);
	}

	private static FilteringBehaviour filtering(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, FunnelBlockEntity.class).getBehaviour(FilteringBehaviour.TYPE);
	}

	@GameTest
	public void clipboardCopiesAndPastesAFunnelFilter(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos source = new BlockPos(2, 1, 2);
		BlockPos target = new BlockPos(5, 1, 2);
		for (BlockPos pos : List.of(source, target))
			helper.setBlock(pos, FunnelRegistry.BRASS_FUNNEL.defaultBlockState().setValue(FunnelBlock.FACING, Direction.NORTH));
		filtering(helper, source).setFilter(new ItemStack(Items.IRON_INGOT));

		ServerPlayer player = player(helper);
		ItemStack clipboard = new ItemStack(ToolsRegistry.CLIPBOARD_ITEM);
		player.setItemInHand(InteractionHand.MAIN_HAND, clipboard);

		// nothing is copied yet, so pasting does nothing
		InteractionResult pasteNothing = ClipboardValueSettingsHandler.leftClickToPaste(player, helper.getLevel(), InteractionHand.MAIN_HAND,
			helper.absolutePos(target), Direction.NORTH);
		check(helper, pasteNothing == InteractionResult.PASS, "nothing to paste");
		check(helper, filtering(helper, target).getFilter().isEmpty(), "the other funnel is untouched");

		InteractionResult copy = ClipboardValueSettingsHandler.rightClickToCopy(player, helper.getLevel(), InteractionHand.MAIN_HAND,
			helper.absolutePos(source), Direction.NORTH);
		check(helper, copy == InteractionResult.SUCCESS, "the copy consumes the click: " + copy);
		ClipboardContent content = clipboard.get(ToolsRegistry.CLIPBOARD_CONTENT);
		check(helper, content != null && content.copiedValues().isPresent(), "the clipboard keeps the copied values");
		check(helper, content.copiedValues().get().contains("Filtering"), "under the filtering key");
		check(helper, content.type() == ClipboardType.WRITTEN, "it counts as written now");

		InteractionResult paste = ClipboardValueSettingsHandler.leftClickToPaste(player, helper.getLevel(), InteractionHand.MAIN_HAND,
			helper.absolutePos(target), Direction.NORTH);
		check(helper, paste == InteractionResult.SUCCESS, "the paste consumes the click: " + paste);
		check(helper, filtering(helper, target).getFilter().is(Items.IRON_INGOT), "the filter was pasted");

		// sneaking leaves the clicks alone
		player.setShiftKeyDown(true);
		check(helper, ClipboardValueSettingsHandler.rightClickToCopy(player, helper.getLevel(), InteractionHand.MAIN_HAND,
			helper.absolutePos(source), Direction.NORTH) == InteractionResult.PASS, "sneak places the clipboard instead");

		// a block with nothing to copy lets the click through
		player.setShiftKeyDown(false);
		helper.setBlock(new BlockPos(3, 1, 5), Blocks.STONE);
		check(helper, ClipboardValueSettingsHandler.rightClickToCopy(player, helper.getLevel(), InteractionHand.MAIN_HAND,
			helper.absolutePos(new BlockPos(3, 1, 5)), Direction.UP) == InteractionResult.PASS, "stone has no settings");
		done(helper);
	}

	@GameTest
	public void clipboardPastesFilterItemsFromTheInventory(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos source = new BlockPos(2, 1, 2);
		BlockPos target = new BlockPos(5, 1, 2);
		for (BlockPos pos : List.of(source, target))
			helper.setBlock(pos, FunnelRegistry.BRASS_FUNNEL.defaultBlockState().setValue(FunnelBlock.FACING, Direction.NORTH));
		ItemStack listFilter = new ItemStack(FilterRegistry.FILTER);
		listFilter.set(FilterRegistry.FILTER_ITEMS, ItemContainerContents.fromItems(List.of(new ItemStack(Items.GOLD_INGOT))));
		filtering(helper, source).setFilter(listFilter);

		ServerPlayer player = player(helper);
		ItemStack clipboard = new ItemStack(ToolsRegistry.CLIPBOARD_ITEM);
		player.setItemInHand(InteractionHand.MAIN_HAND, clipboard);
		ClipboardValueSettingsHandler.rightClickToCopy(player, helper.getLevel(), InteractionHand.MAIN_HAND, helper.absolutePos(source),
			Direction.NORTH);

		// in survival the copied filter item has to come from the inventory
		ClipboardValueSettingsHandler.leftClickToPaste(player, helper.getLevel(), InteractionHand.MAIN_HAND, helper.absolutePos(target),
			Direction.NORTH);
		check(helper, filtering(helper, target).getFilter().isEmpty(), "no filter item in the inventory, no paste");

		player.getInventory().setItem(5, new ItemStack(FilterRegistry.FILTER, 2));
		ClipboardValueSettingsHandler.leftClickToPaste(player, helper.getLevel(), InteractionHand.MAIN_HAND, helper.absolutePos(target),
			Direction.NORTH);
		ItemStack set = filtering(helper, target).getFilter();
		check(helper, set.getItem() == FilterRegistry.FILTER, "the filter was pasted");
		check(helper, FilterItemStack.of(set).test(helper.getLevel(), new ItemStack(Items.GOLD_INGOT)), "with its contents");
		check(helper, player.getInventory().getItem(5).getCount() == 1, "one filter item was used");
		done(helper);
	}

	@GameTest
	public void clipboardCopiesEntriesFromAPlacedClipboard(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ToolsRegistry.CLIPBOARD.defaultBlockState().setValue(ClipboardBlock.FACE, AttachFace.FLOOR));
		ClipboardBlockEntity be = helper.getBlockEntity(pos, ClipboardBlockEntity.class);
		var map = new net.minecraft.core.component.PatchedDataComponentMap(be.components());
		map.set(ToolsRegistry.CLIPBOARD_CONTENT, content(false, new ClipboardEntry(true, Component.literal("alpha")),
			new ClipboardEntry(false, Component.literal("beta"))));
		be.setComponents(map);

		ServerPlayer player = player(helper);
		ItemStack clipboard = clipboardWith(new ClipboardEntry(false, Component.literal("beta")));
		player.setItemInHand(InteractionHand.MAIN_HAND, clipboard);
		InteractionResult result = ClipboardValueSettingsHandler.rightClickToCopy(player, helper.getLevel(), InteractionHand.MAIN_HAND,
			helper.absolutePos(pos), Direction.UP);
		check(helper, result == InteractionResult.SUCCESS, "consumed");
		List<ClipboardEntry> entries = ClipboardEntry.readAll(clipboard).get(0);
		check(helper, entries.size() == 2, "only the missing entry was added: " + entries.size());
		check(helper, entries.get(1).text.getString().equals("alpha") && entries.get(1).checked, "alpha, checked");
		done(helper);
	}

	@GameTest(maxTicks = 40)
	public void packagePortsWriteTheirAddressOnAClipboard(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, PackagesRegistry.PACKAGE_FROGPORT);
		helper.runAfterDelay(3, () -> {
			FrogportBlockEntity port = helper.getBlockEntity(pos, FrogportBlockEntity.class);
			ServerPlayer player = player(helper);
			ItemStack clipboard = new ItemStack(ToolsRegistry.CLIPBOARD_ITEM);
			player.setItemInHand(InteractionHand.MAIN_HAND, clipboard);

			// no address, nothing to write
			check(helper, port.use(player) == InteractionResult.SUCCESS, "the click is used");
			check(helper, !clipboard.has(ToolsRegistry.CLIPBOARD_CONTENT), "a port without an address writes nothing");

			port.applySettings("depot", true);
			port.use(player);
			ClipboardContent content = clipboard.get(ToolsRegistry.CLIPBOARD_CONTENT);
			check(helper, content != null && content.type() == ClipboardType.WRITTEN, "the clipboard is written");
			check(helper, content.pages().get(0).get(0).text.getString().equals("#depot"), "with the address as an entry");

			port.use(player);
			check(helper, ClipboardEntry.readAll(clipboard).get(0).size() == 1, "the same address is not added twice");

			port.applySettings("home", true);
			port.use(player);
			check(helper, ClipboardEntry.readAll(clipboard).get(0).size() == 2, "another address is");

			// a full page makes a new one
			for (int i = 0; i < 12; i++) {
				port.applySettings("port" + i, true);
				port.use(player);
			}
			List<List<ClipboardEntry>> pages = ClipboardEntry.readAll(clipboard);
			check(helper, pages.size() == 2 && pages.get(0).size() == 12, "pages of twelve: " + pages.size());

			// without a clipboard the port opens its screen
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			port.use(player);
			check(helper, player.containerMenu != player.inventoryMenu, "the port opened its screen");
			done(helper);
		});
	}

	// ---------------------------------------------------------------------------------------------------------
	// linked controller
	// ---------------------------------------------------------------------------------------------------------

	private static ItemStack controller(ItemStack... frequencies) {
		ItemStack controller = new ItemStack(ToolsRegistry.LINKED_CONTROLLER);
		List<ItemStack> stacks = new ArrayList<>(List.of(frequencies));
		while (stacks.size() < 12)
			stacks.add(ItemStack.EMPTY);
		controller.set(ToolsRegistry.LINKED_CONTROLLER_ITEMS, ItemContainerContents.fromItems(stacks));
		return controller;
	}

	private static BlockState link(boolean receiver) {
		return RedstoneRegistry.REDSTONE_LINK.defaultBlockState()
			.setValue(RedstoneLinkBlock.FACING, Direction.UP)
			.setValue(RedstoneLinkBlock.RECEIVER, receiver);
	}

	private static void setFrequency(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item first, net.minecraft.world.item.Item second) {
		LinkBehaviour behaviour = helper.getBlockEntity(pos, RedstoneLinkBlockEntity.class).getLink();
		behaviour.setFrequency(true, new ItemStack(first));
		behaviour.setFrequency(false, new ItemStack(second));
	}

	private static int received(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos, RedstoneLinkBlockEntity.class).getReceivedSignal();
	}

	private static void hold(ServerPlayer player, boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift) {
		player.setLastClientInput(new Input(forward, backward, left, right, jump, shift, false));
	}

	@GameTest(maxTicks = 100)
	public void linkedControllerTransmitsHeldKeys(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos matching = new BlockPos(5, 1, 5);
		BlockPos other = new BlockPos(2, 1, 5);
		helper.setBlock(matching, link(true));
		helper.setBlock(other, link(true));
		helper.runAfterDelay(2, () -> {
			setFrequency(helper, matching, Items.IRON_INGOT, Items.GOLD_INGOT);
			setFrequency(helper, other, Items.COPPER_INGOT, Items.GOLD_INGOT);
			ServerPlayer player = player(helper);
			// W is button 0, A (left) is button 2
			ItemStack controller = controller(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), ItemStack.EMPTY,
				ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.GOLD_INGOT));
			player.setItemInHand(InteractionHand.MAIN_HAND, controller);

			check(helper, LinkedControllerHandler.getMode(player) == Mode.IDLE, "idle at first");
			hold(player, true, false, false, false, false, false);
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			check(helper, received(helper, matching) == 0, "nothing is sent while the controller is off");

			controller.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			check(helper, LinkedControllerHandler.getMode(player) == Mode.ACTIVE, "right-click turns it on");
			check(helper, player.getAttribute(Attributes.MOVEMENT_SPEED).getValue() == 0, "the player is held still while it is on");

			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			check(helper, received(helper, matching) == 15, "W transmits button 0: " + received(helper, matching));
			check(helper, received(helper, other) == 0, "the other frequency stays off");

			// the keys held count again and again: still on after the timeout would have run out
			for (int i = 0; i < 40; i++) {
				LinkedControllerServerHandler.tick(helper.getLevel());
				LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			}
			check(helper, received(helper, matching) == 15, "held keys keep the signal");

			// let go of W and press A, which is button 2
			hold(player, false, false, true, false, false, false);
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			LinkedControllerServerHandler.tick(helper.getLevel());
			check(helper, received(helper, matching) == 0, "letting go ends the signal");
			check(helper, received(helper, other) == 15, "A is button 2: " + received(helper, other));

			// space is button 4, which has no frequencies; letting go of A ends its signal
			hold(player, false, false, false, false, true, false);
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			LinkedControllerServerHandler.tick(helper.getLevel());
			check(helper, received(helper, other) == 0, "space transmits nothing");
			check(helper, received(helper, matching) == 0, "and W is still up");
			done(helper);
		});
	}

	@GameTest(maxTicks = 100)
	public void linkedControllerStopsWhenPutAwayOrToggledOff(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos matching = new BlockPos(5, 1, 5);
		helper.setBlock(matching, link(true));
		helper.runAfterDelay(2, () -> {
			setFrequency(helper, matching, Items.IRON_INGOT, Items.GOLD_INGOT);
			ServerPlayer player = player(helper);
			ItemStack controller = controller(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT));
			player.setItemInHand(InteractionHand.MAIN_HAND, controller);
			controller.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			hold(player, true, false, false, false, false, false);
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			check(helper, received(helper, matching) == 15, "on");

			// toggling it off lets go of the buttons
			player.getCooldowns().removeCooldown(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(controller.getItem()));
			controller.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			check(helper, LinkedControllerHandler.getMode(player) == Mode.IDLE, "right-click again turns it off");
			check(helper, player.getAttribute(Attributes.MOVEMENT_SPEED).getValue() > 0, "the player can move again");
			LinkedControllerServerHandler.tick(helper.getLevel());
			check(helper, received(helper, matching) == 0, "the signal ends with it");

			// the off-hand works when there is none in the main hand, and the controller put away stops it
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.OFF_HAND, controller);
			LinkedControllerHandler.toggle(player);
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			check(helper, received(helper, matching) == 15, "the off-hand controller works");
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			check(helper, LinkedControllerHandler.getMode(player) == Mode.IDLE, "put away: off");
			LinkedControllerServerHandler.tick(helper.getLevel());
			check(helper, received(helper, matching) == 0, "and the signal is gone");
			done(helper);
		});
	}

	@GameTest(maxTicks = 60)
	public void linkedControllerBindsAFrequencyToAKey(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos link = new BlockPos(3, 1, 3);
		helper.setBlock(link, link(true));
		helper.runAfterDelay(2, () -> {
			setFrequency(helper, link, Items.DIAMOND, Items.EMERALD);
			ServerPlayer player = player(helper);
			ItemStack controller = controller();
			player.setItemInHand(InteractionHand.MAIN_HAND, controller);

			LinkedControllerHandler.toggleBindMode(player, helper.absolutePos(link));
			check(helper, LinkedControllerHandler.getMode(player) == Mode.BIND, "bind mode");
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			check(helper, LinkedControllerHandler.getMode(player) == Mode.BIND, "waiting for a key");

			hold(player, false, false, false, false, false, true);
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			check(helper, LinkedControllerHandler.getMode(player) == Mode.IDLE, "bound: back to idle");
			ItemStackHandler items = LinkedControllerItem.getFrequencyItems(controller);
			check(helper, items.getStackInSlot(10).is(Items.DIAMOND) && items.getStackInSlot(11).is(Items.EMERALD),
				"sneak is button 5 and gets the frequency");
			check(helper, items.getStackInSlot(0).isEmpty(), "the other buttons are untouched");
			check(helper, player.getAttribute(Attributes.MOVEMENT_SPEED).getValue() > 0, "the player is free again");
			done(helper);
		});
	}

	@GameTest
	public void linkedControllerGuiSetsFrequencies(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack controller = controller();
		player.setItemInHand(InteractionHand.MAIN_HAND, controller);
		LinkedControllerGui gui = new LinkedControllerGui(player, controller);

		// column 2 (A), first frequency in the row below the labels, second in the row after
		player.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT, 3));
		gui.onClick(9 + 2, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		player.containerMenu.setCarried(new ItemStack(Items.GOLD_INGOT, 3));
		gui.onClick(18 + 2, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, player.containerMenu.getCarried().getCount() == 3, "the cursor is not used up");

		ItemStackHandler items = LinkedControllerItem.getFrequencyItems(controller);
		check(helper, items.getStackInSlot(4).is(Items.IRON_INGOT) && items.getStackInSlot(5).is(Items.GOLD_INGOT),
			"button 2 has both frequencies");
		check(helper, items.getStackInSlot(0).isEmpty(), "button 0 has none");
		check(helper, LinkedControllerItem.toFrequency(controller, 2).getFirst().getStack().is(Items.IRON_INGOT), "toFrequency");

		// the labels are not slots
		player.containerMenu.setCarried(new ItemStack(Items.COAL));
		gui.onClick(2, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, LinkedControllerItem.getFrequencyItems(controller).getStackInSlot(0).isEmpty(), "a label takes no items");

		// clearing a slot
		player.containerMenu.setCarried(ItemStack.EMPTY);
		gui.onClick(9 + 2, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, LinkedControllerItem.getFrequencyItems(controller).getStackInSlot(4).isEmpty(), "an empty cursor clears it");
		done(helper);
	}

	@GameTest
	public void linkedControllerItemAsksForItsOwnModel(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack controller = controller();
		player.setItemInHand(InteractionHand.MAIN_HAND, controller);
		check(helper, !LinkedControllerHandler.isActive(player, controller), "not active at first");
		LinkedControllerHandler.toggle(player);
		check(helper, LinkedControllerHandler.isActive(player, controller), "active while on");
		check(helper, !LinkedControllerHandler.isActive(player, new ItemStack(Items.STICK)), "other items are not");
		LinkedControllerHandler.toggle(player);
		check(helper, !LinkedControllerHandler.isActive(player, controller), "off again");
		done(helper);
	}

	// ---------------------------------------------------------------------------------------------------------
	// lectern controller
	// ---------------------------------------------------------------------------------------------------------

	private static BlockState lectern() {
		return Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH);
	}

	@GameTest(maxTicks = 100)
	public void lecternControllerUsesTheControllerOnIt(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos lecternPos = new BlockPos(3, 1, 3);
		BlockPos receiver = new BlockPos(6, 1, 6);
		helper.setBlock(lecternPos, lectern());
		helper.setBlock(receiver, link(true));
		helper.runAfterDelay(2, () -> {
			setFrequency(helper, receiver, Items.IRON_INGOT, Items.GOLD_INGOT);
			ServerPlayer player = player(helper);
			ItemStack controller = controller(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT));
			BlockPos abs = helper.absolutePos(lecternPos);
			player.setPos(abs.getX() + .5, abs.getY(), abs.getZ() + 1.0);

			ToolsRegistry.LECTERN_CONTROLLER.replaceLectern(helper.getBlockState(lecternPos), helper.getLevel(), abs, controller.copy());
			check(helper, helper.getBlockState(lecternPos).is(ToolsRegistry.LECTERN_CONTROLLER), "the lectern holds a controller");
			check(helper, helper.getBlockState(lecternPos).getValue(LecternBlock.FACING) == Direction.NORTH, "facing kept");
			LecternControllerBlockEntity be = helper.getBlockEntity(lecternPos, LecternControllerBlockEntity.class);
			check(helper, LinkedControllerItem.toFrequency(be.getController(), 0).getFirst().getStack().is(Items.IRON_INGOT),
				"the controller keeps its frequencies");

			// the vanilla client sees a lectern
			BlockState shown = ((eu.pb4.polymer.core.api.block.PolymerBlock) ToolsRegistry.LECTERN_CONTROLLER)
				.getPolymerBlockState(helper.getBlockState(lecternPos), null);
			check(helper, shown.is(Blocks.LECTERN), "players see a lectern: " + shown);

			// starting to use it
			be.tryStartUsing(player);
			check(helper, be.isUsedBy(player), "the player uses the lectern");
			check(helper, LinkedControllerHandler.getMode(player) == Mode.ACTIVE, "controls taken over");
			check(helper, LinkedControllerHandler.session(player).lecternPos != null, "in the lectern");

			hold(player, true, false, false, false, false, false);
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			check(helper, received(helper, receiver) == 15, "W through the lectern: " + received(helper, receiver));

			// another player cannot take over
			ServerPlayer other = player(helper);
			other.setPos(abs.getX() + .5, abs.getY(), abs.getZ() + 1.0);
			be.tryStartUsing(other);
			check(helper, !be.isUsedBy(other) && be.isUsedBy(player), "the lectern has one user");

			// stopping
			be.tryStopUsing(player);
			check(helper, !be.hasUser(), "no user");
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			check(helper, LinkedControllerHandler.getMode(player) == Mode.IDLE, "the controls are free");
			LinkedControllerServerHandler.tick(helper.getLevel());
			check(helper, received(helper, receiver) == 0, "the signal ended");
			done(helper);
		});
	}

	@GameTest(maxTicks = 60)
	public void lecternControllerIsDrawnWithItsButtons(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos lecternPos = new BlockPos(3, 1, 3);
		helper.setBlock(lecternPos, lectern());
		BlockPos abs = helper.absolutePos(lecternPos);
		ToolsRegistry.LECTERN_CONTROLLER.replaceLectern(helper.getBlockState(lecternPos), helper.getLevel(),
			abs, controller(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)));
		helper.runAfterDelay(4, () -> {
			var attachment = eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment.get(helper.getLevel(), abs);
			check(helper, attachment != null, "the lectern controller has a model");
			List<ItemDisplayElement> displays = new ArrayList<>();
			for (VirtualElement element : attachment.holder().getElements())
				if (element instanceof ItemDisplayElement display)
					displays.add(display);
			check(helper, displays.size() == 7, "the controller and its six buttons: " + displays.size());
			var before = displays.get(0).getItem().get(DataComponents.ITEM_MODEL);

			ServerPlayer player = player(helper);
			player.setPos(abs.getX() + .5, abs.getY(), abs.getZ() + 1.0);
			helper.getBlockEntity(lecternPos, LecternControllerBlockEntity.class).tryStartUsing(player);
			hold(player, true, false, false, false, false, false);
			LinkedControllerHandler.tick(player, LinkedControllerHandler.session(player));
			helper.runAfterDelay(3, () -> {
				var after = displays.get(0).getItem().get(DataComponents.ITEM_MODEL);
				check(helper, !before.equals(after), "the controller is lit while somebody uses it: " + before + " / " + after);
				done(helper);
			});
		});
	}

	@GameTest(maxTicks = 100)
	public void lecternControllerIsGivenBackAndDropsWhenBroken(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos lecternPos = new BlockPos(3, 1, 3);
		helper.setBlock(lecternPos, lectern());
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = player(helper);
			ItemStack controller = controller(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT));
			BlockPos abs = helper.absolutePos(lecternPos);
			ToolsRegistry.LECTERN_CONTROLLER.replaceLectern(helper.getBlockState(lecternPos), helper.getLevel(), abs, controller.copy());

			// sneak-use retrieves it as a plain lectern with the controller on the ground
			player.setShiftKeyDown(true);
			InteractionResult result = helper.getBlockState(lecternPos).useItemOn(ItemStack.EMPTY, helper.getLevel(), player,
				InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
			check(helper, result == InteractionResult.SUCCESS, "sneaking takes the controller back");
			check(helper, helper.getBlockState(lecternPos).is(Blocks.LECTERN), "an ordinary lectern again");
			var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(2));
			check(helper, drops.size() == 1 && drops.get(0).getItem().getItem() == ToolsRegistry.LINKED_CONTROLLER, "the controller dropped");
			check(helper, LinkedControllerItem.toFrequency(drops.get(0).getItem(), 0).getFirst().getStack().is(Items.IRON_INGOT),
				"with its frequencies");
			drops.forEach(ItemEntity::discard);

			// swapping controllers
			ToolsRegistry.LECTERN_CONTROLLER.replaceLectern(helper.getBlockState(lecternPos), helper.getLevel(), abs, controller.copy());
			ItemStack other = controller(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD));
			player.setShiftKeyDown(false);
			player.setItemInHand(InteractionHand.MAIN_HAND, other);
			helper.getBlockEntity(lecternPos, LecternControllerBlockEntity.class).swapControllers(other, player, InteractionHand.MAIN_HAND,
				helper.getBlockState(lecternPos));
			check(helper, LinkedControllerItem.toFrequency(player.getMainHandItem(), 0).getFirst().getStack().is(Items.IRON_INGOT),
				"the old controller is in the hand");
			check(helper, LinkedControllerItem.toFrequency(helper.getBlockEntity(lecternPos, LecternControllerBlockEntity.class).getController(), 0)
				.getFirst().getStack().is(Items.DIAMOND), "the new one is on the lectern");

			// breaking the block drops the controller too
			helper.setBlock(lecternPos, Blocks.AIR);
			drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(2));
			check(helper, drops.size() == 1 && drops.get(0).getItem().getItem() == ToolsRegistry.LINKED_CONTROLLER, "broken: it dropped");
			done(helper);
		});
	}

	@GameTest
	public void lecternControllerKeepsItsControllerWhenSaved(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos lecternPos = new BlockPos(3, 1, 3);
		helper.setBlock(lecternPos, lectern());
		ItemStack controller = controller(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT));
		BlockPos abs = helper.absolutePos(lecternPos);
		ToolsRegistry.LECTERN_CONTROLLER.replaceLectern(helper.getBlockState(lecternPos), helper.getLevel(), abs, controller);
		LecternControllerBlockEntity be = helper.getBlockEntity(lecternPos, LecternControllerBlockEntity.class);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		be.saveWithoutMetadata(output);
		LecternControllerBlockEntity copy = new LecternControllerBlockEntity(ToolsRegistry.LECTERN_CONTROLLER_BLOCK_ENTITY, abs,
			helper.getBlockState(lecternPos));
		copy.setLevel(helper.getLevel());
		copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
		check(helper, LinkedControllerItem.toFrequency(copy.getController(), 0).getSecond().getStack().is(Items.GOLD_INGOT),
			"the frequencies are saved");
		done(helper);
	}

	// ---------------------------------------------------------------------------------------------------------
	// crafting blueprint
	// ---------------------------------------------------------------------------------------------------------

	/** A 1x1 blueprint on the south face of a stone block, in a spot only that fits. */
	private static BlueprintEntity blueprint(GameTestHelper helper, BlockPos stone) {
		helper.setBlock(stone, Blocks.STONE);
		BlockPos front = helper.absolutePos(stone).relative(Direction.SOUTH);
		BlueprintEntity entity = new BlueprintEntity(ToolsRegistry.CRAFTING_BLUEPRINT_ENTITY, helper.getLevel(), front, Direction.SOUTH,
			Direction.DOWN);
		helper.getLevel().addFreshEntity(entity);
		return entity;
	}

	private static void sticksRecipe(BlueprintSection section, ItemStack top, ItemStack bottom) {
		ItemStackHandler items = section.getItems();
		items.setStackInSlot(0, top);
		items.setStackInSlot(3, bottom);
		items.setStackInSlot(9, new ItemStack(Items.STICK, 4));
		section.save(items);
	}

	@GameTest(maxTicks = 60)
	public void blueprintCraftsFromTheInventory(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlueprintEntity blueprint = blueprint(helper, new BlockPos(3, 1, 3));
		check(helper, blueprint.getSize() == 1, "a lone stone block fits a small blueprint");
		check(helper, blueprint.isAlive(), "in the world");
		BlueprintSection section = blueprint.getSection(0);
		sticksRecipe(section, new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS));

		ServerPlayer player = player(helper);
		player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 5));
		InteractionResult result = blueprint.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
		check(helper, result == InteractionResult.SUCCESS, "used");
		check(helper, count(player, Items.STICK) == 4, "one craft gives four sticks: " + count(player, Items.STICK));
		check(helper, count(player, Items.OAK_PLANKS) == 3, "and takes two planks: " + count(player, Items.OAK_PLANKS));

		// sneaking crafts as long as the inventory lasts
		player.setShiftKeyDown(true);
		blueprint.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
		check(helper, count(player, Items.STICK) == 8, "sneaking crafts the rest: " + count(player, Items.STICK));
		check(helper, count(player, Items.OAK_PLANKS) == 1, "one plank is left: " + count(player, Items.OAK_PLANKS));

		// not enough ingredients: nothing is taken, no screen is needed
		blueprint.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
		check(helper, count(player, Items.STICK) == 8 && count(player, Items.OAK_PLANKS) == 1, "nothing changes without ingredients");
		done(helper);
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++)
			if (player.getInventory().getItem(i).is(item))
				count += player.getInventory().getItem(i).getCount();
		return count;
	}

	@GameTest(maxTicks = 60)
	public void blueprintIngredientsCanBeFilters(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlueprintEntity blueprint = blueprint(helper, new BlockPos(3, 1, 3));
		ItemStack planks = attributeFilter(AttributeFilterWhitelistMode.WHITELIST_DISJ, entry(new InTagAttribute(ItemTags.PLANKS), false));
		sticksRecipe(blueprint.getSection(0), planks, planks.copy());

		ServerPlayer player = player(helper);
		player.getInventory().add(new ItemStack(Items.BIRCH_PLANKS, 1));
		player.getInventory().add(new ItemStack(Items.DARK_OAK_PLANKS, 1));
		blueprint.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
		check(helper, count(player, Items.STICK) == 4, "any planks do: " + count(player, Items.STICK));
		check(helper, count(player, Items.BIRCH_PLANKS) == 0 && count(player, Items.DARK_OAK_PLANKS) == 0, "both were used");

		// a filter that does not match stops the craft
		player.getInventory().add(new ItemStack(Items.STONE, 2));
		blueprint.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
		check(helper, count(player, Items.STICK) == 4 && count(player, Items.STONE) == 2, "stone is no plank");
		done(helper);
	}

	@GameTest(maxTicks = 60)
	public void blueprintGuiInfersTheResult(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlueprintEntity blueprint = blueprint(helper, new BlockPos(3, 1, 3));
		BlueprintSection section = blueprint.getSection(0);
		ServerPlayer player = player(helper);
		BlueprintGui gui = new BlueprintGui(player, section);

		// the grid: slot 0 in the first row, slot 3 below it
		int top = 1 * 9 + 1;
		int bottom = 2 * 9 + 1;
		player.containerMenu.setCarried(new ItemStack(Items.OAK_PLANKS, 8));
		gui.onClick(top, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, section.getItems().getStackInSlot(9).is(Items.OAK_BUTTON), "one plank is a button: " + section.getItems().getStackInSlot(9));
		gui.onClick(bottom, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, player.containerMenu.getCarried().getCount() == 8, "ghost slots do not use up the cursor");

		ItemStackHandler items = section.getItems();
		check(helper, items.getStackInSlot(0).is(Items.OAK_PLANKS) && items.getStackInSlot(3).is(Items.OAK_PLANKS), "the grid is saved");
		check(helper, items.getStackInSlot(9).is(Items.STICK) && items.getStackInSlot(9).getCount() == 4,
			"the display slot shows the sticks: " + items.getStackInSlot(9));
		check(helper, section.isInferredIcon(), "inferred from the recipe");

		// taking an ingredient away changes the inferred result, taking both away empties it
		player.containerMenu.setCarried(ItemStack.EMPTY);
		gui.onClick(bottom, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, section.getItems().getStackInSlot(9).is(Items.OAK_BUTTON), "back to the button");
		gui.onClick(top, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, section.getItems().getStackInSlot(9).isEmpty(), "no recipe, no result");

		// a result chosen by hand stays
		player.containerMenu.setCarried(new ItemStack(Items.DIAMOND));
		gui.onClick(23, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, section.getItems().getStackInSlot(9).is(Items.DIAMOND) && !section.isInferredIcon(), "a manual display item");
		player.containerMenu.setCarried(new ItemStack(Items.OAK_PLANKS));
		gui.onClick(top, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, section.getItems().getStackInSlot(9).is(Items.OAK_BUTTON) && section.isInferredIcon(),
			"a recipe replaces it, as in Create");
		gui.onClick(bottom, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, section.getItems().getStackInSlot(9).is(Items.STICK), "and a better one replaces that");

		// the secondary display slot
		player.containerMenu.setCarried(new ItemStack(Items.APPLE));
		gui.onClick(34, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, section.getItems().getStackInSlot(10).is(Items.APPLE), "secondary display item");
		done(helper);
	}

	@GameTest(maxTicks = 60)
	public void blueprintItemPlacesAnEntity(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlockPos stone = new BlockPos(3, 1, 3);
		helper.setBlock(stone, Blocks.STONE);
		ServerPlayer player = player(helper);
		ItemStack item = new ItemStack(ToolsRegistry.CRAFTING_BLUEPRINT);
		player.setItemInHand(InteractionHand.MAIN_HAND, item);
		BlockPos abs = helper.absolutePos(stone);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0, .5), Direction.SOUTH, abs, false);
		InteractionResult result = item.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, item, hit));
		check(helper, result.consumesAction(), "placed: " + result);
		check(helper, item.isEmpty(), "the item is used up");
		var found = helper.getLevel().getEntitiesOfClass(BlueprintEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(3));
		check(helper, found.size() == 1, "one blueprint entity");
		BlueprintEntity blueprint = found.get(0);
		check(helper, blueprint.getFacing() == Direction.SOUTH, "facing the way the block face did");
		check(helper, blueprint.getBoundingBox().getXsize() > .9 && blueprint.getBoundingBox().getZsize() < .1, "a flat panel against the wall");

		// no second one in the same place
		ItemStack second = new ItemStack(ToolsRegistry.CRAFTING_BLUEPRINT);
		player.setItemInHand(InteractionHand.MAIN_HAND, second);
		second.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, second, hit));
		check(helper, helper.getLevel().getEntitiesOfClass(BlueprintEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(3)).size() == 1,
			"hanging entities do not overlap");
		done(helper);
	}

	@GameTest(maxTicks = 60)
	public void blueprintGrowsToTheSpaceItHas(GameTestHelper helper) {
		TestUtil.floor(helper);
		for (int x = 1; x <= 5; x++)
			for (int y = 1; y <= 3; y++)
				helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
		BlockPos front = helper.absolutePos(new BlockPos(3, 2, 2));
		BlueprintEntity blueprint = new BlueprintEntity(ToolsRegistry.CRAFTING_BLUEPRINT_ENTITY, helper.getLevel(), front, Direction.SOUTH,
			Direction.DOWN);
		check(helper, blueprint.getSize() == 3, "a 3x3 wall makes a 3x3 blueprint: " + blueprint.getSize());
		helper.getLevel().addFreshEntity(blueprint);

		check(helper, blueprint.getSectionAt(Vec3.ZERO).getIndex() == 4, "the middle is square 4");
		check(helper, blueprint.getSectionAt(new Vec3(-1, -1, 0)).getIndex() == 0, "one corner is square 0");
		check(helper, blueprint.getSectionAt(new Vec3(1, 1, 0)).getIndex() == 8, "the other is square 8");
		check(helper, blueprint.getSectionAt(new Vec3(1, -1, 0)).getIndex() == 2, "the bottom row has squares 0 to 2");
		check(helper, blueprint.getBoundingBox().getXsize() > 2.9 && blueprint.getBoundingBox().getYsize() > 2.9, "three blocks across");
		done(helper);
	}

	@GameTest(maxTicks = 60)
	public void blueprintKeepsItsRecipesWhenSaved(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlueprintEntity blueprint = blueprint(helper, new BlockPos(3, 1, 3));
		sticksRecipe(blueprint.getSection(0), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS));
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		blueprint.saveWithoutId(output);

		BlueprintEntity copy = new BlueprintEntity(ToolsRegistry.CRAFTING_BLUEPRINT_ENTITY, helper.getLevel());
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
		check(helper, copy.getFacing() == Direction.SOUTH && copy.getSize() == 1, "facing and size");
		ItemStackHandler items = copy.getSection(0).getItems();
		check(helper, items.getStackInSlot(0).is(Items.OAK_PLANKS) && items.getStackInSlot(3).is(Items.OAK_PLANKS), "the ingredients");
		check(helper, items.getStackInSlot(9).is(Items.STICK) && items.getStackInSlot(9).getCount() == 4, "the display item");
		check(helper, copy.getSection(1).getItems().getStackInSlot(0).isEmpty(), "other squares stay empty");
		check(helper, copy.getBoundingBox().equals(blueprint.getBoundingBox()), "the same place");
		done(helper);
	}

	@GameTest(maxTicks = 60)
	public void blueprintShowsItsPanelIconsAndHitboxes(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlueprintEntity blueprint = blueprint(helper, new BlockPos(3, 1, 3));
		helper.runAfterDelay(4, () -> {
			BlueprintModel model = blueprint.getModel();
			check(helper, model != null, "the entity made its model");
			int panels = 0, hitboxes = 0;
			for (VirtualElement element : model.getElements()) {
				if (element instanceof InteractionElement)
					hitboxes++;
				else if (element instanceof ItemDisplayElement)
					panels++;
			}
			check(helper, panels == 1 && hitboxes == 4, "the panel and four rows of hitboxes: " + panels + " / " + hitboxes);

			sticksRecipe(blueprint.getSection(0), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS));
			helper.runAfterDelay(3, () -> {
				check(helper, model.shownIcons().size() == 1 && model.shownIcons().get(0).is(Items.STICK), "the result icon appears");
				blueprint.getSection(0).save(blueprint.getSection(0).getItems());
				done(helper);
			});
		});
	}

	@GameTest(maxTicks = 60)
	public void blueprintDropsItsItemWhenHit(GameTestHelper helper) {
		TestUtil.floor(helper);
		BlueprintEntity blueprint = blueprint(helper, new BlockPos(3, 1, 3));
		blueprint.dropItem(helper.getLevel(), null);
		var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, blueprint.getBoundingBox().inflate(2));
		check(helper, drops.size() == 1 && drops.get(0).getItem().getItem() == ToolsRegistry.CRAFTING_BLUEPRINT, "dropped the blueprint item");
		check(helper, blueprint.getPickResult().getItem() == ToolsRegistry.CRAFTING_BLUEPRINT, "pick block");
		done(helper);
	}

	// ---------------------------------------------------------------------------------------------------------
	// registration
	// ---------------------------------------------------------------------------------------------------------

	@GameTest
	public void toolsAreRegisteredUnderCreatesIds(GameTestHelper helper) {
		for (String id : List.of("attribute_filter", "package_filter", "clipboard", "linked_controller", "crafting_blueprint"))
			check(helper, net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
				dev.createpolymer.CreatePolymer.id(id)), "item " + id);
		for (String id : List.of("clipboard", "lectern_controller"))
			check(helper, net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(
				dev.createpolymer.CreatePolymer.id(id)), "block " + id);
		for (String id : List.of("clipboard", "lectern_controller"))
			check(helper, net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(
				dev.createpolymer.CreatePolymer.id(id)), "block entity " + id);
		check(helper, net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(
			dev.createpolymer.CreatePolymer.id("crafting_blueprint")), "the blueprint entity");
		check(helper, net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(
			dev.createpolymer.CreatePolymer.id("clipboard_content")), "the clipboard component");
		check(helper, AllItemAttributeTypes.REGISTRY.size() >= 26, "all of Create's attribute types: " + AllItemAttributeTypes.REGISTRY.size());
		done(helper);
	}
}

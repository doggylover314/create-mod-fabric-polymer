package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.mojang.authlib.GameProfile;

import dev.createpolymer.decoration.copycat.CopycatBlock;
import dev.createpolymer.decoration.copycat.CopycatBlockEntity;
import dev.createpolymer.decoration.copycat.CopycatPanelBlock;
import dev.createpolymer.equipment.blueprint.BlueprintEntity;
import dev.createpolymer.equipment.clipboard.ClipboardBlock;
import dev.createpolymer.equipment.clipboard.ClipboardBlockEntity;
import dev.createpolymer.equipment.clipboard.ClipboardContent;
import dev.createpolymer.equipment.clipboard.ClipboardEntry;
import dev.createpolymer.equipment.clipboard.ClipboardOverrides.ClipboardType;
import dev.createpolymer.foundation.block.BlockHelper;
import dev.createpolymer.redstone.link.controller.LecternControllerBlockEntity;
import dev.createpolymer.registry.feature.CopycatRegistry;
import dev.createpolymer.registry.feature.SchematicsRegistry;
import dev.createpolymer.registry.feature.ToolsRegistry;
import dev.createpolymer.schematics.SchematicAndQuillHandler;
import dev.createpolymer.schematics.SchematicExport.SchematicExportResult;
import dev.createpolymer.schematics.SchematicItem;
import dev.createpolymer.schematics.ServerSchematicLoader;
import dev.createpolymer.schematics.cannon.ConfigureSchematicannon;
import dev.createpolymer.schematics.cannon.ConfigureSchematicannon.Option;
import dev.createpolymer.schematics.cannon.MaterialChecklist;
import dev.createpolymer.schematics.cannon.SchematicannonBlockEntity;
import dev.createpolymer.schematics.cannon.SchematicannonBlockEntity.State;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.schematics.requirement.ItemRequirement.StackRequirement;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * The seams between features that were ported apart: the schematicannon's checklist on a clipboard, the clipboard's
 * data filter for schematics, and the requirements and safe data of the copycat, clipboard, lectern controller and
 * crafting blueprint.
 */
public class IntegrationGameTests {

	private static final AtomicInteger PLAYERS = new AtomicInteger();

	/** A creative player of its own (the tests run side by side and a player's schematic folder is limited) */
	private static ServerPlayer player(GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(
			new GameProfile(UUID.randomUUID(), "int" + PLAYERS.incrementAndGet()), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel()
			.getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.CREATIVE;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel()
			.getServer()
			.getPlayerList()
			.placeNewPlayer(connection, player, cookie);
		return player;
	}

	@SuppressWarnings("removal")
	private static void leave(GameTestHelper helper, ServerPlayer player) {
		player.connection.disconnect(Component.literal("test over"));
		helper.getLevel()
			.getServer()
			.getPlayerList()
			.remove(player);
		var dir = ServerSchematicLoader.uploadedDir()
			.resolve(ServerSchematicLoader.playerName(player));
		try (Stream<java.nio.file.Path> files = Files.list(dir)) {
			for (java.nio.file.Path file : files.toList())
				Files.deleteIfExists(file);
			Files.deleteIfExists(dir);
		} catch (IOException ignored) {
		}
	}

	private static String save(GameTestHelper helper, ServerPlayer player, BlockPos base, String name) {
		SchematicAndQuillHandler.discard(player);
		SchematicAndQuillHandler.handleSelection(player, helper.absolutePos(base));
		SchematicAndQuillHandler.handleSelection(player, helper.absolutePos(base.offset(1, 1, 1)));
		SchematicExportResult result = SchematicAndQuillHandler.saveSchematic(player, name, false);
		helper.assertTrue(result != null, "the schematic was not saved");
		return result.fileName();
	}

	private static ItemStack deployed(GameTestHelper helper, ServerPlayer player, String file, BlockPos anchor) {
		ItemStack item = SchematicItem.create(helper.getLevel(), file, ServerSchematicLoader.playerName(player));
		item.set(SchematicsRegistry.SCHEMATIC_DEPLOYED, true);
		item.set(SchematicsRegistry.SCHEMATIC_ANCHOR, helper.absolutePos(anchor));
		return item;
	}

	private static final BlockPos CANNON = new BlockPos(6, 1, 6);
	private static final BlockPos BASE = new BlockPos(1, 1, 1);

	/** A cannon with a chest next to it holding {@code supplies} and a deployed schematic of the structure at BASE */
	private static SchematicannonBlockEntity cannon(GameTestHelper helper, ServerPlayer player, String name,
		ItemStack... supplies) {
		helper.setBlock(CANNON, SchematicsRegistry.SCHEMATICANNON);
		helper.setBlock(CANNON.west(), Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(CANNON.west(), ChestBlockEntity.class);
		for (int i = 0; i < supplies.length; i++)
			chest.setItem(i, supplies[i]);
		String file = save(helper, player, BASE, name);
		for (int x = 0; x < 2; x++)
			for (int y = 0; y < 2; y++)
				for (int z = 0; z < 2; z++)
					helper.setBlock(BASE.offset(x, y, z), Blocks.AIR);
		SchematicannonBlockEntity cannon = helper.getBlockEntity(CANNON, SchematicannonBlockEntity.class);
		cannon.inventory.setStackInSlot(0, deployed(helper, player, file, BASE));
		cannon.inventory.setStackInSlot(4, new ItemStack(Items.GUNPOWDER, 2));
		return cannon;
	}

	private static ClipboardContent content(Component... lines) {
		List<ClipboardEntry> entries = java.util.Arrays.stream(lines)
			.map(line -> new ClipboardEntry(false, line.copy()))
			.toList();
		return new ClipboardContent(ClipboardType.WRITTEN, List.of(entries), false);
	}

	// ---- the checklist on a clipboard ----

	@GameTest(maxTicks = 200)
	public void schematicannonPrintsAChecklistOntoAClipboard(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		SchematicannonBlockEntity cannon;
		try {
			helper.setBlock(BASE, Blocks.STONE);
			helper.setBlock(BASE.east(), Blocks.STONE);
			helper.setBlock(BASE.above(), Blocks.GLASS);
			cannon = cannon(helper, player, "int_clipboard", new ItemStack(Items.STONE, 1));
			ItemStack clipboard = new ItemStack(ToolsRegistry.CLIPBOARD_ITEM);
			helper.assertTrue(cannon.inventory.isItemValid(2, clipboard), "a clipboard is not a paper of the cannon");
			helper.assertTrue(cannon.inventory.isItemValid(2, new ItemStack(Items.BOOK)), "a book is not a paper of the cannon");
			helper.assertTrue(!cannon.inventory.isItemValid(2, new ItemStack(Items.STICK)), "a stick is a paper of the cannon");
			cannon.inventory.setStackInSlot(2, clipboard);
		} catch (RuntimeException e) {
			leave(helper, player);
			throw e;
		}
		helper.succeedWhen(() -> {
			ItemStack out = cannon.inventory.getStackInSlot(3);
			helper.assertTrue(out.is(ToolsRegistry.CLIPBOARD_ITEM), "the output holds " + out);
			helper.assertTrue(cannon.inventory.getStackInSlot(2)
				.isEmpty(), "the clipboard was not used");
			ClipboardContent content = out.get(ToolsRegistry.CLIPBOARD_CONTENT);
			helper.assertTrue(content != null && content.type() == ClipboardType.WRITTEN && content.readOnly(),
				"the clipboard is not a written, read-only one: " + content);
			List<ClipboardEntry> entries = ClipboardEntry.readAll(out)
				.get(0);
			StringBuilder text = new StringBuilder();
			for (ClipboardEntry entry : entries)
				text.append(entry.text.getString())
					.append('\n');
			// 2 stone are needed and the chest has 1; the glass is missing, too
			helper.assertTrue(text.toString()
				.contains("Stone") && text.toString()
					.contains("Glass") && text.toString()
						.contains("x1"),
				"the clipboard says: " + text);
			helper.assertTrue(entries.stream()
				.allMatch(e -> !e.icon.isEmpty() && !e.checked), "the entries show their item and are open: " + entries);
			helper.assertTrue(out.has(DataComponents.CUSTOM_NAME), "the clipboard has no name");
			leave(helper, player);
		});
	}

	@GameTest(maxTicks = 20)
	public void aChecklistClipboardPutsCompletedEntriesLast(GameTestHelper helper) {
		MaterialChecklist checklist = new MaterialChecklist();
		checklist.require(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, new ItemStack(Items.STONE, 3)));
		checklist.require(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, new ItemStack(Items.GLASS, 2)));
		checklist.collect(new ItemStack(Items.GLASS, 2));
		ItemStack clipboard = checklist.createWrittenClipboard();
		helper.assertTrue(clipboard.is(ToolsRegistry.CLIPBOARD_ITEM), "not a clipboard");
		List<ClipboardEntry> entries = ClipboardEntry.readAll(clipboard)
			.get(0);
		helper.assertTrue(entries.size() == 2, "entries: " + entries.size());
		helper.assertTrue(!entries.get(0).checked && entries.get(0).text.getString()
			.contains("Stone"), "stone first: " + entries.get(0).text.getString());
		helper.assertTrue(entries.get(1).checked && entries.get(1).text.getString()
			.contains("Glass"), "the finished glass last: " + entries.get(1).text.getString());
		helper.assertTrue(entries.get(0).itemAmount == 3, "the amount is " + entries.get(0).itemAmount);

		// more than a page of entries goes onto the next page
		MaterialChecklist many = new MaterialChecklist();
		net.minecraft.world.item.Item[] items = { Items.STONE, Items.GLASS, Items.DIRT, Items.SAND, Items.GRAVEL, Items.CLAY,
			Items.OAK_PLANKS, Items.COBBLESTONE, Items.BRICKS };
		for (var item : items)
			many.require(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, new ItemStack(item)));
		List<List<ClipboardEntry>> pages = ClipboardEntry.readAll(many.createWrittenClipboard());
		helper.assertTrue(pages.size() == 2, "pages: " + pages.size());
		helper.assertTrue(pages.get(0).size() == MaterialChecklist.MAX_ENTRIES_PER_CLIPBOARD_PAGE + 1, "first page: " + pages.get(0).size());
		helper.succeed();
	}

	// ---- the clipboard's data filter ----

	@GameTest(maxTicks = 20)
	public void clipboardsWithClickEventsAreNotCopiedBySchematics(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ToolsRegistry.CLIPBOARD.defaultBlockState()
			.setValue(ClipboardBlock.FACE, AttachFace.FLOOR));
		ClipboardBlockEntity be = helper.getBlockEntity(pos, ClipboardBlockEntity.class);
		var map = new net.minecraft.core.component.PatchedDataComponentMap(be.components());
		map.set(ToolsRegistry.CLIPBOARD_CONTENT, content(Component.literal("plain")));
		be.setComponents(map);

		CompoundTag data = BlockHelper.prepareBlockEntityData(helper.getLevel(), helper.getBlockState(pos), be);
		helper.assertTrue(data != null, "a clipboard of plain text is not copied");
		helper.assertTrue(data.contains("components"), "the content is not in the copy: " + data);

		map = new net.minecraft.core.component.PatchedDataComponentMap(be.components());
		map.set(ToolsRegistry.CLIPBOARD_CONTENT, content(Component.literal("plain"), Component.literal("run")
			.withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/say hi")))));
		be.setComponents(map);
		helper.assertTrue(BlockHelper.prepareBlockEntityData(helper.getLevel(), helper.getBlockState(pos), be) == null,
			"a clipboard with a click event is copied");
		helper.succeed();
	}

	// ---- requirements ----

	private static boolean requires(ItemRequirement requirement, net.minecraft.world.item.Item item) {
		return requirement.getRequiredItems()
			.stream()
			.anyMatch(r -> r.stack.is(item));
	}

	@GameTest(maxTicks = 20)
	public void copycatsNeedTheirMaterialItem(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, CopycatRegistry.COPYCAT_PANEL.defaultBlockState()
			.setValue(CopycatPanelBlock.FACING, Direction.NORTH));
		CopycatBlockEntity be = helper.getBlockEntity(pos, CopycatBlockEntity.class);
		BlockState state = helper.getBlockState(pos);

		ItemRequirement bare = ItemRequirement.of(state, be);
		helper.assertTrue(bare.getRequiredItems()
			.size() == 1 && requires(bare, CopycatRegistry.COPYCAT_PANEL_ITEM), "a bare copycat needs only itself");

		be.setMaterial(Blocks.OAK_PLANKS.defaultBlockState());
		be.setConsumedItem(new ItemStack(Items.OAK_PLANKS));
		ItemRequirement covered = ItemRequirement.of(state, be);
		helper.assertTrue(covered.getRequiredItems()
			.size() == 2 && requires(covered, CopycatRegistry.COPYCAT_PANEL_ITEM) && requires(covered, Items.OAK_PLANKS),
			"a covered copycat needs the panel and the planks: " + covered.getRequiredItems()
				.size());
		for (StackRequirement r : covered.getRequiredItems())
			helper.assertTrue(r.usage == ItemRequirement.ItemUseType.CONSUME, "consumed");

		// the safe data keeps the material, but not the components of the item
		ItemStack named = new ItemStack(Items.OAK_PLANKS);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("secret"));
		be.setConsumedItem(named);
		CompoundTag data = BlockHelper.prepareBlockEntityData(helper.getLevel(), state, be);
		helper.assertTrue(data != null && data.contains("Material"), "the copycat data has no material: " + data);
		CompoundTag item = data.getCompoundOrEmpty("Item");
		helper.assertTrue(item.getStringOr("id", "")
			.equals("minecraft:oak_planks"), "the item is " + item);
		helper.assertTrue(!item.contains("components"), "the item keeps its components: " + item);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void clipboardLecternControllerAndBlueprintRequirements(GameTestHelper helper) {
		floor(helper);
		// clipboard: the item, whatever it says
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ToolsRegistry.CLIPBOARD.defaultBlockState()
			.setValue(ClipboardBlock.FACE, AttachFace.FLOOR));
		ClipboardBlockEntity clipboard = helper.getBlockEntity(pos, ClipboardBlockEntity.class);
		var map = new net.minecraft.core.component.PatchedDataComponentMap(clipboard.components());
		map.set(ToolsRegistry.CLIPBOARD_CONTENT, content(Component.literal("note")));
		map.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
		clipboard.setComponents(map);
		ItemRequirement requirement = ItemRequirement.of(helper.getBlockState(pos), clipboard);
		helper.assertTrue(requirement.getRequiredItems()
			.size() == 1, "one item");
		StackRequirement stack = requirement.getRequiredItems()
			.get(0);
		helper.assertTrue(stack.stack.is(ToolsRegistry.CLIPBOARD_ITEM), "a clipboard");
		ItemStack other = new ItemStack(ToolsRegistry.CLIPBOARD_ITEM);
		other.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
		other.set(ToolsRegistry.CLIPBOARD_CONTENT, content(Component.literal("something else")));
		helper.assertTrue(stack.matches(other), "the written content is not part of what matches");
		other.set(DataComponents.CUSTOM_NAME, Component.literal("another name"));
		helper.assertTrue(!stack.matches(other), "the other components are");

		// lectern controller: a lectern and a linked controller
		BlockPos lecternPos = new BlockPos(4, 1, 2);
		helper.setBlock(lecternPos, Blocks.LECTERN.defaultBlockState()
			.setValue(LecternBlock.FACING, Direction.NORTH));
		ToolsRegistry.LECTERN_CONTROLLER.replaceLectern(helper.getBlockState(lecternPos), helper.getLevel(),
			helper.absolutePos(lecternPos), new ItemStack(ToolsRegistry.LINKED_CONTROLLER));
		LecternControllerBlockEntity lectern = helper.getBlockEntity(lecternPos, LecternControllerBlockEntity.class);
		ItemRequirement lecternRequirement = ItemRequirement.of(helper.getBlockState(lecternPos), lectern);
		helper.assertTrue(requires(lecternRequirement, Items.LECTERN) && requires(lecternRequirement, ToolsRegistry.LINKED_CONTROLLER),
			"a lectern controller needs the lectern and the controller");
		CompoundTag lecternData = BlockHelper.prepareBlockEntityData(helper.getLevel(), helper.getBlockState(lecternPos), lectern);
		helper.assertTrue(lecternData != null && lecternData.contains("ControllerData"), "the copy has the controller: " + lecternData);
		helper.assertTrue(!lecternData.contains("User"), "the copy has the user");

		// crafting blueprint: the blueprint item
		BlueprintEntity blueprint = new BlueprintEntity(ToolsRegistry.CRAFTING_BLUEPRINT_ENTITY, helper.getLevel());
		ItemRequirement blueprintRequirement = ItemRequirement.of(blueprint);
		helper.assertTrue(blueprintRequirement.getRequiredItems()
			.size() == 1 && requires(blueprintRequirement, ToolsRegistry.CRAFTING_BLUEPRINT), "a blueprint needs its item");
		helper.succeed();
	}

	// ---- a copycat through a schematic ----

	@GameTest(maxTicks = 700)
	public void aCopycatKeepsItsMaterialThroughTheQuillAndTheCannon(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		SchematicannonBlockEntity cannon;
		try {
			helper.setBlock(BASE, CopycatRegistry.COPYCAT_PANEL.defaultBlockState()
				.setValue(CopycatPanelBlock.FACING, Direction.EAST));
			CopycatBlockEntity source = helper.getBlockEntity(BASE, CopycatBlockEntity.class);
			source.setMaterial(Blocks.OAK_PLANKS.defaultBlockState());
			ItemStack named = new ItemStack(Items.OAK_PLANKS);
			named.set(DataComponents.CUSTOM_NAME, Component.literal("secret"));
			source.setConsumedItem(named);
			cannon = cannon(helper, player, "int_copycat", new ItemStack(CopycatRegistry.COPYCAT_PANEL_ITEM, 1),
				new ItemStack(Items.OAK_PLANKS, 1));
		} catch (RuntimeException e) {
			leave(helper, player);
			throw e;
		}
		helper.runAfterDelay(2, () -> ConfigureSchematicannon.apply(cannon, Option.PLAY, true));
		helper.succeedWhen(() -> {
			helper.assertTrue(cannon.statusMsg.equals("finished"), "the cannon is " + cannon.state + " / " + cannon.statusMsg
				+ ", placed " + cannon.blocksPlaced + " of " + cannon.blocksToPlace);
			helper.assertTrue(cannon.state == State.STOPPED, "the cannon is " + cannon.state);
			BlockState placed = helper.getBlockState(BASE);
			helper.assertTrue(placed.is(CopycatRegistry.COPYCAT_PANEL) && placed.getValue(CopycatPanelBlock.FACING) == Direction.EAST,
				"the panel is " + placed);
			CopycatBlockEntity be = helper.getBlockEntity(BASE, CopycatBlockEntity.class);
			helper.assertTrue(be.getMaterial()
				.is(Blocks.OAK_PLANKS), "the material is " + be.getMaterial());
			helper.assertTrue(be.getConsumedItem()
				.is(Items.OAK_PLANKS), "the consumed item is " + be.getConsumedItem());
			helper.assertTrue(!be.getConsumedItem()
				.has(DataComponents.CUSTOM_NAME), "the item kept its name");
			// the planks and the panel were taken from the chest
			ChestBlockEntity chest = helper.getBlockEntity(CANNON.west(), ChestBlockEntity.class);
			helper.assertTrue(chest.getItem(0)
				.isEmpty() && chest.getItem(1)
					.isEmpty(),
				"the chest still holds " + chest.getItem(0) + ", " + chest.getItem(1));
			leave(helper, player);
		});
	}
}

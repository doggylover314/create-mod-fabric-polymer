package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

import dev.createpolymer.registry.feature.BeltRegistry;
import dev.createpolymer.registry.feature.SchematicsRegistry;
import dev.createpolymer.schematics.NBTProcessors;
import dev.createpolymer.schematics.PlayerBoxOutline;
import dev.createpolymer.schematics.SchematicAndQuillHandler;
import dev.createpolymer.schematics.SchematicExport.SchematicExportResult;
import dev.createpolymer.schematics.SchematicHandler;
import dev.createpolymer.schematics.SchematicItem;
import dev.createpolymer.schematics.SchematicPlaceGui;
import dev.createpolymer.schematics.SchematicPrinter;
import dev.createpolymer.schematics.ServerSchematicLoader;
import dev.createpolymer.schematics.ServerSchematicLoader.AvailableSchematic;
import dev.createpolymer.schematics.cannon.ConfigureSchematicannon.Option;
import dev.createpolymer.schematics.cannon.ConfigureSchematicannon;
import dev.createpolymer.schematics.cannon.MaterialChecklist;
import dev.createpolymer.schematics.cannon.SchematicannonBlockEntity;
import dev.createpolymer.schematics.cannon.SchematicannonBlockEntity.State;
import dev.createpolymer.schematics.cannon.SchematicannonGui;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.schematics.table.SchematicTableBlockEntity;
import dev.createpolymer.schematics.table.SchematicTableGui;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SlotBasedGui;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

/**
 * Create's schematics: the schematic and quill, the table, the placement tools, the schematicannon and the
 * requirements of what it builds. Every test that writes schematic files removes them again.
 */
public class SchematicsGameTests {

	// ---- helpers ----

	private static final AtomicInteger PLAYERS = new AtomicInteger();

	/**
	 * A creative player with a connection, like GameTestHelper.makeMockServerPlayerInLevel but with a name of its own:
	 * the tests run side by side and the folder of a player holds a limited number of schematics.
	 */
	private static ServerPlayer player(GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(
			new GameProfile(UUID.randomUUID(), "sch" + PLAYERS.incrementAndGet()), false);
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

	private static void leave(GameTestHelper helper, ServerPlayer player) {
		player.connection.disconnect(Component.literal("test over"));
		helper.getLevel()
			.getServer()
			.getPlayerList()
			.remove(player);
	}

	/** The shared files tests wrote, so that they can be removed even when a test fails */
	private static final List<Path> WRITTEN = java.util.Collections.synchronizedList(new ArrayList<>());

	static {
		// a failing test does not get to clean up after itself
		Runtime.getRuntime()
			.addShutdownHook(new Thread(() -> {
				for (Path path : new ArrayList<>(WRITTEN))
					try {
						Files.deleteIfExists(path);
					} catch (IOException ignored) {
					}
				try (Stream<Path> dirs = Files.list(ServerSchematicLoader.uploadedDir())) {
					for (Path dir : dirs.filter(d -> d.getFileName()
						.toString()
						.startsWith("sch"))
						.toList()) {
						try (Stream<Path> files = Files.list(dir)) {
							for (Path file : files.toList())
								Files.deleteIfExists(file);
						}
						Files.deleteIfExists(dir);
					}
				} catch (IOException ignored) {
				}
			}));
	}

	private static Path uploaded(ServerPlayer player, String name) {
		return ServerSchematicLoader.resolveUploadPath(ServerSchematicLoader.playerName(player), name);
	}

	private static final java.util.Map<UUID, List<Path>> SHARED = new java.util.concurrent.ConcurrentHashMap<>();

	private static Path shared(ServerPlayer player, String name) {
		Path path = ServerSchematicLoader.resolveSharedPath(name);
		WRITTEN.add(path);
		SHARED.computeIfAbsent(player.getUUID(), id -> new ArrayList<>())
			.add(path);
		return path;
	}

	/** Removes what the test wrote: its shared files, and the folder of its player */
	private static void cleanUp(ServerPlayer player) {
		for (Path path : SHARED.getOrDefault(player.getUUID(), List.of())) {
			try {
				Files.deleteIfExists(path);
				WRITTEN.remove(path);
			} catch (IOException ignored) {
			}
		}
		Path dir = ServerSchematicLoader.uploadedDir()
			.resolve(ServerSchematicLoader.playerName(player));
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.toList())
				Files.deleteIfExists(file);
			Files.deleteIfExists(dir);
		} catch (IOException ignored) {
		}
	}

	/** The 2x2x2 structure the tests build, with its low corner at {@code base} */
	private static void build(GameTestHelper helper, BlockPos base) {
		helper.setBlock(base, Blocks.STONE);
		helper.setBlock(base.east(), Blocks.STONE);
		helper.setBlock(base.south(), Blocks.OAK_STAIRS.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		helper.setBlock(base.south()
			.east(), Blocks.GLASS);
		helper.setBlock(base.above(), Blocks.COBBLESTONE);
		helper.setBlock(base.above()
			.south(), Blocks.OAK_SLAB.defaultBlockState()
				.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
		helper.setBlock(base.above()
			.south()
			.east(), Blocks.STONE_BRICKS);
	}

	private static void clear(GameTestHelper helper, BlockPos base) {
		for (int x = 0; x < 2; x++)
			for (int y = 0; y < 2; y++)
				for (int z = 0; z < 2; z++)
					helper.setBlock(base.offset(x, y, z), Blocks.AIR);
	}

	private static void assertSame(GameTestHelper helper, BlockPos from, BlockPos to) {
		for (int x = 0; x < 2; x++)
			for (int y = 0; y < 2; y++)
				for (int z = 0; z < 2; z++) {
					BlockState expected = helper.getBlockState(from.offset(x, y, z));
					BlockState actual = helper.getBlockState(to.offset(x, y, z));
					helper.assertTrue(expected == actual,
						"at +" + x + "," + y + "," + z + " there is " + actual + " instead of " + expected);
				}
	}

	/** Saves the structure at {@code base} the way the schematic and quill does and returns the file name */
	private static String save(GameTestHelper helper, ServerPlayer player, BlockPos base, String name) {
		BlockPos first = helper.absolutePos(base);
		BlockPos second = helper.absolutePos(base.offset(1, 1, 1));
		SchematicAndQuillHandler.discard(player);
		SchematicAndQuillHandler.handleSelection(player, first);
		SchematicAndQuillHandler.handleSelection(player, second);
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

	private static void click(SlotBasedGui gui, int slot) {
		gui.click(slot, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
	}

	// ---- the schematic and quill ----

	@GameTest(maxTicks = 40)
	public void quillSavesARegionToThePlayersFolderAndItLoadsBackIdentically(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		BlockPos source = new BlockPos(1, 1, 1);
		BlockPos copy = new BlockPos(4, 1, 4);
		try {
			build(helper, source);
			String file = save(helper, player, source, "quill_test");
			Path path = ServerSchematicLoader.uploadedDir()
				.resolve(ServerSchematicLoader.playerName(player))
				.resolve("quill_test.nbt");
			helper.assertTrue(file.equals("quill_test.nbt"), "the file is called " + file);
			helper.assertTrue(Files.isRegularFile(path), "there is no file at " + path);
			helper.assertTrue(ServerSchematicLoader.isGZIPEncoded(path), "the file is not compressed NBT");
			SchematicAndQuillHandler.Selection selection = SchematicAndQuillHandler.peek(player);
			helper.assertTrue(selection.firstPos == null && selection.secondPos == null, "the selection was kept");

			// the item of the file knows how big the structure is
			ItemStack item = SchematicItem.create(helper.getLevel(), file, ServerSchematicLoader.playerName(player));
			helper.assertTrue(new Vec3i(2, 2, 2).equals(item.get(SchematicsRegistry.SCHEMATIC_BOUNDS)),
				"the size is " + item.get(SchematicsRegistry.SCHEMATIC_BOUNDS));

			// and printing it elsewhere builds the same structure
			item.set(SchematicsRegistry.SCHEMATIC_DEPLOYED, true);
			item.set(SchematicsRegistry.SCHEMATIC_ANCHOR, helper.absolutePos(copy));
			helper.assertTrue(SchematicHandler.printInstantly(player, item), "the creative print failed");
			assertSame(helper, source, copy);
			helper.succeed();
		} finally {
			cleanUp(player);
			leave(helper, player);
		}
	}

	@GameTest(maxTicks = 40)
	public void quillSelectionIsDiscardedBySneakingAndKeepsTheCorners(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		try {
			BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
			BlockPos b = helper.absolutePos(new BlockPos(3, 2, 4));
			SchematicAndQuillHandler.discard(player);
			SchematicAndQuillHandler.handleSelection(player, a);
			SchematicAndQuillHandler.handleSelection(player, b);
			SchematicAndQuillHandler.Selection selection = SchematicAndQuillHandler.peek(player);
			helper.assertTrue(a.equals(selection.firstPos) && b.equals(selection.secondPos), "the corners were not kept");
			AABB box = SchematicAndQuillHandler.currentSelectionBox(selection);
			helper.assertTrue(box.getXsize() == 3 && box.getYsize() == 2 && box.getZsize() == 4, "the box is " + box);

			player.setShiftKeyDown(true);
			SchematicAndQuillHandler.handleSelection(player, a);
			player.setShiftKeyDown(false);
			helper.assertTrue(selection.firstPos == null && selection.secondPos == null, "sneaking did not discard");
			helper.succeed();
		} finally {
			leave(helper, player);
		}
	}

	@GameTest(maxTicks = 40)
	public void quillInstantSaveReplacesItWithTheSchematic(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		try {
			build(helper, new BlockPos(1, 1, 1));
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SchematicsRegistry.SCHEMATIC_AND_QUILL));
			SchematicAndQuillHandler.discard(player);
			SchematicAndQuillHandler.handleSelection(player, helper.absolutePos(new BlockPos(1, 1, 1)));
			SchematicAndQuillHandler.handleSelection(player, helper.absolutePos(new BlockPos(2, 2, 2)));
			uploaded(player, "instant_test.nbt");
			SchematicExportResult result = SchematicAndQuillHandler.saveSchematic(player, "instant test", true);
			helper.assertTrue(result != null, "nothing was saved");
			ItemStack held = player.getMainHandItem();
			helper.assertTrue(held.is(SchematicsRegistry.SCHEMATIC), "the hand holds " + held);
			helper.assertTrue("instant_test.nbt".equals(held.get(SchematicsRegistry.SCHEMATIC_FILE)), "the file is "
				+ held.get(SchematicsRegistry.SCHEMATIC_FILE));
			helper.assertTrue(ServerSchematicLoader.playerName(player)
				.equals(held.get(SchematicsRegistry.SCHEMATIC_OWNER)), "the owner is " + held.get(SchematicsRegistry.SCHEMATIC_OWNER));
			helper.succeed();
		} finally {
			cleanUp(player);
			leave(helper, player);
		}
	}

	@GameTest(maxTicks = 40)
	public void quillNamesCannotLeaveThePlayersFolder(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		try {
			build(helper, new BlockPos(1, 1, 1));
			String name = ServerSchematicLoader.playerName(player);

			// Create turns what is typed into a file name
			uploaded(player, "_x.nbt");
			SchematicAndQuillHandler.discard(player);
			SchematicAndQuillHandler.handleSelection(player, helper.absolutePos(new BlockPos(1, 1, 1)));
			SchematicAndQuillHandler.handleSelection(player, helper.absolutePos(new BlockPos(2, 2, 2)));
			SchematicExportResult result = SchematicAndQuillHandler.saveSchematic(player, "../x", false);
			helper.assertTrue(result != null, "nothing was saved");
			Path dir = ServerSchematicLoader.uploadedDir()
				.resolve(name);
			helper.assertTrue(result.file()
				.getParent()
				.equals(dir.toAbsolutePath()), "saved outside of " + dir + ": " + result.file());
			helper.assertTrue(result.fileName()
				.equals("_x.nbt"), "the file is " + result.fileName());
			helper.assertTrue(!Files.exists(ServerSchematicLoader.uploadedDir()
				.resolve("x.nbt")), "x.nbt was written next to the folder");

			// and a name that is a path is refused outright where a name is given as it is
			for (String bad : new String[] { "../x.nbt", "../../evil.nbt", "sub/../../x.nbt", "a/b.nbt", "x.txt", "x.nbt/..",
				"", "..", "/etc/passwd.nbt" })
				helper.assertTrue(ServerSchematicLoader.resolveUploadPath(name, bad) == null, "'" + bad + "' was accepted");
			for (String bad : new String[] { "..", "../a", "", "a/../.." })
				helper.assertTrue(ServerSchematicLoader.resolveUploadPath(bad, "x.nbt") == null,
					"the player folder '" + bad + "' was accepted");
			for (String bad : new String[] { "../x.nbt", "uploaded/x.nbt", "x.txt", "" })
				helper.assertTrue(ServerSchematicLoader.resolveSharedPath(bad) == null, "shared '" + bad + "' was accepted");
			helper.assertTrue(ServerSchematicLoader.resolveUploadPath(name, "fine.nbt") != null, "a plain name was refused");
			helper.assertTrue(ServerSchematicLoader.resolveSharedPath("fine.nbt") != null, "a plain shared name was refused");
			helper.assertTrue(ServerSchematicLoader.importShared(player, "../x.nbt") == null, "importing '../x.nbt' worked");

			// an item cannot read outside of the uploaded folder either
			ItemStack evil = new ItemStack(SchematicsRegistry.SCHEMATIC);
			evil.set(SchematicsRegistry.SCHEMATIC_OWNER, "..");
			evil.set(SchematicsRegistry.SCHEMATIC_FILE, "../schematics/uploaded/" + name + "/_x.nbt");
			helper.assertTrue(SchematicItem.loadSchematic(helper.getLevel(), evil)
				.getSize()
				.equals(Vec3i.ZERO), "the item read a file by a path");
			helper.succeed();
		} finally {
			cleanUp(player);
			leave(helper, player);
		}
	}

	@GameTest(maxTicks = 20)
	public void schematicSizeAndFileLimitsAreChecked(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			helper.assertTrue(ServerSchematicLoader.validateSchematicSizeOnServer(null, 15_999, 16), "15999 bytes are too much for 16 KB");
			helper.assertTrue(!ServerSchematicLoader.validateSchematicSizeOnServer(null, 16_001, 16), "16001 bytes are fine for 16 KB");
			helper.assertTrue(ServerSchematicLoader.validateSchematicSizeOnServer(null, 100_000),
				"100 KB are too much for the default of " + SchematicsRegistry.maxTotalSchematicSize + " KB");

			// the oldest of too many files goes
			Path dir = uploaded(player, "a.nbt").getParent();
			Files.createDirectories(dir);
			for (String name : new String[] { "limit_a.nbt", "limit_b.nbt" }) {
				Path file = dir.resolve(name);
				Files.write(file, new byte[] { 1 });
				Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(name.equals("limit_a.nbt") ? 1000 : 2000));
			}
			helper.assertTrue(ServerSchematicLoader.tryDeleteOldestSchematic(dir, 3), "room for a third file failed");
			helper.assertTrue(Files.exists(dir.resolve("limit_a.nbt")), "a file went although there was room");
			helper.assertTrue(ServerSchematicLoader.tryDeleteOldestSchematic(dir, 2), "making room failed");
			helper.assertTrue(!Files.exists(dir.resolve("limit_a.nbt")), "the oldest file was kept");
			helper.assertTrue(Files.exists(dir.resolve("limit_b.nbt")), "the newer file went");

			helper.assertTrue(ServerSchematicLoader.compareNatural("a2.nbt", "a10.nbt") < 0, "a2 is not before a10");
			helper.assertTrue(ServerSchematicLoader.compareNatural("a10.nbt", "a2.nbt") > 0, "a10 is not after a2");
			helper.succeed();
		} catch (IOException e) {
			helper.fail(e.toString());
		} finally {
			cleanUp(player);
			leave(helper, player);
		}
	}

	// ---- the table ----

	private static BlockPos table(GameTestHelper helper) {
		BlockPos pos = new BlockPos(5, 1, 5);
		helper.setBlock(pos, SchematicsRegistry.SCHEMATIC_TABLE);
		return pos;
	}

	@GameTest(maxTicks = 120)
	public void tableWritesAFileOntoAnEmptySchematic(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		BlockPos tablePos = table(helper);
		build(helper, new BlockPos(1, 1, 1));
		String file = save(helper, player, new BlockPos(1, 1, 1), "table_test");
		helper.runAfterDelay(2, () -> {
			SchematicTableBlockEntity table = helper.getBlockEntity(tablePos, SchematicTableBlockEntity.class);
			try {
				helper.assertTrue(!table.canWrite(), "an empty table can write");
				table.inventory.setStackInSlot(0, new ItemStack(SchematicsRegistry.EMPTY_SCHEMATIC));
				helper.assertTrue(table.canWrite(), "the table cannot write onto an empty schematic");
				helper.assertTrue(table.startWrite(player, new AvailableSchematic(file, true)), "the write did not start");
				helper.assertTrue(table.isUploading, "the table is not writing");
				helper.assertTrue(table.inventory.getStackInSlot(0)
					.isEmpty(), "the empty schematic was not used up");
			} catch (RuntimeException e) {
				cleanUp(player);
				leave(helper, player);
				throw e;
			}
		});
		helper.succeedWhen(() -> {
			SchematicTableBlockEntity table = helper.getBlockEntity(tablePos, SchematicTableBlockEntity.class);
			ItemStack out = table.inventory.getStackInSlot(1);
			helper.assertTrue(out.is(SchematicsRegistry.SCHEMATIC), "the table holds " + out + " in its output");
			helper.assertTrue(!table.isUploading, "the table is still writing");
			helper.assertTrue(file.equals(out.get(SchematicsRegistry.SCHEMATIC_FILE)), "file " + out.get(SchematicsRegistry.SCHEMATIC_FILE));
			helper.assertTrue(ServerSchematicLoader.playerName(player)
				.equals(out.get(SchematicsRegistry.SCHEMATIC_OWNER)), "owner " + out.get(SchematicsRegistry.SCHEMATIC_OWNER));
			helper.assertTrue(BlockPos.ZERO.equals(out.get(SchematicsRegistry.SCHEMATIC_ANCHOR)), "anchor");
			helper.assertTrue(out.get(SchematicsRegistry.SCHEMATIC_ROTATION) == Rotation.NONE, "rotation");
			helper.assertTrue(out.get(SchematicsRegistry.SCHEMATIC_MIRROR) == Mirror.NONE, "mirror");
			helper.assertTrue(Boolean.FALSE.equals(out.get(SchematicsRegistry.SCHEMATIC_DEPLOYED)), "deployed");
			helper.assertTrue(new Vec3i(2, 2, 2).equals(out.get(SchematicsRegistry.SCHEMATIC_BOUNDS)), "bounds");
			cleanUp(player);
			leave(helper, player);
		});
	}

	@GameTest(maxTicks = 120)
	public void tableCopiesASharedFileIntoThePlayersFolder(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		BlockPos tablePos = table(helper);
		build(helper, new BlockPos(1, 1, 1));
		Path shared = shared(player, "shared_test.nbt");
		try {
			Files.createDirectories(shared.getParent());
			helper.assertTrue(dev.createpolymer.schematics.SchematicExport.saveSchematic(shared.getParent(), "shared_test", true,
				helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(2, 2, 2))) != null,
				"the shared file was not written");
		} catch (IOException e) {
			helper.fail(e.toString());
		}
		uploaded(player, "shared_test.nbt");
		helper.runAfterDelay(2, () -> {
			try {
				List<AvailableSchematic> available = ServerSchematicLoader.getAvailableSchematics(ServerSchematicLoader.playerName(player));
				helper.assertTrue(available.contains(new AvailableSchematic("shared_test.nbt", false)), "the shared file is not listed: " + available);

				SchematicTableBlockEntity table = helper.getBlockEntity(tablePos, SchematicTableBlockEntity.class);
				table.inventory.setStackInSlot(0, new ItemStack(SchematicsRegistry.EMPTY_SCHEMATIC));
				helper.assertTrue(table.startWrite(player, new AvailableSchematic("shared_test.nbt", false)), "the write did not start");
				helper.assertTrue(Files.isRegularFile(ServerSchematicLoader.resolveUploadPath(ServerSchematicLoader.playerName(player),
					"shared_test.nbt")), "the file was not copied into the folder of the player");
			} catch (RuntimeException e) {
				cleanUp(player);
				leave(helper, player);
				throw e;
			}
		});
		helper.succeedWhen(() -> {
			SchematicTableBlockEntity table = helper.getBlockEntity(tablePos, SchematicTableBlockEntity.class);
			ItemStack out = table.inventory.getStackInSlot(1);
			helper.assertTrue(out.is(SchematicsRegistry.SCHEMATIC), "the table holds " + out);
			helper.assertTrue(new Vec3i(2, 2, 2).equals(out.get(SchematicsRegistry.SCHEMATIC_BOUNDS)), "bounds");
			cleanUp(player);
			leave(helper, player);
		});
	}

	@GameTest(maxTicks = 160)
	public void tableGuiListsTheFilesAndWritesTheSelectedOne(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		BlockPos tablePos = table(helper);
		build(helper, new BlockPos(1, 1, 1));
		String file = save(helper, player, new BlockPos(1, 1, 1), "gui_table_test");
		helper.runAfterDelay(2, () -> {
			SchematicTableBlockEntity table = helper.getBlockEntity(tablePos, SchematicTableBlockEntity.class);
			SchematicTableGui gui = new SchematicTableGui(player, table);
			try {
				gui.open();
				List<AvailableSchematic> available = ServerSchematicLoader.getAvailableSchematics(ServerSchematicLoader.playerName(player));
				int index = available.indexOf(new AvailableSchematic(file, true));
				helper.assertTrue(index >= 0 && index < SchematicTableGui.PAGE_SIZE, "the file is not on the first page: " + available);
				var entry = gui.getGuiElement(SchematicTableGui.LIST + index);
				helper.assertTrue(entry != null && entry.getItemStack()
					.getHoverName()
					.getString()
					.equals(file), "the list shows " + (entry == null ? null : entry.getItemStack()
						.getHoverName()
						.getString()));

				// without an empty schematic the button does nothing
				click(gui, SchematicTableGui.LIST + index);
				click(gui, SchematicTableGui.WRITE);
				helper.assertTrue(!table.isUploading, "the table wrote without an empty schematic");

				table.inventory.setStackInSlot(0, new ItemStack(SchematicsRegistry.EMPTY_SCHEMATIC));
				click(gui, SchematicTableGui.LIST + index);
				click(gui, SchematicTableGui.WRITE);
				helper.assertTrue(table.isUploading, "the write button did not start the table");
			} catch (RuntimeException e) {
				gui.close();
				cleanUp(player);
				leave(helper, player);
				throw e;
			}
			helper.runAfterDelay(1, gui::close);
		});
		helper.succeedWhen(() -> {
			SchematicTableBlockEntity table = helper.getBlockEntity(tablePos, SchematicTableBlockEntity.class);
			helper.assertTrue(table.inventory.getStackInSlot(1)
				.is(SchematicsRegistry.SCHEMATIC), "the table has not finished");
			cleanUp(player);
			leave(helper, player);
		});
	}

	// ---- placing ----

	@GameTest(maxTicks = 60)
	public void placeGuiMovesRotatesAndMirrorsTheAnchor(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		try {
			build(helper, new BlockPos(1, 1, 1));
			String file = save(helper, player, new BlockPos(1, 1, 1), "place_test");
			// 3 x 2 x 4 would show rotation better, but this is the structure the other tests use: make it asymmetric
			BlockPos anchor = helper.absolutePos(new BlockPos(4, 1, 4));
			ItemStack item = deployed(helper, player, file, new BlockPos(4, 1, 4));
			player.setItemInHand(InteractionHand.MAIN_HAND, item);

			SchematicPlaceGui gui = new SchematicPlaceGui(player);
			gui.open();

			gui.move(1, 0, 0);
			helper.assertTrue(anchor.east()
				.equals(item.get(SchematicsRegistry.SCHEMATIC_ANCHOR)), "x: " + item.get(SchematicsRegistry.SCHEMATIC_ANCHOR));
			gui.move(0, 2, -1);
			helper.assertTrue(anchor.offset(1, 2, -1)
				.equals(item.get(SchematicsRegistry.SCHEMATIC_ANCHOR)), "y z: " + item.get(SchematicsRegistry.SCHEMATIC_ANCHOR));
			gui.move(-1, -2, 1);
			helper.assertTrue(anchor.equals(item.get(SchematicsRegistry.SCHEMATIC_ANCHOR)), "back: " + item.get(SchematicsRegistry.SCHEMATIC_ANCHOR));

			// the buttons of the chest do the same
			click(gui, SchematicPlaceGui.MOVE_X + 1);
			helper.assertTrue(anchor.east()
				.equals(item.get(SchematicsRegistry.SCHEMATIC_ANCHOR)), "the +X button: " + item.get(SchematicsRegistry.SCHEMATIC_ANCHOR));
			click(gui, SchematicPlaceGui.STEP);
			click(gui, SchematicPlaceGui.MOVE_Z);
			helper.assertTrue(anchor.offset(1, 0, -5)
				.equals(item.get(SchematicsRegistry.SCHEMATIC_ANCHOR)), "the -Z button with step 5: " + item.get(SchematicsRegistry.SCHEMATIC_ANCHOR));
			gui.move(-1, 0, 5);

			// a 2x2x2 structure turns around the middle of its box: its box stays where it is
			AABB box = SchematicHandler.worldBox(item);
			helper.assertTrue(box.equals(new AABB(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX() + 2,
				anchor.getY() + 2, anchor.getZ() + 2)), "box " + box);
			gui.rotate(true);
			helper.assertTrue(item.get(SchematicsRegistry.SCHEMATIC_ROTATION) == Rotation.CLOCKWISE_90,
				"rotation " + item.get(SchematicsRegistry.SCHEMATIC_ROTATION));
			helper.assertTrue(box.equals(SchematicHandler.worldBox(item)), "turning moved the box to "
				+ SchematicHandler.worldBox(item));
			gui.rotate(true);
			helper.assertTrue(item.get(SchematicsRegistry.SCHEMATIC_ROTATION) == Rotation.CLOCKWISE_180, "second turn");
			gui.rotate(false);
			gui.rotate(false);
			helper.assertTrue(item.get(SchematicsRegistry.SCHEMATIC_ROTATION) == Rotation.NONE, "turned back");

			gui.mirror(net.minecraft.core.Direction.Axis.X);
			helper.assertTrue(item.get(SchematicsRegistry.SCHEMATIC_MIRROR) == Mirror.LEFT_RIGHT, "left-right " + item.get(SchematicsRegistry.SCHEMATIC_MIRROR));
			gui.mirror(net.minecraft.core.Direction.Axis.X);
			gui.mirror(net.minecraft.core.Direction.Axis.Z);
			helper.assertTrue(item.get(SchematicsRegistry.SCHEMATIC_MIRROR) == Mirror.FRONT_BACK, "front-back " + item.get(SchematicsRegistry.SCHEMATIC_MIRROR));

			gui.pickUp();
			helper.assertTrue(Boolean.FALSE.equals(item.get(SchematicsRegistry.SCHEMATIC_DEPLOYED)), "picking up kept it deployed");
			gui.close();
			helper.succeed();
		} finally {
			cleanUp(player);
			leave(helper, player);
		}
	}

	@GameTest(maxTicks = 60)
	public void deployingPutsTheMiddleOfTheStructureOnTheBlock(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		try {
			build(helper, new BlockPos(1, 1, 1));
			String file = save(helper, player, new BlockPos(1, 1, 1), "deploy_test");
			ItemStack item = SchematicItem.create(helper.getLevel(), file, ServerSchematicLoader.playerName(player));
			player.setItemInHand(InteractionHand.MAIN_HAND, item);
			helper.assertTrue(!item.getOrDefault(SchematicsRegistry.SCHEMATIC_DEPLOYED, false), "deployed from the start");

			// a block that can't be built into: the structure goes on top of it
			BlockPos ground = helper.absolutePos(new BlockPos(4, 0, 4));
			SchematicHandler.deployAt(player, ground, Direction.UP);
			helper.assertTrue(Boolean.TRUE.equals(item.get(SchematicsRegistry.SCHEMATIC_DEPLOYED)), "not deployed");
			// the box is 2 wide: its middle is the second block, so the low corner is one block before
			helper.assertTrue(ground.above()
				.offset(-1, 0, -1)
				.equals(item.get(SchematicsRegistry.SCHEMATIC_ANCHOR)), "anchor " + item.get(SchematicsRegistry.SCHEMATIC_ANCHOR));

			// the same through the chest, at the player's feet
			SchematicPlaceGui gui = new SchematicPlaceGui(player);
			gui.deployFeet();
			BlockPos feet = player.blockPosition();
			helper.assertTrue(feet.offset(-1, 0, -1)
				.equals(item.get(SchematicsRegistry.SCHEMATIC_ANCHOR)), "feet anchor " + item.get(SchematicsRegistry.SCHEMATIC_ANCHOR));
			helper.succeed();
		} finally {
			cleanUp(player);
			leave(helper, player);
		}
	}

	@GameTest(maxTicks = 60)
	public void creativePrintPlacesTheStructureAndSurvivalPlayersCannot(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		try {
			build(helper, new BlockPos(1, 1, 1));
			String file = save(helper, player, new BlockPos(1, 1, 1), "print_test");
			ItemStack item = deployed(helper, player, file, new BlockPos(4, 1, 4));
			// a survival player has to use a cannon
			helper.assertTrue(!SchematicHandler.printInstantly((ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL), item),
				"a survival player printed");
			helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 4))
				.isAir(), "something was placed");
			helper.assertTrue(SchematicHandler.printInstantly(player, item), "the creative print failed");
			assertSame(helper, new BlockPos(1, 1, 1), new BlockPos(4, 1, 4));

			// the config decides whether air replaces what is there
			helper.setBlock(new BlockPos(4, 2, 5), Blocks.DIRT);
			helper.setBlock(new BlockPos(5, 2, 5), Blocks.AIR);
			clear(helper, new BlockPos(1, 1, 1));
			helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
			String second = save(helper, player, new BlockPos(1, 1, 1), "print_air_test");
			ItemStack airItem = deployed(helper, player, second, new BlockPos(4, 1, 4));
			SchematicHandler.printInstantly(player, airItem, false);
			helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 4))
				.is(Blocks.STONE), "air replaced a block although the config says it should not");
			SchematicHandler.printInstantly(player, airItem, true);
			helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 4))
				.isAir(), "air did not replace a block although the config says it should");
			helper.succeed();
		} finally {
			cleanUp(player);
			leave(helper, player);
		}
	}

	@GameTest(maxTicks = 80)
	public void holdingADeployedSchematicShowsItsBoxToThatPlayer(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		try {
			build(helper, new BlockPos(1, 1, 1));
			String file = save(helper, player, new BlockPos(1, 1, 1), "outline_test");
			ItemStack item = deployed(helper, player, file, new BlockPos(4, 1, 4));
			player.setItemInHand(InteractionHand.MAIN_HAND, item);
			helper.runAfterDelay(3, () -> {
				try {
					helper.assertTrue(SchematicHandler.isShowingOutline(player), "no outline is shown");
					BlockPos anchor = helper.absolutePos(new BlockPos(4, 1, 4));
					AABB expected = new AABB(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getX() + 2,
						anchor.getY() + 2, anchor.getZ() + 2);
					helper.assertTrue(expected.equals(SchematicHandler.getOutlineBox(player)), "outline " + SchematicHandler.getOutlineBox(player));
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				} catch (RuntimeException e) {
					cleanUp(player);
					leave(helper, player);
					throw e;
				}
			});
			helper.runAfterDelay(8, () -> {
				try {
					helper.assertTrue(!SchematicHandler.isShowingOutline(player), "the outline stays after the item is put away");
				} finally {
					cleanUp(player);
					leave(helper, player);
				}
				helper.succeed();
			});
		} catch (RuntimeException e) {
			cleanUp(player);
			leave(helper, player);
			throw e;
		}
	}

	@GameTest(maxTicks = 40)
	public void boxOutlineIsTwelveEdgesOfDisplays(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		try {
			PlayerBoxOutline outline = new PlayerBoxOutline(player);
			AABB box = new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).expandTowards(3, 2, 1);
			outline.set(box);
			helper.assertTrue(outline.isShowing(), "not showing");
			helper.assertTrue(box.equals(outline.getBox()), "box " + outline.getBox());
			outline.tick();
			outline.set(null);
			helper.assertTrue(!outline.isShowing(), "still showing");
			helper.succeed();
		} finally {
			leave(helper, player);
		}
	}

	// ---- the schematicannon ----

	private static final BlockPos CANNON = new BlockPos(6, 1, 6);

	/** A cannon with a chest next to it that holds what the structure takes, and a deployed schematic of the structure */
	private static SchematicannonBlockEntity cannon(GameTestHelper helper, ServerPlayer player, BlockPos target, ItemStack... supplies) {
		helper.setBlock(CANNON, SchematicsRegistry.SCHEMATICANNON);
		helper.setBlock(CANNON.west(), Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(CANNON.west(), ChestBlockEntity.class);
		for (int i = 0; i < supplies.length; i++)
			chest.setItem(i, supplies[i]);
		build(helper, new BlockPos(1, 1, 1));
		String file = save(helper, player, new BlockPos(1, 1, 1), "cannon_test");
		clear(helper, new BlockPos(1, 1, 1));
		SchematicannonBlockEntity cannon = helper.getBlockEntity(CANNON, SchematicannonBlockEntity.class);
		cannon.inventory.setStackInSlot(0, deployed(helper, player, file, target));
		cannon.inventory.setStackInSlot(4, new ItemStack(Items.GUNPOWDER, 2));
		return cannon;
	}

	private static ItemStack[] supplies() {
		return new ItemStack[] { new ItemStack(Items.STONE, 10), new ItemStack(Items.OAK_STAIRS, 4), new ItemStack(Items.GLASS, 4),
			new ItemStack(Items.COBBLESTONE, 4), new ItemStack(Items.OAK_SLAB, 4), new ItemStack(Items.STONE_BRICKS, 4) };
	}

	@GameTest(maxTicks = 700)
	public void schematicannonBuildsASchematicFromAdjacentInventories(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		SchematicannonBlockEntity cannon;
		try {
			cannon = cannon(helper, player, new BlockPos(1, 1, 1), supplies());
		} catch (RuntimeException e) {
			cleanUp(player);
			leave(helper, player);
			throw e;
		}
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(cannon.state != State.RUNNING, "the cannon starts by itself");
			ConfigureSchematicannon.apply(cannon, Option.PLAY, true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cannon.statusMsg.equals("finished"), "the cannon is " + cannon.state + " / " + cannon.statusMsg
				+ ", placed " + cannon.blocksPlaced + " of " + cannon.blocksToPlace);
			helper.assertTrue(cannon.state == State.STOPPED, "the cannon is " + cannon.state);
			helper.assertTrue(cannon.flyingBlocks.isEmpty(), "blocks are still flying");
			// the structure is where the schematic says
			BlockPos base = new BlockPos(1, 1, 1);
			helper.assertTrue(helper.getBlockState(base).is(Blocks.STONE), "stone: " + helper.getBlockState(base));
			helper.assertTrue(helper.getBlockState(base.east()).is(Blocks.STONE), "stone 2");
			helper.assertTrue(helper.getBlockState(base.south())
				.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.WEST, "stairs " + helper.getBlockState(base.south()));
			helper.assertTrue(helper.getBlockState(base.south().east()).is(Blocks.GLASS), "glass");
			helper.assertTrue(helper.getBlockState(base.above()).is(Blocks.COBBLESTONE), "cobblestone");
			helper.assertTrue(helper.getBlockState(base.above().south())
				.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP, "slab " + helper.getBlockState(base.above().south()));
			helper.assertTrue(helper.getBlockState(base.above().south().east()).is(Blocks.STONE_BRICKS), "stone bricks");
			helper.assertTrue(helper.getBlockState(base.above().east()).isAir(), "air");
			// the gunpowder and the materials were used
			helper.assertTrue(cannon.inventory.getStackInSlot(4)
				.getCount() == 1, "gunpowder left: " + cannon.inventory.getStackInSlot(4));
			helper.assertTrue(cannon.remainingFuel > 0, "no fuel is left of the gunpowder");
			ChestBlockEntity chest = helper.getBlockEntity(CANNON.west(), ChestBlockEntity.class);
			helper.assertTrue(chest.getItem(0)
				.getCount() == 8, "stone left: " + chest.getItem(0));
			helper.assertTrue(chest.getItem(1)
				.getCount() == 3 && chest.getItem(2)
					.getCount() == 3, "stairs and glass left: " + chest.getItem(1) + ", " + chest.getItem(2));
			// the schematic is replaced by an empty one
			helper.assertTrue(cannon.inventory.getStackInSlot(0)
				.isEmpty(), "the schematic is still in the cannon");
			helper.assertTrue(cannon.inventory.getStackInSlot(1)
				.is(SchematicsRegistry.EMPTY_SCHEMATIC), "no empty schematic came out: " + cannon.inventory.getStackInSlot(1));
			cleanUp(player);
			leave(helper, player);
		});
	}

	@GameTest(maxTicks = 700)
	public void schematicannonKeepsSolidBlocksUnlessTheModeReplacesThem(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		SchematicannonBlockEntity cannon;
		BlockPos base = new BlockPos(1, 1, 1);
		try {
			cannon = cannon(helper, player, base, supplies());
			// stone where the schematic has glass, a flower where it has air
			helper.setBlock(base.south()
				.east(), Blocks.DIRT);
			helper.setBlock(base.above()
				.east(), Blocks.POPPY);
		} catch (RuntimeException e) {
			cleanUp(player);
			leave(helper, player);
			throw e;
		}
		// "don't replace solid blocks": only what is not solid may be replaced (air included)
		cannon.replaceMode = Option.DONT_REPLACE.ordinal();
		helper.runAfterDelay(2, () -> ConfigureSchematicannon.apply(cannon, Option.PLAY, true));
		helper.succeedWhen(() -> {
			helper.assertTrue(cannon.statusMsg.equals("finished"), "the cannon is " + cannon.state + " / " + cannon.statusMsg);
			helper.assertTrue(helper.getBlockState(base.south().east()).is(Blocks.DIRT),
				"the solid block was replaced: " + helper.getBlockState(base.south().east()));
			helper.assertTrue(helper.getBlockState(base).is(Blocks.STONE), "air was not built into");
			cleanUp(player);
			leave(helper, player);
		});
	}

	@GameTest(maxTicks = 700)
	public void schematicannonReplacesSolidBlocksInReplaceAnyMode(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		SchematicannonBlockEntity cannon;
		BlockPos base = new BlockPos(1, 1, 1);
		try {
			cannon = cannon(helper, player, base, supplies());
			helper.setBlock(base.south()
				.east(), Blocks.DIRT);
			helper.setBlock(base.above()
				.east(), Blocks.POPPY);
		} catch (RuntimeException e) {
			cleanUp(player);
			leave(helper, player);
			throw e;
		}
		cannon.replaceMode = Option.REPLACE_ANY.ordinal();
		helper.runAfterDelay(2, () -> ConfigureSchematicannon.apply(cannon, Option.PLAY, true));
		helper.succeedWhen(() -> {
			helper.assertTrue(cannon.statusMsg.equals("finished"), "the cannon is " + cannon.state + " / " + cannon.statusMsg);
			helper.assertTrue(helper.getBlockState(base.south().east()).is(Blocks.GLASS),
				"the solid block was kept: " + helper.getBlockState(base.south().east()));
			// air in the schematic does not remove anything unless the mode clears
			helper.assertTrue(helper.getBlockState(base.above().east()).is(Blocks.POPPY),
				"the flower was cleared: " + helper.getBlockState(base.above().east()));
			cleanUp(player);
			leave(helper, player);
		});
	}

	@GameTest(maxTicks = 700)
	public void schematicannonInReplaceEmptyModeClearsWhatTheSchematicLeavesAir(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		SchematicannonBlockEntity cannon;
		BlockPos base = new BlockPos(1, 1, 1);
		try {
			cannon = cannon(helper, player, base, supplies());
			helper.setBlock(base.above()
				.east(), Blocks.POPPY);
		} catch (RuntimeException e) {
			cleanUp(player);
			leave(helper, player);
			throw e;
		}
		cannon.replaceMode = Option.REPLACE_EMPTY.ordinal();
		helper.runAfterDelay(2, () -> ConfigureSchematicannon.apply(cannon, Option.PLAY, true));
		helper.succeedWhen(() -> {
			helper.assertTrue(cannon.statusMsg.equals("finished"), "the cannon is " + cannon.state + " / " + cannon.statusMsg);
			helper.assertTrue(helper.getBlockState(base.above().east()).isAir(),
				"the flower stays: " + helper.getBlockState(base.above().east()));
			cleanUp(player);
			leave(helper, player);
		});
	}

	@GameTest(maxTicks = 700)
	public void schematicannonPausesForMissingItemsAndSkipsThemWhenAsked(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		SchematicannonBlockEntity cannon;
		BlockPos base = new BlockPos(1, 1, 1);
		try {
			// no glass, no stone bricks
			cannon = cannon(helper, player, base, new ItemStack(Items.STONE, 10), new ItemStack(Items.OAK_STAIRS, 4),
				new ItemStack(Items.COBBLESTONE, 4), new ItemStack(Items.OAK_SLAB, 4));
		} catch (RuntimeException e) {
			cleanUp(player);
			leave(helper, player);
			throw e;
		}
		helper.runAfterDelay(2, () -> ConfigureSchematicannon.apply(cannon, Option.PLAY, true));
		helper.runAfterDelay(120, () -> {
			try {
				helper.assertTrue(cannon.state == State.PAUSED && cannon.statusMsg.equals("missingBlock"),
					"the cannon is " + cannon.state + " / " + cannon.statusMsg);
				helper.assertTrue(cannon.missingItem != null && cannon.missingItem.is(Items.GLASS), "missing " + cannon.missingItem);
				helper.assertTrue(helper.getBlockState(base.south().east()).isAir(), "glass appeared from nowhere");

				// skip what is missing: the rest is built
				ConfigureSchematicannon.apply(cannon, Option.SKIP_MISSING, true);
				ConfigureSchematicannon.apply(cannon, Option.PLAY, true);
			} catch (RuntimeException e) {
					cleanUp(player);
				leave(helper, player);
				throw e;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cannon.statusMsg.equals("finished"), "the cannon is " + cannon.state + " / " + cannon.statusMsg);
			helper.assertTrue(helper.getBlockState(base.south().east()).isAir(), "glass appeared from nowhere");
			helper.assertTrue(helper.getBlockState(base.above().south().east()).isAir(), "bricks appeared from nowhere");
			helper.assertTrue(helper.getBlockState(base.above()).is(Blocks.COBBLESTONE), "the rest was not built");
			cleanUp(player);
			leave(helper, player);
		});
	}

	@GameTest(maxTicks = 200)
	public void schematicannonStopsWithoutGunpowder(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		SchematicannonBlockEntity cannon;
		try {
			cannon = cannon(helper, player, new BlockPos(1, 1, 1), supplies());
			cannon.inventory.setStackInSlot(4, ItemStack.EMPTY);
		} catch (RuntimeException e) {
			cleanUp(player);
			leave(helper, player);
			throw e;
		}
		helper.runAfterDelay(2, () -> ConfigureSchematicannon.apply(cannon, Option.PLAY, true));
		helper.runAfterDelay(40, () -> {
			try {
				helper.assertTrue(cannon.state == State.PAUSED && cannon.statusMsg.equals("noGunpowder"),
					"the cannon is " + cannon.state + " / " + cannon.statusMsg);
				helper.assertTrue(cannon.flyingBlocks.isEmpty() && cannon.blocksPlaced == 0, "it fired without fuel");
				// gunpowder in the chest next to it is used too
				helper.getBlockEntity(CANNON.west(), ChestBlockEntity.class)
					.setItem(10, new ItemStack(Items.GUNPOWDER, 1));
				// Create's cannon waits for the player to start it again
				ConfigureSchematicannon.apply(cannon, Option.PLAY, true);
			} catch (RuntimeException e) {
				cleanUp(player);
				leave(helper, player);
				throw e;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cannon.blocksPlaced > 0, "the cannon did not take the gunpowder of the chest");
			helper.assertTrue(helper.getBlockEntity(CANNON.west(), ChestBlockEntity.class)
				.getItem(10)
				.isEmpty(), "the gunpowder of the chest is still there");
			cleanUp(player);
			leave(helper, player);
		});
	}

	@GameTest(maxTicks = 100)
	public void schematicannonGuiShowsTheSlotsAndStartsThePrinter(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		SchematicannonBlockEntity cannon;
		try {
			cannon = cannon(helper, player, new BlockPos(1, 1, 1), supplies());
		} catch (RuntimeException e) {
			cleanUp(player);
			leave(helper, player);
			throw e;
		}
		SchematicannonGui gui = new SchematicannonGui(player, cannon);
		try {
			gui.open();
			helper.assertTrue(cannon.state == State.STOPPED, "stopped at the start");
			// the buttons of the chest
			click(gui, SchematicannonGui.PLAY);
			helper.assertTrue(cannon.state == State.RUNNING, "play: " + cannon.state);
			click(gui, SchematicannonGui.PAUSE);
			helper.assertTrue(cannon.state == State.PAUSED, "pause: " + cannon.state);
			click(gui, SchematicannonGui.STOP);
			helper.assertTrue(cannon.state == State.STOPPED, "stop: " + cannon.state);

			click(gui, SchematicannonGui.REPLACE + Option.REPLACE_EMPTY.ordinal());
			helper.assertTrue(cannon.replaceMode == Option.REPLACE_EMPTY.ordinal(), "replace mode " + cannon.replaceMode);
			click(gui, SchematicannonGui.REPLACE + Option.DONT_REPLACE.ordinal());
			helper.assertTrue(cannon.replaceMode == Option.DONT_REPLACE.ordinal(), "replace mode " + cannon.replaceMode);
			boolean skip = cannon.skipMissing;
			click(gui, SchematicannonGui.SKIP_MISSING);
			helper.assertTrue(cannon.skipMissing != skip, "skip missing");
			boolean replaceBlockEntities = cannon.replaceBlockEntities;
			click(gui, SchematicannonGui.PROTECT);
			helper.assertTrue(cannon.replaceBlockEntities != replaceBlockEntities, "protect block entities");

			// the schematic slot is the slot of the inventory
			helper.assertTrue(gui.getSlotRedirectOrPlayer(SchematicannonGui.SCHEMATIC)
				.getItem()
				.is(SchematicsRegistry.SCHEMATIC), "the schematic slot does not show the schematic");
			helper.assertTrue(gui.getSlotRedirectOrPlayer(SchematicannonGui.GUNPOWDER)
				.getItem()
				.getCount() == 2, "the gunpowder slot");
			helper.assertTrue(!gui.getSlotRedirectOrPlayer(SchematicannonGui.SCHEMATIC_OUT)
				.mayPlace(new ItemStack(SchematicsRegistry.EMPTY_SCHEMATIC)), "the output takes items");
			helper.assertTrue(gui.getSlotRedirectOrPlayer(SchematicannonGui.GUNPOWDER)
				.mayPlace(new ItemStack(Items.GUNPOWDER)) && !gui.getSlotRedirectOrPlayer(SchematicannonGui.GUNPOWDER)
					.mayPlace(new ItemStack(Items.SAND)), "the gunpowder slot takes the wrong items");
			helper.succeed();
		} finally {
			gui.close();
			cleanUp(player);
			leave(helper, player);
		}
	}

	@GameTest(maxTicks = 200)
	public void schematicannonPrintsAChecklistOntoABook(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		SchematicannonBlockEntity cannon;
		try {
			cannon = cannon(helper, player, new BlockPos(1, 1, 1), new ItemStack(Items.STONE, 1));
			cannon.inventory.setStackInSlot(2, new ItemStack(Items.BOOK, 1));
		} catch (RuntimeException e) {
			cleanUp(player);
			leave(helper, player);
			throw e;
		}
		helper.succeedWhen(() -> {
			ItemStack book = cannon.inventory.getStackInSlot(3);
			helper.assertTrue(book.is(Items.WRITTEN_BOOK), "the output holds " + book);
			helper.assertTrue(cannon.inventory.getStackInSlot(2)
				.isEmpty(), "the book was not used");
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			String text = pagesText(content);
			// the structure takes 2 stone, the chest has 1: one is missing, and one of everything else
			helper.assertTrue(text.contains("Stone") && text.contains("Glass") && text.contains("x1"), "the book says: " + text);
			cleanUp(player);
			leave(helper, player);
		});
	}

	private static String pagesText(WrittenBookContent content) {
		StringBuilder text = new StringBuilder();
		for (var page : content.pages())
			text.append(page.raw()
				.getString())
				.append('\n');
		return text.toString();
	}

	// ---- requirements ----

	@GameTest(maxTicks = 40)
	public void materialChecklistCountsWhatTheStructureNeeds(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = player(helper);
		try {
			build(helper, new BlockPos(1, 1, 1));
			String file = save(helper, player, new BlockPos(1, 1, 1), "checklist_test");
			clear(helper, new BlockPos(1, 1, 1));
			ItemStack item = deployed(helper, player, file, new BlockPos(1, 1, 1));

			SchematicPrinter printer = new SchematicPrinter();
			printer.loadSchematic(item, helper.getLevel(), true);
			helper.assertTrue(printer.isLoaded() && !printer.isErrored(), "the printer did not load");
			MaterialChecklist checklist = new MaterialChecklist();
			int blocks = printer.markAllBlockRequirements(checklist, helper.getLevel(), (a, b, c, d, e, f) -> true);
			helper.assertTrue(blocks == 7, "blocks to place: " + blocks);
			helper.assertTrue(checklist.required.getInt(Items.STONE) == 2, "stone " + checklist.required.getInt(Items.STONE));
			helper.assertTrue(checklist.required.getInt(Items.OAK_STAIRS) == 1, "stairs");
			helper.assertTrue(checklist.required.getInt(Items.GLASS) == 1, "glass");
			helper.assertTrue(checklist.required.getInt(Items.COBBLESTONE) == 1, "cobblestone");
			helper.assertTrue(checklist.required.getInt(Items.OAK_SLAB) == 1, "slab");
			helper.assertTrue(checklist.required.getInt(Items.STONE_BRICKS) == 1, "bricks");

			// what is gathered is crossed off
			checklist.collect(new ItemStack(Items.STONE, 1));
			checklist.collect(new ItemStack(Items.DIAMOND, 5));
			helper.assertTrue(checklist.gathered.getInt(Items.STONE) == 1 && !checklist.gathered.containsKey(Items.DIAMOND),
				"gathered " + checklist.gathered);

			ItemStack book = checklist.createWrittenBook();
			helper.assertTrue(book.is(Items.WRITTEN_BOOK), "not a written book");
			String text = pagesText(book.get(DataComponents.WRITTEN_BOOK_CONTENT));
			helper.assertTrue(text.contains("Glass") && text.contains("Cobblestone") && text.contains("Stone Bricks"), "text: " + text);
			helper.assertTrue(text.contains("x1"), "amounts: " + text);
			helper.succeed();
		} finally {
			cleanUp(player);
			leave(helper, player);
		}
	}

	@GameTest(maxTicks = 20)
	public void itemRequirementsOfBlocks(GameTestHelper helper) {
		// plain blocks need their item
		ItemRequirement stone = ItemRequirement.of(Blocks.STONE.defaultBlockState(), null);
		helper.assertTrue(stone.getRequiredItems().size() == 1 && stone.getRequiredItems().get(0).stack.is(Items.STONE), "stone");
		helper.assertTrue(ItemRequirement.of(Blocks.AIR.defaultBlockState(), null).isEmpty(), "air needs something");
		helper.assertTrue(ItemRequirement.of(Blocks.WATER.defaultBlockState(), null).isInvalid(), "water has an item");
		// a double slab takes two
		ItemRequirement doubleSlab = ItemRequirement.of(Blocks.OAK_SLAB.defaultBlockState()
			.setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE), null);
		helper.assertTrue(doubleSlab.getRequiredItems().get(0).stack.getCount() == 2, "double slab");
		// farmland is made of dirt, snow layers and pickles count
		helper.assertTrue(ItemRequirement.of(Blocks.FARMLAND.defaultBlockState(), null).getRequiredItems().get(0).stack.is(Items.DIRT), "farmland");
		helper.assertTrue(ItemRequirement.of(Blocks.SNOW.defaultBlockState()
			.setValue(BlockStateProperties.LAYERS, 3), null).getRequiredItems().get(0).stack.getCount() == 3, "snow");
		helper.assertTrue(ItemRequirement.of(Blocks.TALL_GRASS.defaultBlockState(), null).getRequiredItems().get(0).stack.getCount() == 2, "tall grass");

		// Create's belts need a shaft per end and a connector at the start
		ItemRequirement belt = ItemRequirement.of(BeltRegistry.BELT.defaultBlockState()
			.setValue(dev.createpolymer.kinetics.belt.BeltBlock.PART, dev.createpolymer.kinetics.belt.BeltPart.START), null);
		helper.assertTrue(belt.getRequiredItems().size() == 2 && belt.getRequiredItems().get(1).stack.is(BeltRegistry.BELT_CONNECTOR), "start of a belt");
		ItemRequirement middle = ItemRequirement.of(BeltRegistry.BELT.defaultBlockState()
			.setValue(dev.createpolymer.kinetics.belt.BeltBlock.PART, dev.createpolymer.kinetics.belt.BeltPart.MIDDLE), null);
		helper.assertTrue(middle.isEmpty(), "the middle of a belt needs something");
		// encased blocks need what they are made of
		ItemRequirement encased = ItemRequirement.of(dev.createpolymer.registry.ModBlocks.ANDESITE_ENCASED_SHAFT.defaultBlockState(), null);
		helper.assertTrue(encased.getRequiredItems().get(0).stack.is(dev.createpolymer.registry.ModItems.SHAFT), "encased shaft");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void blockEntityDataThatSchematicsCopyIsFiltered(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		// a chest keeps its contents out of schematics
		helper.setBlock(pos, Blocks.CHEST);
		helper.getBlockEntity(pos, ChestBlockEntity.class)
			.setItem(0, new ItemStack(Items.DIAMOND, 3));
		var chestData = dev.createpolymer.foundation.block.BlockHelper.prepareBlockEntityData(helper.getLevel(),
			helper.getBlockState(pos), helper.getLevel()
				.getBlockEntity(helper.absolutePos(pos)));
		helper.assertTrue(chestData == null, "a chest is copied with its contents");

		// a sign with a click event is not copied at all
		BlockPos signPos = new BlockPos(4, 1, 2);
		helper.setBlock(signPos, Blocks.OAK_SIGN);
		var sign = (net.minecraft.world.level.block.entity.SignBlockEntity) helper.getLevel()
			.getBlockEntity(helper.absolutePos(signPos));
		var front = net.minecraft.world.level.block.entity.SignTextSlot.FRONT;
		sign.setText(sign.getText(front)
			.asMutable()
			.setLine(0, Component.literal("plain"))
			.asImmutable(), front);
		var safe = dev.createpolymer.foundation.block.BlockHelper.prepareBlockEntityData(helper.getLevel(),
			helper.getBlockState(signPos), sign);
		helper.assertTrue(safe != null, "a plain sign is not copied");
		sign.setText(sign.getText(front)
			.asMutable()
			.setLine(0, Component.literal("run")
				.withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/say hi"))))
			.asImmutable(), front);
		var unsafe = dev.createpolymer.foundation.block.BlockHelper.prepareBlockEntityData(helper.getLevel(),
			helper.getBlockState(signPos), sign);
		helper.assertTrue(unsafe == null, "a sign with a click event is copied");
		helper.assertTrue(NBTProcessors.textComponentHasClickEvent(Component.literal("a")
			.append(Component.literal("b")
				.withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/say hi"))))), "sibling click event");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void schematicItemFilesAreRegisteredWithTheirIds(GameTestHelper helper) {
		for (String id : new String[] { "schematic", "empty_schematic", "schematic_and_quill", "schematicannon", "schematic_table" })
			helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(dev.createpolymer.CreatePolymer.id(id)), id + " is not an item");
		for (String id : new String[] { "schematicannon", "schematic_table" })
			helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(dev.createpolymer.CreatePolymer.id(id)), id + " is not a block");
		helper.succeed();
	}
}

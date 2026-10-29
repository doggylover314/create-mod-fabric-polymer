package dev.createpolymer.test;

import static dev.createpolymer.test.TestUtil.floor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joml.Vector3fc;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.contraptions.ControlledContraptionEntity;
import dev.createpolymer.contraptions.actors.seat.SeatBlock;
import dev.createpolymer.contraptions.elevator.ElevatorContactBlockEntity;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock;
import dev.createpolymer.decoration.bracket.BracketBlock;
import dev.createpolymer.decoration.bracket.BracketBlock.BracketType;
import dev.createpolymer.decoration.bracket.BracketedBlockEntityBehaviour;
import dev.createpolymer.decoration.copycat.CopycatBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.foundation.recipe.ItemCopyingRecipe;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.kinetics.motor.CreativeMotorBlockEntity;
import dev.createpolymer.equipment.clipboard.ClipboardContent;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBehaviour;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlock;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlockEntity;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlock;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlockEntity;
import dev.createpolymer.redstone.nixieTube.DoubleFaceAttachedBlock.DoubleAttachFace;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlock;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlockEntity;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlock;
import dev.createpolymer.redstone.smartObserver.SmartObserverBlockEntity;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import dev.createpolymer.registry.feature.CopycatRegistry;
import dev.createpolymer.registry.feature.DisplayRegistry;
import dev.createpolymer.registry.feature.DoorsRegistry;
import dev.createpolymer.registry.feature.FactoryGaugeRegistry;
import dev.createpolymer.registry.feature.FilterRegistry;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.registry.feature.PulleyRegistry;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.registry.feature.SchedulesRegistry;
import dev.createpolymer.registry.feature.SignalsRegistry;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.registry.feature.ToolsRegistry;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.trains.signal.SignalBlockEntity;
import dev.createpolymer.trains.signal.SignalBlockEntity.SignalState;
import dev.createpolymer.trains.station.StationBlockEntity;
import dev.createpolymer.trains.track.TrackBlock;
import dev.createpolymer.trains.track.TrackShape;
import dev.createpolymer.visual.BlockstateModels;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

/**
 * Gaps that earlier ports left: the signal on a nixie tube, display sources, fluid filters, copying of filters and
 * clipboards, and brackets and copycat materials on contraptions.
 */
public class LeftoversGameTests {

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, message);
	}

	// ---- nixie tube signal ----

	private static final String TRAINS = "create_polymer_test:trains";
	private static final int Z = 3;

	private static void signalLine(GameTestHelper helper) {
		for (int x = 0; x <= 39; x++)
			helper.setBlock(new BlockPos(x, 1, Z), TrackRegistry.TRACK.defaultBlockState()
				.setValue(TrackBlock.SHAPE, TrackShape.XO));
	}

	/** A signal beside the track, like SignalGameTests places them. */
	private static SignalBlockEntity signalAt(GameTestHelper helper, int x) {
		BlockPos pos = new BlockPos(x, 1, Z + 1);
		helper.setBlock(pos, SignalsRegistry.TRACK_SIGNAL);
		SignalBlockEntity be = helper.getBlockEntity(pos, SignalBlockEntity.class);
		BlockPos abs = helper.absolutePos(pos);
		BlockPos trackPos = helper.absolutePos(new BlockPos(x, 1, Z));
		CompoundTag data = new CompoundTag();
		data.store("TargetTrack", BlockPos.CODEC, trackPos.subtract(abs));
		data.putBoolean("TargetDirection", true);
		be.edgePoint.read(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel()
			.registryAccess(), data));
		return be;
	}

	private static BlockState floorNixie() {
		return RedstoneRegistry.ORANGE_NIXIE_TUBE.defaultBlockState()
			.setValue(NixieTubeBlock.FACE, DoubleAttachFace.FLOOR)
			.setValue(NixieTubeBlock.FACING, Direction.NORTH);
	}

	/** The item displays of the tube that show something, and its text displays that show a character. */
	private static int[] shown(GameTestHelper helper, BlockPos pos) {
		BlockBoundAttachment attachment = BlockBoundAttachment.get(helper.getLevel(), helper.absolutePos(pos));
		check(helper, attachment != null, "the nixie tube has no model");
		int items = 0, texts = 0;
		for (VirtualElement element : attachment.holder()
			.getElements()) {
			if (element instanceof ItemDisplayElement item && !item.getItem()
				.isEmpty())
				items++;
			if (element instanceof TextDisplayElement text && !text.getText()
				.getString()
				.isEmpty())
				texts++;
		}
		return new int[] { items, texts };
	}

	@GameTest(structure = TRAINS, maxTicks = 200)
	public void nixieTubeShowsTheLampsOfTheSignalBehindIt(GameTestHelper helper) {
		signalLine(helper);
		SignalBlockEntity signal = signalAt(helper, 16);
		BlockPos nixiePos = new BlockPos(16, 2, Z + 1);
		BlockPos redstone = new BlockPos(16, 1, Z + 2);
		helper.setBlock(nixiePos, floorNixie());

		helper.runAfterDelay(12, () -> {
			NixieTubeBlockEntity nixie = helper.getBlockEntity(nixiePos, NixieTubeBlockEntity.class);
			check(helper, signal.getState() == SignalState.GREEN, "the signal is " + signal.getState());
			check(helper, nixie.signalState == SignalState.GREEN, "the tube sees " + nixie.signalState);
			// the tube's glass, the panel and the three pieces of the green lamp, no digits
			int[] now = shown(helper, nixiePos);
			check(helper, now[0] == 5 && now[1] == 0, "green: " + now[0] + " model pieces, " + now[1] + " digits");
			helper.setBlock(redstone, Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(30, () -> {
			NixieTubeBlockEntity nixie = helper.getBlockEntity(nixiePos, NixieTubeBlockEntity.class);
			check(helper, signal.getState() == SignalState.RED, "the signal is " + signal.getState());
			check(helper, nixie.signalState == SignalState.RED, "the tube sees " + nixie.signalState);
			int[] now = shown(helper, nixiePos);
			check(helper, now[0] == 5 && now[1] == 0, "red: " + now[0] + " model pieces, " + now[1] + " digits");
			helper.setBlock(new BlockPos(16, 1, Z + 1), Blocks.AIR);
		});
		helper.runAfterDelay(40, () -> {
			NixieTubeBlockEntity nixie = helper.getBlockEntity(nixiePos, NixieTubeBlockEntity.class);
			check(helper, nixie.signalState == null, "the tube still sees " + nixie.signalState);
			int[] now = shown(helper, nixiePos);
			check(helper, now[0] == 1, "model pieces of the signal remain: " + now[0]);
			check(helper, now[1] == 4, "the digits are not back: " + now[1]);
			helper.succeed();
		});
	}

	@GameTest
	public void nixieTubeWithoutASignalKeepsItsDigits(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, floorNixie());
		helper.runAfterDelay(8, () -> {
			NixieTubeBlockEntity nixie = helper.getBlockEntity(pos, NixieTubeBlockEntity.class);
			check(helper, nixie.signalState == null, "signal state " + nixie.signalState);
			check(helper, nixie.getDisplayedStrings().getFirst().equals("0"), "digit " + nixie.getDisplayedStrings().getFirst());
			helper.succeed();
		});
	}

	// ---- display sources ----

	private static CompoundTag config(String source) {
		CompoundTag tag = new CompoundTag();
		tag.putString("Id", "create:" + source);
		return tag;
	}

	private static String signLine(GameTestHelper helper, BlockPos sign, int line) {
		return helper.getBlockEntity(sign, SignBlockEntity.class)
			.getText(SignTextSlot.FRONT)
			.getMessages(false)
			.get(line)
			.getString();
	}

	private static DisplayLinkBlockEntity link(GameTestHelper helper, BlockPos link, Direction facing, BlockPos sign) {
		helper.setBlock(link, DisplayRegistry.DISPLAY_LINK.defaultBlockState()
			.setValue(DisplayLinkBlock.FACING, facing));
		DisplayLinkBlockEntity be = helper.getBlockEntity(link, DisplayLinkBlockEntity.class);
		be.target(helper.absolutePos(sign));
		return be;
	}

	@GameTest
	public void theRemainingDisplaySourcesAreOfferedForTheirBlocks(GameTestHelper helper) {
		floor(helper);
		BlockPos station = new BlockPos(1, 1, 1);
		BlockPos contact = new BlockPos(2, 1, 1);
		BlockPos gauge = new BlockPos(3, 1, 1);
		BlockPos seat = new BlockPos(4, 1, 1);
		BlockPos observer = new BlockPos(5, 1, 1);
		helper.setBlock(station, TrainsRegistry.TRACK_STATION);
		helper.setBlock(contact, PulleyRegistry.ELEVATOR_CONTACT);
		helper.setBlock(gauge, FactoryGaugeRegistry.FACTORY_GAUGE.defaultBlockState()
			.setValue(FactoryPanelBlock.FACE, AttachFace.FLOOR)
			.setValue(FactoryPanelBlock.FACING, Direction.NORTH));
		check(helper, helper.getBlockEntity(gauge, FactoryPanelBlockEntity.class)
			.addPanel(PanelSlot.BOTTOM_LEFT, UUID.randomUUID()), "the panel could not be added");
		helper.setBlock(seat, ActorsRegistry.SEATS.get(DyeColor.RED));
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER);
		helper.runAfterDelay(2, () -> {
			var level = helper.getLevel();
			check(helper, DisplaySource.getAll(level, helper.absolutePos(station)).contains(DisplayRegistry.TRAIN_STATUS), "train status");
			check(helper, DisplaySource.getAll(level, helper.absolutePos(contact)).equals(List.of(DisplayRegistry.CURRENT_FLOOR)), "current floor");
			check(helper, DisplaySource.getAll(level, helper.absolutePos(gauge)).equals(List.of(DisplayRegistry.GAUGE_STATUS)), "gauge status");
			check(helper, DisplaySource.getAll(level, helper.absolutePos(seat)).equals(List.of(DisplayRegistry.ENTITY_NAME)), "entity name");
			check(helper, DisplaySource.getAll(level, helper.absolutePos(observer)).contains(DisplayRegistry.READ_PACKAGE_ADDRESS), "package address");
			for (DyeColor color : DyeColor.values())
				check(helper, DisplaySource.get(net.minecraft.resources.Identifier.parse("create:" + color.getSerializedName() + "_seat_source_entity_name"))
					== DisplayRegistry.ENTITY_NAME, "legacy seat source of " + color);
			check(helper, DisplaySource.get(net.minecraft.resources.Identifier.parse("create:elevator_contact_source_current_floor")) == DisplayRegistry.CURRENT_FLOOR, "legacy floor");
			check(helper, DisplaySource.get(net.minecraft.resources.Identifier.parse("create:track_station_source_train_status")) == DisplayRegistry.TRAIN_STATUS, "legacy status");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void currentFloorGoesToASign(GameTestHelper helper) {
		floor(helper);
		BlockPos contact = new BlockPos(2, 1, 2);
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(contact, PulleyRegistry.ELEVATOR_CONTACT);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		DisplayLinkBlockEntity link = link(helper, contact.above(), Direction.UP, sign);
		helper.runAfterDelay(3, () -> {
			helper.getBlockEntity(contact, ElevatorContactBlockEntity.class).lastReportedCurrentFloor = "Lobby";
			link.applySettings(config("current_floor"), 0);
			check(helper, link.activeSource == DisplayRegistry.CURRENT_FLOOR, "source " + link.activeSource);
			check(helper, signLine(helper, sign, 0).equals("Lobby"), "sign '" + signLine(helper, sign, 0) + "'");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void seatedEntityNameGoesToASign(GameTestHelper helper) {
		floor(helper);
		BlockPos seat = new BlockPos(2, 1, 2);
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(seat, ActorsRegistry.SEATS.get(DyeColor.RED));
		helper.setBlock(sign, Blocks.OAK_SIGN);
		DisplayLinkBlockEntity link = link(helper, seat.above(), Direction.UP, sign);
		Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, 4, 1, 4);
		pig.setCustomName(net.minecraft.network.chat.Component.literal("Wilbur"));
		helper.runAfterDelay(3, () -> {
			link.applySettings(config("entity_name"), 0);
			check(helper, link.activeSource == DisplayRegistry.ENTITY_NAME, "source " + link.activeSource);
			check(helper, signLine(helper, sign, 0).isBlank(), "empty seat shows '" + signLine(helper, sign, 0) + "'");
			SeatBlock.sitDown(helper.getLevel(), helper.absolutePos(seat), pig);
			link.updateGatheredData();
			check(helper, signLine(helper, sign, 0).equals("Wilbur"), "seated pig shows '" + signLine(helper, sign, 0) + "'");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void packageAddressGoesToASign(GameTestHelper helper) {
		floor(helper);
		BlockPos chest = new BlockPos(2, 1, 2);
		BlockPos observer = chest.above();
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER.defaultBlockState()
			.setValue(SmartObserverBlock.TARGET, AttachFace.FLOOR)
			.setValue(SmartObserverBlock.FACING, Direction.NORTH));
		helper.setBlock(sign, Blocks.OAK_SIGN);
		DisplayLinkBlockEntity link = link(helper, observer.east(), Direction.EAST, sign);
		helper.runAfterDelay(3, () -> {
			ItemStack first = PackageItem.containing(List.of(new ItemStack(Items.STICK)));
			PackageItem.addAddress(first, "Warehouse");
			ItemStack second = PackageItem.containing(List.of(new ItemStack(Items.COAL)));
			PackageItem.addAddress(second, "Harbour");
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND));
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(1, first);
			helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(2, second);
		});
		helper.runAfterDelay(25, () -> {
			link.applySettings(config("read_package_address"), 0);
			check(helper, link.activeSource == DisplayRegistry.READ_PACKAGE_ADDRESS, "source " + link.activeSource);
			check(helper, signLine(helper, sign, 0).equals("Warehouse"), "first package '" + signLine(helper, sign, 0) + "'");

			// the filter picks the package by its address
			ItemStack filter = new ItemStack(ToolsRegistry.PACKAGE_FILTER);
			filter.set(PackagesRegistry.PACKAGE_ADDRESS, "Harbour");
			helper.getBlockEntity(observer, SmartObserverBlockEntity.class)
				.getBehaviour(FilteringBehaviour.TYPE)
				.setFilter(filter);
			link.updateGatheredData();
			check(helper, signLine(helper, sign, 0).equals("Harbour"), "filtered package '" + signLine(helper, sign, 0) + "'");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void gaugeStatusListsTheLinkedGauges(GameTestHelper helper) {
		floor(helper);
		BlockPos gaugePos = new BlockPos(2, 1, 2);
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(gaugePos, FactoryGaugeRegistry.FACTORY_GAUGE.defaultBlockState()
			.setValue(FactoryPanelBlock.FACE, AttachFace.FLOOR)
			.setValue(FactoryPanelBlock.FACING, Direction.NORTH));
		FactoryPanelBlockEntity gauge = helper.getBlockEntity(gaugePos, FactoryPanelBlockEntity.class);
		check(helper, gauge.addPanel(PanelSlot.BOTTOM_LEFT, UUID.randomUUID()), "the panel could not be added");
		FactoryPanelBehaviour panel = gauge.panels.get(PanelSlot.BOTTOM_LEFT);
		panel.setFilter(new ItemStack(Items.IRON_INGOT));
		panel.count = 16;
		panel.upTo = true;

		helper.setBlock(sign, Blocks.OAK_SIGN);
		DisplayLinkBlockEntity link = link(helper, new BlockPos(2, 1, 4), Direction.UP, sign);
		link.factoryPanelSupport.connect(panel);
		helper.runAfterDelay(3, () -> {
			check(helper, link.getSourcePosition().equals(helper.absolutePos(gaugePos)), "source " + link.getSourcePosition());
			link.applySettings(config("gauge_status"), 0);
			check(helper, link.activeSource == DisplayRegistry.GAUGE_STATUS, "source " + link.activeSource);
			String line = signLine(helper, sign, 0);
			check(helper, line.contains("Iron Ingot"), "sign '" + line + "'");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void trainStatusIsBlankWithoutATrain(GameTestHelper helper) {
		floor(helper);
		BlockPos station = new BlockPos(2, 1, 2);
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(station, TrainsRegistry.TRACK_STATION);
		helper.setBlock(sign, Blocks.OAK_SIGN);
		DisplayLinkBlockEntity link = link(helper, station.above(), Direction.UP, sign);
		helper.runAfterDelay(3, () -> {
			check(helper, helper.getBlockEntity(station, StationBlockEntity.class).getStation() == null, "the station has an edge point");
			link.applySettings(config("train_status"), 0);
			check(helper, link.activeSource == DisplayRegistry.TRAIN_STATUS, "source " + link.activeSource);
			check(helper, signLine(helper, sign, 0).isBlank(), "sign '" + signLine(helper, sign, 0) + "'");
			helper.succeed();
		});
	}

	// ---- fluid filters ----

	@GameTest
	public void filteringBehaviourTestsFluids(GameTestHelper helper) {
		floor(helper);
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, RedstoneRegistry.SMART_OBSERVER);
		FilteringBehaviour filtering = helper.getBlockEntity(pos, SmartObserverBlockEntity.class)
			.getBehaviour(FilteringBehaviour.TYPE);
		FluidStack water = new FluidStack(Fluids.WATER, 1000);
		FluidStack lava = new FluidStack(Fluids.LAVA, 1000);
		check(helper, filtering.test(water) && filtering.test(lava), "an empty filter lets fluids pass");

		filtering.setFilter(new ItemStack(Items.WATER_BUCKET));
		check(helper, filtering.test(water), "a water bucket lets water pass");
		check(helper, !filtering.test(lava), "a water bucket stops lava");
		check(helper, !filtering.test(FluidStack.EMPTY), "no fluid does not pass a filter");

		// an item that holds no fluid matches none
		filtering.setFilter(new ItemStack(Items.STICK));
		check(helper, !filtering.test(water), "a stick passes water");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void observerReactsToFluidsThatPassItsFilter(GameTestHelper helper) {
		floor(helper);
		BlockPos tank = new BlockPos(2, 1, 2);
		BlockPos observer = tank.above();
		helper.setBlock(tank, TanksRegistry.FLUID_TANK);
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER.defaultBlockState()
			.setValue(SmartObserverBlock.TARGET, AttachFace.FLOOR)
			.setValue(SmartObserverBlock.FACING, Direction.NORTH));
		FilteringBehaviour filtering = helper.getBlockEntity(observer, SmartObserverBlockEntity.class)
			.getBehaviour(FilteringBehaviour.TYPE);
		filtering.setFilter(new ItemStack(Items.LAVA_BUCKET));
		helper.runAfterDelay(3, () -> FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(tank), null)
			.fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE));
		helper.runAfterDelay(30, () -> {
			check(helper, !helper.getBlockState(observer)
				.getValue(SmartObserverBlock.POWERED), "the observer reacts to water with a lava filter");
			filtering.setFilter(new ItemStack(Items.WATER_BUCKET));
		});
		helper.runAfterDelay(50, () -> {
			check(helper, helper.getBlockState(observer)
				.getValue(SmartObserverBlock.POWERED), "the observer does not react to water with a water filter");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void fluidSourcesFollowTheObserversFluidFilter(GameTestHelper helper) {
		floor(helper);
		BlockPos tank = new BlockPos(2, 1, 2);
		BlockPos observer = tank.above();
		BlockPos sign = new BlockPos(5, 1, 5);
		helper.setBlock(tank, TanksRegistry.FLUID_TANK);
		helper.setBlock(observer, RedstoneRegistry.SMART_OBSERVER.defaultBlockState()
			.setValue(SmartObserverBlock.TARGET, AttachFace.FLOOR)
			.setValue(SmartObserverBlock.FACING, Direction.NORTH));
		helper.setBlock(sign, Blocks.OAK_SIGN);
		DisplayLinkBlockEntity link = link(helper, observer.east(), Direction.EAST, sign);
		helper.runAfterDelay(3, () -> FluidHandlerLookup.get(helper.getLevel(), helper.absolutePos(tank), null)
			.fill(new FluidStack(Fluids.WATER, 3000), FluidAction.EXECUTE));
		helper.runAfterDelay(10, () -> {
			link.applySettings(config("count_fluids"), 0);
			check(helper, signLine(helper, sign, 0).equals("3000 mB"), "amount '" + signLine(helper, sign, 0) + "'");
			FilteringBehaviour filtering = helper.getBlockEntity(observer, SmartObserverBlockEntity.class)
				.getBehaviour(FilteringBehaviour.TYPE);
			filtering.setFilter(new ItemStack(Items.LAVA_BUCKET));
			link.updateGatheredData();
			check(helper, !signLine(helper, sign, 0).equals("3000 mB"), "amount with a lava filter '" + signLine(helper, sign, 0) + "'");
			filtering.setFilter(new ItemStack(Items.WATER_BUCKET));
			link.updateGatheredData();
			check(helper, signLine(helper, sign, 0).equals("3000 mB"), "amount with a water filter '" + signLine(helper, sign, 0) + "'");
			helper.succeed();
		});
	}

	// ---- item copying ----

	@SuppressWarnings("unchecked")
	private static <T> ItemStack withComponent(ItemStack stack, DataComponentType<?> type, T value) {
		stack.set((DataComponentType<T>) type, value);
		return stack;
	}

	/** Each copyable item with a value for its component. */
	private static List<ItemStack> copyable() {
		List<ItemStack> list = new ArrayList<>();
		list.add(withComponent(new ItemStack(FilterRegistry.FILTER), FilterRegistry.FILTER_ITEMS, ItemContainerContents.EMPTY));
		list.add(withComponent(new ItemStack(ToolsRegistry.ATTRIBUTE_FILTER), ToolsRegistry.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES,
			List.<dev.createpolymer.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry>of()));
		list.add(withComponent(new ItemStack(ToolsRegistry.PACKAGE_FILTER), PackagesRegistry.PACKAGE_ADDRESS, "Warehouse"));
		list.add(withComponent(new ItemStack(ToolsRegistry.CLIPBOARD_ITEM), ToolsRegistry.CLIPBOARD_CONTENT, ClipboardContent.EMPTY));
		CompoundTag schedule = new CompoundTag();
		schedule.putInt("Cyclic", 1);
		list.add(withComponent(new ItemStack(SchedulesRegistry.SCHEDULE), SchedulesRegistry.TRAIN_SCHEDULE, schedule));
		return list;
	}

	@GameTest
	public void itemCopyingRecipeIsRegisteredAndLoaded(GameTestHelper helper) {
		check(helper, net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER
			.getValue(CreatePolymer.id("item_copying")) == ItemCopyingRecipe.SERIALIZER, "serializer is not registered");
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, CreatePolymer.id("crafting/curiosities/item_copying"));
		var holder = helper.getLevel().getServer().getRecipeManager().byKey(key);
		check(helper, holder.isPresent() && holder.get().value() instanceof ItemCopyingRecipe, "the recipe was not loaded: " + holder);
		helper.succeed();
	}

	@GameTest
	public void filtersClipboardsAndSchedulesCanBeCopied(GameTestHelper helper) {
		ItemCopyingRecipe recipe = ItemCopyingRecipe.INSTANCE;
		for (ItemStack original : copyable()) {
			ItemStack empty = new ItemStack(original.getItem());
			String name = original.getItem().toString();

			CraftingInput one = CraftingInput.of(2, 1, List.of(original, empty));
			check(helper, recipe.matches(one, helper.getLevel()), name + ": original and empty copy do not match");
			ItemStack copy = recipe.assemble(one);
			check(helper, copy.is(original.getItem()) && copy.getCount() == 2, name + ": result " + copy);
			DataComponentType<?> type = ((dev.createpolymer.foundation.recipe.ItemCopyingRecipe.SupportsItemCopying) original.getItem()).getComponentType();
			check(helper, copy.has(type), name + ": the copy lost its content");

			CraftingInput two = CraftingInput.of(2, 2, List.of(original, new ItemStack(original.getItem()), new ItemStack(original.getItem()), ItemStack.EMPTY));
			check(helper, recipe.matches(two, helper.getLevel()) && recipe.assemble(two).getCount() == 3, name + ": two targets give three");

			// the original alone, two originals, or a target that is already written do not copy
			check(helper, !recipe.matches(CraftingInput.of(1, 1, List.of(original)), helper.getLevel()), name + ": a lone item matches");
			check(helper, !recipe.matches(CraftingInput.of(2, 1, List.of(original, original.copy())), helper.getLevel()), name + ": two written items match");
			check(helper, !recipe.matches(CraftingInput.of(2, 1, List.of(empty, new ItemStack(original.getItem()))), helper.getLevel()), name + ": two empty items match");
			// something that cannot hold the content spoils it
			check(helper, !recipe.matches(CraftingInput.of(2, 1, List.of(original, new ItemStack(Items.PAPER))), helper.getLevel()), name + ": paper matches");
		}
		helper.succeed();
	}

	@GameTest
	public void copiesLoseEnchantmentsAndOtherFiltersDoNotMix(GameTestHelper helper) {
		ItemCopyingRecipe recipe = ItemCopyingRecipe.INSTANCE;
		ItemStack original = copyable().get(0);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.set(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 1);
		original.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
		ItemStack copy = recipe.assemble(CraftingInput.of(2, 1, List.of(original, new ItemStack(FilterRegistry.FILTER))));
		check(helper, copy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(), "the copy kept the enchantment glint");

		// a filter and an empty attribute filter are not the same item
		check(helper, !recipe.matches(CraftingInput.of(2, 1, List.of(copyable().get(0), new ItemStack(ToolsRegistry.ATTRIBUTE_FILTER))), helper.getLevel()), "different filters matched");
		helper.succeed();
	}

	// ---- brackets and copycats on contraptions ----

	private static final BlockPos PISTON = new BlockPos(2, 3, 2);

	private static List<DisplayElement> contraptionDisplays(GameTestHelper helper) {
		List<ControlledContraptionEntity> list = helper.getLevel()
			.getEntitiesOfClass(ControlledContraptionEntity.class, new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(16));
		check(helper, list.size() == 1, "expected one contraption entity, found " + list.size());
		check(helper, list.get(0).getVisual() != null, "no visual");
		return list.get(0).getVisual().getDisplayElements();
	}

	@GameTest(maxTicks = 120)
	public void bracketsAndCopycatMaterialsAreDrawnOnContraptions(GameTestHelper helper) {
		floor(helper);
		for (int y = 1; y <= 2; y++)
			helper.setBlock(new BlockPos(2, y, 2), ContraptionRegistry.PISTON_EXTENSION_POLE.defaultBlockState()
				.setValue(MechanicalPistonBlock.FACING, Direction.UP));
		helper.setBlock(PISTON, ContraptionRegistry.MECHANICAL_PISTON.defaultBlockState()
			.setValue(MechanicalPistonBlock.FACING, Direction.UP)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));

		// a copycat panel of stone (its 3 pixels at the bottom), with a bracketed shaft on top of it
		BlockPos copycat = new BlockPos(2, 4, 2);
		BlockPos shaft = new BlockPos(2, 5, 2);
		helper.setBlock(copycat, CopycatRegistry.COPYCAT_PANEL.defaultBlockState()
			.setValue(dev.createpolymer.decoration.copycat.CopycatPanelBlock.FACING, Direction.UP));
		helper.getBlockEntity(copycat, CopycatBlockEntity.class)
			.setMaterial(Blocks.STONE.defaultBlockState());
		helper.setBlock(shaft, ModBlocks.SHAFT.defaultBlockState()
			.setValue(RotatedPillarKineticBlock.AXIS, Axis.X));
		BlockState bracket = DoorsRegistry.METAL_BRACKET.defaultBlockState()
			.setValue(BracketBlock.TYPE, BracketType.SHAFT)
			.setValue(BracketBlock.FACING, Direction.UP);
		BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(shaft), BracketedBlockEntityBehaviour.TYPE)
			.applyBracket(bracket);

		BlockPos motor = PISTON.north();
		helper.setBlock(motor, ModBlocks.CREATIVE_MOTOR.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
		helper.runAfterDelay(2, () -> helper.getBlockEntity(motor, CreativeMotorBlockEntity.class)
			.setSetting(-16));

		helper.runAfterDelay(30, () -> {
			List<DisplayElement> displays = contraptionDisplays(helper);

			// the stone of the copycat, squeezed into the panel: full width, 3/16 high
			BlockDisplayElement stone = null;
			for (DisplayElement element : displays)
				if (element instanceof BlockDisplayElement block && block.getBlockState().is(Blocks.STONE))
					stone = block;
			check(helper, stone != null, "the copycat's material is not drawn");
			Vector3fc scale = stone.getScale();
			check(helper, Math.abs(scale.x() - 1) < 1e-4 && Math.abs(scale.z() - 1) < 1e-4 && Math.abs(scale.y() - 3 / 16f) < 1e-4,
				"the material is scaled by " + scale);

			// the bracket is an item display with Create's bracket model
			ItemStack bracketModel = BlockstateModels.get("metal_bracket", bracket)
				.stack();
			boolean bracketShown = false;
			for (DisplayElement element : displays)
				if (element instanceof ItemDisplayElement item && ItemStack.isSameItemSameComponents(item.getItem(), bracketModel))
					bracketShown = true;
			check(helper, bracketShown, "the bracket is not drawn");
			helper.succeed();
		});
	}
}

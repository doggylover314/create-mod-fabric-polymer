package dev.createpolymer.test;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBoard;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsInputHandler;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour;
import dev.createpolymer.foundation.item.ItemHelper.ExtractionCountMode;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.createpolymer.foundation.gui.FilterGui;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.registry.feature.FilterRegistry;
import dev.createpolymer.util.BlockFace;
import eu.pb4.sgui.api.ClickType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class FilterGameTests {

	/** A SmartBlockEntity with no block of its own: it borrows the chest type and is only given behaviours. */
	private static class TestBlockEntity extends SmartBlockEntity {
		TestBlockEntity(BlockPos pos) {
			super(BlockEntityTypes.CHEST, pos, Blocks.CHEST.defaultBlockState());
		}
	}

	private static TestBlockEntity testBlockEntity(GameTestHelper helper, BlockPos relative) {
		TestBlockEntity be = new TestBlockEntity(helper.absolutePos(relative));
		be.setLevel(helper.getLevel());
		return be;
	}

	private static ItemStack listFilter(boolean blacklist, boolean respectData, ItemStack... contents) {
		ItemStack filter = new ItemStack(FilterRegistry.FILTER);
		List<ItemStack> stacks = new ArrayList<>(List.of(contents));
		filter.set(FilterRegistry.FILTER_ITEMS, ItemContainerContents.fromItems(stacks));
		filter.set(FilterRegistry.FILTER_ITEMS_BLACKLIST, blacklist);
		filter.set(FilterRegistry.FILTER_ITEMS_RESPECT_NBT, respectData);
		return filter;
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	@GameTest
	public void plainItemFilter(GameTestHelper helper) {
		TestBlockEntity be = testBlockEntity(helper, new BlockPos(1, 1, 1));
		FilteringBehaviour filtering = new FilteringBehaviour(be, new CenteredSideValueBoxTransform());
		be.attachBehaviourLate(filtering);

		check(helper, filtering.test(new ItemStack(Items.DIRT)), "an empty filter lets everything through");
		check(helper, filtering.setFilter(new ItemStack(Items.IRON_INGOT)), "setFilter accepted");
		check(helper, filtering.test(new ItemStack(Items.IRON_INGOT)), "iron passes an iron filter");
		check(helper, !filtering.test(new ItemStack(Items.GOLD_INGOT)), "gold is rejected by an iron filter");
		check(helper, filtering.getFilter().is(Items.IRON_INGOT), "filter item is kept");

		filtering.onlyActiveWhen(() -> false);
		check(helper, filtering.test(new ItemStack(Items.GOLD_INGOT)), "an inactive filter lets everything through");
		helper.succeed();
	}

	@GameTest
	public void filterPredicateRejectsItems(GameTestHelper helper) {
		TestBlockEntity be = testBlockEntity(helper, new BlockPos(1, 1, 1));
		FilteringBehaviour filtering = new FilteringBehaviour(be, new CenteredSideValueBoxTransform())
			.withPredicate(stack -> !stack.is(Items.DIRT));
		be.attachBehaviourLate(filtering);
		check(helper, !filtering.setFilter(new ItemStack(Items.DIRT)), "dirt is refused");
		check(helper, filtering.getFilter().isEmpty(), "filter stays empty");
		check(helper, filtering.setFilter(new ItemStack(Items.STONE)), "stone is accepted");
		check(helper, filtering.setFilter(ItemStack.EMPTY), "clearing is always accepted");
		helper.succeed();
	}

	@GameTest
	public void listFilterAllowList(GameTestHelper helper) {
		TestBlockEntity be = testBlockEntity(helper, new BlockPos(1, 1, 1));
		FilteringBehaviour filtering = new FilteringBehaviour(be, new CenteredSideValueBoxTransform());
		be.attachBehaviourLate(filtering);

		filtering.setFilter(listFilter(false, false, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT)));
		check(helper, filtering.getFilter().is(FilterRegistry.FILTER), "the filter item is stored");
		check(helper, filtering.test(new ItemStack(Items.IRON_INGOT)), "listed iron passes");
		check(helper, filtering.test(new ItemStack(Items.COPPER_INGOT)), "listed copper passes");
		check(helper, !filtering.test(new ItemStack(Items.GOLD_INGOT)), "unlisted gold is rejected");

		filtering.setFilter(listFilter(false, false));
		check(helper, !filtering.test(new ItemStack(Items.IRON_INGOT)), "an empty allow list rejects everything");
		helper.succeed();
	}

	@GameTest
	public void listFilterDenyList(GameTestHelper helper) {
		TestBlockEntity be = testBlockEntity(helper, new BlockPos(1, 1, 1));
		FilteringBehaviour filtering = new FilteringBehaviour(be, new CenteredSideValueBoxTransform());
		be.attachBehaviourLate(filtering);

		filtering.setFilter(listFilter(true, false, new ItemStack(Items.IRON_INGOT)));
		check(helper, !filtering.test(new ItemStack(Items.IRON_INGOT)), "listed iron is denied");
		check(helper, filtering.test(new ItemStack(Items.GOLD_INGOT)), "unlisted gold passes");

		filtering.setFilter(listFilter(true, false));
		check(helper, filtering.test(new ItemStack(Items.IRON_INGOT)), "an empty deny list accepts everything");
		helper.succeed();
	}

	@GameTest
	public void listFilterRespectsData(GameTestHelper helper) {
		ItemStack renamed = new ItemStack(Items.IRON_INGOT);
		renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Special"));
		ItemStack plain = new ItemStack(Items.IRON_INGOT);

		TestBlockEntity be = testBlockEntity(helper, new BlockPos(1, 1, 1));
		FilteringBehaviour filtering = new FilteringBehaviour(be, new CenteredSideValueBoxTransform());
		be.attachBehaviourLate(filtering);

		filtering.setFilter(listFilter(false, false, renamed));
		check(helper, filtering.test(plain), "ignoring data, a plain ingot matches a renamed one");
		filtering.setFilter(listFilter(false, true, renamed));
		check(helper, !filtering.test(plain), "respecting data, a plain ingot does not match a renamed one");
		check(helper, filtering.test(renamed.copy()), "respecting data, an identical stack matches");
		helper.succeed();
	}

	@GameTest
	public void listFilterNestsAndKeepsComponents(GameTestHelper helper) {
		// A list filter inside a list filter matches what the inner one matches
		ItemStack inner = listFilter(false, false, new ItemStack(Items.STONE));
		FilterItemStack outer = FilterItemStack.of(listFilter(false, false, inner));
		check(helper, outer.test(helper.getLevel(), new ItemStack(Items.STONE)), "nested filter passes stone");
		check(helper, !outer.test(helper.getLevel(), new ItemStack(Items.DIRT)), "nested filter rejects dirt");

		// The filter's data survives a save and load, the way a block entity stores its filter slot
		ItemStack filter = listFilter(true, true, new ItemStack(Items.IRON_INGOT));
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		var encoded = ItemStack.OPTIONAL_CODEC.encodeStart(ops, filter).getOrThrow();
		ItemStack decoded = ItemStack.OPTIONAL_CODEC.parse(ops, encoded).getOrThrow();
		check(helper, decoded.getOrDefault(FilterRegistry.FILTER_ITEMS_BLACKLIST, false), "blacklist flag survives");
		check(helper, decoded.getOrDefault(FilterRegistry.FILTER_ITEMS_RESPECT_NBT, false), "respect data flag survives");
		FilterItemStack wrapped = FilterItemStack.of(decoded);
		check(helper, !wrapped.test(helper.getLevel(), new ItemStack(Items.IRON_INGOT)), "decoded deny list denies iron");
		check(helper, wrapped.test(helper.getLevel(), new ItemStack(Items.GOLD_INGOT)), "decoded deny list passes gold");
		helper.succeed();
	}

	@GameTest
	public void filteringBehaviourSavesAndLoads(GameTestHelper helper) {
		Level level = helper.getLevel();
		TestBlockEntity be = testBlockEntity(helper, new BlockPos(1, 1, 1));
		FilteringBehaviour filtering = new FilteringBehaviour(be, new CenteredSideValueBoxTransform()).showCount();
		be.attachBehaviourLate(filtering);
		filtering.setFilter(listFilter(false, false, new ItemStack(Items.IRON_INGOT)));
		filtering.count = 16;
		filtering.upTo = false;

		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		filtering.write(out);
		CompoundTag tag = out.buildResult();

		TestBlockEntity other = testBlockEntity(helper, new BlockPos(2, 1, 1));
		FilteringBehaviour loaded = new FilteringBehaviour(other, new CenteredSideValueBoxTransform());
		other.attachBehaviourLate(loaded);
		loaded.read(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));

		check(helper, loaded.getFilter().is(FilterRegistry.FILTER), "filter item loaded");
		check(helper, loaded.test(new ItemStack(Items.IRON_INGOT)) && !loaded.test(new ItemStack(Items.GOLD_INGOT)), "loaded list filter works");
		check(helper, loaded.count == 16 && !loaded.upTo, "amount and mode loaded");
		helper.succeed();
	}

	@GameTest
	public void sidedFilters(GameTestHelper helper) {
		TestBlockEntity be = testBlockEntity(helper, new BlockPos(1, 1, 1));
		SidedFilteringBehaviour sided = new SidedFilteringBehaviour(be, new CenteredSideValueBoxTransform(),
			(side, filter) -> filter, side -> side.getAxis().isHorizontal());
		be.attachBehaviourLate(sided);

		check(helper, sided.get(Direction.NORTH) != null && sided.get(Direction.UP) == null, "filters only exist on valid sides");
		sided.setFilter(Direction.NORTH, new ItemStack(Items.STONE));
		check(helper, sided.test(Direction.NORTH, new ItemStack(Items.STONE)), "stone passes on the north filter");
		check(helper, !sided.test(Direction.NORTH, new ItemStack(Items.DIRT)), "dirt is rejected on the north filter");
		check(helper, sided.test(Direction.SOUTH, new ItemStack(Items.DIRT)), "the empty south filter lets dirt through");
		check(helper, sided.getFilter(Direction.NORTH).is(Items.STONE) && sided.getFilter(Direction.SOUTH).isEmpty(), "filters are kept per side");

		Level level = helper.getLevel();
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		sided.write(out);
		TestBlockEntity other = testBlockEntity(helper, new BlockPos(2, 1, 1));
		SidedFilteringBehaviour loaded = new SidedFilteringBehaviour(other, new CenteredSideValueBoxTransform(),
			(side, filter) -> filter, side -> side.getAxis().isHorizontal());
		other.attachBehaviourLate(loaded);
		loaded.read(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
		check(helper, loaded.getFilter(Direction.NORTH).is(Items.STONE), "sided filters load");
		helper.succeed();
	}

	@GameTest
	public void valueBoxHitTest(GameTestHelper helper) {
		CenteredSideValueBoxTransform transform = new CenteredSideValueBoxTransform();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		var state = Blocks.CHEST.defaultBlockState();
		var level = helper.getLevel();

		transform.fromSide(Direction.SOUTH);
		Vec3 offset = transform.getLocalOffset(level, pos, state);
		check(helper, Math.abs(offset.x - .5) < 1e-6 && Math.abs(offset.y - .5) < 1e-6 && Math.abs(offset.z - 15.5 / 16) < 1e-6,
			"south value box sits on the south face, was " + offset);
		check(helper, transform.testHit(level, pos, state, new Vec3(.5, .5, .97)), "centre of the south box is a hit");
		check(helper, !transform.testHit(level, pos, state, new Vec3(.5, .5, .5)), "the block centre is not a hit");

		transform.fromSide(Direction.UP);
		offset = transform.getLocalOffset(level, pos, state);
		check(helper, Math.abs(offset.y - 15.5 / 16) < 1e-6, "up value box sits on the top face, was " + offset);

		// transform() moves a matrix to the box, like Create does with its pose stack
		Matrix4f m = new Matrix4f();
		transform.fromSide(Direction.EAST);
		transform.transform(level, pos, state, m);
		Vec3 east = transform.getLocalOffset(level, pos, state);
		check(helper, Math.abs(m.m30() - east.x) < 1e-5 && Math.abs(m.m31() - east.y) < 1e-5 && Math.abs(m.m32() - east.z) < 1e-5,
			"transform translates to the box");
		check(helper, Math.abs(m.transformDirection(new Vector3f(1, 0, 0)).length() - transform.getScale()) < 1e-5,
			"transform scales by the box scale");
		helper.succeed();
	}

	@GameTest
	public void valueSettings(GameTestHelper helper) {
		TestBlockEntity be = testBlockEntity(helper, new BlockPos(1, 1, 1));
		FilteringBehaviour filtering = new FilteringBehaviour(be, new CenteredSideValueBoxTransform()).showCount();
		be.attachBehaviourLate(filtering);
		filtering.setFilter(new ItemStack(Items.STONE));
		check(helper, filtering.acceptsValueSettings(), "stackable filter accepts amounts");
		filtering.setFilter(new ItemStack(Items.DIAMOND_SWORD));
		check(helper, !filtering.acceptsValueSettings(), "an unstackable filter has no amount");
		filtering.setFilter(new ItemStack(Items.STONE));
		filtering.setValueSettings(null, new ValueSettings(1, 16), false);
		check(helper, filtering.count == 16 && !filtering.upTo, "exact 16 stored");
		check(helper, filtering.getValueSettings().equals(new ValueSettings(1, 16)), "value settings round trip");
		filtering.setValueSettings(null, new ValueSettings(0, 500), false);
		check(helper, filtering.count == 64 && filtering.upTo, "amount is clamped to the stack size");

		ValueSettingsBoard board = filtering.createBoard(null, new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false));
		check(helper, board.maxValue() == 64 && board.rows().size() == 2 && board.milestoneInterval() == 16, "filter board");
		check(helper, board.formatter().format(new ValueSettings(1, 5)).getString().equals("=5"), "exact amounts read =n");

		ScrollValueBehaviour scroll = new ScrollValueBehaviour(Component.literal("Speed"), be, new CenteredSideValueBoxTransform())
			.between(0, 5);
		be.attachBehaviourLate(scroll);
		scroll.setValueSettings(null, new ValueSettings(0, 10), false);
		check(helper, scroll.getValue() == 5, "scroll value is clamped to its maximum");
		check(helper, scroll.createBoard(null, null).maxValue() == 5, "scroll board maximum");
		helper.succeed();
	}

	@GameTest
	public void rightClickSetsFilter(GameTestHelper helper) {
		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, Blocks.CHEST);
		BlockPos pos = helper.absolutePos(rel);
		TestBlockEntity be = new TestBlockEntity(pos);
		FilteringBehaviour filtering = new FilteringBehaviour(be, new CenteredSideValueBoxTransform());
		be.setLevel(helper.getLevel());
		be.attachBehaviourLate(filtering);
		helper.getLevel().setBlockEntity(be);

		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 4));

		BlockHitResult miss = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(.5, .5, .5), Direction.SOUTH, pos, false);
		check(helper, ValueSettingsInputHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND, miss) == InteractionResult.PASS,
			"clicking beside the value box is not handled");
		check(helper, filtering.getFilter().isEmpty(), "no filter set by a miss");

		BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(.5, .5, .97), Direction.SOUTH, pos, false);
		check(helper, ValueSettingsInputHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit) == InteractionResult.SUCCESS,
			"clicking the value box is handled");
		check(helper, filtering.getFilter().is(Items.IRON_INGOT), "the held item became the filter");
		check(helper, player.getMainHandItem().getCount() == 4, "plain items are not consumed");

		// a list filter in hand is consumed and handed back when replaced
		player.setItemInHand(InteractionHand.MAIN_HAND, listFilter(false, false, new ItemStack(Items.COAL)));
		ValueSettingsInputHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
		check(helper, filtering.getFilter().is(FilterRegistry.FILTER) && player.getMainHandItem().isEmpty(), "filter item moved into the slot");
		check(helper, filtering.test(new ItemStack(Items.COAL)) && !filtering.test(new ItemStack(Items.IRON_INGOT)), "slotted list filter is active");

		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
		check(helper, ValueSettingsInputHandler.onBlockActivated(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
			"sneaking players are left alone");
		helper.succeed();
	}

	@GameTest
	public void filterGuiEditsTheHeldFilter(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ItemStack filter = new ItemStack(FilterRegistry.FILTER);
		player.setItemInHand(InteractionHand.MAIN_HAND, filter);
		FilterGui gui = new FilterGui(player, filter);

		player.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT, 5));
		gui.onClick(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		gui.onClick(1, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, player.containerMenu.getCarried().getCount() == 5, "the cursor stack is untouched");

		FilterItemStack wrapped = FilterItemStack.of(filter);
		check(helper, wrapped.test(helper.getLevel(), new ItemStack(Items.IRON_INGOT)), "iron was added to the filter");
		check(helper, !wrapped.test(helper.getLevel(), new ItemStack(Items.GOLD_INGOT)), "gold was not");

		player.containerMenu.setCarried(ItemStack.EMPTY);
		gui.onClick(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, filter.has(FilterRegistry.FILTER_ITEMS), "one slot is still filled");
		gui.onClick(1, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		check(helper, !filter.has(FilterRegistry.FILTER_ITEMS), "clearing every slot removes the contents component");

		// the allow/deny toggle is saved straight into the item
		player.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT));
		gui.onClick(2, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, null);
		gui.getGuiElement(20).getGuiCallback().click(20, ClickType.MOUSE_LEFT, ContainerInput.PICKUP, gui);
		check(helper, filter.getOrDefault(FilterRegistry.FILTER_ITEMS_BLACKLIST, false), "deny list saved");
		wrapped = FilterItemStack.of(filter);
		check(helper, !wrapped.test(helper.getLevel(), new ItemStack(Items.IRON_INGOT)) && wrapped.test(helper.getLevel(), new ItemStack(Items.GOLD_INGOT)),
			"the item now works as a deny list");
		helper.succeed();
	}

	@GameTest
	public void invManipulationExtractsFromChest(GameTestHelper helper) {
		BlockPos chestRel = new BlockPos(3, 1, 2);
		helper.setBlock(chestRel, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(chestRel, ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 5));
		chest.setItem(2, new ItemStack(Items.IRON_INGOT, 7));

		TestBlockEntity be = testBlockEntity(helper, new BlockPos(2, 1, 2));
		FilteringBehaviour filtering = new FilteringBehaviour(be, new CenteredSideValueBoxTransform());
		be.attachBehaviourLate(filtering);
		InvManipulationBehaviour inventory = new InvManipulationBehaviour(be, (w, p, s) -> new BlockFace(p, Direction.EAST));
		be.attachBehaviourLate(inventory);

		inventory.findNewCapability();
		check(helper, inventory.hasInventory(), "the chest next to the block entity is found");

		check(helper, inventory.extract(ExtractionCountMode.UPTO, 8, stack -> stack.is(Items.GOLD_INGOT)).getCount() == 5,
			"only gold, only what is there");
		filtering.setFilter(new ItemStack(Items.IRON_INGOT));
		ItemStack simulated = inventory.simulate().extract(ExtractionCountMode.UPTO, 12);
		check(helper, simulated.is(Items.IRON_INGOT) && simulated.getCount() == 12, "a simulated extraction of 12 iron reports 12, was " + simulated);
		check(helper, chest.getItem(0).getCount() == 10 && chest.getItem(2).getCount() == 7, "simulating takes nothing");

		filtering.count = 64;
		ItemStack all = inventory.extract();
		check(helper, all.is(Items.IRON_INGOT) && all.getCount() == 17, "extract() takes every iron up to the filter amount, was " + all);
		check(helper, chest.getItem(0).isEmpty() && chest.getItem(2).isEmpty(), "chest iron is gone");

		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
		check(helper, inventory.extract(ExtractionCountMode.EXACTLY, 4).isEmpty(), "an exact amount that is not there extracts nothing");
		check(helper, chest.getItem(0).getCount() == 3, "and takes nothing");
		check(helper, inventory.extract(ExtractionCountMode.EXACTLY, 3).getCount() == 3, "an exact amount that is there is extracted");

		ItemStack rest = inventory.insert(new ItemStack(Items.STONE, 70));
		check(helper, rest.isEmpty(), "inserting stone fits into the chest");
		int stone = 0;
		for (int i = 0; i < chest.getContainerSize(); i++)
			if (chest.getItem(i).is(Items.STONE))
				stone += chest.getItem(i).getCount();
		check(helper, stone == 70, "70 stone ended up in the chest, was " + stone);
		helper.succeed();
	}
}

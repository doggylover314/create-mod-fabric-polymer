package dev.createpolymer.equipment.zapper;

import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The parts of Create's BlockHelper that the worldshaper and the wand of symmetry use (setZeroAge, getRequiredItem,
 * findAndRemoveInInventory), and Create's safe_nbt block tag as code: the blocks whose block entity data the
 * worldshaper may copy.
 */
public final class ZapperBlockHelper {
	private ZapperBlockHelper() {}

	private static final List<IntegerProperty> COUNT_STATES = List.of(
		BlockStateProperties.EGGS,
		BlockStateProperties.PICKLES,
		BlockStateProperties.CANDLES
	);

	private static final List<Block> VINELIKE_BLOCKS = List.of(
		Blocks.VINE, Blocks.GLOW_LICHEN
	);

	private static final List<BooleanProperty> VINELIKE_STATES = List.of(
		BlockStateProperties.UP,
		BlockStateProperties.NORTH,
		BlockStateProperties.EAST,
		BlockStateProperties.SOUTH,
		BlockStateProperties.WEST,
		BlockStateProperties.DOWN
	);

	/** The entries of Create's create:safe_nbt block tag that are ids (it also holds the banner and sign tags). */
	private static final Set<String> SAFE_NBT = Set.of("create:creative_motor", "create:creative_fluid_tank",
		"create:mechanical_piston", "create:sticky_mechanical_piston", "create:windmill_bearing",
		"create:mechanical_bearing", "create:clockwork_bearing", "create:rope_pulley", "create:cart_assembler",
		"create:linear_chassis", "create:secondary_linear_chassis", "create:radial_chassis",
		"create:sequenced_gearshift", "create:rotation_speed_controller", "create:andesite_funnel",
		"create:andesite_belt_funnel", "create:brass_funnel", "create:brass_belt_funnel", "create:redstone_link",
		"create:analog_lever", "create:placard", "create:pulse_repeater", "create:pulse_extender",
		"create:pulse_timer", "create:clipboard");

	/** Create's AllBlockTags.SAFE_NBT.matches(state) */
	public static boolean isSafeNbt(BlockState state) {
		return state.is(BlockTags.BANNERS) || state.is(BlockTags.ALL_SIGNS)
			|| SAFE_NBT.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock())
				.toString());
	}

	/**
	 * Stands in for Catnip's NBTProcessors on the block entity data the worldshaper copies: data that carries a click
	 * event (signs, lecterns and books can run commands with them) is refused. Returns null when the data is refused.
	 */
	public static CompoundTag processBlockEntityData(CompoundTag data) {
		return containsClickEvent(data) ? null : data;
	}

	private static boolean containsClickEvent(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			for (String key : compound.keySet()) {
				if (key.equals("click_event") || key.equals("clickEvent"))
					return true;
				if (containsClickEvent(compound.get(key)))
					return true;
			}
		} else if (tag instanceof ListTag list) {
			for (Tag element : list)
				if (containsClickEvent(element))
					return true;
		} else if (tag instanceof StringTag string) {
			String value = string.value();
			return value.contains("click_event") || value.contains("clickEvent");
		}
		return false;
	}

	public static BlockState setZeroAge(BlockState blockState) {
		if (blockState.hasProperty(BlockStateProperties.AGE_1))
			return blockState.setValue(BlockStateProperties.AGE_1, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_2))
			return blockState.setValue(BlockStateProperties.AGE_2, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_3))
			return blockState.setValue(BlockStateProperties.AGE_3, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_5))
			return blockState.setValue(BlockStateProperties.AGE_5, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_7))
			return blockState.setValue(BlockStateProperties.AGE_7, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_15))
			return blockState.setValue(BlockStateProperties.AGE_15, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_25))
			return blockState.setValue(BlockStateProperties.AGE_25, 0);
		if (blockState.hasProperty(BlockStateProperties.LEVEL_HONEY))
			return blockState.setValue(BlockStateProperties.LEVEL_HONEY, 0);
		if (blockState.hasProperty(BlockStateProperties.HATCH))
			return blockState.setValue(BlockStateProperties.HATCH, 0);
		if (blockState.hasProperty(BlockStateProperties.STAGE))
			return blockState.setValue(BlockStateProperties.STAGE, 0);
		if (blockState.is(BlockTags.CAULDRONS))
			return Blocks.CAULDRON.defaultBlockState();
		if (blockState.hasProperty(BlockStateProperties.LEVEL_COMPOSTER))
			return blockState.setValue(BlockStateProperties.LEVEL_COMPOSTER, 0);
		if (blockState.hasProperty(BlockStateProperties.EXTENDED))
			return blockState.setValue(BlockStateProperties.EXTENDED, false);
		return blockState;
	}

	public static ItemStack getRequiredItem(BlockState state) {
		ItemStack itemStack = new ItemStack(state.getBlock());
		Item item = itemStack.getItem();
		if (item == Items.FARMLAND || item == Items.DIRT_PATH)
			itemStack = new ItemStack(Items.DIRT);
		return itemStack;
	}

	/**
	 * Takes the items that placing {@code block} costs from the player's inventory, the selected slot first, and
	 * returns how many blocks' worth were found.
	 */
	public static int findAndRemoveInInventory(BlockState block, Player player, int amount) {
		int amountFound = 0;
		Item required = getRequiredItem(block).getItem();

		boolean needsTwo = block.hasProperty(BlockStateProperties.SLAB_TYPE)
			&& block.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE;

		if (needsTwo)
			amount *= 2;

		for (IntegerProperty property : COUNT_STATES)
			if (block.hasProperty(property))
				amount *= block.getValue(property);

		if (VINELIKE_BLOCKS.contains(block.getBlock())) {
			int vineCount = 0;

			for (BooleanProperty vineState : VINELIKE_STATES) {
				if (block.hasProperty(vineState) && block.getValue(vineState)) {
					vineCount++;
				}
			}

			amount += vineCount - 1;
		}

		Inventory inventory = player.getInventory();

		{
			// Try held Item first
			ItemStack itemstack = inventory.getItem(inventory.getSelectedSlot());
			int count = itemstack.getCount();
			if (itemstack.getItem() == required && count > 0) {
				int taken = Math.min(count, amount - amountFound);
				itemstack.shrink(taken);
				amountFound += taken;
			}
		}

		// Search inventory
		for (int i = 0; i < inventory.getContainerSize(); ++i) {
			if (amountFound == amount)
				break;

			ItemStack itemstack = inventory.getItem(i);
			int count = itemstack.getCount();
			if (itemstack.getItem() == required && count > 0) {
				int taken = Math.min(count, amount - amountFound);
				itemstack.shrink(taken);
				amountFound += taken;
			}
		}

		if (needsTwo) {
			// Give back 1 if uneven amount was removed
			if (amountFound % 2 != 0)
				inventory.add(new ItemStack(required));
			amountFound /= 2;
		}

		return amountFound;
	}
}

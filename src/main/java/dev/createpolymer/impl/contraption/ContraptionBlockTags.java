package dev.createpolymer.impl.contraption;

import java.util.function.Predicate;

import dev.createpolymer.CreatePolymer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's AllBlockTags used by contraptions (create:brittle, create:movable_empty_collider, create:non_movable,
 * create:seats), as code instead of data: the membership is what Create's data generation writes. Blocks that are not
 * registered (other mods, Create blocks that are not ported yet) are simply ignored.
 */
public enum ContraptionBlockTags {
	/** Blocks that can never be moved by a contraption (Create only lists optional Immersive Engineering blocks). */
	NON_MOVABLE(state -> false),

	/** Blocks without collision that contraptions move anyway. */
	MOVABLE_EMPTY_COLLIDER(state -> state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.TRIPWIRE)
		|| state.is(Blocks.TRIPWIRE_HOOK) || state.is(Blocks.BAMBOO_SAPLING) || state.is(Blocks.VINE)
		|| state.is(Blocks.TWISTING_VINES_PLANT) || state.is(Blocks.TWISTING_VINES)
		|| state.is(Blocks.WEEPING_VINES_PLANT) || state.is(Blocks.WEEPING_VINES)
		|| isCoral(state) || state.is(BlockTags.CAVE_VINES) || state.is(BlockTags.FENCE_GATES)
		|| state.is(BlockTags.BANNERS)),

	/** Blocks that need a supporting block, so they are moved before it (and are not supportive themselves). */
	BRITTLE(state -> matchesIds(state, "create:nozzle", "create:hand_crank", "create:copper_valve_handle",
		"create:rope", "create:pulley_magnet", "create:redstone_link", "create:peculiar_bell",
		"create:haunted_bell", "minecraft:bell", "minecraft:cocoa", "minecraft:flower_pot", "minecraft:moss_carpet",
		"minecraft:bamboo_sapling", "minecraft:big_dripleaf", "minecraft:vine", "minecraft:twisting_vines_plant",
		"minecraft:twisting_vines", "minecraft:weeping_vines_plant", "minecraft:weeping_vines", "minecraft:cake")
		|| isValveHandle(state) || isCoral(state) || state.is(BlockTags.CAVE_VINES) || state.is(BlockTags.BANNERS)
		|| state.is(BlockTags.BEDS) || state.is(BlockTags.DOORS)),

	SEATS(state -> isSeat(state));

	private final Predicate<BlockState> predicate;

	ContraptionBlockTags(Predicate<BlockState> predicate) {
		this.predicate = predicate;
	}

	public boolean matches(BlockState state) {
		return predicate.test(state);
	}

	public boolean matches(Block block) {
		return matches(block.defaultBlockState());
	}

	private static boolean matchesIds(BlockState state, String... ids) {
		Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		String id = key.toString();
		for (String s : ids)
			if (s.equals(id))
				return true;
		return false;
	}

	private static boolean isValveHandle(BlockState state) {
		Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return key.getNamespace().equals(CreatePolymer.MOD_ID) && key.getPath().endsWith("_valve_handle");
	}

	private static boolean isSeat(BlockState state) {
		Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return key.getNamespace().equals(CreatePolymer.MOD_ID) && key.getPath().endsWith("_seat");
	}

	/** Create's create:corals tag: every coral, coral fan and wall fan, dead or alive (not the coral blocks). */
	private static boolean isCoral(BlockState state) {
		if (state.is(BlockTags.CORALS) || state.is(BlockTags.WALL_CORALS) || state.is(BlockTags.CORAL_PLANTS))
			return true;
		Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		String path = key.getPath();
		return key.getNamespace().equals("minecraft") && path.startsWith("dead_") && path.contains("coral")
			&& !path.endsWith("_block");
	}
}

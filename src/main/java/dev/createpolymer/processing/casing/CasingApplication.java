package dev.createpolymer.processing.casing;

import java.util.List;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's item application recipes for the brass and copper casing: using an ingot on a stripped log or
 * stripped wood turns the block into the casing and uses up the ingot. (The andesite casing does the same
 * through the andesite alloy item.)
 */
public final class CasingApplication {
	private record Application(TagKey<Item> ingredient, Supplier<Block> casing) {}

	private static final TagKey<Block> STRIPPED_LOGS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "stripped_logs"));
	private static final TagKey<Block> STRIPPED_WOODS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "stripped_woods"));

	private static final List<Application> APPLICATIONS = new java.util.ArrayList<>();

	private CasingApplication() {}

	/** {@code ingredient}: item tag such as "c:ingots/brass". */
	public static void add(String ingredient, Supplier<Block> casing) {
		APPLICATIONS.add(new Application(TagKey.create(Registries.ITEM, Identifier.parse(ingredient)), casing));
	}

	public static void registerEvents() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
			apply(level, player, player.getItemInHand(hand), hit.getBlockPos()));
	}

	public static InteractionResult apply(Level level, Player player, ItemStack held, BlockPos pos) {
		if (held.isEmpty() || player.isSpectator() || !player.mayBuild())
			return InteractionResult.PASS;
		BlockState state = level.getBlockState(pos);
		if (!(state.is(STRIPPED_LOGS) || state.is(STRIPPED_WOODS)))
			return InteractionResult.PASS;
		for (Application application : APPLICATIONS) {
			if (!held.is(application.ingredient))
				continue;
			if (level.isClientSide())
				return InteractionResult.SUCCESS;

			BlockState casing = application.casing.get().defaultBlockState();
			level.setBlockAndUpdate(pos, casing);
			level.playSound(null, pos, casing.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1, 1);
			level.levelEvent(2001, pos, Block.getId(state));
			if (!player.isCreative())
				held.shrink(1);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}

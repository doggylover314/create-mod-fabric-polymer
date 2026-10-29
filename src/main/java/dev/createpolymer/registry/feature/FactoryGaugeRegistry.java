package dev.createpolymer.registry.feature;

import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlock;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlockEntity;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlockItem;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelConnectionHandler;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Create's factory gauges: panels on the face of a block (up to four per block), each monitoring one item of a
 * logistics network, or of the packager behind it, and ordering what is missing.
 */
public final class FactoryGaugeRegistry {
	private FactoryGaugeRegistry() {}

	/** Create's CLogistics.factoryGaugeTimer: ticks a factory gauge waits between requests. */
	public static int factoryGaugeTimer = 100;

	// Create: SharedProperties.copperMetal (copper block) with noOcclusion and forceSolidOn
	public static final FactoryPanelBlock FACTORY_GAUGE = ModBlocks.register("factory_gauge", FactoryPanelBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering()
			.unaffected())
			.noOcclusion()
			.forceSolidOn());

	public static final BlockEntityType<FactoryPanelBlockEntity> FACTORY_PANEL_BLOCK_ENTITY = ModBlockEntities.register(
		"factory_panel", (pos, state) -> new FactoryPanelBlockEntity(FactoryGaugeRegistry.FACTORY_PANEL_BLOCK_ENTITY, pos, state),
		FACTORY_GAUGE);

	public static final Item FACTORY_GAUGE_ITEM = ModItems.register("factory_gauge",
		p -> new FactoryPanelBlockItem(FACTORY_GAUGE, p), new Item.Properties().useBlockDescriptionPrefix());

	private static boolean initialized;

	public static void init() {
		if (initialized)
			return;
		initialized = true;

		FactoryPanelConnectionHandler.init();

		// A block with several panels only loses the one the player looks at (Create: onDestroyedByPlayer)
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (state.getBlock() instanceof FactoryPanelBlock gauge && !level.isClientSide())
				return !gauge.tryDestroySubPanelFirst(state, level, pos, player);
			return true;
		});
	}

	/** Create's AllSoundEvents.DENY: a note block bass. */
	public static void deny(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1f, .5f);
	}

	/** Create's AllSoundEvents.CONFIRM and CONFIRM_2 at the volumes the gauge uses: two quiet dings. */
	public static void confirm(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, .5f * .075f, .8f);
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, .125f, .575f);
	}
}

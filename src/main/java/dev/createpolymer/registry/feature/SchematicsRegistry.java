package dev.createpolymer.registry.feature;

import java.util.function.Predicate;

import com.mojang.serialization.Codec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.schematics.NBTProcessors;
import dev.createpolymer.schematics.SchematicAndQuillHandler;
import dev.createpolymer.schematics.SchematicAndQuillItem;
import dev.createpolymer.schematics.SchematicHandler;
import dev.createpolymer.schematics.SchematicItem;
import dev.createpolymer.schematics.SchematicProcessor;
import dev.createpolymer.schematics.cannon.SchematicannonBlock;
import dev.createpolymer.schematics.cannon.SchematicannonBlockEntity;
import dev.createpolymer.schematics.cannon.SchematicannonBlockEntity.SchematicannonOptions;
import dev.createpolymer.schematics.table.SchematicTableBlock;
import dev.createpolymer.schematics.table.SchematicTableBlockEntity;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Create's schematics: the schematic and quill that saves a region to a file, the empty schematic and the schematic
 * that holds a structure and where it is placed, the table that writes a file onto an empty schematic, and the
 * schematicannon that builds a placed schematic from the items next to it (see {@code dev.createpolymer.schematics}).
 * <p>
 * Create's clients keep their schematics in a folder of their own, show a hologram of the schematic and have a tool
 * screen to place it. A vanilla client has none of that, so the files are the ones in the server's
 * {@code schematics/} folder and in {@code schematics/uploaded/<player>/}, the hologram is the outline of its box,
 * and the tools are a chest GUI. The components of the schematic item are server-only, like all components of this
 * port.
 */
public final class SchematicsRegistry {
	private SchematicsRegistry() {}

	// ---- config (Create's CSchematics, section "schematics" of create.json) ----

	/** Whether placing a Schematic directly in Creative Mode should replace world blocks with Air */
	public static boolean creativePrintIncludesAir = false;
	/** The amount of Schematics a player can upload until previous ones are overwritten */
	public static int maxSchematics = 10;
	/** The maximum allowed file size of uploaded Schematics, in KiloBytes */
	public static int maxTotalSchematicSize = 256;
	/** The packet size Schematics are split into; here it sets how long the schematic table takes to write one */
	public static int maxSchematicPacketSize = 1024;
	/** Create's idle timeout for uploads: there are no uploads that could idle here, the setting is kept for the file */
	public static int schematicIdleTimeout = 600;
	/** Amount of game ticks between shots of the cannon. Higher => Slower */
	public static int schematicannonDelay = 10;
	/** Amount of blocks a Schematicannon can print per Gunpowder item provided */
	public static int schematicannonShotsPerGunpowder = 400;

	// ---- data components (server only) ----

	public static final DataComponentType<Boolean> SCHEMATIC_DEPLOYED = component("schematic_deployed",
		DataComponentType.<Boolean>builder()
			.persistent(Codec.BOOL)
			.build());
	public static final DataComponentType<String> SCHEMATIC_OWNER = component("schematic_owner",
		DataComponentType.<String>builder()
			.persistent(Codec.STRING)
			.build());
	public static final DataComponentType<String> SCHEMATIC_FILE = component("schematic_file",
		DataComponentType.<String>builder()
			.persistent(Codec.STRING)
			.build());
	public static final DataComponentType<BlockPos> SCHEMATIC_ANCHOR = component("schematic_anchor",
		DataComponentType.<BlockPos>builder()
			.persistent(BlockPos.CODEC)
			.build());
	public static final DataComponentType<Rotation> SCHEMATIC_ROTATION = component("schematic_rotation",
		DataComponentType.<Rotation>builder()
			.persistent(Rotation.CODEC)
			.build());
	public static final DataComponentType<Mirror> SCHEMATIC_MIRROR = component("schematic_mirror",
		DataComponentType.<Mirror>builder()
			.persistent(Mirror.CODEC)
			.build());
	public static final DataComponentType<Vec3i> SCHEMATIC_BOUNDS = component("schematic_bounds",
		DataComponentType.<Vec3i>builder()
			.persistent(Vec3i.CODEC)
			.build());
	public static final DataComponentType<Integer> SCHEMATIC_HASH = component("schematic_hash",
		DataComponentType.<Integer>builder()
			.persistent(Codec.INT)
			.build());
	public static final DataComponentType<SchematicannonOptions> SCHEMATICANNON_OPTIONS = component("schematicannon_options",
		DataComponentType.<SchematicannonOptions>builder()
			.persistent(SchematicannonOptions.CODEC)
			.build());

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, CreatePolymer.id(id), type);
		PolymerComponent.registerDataComponent(type);
		return type;
	}

	// ---- structure processor ----

	static {
		Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, CreatePolymer.id("schematic"), SchematicProcessor.CODEC);
		RegistrySyncUtils.setServerEntry(BuiltInRegistries.STRUCTURE_PROCESSOR, SchematicProcessor.CODEC);
	}

	// ---- blocks ----

	/** Create: initialProperties(() -> Blocks.DISPENSER), MapColor.COLOR_GRAY */
	public static final SchematicannonBlock SCHEMATICANNON = ModBlocks.register("schematicannon", SchematicannonBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.DISPENSER)
			.mapColor(MapColor.COLOR_GRAY)
			.noOcclusion());
	/** Create: initialProperties(() -> Blocks.LECTERN), MapColor.PODZOL, forceSolidOn */
	public static final SchematicTableBlock SCHEMATIC_TABLE = ModBlocks.register("schematic_table", SchematicTableBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN)
			.mapColor(MapColor.PODZOL)
			.pushReaction(PushReaction.IMMOVEABLE)
			.noOcclusion()
			.forceSolidOn());

	// ---- block entities ----

	public static final BlockEntityType<SchematicannonBlockEntity> SCHEMATICANNON_BLOCK_ENTITY = ModBlockEntities.register("schematicannon",
		(pos, state) -> new SchematicannonBlockEntity(SchematicsRegistry.SCHEMATICANNON_BLOCK_ENTITY, pos, state), SCHEMATICANNON);
	public static final BlockEntityType<SchematicTableBlockEntity> SCHEMATIC_TABLE_BLOCK_ENTITY = ModBlockEntities.register("schematic_table",
		(pos, state) -> new SchematicTableBlockEntity(SchematicsRegistry.SCHEMATIC_TABLE_BLOCK_ENTITY, pos, state), SCHEMATIC_TABLE);

	// ---- items ----

	public static final Item SCHEMATICANNON_ITEM = ModItems.block(SCHEMATICANNON);
	public static final Item SCHEMATIC_TABLE_ITEM = ModItems.block(SCHEMATIC_TABLE);

	public static final Item EMPTY_SCHEMATIC = ModItems.register("empty_schematic",
		p -> new SimplePolymerItem(p, Items.TRIAL_KEY, true), new Item.Properties().stacksTo(1));
	public static final SchematicAndQuillItem SCHEMATIC_AND_QUILL = ModItems.register("schematic_and_quill",
		SchematicAndQuillItem::new, new Item.Properties().stacksTo(1));
	public static final SchematicItem SCHEMATIC = ModItems.register("schematic", SchematicItem::new,
		new Item.Properties().stacksTo(1));

	// ---- the paper a checklist is printed on ----

	private static Predicate<ItemStack> checklistPaper = stack -> false;

	/**
	 * Create also prints checklists onto clipboards. Whoever ports the clipboard calls this with a test for it, and
	 * sets {@link dev.createpolymer.schematics.cannon.MaterialChecklist#clipboardWriter}; books are always accepted.
	 */
	public static void registerChecklistPaper(Predicate<ItemStack> paper) {
		Predicate<ItemStack> previous = checklistPaper;
		checklistPaper = stack -> previous.test(stack) || paper.test(stack);
	}

	public static boolean isChecklistPaper(ItemStack stack) {
		return checklistPaper.test(stack);
	}

	// ---- sounds (Create's SCHEMATICANNON_LAUNCH_BLOCK and SCHEMATICANNON_FINISH are these vanilla sounds) ----

	public static void playLaunchSound(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, .1f, 1.1f);
	}

	public static void playFinishSound(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1, .7f);
	}

	public static void init() {
		SchematicannonBlockEntity.registerCapabilities();

		NBTProcessors.registerDefaults();
		NBTProcessors.addProcessor(MiscRegistry.CREATIVE_CRATE_BLOCK_ENTITY, NBTProcessors.itemProcessor("Filter"));

		// Create's AllSchematicStateFilters
		dev.createpolymer.api.schematic.state.SchematicStateFilterRegistry.REGISTRY.register(Blocks.CHISELED_BOOKSHELF,
			(blockEntity, state) -> {
				for (BooleanProperty p : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES)
					state = state.setValue(p, false);

				return state;
			});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SchematicHandler.tick(server);
			SchematicAndQuillHandler.tick(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SchematicHandler.onDisconnect(handler.getPlayer());
			SchematicAndQuillHandler.onDisconnect(handler.getPlayer());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SchematicHandler.clear();
			SchematicAndQuillHandler.clear();
		});
	}
}

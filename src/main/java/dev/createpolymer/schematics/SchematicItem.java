package dev.createpolymer.schematics;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.createpolymer.registry.feature.SchematicsRegistry;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Create's schematic item: a file name, the player whose folder it is in, and where, how turned and how mirrored the
 * structure is placed. Create's client holds the hologram and the tools that position it; here {@link
 * SchematicHandler} and {@link SchematicPlaceGui} do that for a player holding the item.
 */
public class SchematicItem extends SimplePolymerItem {

	private static final Logger LOGGER = LogUtils.getLogger();

	public SchematicItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	public static ItemStack create(Level level, String schematic, String owner) {
		ItemStack blueprint = new ItemStack(SchematicsRegistry.SCHEMATIC);

		blueprint.set(SchematicsRegistry.SCHEMATIC_DEPLOYED, false);
		blueprint.set(SchematicsRegistry.SCHEMATIC_OWNER, owner);
		blueprint.set(SchematicsRegistry.SCHEMATIC_FILE, schematic);
		blueprint.set(SchematicsRegistry.SCHEMATIC_ANCHOR, BlockPos.ZERO);
		blueprint.set(SchematicsRegistry.SCHEMATIC_ROTATION, Rotation.NONE);
		blueprint.set(SchematicsRegistry.SCHEMATIC_MIRROR, Mirror.NONE);

		writeSize(level, blueprint);
		return blueprint;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
		Consumer<Component> builder, TooltipFlag flag) {
		if (stack.has(SchematicsRegistry.SCHEMATIC_FILE)) {
			builder.accept(Component.literal(stack.get(SchematicsRegistry.SCHEMATIC_FILE))
				.withStyle(ChatFormatting.GOLD));
		} else {
			builder.accept(Component.translatable("create.schematic.invalid")
				.withStyle(ChatFormatting.RED));
		}
		super.appendHoverText(stack, context, display, builder, flag);
	}

	public static void writeSize(Level level, ItemStack blueprint) {
		StructureTemplate t = loadSchematic(level, blueprint);
		blueprint.set(SchematicsRegistry.SCHEMATIC_BOUNDS, t.getSize());
		SchematicInstances.clearHash(blueprint);
	}

	public static StructurePlaceSettings getSettings(ItemStack blueprint) {
		return getSettings(blueprint, true);
	}

	public static StructurePlaceSettings getSettings(ItemStack blueprint, boolean processNBT) {
		StructurePlaceSettings settings = new StructurePlaceSettings();
		settings.setRotation(blueprint.getOrDefault(SchematicsRegistry.SCHEMATIC_ROTATION, Rotation.NONE));
		settings.setMirror(blueprint.getOrDefault(SchematicsRegistry.SCHEMATIC_MIRROR, Mirror.NONE));
		if (processNBT)
			settings.addProcessor(SchematicProcessor.INSTANCE);
		return settings;
	}

	public static StructureTemplate loadSchematic(Level level, ItemStack blueprint) {
		StructureTemplate t = new StructureTemplate();
		String owner = blueprint.get(SchematicsRegistry.SCHEMATIC_OWNER);
		String schematic = blueprint.get(SchematicsRegistry.SCHEMATIC_FILE);

		if (owner == null || schematic == null || !schematic.endsWith(".nbt"))
			return t;

		Path dir = ServerSchematicLoader.uploadedDir()
			.normalize();
		Path path;
		try {
			Path ownerDir = dir.resolve(owner)
				.normalize();
			// the owner is the name of a folder of the uploaded schematics, and the file is in it
			if (!dir.equals(ownerDir.getParent()))
				return t;
			path = ownerDir.resolve(Paths.get(schematic))
				.normalize();
			if (!path.startsWith(ownerDir))
				return t;
		} catch (java.nio.file.InvalidPathException e) {
			return t;
		}

		try {
			CompoundTag nbt = NbtIo.readCompressed(path, NbtAccounter.create(0x20000000L));
			t.load(level.holderLookup(Registries.BLOCK), nbt);
		} catch (IOException e) {
			LOGGER.warn("Failed to read schematic", e);
		}

		return t;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || context.getHand() != InteractionHand.MAIN_HAND
			|| !player.getItemInHand(context.getHand())
				.has(SchematicsRegistry.SCHEMATIC_FILE))
			return super.useOn(context);
		if (!(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.SUCCESS;
		// Create's deploy tool: right-click on a block puts the structure there, sneaking opens the tools
		if (player.isShiftKeyDown())
			SchematicPlaceGui.open(serverPlayer);
		else
			SchematicHandler.deployAt(serverPlayer, context.getClickedPos(), context.getClickedFace());
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand)
			.has(SchematicsRegistry.SCHEMATIC_FILE))
			return super.use(level, player, hand);
		if (player instanceof ServerPlayer serverPlayer)
			SchematicPlaceGui.open(serverPlayer);
		return InteractionResult.SUCCESS;
	}

	public static boolean isSchematic(ItemStack stack) {
		return stack.is(SchematicsRegistry.SCHEMATIC);
	}
}

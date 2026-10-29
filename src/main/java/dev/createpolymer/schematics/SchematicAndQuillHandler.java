package dev.createpolymer.schematics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.registry.feature.SchematicsRegistry;
import dev.createpolymer.schematics.SchematicExport.SchematicExportResult;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Create's SchematicAndQuillHandler, run on the server for every player. The selection of a player is two corners:
 * right-click a block for each; right-click again to name and save the schematic (an anvil takes the name); sneaking
 * or pointing at nothing discards the selection. While a schematic and quill is held, the box that would be saved is
 * shown to the player, from the first corner to the block they point at.
 */
public class SchematicAndQuillHandler {

	private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
	/** Create selects with a trace this long */
	private static final double RANGE = 75;

	/** One player's selection, with the box that is shown for it */
	public static class Selection {
		public BlockPos firstPos;
		public BlockPos secondPos;
		/** where the player points */
		@Nullable
		public BlockPos selectedPos;
		private PlayerBoxOutline outline;
	}

	public static Selection get(ServerPlayer player) {
		return SELECTIONS.computeIfAbsent(player.getUUID(), id -> new Selection());
	}

	@Nullable
	public static Selection peek(ServerPlayer player) {
		return SELECTIONS.get(player.getUUID());
	}

	/** Right-click with the schematic and quill: the use of the item in the air, or on a block */
	public static InteractionResult onUse(Player player, InteractionHand hand, boolean onBlock) {
		if (hand != InteractionHand.MAIN_HAND)
			return InteractionResult.PASS;
		if (!(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.SUCCESS;

		BlockPos target = SchematicHandler.selectedPos(serverPlayer, RANGE);
		if (!serverPlayer.isShiftKeyDown() && target == null) {
			// pointing at nothing resets the selection
			Selection selection = peek(serverPlayer);
			if (selection != null && selection.firstPos != null) {
				discard(serverPlayer);
				return InteractionResult.SUCCESS;
			}
		}
		handleSelection(serverPlayer, target);
		return InteractionResult.SUCCESS;
	}

	/**
	 * Create's onMouseInput for the right mouse button: sneaking discards, a complete selection asks for a name, and
	 * otherwise the position becomes the first or the second corner.
	 */
	public static void handleSelection(ServerPlayer player, @Nullable BlockPos selectedPos) {
		if (player.isShiftKeyDown()) {
			discard(player);
			return;
		}

		Selection selection = get(player);
		if (selection.secondPos != null) {
			openPrompt(player);
			return;
		}

		if (selectedPos == null) {
			player.sendOverlayMessage(Component.translatable("create.schematicAndQuill.noTarget"));
			return;
		}

		if (selection.firstPos != null) {
			selection.secondPos = selectedPos;
			AABB bb = new AABB(Vec3.atLowerCornerOf(selection.firstPos), Vec3.atLowerCornerOf(selection.secondPos));
			player.sendOverlayMessage(Component.translatable("create.schematicAndQuill.secondPos")
				.append(Component.literal(" "))
				.append(Component.translatable("create.schematicAndQuill.dimensions", (int) bb.getXsize() + 1,
					(int) bb.getYsize() + 1, (int) bb.getZsize() + 1)));
			return;
		}

		selection.firstPos = selectedPos;
		player.sendOverlayMessage(Component.translatable("create.schematicAndQuill.firstPos"));
	}

	public static void discard(ServerPlayer player) {
		Selection selection = SELECTIONS.get(player.getUUID());
		boolean had = selection != null && selection.firstPos != null;
		if (selection != null) {
			selection.firstPos = null;
			selection.secondPos = null;
			if (selection.outline != null)
				selection.outline.remove();
		}
		if (had || selection == null)
			player.sendOverlayMessage(Component.translatable("create.schematicAndQuill.abort"));
	}

	/** Create's SchematicPromptScreen: the name of the schematic is typed in an anvil */
	public static void openPrompt(ServerPlayer player) {
		AnvilInputGui anvil = new AnvilInputGui(player, false);
		anvil.setTitle(Component.translatable("create.schematicAndQuill.title"));
		anvil.setDefaultInputValue("");
		anvil.setSlot(1, new GuiElementBuilder(Items.WRITTEN_BOOK)
			.setName(Component.translatable("create.schematicAndQuill.convert")
				.withStyle(ChatFormatting.AQUA))
			.hideDefaultTooltip()
			.setCallback(() -> {
				anvil.close();
				saveSchematic(player, anvil.getInput(), true);
			}));
		anvil.setSlot(2, new GuiElementBuilder(Items.NAME_TAG)
			.setName(Component.translatable("create.gui.value_settings.confirm")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> {
				anvil.close();
				saveSchematic(player, anvil.getInput(), false);
			}));
		anvil.open();
	}

	/**
	 * Create's saveSchematic: the region goes to the folder of the player. With {@code convertImmediately} the
	 * schematic and quill is replaced by a schematic item of it, the way Create's instant upload does.
	 *
	 * @return the saved file, or null when nothing was saved
	 */
	@Nullable
	public static SchematicExportResult saveSchematic(ServerPlayer player, String name, boolean convertImmediately) {
		Selection selection = peek(player);
		if (selection == null || selection.firstPos == null || selection.secondPos == null)
			return null;
		if (name.length() > 35)
			name = name.substring(0, 35);

		SchematicExportResult result = ServerSchematicLoader.saveFromQuill(player, name, player.level(),
			selection.firstPos, selection.secondPos);
		if (result == null)
			return null;

		player.sendOverlayMessage(Component.translatable("create.schematicAndQuill.saved", result.fileName()));
		selection.firstPos = null;
		selection.secondPos = null;
		if (selection.outline != null)
			selection.outline.remove();
		if (!convertImmediately)
			return result;

		ServerSchematicLoader.handleInstantSchematic(player, result.fileName(), player.level(), result.origin(),
			result.bounds());
		return result;
	}

	// ---- the box shown while the item is held ----

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList()
			.getPlayers()) {
			Selection selection = SELECTIONS.get(player.getUUID());
			boolean holding = player.getMainHandItem()
				.is(SchematicsRegistry.SCHEMATIC_AND_QUILL);
			if (!holding) {
				if (selection != null && selection.outline != null)
					selection.outline.remove();
				continue;
			}
			if (selection == null)
				selection = get(player);
			if (selection.outline == null)
				selection.outline = new PlayerBoxOutline(player);

			selection.selectedPos = SchematicHandler.selectedPos(player, RANGE);
			selection.outline.set(currentSelectionBox(selection));
			selection.outline.tick();
		}
	}

	@Nullable
	public static AABB currentSelectionBox(Selection s) {
		if (s.secondPos == null) {
			if (s.firstPos == null)
				return s.selectedPos == null ? null : new AABB(s.selectedPos);
			return s.selectedPos == null ? new AABB(s.firstPos)
				: new AABB(Vec3.atLowerCornerOf(s.firstPos), Vec3.atLowerCornerOf(s.selectedPos)).expandTowards(1, 1, 1);
		}
		return new AABB(Vec3.atLowerCornerOf(s.firstPos), Vec3.atLowerCornerOf(s.secondPos)).expandTowards(1, 1, 1);
	}

	public static void onDisconnect(ServerPlayer player) {
		Selection selection = SELECTIONS.remove(player.getUUID());
		if (selection != null && selection.outline != null)
			selection.outline.remove();
	}

	public static void clear() {
		SELECTIONS.values()
			.forEach(s -> {
				if (s.outline != null)
					s.outline.remove();
			});
		SELECTIONS.clear();
	}
}

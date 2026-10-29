package dev.createpolymer.equipment.clipboard;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.equipment.clipboard.ClipboardOverrides.ClipboardType;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.registry.feature.ToolsRegistry;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The server half of Create's ClipboardValueSettingsHandler: right-click a block with a clipboard to copy its
 * settings (filters, frequencies, speeds, timings, ...), left-click another block to paste them. Right-clicking a
 * placed clipboard adds its entries to the one in hand. The client half (the outline and the hover tip) is not
 * needed, a vanilla client shows the action bar message instead.
 */
public class ClipboardValueSettingsHandler {

	private static boolean registered;

	/** Registers the handlers with Fabric's interaction callbacks. Call once during mod initialisation. */
	public static void init() {
		if (registered)
			return;
		registered = true;
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> rightClickToCopy(player, level, hand, hit.getBlockPos(),
			hit.getDirection()));
		AttackBlockCallback.EVENT.register(ClipboardValueSettingsHandler::leftClickToPaste);
	}

	public static InteractionResult rightClickToCopy(Player player, Level world, InteractionHand hand, BlockPos pos,
		Direction face) {
		return interact(player, world, hand, pos, face, false);
	}

	public static InteractionResult leftClickToPaste(Player player, Level world, InteractionHand hand, BlockPos pos,
		Direction face) {
		return interact(player, world, hand, pos, face, true);
	}

	/** Whether a block has anything a clipboard can copy. */
	public static boolean canCopy(Level world, BlockPos pos, Direction side) {
		if (!(world.getBlockEntity(pos) instanceof SmartBlockEntity smartBE))
			return false;
		if (smartBE instanceof ClipboardBlockEntity)
			return true;
		for (BlockEntityBehaviour behaviour : smartBE.getAllBehaviours())
			if (behaviour instanceof ClipboardCloneable cc && cc.writeToClipboard(world.registryAccess(), new CompoundTag(), side))
				return true;
		return smartBE instanceof ClipboardCloneable ccbe && ccbe.writeToClipboard(world.registryAccess(), new CompoundTag(), side);
	}

	private static InteractionResult interact(Player player, Level world, InteractionHand hand, BlockPos pos, Direction face,
		boolean paste) {
		ItemStack itemStack = player.getItemInHand(hand);
		if (!(itemStack.getItem() instanceof ClipboardBlockItem))
			return InteractionResult.PASS;
		if (world.isClientSide())
			return InteractionResult.PASS;

		if (player.isSpectator())
			return InteractionResult.PASS;
		if (player.isShiftKeyDown())
			return InteractionResult.PASS;
		if (!(world.getBlockEntity(pos) instanceof SmartBlockEntity smartBE))
			return InteractionResult.PASS;

		ClipboardContent clipboardContent = itemStack.getOrDefault(ToolsRegistry.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);

		if (smartBE instanceof ClipboardBlockEntity cbe) {
			List<List<ClipboardEntry>> listTo = ClipboardEntry.readAll(clipboardContent);
			List<List<ClipboardEntry>> listFrom = ClipboardEntry.readAll(cbe.components());
			List<ClipboardEntry> toAdd = new ArrayList<>();

			for (List<ClipboardEntry> page : listFrom) {
				Copy: for (ClipboardEntry entry : page) {
					String entryToAdd = entry.text.getString();
					for (List<ClipboardEntry> pageTo : listTo)
						for (ClipboardEntry existing : pageTo)
							if (entryToAdd.equals(existing.text.getString()))
								continue Copy;
					toAdd.add(new ClipboardEntry(entry.checked, entry.text));
				}
			}

			for (ClipboardEntry entry : toAdd) {
				List<ClipboardEntry> page = null;
				for (List<ClipboardEntry> freePage : listTo) {
					if (freePage.size() > 11)
						continue;
					page = freePage;
					break;
				}
				if (page == null) {
					page = new ArrayList<>();
					listTo.add(page);
				}
				page.add(entry);

				clipboardContent = clipboardContent.setType(ClipboardType.WRITTEN);
				itemStack.set(ToolsRegistry.CLIPBOARD_CONTENT, clipboardContent);
			}

			clipboardContent = clipboardContent.setPages(listTo);
			itemStack.set(ToolsRegistry.CLIPBOARD_CONTENT, clipboardContent);

			player.sendOverlayMessage(Component.translatable("create.clipboard.copied_from_clipboard", world.getBlockState(pos)
				.getBlock()
				.getName()
				.withStyle(ChatFormatting.WHITE))
				.withStyle(ChatFormatting.GREEN));
			return InteractionResult.SUCCESS;
		}

		CompoundTag tag = clipboardContent.copiedValues()
			.orElse(null);
		if (paste && tag == null)
			return InteractionResult.PASS;
		if (!paste)
			tag = new CompoundTag();

		boolean anySuccess = false;
		boolean anyValid = false;
		for (BlockEntityBehaviour behaviour : smartBE.getAllBehaviours()) {
			if (!(behaviour instanceof ClipboardCloneable cc))
				continue;
			anyValid = true;
			String clipboardKey = cc.getClipboardKey();
			if (paste) {
				anySuccess |= cc.readFromClipboard(world.registryAccess(), tag.getCompoundOrEmpty(clipboardKey), player, face, false);
				continue;
			}
			CompoundTag compoundTag = new CompoundTag();
			boolean success = cc.writeToClipboard(world.registryAccess(), compoundTag, face);
			anySuccess |= success;
			if (success)
				tag.put(clipboardKey, compoundTag);
		}

		if (smartBE instanceof ClipboardCloneable ccbe) {
			anyValid = true;
			String clipboardKey = ccbe.getClipboardKey();
			if (paste) {
				anySuccess |= ccbe.readFromClipboard(world.registryAccess(), tag.getCompoundOrEmpty(clipboardKey), player, face, false);
			} else {
				CompoundTag compoundTag = new CompoundTag();
				boolean success = ccbe.writeToClipboard(world.registryAccess(), compoundTag, face);
				anySuccess |= success;
				if (success)
					tag.put(clipboardKey, compoundTag);
			}
		}

		if (!anyValid)
			return InteractionResult.PASS;
		if (!anySuccess)
			return InteractionResult.SUCCESS;

		player.sendOverlayMessage(Component
			.translatable(paste ? "create.clipboard.pasted_to" : "create.clipboard.copied_from", world.getBlockState(pos)
				.getBlock()
				.getName()
				.withStyle(ChatFormatting.WHITE))
			.withStyle(ChatFormatting.GREEN));

		if (!paste) {
			clipboardContent = clipboardContent.setType(ClipboardType.WRITTEN);
			clipboardContent = clipboardContent.setCopiedValues(tag);
			itemStack.set(ToolsRegistry.CLIPBOARD_CONTENT, clipboardContent);
		}
		return InteractionResult.SUCCESS;
	}

}

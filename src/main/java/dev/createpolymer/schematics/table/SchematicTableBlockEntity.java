package dev.createpolymer.schematics.table;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.registry.feature.SchematicsRegistry;
import dev.createpolymer.schematics.SchematicItem;
import dev.createpolymer.schematics.ServerSchematicLoader;
import dev.createpolymer.schematics.ServerSchematicLoader.AvailableSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Create's schematic table. Create's table receives a file that a client uploads in packets, which fills the progress
 * bar of its screen; here the table copies a file from the folder of the player (or the shared folder) onto the empty
 * schematic, over as many ticks as the same file would need packets.
 */
public class SchematicTableBlockEntity extends SmartBlockEntity implements Clearable {
	public SchematicTableInventory inventory;
	public boolean isUploading;
	public String uploadingSchematic;
	public String uploadingOwner;
	public float uploadingProgress;
	private int uploadingTicks;
	public boolean sendUpdate;

	public class SchematicTableInventory extends ItemStackHandler {
		public SchematicTableInventory() {
			super(2);
		}

		@Override
		public void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			setChanged();
		}
	}

	public SchematicTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		inventory = new SchematicTableInventory();
		uploadingSchematic = null;
		uploadingProgress = 0;
	}

	public static boolean isValidInput(ItemStack stack) {
		return stack.is(SchematicsRegistry.EMPTY_SCHEMATIC) || stack.is(SchematicsRegistry.SCHEMATIC_AND_QUILL)
			|| stack.is(SchematicsRegistry.SCHEMATIC);
	}

	public boolean canWrite() {
		return !isUploading && !inventory.getStackInSlot(0)
			.isEmpty() && inventory.getStackInSlot(1)
				.isEmpty();
	}

	@Override
	protected void read(ValueInput compound) {
		inventory.deserialize(compound.childOrEmpty("Inventory"));
		super.read(compound);
		if (compound.getString("Uploading")
			.isPresent()) {
			isUploading = true;
			uploadingSchematic = compound.getStringOr("Uploading", "");
			uploadingOwner = compound.getStringOr("Owner", "");
			uploadingProgress = compound.getFloatOr("Progress", 0);
			uploadingTicks = compound.getIntOr("Duration", 20);
		} else {
			isUploading = false;
			uploadingSchematic = null;
			uploadingOwner = null;
			uploadingProgress = 0;
		}
	}

	@Override
	protected void write(ValueOutput compound) {
		inventory.serialize(compound.child("Inventory"));
		super.write(compound);
		if (isUploading) {
			compound.putString("Uploading", uploadingSchematic);
			compound.putString("Owner", uploadingOwner);
			compound.putFloat("Progress", uploadingProgress);
			compound.putInt("Duration", uploadingTicks);
		}
	}

	@Override
	public void clearContent() {
		inventory.setStackInSlot(0, ItemStack.EMPTY);
		inventory.setStackInSlot(1, ItemStack.EMPTY);
	}

	@Override
	public void destroy() {
		super.destroy();
		ItemHelper.dropContents(level, worldPosition, inventory);
	}

	@Override
	public void tick() {
		super.tick();
		if (!isUploading || level == null || level.isClientSide())
			return;
		uploadingProgress = Math.min(1, uploadingProgress + 1f / Math.max(1, uploadingTicks));
		if (uploadingProgress >= 1)
			finishUpload(true);
		setChanged();
	}

	/**
	 * Create's handleNewUpload and table.startUpload: checks the file, makes room in the folder of the player and
	 * starts writing. Returns whether it started; the player is told why not.
	 */
	public boolean startWrite(ServerPlayer player, AvailableSchematic schematic) {
		if (!canWrite()) {
			player.sendOverlayMessage(Component.translatableWithFallback("create.gui.schematicTable.cannotWrite",
				"Needs an Empty Schematic and a free output slot"));
			return false;
		}
		if (!isValidInput(inventory.getStackInSlot(0)))
			return false;

		String name = schematic.name();
		if (!schematic.uploaded()) {
			// from the shared folder: the file goes to the folder of the player first
			name = ServerSchematicLoader.importShared(player, name);
			if (name == null)
				return false;
		} else if (!ServerSchematicLoader.isUsable(player, name))
			return false;

		Path file = ServerSchematicLoader.resolveUploadPath(ServerSchematicLoader.playerName(player), name);
		long size = 0;
		try {
			size = file == null ? 0 : Files.size(file);
		} catch (IOException ignored) {
		}
		int packets = (int) Math.ceil(size / (double) Math.max(1, SchematicsRegistry.maxSchematicPacketSize));
		startUpload(name, ServerSchematicLoader.playerName(player), Math.min(200, 20 + packets * 2));
		return true;
	}

	public void startUpload(String schematic, String owner, int ticks) {
		isUploading = true;
		uploadingProgress = 0;
		uploadingSchematic = schematic;
		uploadingOwner = owner;
		uploadingTicks = ticks;
		sendUpdate = true;
		inventory.setStackInSlot(0, ItemStack.EMPTY);
	}

	public void finishUpload() {
		finishUpload(false);
	}

	private void finishUpload(boolean produce) {
		String schematic = uploadingSchematic;
		String owner = uploadingOwner;
		isUploading = false;
		uploadingProgress = 0;
		uploadingSchematic = null;
		uploadingOwner = null;
		sendUpdate = true;
		if (produce && schematic != null && owner != null && level != null)
			inventory.setStackInSlot(1, SchematicItem.create(level, schematic, owner));
	}

	public Component getDisplayName() {
		return Component.translatable("create.gui.schematicTable.title");
	}

	public void openGui(ServerPlayer player) {
		new SchematicTableGui(player, this).open();
	}
}

package dev.createpolymer.logistics.vault;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.api.connectivity.ConnectivityHandler;
import dev.createpolymer.foundation.blockEntity.IMultiBlockEntityContainer;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.item.handler.CombinedInvWrapper;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.registry.feature.VaultRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.SectionPos;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ItemVaultBlockEntity extends SmartBlockEntity implements IMultiBlockEntityContainer.Inventory, Clearable {

	/** Create's config logistics.vaultCapacity (default): the amount of stacks one vault block holds. */
	public static final int VAULT_CAPACITY = 20;

	protected IItemHandler itemCapability = null;

	protected ItemStackHandler inventory;
	protected BlockPos controller;
	protected BlockPos lastKnownPos;
	protected boolean updateConnectivity;
	protected int radius;
	protected int length;

	public ItemVaultBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);

		inventory = new ItemStackHandler(VAULT_CAPACITY) {
			@Override
			protected void onContentsChanged(int slot) {
				super.onContentsChanged(slot);
				if (level == null)
					return;
				updateComparators();
				level.blockEntityChanged(worldPosition);
			}
		};

		radius = 1;
		length = 1;
	}

	/** Every block of a vault answers with the controller's combined handler. */
	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(VaultRegistry.ITEM_VAULT_BLOCK_ENTITY, (be, side) -> be.getCapability());
	}

	protected void updateConnectivity() {
		updateConnectivity = false;
		if (level.isClientSide())
			return;
		if (!isController())
			return;
		ConnectivityHandler.formMulti(this);
	}

	protected void updateComparators() {
		ItemVaultBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null)
			return;

		level.blockEntityChanged(controllerBE.worldPosition);

		BlockPos pos = controllerBE.getBlockPos();

		int radius = controllerBE.radius;
		int length = controllerBE.length;

		Axis axis = controllerBE.getMainConnectionAxis();

		int zMax = (axis == Axis.X ? radius : length);
		int xMax = (axis == Axis.Z ? radius : length);

		// Mutable position we'll use for the blocks we poke updates at.
		MutableBlockPos updatePos = new MutableBlockPos();
		// Mutable position we'll set to be the vault block next to the update position.
		MutableBlockPos provokingPos = new MutableBlockPos();

		for (int y = 0; y < radius; y++) {
			for (int z = 0; z < zMax; z++) {
				for (int x = 0; x < xMax; x++) {
					// Emulate the effect of this line, but only for blocks along the surface of the vault:
					// level.updateNeighbourForOutputSignal(pos.offset(x, y, z), getBlockState().getBlock());
					// That method pokes all 6 directions in order. We want to preserve the update order
					// but skip the wasted work of checking other blocks that are part of this vault.

					var sectionX = SectionPos.blockToSectionCoord(pos.getX() + x);
					var sectionZ = SectionPos.blockToSectionCoord(pos.getZ() + z);
					if (!level.hasChunk(sectionX, sectionZ)) {
						continue;
					}
					provokingPos.setWithOffset(pos, x, y, z);

					// Technically all this work is wasted for the inner blocks of a long 3x3 vault, but
					// this is fast enough and relatively simple.
					Block provokingBlock = level.getBlockState(provokingPos).getBlock();

					// The 6 calls below should match the order of Direction.values().
					if (y == 0) {
						updateComaratorsInner(level, provokingBlock, provokingPos, updatePos, Direction.DOWN);
					}
					if (y == radius - 1) {
						updateComaratorsInner(level, provokingBlock, provokingPos, updatePos, Direction.UP);
					}
					if (z == 0) {
						updateComaratorsInner(level, provokingBlock, provokingPos, updatePos, Direction.NORTH);
					}
					if (z == zMax - 1) {
						updateComaratorsInner(level, provokingBlock, provokingPos, updatePos, Direction.SOUTH);
					}
					if (x == 0) {
						updateComaratorsInner(level, provokingBlock, provokingPos, updatePos, Direction.WEST);
					}
					if (x == xMax - 1) {
						updateComaratorsInner(level, provokingBlock, provokingPos, updatePos, Direction.EAST);
					}
				}
			}
		}
	}

	/**
	 * See {@link Level#updateNeighbourForOutputSignal(BlockPos, Block)}.
	 */
	private static void updateComaratorsInner(Level level, Block provokingBlock, BlockPos provokingPos, MutableBlockPos updatePos, Direction direction) {
		updatePos.setWithOffset(provokingPos, direction);

		var sectionX = SectionPos.blockToSectionCoord(updatePos.getX());
		var sectionZ = SectionPos.blockToSectionCoord(updatePos.getZ());
		if (!level.hasChunk(sectionX, sectionZ)) {
			return;
		}

		BlockState blockstate = level.getBlockState(updatePos);
		if (blockstate.is(Blocks.COMPARATOR)) {
			level.neighborChanged(blockstate, updatePos, provokingBlock, null, false);
		} else if (blockstate.isRedstoneConductor(level, updatePos)) {
			updatePos.move(direction);
			blockstate = level.getBlockState(updatePos);
			if (blockstate.is(Blocks.COMPARATOR)) {
				level.neighborChanged(blockstate, updatePos, provokingBlock, null, false);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (lastKnownPos == null)
			lastKnownPos = getBlockPos();
		else if (!lastKnownPos.equals(worldPosition) && worldPosition != null) {
			onPositionChanged();
			return;
		}

		if (updateConnectivity)
			updateConnectivity();
	}

	@Override
	public BlockPos getLastKnownPos() {
		return lastKnownPos;
	}

	@Override
	public boolean isController() {
		return controller == null || worldPosition.getX() == controller.getX()
			&& worldPosition.getY() == controller.getY() && worldPosition.getZ() == controller.getZ();
	}

	private void onPositionChanged() {
		removeController(true);
		lastKnownPos = worldPosition;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ItemVaultBlockEntity getControllerBE() {
		if (isController())
			return this;
		BlockEntity blockEntity = level.getBlockEntity(controller);
		if (blockEntity instanceof ItemVaultBlockEntity)
			return (ItemVaultBlockEntity) blockEntity;
		return null;
	}

	public void removeController(boolean keepContents) {
		if (level.isClientSide())
			return;
		updateConnectivity = true;
		controller = null;
		radius = 1;
		length = 1;

		BlockState state = getBlockState();
		if (ItemVaultBlock.isVault(state)) {
			state = state.setValue(ItemVaultBlock.LARGE, false);
			getLevel().setBlock(worldPosition, state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
		}

		itemCapability = null;
		setChanged();
		sendData();
	}

	@Override
	public void setController(BlockPos controller) {
		if (level.isClientSide())
			return;
		if (controller.equals(this.controller))
			return;
		this.controller = controller;
		itemCapability = null;
		setChanged();
		sendData();
	}

	@Override
	public BlockPos getController() {
		return isController() ? worldPosition : controller;
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);

		updateConnectivity = compound.getBooleanOr("Uninitialized", false);

		lastKnownPos = compound.read("LastKnownPos", BlockPos.CODEC)
			.orElse(null);

		controller = compound.read("Controller", BlockPos.CODEC)
			.orElse(null);

		if (isController()) {
			radius = compound.getIntOr("Size", 1);
			length = compound.getIntOr("Length", 1);
		}

		inventory.deserialize(compound.childOrEmpty("Inventory"));
	}

	@Override
	protected void write(ValueOutput compound) {
		if (updateConnectivity)
			compound.putBoolean("Uninitialized", true);

		if (lastKnownPos != null)
			compound.store("LastKnownPos", BlockPos.CODEC, lastKnownPos);
		if (!isController())
			compound.store("Controller", BlockPos.CODEC, controller);
		if (isController()) {
			compound.putInt("Size", radius);
			compound.putInt("Length", length);
		}

		super.write(compound);

		compound.putString("StorageType", "CombinedInv");
		inventory.serialize(compound.child("Inventory"));
	}

	/** Block broken or replaced: drop this block's stacks and split the multiblock (Create's ItemVaultBlock.onRemove). */
	@Override
	public void destroy() {
		super.destroy();
		for (int slot = 0; slot < inventory.getSlots(); slot++)
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
				inventory.getStackInSlot(slot));
		level.removeBlockEntity(worldPosition);
		ConnectivityHandler.splitMulti(this);
	}

	@Override
	public void clearContent() {
		inventory.setSize(inventory.getSlots());
	}

	public ItemStackHandler getInventoryOfBlock() {
		return inventory;
	}

	/** The handler of the whole multiblock (the controller's), or null if the controller is missing. */
	@Nullable
	public IItemHandler getCapability() {
		ItemVaultBlockEntity controllerBE = getControllerBE();
		if (controllerBE == null)
			return null;
		if (controllerBE != this && controllerBE.isRemoved())
			return null;
		return controllerBE.initCapability();
	}

	/** Controller only: the combined view of every vault block's own inventory. */
	private IItemHandler initCapability() {
		if (itemCapability != null)
			return itemCapability;

		boolean alongZ = ItemVaultBlock.getVaultBlockAxis(getBlockState()) == Axis.Z;
		IItemHandlerModifiable[] invs = new IItemHandlerModifiable[length * radius * radius];
		boolean complete = true;
		for (int yOffset = 0; yOffset < length; yOffset++) {
			for (int xOffset = 0; xOffset < radius; xOffset++) {
				for (int zOffset = 0; zOffset < radius; zOffset++) {
					BlockPos vaultPos = alongZ ? worldPosition.offset(xOffset, zOffset, yOffset)
						: worldPosition.offset(yOffset, xOffset, zOffset);
					ItemVaultBlockEntity vaultAt =
						ConnectivityHandler.partAt(VaultRegistry.ITEM_VAULT_BLOCK_ENTITY, level, vaultPos);
					if (vaultAt == null)
						complete = false;
					invs[yOffset * radius * radius + xOffset * radius + zOffset] =
						vaultAt != null ? vaultAt.inventory : new ItemStackHandler(VAULT_CAPACITY);
				}
			}
		}

		IItemHandler combined = new CombinedInvWrapper(invs);
		// Do not keep a view with a placeholder in it: a part may just not be loaded yet.
		if (complete)
			itemCapability = combined;
		return combined;
	}

	public static int getMaxLength(int radius) {
		return radius * 3;
	}

	@Override
	public void preventConnectivityUpdate() { updateConnectivity = false; }

	@Override
	public void notifyMultiUpdated() {
		BlockState state = this.getBlockState();
		if (ItemVaultBlock.isVault(state)) { // safety
			level.setBlock(getBlockPos(), state.setValue(ItemVaultBlock.LARGE, radius > 2), Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
		}
		itemCapability = null;
		setChanged();
		sendData();
	}

	@Override
	public Direction.Axis getMainConnectionAxis() { return getMainAxisOf(this); }

	@Override
	public int getMaxLength(Direction.Axis longAxis, int width) {
		if (longAxis == Direction.Axis.Y) return getMaxWidth();
		return getMaxLength(width);
	}

	@Override
	public int getMaxWidth() {
		return 3;
	}

	@Override
	public int getHeight() { return length; }

	@Override
	public int getWidth() { return radius; }

	@Override
	public void setHeight(int height) { this.length = height; }

	@Override
	public void setWidth(int width) { this.radius = width; }

	@Override
	public boolean hasInventory() { return true; }
}

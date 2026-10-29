package dev.createpolymer.logistics.packagePort;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.equipment.clipboard.ClipboardBlockItem;
import dev.createpolymer.equipment.clipboard.ClipboardContent;
import dev.createpolymer.equipment.clipboard.ClipboardEntry;
import dev.createpolymer.equipment.clipboard.ClipboardOverrides.ClipboardType;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.registry.feature.ToolsRegistry;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Create's PackagePortBlockEntity: the inventory, address filter and target shared by the frogport and the postbox.
 * Create opens a menu on right-click; here {@link PackagePortGui} (an sgui chest) takes its place. Writing the
 * Right-clicking with a clipboard writes the port's address on it.
 */
public abstract class PackagePortBlockEntity extends SmartBlockEntity implements Clearable {
	public boolean acceptsPackages;
	public String addressFilter;
	public PackagePortTarget target;
	public final PortInventory inventory;

	protected AnimatedContainerBehaviour openTracker;

	protected IItemHandler itemHandler;

	/** Create's SmartInventory(18, be, ...) that only takes packages. */
	public static class PortInventory extends ItemStackHandler {
		private final PackagePortBlockEntity blockEntity;

		public PortInventory(PackagePortBlockEntity blockEntity) {
			super(18);
			this.blockEntity = blockEntity;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return PackageItem.isPackage(stack);
		}

		@Override
		protected void onContentsChanged(int slot) {
			blockEntity.notifyUpdate();
		}

		public boolean isEmpty() {
			for (ItemStack stack : stacks)
				if (!stack.isEmpty())
					return false;
			return true;
		}

		public void clearContent() {
			for (int i = 0; i < stacks.size(); i++)
				stacks.set(i, ItemStack.EMPTY);
			blockEntity.notifyUpdate();
		}
	}

	public PackagePortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		addressFilter = "";
		acceptsPackages = true;
		inventory = new PortInventory(this);
		itemHandler = new PackagePortAutomationInventoryWrapper(inventory, this);
	}

	public boolean isBackedUp() {
		for (int i = 0; i < inventory.getSlots(); i++)
			if (inventory.getStackInSlot(i)
				.isEmpty())
				return false;
		return true;
	}

	public void filterChanged() {
		if (target != null) {
			target.deregister(this, level, worldPosition);
			target.register(this, level, worldPosition);
		}
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (target != null)
			target.register(this, level, worldPosition);
	}

	public String getFilterString() {
		return acceptsPackages ? addressFilter : null;
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);
		if (target != null)
			output.store("Target", PackagePortTarget.CODEC, target);
		output.putString("AddressFilter", addressFilter);
		output.putBoolean("AcceptsPackages", acceptsPackages);
		inventory.serialize(output.child("Inventory"));
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);
		inventory.deserialize(input.childOrEmpty("Inventory"));
		target = input.read("Target", PackagePortTarget.CODEC).orElse(null);
		addressFilter = input.getStringOr("AddressFilter", "");
		acceptsPackages = input.getBooleanOr("AcceptsPackages", true);
	}

	@Override
	public void clearContent() {
		inventory.clearContent();
	}

	@Override
	public void destroy() {
		if (target != null)
			target.deregister(this, level, worldPosition);
		super.destroy();
		for (int i = 0; i < inventory.getSlots(); i++)
			drop(inventory.getStackInSlot(i));
	}

	public void drop(ItemStack box) {
		if (box.isEmpty())
			return;
		Block.popResource(level, worldPosition, box);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(openTracker = new AnimatedContainerBehaviour(this));
		openTracker.onOpenChanged(this::onOpenChange);
	}

	protected abstract void onOpenChange(boolean open);

	public AnimatedContainerBehaviour getOpenTracker() {
		return openTracker;
	}

	/** Sets the address filter and the send-and-receive switch, as the GUI (Create: PackagePortConfigurationPacket) does. */
	public void applySettings(String newFilter, boolean acceptPackages) {
		if (addressFilter.equals(newFilter) && acceptsPackages == acceptPackages)
			return;
		addressFilter = newFilter;
		acceptsPackages = acceptPackages;
		filterChanged();
		notifyUpdate();
	}

	public InteractionResult use(Player player) {
		if (player == null || player.isCrouching())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (player instanceof FakePlayer)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		ItemStack mainHandItem = player.getMainHandItem();
		boolean clipboard = mainHandItem.getItem() instanceof ClipboardBlockItem;

		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		if (clipboard) {
			addAddressToClipboard(player, mainHandItem);
			return InteractionResult.SUCCESS;
		}

		if (player instanceof ServerPlayer serverPlayer)
			new PackagePortGui(serverPlayer, this).open();
		return InteractionResult.SUCCESS;
	}

	/** Writes the address of this port on a clipboard, as an entry starting with '#'. */
	private void addAddressToClipboard(Player player, ItemStack mainHandItem) {
		if (addressFilter == null || addressFilter.isBlank())
			return;

		ClipboardContent clipboard = mainHandItem.getOrDefault(ToolsRegistry.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
		List<List<ClipboardEntry>> list = ClipboardEntry.readAll(clipboard);
		for (List<ClipboardEntry> page : list) {
			for (ClipboardEntry entry : page) {
				String existing = entry.text.getString();
				if (existing.equals("#" + addressFilter) || existing.equals("# " + addressFilter))
					return;
			}
		}

		List<ClipboardEntry> page = null;

		for (List<ClipboardEntry> freePage : list) {
			if (freePage.size() > 11)
				continue;
			page = freePage;
			break;
		}

		if (page == null) {
			page = new ArrayList<>();
			list.add(page);
		}

		page.add(new ClipboardEntry(false, Component.literal("#" + addressFilter)));
		player.sendOverlayMessage(Component.translatable("create.clipboard.address_added", addressFilter));

		clipboard = clipboard.setPages(list)
			.setType(ClipboardType.WRITTEN);
		mainHandItem.set(ToolsRegistry.CLIPBOARD_CONTENT, clipboard);
	}

	public int getComparatorOutput() {
		return ItemHandlerHelper.calcRedstoneFromInventory(inventory);
	}
}

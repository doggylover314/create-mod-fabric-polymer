package dev.createpolymer.equipment.toolbox;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import dev.createpolymer.util.VecHelper;

/**
 * Ported from Create's ToolboxBlockEntity (MIT, The Create Team). Create's hotbar tracking (players equip a
 * compartment to a hotbar slot with a key bind, and the toolbox keeps that slot stocked) needs the client's radial
 * menu and is not ported; the toolbox is opened with a GUI.
 */
public class ToolboxBlockEntity extends SmartBlockEntity implements Nameable {

	public LerpedFloat lid = LerpedFloat.linear()
		.startWithValue(0);

	public LerpedFloat drawers = LerpedFloat.linear()
		.startWithValue(0);

	UUID uniqueId;
	ToolboxInventory inventory;
	private @Nullable DyeColor color;

	private @Nullable Component customName;

	/** The players who have this toolbox open, for the lid animation */
	private final Set<UUID> openers = new HashSet<>();

	public ToolboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		inventory = new ToolboxInventory(this);
		setLazyTickRate(10);
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(EquipmentRegistry.TOOLBOX, (be, side) -> be.inventory);
	}

	public DyeColor getColor() {
		if (color == null) {
			BlockState blockState = getBlockState();
			if (blockState.getBlock() instanceof ToolboxBlock toolbox)
				color = toolbox.getColor();
			else
				return DyeColor.BROWN;
		}
		return color;
	}

	public ToolboxInventory getInventory() {
		return inventory;
	}

	@Override
	public void tick() {
		super.tick();

		lid.chase(openers.isEmpty() ? 0 : 1, 0.2f, Chaser.LINEAR);
		drawers.chase(openers.isEmpty() ? 0 : 1, 0.2f, Chaser.EXP);
		lid.tickChaser();
		drawers.tickChaser();
	}

	public int getOpenCount() {
		return openers.size();
	}

	/** Create's AnimatedContainerBehaviour.startOpen */
	public void startOpen(Player player) {
		boolean wasClosed = openers.isEmpty();
		openers.add(player.getUUID());
		if (wasClosed && level != null) {
			Vec3 vec = VecHelper.getCenterOf(worldPosition);
			level.playSound(null, vec.x, vec.y, vec.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.25F,
				level.getRandom().nextFloat() * 0.1F + 1.2F);
			level.playSound(null, vec.x, vec.y, vec.z, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.1F,
				level.getRandom().nextFloat() * 0.1F + 1.1F);
		}
	}

	/** Create's AnimatedContainerBehaviour.stopOpen */
	public void stopOpen(Player player) {
		boolean removed = openers.remove(player.getUUID());
		if (removed && openers.isEmpty() && level != null) {
			Vec3 vec = VecHelper.getCenterOf(worldPosition);
			level.playSound(null, vec.x, vec.y, vec.z, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.1F,
				level.getRandom().nextFloat() * 0.1F + 1.1F);
			level.playSound(null, vec.x, vec.y, vec.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.25F,
				level.getRandom().nextFloat() * 0.1F + 1.2F);
		}
	}

	@Override
	protected void read(ValueInput compound) {
		inventory.deserialize(compound.childOrEmpty("Inventory"));
		super.read(compound);
		compound.read("UniqueId", UUIDUtil.CODEC)
			.ifPresent(id -> this.uniqueId = id);
		this.customName = compound.read("CustomName", ComponentSerialization.CODEC)
			.orElse(null);
	}

	@Override
	protected void write(ValueOutput compound) {
		if (uniqueId == null)
			uniqueId = UUID.randomUUID();

		inventory.serialize(compound.child("Inventory"));
		compound.store("UniqueId", UUIDUtil.CODEC, uniqueId);

		if (customName != null)
			compound.store("CustomName", ComponentSerialization.CODEC, customName);
		super.write(compound);
	}

	public void readInventory(@Nullable ToolboxInventory inv) {
		if (inv != null) {
			this.inventory.filters = new java.util.ArrayList<>(inv.filters);
			for (int i = 0; i < inv.getSlots(); i++)
				this.inventory.setStackInSlot(i, inv.getStackInSlot(i)
					.copy());
		}
	}

	public void setUniqueId(@Nullable UUID uniqueId) {
		this.uniqueId = uniqueId;
	}

	public UUID getUniqueId() {
		if (uniqueId == null)
			uniqueId = UUID.randomUUID();
		return uniqueId;
	}

	public boolean isFullyInitialized() {
		// returns true when uniqueId has been initialized
		return uniqueId != null;
	}

	public void setCustomName(Component customName) {
		this.customName = customName;
	}

	@Override
	public Component getDisplayName() {
		return customName != null ? customName
			: getBlockState().getBlock()
				.getName();
	}

	@Override
	public @Nullable Component getCustomName() {
		return customName;
	}

	@Override
	public boolean hasCustomName() {
		return customName != null;
	}

	@Override
	public Component getName() {
		return getDisplayName();
	}

	@Override
	public void setBlockState(BlockState state) {
		super.setBlockState(state);
		color = null;
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter componentInput) {
		setUniqueId(componentInput.get(EquipmentRegistry.TOOLBOX_UUID));
		readInventory(componentInput.get(EquipmentRegistry.TOOLBOX_INVENTORY));
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		components.set(EquipmentRegistry.TOOLBOX_UUID, getUniqueId());
		components.set(EquipmentRegistry.TOOLBOX_INVENTORY, inventory.copy());
	}
}

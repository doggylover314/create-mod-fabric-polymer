package dev.createpolymer.redstone.link;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.apache.commons.lang3.tuple.Pair;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.DynamicOps;

import dev.createpolymer.equipment.clipboard.ClipboardCloneable;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import dev.createpolymer.util.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class LinkBehaviour extends BlockEntityBehaviour implements IRedstoneLinkable, ClipboardCloneable {

	public static final BehaviourType<LinkBehaviour> TYPE = new BehaviourType<>();

	enum Mode {
		TRANSMIT, RECEIVE
	}

	Frequency frequencyFirst;
	Frequency frequencyLast;
	ValueBoxTransform firstSlot;
	ValueBoxTransform secondSlot;
	Vec3 textShift;

	public boolean newPosition;
	private Mode mode;
	private IntSupplier transmission;
	private IntConsumer signalCallback;

	protected LinkBehaviour(SmartBlockEntity be, Pair<ValueBoxTransform, ValueBoxTransform> slots) {
		super(be);
		frequencyFirst = Frequency.EMPTY;
		frequencyLast = Frequency.EMPTY;
		firstSlot = slots.getLeft();
		secondSlot = slots.getRight();
		textShift = Vec3.ZERO;
		newPosition = true;
	}

	public static LinkBehaviour receiver(SmartBlockEntity be, Pair<ValueBoxTransform, ValueBoxTransform> slots,
		IntConsumer signalCallback) {
		LinkBehaviour behaviour = new LinkBehaviour(be, slots);
		behaviour.signalCallback = signalCallback;
		behaviour.mode = Mode.RECEIVE;
		return behaviour;
	}

	public static LinkBehaviour transmitter(SmartBlockEntity be, Pair<ValueBoxTransform, ValueBoxTransform> slots,
		IntSupplier transmission) {
		LinkBehaviour behaviour = new LinkBehaviour(be, slots);
		behaviour.transmission = transmission;
		behaviour.mode = Mode.TRANSMIT;
		return behaviour;
	}

	public LinkBehaviour moveText(Vec3 shift) {
		textShift = shift;
		return this;
	}

	public void copyItemsFrom(LinkBehaviour behaviour) {
		if (behaviour == null)
			return;
		frequencyFirst = behaviour.frequencyFirst;
		frequencyLast = behaviour.frequencyLast;
	}

	@Override
	public boolean isListening() {
		return mode == Mode.RECEIVE;
	}

	@Override
	public int getTransmittedStrength() {
		return mode == Mode.TRANSMIT ? transmission.getAsInt() : 0;
	}

	@Override
	public void setReceivedStrength(int networkPower) {
		if (!newPosition)
			return;
		signalCallback.accept(networkPower);
	}

	public void notifySignalChange() {
		getHandler().updateNetworkOf(getWorld(), this);
	}

	@Override
	public void initialize() {
		super.initialize();
		if (getWorld().isClientSide())
			return;
		getHandler().addToNetwork(getWorld(), this);
		newPosition = true;
	}

	@Override
	public Couple<Frequency> getNetworkKey() {
		return Couple.create(frequencyFirst, frequencyLast);
	}

	@Override
	public void unload() {
		super.unload();
		if (getWorld().isClientSide())
			return;
		getHandler().removeFromNetwork(getWorld(), this);
	}

	@Override
	public void write(ValueOutput nbt) {
		super.write(nbt);
		nbt.store("FrequencyFirst", ItemStack.OPTIONAL_CODEC, frequencyFirst.getStack());
		nbt.store("FrequencyLast", ItemStack.OPTIONAL_CODEC, frequencyLast.getStack());
		nbt.putLong("LastKnownPosition", blockEntity.getBlockPos()
			.asLong());
	}

	@Override
	public void read(ValueInput nbt) {
		long positionInTag = blockEntity.getBlockPos()
			.asLong();
		long positionKey = nbt.getLongOr("LastKnownPosition", 0);
		newPosition = positionInTag != positionKey;

		super.read(nbt);
		frequencyFirst = Frequency.of(nbt.read("FrequencyFirst", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
		frequencyLast = Frequency.of(nbt.read("FrequencyLast", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
	}

	public void setFrequency(boolean first, ItemStack stack) {
		stack = stack.copy();
		stack.setCount(1);
		ItemStack toCompare = first ? frequencyFirst.getStack() : frequencyLast.getStack();
		boolean changed = !ItemStack.isSameItemSameComponents(stack, toCompare);

		if (changed)
			getHandler().removeFromNetwork(getWorld(), this);

		if (first)
			frequencyFirst = Frequency.of(stack);
		else
			frequencyLast = Frequency.of(stack);

		if (!changed)
			return;

		blockEntity.setChanged();
		blockEntity.sendData();
		getHandler().addToNetwork(getWorld(), this);
	}

	public ItemStack getFrequencyStack(boolean first) {
		return first ? frequencyFirst.getStack() : frequencyLast.getStack();
	}

	public ValueBoxTransform getSlot(boolean first) {
		return first ? firstSlot : secondSlot;
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

	private RedstoneLinkNetworkHandler getHandler() {
		return RedstoneLinkNetworkHandler.INSTANCE;
	}

	public boolean testHit(Boolean first, Vec3 hit) {
		BlockState state = blockEntity.getBlockState();
		Vec3 localHit = hit.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
		return (first ? firstSlot : secondSlot).testHit(getWorld(), getPos(), state, localHit);
	}

	@Override
	public boolean isAlive() {
		Level level = getWorld();
		BlockPos pos = getPos();
		if (blockEntity.isRemoved())
			return false;
		if (!level.isLoaded(pos))
			return false;
		return level.getBlockEntity(pos) == blockEntity;
	}

	@Override
	public BlockPos getLocation() {
		return getPos();
	}

	@Override
	public String getClipboardKey() {
		return "Frequencies";
	}

	@Override
	public boolean writeToClipboard(@NotNull HolderLookup.Provider registries, CompoundTag tag, Direction side) {
		DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		tag.store("First", ItemStack.OPTIONAL_CODEC, ops, frequencyFirst.getStack());
		tag.store("Last", ItemStack.OPTIONAL_CODEC, ops, frequencyLast.getStack());
		return true;
	}

	@Override
	public boolean readFromClipboard(@NotNull HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side, boolean simulate) {
		if (!tag.contains("First") || !tag.contains("Last"))
			return false;
		if (simulate)
			return true;
		DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		setFrequency(true, tag.read("First", ItemStack.OPTIONAL_CODEC, ops).orElse(ItemStack.EMPTY));
		setFrequency(false, tag.read("Last", ItemStack.OPTIONAL_CODEC, ops).orElse(ItemStack.EMPTY));
		return true;
	}

}

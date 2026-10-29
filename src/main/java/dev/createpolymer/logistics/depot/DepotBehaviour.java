package dev.createpolymer.logistics.depot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.kinetics.belt.BeltHelper;
import dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class DepotBehaviour extends BlockEntityBehaviour implements Clearable {
	public static final BehaviourType<DepotBehaviour> TYPE = new BehaviourType<>();

	TransportedItemStack heldItem;
	List<TransportedItemStack> incoming;
	ItemStackHandler processingOutputBuffer;
	public DepotItemHandler itemHandler;
	TransportedItemStackHandlerBehaviour transportedHandler;
	Supplier<Integer> maxStackSize;
	Supplier<Boolean> canAcceptItems;
	Predicate<Direction> canFunnelsPullFrom;
	Consumer<ItemStack> onHeldInserted;
	Predicate<ItemStack> acceptedItems;
	boolean allowMerge;

	public DepotBehaviour(SmartBlockEntity be) {
		super(be);
		maxStackSize = () -> heldItem != null ? heldItem.stack.getMaxStackSize() : 64;
		canAcceptItems = () -> true;
		canFunnelsPullFrom = $ -> true;
		acceptedItems = $ -> true;
		onHeldInserted = $ -> {
		};
		incoming = new ArrayList<>();
		itemHandler = new DepotItemHandler(this);
		processingOutputBuffer = new ItemStackHandler(8) {
			@Override
			protected void onContentsChanged(int slot) {
				be.notifyUpdate();
			}
		};
	}

	public void enableMerging() {
		allowMerge = true;
	}

	public DepotBehaviour withCallback(Consumer<ItemStack> changeListener) {
		onHeldInserted = changeListener;
		return this;
	}

	public DepotBehaviour onlyAccepts(Predicate<ItemStack> filter) {
		acceptedItems = filter;
		return this;
	}

	@Override
	public void tick() {
		super.tick();

		Level world = blockEntity.getLevel();

		for (Iterator<TransportedItemStack> iterator = incoming.iterator(); iterator.hasNext(); ) {
			TransportedItemStack ts = iterator.next();
			if (!tick(ts))
				continue;
			if (heldItem == null) {
				heldItem = ts;
			} else {
				if (!ItemHelper.canItemStackAmountsStack(heldItem.stack, ts.stack)) {
					Vec3 vec = VecHelper.getCenterOf(blockEntity.getBlockPos());
					Containers.dropItemStack(blockEntity.getLevel(), vec.x, vec.y + .5f, vec.z, ts.stack);
				} else {
					heldItem.stack.grow(ts.stack.getCount());
				}
			}
			iterator.remove();
			blockEntity.notifyUpdate();
		}

		if (heldItem == null)
			return;
		if (!tick(heldItem))
			return;

		BlockPos pos = blockEntity.getBlockPos();

		if (world.isClientSide())
			return;

		BeltProcessingBehaviour processingBehaviour =
			BlockEntityBehaviour.get(world, pos.above(2), BeltProcessingBehaviour.TYPE);
		if (processingBehaviour == null)
			return;
		if (!heldItem.locked && BeltProcessingBehaviour.isBlocked(world, pos))
			return;

		ItemStack previousItem = heldItem.stack;
		boolean wasLocked = heldItem.locked;
		ProcessingResult result = wasLocked ? processingBehaviour.handleHeldItem(heldItem, transportedHandler)
			: processingBehaviour.handleReceivedItem(heldItem, transportedHandler);
		if (heldItem == null || result == ProcessingResult.REMOVE) {
			heldItem = null;
			blockEntity.sendData();
			return;
		}

		heldItem.locked = result == ProcessingResult.HOLD;
		if (heldItem.locked != wasLocked || !ItemStack.matches(previousItem, heldItem.stack))
			blockEntity.sendData();
	}

	protected boolean tick(TransportedItemStack heldItem) {
		heldItem.prevBeltPosition = heldItem.beltPosition;
		heldItem.prevSideOffset = heldItem.sideOffset;
		float diff = .5f - heldItem.beltPosition;
		if (diff > 1 / 512f) {
			if (diff > 1 / 32f && !BeltHelper.isItemUpright(heldItem.stack))
				heldItem.angle += 1;
			heldItem.beltPosition += diff / 4f;
		}
		return diff < 1 / 16f;
	}

	@Override
	public void clearContent() {
		processingOutputBuffer.setSize(processingOutputBuffer.getSlots());
		incoming.clear();
		heldItem = null;
	}

	@Override
	public void destroy() {
		super.destroy();
		Level level = getWorld();
		BlockPos pos = getPos();
		ItemHelper.dropContents(level, pos, processingOutputBuffer);
		for (TransportedItemStack transportedItemStack : incoming)
			Block.popResource(level, pos, transportedItemStack.stack);
		if (!getHeldItemStack().isEmpty())
			Block.popResource(level, pos, getHeldItemStack());
	}

	@Override
	public void write(ValueOutput compound) {
		if (heldItem != null)
			heldItem.write(compound.child("HeldItem"));
		processingOutputBuffer.serialize(compound.child("OutputBuffer"));
		if (canMergeItems() && !incoming.isEmpty()) {
			ValueOutput.ValueOutputList list = compound.childrenList("Incoming");
			for (TransportedItemStack stack : incoming)
				stack.write(list.addChild());
		}
	}

	@Override
	public void read(ValueInput compound) {
		heldItem = compound.child("HeldItem")
			.map(TransportedItemStack::read)
			.orElse(null);
		processingOutputBuffer.deserialize(compound.childOrEmpty("OutputBuffer"));
		if (canMergeItems()) {
			incoming = new ArrayList<>();
			for (ValueInput entry : compound.childrenListOrEmpty("Incoming"))
				incoming.add(TransportedItemStack.read(entry));
		}
	}

	public void addSubBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new DirectBeltInputBehaviour(blockEntity).allowingBeltFunnels()
			.setInsertionHandler(this::tryInsertingFromSide).considerOccupiedWhen(this::isOccupied));
		transportedHandler = new TransportedItemStackHandlerBehaviour(blockEntity, this::applyToAllItems)
			.withStackPlacement(this::getWorldPositionOf);
		behaviours.add(transportedHandler);
	}

	public ItemStack getHeldItemStack() {
		return heldItem == null ? ItemStack.EMPTY : heldItem.stack;
	}

	public boolean canMergeItems() {
		return allowMerge;
	}

	public int getPresentStackSize() {
		int cumulativeStackSize = 0;
		cumulativeStackSize += getHeldItemStack().getCount();
		for (int slot = 0; slot < processingOutputBuffer.getSlots(); slot++)
			cumulativeStackSize += processingOutputBuffer.getStackInSlot(slot)
				.getCount();
		return cumulativeStackSize;
	}

	public int getRemainingSpace() {
		int cumulativeStackSize = getPresentStackSize();
		for (TransportedItemStack transportedItemStack : incoming)
			cumulativeStackSize += transportedItemStack.stack.getCount();
		int fromGetter =
			Math.min(maxStackSize.get() == 0 ? 64 : maxStackSize.get(), getHeldItemStack().getMaxStackSize());
		return (fromGetter) - cumulativeStackSize;
	}

	public ItemStack insert(TransportedItemStack heldItem, boolean simulate) {
		if (!canAcceptItems.get())
			return heldItem.stack;
		if (!acceptedItems.test(heldItem.stack))
			return heldItem.stack;

		if (canMergeItems()) {
			int remainingSpace = getRemainingSpace();
			ItemStack inserted = heldItem.stack;
			if (remainingSpace <= 0)
				return inserted;
			if (this.heldItem != null && !ItemHelper.canItemStackAmountsStack(this.heldItem.stack, inserted))
				return inserted;

			ItemStack returned = ItemStack.EMPTY;
			if (remainingSpace < inserted.getCount()) {
				returned = heldItem.stack.copyWithCount(inserted.getCount() - remainingSpace);
				if (!simulate) {
					TransportedItemStack copy = heldItem.copy();
					copy.stack.setCount(remainingSpace);
					if (this.heldItem != null)
						incoming.add(copy);
					else
						this.heldItem = copy;
				}
			} else {
				if (!simulate) {
					if (this.heldItem != null)
						incoming.add(heldItem);
					else
						this.heldItem = heldItem;
				}
			}
			return returned;
		}

		ItemStack returned = ItemStack.EMPTY;
		int maxCount = heldItem.stack.getMaxStackSize();
		boolean stackTooLarge = maxCount < heldItem.stack.getCount();
		if (stackTooLarge)
			returned = heldItem.stack.copyWithCount(heldItem.stack.getCount() - maxCount);

		if (simulate)
			return returned;

		if (this.isEmpty()) {
			if (heldItem.insertedFrom.getAxis().isHorizontal())
				DepotSounds.depotSlide(getWorld(), getPos());
			else
				DepotSounds.depotPlop(getWorld(), getPos());
		}

		if (stackTooLarge) {
			heldItem = heldItem.copy();
			heldItem.stack.setCount(maxCount);
		}

		this.heldItem = heldItem;
		onHeldInserted.accept(heldItem.stack);
		return returned;
	}

	public void setHeldItem(TransportedItemStack heldItem) {
		this.heldItem = heldItem;
	}

	public void removeHeldItem() {
		this.heldItem = null;
	}

	public void setCenteredHeldItem(TransportedItemStack heldItem) {
		this.heldItem = heldItem;
		this.heldItem.beltPosition = 0.5f;
		this.heldItem.prevBeltPosition = 0.5f;
	}

	private boolean isOccupied(Direction side) {
		if (!getHeldItemStack().isEmpty() && !canMergeItems())
			return true;
		if (!isOutputEmpty() && !canMergeItems())
			return true;
		if (!canAcceptItems.get())
			return true;
		return false;
	}

	private ItemStack tryInsertingFromSide(TransportedItemStack transportedStack, Direction side, boolean simulate) {
		ItemStack inserted = transportedStack.stack;

		if (isOccupied(side))
			return inserted;

		int size = transportedStack.stack.getCount();
		transportedStack = transportedStack.copy();
		transportedStack.beltPosition = side.getAxis()
			.isVertical() ? .5f : 0;
		transportedStack.insertedFrom = side;
		transportedStack.prevSideOffset = transportedStack.sideOffset;
		transportedStack.prevBeltPosition = transportedStack.beltPosition;
		ItemStack remainder = insert(transportedStack, simulate);
		if (remainder.getCount() != size)
			blockEntity.notifyUpdate();

		return remainder;
	}

	private void applyToAllItems(float maxDistanceFromCentre,
								 Function<TransportedItemStack, TransportedResult> processFunction) {
		if (heldItem == null)
			return;
		if (.5f - heldItem.beltPosition > maxDistanceFromCentre)
			return;

		boolean dirty = false;
		TransportedItemStack transportedItemStack = heldItem;
		ItemStack stackBefore = transportedItemStack.stack.copy();
		TransportedResult result = processFunction.apply(transportedItemStack);
		if (result == null || result.didntChangeFrom(stackBefore))
			return;

		dirty = true;
		heldItem = null;
		if (result.hasHeldOutput())
			setCenteredHeldItem(result.getHeldOutput());

		for (TransportedItemStack added : result.getOutputs()) {
			if (getHeldItemStack().isEmpty()) {
				setCenteredHeldItem(added);
				continue;
			}
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(processingOutputBuffer, added.stack, false);
			Vec3 vec = VecHelper.getCenterOf(blockEntity.getBlockPos());
			Containers.dropItemStack(blockEntity.getLevel(), vec.x, vec.y + .5f, vec.z, remainder);
		}

		if (dirty)
			blockEntity.notifyUpdate();
	}

	public boolean isEmpty() {
		return heldItem == null && isOutputEmpty();
	}

	public boolean isOutputEmpty() {
		for (int i = 0; i < processingOutputBuffer.getSlots(); i++)
			if (!processingOutputBuffer.getStackInSlot(i)
				.isEmpty())
				return false;
		return true;
	}

	private Vec3 getWorldPositionOf(TransportedItemStack transported) {
		return VecHelper.getCenterOf(blockEntity.getBlockPos());
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

	public boolean isItemValid(ItemStack stack) {
		return acceptedItems.test(stack);
	}
}

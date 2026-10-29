package dev.createpolymer.processing.basin;

import java.util.List;
import java.util.Optional;

import dev.createpolymer.kinetics.base.SmartBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.util.ItemSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Ported from Create's BasinBlockEntity (MIT, The Create Team), items only.
 * Fluids, the filter slot, heat and side outputs are not ported yet; results
 * go into the basin's own output slots, which hoppers below can pull from.
 * Container slots 0-8 are inputs and 9-17 are outputs.
 */
public class BasinBlockEntity extends SmartBlockEntity implements WorldlyContainer {

	public final ItemSlots inputInventory = new ItemSlots(9, 64, this::onInputChanged);
	public final ItemSlots outputInventory = new ItemSlots(9, 64, this::setChanged);
	private boolean contentsChanged = true;

	private static final int[] INPUT_SLOTS = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
	private static final int[] OUTPUT_SLOTS = { 9, 10, 11, 12, 13, 14, 15, 16, 17 };

	public BasinBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BASIN, pos, state);
	}

	private void onInputChanged() {
		contentsChanged = true;
		setChanged();
	}

	public void notifyChangeOfContents() {
		contentsChanged = true;
	}

	@Override
	public void tick() {
		super.tick();

		if (level.getGameTime() % 4 == 0)
			collectItemEntities();

		if (!contentsChanged)
			return;
		contentsChanged = false;
		getOperator().ifPresent(be -> be.scheduleBasinUpdate());
	}

	public Optional<BasinOperatingBlockEntity> getOperator() {
		if (level == null)
			return Optional.empty();
		BlockEntity be = level.getBlockEntity(worldPosition.above(2));
		if (be instanceof BasinOperatingBlockEntity operator)
			return Optional.of(operator);
		return Optional.empty();
	}

	/** Items thrown into the basin land on its floor; pick them up from there. */
	private void collectItemEntities() {
		AABB inside = new AABB(worldPosition).deflate(2 / 16d, 0, 2 / 16d).setMaxY(worldPosition.getY() + 1.2);
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, inside)) {
			if (!itemEntity.isAlive())
				continue;
			ItemStack remainder = insertInput(itemEntity.getItem().copy(), false);
			if (remainder.isEmpty())
				itemEntity.discard();
			else
				itemEntity.setItem(remainder);
		}
	}

	/**
	 * Create's BasinInventory rule: an item type may only occupy one input slot,
	 * so mixed ingredients do not crowd each other out.
	 */
	public ItemStack insertInput(ItemStack stack, boolean simulate) {
		for (int i = 0; i < inputInventory.size(); i++)
			if (ItemStack.isSameItemSameComponents(stack, inputInventory.get(i)))
				return inputInventory.insert(i, stack, simulate);
		for (int i = 0; i < inputInventory.size(); i++)
			if (inputInventory.get(i).isEmpty())
				return inputInventory.insert(i, stack, simulate);
		return stack;
	}

	private boolean canInsertInto(int slot, ItemStack stack) {
		for (int i = 0; i < inputInventory.size(); i++)
			if (ItemStack.isSameItemSameComponents(stack, inputInventory.get(i)))
				return i == slot;
		for (int i = 0; i < inputInventory.size(); i++)
			if (inputInventory.get(i).isEmpty())
				return i == slot;
		return false;
	}

	public boolean isEmpty() {
		return inputInventory.isEmpty() && outputInventory.isEmpty();
	}

	public boolean canContinueProcessing() {
		return true;
	}

	/** Puts recipe results into the output slots. All or nothing. */
	public boolean acceptOutputs(List<ItemStack> outputItems, boolean simulate) {
		ItemSlots target = simulate ? outputInventory.copy() : outputInventory;
		for (ItemStack itemStack : outputItems)
			if (!target.insertStacked(itemStack.copy(), false).isEmpty())
				return false;
		return true;
	}

	public void takeContents(Player player) {
		boolean success = false;
		for (ItemSlots inv : new ItemSlots[] { inputInventory, outputInventory }) {
			for (int slot = 0; slot < inv.size(); slot++) {
				ItemStack stackInSlot = inv.get(slot);
				if (stackInSlot.isEmpty())
					continue;
				player.getInventory().placeItemBackInInventory(stackInSlot, Prediction.SERVER_ONLY);
				inv.set(slot, ItemStack.EMPTY);
				success = true;
			}
		}
		if (success)
			level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
				1f + level.getRandom().nextFloat());
		notifyChangeOfContents();
	}

	@Override
	public void destroy() {
		super.destroy();
		for (ItemSlots inv : new ItemSlots[] { inputInventory, outputInventory }) {
			for (ItemStack s : inv.stacks())
				Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), s);
			inv.clear();
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		destroy();
		remove();
	}

	@Override
	protected void write(ValueOutput compound) {
		super.write(compound);
		inputInventory.save(compound.child("InputItems"));
		outputInventory.save(compound.child("OutputItems"));
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		inputInventory.load(compound.childOrEmpty("InputItems"));
		outputInventory.load(compound.childOrEmpty("OutputItems"));
		contentsChanged = true;
	}

	// ---- WorldlyContainer ----

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot < 9 && canInsertInto(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot >= 9;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot < 9 && canInsertInto(slot, stack);
	}

	@Override
	public int getContainerSize() {
		return 18;
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot < 9 ? inputInventory.get(slot) : outputInventory.get(slot - 9);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return slot < 9 ? inputInventory.extract(slot, count, false) : outputInventory.extract(slot - 9, count, false);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack s = getItem(slot);
		setItem(slot, ItemStack.EMPTY);
		return s;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot < 9)
			inputInventory.set(slot, stack);
		else
			outputInventory.set(slot - 9, stack);
	}

	@Override
	public boolean stillValid(Player player) {
		return false;
	}

	@Override
	public void clearContent() {
		inputInventory.clear();
		outputInventory.clear();
	}
}

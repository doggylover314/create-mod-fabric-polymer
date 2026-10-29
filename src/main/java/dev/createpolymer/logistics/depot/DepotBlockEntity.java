package dev.createpolymer.logistics.depot;

import java.util.List;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Ported from Create's DepotBlockEntity / DepotBehaviour (MIT, The Create Team), without belts.
 * Holds one stack on top; a press above processes it, results go to the output buffer.
 * Container slot 0 is the held stack, slots 1-8 the outputs.
 */
public class DepotBlockEntity extends SmartBlockEntity implements WorldlyContainer {
	public final ItemSlots held = new ItemSlots(1, 64, this::changed);
	public final ItemSlots outputs = new ItemSlots(8, 64, this::changed);

	private static final int[] TOP_SLOTS = { 0 };
	private static final int[] DOWN_SLOTS = { 1, 2, 3, 4, 5, 6, 7, 8 };
	private static final int[] SIDE_SLOTS = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };

	public DepotBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DEPOT, pos, state);
	}

	private void changed() {
		setChanged();
		sendData();
		if (level != null && !level.isClientSide())
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
	}

	public ItemStack getHeldItem() {
		return held.get(0);
	}

	public void setHeldItem(ItemStack stack) {
		held.set(0, stack);
	}

	@Override
	public void tick() {
		super.tick();
		if (level.getGameTime() % 4 == 0)
			collectItemEntities();
	}

	/** Items landing on the depot are taken in (Create's SharedDepotBlockMethods.onLanded). */
	private void collectItemEntities() {
		AABB top = new AABB(worldPosition.getX(), worldPosition.getY() + 13 / 16d, worldPosition.getZ(),
			worldPosition.getX() + 1, worldPosition.getY() + 1.1, worldPosition.getZ() + 1);
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, top)) {
			if (!itemEntity.isAlive())
				continue;
			ItemStack remainder = held.insert(0, itemEntity.getItem().copy(), false);
			if (remainder.isEmpty())
				itemEntity.discard();
			else
				itemEntity.setItem(remainder);
		}
	}

	/** Right-click on top: take everything, then put down what was in hand (Create's onUse). */
	public void use(Player player, net.minecraft.world.InteractionHand hand) {
		ItemStack inHand = player.getItemInHand(hand);
		boolean wasEmptyHanded = inHand.isEmpty();
		if (!getHeldItem().isEmpty()) {
			player.getInventory().placeItemBackInInventory(getHeldItem(), Prediction.SERVER_ONLY);
			setHeldItem(ItemStack.EMPTY);
			level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f, 1f + level.getRandom().nextFloat());
		}
		for (int i = 0; i < outputs.size(); i++)
			player.getInventory().placeItemBackInInventory(outputs.extract(i, 64, false), Prediction.SERVER_ONLY);
		if (!wasEmptyHanded) {
			setHeldItem(inHand.copy());
			player.setItemInHand(hand, ItemStack.EMPTY);
			level.playSound(null, worldPosition, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, .5f, 1f);
		}
	}

	/**
	 * Called by a press at the bottom of its stroke. {@code results} came from pressing
	 * {@code processed} items; the rest of the held stack stays put.
	 */
	public void applyProcessing(int processed, List<ItemStack> results) {
		ItemStack remaining = getHeldItem().copy();
		remaining.shrink(processed);
		setHeldItem(remaining);
		for (ItemStack result : results) {
			ItemStack left = outputs.insertStacked(result, false);
			if (!left.isEmpty())
				Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, left);
		}
	}

	public boolean canProcessFurther() {
		for (ItemStack s : outputs.stacks())
			if (s.isEmpty())
				return true;
		return false;
	}

	@Override
	public void destroy() {
		super.destroy();
		for (ItemSlots inv : new ItemSlots[] { held, outputs }) {
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
		held.save(compound.child("HeldItem"));
		outputs.save(compound.child("OutputBuffer"));
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		held.load(compound.childOrEmpty("HeldItem"));
		outputs.load(compound.childOrEmpty("OutputBuffer"));
	}

	// ---- WorldlyContainer ----

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.UP ? TOP_SLOTS : side == Direction.DOWN ? DOWN_SLOTS : SIDE_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot == 0;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot != 0;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == 0;
	}

	@Override
	public int getContainerSize() {
		return 9;
	}

	@Override
	public boolean isEmpty() {
		return held.isEmpty() && outputs.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot == 0 ? held.get(0) : outputs.get(slot - 1);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return slot == 0 ? held.extract(0, count, false) : outputs.extract(slot - 1, count, false);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack s = getItem(slot);
		setItem(slot, ItemStack.EMPTY);
		return s;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot == 0)
			held.set(0, stack);
		else
			outputs.set(slot - 1, stack);
	}

	@Override
	public boolean stillValid(Player player) {
		return false;
	}

	@Override
	public void clearContent() {
		held.clear();
		outputs.clear();
	}
}

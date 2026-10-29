package dev.createpolymer.kinetics.millstone;

import java.util.Optional;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.processing.recipe.MillingRecipe;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModRecipeTypes;
import dev.createpolymer.util.ItemSlots;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's MillstoneBlockEntity (MIT, The Create Team).
 * Exposed as a vanilla container: slot 0 is the input, slots 1-9 are outputs.
 */
public class MillstoneBlockEntity extends KineticBlockEntity implements WorldlyContainer {

	public final ItemSlots inputInv = new ItemSlots(1, 64, this::setChanged);
	public final ItemSlots outputInv = new ItemSlots(9, 64, this::setChanged);
	public int timer;
	private MillingRecipe lastRecipe;

	private static final int[] UP_SLOTS = { 0 };
	private static final int[] DOWN_SLOTS = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
	private static final int[] SIDE_SLOTS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };

	public MillstoneBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MILLSTONE, pos, state);
	}

	@Override
	public void tick() {
		super.tick();

		if (level.getGameTime() % 4 == 0)
			collectItemEntities();

		if (getSpeed() == 0)
			return;
		for (int i = 0; i < outputInv.size(); i++)
			if (outputInv.get(i).getCount() == outputInv.getSlotLimit(outputInv.get(i)) && !outputInv.get(i).isEmpty())
				return;

		if (timer > 0) {
			timer -= getProcessingSpeed();
			if (level.getGameTime() % 2 == 0)
				spawnParticles();
			if (timer <= 0)
				process();
			return;
		}

		if (inputInv.get(0).isEmpty())
			return;

		SingleRecipeInput inventoryIn = new SingleRecipeInput(inputInv.get(0));
		if (lastRecipe == null || !lastRecipe.matches(inventoryIn, level)) {
			Optional<MillingRecipe> recipe = findRecipe(inputInv.get(0));
			if (recipe.isEmpty()) {
				timer = 100;
				sendData();
			} else {
				lastRecipe = recipe.get();
				timer = lastRecipe.getProcessingDuration();
				sendData();
			}
			return;
		}

		timer = lastRecipe.getProcessingDuration();
		sendData();
	}

	private Optional<MillingRecipe> findRecipe(ItemStack stack) {
		if (!(level instanceof ServerLevel serverLevel))
			return Optional.empty();
		return serverLevel.recipeAccess()
			.getRecipeFor(ModRecipeTypes.MILLING, new SingleRecipeInput(stack), level)
			.map(RecipeHolder::value);
	}

	/** Create inserts items that land on the millstone; done here by scanning above the block. */
	private void collectItemEntities() {
		AABB box = new AABB(worldPosition.above()).inflate(-.05, 0, -.05).move(0, -.5, 0);
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, box)) {
			if (!itemEntity.isAlive())
				continue;
			ItemStack stack = itemEntity.getItem();
			if (!canProcess(stack))
				continue;
			ItemStack remainder = inputInv.insert(0, stack, false);
			if (remainder.isEmpty())
				itemEntity.discard();
			else
				itemEntity.setItem(remainder);
		}
	}

	@Override
	public void destroy() {
		super.destroy();
		for (ItemStack s : inputInv.stacks())
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), s);
		for (ItemStack s : outputInv.stacks())
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), s);
		inputInv.clear();
		outputInv.clear();
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// Do not let vanilla drop the container contents a second time.
		destroy();
		remove();
	}

	private void process() {
		SingleRecipeInput inventoryIn = new SingleRecipeInput(inputInv.get(0));

		if (lastRecipe == null || !lastRecipe.matches(inventoryIn, level)) {
			Optional<MillingRecipe> recipe = findRecipe(inputInv.get(0));
			if (recipe.isEmpty())
				return;
			lastRecipe = recipe.get();
		}

		ItemStack stackInSlot = inputInv.get(0);
		ItemStackTemplate remainder = stackInSlot.getItem().getCraftingRemainder();
		stackInSlot.shrink(1);
		inputInv.set(0, stackInSlot);
		lastRecipe.rollResults(level.getRandom()).forEach(stack -> outputInv.insertStacked(stack, false));
		if (remainder != null)
			outputInv.insertStacked(remainder.create(), false);

		sendData();
		setChanged();
	}

	public void spawnParticles() {
		ItemStack stackInSlot = inputInv.get(0);
		if (stackInSlot.isEmpty() || !(level instanceof ServerLevel serverLevel))
			return;

		ItemParticleOption data = new ItemParticleOption(ParticleTypes.ITEM, stackInSlot.getItem());
		float angle = level.getRandom().nextFloat() * 360;
		Vec3 offset = VecHelper.rotate(new Vec3(0, 0, 0.5f), angle, Direction.Axis.Y);
		Vec3 target = VecHelper.rotate(offset, getSpeed() > 0 ? 25 : -25, Direction.Axis.Y);
		Vec3 center = offset.add(VecHelper.getCenterOf(worldPosition));
		target = VecHelper.offsetRandomly(target.subtract(offset), level.getRandom(), 1 / 128f);
		serverLevel.sendParticles(data, center.x, center.y, center.z, 0, target.x, target.y, target.z, 1);
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putInt("Timer", timer);
		inputInv.save(compound.child("InputInventory"));
		outputInv.save(compound.child("OutputInventory"));
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		timer = compound.getIntOr("Timer", 0);
		inputInv.load(compound.childOrEmpty("InputInventory"));
		outputInv.load(compound.childOrEmpty("OutputInventory"));
		super.read(compound);
	}

	public int getProcessingSpeed() {
		return Mth.clamp((int) Math.abs(getSpeed() / 16f), 1, 512);
	}

	public boolean canProcess(ItemStack stack) {
		if (stack.isEmpty())
			return false;
		if (lastRecipe != null && lastRecipe.matches(new SingleRecipeInput(stack), level))
			return true;
		return findRecipe(stack).isPresent();
	}

	/** Right-click with an empty hand: take outputs, or the input if there are no outputs. */
	public void takeContents(Player player) {
		boolean emptyOutput = true;
		for (int slot = 0; slot < outputInv.size(); slot++) {
			ItemStack stackInSlot = outputInv.get(slot);
			if (!stackInSlot.isEmpty())
				emptyOutput = false;
			player.getInventory().placeItemBackInInventory(stackInSlot, net.minecraft.util.Prediction.SERVER_ONLY);
			outputInv.set(slot, ItemStack.EMPTY);
		}

		if (emptyOutput) {
			player.getInventory().placeItemBackInInventory(inputInv.get(0), net.minecraft.util.Prediction.SERVER_ONLY);
			inputInv.set(0, ItemStack.EMPTY);
		}

		setChanged();
		sendData();
	}

	// ---- WorldlyContainer (hoppers etc.) ----

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.UP ? UP_SLOTS : side == Direction.DOWN ? DOWN_SLOTS : SIDE_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot == 0 && canProcess(stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot != 0;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == 0 && canProcess(stack);
	}

	@Override
	public int getContainerSize() {
		return 10;
	}

	@Override
	public boolean isEmpty() {
		return inputInv.isEmpty() && outputInv.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot == 0 ? inputInv.get(0) : outputInv.get(slot - 1);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return slot == 0 ? inputInv.extract(0, count, false) : outputInv.extract(slot - 1, count, false);
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
			inputInv.set(0, stack);
		else
			outputInv.set(slot - 1, stack);
	}

	@Override
	public boolean stillValid(Player player) {
		return false;
	}

	@Override
	public void clearContent() {
		inputInv.clear();
		outputInv.clear();
	}
}

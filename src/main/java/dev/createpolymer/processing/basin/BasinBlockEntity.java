package dev.createpolymer.processing.basin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.equipment.goggles.IHaveGoggleInformation;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.fluid.CombinedTankWrapper;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.ItemSlots;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.LangBuilder;
import dev.createpolymer.util.NBTHelper;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Ported from Create's BasinBlockEntity (MIT, The Create Team).
 * The filter slot is not ported yet. Results go into the basin's own output slots, which hoppers below can pull
 * from, or out of the side the basin faces (wrenched) into a funnel, belt or inventory.
 * Container slots 0-8 are inputs and 9-17 are outputs.
 * Fluids are as in Create: two input tanks and two output tanks of 1000 mB, exposed as one fluid handler.
 */
public class BasinBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, WorldlyContainer {

	public final ItemSlots inputInventory = new ItemSlots(9, 64, this::onInputChanged);
	public final ItemSlots outputInventory = new ItemSlots(9, 64, this::setChanged);
	private boolean contentsChanged = true;
	private @Nullable HeatLevel cachedHeatLevel;

	List<Direction> disabledSpoutputs = new ArrayList<>();
	@Nullable Direction preferredSpoutput;
	protected List<ItemStack> spoutputBuffer = new ArrayList<>();
	protected List<FluidStack> spoutputFluidBuffer = new ArrayList<>();

	// assigned in addBehaviours, which runs before this class's fields are initialised
	public SmartFluidTankBehaviour inputTank;
	protected SmartFluidTankBehaviour outputTank;
	private Couple<SmartFluidTankBehaviour> tanks;
	protected IFluidHandler fluidCapability;

	private static final int[] INPUT_SLOTS = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
	private static final int[] OUTPUT_SLOTS = { 9, 10, 11, 12, 13, 14, 15, 16, 17 };

	public BasinBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BASIN, pos, state);
		tanks = Couple.create(inputTank, outputTank);
	}

	public static void registerCapabilities() {
		FluidHandlerLookup.registerBlockEntity(ModBlockEntities.BASIN, (be, side) -> be.fluidCapability);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		inputTank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 2, 1000, true)
			.whenFluidUpdates(() -> contentsChanged = true);
		outputTank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.OUTPUT, this, 2, 1000, true)
			.whenFluidUpdates(() -> contentsChanged = true)
			.forbidInsertion();
		behaviours.add(inputTank);
		behaviours.add(outputTank);

		fluidCapability = new CombinedTankWrapper(outputTank.getCapability(), inputTank.getCapability());
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
		cachedHeatLevel = null;

		super.tick();

		if (level.getGameTime() % 4 == 0)
			collectItemEntities();

		if ((!spoutputBuffer.isEmpty() || !spoutputFluidBuffer.isEmpty()) && !level.isClientSide())
			tryClearingSpoutputOverflow();
		if (!contentsChanged)
			return;
		contentsChanged = false;
		getOperator().ifPresent(be -> be.scheduleBasinUpdate());

		for (Direction offset : Iterate.horizontalDirections) {
			BlockPos toUpdate = worldPosition.above()
				.relative(offset);
			BlockState stateToUpdate = level.getBlockState(toUpdate);
			if (stateToUpdate.getBlock() instanceof BasinBlock
				&& stateToUpdate.getValue(BasinBlock.FACING) == offset.getOpposite()) {
				BlockEntity be = level.getBlockEntity(toUpdate);
				if (be instanceof BasinBlockEntity)
					((BasinBlockEntity) be).contentsChanged = true;
			}
		}
	}

	public void onWrenched(Direction face) {
		BlockState blockState = getBlockState();
		Direction currentFacing = blockState.getValue(BasinBlock.FACING);

		disabledSpoutputs.remove(face);
		if (currentFacing == face) {
			if (preferredSpoutput == face)
				preferredSpoutput = null;
			disabledSpoutputs.add(face);
		} else
			preferredSpoutput = face;

		updateSpoutput();
	}

	private void updateSpoutput() {
		BlockState blockState = getBlockState();
		Direction currentFacing = blockState.getValue(BasinBlock.FACING);
		Direction newFacing = Direction.DOWN;
		for (Direction test : Iterate.horizontalDirections) {
			boolean canOutputTo = BasinBlock.canOutputTo(level, worldPosition, test);
			if (canOutputTo && !disabledSpoutputs.contains(test))
				newFacing = test;
		}

		if (preferredSpoutput != null && BasinBlock.canOutputTo(level, worldPosition, preferredSpoutput)
			&& preferredSpoutput != Direction.UP)
			newFacing = preferredSpoutput;

		if (newFacing == currentFacing)
			return;

		level.setBlockAndUpdate(worldPosition, blockState.setValue(BasinBlock.FACING, newFacing));

		if (newFacing.getAxis()
			.isVertical())
			return;

		for (int slot = 0; slot < outputInventory.size(); slot++) {
			ItemStack extractItem = outputInventory.extract(slot, 64, true);
			if (extractItem.isEmpty())
				continue;
			if (acceptOutputs(ImmutableList.of(extractItem), Collections.emptyList(), true))
				acceptOutputs(ImmutableList.of(outputInventory.extract(slot, 64, false)), Collections.emptyList(),
					false);
		}

		IFluidHandler handler = outputTank.getCapability();
		for (int slot = 0; slot < handler.getTanks(); slot++) {
			FluidStack fs = handler.getFluidInTank(slot)
				.copy();
			if (fs.isEmpty())
				continue;
			if (acceptOutputs(Collections.emptyList(), ImmutableList.of(fs), true)) {
				handler.drain(fs, FluidAction.EXECUTE);
				acceptOutputs(Collections.emptyList(), ImmutableList.of(fs), false);
			}
		}

		notifyChangeOfContents();
		notifyUpdate();
	}

	private void tryClearingSpoutputOverflow() {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof BasinBlock))
			return;
		Direction direction = blockState.getValue(BasinBlock.FACING);
		BlockEntity be = level.getBlockEntity(worldPosition.below()
			.relative(direction));

		FilteringBehaviour filter = null;
		InvManipulationBehaviour inserter = null;
		if (be != null) {
			filter = BlockEntityBehaviour.get(level, be.getBlockPos(), FilteringBehaviour.TYPE);
			inserter = BlockEntityBehaviour.get(level, be.getBlockPos(), InvManipulationBehaviour.TYPE);
		}

		if (filter != null && filter.isRecipeFilter())
			filter = null; // Do not test spout outputs against the recipe filter

		IItemHandler targetInv = be == null ? null : getTargetInventory(be, direction, inserter);

		IFluidHandler targetTank = be == null ? null
			: FluidHandlerLookup.get(level, be.getBlockPos(), direction.getOpposite());

		boolean update = false;

		for (Iterator<ItemStack> iterator = spoutputBuffer.iterator(); iterator.hasNext();) {
			ItemStack itemStack = iterator.next();

			if (direction == Direction.DOWN) {
				Block.popResource(level, worldPosition, itemStack);
				iterator.remove();
				update = true;
				continue;
			}

			if (targetInv == null)
				break;

			ItemStack remainder = ItemHandlerHelper.insertItemStacked(targetInv, itemStack, true);
			if (remainder.getCount() == itemStack.getCount())
				continue;
			if (filter != null && !filter.test(itemStack))
				continue;

			update = true;

			remainder = ItemHandlerHelper.insertItemStacked(targetInv, itemStack.copy(), false);
			if (remainder.isEmpty())
				iterator.remove();
			else
				itemStack.setCount(remainder.getCount());
		}

		for (Iterator<FluidStack> iterator = spoutputFluidBuffer.iterator(); iterator.hasNext();) {
			FluidStack fluidStack = iterator.next();

			if (direction == Direction.DOWN) {
				iterator.remove();
				update = true;
				continue;
			}

			if (targetTank == null)
				break;

			for (boolean simulate : Iterate.trueAndFalse) {
				FluidAction action = simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE;
				int fill = targetTank instanceof SmartFluidTankBehaviour.InternalFluidHandler
					? ((SmartFluidTankBehaviour.InternalFluidHandler) targetTank).forceFill(fluidStack.copy(), action)
					: targetTank.fill(fluidStack.copy(), action);
				if (fill != fluidStack.getAmount())
					break;
				if (simulate)
					continue;

				update = true;
				iterator.remove();
			}
		}

		if (update) {
			notifyChangeOfContents();
			sendData();
		}
	}

	@Nullable
	private IItemHandler getTargetInventory(BlockEntity be, Direction direction,
		@Nullable InvManipulationBehaviour inserter) {
		IItemHandler handler = ItemHandlerLookup.get(level, be.getBlockPos(), direction.getOpposite());
		if (handler != null)
			return handler;
		return inserter == null ? null : inserter.getInventory();
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

	/**
	 * Heat of the block below the basin. Create's BasinBlockEntity.getHeatLevelOf also counts passive heaters
	 * (lit campfires and the like) as smouldering, which no heat condition can tell apart from no heat.
	 */
	public static HeatLevel getHeatLevelOf(BlockState state) {
		return BlazeBurnerBlock.getHeatLevelOf(state);
	}

	HeatLevel getHeatLevel() {
		if (cachedHeatLevel == null) {
			if (level == null)
				return HeatLevel.NONE;

			cachedHeatLevel = getHeatLevelOf(level.getBlockState(getBlockPos().below(1)));
		}
		return cachedHeatLevel;
	}

	public boolean isEmpty() {
		return inputInventory.isEmpty() && outputInventory.isEmpty() && inputTank.isEmpty() && outputTank.isEmpty();
	}

	public boolean canContinueProcessing() {
		return spoutputBuffer.isEmpty() && spoutputFluidBuffer.isEmpty();
	}

	/** Puts recipe results into the output slots. All or nothing. */
	public boolean acceptOutputs(List<ItemStack> outputItems, boolean simulate) {
		return acceptOutputs(outputItems, Collections.emptyList(), simulate);
	}

	/** Puts recipe results into the output slots and output tanks. Items and fluids each all or nothing. */
	public boolean acceptOutputs(List<ItemStack> outputItems, List<FluidStack> outputFluids, boolean simulate) {
		outputTank.allowInsertion();
		boolean accepted = acceptOutputsInner(outputItems, outputFluids, simulate);
		outputTank.forbidInsertion();
		return accepted;
	}

	private boolean acceptOutputsInner(List<ItemStack> outputItems, List<FluidStack> outputFluids, boolean simulate) {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof BasinBlock))
			return false;

		Direction direction = blockState.getValue(BasinBlock.FACING);
		if (direction != Direction.DOWN) {

			BlockEntity be = level.getBlockEntity(worldPosition.below()
				.relative(direction));

			InvManipulationBehaviour inserter =
				be == null ? null : BlockEntityBehaviour.get(level, be.getBlockPos(), InvManipulationBehaviour.TYPE);
			IItemHandler targetInv = be == null ? null : getTargetInventory(be, direction, inserter);
			IFluidHandler targetTank = be == null ? null
				: FluidHandlerLookup.get(level, be.getBlockPos(), direction.getOpposite());
			boolean externalTankNotPresent = targetTank == null;

			if (!outputItems.isEmpty() && targetInv == null)
				return false;
			if (!outputFluids.isEmpty() && externalTankNotPresent) {
				// Special case - fluid outputs but output only accepts items
				targetTank = outputTank.getCapability();
				if (targetTank == null)
					return false;
				if (!acceptFluidOutputsIntoBasin(outputFluids, simulate, targetTank))
					return false;
			}

			if (simulate)
				return true;
			for (ItemStack itemStack : outputItems)
				if (!itemStack.isEmpty())
					spoutputBuffer.add(itemStack.copy());
			if (!externalTankNotPresent)
				for (FluidStack fluidStack : outputFluids)
					spoutputFluidBuffer.add(fluidStack.copy());
			return true;
		}

		ItemSlots target = simulate ? outputInventory.copy() : outputInventory;
		for (ItemStack itemStack : outputItems)
			if (!target.insertStacked(itemStack.copy(), false).isEmpty())
				return false;
		if (outputFluids.isEmpty())
			return true;
		return acceptFluidOutputsIntoBasin(outputFluids, simulate, outputTank.getCapability());
	}

	private boolean acceptFluidOutputsIntoBasin(List<FluidStack> outputFluids, boolean simulate,
												IFluidHandler targetTank) {
		for (FluidStack fluidStack : outputFluids) {
			FluidAction action = simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE;
			int fill = targetTank instanceof SmartFluidTankBehaviour.InternalFluidHandler
				? ((SmartFluidTankBehaviour.InternalFluidHandler) targetTank).forceFill(fluidStack.copy(), action)
				: targetTank.fill(fluidStack.copy(), action);
			if (fill != fluidStack.getAmount())
				return false;
		}
		return true;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		Lang.builder("create").translate("gui.goggles.basin_contents")
			.forGoggles(tooltip);

		boolean isEmpty = true;

		for (ItemSlots inv : new ItemSlots[] { inputInventory, outputInventory }) {
			for (int i = 0; i < inv.size(); i++) {
				ItemStack stackInSlot = inv.get(i);
				if (stackInSlot.isEmpty())
					continue;
				Lang.builder("create").text("")
					.add(stackInSlot.getItemName().copy()
						.withStyle(ChatFormatting.GRAY))
					.add(Lang.builder("create").text(" x" + stackInSlot.getCount())
						.style(ChatFormatting.GREEN))
					.forGoggles(tooltip, 1);
				isEmpty = false;
			}
		}

		LangBuilder mb = Lang.builder("create").translate("generic.unit.millibuckets");
		for (int i = 0; i < fluidCapability.getTanks(); i++) {
			FluidStack fluidStack = fluidCapability.getFluidInTank(i);
			if (fluidStack.isEmpty())
				continue;
			Lang.builder("create").text("")
				.add(Lang.builder("create").add(fluidStack.getHoverName().copy())
					.add(Lang.builder("create").text(" "))
					.style(ChatFormatting.GRAY)
					.add(Lang.builder("create").text(Lang.formatNumber(fluidStack.getAmount()))
						.add(mb)
						.style(ChatFormatting.BLUE)))
				.forGoggles(tooltip, 1);
			isEmpty = false;
		}

		if (isEmpty)
			tooltip.remove(0);

		return true;
	}

	public Couple<SmartFluidTankBehaviour> getTanks() {
		return tanks;
	}

	public float getTotalFluidUnits(float partialTicks) {
		int renderedFluids = 0;
		float totalUnits = 0;

		for (SmartFluidTankBehaviour behaviour : getTanks()) {
			if (behaviour == null)
				continue;
			for (SmartFluidTankBehaviour.TankSegment tankSegment : behaviour.getTanks()) {
				if (tankSegment.getRenderedFluid()
					.isEmpty())
					continue;
				float units = tankSegment.getTotalUnits(partialTicks);
				if (units < 1)
					continue;
				totalUnits += units;
				renderedFluids++;
			}
		}

		if (renderedFluids == 0)
			return 0;
		if (totalUnits < 1)
			return 0;
		return totalUnits;
	}

	public IFluidHandler getFluidCapability() {
		return fluidCapability;
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
		spoutputBuffer.forEach(is -> Block.popResource(level, worldPosition, is));
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

		if (preferredSpoutput != null)
			NBTHelper.writeEnum(compound, "PreferredSpoutput", preferredSpoutput);
		ValueOutput.TypedOutputList<String> disabledList = compound.list("DisabledSpoutput", Codec.STRING);
		disabledSpoutputs.forEach(d -> disabledList.add(d.name()));
		NBTHelper.writeItemList(compound, "Overflow", spoutputBuffer);
		ValueOutput.TypedOutputList<FluidStack> overflowFluids =
			compound.list("FluidOverflow", FluidStack.OPTIONAL_CODEC);
		spoutputFluidBuffer.forEach(overflowFluids::add);
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		inputInventory.load(compound.childOrEmpty("InputItems"));
		outputInventory.load(compound.childOrEmpty("OutputItems"));

		preferredSpoutput = compound.read("PreferredSpoutput", Codec.STRING)
			.map(Direction::valueOf)
			.orElse(null);
		disabledSpoutputs.clear();
		compound.listOrEmpty("DisabledSpoutput", Codec.STRING)
			.forEach(d -> disabledSpoutputs.add(Direction.valueOf(d)));
		spoutputBuffer = new ArrayList<>(NBTHelper.readItemList(compound, "Overflow"));
		spoutputFluidBuffer = new ArrayList<>();
		compound.listOrEmpty("FluidOverflow", FluidStack.OPTIONAL_CODEC)
			.forEach(spoutputFluidBuffer::add);
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
		spoutputBuffer.clear();
		spoutputFluidBuffer.clear();
	}
}

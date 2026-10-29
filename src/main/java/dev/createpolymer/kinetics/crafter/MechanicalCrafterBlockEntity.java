package dev.createpolymer.kinetics.crafter;

import static dev.createpolymer.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.edgeInteraction.EdgeInteractionBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.kinetics.crafter.ConnectedInputHandler.ConnectedInput;
import dev.createpolymer.kinetics.crafter.RecipeGridHandler.GroupedItems;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.CrafterRegistry;
import dev.createpolymer.util.BlockFace;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Pointing;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class MechanicalCrafterBlockEntity extends KineticBlockEntity {

	public enum Phase {
		IDLE, ACCEPTING, ASSEMBLING, EXPORTING, WAITING, CRAFTING, INSERTING;
	}

	/** Create's SmartInventory(1, be, 1, false) with forbidExtraction(): one item, nothing can be taken out. */
	public static class Inventory extends ItemStackHandler {

		private final MechanicalCrafterBlockEntity blockEntity;
		private final Consumer<Integer> updateCallback;

		public Inventory(MechanicalCrafterBlockEntity blockEntity) {
			super(1);
			this.blockEntity = blockEntity;
			updateCallback = slot -> {
				if (getItem(slot).isEmpty())
					return;
				if (blockEntity.phase == Phase.IDLE)
					blockEntity.checkCompletedRecipe(false);
			};
		}

		public ItemStack getItem(int slot) {
			return getStackInSlot(slot);
		}

		public boolean isEmpty() {
			return getItem(0).isEmpty();
		}

		@Override
		protected void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			updateCallback.accept(slot);
			blockEntity.notifyUpdate();
		}

		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if (blockEntity.phase != Phase.IDLE)
				return stack;
			if (blockEntity.covered)
				return stack;
			ItemStack insertItem = super.insertItem(slot, stack, simulate);
			if (insertItem.getCount() != stack.getCount() && !simulate)
				blockEntity.getLevel()
					.playSound(null, blockEntity.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25f, .5f);
			return insertItem;
		}

	}

	protected Inventory inventory;
	protected GroupedItems groupedItems = new GroupedItems();
	protected ConnectedInput input = new ConnectedInput();
	@Nullable
	protected IItemHandler invCap;
	protected Phase phase;
	protected int countDown;
	protected boolean covered;
	protected boolean wasPoweredBefore;

	protected GroupedItems groupedItemsBeforeCraft; // for rendering
	private InvManipulationBehaviour inserting;
	private EdgeInteractionBehaviour connectivity;

	public MechanicalCrafterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(20);
		phase = Phase.IDLE;
		groupedItemsBeforeCraft = new GroupedItems();
		inventory = new Inventory(this);

		// Does not get serialized due to active checking in tick
		wasPoweredBefore = true;
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(CrafterRegistry.MECHANICAL_CRAFTER_BE,
			(be, side) -> be.getInvCapability());
	}

	protected IItemHandler getInvCapability() {
		if (invCap == null) {
			invCap = input.getItemHandler(getLevel(), getBlockPos());
		}
		return invCap;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		inserting = new InvManipulationBehaviour(this, this::getTargetFace);
		connectivity = new EdgeInteractionBehaviour(this, ConnectedInputHandler::toggleConnection)
			.connectivity(ConnectedInputHandler::shouldConnect)
			.require(item -> item == ModItems.WRENCH);
		behaviours.add(inserting);
		behaviours.add(connectivity);
	}

	public void blockChanged() {
		removeBehaviour(InvManipulationBehaviour.TYPE);
		inserting = new InvManipulationBehaviour(this, this::getTargetFace);
		attachBehaviourLate(inserting);
	}

	public BlockFace getTargetFace(Level world, BlockPos pos, BlockState state) {
		return new BlockFace(pos, MechanicalCrafterBlock.getTargetDirection(state));
	}

	public Direction getTargetDirection() {
		return MechanicalCrafterBlock.getTargetDirection(getBlockState());
	}

	@Override
	public void writeSafe(ValueOutput compound) {
		super.writeSafe(compound);
		if (input == null)
			return;

		input.write(compound.child("ConnectedInput"));
	}

	@Override
	protected void write(ValueOutput compound) {
		inventory.serialize(compound.child("Inventory"));
		input.write(compound.child("ConnectedInput"));
		groupedItems.write(compound.child("GroupedItems"));

		compound.putString("Phase", phase.name());
		compound.putInt("CountDown", countDown);
		compound.putBoolean("Cover", covered);

		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		inventory.deserialize(compound.childOrEmpty("Inventory"));
		input.read(compound.childOrEmpty("ConnectedInput"));
		groupedItems = GroupedItems.read(compound.childOrEmpty("GroupedItems"));
		phase = Phase.IDLE;
		String name = compound.getStringOr("Phase", "");
		for (Phase phase : Phase.values())
			if (phase.name()
				.equals(name))
				this.phase = phase;
		countDown = compound.getIntOr("CountDown", 0);
		covered = compound.getBooleanOr("Cover", false);
		super.read(compound);
	}

	@Override
	public void invalidate() {
		super.invalidate();
		invCap = null;
	}

	/** Block replaced: Create does this in MechanicalCrafterBlock.onRemove. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide()) {
			if (covered)
				Block.popResource(level, pos, new ItemStack(CrafterRegistry.CRAFTER_SLOT_COVER));
			ejectWholeGrid();

			for (Direction direction : Iterate.directions) {
				if (direction.getAxis() == state.getValue(HORIZONTAL_FACING)
					.getAxis())
					continue;

				BlockPos otherPos = pos.relative(direction);
				ConnectedInput thisInput = CrafterHelper.getInput(level, pos);
				ConnectedInput otherInput = CrafterHelper.getInput(level, otherPos);

				if (thisInput == null || otherInput == null)
					continue;
				if (!pos.offset(thisInput.data.get(0))
					.equals(otherPos.offset(otherInput.data.get(0))))
					continue;

				ConnectedInputHandler.toggleConnection(level, pos, otherPos);
			}
		}
		super.preRemoveSideEffects(pos, state);
	}

	public int getCountDownSpeed() {
		if (getSpeed() == 0)
			return 0;
		return Mth.clamp((int) Math.abs(getSpeed()), 4, 250);
	}

	@Override
	public void tick() {
		super.tick();

		if (phase == Phase.ACCEPTING)
			return;

		if (wasPoweredBefore != level.hasNeighborSignal(worldPosition)) {
			wasPoweredBefore = level.hasNeighborSignal(worldPosition);
			if (wasPoweredBefore) {
				checkCompletedRecipe(true);
			}
		}

		if (phase == Phase.ASSEMBLING) {
			countDown -= getCountDownSpeed();
			if (countDown < 0) {
				countDown = 0;
				if (RecipeGridHandler.getTargetingCrafter(this) != null) {
					phase = Phase.EXPORTING;
					countDown = groupedItems.onlyEmptyItems() ? 0 : 1000;
					sendData();
					return;
				}

				ItemStack result = RecipeGridHandler.tryToApplyRecipe(level, groupedItems);

				if (result != null) {
					List<ItemStack> containers = new ArrayList<>();
					groupedItems.grid.values()
						.forEach(stack -> {
							ItemStackTemplate remainder = stack.getItem()
								.getCraftingRemainder();
							if (remainder != null)
								containers.add(remainder.create());
						});

					groupedItemsBeforeCraft = groupedItems;

					groupedItems = new GroupedItems(result);
					for (int i = 0; i < containers.size(); i++) {
						ItemStack stack = containers.get(i);
						GroupedItems container = new GroupedItems();
						container.grid.put(Pair.of(i, 0), stack);
						container.mergeOnto(groupedItems, Pointing.LEFT);
					}

					phase = Phase.CRAFTING;
					countDown = 2000;
					sendData();
					return;
				}
				ejectWholeGrid();
				return;
			}
		}

		if (phase == Phase.EXPORTING) {
			countDown -= getCountDownSpeed();

			if (countDown < 0) {
				countDown = 0;

				MechanicalCrafterBlockEntity targetingCrafter = RecipeGridHandler.getTargetingCrafter(this);
				if (targetingCrafter == null) {
					ejectWholeGrid();
					return;
				}

				boolean empty = groupedItems.onlyEmptyItems();
				Pointing pointing = getBlockState().getValue(MechanicalCrafterBlock.POINTING);
				groupedItems.mergeOnto(targetingCrafter.groupedItems, pointing);
				groupedItems = new GroupedItems();

				float pitch = targetingCrafter.groupedItems.grid.size() * 1 / 16f + .5f;

				if (!empty)
					crafterClick(level, worldPosition, 1, pitch);

				phase = Phase.WAITING;
				countDown = 0;
				sendData();
				targetingCrafter.continueIfAllPrecedingFinished();
				targetingCrafter.sendData();

				if (!empty && level instanceof ServerLevel serverLevel) {
					Direction facing = getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
					Vec3 vec = Vec3.atLowerCornerOf(facing.getUnitVec3i())
						.scale(.75)
						.add(VecHelper.getCenterOf(worldPosition));
					Direction targetDirection = MechanicalCrafterBlock.getTargetDirection(getBlockState());
					vec = vec.add(Vec3.atLowerCornerOf(targetDirection.getUnitVec3i())
						.scale(1));
					serverLevel.sendParticles(ParticleTypes.CRIT, vec.x, vec.y, vec.z, 1, 0, 0, 0, 0);
				}
				return;
			}
		}

		if (phase == Phase.CRAFTING) {

			if (level instanceof ServerLevel serverLevel)
				craftingParticles(serverLevel);

			int prev = countDown;
			countDown -= getCountDownSpeed();

			if (countDown < 1000 && prev >= 1000) {
				crafterClick(level, worldPosition, 1, 2);
				crafterCraft(level, worldPosition);
			}

			if (countDown < 0) {
				countDown = 0;
				tryInsert();
				return;
			}
		}

		if (phase == Phase.INSERTING) {
			if (isTargetingBelt())
				tryInsert();
			return;
		}
	}

	/** What Create's tick spawns on the client while crafting. */
	private void craftingParticles(ServerLevel serverLevel) {
		Direction facing = getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
		float progress = countDown / 2000f;
		Vec3 facingVec = Vec3.atLowerCornerOf(facing.getUnitVec3i());
		Vec3 vec = facingVec.scale(.65)
			.add(VecHelper.getCenterOf(worldPosition));
		Vec3 offset = VecHelper.offsetRandomly(Vec3.ZERO, serverLevel.getRandom(), .125f)
			.multiply(VecHelper.axisAlingedPlaneOf(facingVec))
			.normalize()
			.scale(progress * .5f)
			.add(vec);
		if (progress > .5f)
			serverLevel.sendParticles(ParticleTypes.CRIT, offset.x, offset.y, offset.z, 1, 0, 0, 0, 0);

		if (!groupedItemsBeforeCraft.grid.isEmpty() && progress < .5f) {
			if (groupedItems.grid.containsKey(Pair.of(0, 0))) {
				ItemStack stack = groupedItems.grid.get(Pair.of(0, 0));
				groupedItemsBeforeCraft = new GroupedItems();
				sendData();

				for (int i = 0; i < 10; i++) {
					Vec3 randVec = VecHelper.offsetRandomly(Vec3.ZERO, serverLevel.getRandom(), .125f)
						.multiply(VecHelper.axisAlingedPlaneOf(facingVec))
						.normalize()
						.scale(.25f);
					Vec3 offset2 = randVec.add(vec);
					randVec = randVec.scale(.35f);
					serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()), offset2.x,
						offset2.y, offset2.z, 0, randVec.x, randVec.y, randVec.z, 1);
				}
			}
		}
	}

	// Create's CRAFTER_CLICK / CRAFTER_CRAFT sound events are combinations of vanilla sounds
	static void crafterClick(Level level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.BLOCKS, .25f * volume, pitch);
		level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, .125f * volume, pitch);
	}

	static void crafterCraft(Level level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, .125f, .75f);
	}

	protected boolean isTargetingBelt() {
		DirectBeltInputBehaviour behaviour = getTargetingBelt();
		return behaviour != null && behaviour.canInsertFromSide(getTargetDirection());
	}

	protected DirectBeltInputBehaviour getTargetingBelt() {
		BlockPos targetPos = worldPosition.relative(getTargetDirection());
		return BlockEntityBehaviour.get(level, targetPos, DirectBeltInputBehaviour.TYPE);
	}

	public void tryInsert() {
		if (!inserting.hasInventory() && !isTargetingBelt()) {
			ejectWholeGrid();
			return;
		}

		boolean chagedPhase = phase != Phase.INSERTING;
		final List<Pair<Integer, Integer>> inserted = new LinkedList<>();

		DirectBeltInputBehaviour behaviour = getTargetingBelt();
		for (Entry<Pair<Integer, Integer>, ItemStack> entry : groupedItems.grid.entrySet()) {
			Pair<Integer, Integer> pair = entry.getKey();
			ItemStack stack = entry.getValue();
			BlockFace face = getTargetFace(level, worldPosition, getBlockState());

			ItemStack remainder = behaviour == null ? inserting.insert(stack.copy())
				: behaviour.handleInsertion(stack, face.getFace(), false);
			if (!remainder.isEmpty()) {
				stack.setCount(remainder.getCount());
				continue;
			}

			inserted.add(pair);
		}

		inserted.forEach(groupedItems.grid::remove);
		if (groupedItems.grid.isEmpty())
			ejectWholeGrid();
		else
			phase = Phase.INSERTING;
		if (!inserted.isEmpty() || chagedPhase)
			sendData();
	}

	public void ejectWholeGrid() {
		List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChain(this);
		if (chain == null)
			return;
		chain.forEach(MechanicalCrafterBlockEntity::eject);
	}

	public void eject() {
		BlockState blockState = getBlockState();
		boolean present = blockState.is(CrafterRegistry.MECHANICAL_CRAFTER);
		Vec3 vec = present ? Vec3.atLowerCornerOf(blockState.getValue(HORIZONTAL_FACING)
			.getUnitVec3i())
			.scale(.75f) : Vec3.ZERO;
		Vec3 ejectPos = VecHelper.getCenterOf(worldPosition)
			.add(vec);
		groupedItems.grid.forEach((pair, stack) -> dropItem(ejectPos, stack));
		if (!inventory.getItem(0)
			.isEmpty())
			dropItem(ejectPos, inventory.getItem(0));
		phase = Phase.IDLE;
		groupedItems = new GroupedItems();
		inventory.setStackInSlot(0, ItemStack.EMPTY);
		sendData();
	}

	public void dropItem(Vec3 ejectPos, ItemStack stack) {
		if (stack.isEmpty())
			return;
		ItemEntity itemEntity = new ItemEntity(level, ejectPos.x, ejectPos.y, ejectPos.z, stack);
		itemEntity.setDefaultPickUpDelay();
		level.addFreshEntity(itemEntity);
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		// the chain of connected inventories is looked up when first needed; neighbours may have loaded since
		invCap = null;
		if (phase == Phase.IDLE && craftingItemPresent())
			checkCompletedRecipe(false);
		if (phase == Phase.INSERTING)
			tryInsert();
	}

	public boolean craftingItemPresent() {
		return !inventory.getItem(0)
			.isEmpty();
	}

	public boolean craftingItemOrCoverPresent() {
		return !inventory.getItem(0)
			.isEmpty() || covered;
	}

	public void checkCompletedRecipe(boolean poweredStart) {
		if (getSpeed() == 0)
			return;
		List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChainIf(this,
			poweredStart ? MechanicalCrafterBlockEntity::craftingItemPresent
				: MechanicalCrafterBlockEntity::craftingItemOrCoverPresent,
			poweredStart);
		if (chain == null)
			return;
		chain.forEach(MechanicalCrafterBlockEntity::begin);
	}

	protected void begin() {
		phase = Phase.ACCEPTING;
		groupedItems = new GroupedItems(inventory.getItem(0));
		inventory.setStackInSlot(0, ItemStack.EMPTY);
		if (RecipeGridHandler.getPrecedingCrafters(this)
			.isEmpty()) {
			phase = Phase.ASSEMBLING;
			countDown = 1;
		}
		sendData();
	}

	protected void continueIfAllPrecedingFinished() {
		List<MechanicalCrafterBlockEntity> preceding = RecipeGridHandler.getPrecedingCrafters(this);
		if (preceding == null) {
			ejectWholeGrid();
			return;
		}

		for (MechanicalCrafterBlockEntity blockEntity : preceding)
			if (blockEntity.phase != Phase.WAITING)
				return;

		phase = Phase.ASSEMBLING;
		countDown = 1;
	}

	public void connectivityChanged() {
		sendData();
		invCap = null;
	}

	public Inventory getInventory() {
		return inventory;
	}

	public ConnectedInput getInput() {
		return input;
	}

	public Phase getPhase() {
		return phase;
	}

	public int getCountDown() {
		return countDown;
	}

	public boolean isCovered() {
		return covered;
	}

	public void setCovered(boolean covered) {
		this.covered = covered;
		setChanged();
		sendData();
	}
}

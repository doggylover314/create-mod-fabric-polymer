package dev.createpolymer.schematics.cannon;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.foundation.block.BlockHelper;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.ItemHelper.ExtractionCountMode;
import dev.createpolymer.foundation.item.handler.EmptyItemHandler;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.belt.BeltBlock;
import dev.createpolymer.kinetics.belt.BeltBlockEntity;
import dev.createpolymer.kinetics.belt.BeltBlockEntity.CasingType;
import dev.createpolymer.kinetics.belt.BeltPart;
import dev.createpolymer.kinetics.belt.BeltSlope;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.BeltRegistry;
import dev.createpolymer.registry.feature.MiscRegistry;
import dev.createpolymer.registry.feature.SchematicsRegistry;
import dev.createpolymer.schematics.SchematicPrinter;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.schematics.requirement.ItemRequirement.ItemUseType;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class SchematicannonBlockEntity extends SmartBlockEntity implements Clearable {
	public static final int NEIGHBOUR_CHECKING = 100;
	public static final int MAX_ANCHOR_DISTANCE = 256;

	// Inventory
	public SchematicannonInventory inventory;

	public boolean sendUpdate;
	// Sync
	public boolean dontUpdateChecklist;
	public int neighbourCheckCooldown;

	// Printer
	public SchematicPrinter printer;
	public ItemStack missingItem;
	public boolean positionNotLoaded;
	public boolean hasCreativeCrate;
	private int printerCooldown;
	private int skipsLeft;
	private boolean blockSkipped;

	public BlockPos previousTarget;
	public LinkedHashSet<IItemHandler> attachedInventories;
	public List<LaunchedItem> flyingBlocks;
	public MaterialChecklist checklist;

	// Gui information
	public int remainingFuel;
	public float bookPrintingProgress;
	public float schematicProgress;
	public String statusMsg;
	public State state;
	public int blocksPlaced;
	public int blocksToPlace;

	// Settings
	public int replaceMode;
	public boolean skipMissing;
	public boolean replaceBlockEntities;

	// Render
	public boolean firstRenderTick;
	public float defaultYaw;

	public SchematicannonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(30);
		attachedInventories = new LinkedHashSet<>();
		flyingBlocks = new LinkedList<>();
		inventory = new SchematicannonInventory(this);
		statusMsg = "idle";
		this.state = State.STOPPED;
		replaceMode = 2;
		checklist = new MaterialChecklist();
		printer = new SchematicPrinter();
	}

	public static void registerCapabilities() {
		// the schematicannon takes items from its neighbours; nothing reaches into it
	}

	public void findInventories() {
		hasCreativeCrate = false;
		attachedInventories.clear();
		for (Direction facing : Iterate.directions) {

			if (!level.isLoaded(worldPosition.relative(facing)))
				continue;

			if (level.getBlockState(worldPosition.relative(facing))
				.is(MiscRegistry.CREATIVE_CRATE))
				hasCreativeCrate = true;

			BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(facing));
			if (blockEntity != null) {
				IItemHandler capability =
					ItemHandlerLookup.get(level, blockEntity.getBlockPos(), facing.getOpposite());
				if (capability != null) {
					attachedInventories.add(capability);
				}
			}
		}
	}

	@Override
	public void clearContent() {
		inventory.clearAll();
	}

	@Override
	protected void read(ValueInput compound) {
		inventory.deserialize(compound.childOrEmpty("Inventory"));

		// Gui information
		statusMsg = compound.getStringOr("Status", "");
		schematicProgress = compound.getFloatOr("Progress", 0);
		bookPrintingProgress = compound.getFloatOr("PaperProgress", 0);
		remainingFuel = compound.getIntOr("RemainingFuel", 0);
		String stateString = compound.getStringOr("State", "");
		state = stateString.isEmpty() ? State.STOPPED : State.valueOf(stateString);
		blocksPlaced = compound.getIntOr("AmountPlaced", 0);
		blocksToPlace = compound.getIntOr("AmountToPlace", 0);

		missingItem = compound.read("MissingItem", ItemStack.OPTIONAL_CODEC)
			.filter(stack -> !stack.isEmpty())
			.orElse(null);

		// Settings
		SchematicannonOptions options = compound.read("Options", SchematicannonOptions.CODEC)
			.orElse(new SchematicannonOptions(2, false, false));
		replaceMode = options.replaceMode;
		skipMissing = options.skipMissing;
		replaceBlockEntities = options.replaceBlockEntities;

		// Printer & Flying Blocks
		compound.child("Printer")
			.ifPresent(printer::fromTag);
		flyingBlocks.clear();
		compound.childrenListOrEmpty("FlyingBlocks")
			.forEach(c -> flyingBlocks.add(LaunchedItem.fromValues(c)));

		defaultYaw = compound.getFloatOr("DefaultYaw", 0);

		super.read(compound);
	}

	@Override
	public void write(ValueOutput compound) {
		inventory.serialize(compound.child("Inventory"));
		if (state == State.RUNNING) {
			compound.putBoolean("Running", true);
		}

		// Gui information
		compound.putFloat("Progress", schematicProgress);
		compound.putFloat("PaperProgress", bookPrintingProgress);
		compound.putInt("RemainingFuel", remainingFuel);
		compound.putString("Status", statusMsg);
		compound.putString("State", state.name());
		compound.putInt("AmountPlaced", blocksPlaced);
		compound.putInt("AmountToPlace", blocksToPlace);

		if (missingItem != null)
			compound.store("MissingItem", ItemStack.OPTIONAL_CODEC, missingItem);

		// Settings
		compound.store("Options", SchematicannonOptions.CODEC,
			new SchematicannonOptions(replaceMode, skipMissing, replaceBlockEntities));

		// Printer & Flying Blocks
		printer.write(compound.child("Printer"));

		ValueOutput.ValueOutputList tagFlyingBlocks = compound.childrenList("FlyingBlocks");
		for (LaunchedItem b : flyingBlocks)
			b.write(tagFlyingBlocks.addChild());

		compound.putFloat("DefaultYaw", defaultYaw);

		super.write(compound);
	}

	@Override
	public void tick() {
		super.tick();

		if (state != State.STOPPED && neighbourCheckCooldown-- <= 0) {
			neighbourCheckCooldown = NEIGHBOUR_CHECKING;
			findInventories();
		}

		firstRenderTick = true;
		previousTarget = printer.getCurrentTarget();
		tickFlyingBlocks();

		if (level.isClientSide())
			return;

		// Update Fuel and Paper
		tickPaperPrinter();
		refillFuelIfPossible();

		// Update Printer
		skipsLeft = 1000;
		blockSkipped = true;

		while (blockSkipped && skipsLeft-- > 0)
			tickPrinter();

		schematicProgress = 0;
		if (blocksToPlace > 0)
			schematicProgress = (float) blocksPlaced / blocksToPlace;

		// Update the model
		if (sendUpdate) {
			sendUpdate = false;
			sendData();
		}
	}

	protected void tickPrinter() {
		ItemStack blueprint = inventory.getStackInSlot(0);
		blockSkipped = false;

		if (blueprint.isEmpty() && !statusMsg.equals("idle") && inventory.getStackInSlot(1)
			.isEmpty()) {
			state = State.STOPPED;
			statusMsg = "idle";
			sendUpdate = true;
			return;
		}

		// Skip if not Active
		if (state == State.STOPPED) {
			if (printer.isLoaded())
				resetPrinter();
			return;
		}

		if (state == State.PAUSED && !positionNotLoaded && missingItem == null && remainingFuel > 0)
			return;

		// Initialize Printer
		if (!printer.isLoaded()) {
			initializePrinter(blueprint);
			return;
		}

		// Cooldown from last shot
		if (printerCooldown > 0) {
			printerCooldown--;
			return;
		}

		// Check Fuel
		if (remainingFuel <= 0 && !hasCreativeCrate) {
			refillFuelIfPossible();
			if (remainingFuel <= 0) {
				state = State.PAUSED;
				statusMsg = "noGunpowder";
				sendUpdate = true;
				return;
			}
		}

		if (hasCreativeCrate) {
			remainingFuel = 0;
			if (missingItem != null) {
				missingItem = null;
				state = State.RUNNING;
			}
		}

		// Update Target
		if (missingItem == null && !positionNotLoaded) {
			if (!printer.advanceCurrentPos()) {
				finishedPrinting();
				return;
			}
			sendUpdate = true;
		}

		// Check block
		if (!getLevel().isLoaded(printer.getCurrentTarget())) {
			positionNotLoaded = true;
			statusMsg = "targetNotLoaded";
			state = State.PAUSED;
			return;
		} else {
			if (positionNotLoaded) {
				positionNotLoaded = false;
				state = State.RUNNING;
			}
		}

		// Get item requirement
		ItemRequirement requirement = printer.getCurrentRequirement();
		if (requirement.isInvalid() || !printer.shouldPlaceCurrent(level, this::shouldPlace)) {
			sendUpdate = !statusMsg.equals("searching");
			statusMsg = "searching";
			blockSkipped = true;
			return;
		}

		// Find item
		List<ItemRequirement.StackRequirement> requiredItems = requirement.getRequiredItems();
		if (!requirement.isEmpty()) {
			for (ItemRequirement.StackRequirement required : requiredItems) {
				if (!grabItemsFromAttachedInventories(required, true)) {
					if (skipMissing) {
						statusMsg = "skipping";
						blockSkipped = true;
						if (missingItem != null) {
							missingItem = null;
							state = State.RUNNING;
						}
						return;
					}

					missingItem = required.stack;
					state = State.PAUSED;
					statusMsg = "missingBlock";
					return;
				}
			}

			for (ItemRequirement.StackRequirement required : requiredItems)
				grabItemsFromAttachedInventories(required, false);
		}

		// Success
		state = State.RUNNING;
		ItemStack icon = requirement.isEmpty() || requiredItems.isEmpty() ? ItemStack.EMPTY : requiredItems.get(0).stack;
		printer.handleCurrentTarget((target, blockState, blockEntity) -> {
			// Launch block
			statusMsg = blockState.getBlock() != Blocks.AIR ? "placing" : "clearing";
			launchBlockOrBelt(target, icon, blockState, blockEntity);
		}, (target, entity) -> {
			// Launch entity
			statusMsg = "placing";
			launchEntity(target, icon, entity);
		});

		printerCooldown = SchematicsRegistry.schematicannonDelay;
		remainingFuel -= 1;
		sendUpdate = true;
		missingItem = null;
	}

	public int getShotsPerGunpowder() {
		return hasCreativeCrate ? 0 : SchematicsRegistry.schematicannonShotsPerGunpowder;
	}

	protected void initializePrinter(ItemStack blueprint) {
		if (!blueprint.has(SchematicsRegistry.SCHEMATIC_ANCHOR)) {
			state = State.STOPPED;
			statusMsg = "schematicInvalid";
			sendUpdate = true;
			return;
		}

		if (!blueprint.getOrDefault(SchematicsRegistry.SCHEMATIC_DEPLOYED, false)) {
			state = State.STOPPED;
			statusMsg = "schematicNotPlaced";
			sendUpdate = true;
			return;
		}

		// Load blocks into reader
		printer.loadSchematic(blueprint, level, true);

		if (printer.isErrored()) {
			state = State.STOPPED;
			statusMsg = "schematicErrored";
			inventory.setStackInSlot(0, ItemStack.EMPTY);
			inventory.setStackInSlot(1, new ItemStack(SchematicsRegistry.EMPTY_SCHEMATIC));
			printer.resetSchematic();
			sendUpdate = true;
			return;
		}

		if (printer.isWorldEmpty()) {
			state = State.STOPPED;
			statusMsg = "schematicExpired";
			inventory.setStackInSlot(0, ItemStack.EMPTY);
			inventory.setStackInSlot(1, new ItemStack(SchematicsRegistry.EMPTY_SCHEMATIC));
			printer.resetSchematic();
			sendUpdate = true;
			return;
		}

		if (!printer.getAnchor()
			.closerThan(getBlockPos(), MAX_ANCHOR_DISTANCE)) {
			state = State.STOPPED;
			statusMsg = "targetOutsideRange";
			printer.resetSchematic();
			sendUpdate = true;
			return;
		}

		state = State.PAUSED;
		statusMsg = "ready";
		updateChecklist();
		sendUpdate = true;
		blocksToPlace += blocksPlaced;
	}

	protected ItemStack getItemForBlock(BlockState blockState) {
		Item item = BlockItem.BY_BLOCK.getOrDefault(blockState.getBlock(), Items.AIR);
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	protected boolean grabItemsFromAttachedInventories(ItemRequirement.StackRequirement required, boolean simulate) {
		if (hasCreativeCrate)
			return true;

		attachedInventories.removeIf(Objects::isNull);

		ItemUseType usage = required.usage;

		// Find and apply damage
		if (usage == ItemUseType.DAMAGE) {
			for (IItemHandler cap : attachedInventories) {
				if (cap == null)
					cap = EmptyItemHandler.INSTANCE;
				for (int slot = 0; slot < cap.getSlots(); slot++) {
					ItemStack extractItem = cap.extractItem(slot, 1, true);
					if (!required.matches(extractItem))
						continue;
					if (!extractItem.isDamageableItem())
						continue;

					if (!simulate) {
						ItemStack stack = cap.extractItem(slot, 1, false);
						stack.setDamageValue(stack.getDamageValue() + 1);
						if (stack.getDamageValue() <= stack.getMaxDamage()) {
							if (cap.getStackInSlot(slot)
								.isEmpty())
								cap.insertItem(slot, stack, false);
							else
								ItemHandlerHelper.insertItem(cap, stack, false);
						}
					}

					return true;
				}
			}

			return false;
		}

		// Find and remove
		boolean success = false;
		int amountFound = 0;
		for (IItemHandler cap : attachedInventories) {
			if (cap == null)
				cap = EmptyItemHandler.INSTANCE;
			amountFound += ItemHelper
				.extract(cap, required::matches, ExtractionCountMode.UPTO,
					required.stack.getCount(), true)
				.getCount();

			if (amountFound < required.stack.getCount())
				continue;

			success = true;
			break;
		}

		if (!simulate && success) {
			amountFound = 0;
			for (IItemHandler cap : attachedInventories) {
				if (cap == null)
					cap = EmptyItemHandler.INSTANCE;
				amountFound += ItemHelper
					.extract(cap, required::matches, ExtractionCountMode.UPTO,
						required.stack.getCount(), false)
					.getCount();
				if (amountFound < required.stack.getCount())
					continue;
				break;
			}
		}

		return success;
	}

	public void finishedPrinting() {
		if (replaceMode == ConfigureSchematicannon.Option.REPLACE_EMPTY.ordinal())
			printer.sendBlockUpdates(level);
		inventory.setStackInSlot(0, ItemStack.EMPTY);
		inventory.setStackInSlot(1, new ItemStack(SchematicsRegistry.EMPTY_SCHEMATIC, inventory.getStackInSlot(1)
			.getCount() + 1));
		state = State.STOPPED;
		statusMsg = "finished";
		resetPrinter();
		SchematicsRegistry.playFinishSound(level, worldPosition);
		sendUpdate = true;
	}

	protected void resetPrinter() {
		printer.resetSchematic();
		missingItem = null;
		sendUpdate = true;
		schematicProgress = 0;
		blocksPlaced = 0;
		blocksToPlace = 0;
	}

	protected boolean shouldPlace(BlockPos pos, BlockState state, BlockEntity be, BlockState toReplace,
		BlockState toReplaceOther, boolean isNormalCube) {
		if (pos.closerThan(getBlockPos(), 2f))
			return false;
		if (!replaceBlockEntities
			&& (toReplace.hasBlockEntity() || (toReplaceOther != null && toReplaceOther.hasBlockEntity())))
			return false;

		if (shouldIgnoreBlockState(state, be))
			return false;

		boolean placingAir = state.isAir();

		if (replaceMode == 3)
			return true;
		if (replaceMode == 2 && !placingAir)
			return true;
		if (replaceMode == 1 && (isNormalCube || (!toReplace.isRedstoneConductor(level, pos)
			&& (toReplaceOther == null || !toReplaceOther.isRedstoneConductor(level, pos)))) && !placingAir)
			return true;
		if (replaceMode == 0 && !toReplace.isRedstoneConductor(level, pos)
			&& (toReplaceOther == null || !toReplaceOther.isRedstoneConductor(level, pos)) && !placingAir)
			return true;

		return false;
	}

	protected boolean shouldIgnoreBlockState(BlockState state, BlockEntity be) {
		// Block doesn't have a mapping (Water, lava, etc)
		if (state.getBlock() == Blocks.STRUCTURE_VOID)
			return true;

		ItemRequirement requirement = ItemRequirement.of(state, be);
		if (requirement.isEmpty())
			return false;
		if (requirement.isInvalid())
			return false;

		// Block doesn't need to be placed twice (Doors, beds, double plants)
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
			&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
			return true;
		if (state.hasProperty(BlockStateProperties.BED_PART)
			&& state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD)
			return true;
		if (state.getBlock() instanceof PistonHeadBlock)
			return true;
		if (state.is(BeltRegistry.BELT))
			return state.getValue(BeltBlock.PART) == BeltPart.MIDDLE;

		return false;
	}

	protected void tickFlyingBlocks() {
		List<LaunchedItem> toRemove = new LinkedList<>();
		for (LaunchedItem b : flyingBlocks)
			if (b.update(level))
				toRemove.add(b);
		flyingBlocks.removeAll(toRemove);
	}

	protected void refillFuelIfPossible() {
		if (hasCreativeCrate)
			return;
		if (remainingFuel > getShotsPerGunpowder()) {
			remainingFuel = getShotsPerGunpowder();
			sendUpdate = true;
			return;
		}

		if (remainingFuel > 0)
			return;

		if (!inventory.getStackInSlot(4)
			.isEmpty())
			inventory.getStackInSlot(4)
				.shrink(1);
		else {
			boolean externalGunpowderFound = false;
			for (IItemHandler cap : attachedInventories) {
				IItemHandler itemHandler = cap;

				if (itemHandler == null)
					itemHandler = EmptyItemHandler.INSTANCE;

				if (ItemHelper.extract(itemHandler, stack -> inventory.isItemValid(4, stack), 1, false)
					.isEmpty())
					continue;
				externalGunpowderFound = true;
				break;
			}
			if (!externalGunpowderFound)
				return;
		}

		remainingFuel += getShotsPerGunpowder();
		if (statusMsg.equals("noGunpowder")) {
			if (blocksPlaced > 0)
				state = State.RUNNING;
			statusMsg = "ready";
		}
		sendUpdate = true;
	}

	protected void tickPaperPrinter() {
		int BookInput = 2;
		int BookOutput = 3;

		ItemStack blueprint = inventory.getStackInSlot(0);
		ItemStack paper = inventory.extractItem(BookInput, 1, true);
		boolean outputFull = inventory.getStackInSlot(BookOutput)
			.getCount() == inventory.getSlotLimit(BookOutput);

		if (printer.isErrored())
			return;

		if (!printer.isLoaded()) {
			if (!blueprint.isEmpty())
				initializePrinter(blueprint);
			return;
		}

		if (paper.isEmpty() || outputFull) {
			if (bookPrintingProgress != 0)
				sendUpdate = true;
			bookPrintingProgress = 0;
			dontUpdateChecklist = false;
			return;
		}

		if (bookPrintingProgress >= 1) {
			bookPrintingProgress = 0;

			if (!dontUpdateChecklist)
				updateChecklist();

			dontUpdateChecklist = true;
			ItemStack extractItem = inventory.extractItem(BookInput, 1, false);
			ItemStack stack = checklist.createWritten(extractItem);
			stack.setCount(inventory.getStackInSlot(BookOutput)
				.getCount() + 1);
			inventory.setStackInSlot(BookOutput, stack);
			sendUpdate = true;
			return;
		}

		bookPrintingProgress += 0.05f;
		sendUpdate = true;
	}

	public static BlockState stripBeltIfNotLast(BlockState blockState) {
		BeltPart part = blockState.getValue(BeltBlock.PART);
		if (part == BeltPart.MIDDLE)
			return Blocks.AIR.defaultBlockState();

		// is highest belt?
		boolean isLastSegment = false;
		Direction facing = blockState.getValue(BeltBlock.HORIZONTAL_FACING);
		BeltSlope slope = blockState.getValue(BeltBlock.SLOPE);
		boolean positive = facing.getAxisDirection() == AxisDirection.POSITIVE;
		boolean start = part == BeltPart.START;
		boolean end = part == BeltPart.END;

		switch (slope) {
		case DOWNWARD:
			isLastSegment = start;
			break;
		case UPWARD:
			isLastSegment = end;
			break;
		default:
			isLastSegment = positive && end || !positive && start;
		}
		if (isLastSegment)
			return blockState;

		return ModBlocks.SHAFT.defaultBlockState()
			.setValue(BlockStateProperties.AXIS, slope == BeltSlope.SIDEWAYS ? Axis.Y
				: facing.getClockWise()
					.getAxis());
	}

	protected void launchBlockOrBelt(BlockPos target, ItemStack icon, BlockState blockState, BlockEntity blockEntity) {
		if (blockState.is(BeltRegistry.BELT)) {
			blockState = stripBeltIfNotLast(blockState);
			if (blockEntity instanceof BeltBlockEntity bbe && blockState.is(BeltRegistry.BELT)) {
				CasingType[] casings = new CasingType[bbe.beltLength];
				Arrays.fill(casings, CasingType.NONE);
				BlockPos currentPos = target;
				for (int i = 0; i < bbe.beltLength; i++) {
					BlockState currentState = bbe.getLevel()
						.getBlockState(currentPos);
					if (!(currentState.getBlock() instanceof BeltBlock))
						break;
					if (!(bbe.getLevel()
						.getBlockEntity(currentPos) instanceof BeltBlockEntity beltAtSegment))
						break;
					casings[i] = beltAtSegment.casing;
					currentPos = BeltBlock.nextSegmentPosition(currentState, currentPos,
						blockState.getValue(BeltBlock.PART) != BeltPart.END);
				}
				launchBelt(target, blockState, bbe.beltLength, casings);
			} else if (blockState != Blocks.AIR.defaultBlockState())
				launchBlock(target, icon, blockState, null);
			return;
		}

		CompoundTag data = BlockHelper.prepareBlockEntityData(level, blockState, blockEntity);
		launchBlock(target, icon, blockState, data);
	}

	protected void launchBelt(BlockPos target, BlockState state, int length, CasingType[] casings) {
		blocksPlaced++;
		ItemStack connector = new ItemStack(BeltRegistry.BELT_CONNECTOR);
		flyingBlocks.add(new LaunchedItem.ForBelt(this.getBlockPos(), target, connector, state, casings));
		playFiringSound();
	}

	protected void launchBlock(BlockPos target, ItemStack stack, BlockState state, @Nullable CompoundTag data) {
		if (!state.isAir())
			blocksPlaced++;
		flyingBlocks.add(new LaunchedItem.ForBlockState(this.getBlockPos(), target, stack, state, data));
		playFiringSound();
	}

	protected void launchEntity(BlockPos target, ItemStack stack, Entity entity) {
		blocksPlaced++;
		flyingBlocks.add(new LaunchedItem.ForEntity(this.getBlockPos(), target, stack, entity));
		playFiringSound();
	}

	public void playFiringSound() {
		SchematicsRegistry.playLaunchSound(level, worldPosition);
		// Create's SchematicannonRenderer puffs clouds when a shot leaves the cannon
		if (level instanceof ServerLevel serverLevel && !flyingBlocks.isEmpty()) {
			LaunchedItem launched = flyingBlocks.get(flyingBlocks.size() - 1);
			Vec3 start = Vec3.atCenterOf(worldPosition.above());
			Vec3 offset = launchOffset(start, launched.target);
			Vec3 at = start.add(offset);
			serverLevel.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 10, .3, .3, .3, .01);
		}
	}

	/** Where a shot leaves the cannon, relative to the block above it (Create's SchematicannonRenderer) */
	public static Vec3 launchOffset(Vec3 start, BlockPos targetPos) {
		Vec3 target = Vec3.atCenterOf(targetPos);
		Vec3 distance = target.subtract(start);
		double yDifference = target.y - start.y;
		double throwHeight = Math.sqrt(distance.lengthSqr()) * .6f + yDifference;
		return distance.add(0, throwHeight, 0)
			.normalize()
			.scale(2);
	}

	public void openGui(ServerPlayer player) {
		new SchematicannonGui(player, this).open();
	}

	public Component getDisplayName() {
		return Component.translatable("create.gui.schematicannon.title");
	}

	public void updateChecklist() {
		checklist.required.clear();
		checklist.damageRequired.clear();
		checklist.blocksNotLoaded = false;

		if (printer.isLoaded() && !printer.isErrored()) {
			blocksToPlace = blocksPlaced;
			blocksToPlace += printer.markAllBlockRequirements(checklist, level, this::shouldPlace);
			printer.markAllEntityRequirements(checklist);
		}

		checklist.gathered.clear();
		findInventories();
		for (IItemHandler cap : attachedInventories) {
			if (cap == null)
				continue;
			for (int slot = 0; slot < cap.getSlots(); slot++) {
				ItemStack stackInSlot = cap.getStackInSlot(slot);
				if (cap.extractItem(slot, 1, true)
					.isEmpty())
					continue;
				checklist.collect(stackInSlot);
			}
		}
		sendUpdate = true;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		findInventories();
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter componentInput) {
		SchematicannonOptions options = componentInput.getOrDefault(SchematicsRegistry.SCHEMATICANNON_OPTIONS,
			new SchematicannonOptions(2, true, false));
		replaceMode = options.replaceMode;
		skipMissing = options.skipMissing;
		replaceBlockEntities = options.replaceBlockEntities;
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		components.set(SchematicsRegistry.SCHEMATICANNON_OPTIONS,
			new SchematicannonOptions(replaceMode, skipMissing, replaceBlockEntities));
	}

	public enum State {
		STOPPED, PAUSED, RUNNING;
	}

	public record SchematicannonOptions(int replaceMode, boolean skipMissing, boolean replaceBlockEntities) {
		public static final Codec<SchematicannonOptions> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("replace_mode").forGetter(SchematicannonOptions::replaceMode),
				Codec.BOOL.fieldOf("skip_missing").forGetter(SchematicannonOptions::skipMissing),
				Codec.BOOL.fieldOf("replace_block_entities").forGetter(SchematicannonOptions::replaceBlockEntities)
		).apply(i, SchematicannonOptions::new));
	}
}

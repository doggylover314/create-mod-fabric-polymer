package dev.createpolymer.kinetics.mechanicalArm;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.mechanicalArm.ArmInteractionPoint.Mode;
import dev.createpolymer.registry.feature.ArmRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.NBTHelper;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's ArmBlockEntity (MIT, The Create Team).
 * <p>
 * Create runs the arm's animation (the chased angles) on the client from synced data. There is no client here, so
 * the server runs it every tick and the display-entity model in {@link ArmBlock.Model} reads the same fields.
 */
public class ArmBlockEntity extends KineticBlockEntity implements Clearable {

	// Server
	List<ArmInteractionPoint> inputs;
	List<ArmInteractionPoint> outputs;
	List<CompoundTag> interactionPointTag;

	float chasedPointProgress;
	int chasedPointIndex;
	ItemStack heldItem;
	Phase phase;
	boolean goggles;

	// Animation (Create's client side)
	ArmAngleTarget previousTarget;
	LerpedFloat lowerArmAngle;
	LerpedFloat upperArmAngle;
	LerpedFloat baseAngle;
	LerpedFloat headAngle;
	float previousBaseAngle;
	boolean updateInteractionPoints;
	private Phase observedPhase;
	private int observedIndex;

	//
	protected ScrollOptionBehaviour<SelectionMode> selectionMode;
	protected int lastInputIndex = -1;
	protected int lastOutputIndex = -1;
	protected boolean redstoneLocked;

	public enum Phase {
		SEARCH_INPUTS, MOVE_TO_INPUT, SEARCH_OUTPUTS, MOVE_TO_OUTPUT, DANCING
	}

	public ArmBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		inputs = new ArrayList<>();
		outputs = new ArrayList<>();
		interactionPointTag = null;
		heldItem = ItemStack.EMPTY;
		phase = Phase.SEARCH_INPUTS;
		previousTarget = ArmAngleTarget.NO_TARGET;
		baseAngle = LerpedFloat.angular();
		baseAngle.startWithValue(previousTarget.baseAngle);
		lowerArmAngle = LerpedFloat.angular();
		lowerArmAngle.startWithValue(previousTarget.lowerArmAngle);
		upperArmAngle = LerpedFloat.angular();
		upperArmAngle.startWithValue(previousTarget.upperArmAngle);
		headAngle = LerpedFloat.angular();
		headAngle.startWithValue(previousTarget.headAngle);
		previousBaseAngle = previousTarget.baseAngle;
		updateInteractionPoints = true;
		redstoneLocked = false;
		goggles = false;
		observedPhase = phase;
		observedIndex = chasedPointIndex;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);

		selectionMode = new ScrollOptionBehaviour<>(SelectionMode.class,
			Component.translatable("create.logistics.when_multiple_outputs_available"), this, new SelectionModeValueBox());
		behaviours.add(selectionMode);
	}

	@Override
	public void tick() {
		super.tick();
		initInteractionPoints();
		boolean targetReached = tickMovementProgress();

		if (chasedPointProgress < 1) {
			if (phase == Phase.MOVE_TO_INPUT) {
				ArmInteractionPoint point = getTargetedInteractionPoint();
				if (point != null)
					point.keepAlive();
			}
			return;
		}

		if (phase == Phase.MOVE_TO_INPUT)
			collectItem();
		else if (phase == Phase.MOVE_TO_OUTPUT)
			depositItem();
		else if (phase == Phase.SEARCH_INPUTS || phase == Phase.DANCING)
			searchForItem();

		if (targetReached)
			lazyTick();
	}

	@Override
	public void lazyTick() {
		super.lazyTick();

		if (chasedPointProgress < .5f)
			return;
		if (phase == Phase.SEARCH_INPUTS || phase == Phase.DANCING)
			checkForMusic();
		if (phase == Phase.SEARCH_OUTPUTS)
			searchForDestination();
	}

	private void checkForMusic() {
		boolean hasMusic = checkForMusicAmong(inputs) || checkForMusicAmong(outputs);
		if (hasMusic != (phase == Phase.DANCING)) {
			phase = hasMusic ? Phase.DANCING : Phase.SEARCH_INPUTS;
			setChanged();
			sendData();
		}
	}

	private boolean checkForMusicAmong(List<ArmInteractionPoint> list) {
		for (ArmInteractionPoint armInteractionPoint : list) {
			if (!(armInteractionPoint instanceof AllArmInteractionPointTypes.JukeboxPoint))
				continue;
			BlockState state = level.getBlockState(armInteractionPoint.getPos());
			if (state.getOptionalValue(JukeboxBlock.HAS_RECORD)
				.orElse(false))
				return true;
		}
		return false;
	}

	private boolean tickMovementProgress() {
		boolean targetReachedPreviously = chasedPointProgress >= 1;

		// Create does this when the synced phase or target changes on the client
		if (observedIndex != chasedPointIndex || observedPhase != phase) {
			onTargetChanged(observedPhase, observedIndex);
			observedPhase = phase;
			observedIndex = chasedPointIndex;
		}

		chasedPointProgress += Math.min(256, Math.abs(getSpeed())) / 1024f;
		if (chasedPointProgress > 1)
			chasedPointProgress = 1;
		boolean targetReached = !targetReachedPreviously && chasedPointProgress >= 1;

		ArmInteractionPoint targetedInteractionPoint = getTargetedInteractionPoint();
		ArmAngleTarget previousTarget = this.previousTarget;
		ArmAngleTarget target = targetedInteractionPoint == null ? ArmAngleTarget.NO_TARGET
			: targetedInteractionPoint.getTargetAngles(worldPosition, isOnCeiling());

		baseAngle.setValue(AngleHelper.angleLerp(chasedPointProgress, previousBaseAngle,
			target == ArmAngleTarget.NO_TARGET ? previousBaseAngle : target.baseAngle));

		// Arm's angles first backup to resting position and then continue
		if (chasedPointProgress < .5f)
			target = ArmAngleTarget.NO_TARGET;
		else
			previousTarget = ArmAngleTarget.NO_TARGET;
		float progress = chasedPointProgress == 1 ? 1 : (chasedPointProgress % .5f) * 2;

		lowerArmAngle.setValue(Mth.lerp(progress, previousTarget.lowerArmAngle, target.lowerArmAngle));
		upperArmAngle.setValue(Mth.lerp(progress, previousTarget.upperArmAngle, target.upperArmAngle));
		headAngle.setValue(AngleHelper.angleLerp(progress, previousTarget.headAngle % 360, target.headAngle % 360));

		return targetReached;
	}

	/** The part of Create's ArmBlockEntity.read that follows the arm's target on the client. */
	private void onTargetChanged(Phase previousPhase, int previousIndex) {
		boolean ceiling = isOnCeiling();
		ArmInteractionPoint previousPoint = null;
		if (previousPhase == Phase.MOVE_TO_INPUT && previousIndex >= 0 && previousIndex < inputs.size())
			previousPoint = inputs.get(previousIndex);
		if (previousPhase == Phase.MOVE_TO_OUTPUT && previousIndex >= 0 && previousIndex < outputs.size())
			previousPoint = outputs.get(previousIndex);
		previousTarget = previousPoint == null ? ArmAngleTarget.NO_TARGET
			: previousPoint.getTargetAngles(worldPosition, ceiling);
		if (previousPoint != null)
			previousBaseAngle = previousTarget.baseAngle;

		ArmInteractionPoint targetedPoint = getTargetedInteractionPoint();
		if (targetedPoint != null)
			targetedPoint.updateCachedState();
	}

	protected boolean isOnCeiling() {
		BlockState state = getBlockState();
		return hasLevel() && state.getOptionalValue(ArmBlock.CEILING)
			.orElse(false);
	}

	@Override
	public void destroy() {
		super.destroy();
		if (!heldItem.isEmpty())
			Block.popResource(level, worldPosition, heldItem);
	}

	@Nullable
	private ArmInteractionPoint getTargetedInteractionPoint() {
		if (chasedPointIndex == -1)
			return null;
		if (phase == Phase.MOVE_TO_INPUT && chasedPointIndex < inputs.size())
			return inputs.get(chasedPointIndex);
		if (phase == Phase.MOVE_TO_OUTPUT && chasedPointIndex < outputs.size())
			return outputs.get(chasedPointIndex);
		return null;
	}

	protected void searchForItem() {
		if (redstoneLocked)
			return;

		boolean foundInput = false;
		// for round robin, we start looking after the last used index, for default we
		// start at 0;
		int startIndex = selectionMode.get() == SelectionMode.PREFER_FIRST ? 0 : lastInputIndex + 1;

		// if we enforce round robin, only look at the next input in the list,
		// otherwise, look at all inputs
		int scanRange = selectionMode.get() == SelectionMode.FORCED_ROUND_ROBIN ? lastInputIndex + 2 : inputs.size();
		if (scanRange > inputs.size())
			scanRange = inputs.size();

		InteractionPoints:
		for (int i = startIndex; i < scanRange; i++) {
			ArmInteractionPoint armInteractionPoint = inputs.get(i);
			if (!armInteractionPoint.isValid())
				continue;
			for (int j = 0; j < armInteractionPoint.getSlotCount(this); j++) {
				if (getDistributableAmount(armInteractionPoint, j) == 0)
					continue;

				selectIndex(true, i);
				foundInput = true;
				break InteractionPoints;
			}
		}
		if (!foundInput && selectionMode.get() == SelectionMode.ROUND_ROBIN) {
			// if we didn't find an input, but don't want to enforce round robin, reset the
			// last index
			lastInputIndex = -1;
		}
		if (lastInputIndex == inputs.size() - 1) {
			// if we reached the last input in the list, reset the last index
			lastInputIndex = -1;
		}
	}

	protected void searchForDestination() {
		ItemStack held = heldItem.copy();

		boolean foundOutput = false;
		// for round robin, we start looking after the last used index, for default we
		// start at 0;
		int startIndex = selectionMode.get() == SelectionMode.PREFER_FIRST ? 0 : lastOutputIndex + 1;

		// if we enforce round robin, only look at the next index in the list,
		// otherwise, look at all
		int scanRange = selectionMode.get() == SelectionMode.FORCED_ROUND_ROBIN ? lastOutputIndex + 2 : outputs.size();
		if (scanRange > outputs.size())
			scanRange = outputs.size();

		for (int i = startIndex; i < scanRange; i++) {
			ArmInteractionPoint armInteractionPoint = outputs.get(i);
			if (!armInteractionPoint.isValid())
				continue;

			ItemStack remainder = armInteractionPoint.insert(this, held, true);
			if (ItemStack.matches(remainder, heldItem))
				continue;

			selectIndex(false, i);
			foundOutput = true;
			break;
		}

		if (!foundOutput && selectionMode.get() == SelectionMode.ROUND_ROBIN) {
			// if we didn't find an input, but don't want to enforce round robin, reset the
			// last index
			lastOutputIndex = -1;
		}
		if (lastOutputIndex == outputs.size() - 1) {
			// if we reached the last input in the list, reset the last index
			lastOutputIndex = -1;
		}
	}

	// input == true => select input, false => select output
	private void selectIndex(boolean input, int index) {
		phase = input ? Phase.MOVE_TO_INPUT : Phase.MOVE_TO_OUTPUT;
		chasedPointIndex = index;
		chasedPointProgress = 0;
		if (input)
			lastInputIndex = index;
		else
			lastOutputIndex = index;
		sendData();
		setChanged();
	}

	protected int getDistributableAmount(ArmInteractionPoint armInteractionPoint, int i) {
		ItemStack stack = armInteractionPoint.extract(this, i, true);
		ItemStack remainder = simulateInsertion(stack);
		if (ItemStack.isSameItem(stack, remainder)) {
			return stack.getCount() - remainder.getCount();
		} else {
			return stack.getCount();
		}
	}

	private ItemStack simulateInsertion(ItemStack stack) {
		for (ArmInteractionPoint armInteractionPoint : outputs) {
			if (armInteractionPoint.isValid())
				stack = armInteractionPoint.insert(this, stack, true);
			if (stack.isEmpty())
				break;
		}
		return stack;
	}

	protected void depositItem() {
		ArmInteractionPoint armInteractionPoint = getTargetedInteractionPoint();
		if (armInteractionPoint != null && armInteractionPoint.isValid()) {
			ItemStack toInsert = heldItem.copy();
			ItemStack remainder = armInteractionPoint.insert(this, toInsert, false);
			heldItem = remainder;
		}

		phase = heldItem.isEmpty() ? Phase.SEARCH_INPUTS : Phase.SEARCH_OUTPUTS;
		chasedPointProgress = 0;
		chasedPointIndex = -1;
		sendData();
		setChanged();
	}

	protected void collectItem() {
		ArmInteractionPoint armInteractionPoint = getTargetedInteractionPoint();
		if (armInteractionPoint != null && armInteractionPoint.isValid())
			for (int i = 0; i < armInteractionPoint.getSlotCount(this); i++) {
				int amountExtracted = getDistributableAmount(armInteractionPoint, i);
				if (amountExtracted == 0)
					continue;

				ItemStack prevHeld = heldItem;
				heldItem = armInteractionPoint.extract(this, i, amountExtracted, false);
				phase = Phase.SEARCH_OUTPUTS;
				chasedPointProgress = 0;
				chasedPointIndex = -1;
				sendData();
				setChanged();

				if (!ItemStack.isSameItem(heldItem, prevHeld))
					level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, .125f,
						.5f + level.getRandom().nextFloat() * .25f);
				return;
			}

		phase = Phase.SEARCH_INPUTS;
		chasedPointProgress = 0;
		chasedPointIndex = -1;
		sendData();
		setChanged();
	}

	public void redstoneUpdate() {
		if (level.isClientSide())
			return;
		boolean blockPowered = level.hasNeighborSignal(worldPosition);
		if (blockPowered == redstoneLocked)
			return;
		redstoneLocked = blockPowered;
		sendData();
		if (!redstoneLocked)
			searchForItem();
	}

	protected boolean isAreaActuallyLoaded(BlockPos center, int range) {
		return level.hasChunksAt(center.offset(-range, 0, -range), center.offset(range, 0, range));
	}

	protected void initInteractionPoints() {
		if (!updateInteractionPoints || interactionPointTag == null)
			return;
		if (!isAreaActuallyLoaded(worldPosition, getRange() + 1))
			return;
		inputs.clear();
		outputs.clear();

		for (CompoundTag tag : interactionPointTag) {
			ArmInteractionPoint point = ArmInteractionPoint.deserialize(tag, level, worldPosition);
			if (point == null)
				continue;
			if (point.getMode() == Mode.DEPOSIT)
				outputs.add(point);
			else if (point.getMode() == Mode.TAKE)
				inputs.add(point);
		}

		updateInteractionPoints = false;
		sendData();
		setChanged();
	}

	/**
	 * Replaces the arm's inputs and outputs with the given serialized points (relative to the arm), as Create's
	 * ArmPlacementPacket does.
	 */
	public void setInteractionPoints(List<CompoundTag> points) {
		interactionPointTag = new ArrayList<>(points);
		updateInteractionPoints = true;
		setChanged();
	}

	public List<ArmInteractionPoint> getInputs() {
		return inputs;
	}

	public List<ArmInteractionPoint> getOutputs() {
		return outputs;
	}

	public ItemStack getHeldItem() {
		return heldItem;
	}

	public Phase getPhase() {
		return phase;
	}

	public boolean hasGoggles() {
		return goggles;
	}

	public SelectionMode getSelectionMode() {
		return selectionMode.get();
	}

	public void writeInteractionPoints(ValueOutput compound) {
		ValueOutput.TypedOutputList<CompoundTag> pointsNBT = compound.list("InteractionPoints", CompoundTag.CODEC);
		if (updateInteractionPoints && interactionPointTag != null) {
			interactionPointTag.forEach(pointsNBT::add);
		} else {
			inputs.stream()
				.map(aip -> aip.serialize(worldPosition))
				.forEach(pointsNBT::add);
			outputs.stream()
				.map(aip -> aip.serialize(worldPosition))
				.forEach(pointsNBT::add);
		}
	}

	@Override
	public void writeSafe(ValueOutput compound) {
		super.writeSafe(compound);

		writeInteractionPoints(compound);
	}

	@Override
	protected void write(ValueOutput compound) {
		super.write(compound);

		writeInteractionPoints(compound);

		NBTHelper.writeEnum(compound, "Phase", phase);
		compound.putBoolean("Powered", redstoneLocked);
		compound.putBoolean("Goggles", goggles);
		compound.store("HeldItem", ItemStack.OPTIONAL_CODEC, heldItem);
		compound.putInt("TargetPointIndex", chasedPointIndex);
		compound.putFloat("MovementProgress", chasedPointProgress);
	}

	@Override
	protected void read(ValueInput tag) {
		super.read(tag);
		heldItem = tag.read("HeldItem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		phase = NBTHelper.readEnum(tag, "Phase", Phase.class);
		chasedPointIndex = tag.getIntOr("TargetPointIndex", 0);
		chasedPointProgress = tag.getFloatOr("MovementProgress", 0);
		List<CompoundTag> points = new ArrayList<>();
		tag.listOrEmpty("InteractionPoints", CompoundTag.CODEC)
			.forEach(points::add);
		interactionPointTag = points;
		redstoneLocked = tag.getBooleanOr("Powered", false);
		goggles = tag.getBooleanOr("Goggles", false);

		updateInteractionPoints = true;
		observedPhase = phase;
		observedIndex = chasedPointIndex;
	}

	public static int getRange() {
		return ArmRegistry.mechanicalArmRange;
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		for (ArmInteractionPoint input : inputs) {
			input.setLevel(level);
		}
		for (ArmInteractionPoint output : outputs) {
			output.setLevel(level);
		}
	}

	private class SelectionModeValueBox extends CenteredSideValueBoxTransform {

		public SelectionModeValueBox() {
			super((blockState, direction) -> !direction.getAxis()
				.isVertical());
		}

		@Override
		public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
			int yPos = state.getValue(ArmBlock.CEILING) ? 16 - 3 : 3;
			Vec3 location = VecHelper.voxelSpace(8, yPos, 15.5);
			location = VecHelper.rotateCentered(location, AngleHelper.horizontalAngle(getSide()), Direction.Axis.Y);
			return location;
		}

		@Override
		public float getScale() {
			return super.getScale();
		}

	}

	public enum SelectionMode implements INamedIconOptions {
		ROUND_ROBIN(Items.REPEATER),
		FORCED_ROUND_ROBIN(Items.COMPARATOR),
		PREFER_FIRST(Items.ARROW),

		;

		private final String translationKey;
		private final Item icon;

		SelectionMode(Item icon) {
			this.icon = icon;
			this.translationKey = "create.mechanical_arm.selection_mode." + Lang.asId(name());
		}

		@Override
		public Item getIconItem() {
			return icon;
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}
	}

	@Override
	public void clearContent() {
		heldItem = ItemStack.EMPTY;
	}
}

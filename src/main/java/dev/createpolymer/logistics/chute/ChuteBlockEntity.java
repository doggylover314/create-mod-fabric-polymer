package dev.createpolymer.logistics.chute;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.createpolymer.foundation.block.BlockHelper;
import dev.createpolymer.kinetics.fan.FanConfig;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.ItemHelper.ExtractionCountMode;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import dev.createpolymer.kinetics.fan.AirCurrent;
import dev.createpolymer.kinetics.fan.EncasedFanBlock;
import dev.createpolymer.kinetics.fan.EncasedFanBlockEntity;
import dev.createpolymer.logistics.box.PackageEntity;
import dev.createpolymer.logistics.funnel.FunnelBlock;
import dev.createpolymer.registry.feature.FanRegistry;
import dev.createpolymer.registry.feature.ChuteRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/*
 * Commented Code: Chutes create air streams and act similarly to encased fans
 * (Unfinished)
 */
public class ChuteBlockEntity extends SmartBlockEntity implements Clearable { // , IAirCurrentSource {

	// public AirCurrent airCurrent;

	float pull;
	float push;

	ItemStack item;
	LerpedFloat itemPosition;
	ChuteItemHandler itemHandler;
	boolean canPickUpItems;

	float bottomPullDistance;
	float beltBelowOffset;
	TransportedItemStackHandlerBehaviour beltBelow;
	boolean updateAirFlow;
	int airCurrentUpdateCooldown;
	int entitySearchCooldown;

	VersionedInventoryTrackerBehaviour invVersionTracker;

	public ChuteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		item = ItemStack.EMPTY;
		itemPosition = LerpedFloat.linear();
		itemHandler = new ChuteItemHandler(this);
		canPickUpItems = false;
		bottomPullDistance = 0;
		// airCurrent = new AirCurrent(this);
		updateAirFlow = true;
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(ChuteRegistry.CHUTE_BLOCK_ENTITY, (be, side) -> be.itemHandler);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new DirectBeltInputBehaviour(this).onlyInsertWhen((d) -> canDirectlyInsertCached()));
		behaviours.add(invVersionTracker = new VersionedInventoryTrackerBehaviour(this));
	}

	// Cached per-tick, useful when a lot of items are waiting on top of it
	public boolean canDirectlyInsertCached() {
		return canPickUpItems;
	}

	private boolean canDirectlyInsert() {
		BlockState blockState = getBlockState();
		BlockState blockStateAbove = level.getBlockState(worldPosition.above());
		if (!AbstractChuteBlock.isChute(blockState))
			return false;
		if (AbstractChuteBlock.getChuteFacing(blockStateAbove) == Direction.DOWN)
			return false;
		if (getItemMotion() > 0 && getInputChutes().isEmpty())
			return false;
		return AbstractChuteBlock.isOpenChute(blockState);
	}

	@Override
	public void initialize() {
		super.initialize();
		onAdded();
	}

	@Override
	public void tick() {
		super.tick();

		canPickUpItems = canDirectlyInsert();

		float itemMotion = getItemMotion();
		tickAirStreams(itemMotion);

		if (canPickUpItems)
			collectItemEntities(level.getGameTime() % 2 == 0);

		if (item.isEmpty()) {
			if (itemMotion < 0)
				handleInputFromAbove();
			if (itemMotion > 0)
				handleInputFromBelow();
			return;
		}

		float nextOffset = itemPosition.getValue() + itemMotion;

		if (itemMotion < 0) {
			if (nextOffset < .5f) {
				if (!handleDownwardOutput(true))
					nextOffset = .5f;
				else if (nextOffset < 0) {
					handleDownwardOutput(false);
					nextOffset = itemPosition.getValue();
				}
			}
		} else if (itemMotion > 0) {
			if (nextOffset > .5f) {
				if (!handleUpwardOutput(true))
					nextOffset = .5f;
				else if (nextOffset > 1) {
					handleUpwardOutput(false);
					nextOffset = itemPosition.getValue();
				}
			}
		}

		itemPosition.setValue(nextOffset);
	}

	/**
	 * Create gets items resting on top from Block.updateEntityAfterFallOn, which does not exist in 26.3, so the
	 * item entities standing on this chute are looked up here.
	 */
	private void collectItemEntities(boolean items) {
		AABB top = new AABB(worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(),
			worldPosition.getX() + 1, worldPosition.getY() + 1.5, worldPosition.getZ() + 1);
		for (Entity itemEntity : level.getEntitiesOfClass(Entity.class, top,
			e -> e instanceof PackageEntity || items && e instanceof ItemEntity)) {
			if (!itemEntity.onGround())
				continue;
			BlockPos below = BlockPos.containing(itemEntity.position()
				.add(0, 0.5f, 0))
				.below();
			if (below.equals(worldPosition))
				AbstractChuteBlock.updateEntityAfterFallOn(level, itemEntity);
		}
	}

	private void updateAirFlow(float itemSpeed) {
		updateAirFlow = false;
		// airCurrent.rebuild();
		if (itemSpeed > 0 && level != null && !level.isClientSide()) {
			float speed = pull - push;
			beltBelow = null;

			float maxPullDistance;
			if (speed >= 128)
				maxPullDistance = 3;
			else if (speed >= 64)
				maxPullDistance = 2;
			else if (speed >= 32)
				maxPullDistance = 1;
			else
				maxPullDistance = Mth.lerp(speed / 32, 0, 1);

			if (AbstractChuteBlock.isChute(level.getBlockState(worldPosition.below())))
				maxPullDistance = 0;
			float flowLimit = maxPullDistance;
			if (flowLimit > 0)
				flowLimit = AirCurrent.getFlowLimit(level, worldPosition, maxPullDistance, Direction.DOWN);

			for (int i = 1; i <= flowLimit + 1; i++) {
				TransportedItemStackHandlerBehaviour behaviour =
					BlockEntityBehaviour.get(level, worldPosition.below(i), TransportedItemStackHandlerBehaviour.TYPE);
				if (behaviour == null)
					continue;
				beltBelow = behaviour;
				beltBelowOffset = i - 1;
				break;
			}
			this.bottomPullDistance = Math.max(0, flowLimit);
		}
		sendData();
	}

	private void findEntities(float itemSpeed) {
		// if (getSpeed() != 0)
		// airCurrent.findEntities();
		if (bottomPullDistance <= 0 && !getItem().isEmpty() || itemSpeed <= 0 || level == null || level.isClientSide())
			return;
		if (!canActivate())
			return;
		Vec3 center = VecHelper.getCenterOf(worldPosition);
		AABB searchArea = new AABB(center.add(0, -bottomPullDistance - 0.5, 0), center.add(0, -0.5, 0)).inflate(.45f);
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, searchArea)) {
			if (!itemEntity.isAlive())
				continue;
			ItemStack entityItem = itemEntity.getItem();
			if (!canAcceptItem(entityItem))
				continue;
			setItem(entityItem.copy(), (float) (itemEntity.getBoundingBox()
				.getCenter().y - worldPosition.getY()));
			itemEntity.discard();
			break;
		}
	}

	private void extractFromBelt(float itemSpeed) {
		if (itemSpeed <= 0 || level == null || level.isClientSide())
			return;
		if (getItem().isEmpty() && beltBelow != null) {
			beltBelow.handleCenteredProcessingOnAllItems(.5f, ts -> {
				if (canAcceptItem(ts.stack)) {
					setItem(ts.stack.copy(), -beltBelowOffset);
					return TransportedResult.removeItem();
				}
				return TransportedResult.doNothing();
			});
		}
	}

	private void tickAirStreams(float itemSpeed) {
		if (!level.isClientSide() && airCurrentUpdateCooldown-- <= 0) {
			airCurrentUpdateCooldown = FanConfig.fanBlockCheckRate;
			updateAirFlow = true;
		}

		if (updateAirFlow) {
			updateAirFlow(itemSpeed);
		}

		if (entitySearchCooldown-- <= 0 && item.isEmpty()) {
			entitySearchCooldown = 5;
			findEntities(itemSpeed);
		}

		extractFromBelt(itemSpeed);
		// if (getSpeed() != 0)
		// airCurrent.tick();
	}

	public void blockBelowChanged() {
		updateAirFlow = true;
	}

	private void handleInputFromAbove() {
		handleInput(grabCapability(Direction.UP), 1);
	}

	private void handleInputFromBelow() {
		handleInput(grabCapability(Direction.DOWN), 0);
	}

	private void handleInput(@Nullable IItemHandler inv, float startLocation) {
		if (inv == null)
			return;
		if (!canActivate())
			return;
		if (invVersionTracker.stillWaiting(inv))
			return;
		Predicate<ItemStack> canAccept = this::canAcceptItem;
		int count = getExtractionAmount();
		ExtractionCountMode mode = getExtractionMode();
		if (mode == ExtractionCountMode.UPTO || !ItemHelper.extract(inv, canAccept, mode, count, true)
			.isEmpty()) {
			ItemStack extracted = ItemHelper.extract(inv, canAccept, mode, count, false);
			if (!extracted.isEmpty()) {
				setItem(extracted, startLocation);
				return;
			}
		}
		invVersionTracker.awaitNewVersion(inv);
	}

	private boolean handleDownwardOutput(boolean simulate) {
		BlockState blockState = getBlockState();
		ChuteBlockEntity targetChute = getTargetChute(blockState);
		Direction direction = AbstractChuteBlock.getChuteFacing(blockState);

		if (level == null || direction == null || !this.canActivate())
			return false;
		IItemHandler capBelow = grabCapability(Direction.DOWN);
		if (capBelow != null) {
			if (invVersionTracker.stillWaiting(capBelow))
				return false;
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(capBelow, item, simulate);
			ItemStack held = getItem();
			if (!simulate)
				setItem(remainder, itemPosition.getValue(0));
			if (remainder.getCount() != held.getCount())
				return true;
			invVersionTracker.awaitNewVersion(capBelow);
			if (direction == Direction.DOWN)
				return false;
		}

		if (targetChute != null) {
			boolean canInsert = targetChute.canAcceptItem(item);
			if (!simulate && canInsert) {
				targetChute.setItem(item, direction == Direction.DOWN ? 1 : .51f);
				setItem(ItemStack.EMPTY);
			}
			return canInsert;
		}

		// Diagonal chutes cannot drop items
		if (direction.getAxis()
			.isHorizontal())
			return false;

		if (FunnelBlock.getFunnelFacing(level.getBlockState(worldPosition.below())) == Direction.DOWN)
			return false;
		if (Block.canSupportRigidBlock(level, worldPosition.below()))
			return false;

		if (!simulate) {
			Vec3 dropVec = VecHelper.getCenterOf(worldPosition)
				.add(0, -12 / 16f, 0);
			ItemEntity dropped = new ItemEntity(level, dropVec.x, dropVec.y, dropVec.z, item.copy());
			dropped.setDefaultPickUpDelay();
			dropped.setDeltaMovement(0, -.25f, 0);
			level.addFreshEntity(dropped);
			setItem(ItemStack.EMPTY);
		}

		return true;
	}

	private boolean handleUpwardOutput(boolean simulate) {
		BlockState stateAbove = level.getBlockState(worldPosition.above());

		if (level == null || !this.canActivate())
			return false;

		if (AbstractChuteBlock.isOpenChute(getBlockState())) {
			IItemHandler capAbove = grabCapability(Direction.UP);
			if (capAbove != null) {
				int countBefore = item.getCount();
				if (invVersionTracker.stillWaiting(capAbove))
					return false;
				ItemStack remainder = ItemHandlerHelper.insertItemStacked(capAbove, item, simulate);
				if (!simulate)
					item = remainder;
				if (countBefore != remainder.getCount())
					return true;
				invVersionTracker.awaitNewVersion(capAbove);
				return false;
			}
		}

		ChuteBlockEntity bestOutput = null;
		List<ChuteBlockEntity> inputChutes = getInputChutes();
		for (ChuteBlockEntity targetChute : inputChutes) {
			if (!targetChute.canAcceptItem(item))
				continue;
			float itemMotion = targetChute.getItemMotion();
			if (itemMotion < 0)
				continue;
			if (bestOutput == null || bestOutput.getItemMotion() < itemMotion) {
				bestOutput = targetChute;
			}
		}

		if (bestOutput != null) {
			if (!simulate) {
				bestOutput.setItem(item, 0);
				setItem(ItemStack.EMPTY);
			}
			return true;
		}

		if (FunnelBlock.getFunnelFacing(level.getBlockState(worldPosition.above())) == Direction.UP)
			return false;
		if (BlockHelper.hasBlockSolidSide(stateAbove, level, worldPosition.above(), Direction.DOWN))
			return false;
		if (!inputChutes.isEmpty())
			return false;

		if (!simulate) {
			Vec3 dropVec = VecHelper.getCenterOf(worldPosition)
				.add(0, 8 / 16f, 0);
			ItemEntity dropped = new ItemEntity(level, dropVec.x, dropVec.y, dropVec.z, item.copy());
			dropped.setDefaultPickUpDelay();
			dropped.setDeltaMovement(0, getItemMotion() * 2, 0);
			level.addFreshEntity(dropped);
			setItem(ItemStack.EMPTY);
		}
		return true;
	}

	protected boolean canAcceptItem(ItemStack stack) {
		return item.isEmpty();
	}

	protected int getExtractionAmount() {
		return 16;
	}

	protected ExtractionCountMode getExtractionMode() {
		return ExtractionCountMode.UPTO;
	}

	protected boolean canActivate() {
		return true;
	}

	private @Nullable IItemHandler grabCapability(@NotNull Direction side) {
		BlockPos pos = this.worldPosition.relative(side);
		if (level == null)
			return null;
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof ChuteBlockEntity) {
			if (side != Direction.DOWN || !(be instanceof SmartChuteBlockEntity) || getItemMotion() > 0)
				return null;
		}
		return ItemHandlerLookup.get(level, pos, side.getOpposite());
	}

	public void setItem(ItemStack stack) {
		setItem(stack, getItemMotion() < 0 ? 1 : 0);
	}

	public void setItem(ItemStack stack, float insertionPos) {
		item = stack;
		itemPosition.startWithValue(insertionPos);
		invVersionTracker.reset();
		if (!level.isClientSide()) {
			notifyUpdate();
		}
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.store("Item", ItemStack.OPTIONAL_CODEC, item);
		compound.putFloat("ItemPosition", itemPosition.getValue());
		compound.putFloat("Pull", pull);
		compound.putFloat("Push", push);
		compound.putFloat("BottomAirFlowDistance", bottomPullDistance);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		item = compound.read("Item", ItemStack.OPTIONAL_CODEC)
			.orElse(ItemStack.EMPTY);
		itemPosition.startWithValue(compound.getFloatOr("ItemPosition", 0));
		pull = compound.getFloatOr("Pull", 0);
		push = compound.getFloatOr("Push", 0);
		bottomPullDistance = compound.getFloatOr("BottomAirFlowDistance", 0);
		super.read(compound);
	}

	public float getItemMotion() {
		// Chutes per second
		final float fanSpeedModifier = 1 / 64f;
		final float maxItemSpeed = 20f;
		final float gravity = 4f;

		float motion = (push + pull) * fanSpeedModifier;
		return (Mth.clamp(motion, -maxItemSpeed, maxItemSpeed) + (motion <= 0 ? -gravity : 0)) / 20f;
	}

	@Override
	public void clearContent() {
		item = ItemStack.EMPTY;
	}

	@Override
	public void destroy() {
		super.destroy();
		ChuteBlockEntity targetChute = getTargetChute(getBlockState());
		List<ChuteBlockEntity> inputChutes = getInputChutes();
		if (!item.isEmpty() && level != null)
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), item);
		setRemoved();
		if (targetChute != null) {
			targetChute.updatePull();
			targetChute.propagatePush();
		}
		inputChutes.forEach(c -> c.updatePush(inputChutes.size()));
	}

	public void onAdded() {
		refreshBlockState();
		updatePull();
		ChuteBlockEntity targetChute = getTargetChute(getBlockState());
		if (targetChute != null)
			targetChute.propagatePush();
		else
			updatePush(1);
	}

	public void updatePull() {
		float totalPull = calculatePull();
		if (pull == totalPull)
			return;
		pull = totalPull;
		updateAirFlow = true;
		sendData();
		ChuteBlockEntity targetChute = getTargetChute(getBlockState());
		if (targetChute != null)
			targetChute.updatePull();
	}

	public void updatePush(int branchCount) {
		float totalPush = calculatePush(branchCount);
		if (push == totalPush)
			return;
		updateAirFlow = true;
		push = totalPush;
		sendData();
		propagatePush();
	}

	public void propagatePush() {
		List<ChuteBlockEntity> inputs = getInputChutes();
		inputs.forEach(c -> c.updatePush(inputs.size()));
	}

	protected float calculatePull() {
		BlockState blockStateAbove = level.getBlockState(worldPosition.above());
		if (blockStateAbove.is(FanRegistry.ENCASED_FAN)
			&& blockStateAbove.getValue(EncasedFanBlock.FACING) == Direction.DOWN) {
			BlockEntity be = level.getBlockEntity(worldPosition.above());
			if (be instanceof EncasedFanBlockEntity fan && !be.isRemoved()) {
				return fan.getSpeed();
			}
		}

		float totalPull = 0;
		for (Direction d : Iterate.directions) {
			ChuteBlockEntity inputChute = getInputChute(d);
			if (inputChute == null)
				continue;
			totalPull += inputChute.pull;
		}
		return totalPull;
	}

	protected float calculatePush(int branchCount) {
		if (level == null)
			return 0;
		BlockState blockStateBelow = level.getBlockState(worldPosition.below());
		if (blockStateBelow.is(FanRegistry.ENCASED_FAN)
			&& blockStateBelow.getValue(EncasedFanBlock.FACING) == Direction.UP) {
			BlockEntity be = level.getBlockEntity(worldPosition.below());
			if (be instanceof EncasedFanBlockEntity fan && !be.isRemoved()) {
				return fan.getSpeed();
			}
		}

		ChuteBlockEntity targetChute = getTargetChute(getBlockState());
		if (targetChute == null)
			return 0;
		return targetChute.push / branchCount;
	}

	@Nullable
	private ChuteBlockEntity getTargetChute(BlockState state) {
		if (level == null)
			return null;
		Direction targetDirection = AbstractChuteBlock.getChuteFacing(state);
		if (targetDirection == null)
			return null;
		BlockPos chutePos = worldPosition.below();
		if (targetDirection.getAxis()
			.isHorizontal())
			chutePos = chutePos.relative(targetDirection.getOpposite());
		BlockState chuteState = level.getBlockState(chutePos);
		if (!AbstractChuteBlock.isChute(chuteState))
			return null;
		BlockEntity be = level.getBlockEntity(chutePos);
		if (be instanceof ChuteBlockEntity)
			return (ChuteBlockEntity) be;
		return null;
	}

	private List<ChuteBlockEntity> getInputChutes() {
		List<ChuteBlockEntity> inputs = new LinkedList<>();
		for (Direction d : Iterate.directions) {
			ChuteBlockEntity inputChute = getInputChute(d);
			if (inputChute == null)
				continue;
			inputs.add(inputChute);
		}
		return inputs;
	}

	@Nullable
	private ChuteBlockEntity getInputChute(Direction direction) {
		if (level == null || direction == Direction.DOWN)
			return null;
		direction = direction.getOpposite();
		BlockPos chutePos = worldPosition.above();
		if (direction.getAxis()
			.isHorizontal())
			chutePos = chutePos.relative(direction);
		BlockState chuteState = level.getBlockState(chutePos);
		Direction chuteFacing = AbstractChuteBlock.getChuteFacing(chuteState);
		if (chuteFacing != direction)
			return null;
		BlockEntity be = level.getBlockEntity(chutePos);
		if (be instanceof ChuteBlockEntity && !be.isRemoved())
			return (ChuteBlockEntity) be;
		return null;
	}

	public ItemStack getItem() {
		return item;
	}

	// @Override
	// @Nullable
	// public AirCurrent getAirCurrent() {
	// return airCurrent;
	// }
	//
	// @Nullable
	// @Override
	// public World getAirCurrentWorld() {
	// return world;
	// }
	//
	// @Override
	// public BlockPos getAirCurrentPos() {
	// return pos;
	// }
	//
	// @Override
	// public float getSpeed() {
	// if (getBlockState().get(ChuteBlock.SHAPE) == Shape.NORMAL &&
	// getBlockState().get(ChuteBlock.FACING) != Direction.DOWN)
	// return 0;
	// return pull + push;
	// }
	//
	// @Override
	// @Nullable
	// public Direction getAirFlowDirection() {
	// float speed = getSpeed();
	// if (speed == 0)
	// return null;
	// return speed > 0 ? Direction.UP : Direction.DOWN;
	// }
	//
	// @Override
	// public boolean isSourceRemoved() {
	// return removed;
	// }
	//
	// @Override
	// public Direction getAirflowOriginSide() {
	// return world != null && !(world.getBlockEntity(pos.down()) instanceof
	// IAirCurrentSource)
	// && getBlockState().get(ChuteBlock.FACING) == Direction.DOWN ? Direction.DOWN
	// : Direction.UP;
	// }
}

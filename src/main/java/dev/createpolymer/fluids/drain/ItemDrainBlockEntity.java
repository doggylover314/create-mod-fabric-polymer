package dev.createpolymer.fluids.drain;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import dev.createpolymer.fluids.transfer.GenericItemEmptying;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.createpolymer.foundation.fluid.FluidHandlerLookup;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.foundation.fluid.IFluidHandler.FluidAction;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Pair;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.BlockTags;

/**
 * Ported from Create's ItemDrainBlockEntity (MIT, The Create Team). The funnel export, advancements and goggle
 * tooltip are not ported.
 */
public class ItemDrainBlockEntity extends SmartBlockEntity implements Clearable {

	public static final int FILLING_TIME = 20;

	SmartFluidTankBehaviour internalTank;
	TransportedItemStack heldItem;
	protected int processingTicks;
	Map<Direction, ItemDrainItemHandler> itemHandlers;

	public ItemDrainBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		itemHandlers = new IdentityHashMap<>();
		for (Direction d : Iterate.horizontalDirections) {
			itemHandlers.put(d, new ItemDrainItemHandler(this, d));
		}
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(TanksRegistry.ITEM_DRAIN_BLOCK_ENTITY,
			(be, side) -> {
				if (side != null && side.getAxis().isHorizontal())
					return be.itemHandlers.get(side);
				return null;
			});

		FluidHandlerLookup.registerBlockEntity(TanksRegistry.ITEM_DRAIN_BLOCK_ENTITY,
			(be, side) -> {
				if (side != Direction.UP)
					return be.internalTank.getCapability();
				return null;
			});
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels()
			.setInsertionHandler(this::tryInsertingFromSide));
		behaviours.add(internalTank = SmartFluidTankBehaviour.single(this, 1500)
			.allowExtraction()
			.forbidInsertion());
	}

	public SmartFluidTankBehaviour getInternalTank() {
		return internalTank;
	}

	private ItemStack tryInsertingFromSide(TransportedItemStack transportedStack, Direction side, boolean simulate) {
		ItemStack inserted = transportedStack.stack;
		ItemStack returned = ItemStack.EMPTY;

		if (!getHeldItemStack().isEmpty())
			return inserted;

		if (inserted.getCount() > 1 && GenericItemEmptying.canItemBeEmptied(level, inserted)) {
			returned = inserted.copyWithCount(inserted.getCount() - 1);
			inserted = inserted.copyWithCount(1);
		}

		if (simulate)
			return returned;

		transportedStack = transportedStack.copy();
		transportedStack.stack = inserted.copy();
		transportedStack.beltPosition = side.getAxis()
			.isVertical() ? .5f : 0;
		transportedStack.prevSideOffset = transportedStack.sideOffset;
		transportedStack.prevBeltPosition = transportedStack.beltPosition;
		setHeldItem(transportedStack, side);
		setChanged();
		sendData();

		return returned;
	}

	public ItemStack getHeldItemStack() {
		return heldItem == null ? ItemStack.EMPTY : heldItem.stack;
	}

	/**
	 * Items that fall on the drain are taken in. Create does this from Block.updateEntityAfterFallOn, which every tick
	 * an entity stands on the block; Minecraft 26.3 only has Block.fallOn, so poll for the rest.
	 */
	private void collectItemEntities() {
		AABB top = new AABB(worldPosition.getX(), worldPosition.getY() + 13 / 16d, worldPosition.getZ(),
			worldPosition.getX() + 1, worldPosition.getY() + 1, worldPosition.getZ() + 1);
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, top))
			onItemLanded(itemEntity);
	}

	private void onItemLanded(ItemEntity itemEntity) {
		if (!itemEntity.isAlive())
			return;
		DirectBeltInputBehaviour inputBehaviour = getBehaviour(DirectBeltInputBehaviour.TYPE);
		if (inputBehaviour == null)
			return;
		Vec3 deltaMovement = itemEntity.getDeltaMovement()
			.multiply(1, 0, 1)
			.normalize();
		Direction nearest = Direction.getApproximateNearest(deltaMovement.x, deltaMovement.y, deltaMovement.z);
		ItemStack remainder = inputBehaviour.handleInsertion(itemEntity.getItem(), nearest, false);
		itemEntity.setItem(remainder);
		if (remainder.isEmpty())
			itemEntity.discard();
	}

	@Override
	public void tick() {
		super.tick();

		if (level.getGameTime() % 4 == 0)
			collectItemEntities();

		if (heldItem == null) {
			processingTicks = 0;
			return;
		}

		if (processingTicks > 0) {
			heldItem.prevBeltPosition = .5f;
			boolean wasAtBeginning = processingTicks == FILLING_TIME;
			processingTicks--;
			if (!continueProcessing()) {
				processingTicks = 0;
				notifyUpdate();
				return;
			}
			if (wasAtBeginning != (processingTicks == FILLING_TIME))
				sendData();
			return;
		}

		heldItem.prevBeltPosition = heldItem.beltPosition;
		heldItem.prevSideOffset = heldItem.sideOffset;

		heldItem.beltPosition += itemMovementPerTick();
		if (heldItem.beltPosition > 1) {
			heldItem.beltPosition = 1;

			Direction side = heldItem.insertedFrom;

			BlockPos nextPosition = worldPosition.relative(side);
			DirectBeltInputBehaviour directBeltInputBehaviour =
				BlockEntityBehaviour.get(level, nextPosition, DirectBeltInputBehaviour.TYPE);
			if (directBeltInputBehaviour == null) {
				if (!hasBlockSolidSide(level.getBlockState(nextPosition), nextPosition, side.getOpposite())) {
					ItemStack ejected = heldItem.stack;
					Vec3 outPos = VecHelper.getCenterOf(worldPosition)
						.add(Vec3.atLowerCornerOf(side.getUnitVec3i())
							.scale(.75));
					float movementSpeed = itemMovementPerTick();
					Vec3 outMotion = Vec3.atLowerCornerOf(side.getUnitVec3i())
						.scale(movementSpeed)
						.add(0, 1 / 8f, 0);
					outPos.add(outMotion.normalize());
					ItemEntity entity = new ItemEntity(level, outPos.x, outPos.y + 6 / 16f, outPos.z, ejected);
					entity.setDeltaMovement(outMotion);
					entity.setDefaultPickUpDelay();
					level.addFreshEntity(entity);

					heldItem = null;
					notifyUpdate();
				}
				return;
			}

			if (!directBeltInputBehaviour.canInsertFromSide(side))
				return;

			ItemStack returned = directBeltInputBehaviour.handleInsertion(heldItem.copy(), side, false);

			if (returned.isEmpty()) {
				heldItem = null;
				notifyUpdate();
				return;
			}

			if (returned.getCount() != heldItem.stack.getCount()) {
				heldItem.stack = returned;
				notifyUpdate();
				return;
			}

			return;
		}

		if (heldItem.prevBeltPosition < .5f && heldItem.beltPosition >= .5f) {
			if (!GenericItemEmptying.canItemBeEmptied(level, heldItem.stack))
				return;
			heldItem.beltPosition = .5f;
			processingTicks = FILLING_TIME;
			sendData();
		}

	}

	/** Create's BlockHelper.hasBlockSolidSide */
	private boolean hasBlockSolidSide(BlockState state, BlockPos pos, Direction dir) {
		return !state.is(BlockTags.LEAVES) && Block.isFaceFull(state.getCollisionShape(level, pos), dir);
	}

	protected boolean continueProcessing() {
		if (processingTicks < 5)
			return true;
		if (!GenericItemEmptying.canItemBeEmptied(level, heldItem.stack))
			return false;

		Pair<FluidStack, ItemStack> emptyItem = GenericItemEmptying.emptyItem(level, heldItem.stack, true);
		FluidStack fluidFromItem = emptyItem.getFirst();

		if (processingTicks > 5) {
			internalTank.allowInsertion();
			if (internalTank.getPrimaryHandler()
				.fill(fluidFromItem, FluidAction.SIMULATE) != fluidFromItem.getAmount()) {
				internalTank.forbidInsertion();
				processingTicks = FILLING_TIME;
				return true;
			}
			internalTank.forbidInsertion();
			return true;
		}

		emptyItem = GenericItemEmptying.emptyItem(level, heldItem.stack.copy(), false);

		// Process finished
		ItemStack out = emptyItem.getSecond();
		if (!out.isEmpty())
			heldItem.stack = out;
		else
			heldItem = null;
		internalTank.allowInsertion();
		internalTank.getPrimaryHandler()
			.fill(fluidFromItem, FluidAction.EXECUTE);
		internalTank.forbidInsertion();
		notifyUpdate();
		return true;
	}

	private float itemMovementPerTick() {
		return 1 / 8f;
	}

	public void setHeldItem(TransportedItemStack heldItem, Direction insertedFrom) {
		this.heldItem = heldItem;
		this.heldItem.insertedFrom = insertedFrom;
	}

	@Override
	public void clearContent() {
		this.heldItem = null;
	}

	/** Block broken or replaced: drop the held item (Create's ItemDrainBlock.onRemove). */
	@Override
	public void destroy() {
		super.destroy();
		ItemStack heldItemStack = getHeldItemStack();
		if (!heldItemStack.isEmpty())
			net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
				heldItemStack);
	}

	@Override
	public void write(ValueOutput compound) {
		compound.putInt("ProcessingTicks", processingTicks);
		if (heldItem != null)
			heldItem.write(compound.child("HeldItem"));
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		heldItem = compound.child("HeldItem")
			.map(TransportedItemStack::read)
			.orElse(null);
		processingTicks = compound.getIntOr("ProcessingTicks", 0);
		super.read(compound);
	}

}

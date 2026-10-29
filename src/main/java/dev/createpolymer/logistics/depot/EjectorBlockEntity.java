package dev.createpolymer.logistics.depot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.logistics.box.PackageEntity;
import dev.createpolymer.logistics.funnel.AbstractFunnelBlock;
import dev.createpolymer.logistics.funnel.FunnelBlock;
import dev.createpolymer.registry.feature.EjectorRegistry;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.IntAttached;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import dev.createpolymer.util.NBTHelper;
import dev.createpolymer.util.Pair;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.CreatePolymer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;

public class EjectorBlockEntity extends KineticBlockEntity {

	/**
	 * Players see an invisible full-size block, so they stand on its top face and not on the 13/16 high
	 * casing. The entities an ejector launches are searched for this much higher than Create does.
	 */
	static final double STANDING_REACH = 3 / 16d;

	List<IntAttached<ItemStack>> launchedItems;
	ScrollValueBehaviour maxStackSize;
	DepotBehaviour depotBehaviour;
	EntityLauncher launcher;
	LerpedFloat lidProgress;
	boolean powered;
	boolean launch;
	State state;

	// item collision
	@Nullable
	Pair<Vec3, BlockPos> earlyTarget;
	float earlyTargetTime;
	// runtime stuff
	int scanCooldown;
	ItemStack trackedItem;

	public enum State {
		CHARGED, LAUNCHING, RETRACTING;
	}

	public EjectorBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		launcher = new EntityLauncher(1, 0);
		lidProgress = LerpedFloat.linear()
			.startWithValue(1);
		this.state = State.RETRACTING;
		launchedItems = new ArrayList<>();
		powered = false;
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(
			EjectorRegistry.WEIGHTED_EJECTOR_BLOCK_ENTITY, (be, side) -> be.depotBehaviour.itemHandler);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.add(depotBehaviour = new DepotBehaviour(this));

		maxStackSize =
			new ScrollValueBehaviour(Lang.builder(CreatePolymer.MOD_ID).translate("weighted_ejector.stack_size").component(),
				this, new EjectorSlot())
				.between(0, 64)
				.withFormatter(i -> i == 0 ? "*" : String.valueOf(i));
		behaviours.add(maxStackSize);

		depotBehaviour.maxStackSize = () -> maxStackSize.getValue();
		depotBehaviour.canAcceptItems = () -> state == State.CHARGED;
		depotBehaviour.canFunnelsPullFrom = side -> side != getFacing();
		depotBehaviour.enableMerging();
		depotBehaviour.addSubBehaviours(behaviours);
	}

	@Override
	public void initialize() {
		super.initialize();
		updateSignal();
	}

	public void activate() {
		launch = true;
		nudgeEntities();
	}

	protected boolean cannotLaunch() {
		return state != State.CHARGED;
	}

	/** The area in which entities are picked up by a launch. */
	protected AABB getLaunchArea() {
		return new AABB(worldPosition).inflate(-1 / 16f, 0, -1 / 16f)
			.expandTowards(0, STANDING_REACH, 0);
	}

	public void activateDeferred() {
		if (cannotLaunch())
			return;
		Direction facing = getFacing();
		List<Entity> entities = level.getEntitiesOfClass(Entity.class, getLaunchArea());

		// Launch Items
		launchItems();

		// Launch Entities
		for (Entity entity : entities) {
			boolean isPlayerEntity = entity instanceof Player;
			if (!entity.isAlive())
				continue;
			if (entity instanceof ItemEntity)
				continue;
			if (entity instanceof PackageEntity)
				continue;
			if (entity.getPistonPushReaction() == PushReaction.IGNORE_ENTITY)
				continue;

			entity.setOnGround(false);

			entity.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5);
			launcher.applyMotion(entity, facing);

			if (isPlayerEntity) {
				Player playerEntity = (Player) entity;

				if (LivingEntity.canGlideUsing(playerEntity.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST)) {
					playerEntity.setXRot(-35);
					playerEntity.setYRot(facing.toYRot());
					playerEntity.setDeltaMovement(playerEntity.getDeltaMovement()
						.scale(.75f));
					deployElytra(playerEntity);
				}

				// Players move on their own: tell the client where it is now and how fast it is going
				if (playerEntity instanceof ServerPlayer serverPlayer && serverPlayer.connection != null)
					serverPlayer.connection.teleport(new PositionMoveRotation(serverPlayer.position(),
						serverPlayer.getDeltaMovement(), serverPlayer.getYRot(), serverPlayer.getXRot()), Set.of());
			}

			entity.syncVelocity = true;
		}

		lidProgress.chase(1, .8f, Chaser.EXP);
		state = State.LAUNCHING;
		level.playSound(null, worldPosition, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, .35f, 1f);
		level.playSound(null, worldPosition, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, .1f, 1.4f);
	}

	public void deployElytra(Player playerEntity) {
		playerEntity.startFallFlying();
	}

	protected void launchItems() {
		ItemStack heldItemStack = depotBehaviour.getHeldItemStack();
		Direction funnelFacing = getFacing().getOpposite();

		if (AbstractFunnelBlock.getFunnelFacing(level.getBlockState(worldPosition.above())) == funnelFacing) {
			DirectBeltInputBehaviour directOutput = getBehaviour(DirectBeltInputBehaviour.TYPE);

			if (depotBehaviour.heldItem != null) {
				ItemStack remainder = directOutput.tryExportingToBeltFunnel(heldItemStack, funnelFacing, false);
				if (remainder == null)
					;
				else if (remainder.isEmpty())
					depotBehaviour.removeHeldItem();
				else if (remainder.getCount() != heldItemStack.getCount())
					depotBehaviour.heldItem.stack = remainder;
			}

			for (Iterator<TransportedItemStack> iterator = depotBehaviour.incoming.iterator(); iterator.hasNext();) {
				TransportedItemStack transportedItemStack = iterator.next();
				ItemStack stack = transportedItemStack.stack;
				ItemStack remainder = directOutput.tryExportingToBeltFunnel(stack, funnelFacing, false);
				if (remainder == null)
					;
				else if (remainder.isEmpty())
					iterator.remove();
				else if (!ItemStack.isSameItem(remainder, stack))
					transportedItemStack.stack = remainder;
			}

			ItemStackHandler outputs = depotBehaviour.processingOutputBuffer;
			for (int i = 0; i < outputs.getSlots(); i++) {
				ItemStack remainder =
					directOutput.tryExportingToBeltFunnel(outputs.getStackInSlot(i), funnelFacing, false);
				if (remainder != null)
					outputs.setStackInSlot(i, remainder);
			}
			return;
		}

		for (Direction d : Iterate.directions) {
			BlockState blockState = level.getBlockState(worldPosition.relative(d));
			if (!(blockState.getBlock() instanceof ObserverBlock))
				continue;
			if (blockState.getValue(ObserverBlock.FACING) != d.getOpposite())
				continue;
			if (level instanceof ServerLevel serverLevel)
				blockState.updateShape(level, serverLevel, worldPosition.relative(d), d.getOpposite(), worldPosition,
					getBlockState(), serverLevel.getRandom());
		}

		if (depotBehaviour.heldItem != null) {
			addToLaunchedItems(heldItemStack);
			depotBehaviour.removeHeldItem();
		}

		for (TransportedItemStack transportedItemStack : depotBehaviour.incoming)
			addToLaunchedItems(transportedItemStack.stack);
		depotBehaviour.incoming.clear();

		ItemStackHandler outputs = depotBehaviour.processingOutputBuffer;
		for (int i = 0; i < outputs.getSlots(); i++) {
			ItemStack extractItem = outputs.extractItem(i, 64, false);
			if (!extractItem.isEmpty())
				addToLaunchedItems(extractItem);
		}
	}

	protected boolean addToLaunchedItems(ItemStack stack) {
		if (trackedItem == null && scanCooldown == 0) {
			scanCooldown = EjectorRegistry.ejectorScanInterval;
			trackedItem = stack;
		}
		return launchedItems.add(IntAttached.withZero(stack));
	}

	protected Direction getFacing() {
		BlockState blockState = getBlockState();
		if (!blockState.is(EjectorRegistry.WEIGHTED_EJECTOR))
			return Direction.UP;
		Direction facing = blockState.getValue(EjectorBlock.HORIZONTAL_FACING);
		return facing;
	}

	@Override
	public void tick() {
		super.tick();

		State prevState = state;
		float totalTime = Math.max(3, (float) launcher.getTotalFlyingTicks());

		if (scanCooldown > 0)
			scanCooldown--;

		// Create learns of these from Block.updateEntityAfterFallOn
		checkEntitiesOnTop();

		if (launch) {
			launch = false;
			activateDeferred();
		}

		for (Iterator<IntAttached<ItemStack>> iterator = launchedItems.iterator(); iterator.hasNext();) {
			IntAttached<ItemStack> intAttached = iterator.next();
			boolean hit = false;
			if (intAttached.getSecond() == trackedItem)
				hit = scanTrajectoryForObstacles(intAttached.getFirst());
			float maxTime = earlyTarget != null ? Math.min(earlyTargetTime, totalTime) : totalTime;
			if (hit || intAttached.exceeds((int) maxTime)) {
				placeItemAtTarget(true, maxTime, intAttached);
				iterator.remove();
			}
			intAttached.increment();
		}

		if (state == State.LAUNCHING) {
			lidProgress.chase(1, .8f, Chaser.EXP);
			lidProgress.tickChaser();
			if (lidProgress.getValue() > 1 - 1 / 16f) {
				state = State.RETRACTING;
				lidProgress.setValue(1);
			}
		}

		if (state == State.CHARGED) {
			lidProgress.setValue(0);
			lidProgress.updateChaseSpeed(0);
			ejectIfTriggered();
		}

		if (state == State.RETRACTING) {
			if (lidProgress.getChaseTarget() == 1 && !lidProgress.settled()) {
				lidProgress.tickChaser();
			} else {
				lidProgress.updateChaseTarget(0);
				lidProgress.updateChaseSpeed(0);
				if (lidProgress.getValue() == 0) {
					state = State.CHARGED;
					lidProgress.setValue(0);
					sendData();
				}

				float value = Mth.clamp(lidProgress.getValue() - getWindUpSpeed(), 0, 1);
				lidProgress.setValue(value);

				int soundRate = (int) (1 / (getWindUpSpeed() * 5)) + 1;
				float volume = .125f;
				float pitch = 1.5f - lidProgress.getValue();
				if (((int) level.getGameTime()) % soundRate == 0)
					level.playSound(null, worldPosition, SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundSource.BLOCKS,
						volume, pitch);
			}
		}

		if (state != prevState)
			notifyUpdate();
	}

	/**
	 * Minecraft 26.3 has no Block.updateEntityAfterFallOn, which Create uses to notice entities standing on the
	 * ejector every tick, so the ejector looks for them itself.
	 */
	protected void checkEntitiesOnTop() {
		if (!(getBlockState().getBlock() instanceof EjectorBlock ejector))
			return;
		AABB top = new AABB(worldPosition.getX(), worldPosition.getY() + 12 / 16d, worldPosition.getZ(),
			worldPosition.getX() + 1, worldPosition.getY() + 1 + STANDING_REACH, worldPosition.getZ() + 1);
		for (Entity entity : level.getEntitiesOfClass(Entity.class, top)) {
			// items are taken in by the cell they are in
			if ((entity instanceof ItemEntity || entity instanceof PackageEntity) && !entity.blockPosition().equals(worldPosition))
				continue;
			ejector.updateEntityAfterFallOn(level, entity);
		}
	}

	private boolean scanTrajectoryForObstacles(int time) {
		if (time <= 2)
			return false;

		Vec3 source = getLaunchedItemLocation(time);
		Vec3 target = getLaunchedItemLocation(time + 1);

		BlockHitResult rayTraceBlocks = level.clip(new ClipContext(source, target, Block.COLLIDER, Fluid.NONE, CollisionContext.empty()));
		boolean miss = rayTraceBlocks.getType() == Type.MISS;

		if (!miss && rayTraceBlocks.getType() == Type.BLOCK) {
			BlockState blockState = level.getBlockState(rayTraceBlocks.getBlockPos());
			if (FunnelBlock.isFunnel(blockState) && blockState.hasProperty(FunnelBlock.EXTRACTING)
				&& blockState.getValue(FunnelBlock.EXTRACTING))
				miss = true;
		}

		if (miss) {
			if (earlyTarget != null && earlyTargetTime < time + 1) {
				earlyTarget = null;
				earlyTargetTime = 0;
			}
			return false;
		}

		Vec3 vec = rayTraceBlocks.getLocation();
		earlyTarget = Pair.of(vec.add(Vec3.atLowerCornerOf(rayTraceBlocks.getDirection()
			.getUnitVec3i())
			.scale(.25f)), rayTraceBlocks.getBlockPos());
		earlyTargetTime = (float) (time + (source.distanceTo(vec) / source.distanceTo(target)));
		sendData();
		return true;
	}

	protected void nudgeEntities() {
		for (Entity entity : level.getEntitiesOfClass(Entity.class, getLaunchArea())) {
			if (!entity.isAlive())
				continue;
			if (entity.getPistonPushReaction() == PushReaction.IGNORE_ENTITY)
				continue;
			if (!(entity instanceof Player))
				entity.setPos(entity.getX(), entity.getY() + .125f, entity.getZ());
		}
	}

	protected void ejectIfTriggered() {
		if (powered)
			return;
		int presentStackSize = depotBehaviour.getPresentStackSize();
		if (presentStackSize == 0)
			return;
		if (presentStackSize < maxStackSize.getValue())
			return;
		if (depotBehaviour.heldItem != null && depotBehaviour.heldItem.beltPosition < .49f)
			return;

		Direction funnelFacing = getFacing().getOpposite();
		ItemStack held = depotBehaviour.getHeldItemStack();
		if (AbstractFunnelBlock.getFunnelFacing(level.getBlockState(worldPosition.above())) == funnelFacing) {
			DirectBeltInputBehaviour directOutput = getBehaviour(DirectBeltInputBehaviour.TYPE);
			if (depotBehaviour.heldItem != null) {
				ItemStack tryFunnel = directOutput.tryExportingToBeltFunnel(held, funnelFacing, true);
				if (tryFunnel == null || !tryFunnel.isEmpty())
					return;
			}
		}

		DirectBeltInputBehaviour targetOpenInv = getTargetOpenInv();

		// Do not eject if target cannot accept held item
		if (targetOpenInv != null && depotBehaviour.heldItem != null
			&& targetOpenInv.handleInsertion(held, Direction.UP, true)
				.getCount() == held.getCount())
			return;

		activate();
		notifyUpdate();
	}

	protected void placeItemAtTarget(boolean doLogic, float maxTime, IntAttached<ItemStack> intAttached) {
		if (!doLogic)
			return;
		if (intAttached.getSecond() == trackedItem)
			trackedItem = null;

		DirectBeltInputBehaviour targetOpenInv = getTargetOpenInv();
		if (targetOpenInv != null) {
			ItemStack remainder = targetOpenInv.handleInsertion(intAttached.getValue(), Direction.UP, false);
			intAttached.setSecond(remainder);
		}

		if (intAttached.getValue()
			.isEmpty())
			return;

		Vec3 ejectVec = earlyTarget != null ? earlyTarget.getFirst() : getLaunchedItemLocation(maxTime);
		Vec3 ejectMotionVec = getLaunchedItemMotion(maxTime);
		ItemEntity item = new ItemEntity(level, ejectVec.x, ejectVec.y, ejectVec.z, intAttached.getValue());
		item.setDeltaMovement(ejectMotionVec);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	public DirectBeltInputBehaviour getTargetOpenInv() {
		BlockPos targetPos = earlyTarget != null ? earlyTarget.getSecond()
			: worldPosition.above(launcher.getVerticalDistance())
				.relative(getFacing(), Math.max(1, launcher.getHorizontalDistance()));
		return BlockEntityBehaviour.get(level, targetPos, DirectBeltInputBehaviour.TYPE);
	}

	public Vec3 getLaunchedItemLocation(float time) {
		return launcher.getGlobalPos(time, getFacing().getOpposite(), worldPosition);
	}

	public Vec3 getLaunchedItemMotion(float time) {
		return launcher.getGlobalVelocity(time, getFacing().getOpposite(), worldPosition)
			.scale(.5f);
	}

	@Override
	public void destroy() {
		super.destroy();
		dropFlyingItems();
	}

	public void dropFlyingItems() {
		for (IntAttached<ItemStack> intAttached : launchedItems) {
			Vec3 ejectVec = getLaunchedItemLocation(intAttached.getFirst());
			Vec3 ejectMotionVec = getLaunchedItemMotion(intAttached.getFirst());
			ItemEntity item = new ItemEntity(level, 0, 0, 0, intAttached.getValue());
			item.setPosRaw(ejectVec.x, ejectVec.y, ejectVec.z);
			item.setDeltaMovement(ejectMotionVec);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		launchedItems.clear();
	}

	public float getWindUpSpeed() {
		int hd = launcher.getHorizontalDistance();
		int vd = launcher.getVerticalDistance();

		float speedFactor = Math.abs(getSpeed()) / 256f;
		float distanceFactor;
		if (hd == 0 && vd == 0)
			distanceFactor = 1;
		else
			distanceFactor = 1 * Mth.sqrt(hd * hd + vd * vd);
		return speedFactor / distanceFactor;
	}

	@Override
	public void writeSafe(ValueOutput compound) {
		super.writeSafe(compound);
		compound.putInt("HorizontalDistance", launcher.getHorizontalDistance());
		compound.putInt("VerticalDistance", launcher.getVerticalDistance());
	}

	@Override
	protected void write(ValueOutput compound) {
		super.write(compound);
		compound.putInt("HorizontalDistance", launcher.getHorizontalDistance());
		compound.putInt("VerticalDistance", launcher.getVerticalDistance());
		compound.putBoolean("Powered", powered);
		NBTHelper.writeEnum(compound, "State", state);
		ValueOutput lid = compound.child("Lid");
		lid.putFloat("Value", lidProgress.getValue());
		lid.putFloat("Target", lidProgress.getChaseTarget());
		ValueOutput.TypedOutputList<IntAttached<ItemStack>> items =
			compound.list("LaunchedItems", IntAttached.codec(ItemStack.OPTIONAL_CODEC));
		for (IntAttached<ItemStack> item : launchedItems)
			items.add(item);

		if (earlyTarget != null) {
			compound.store("EarlyTarget", Vec3.CODEC, earlyTarget.getFirst());
			compound.store("EarlyTargetPos", BlockPos.CODEC, earlyTarget.getSecond());
			compound.putFloat("EarlyTargetTime", earlyTargetTime);
		}
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		int horizontalDistance = compound.getIntOr("HorizontalDistance", 0);
		int verticalDistance = compound.getIntOr("VerticalDistance", 0);

		if (launcher.getHorizontalDistance() != horizontalDistance
			|| launcher.getVerticalDistance() != verticalDistance) {
			launcher.set(horizontalDistance, verticalDistance);
			launcher.clamp(EjectorRegistry.maxEjectorDistance);
		}

		powered = compound.getBooleanOr("Powered", false);
		state = NBTHelper.readEnum(compound, "State", State.class);
		ValueInput lid = compound.childOrEmpty("Lid");
		lidProgress.startWithValue(lid.getFloatOr("Value", 1));
		lidProgress.updateChaseTarget(lid.getFloatOr("Target", 1));
		launchedItems = new ArrayList<>();
		for (IntAttached<ItemStack> item : compound.listOrEmpty("LaunchedItems", IntAttached.codec(ItemStack.OPTIONAL_CODEC)))
			launchedItems.add(item);

		earlyTarget = null;
		earlyTargetTime = 0;
		Vec3 earlyTargetVec = compound.read("EarlyTarget", Vec3.CODEC).orElse(null);
		BlockPos earlyTargetPos = compound.read("EarlyTargetPos", BlockPos.CODEC).orElse(null);
		if (earlyTargetVec != null && earlyTargetPos != null) {
			earlyTarget = Pair.of(earlyTargetVec, earlyTargetPos);
			earlyTargetTime = compound.getFloatOr("EarlyTargetTime", 0);
		}
	}

	public void updateSignal() {
		boolean shoudPower = level.hasNeighborSignal(worldPosition);
		if (shoudPower == powered)
			return;
		powered = shoudPower;
		notifyUpdate();
	}

	public void setTarget(int horizontalDistance, int verticalDistance) {
		launcher.set(Math.max(1, horizontalDistance), verticalDistance);
		notifyUpdate();
	}

	public BlockPos getTargetPosition() {
		BlockState blockState = getBlockState();
		if (!blockState.is(EjectorRegistry.WEIGHTED_EJECTOR))
			return worldPosition;
		Direction facing = blockState.getValue(EjectorBlock.HORIZONTAL_FACING);
		return worldPosition.relative(facing, launcher.getHorizontalDistance())
			.above(launcher.getVerticalDistance());
	}

	public float getLidProgress(float pt) {
		return lidProgress.getValue(pt);
	}

	public State getState() {
		return state;
	}

	public EntityLauncher getLauncher() {
		return launcher;
	}

	public boolean isPowered() {
		return powered;
	}

	public int getLaunchedItemCount() {
		return launchedItems.size();
	}

	public DepotBehaviour getDepotBehaviour() {
		return depotBehaviour;
	}

	private class EjectorSlot extends ValueBoxTransform.Sided {

		@Override
		public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
			if (direction != Direction.UP)
				return super.getLocalOffset(level, pos, state);
			return new Vec3(.5, 10.5 / 16f, .5).add(VecHelper.rotate(VecHelper.voxelSpace(0, 0, -5), angle(state), Axis.Y));
		}

		@Override
		public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
			if (direction != Direction.UP) {
				super.rotate(level, pos, state, ms);
				return;
			}
			ms.rotateY((float) Math.toRadians(angle(state)))
				.rotateX((float) Math.toRadians(90));
		}

		protected float angle(BlockState state) {
			float horizontalAngle = state.is(EjectorRegistry.WEIGHTED_EJECTOR)
				? AngleHelper.horizontalAngle(state.getValue(EjectorBlock.HORIZONTAL_FACING))
				: 0;
			return horizontalAngle;
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() == state.getValue(EjectorBlock.HORIZONTAL_FACING)
				.getAxis()
				|| direction == Direction.UP && EjectorBlockEntity.this.state != EjectorBlockEntity.State.CHARGED;
		}

		@Override
		protected Vec3 getSouthLocation() {
			return direction == Direction.UP ? Vec3.ZERO : VecHelper.voxelSpace(8, 6, 15.5);
		}

	}

}

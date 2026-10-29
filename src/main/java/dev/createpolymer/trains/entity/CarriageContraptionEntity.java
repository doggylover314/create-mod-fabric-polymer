package dev.createpolymer.trains.entity;

import java.util.Collection;
import java.util.UUID;

import com.google.common.base.Strings;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.OrientedContraptionEntity;
import dev.createpolymer.contraptions.actors.trainControls.ControlsBlock;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.TrainConfig;
import dev.createpolymer.trains.entity.Carriage.DimensionalCarriageEntity;
import dev.createpolymer.trains.entity.TravellingPoint.SteerDirection;
import dev.createpolymer.trains.station.GlobalStation;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;

/**
 * One carriage of a train (Create's CarriageContraptionEntity). The carriage object of the train moves the entity
 * along the track and re-creates it when its chunk ticks again; it is never saved with the chunk.
 * <p>
 * Vanilla clients see the blocks like those of any contraption, and the bogeys as separate display parts that follow
 * the wheels ({@link CarriageBogeyHolder}).
 */
public class CarriageContraptionEntity extends OrientedContraptionEntity {

	public UUID trainId;
	public int carriageIndex;

	private Carriage carriage;
	public boolean validForRender;
	public boolean movingBackwards;

	public boolean leftTickingChunks;
	public boolean firstPositionUpdate;

	private Vec3 serverPrevPos;

	private CarriageBogeyHolder bogeyHolder;
	private EntityAttachment bogeyAttachment;

	public CarriageContraptionEntity(EntityType<?> type, Level world) {
		super(type, world);
		validForRender = false;
		firstPositionUpdate = true;
	}

	/** Create syncs the carriage data of the entity to clients here; there is nothing to sync. */
	public void syncCarriage() {}

	public boolean hasSchedule() {
		return carriage != null && carriage.train.runtime.getSchedule() != null;
	}

	public void setServerSidePrevPosition() {
		serverPrevPos = position();
	}

	@Override
	public Vec3 getPrevPositionVec() {
		if (!level().isClientSide() && serverPrevPos != null)
			return serverPrevPos;
		return super.getPrevPositionVec();
	}

	public boolean isLocalCoordWithin(BlockPos localPos, int min, int max) {
		if (!(getContraption() instanceof CarriageContraption cc))
			return false;
		Direction facing = cc.getAssemblyDirection();
		Axis axis = facing.getClockWise()
			.getAxis();
		int coord = axis.choose(localPos.getZ(), localPos.getY(), localPos.getX()) * -facing.getAxisDirection()
			.getStep();
		return coord >= min && coord <= max;
	}

	public static CarriageContraptionEntity create(Level world, CarriageContraption contraption) {
		CarriageContraptionEntity entity = new CarriageContraptionEntity(TrainsRegistry.CARRIAGE_CONTRAPTION, world);
		entity.setContraption(contraption);
		entity.setInitialOrientation(contraption.getAssemblyDirection()
			.getClockWise());
		entity.startAtInitialYaw();
		return entity;
	}

	@Override
	public void tick() {
		super.tick();

		if (contraption instanceof CarriageContraption cc)
			for (Entity entity : getPassengers()) {
				if (entity instanceof Player)
					continue;
				BlockPos seatOf = cc.getSeatOf(entity.getUUID());
				if (seatOf == null)
					continue;
				if (cc.conductorSeats.get(seatOf) == null)
					continue;
				alignPassenger(entity);
			}

		tickBogeys();
	}

	private void tickBogeys() {
		if (contraption == null || !(level() instanceof ServerLevel) || carriage == null || isRemoved())
			return;
		if (bogeyHolder == null) {
			bogeyHolder = new CarriageBogeyHolder(this);
			bogeyAttachment = EntityAttachment.ofTicking(bogeyHolder, this);
		}
	}

	public CarriageBogeyHolder getBogeyHolder() {
		return bogeyHolder;
	}

	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if (bogeyHolder != null) {
			bogeyHolder.destroy();
			bogeyHolder = null;
			bogeyAttachment = null;
		}
	}

	@Override
	public void setBlock(BlockPos localPos, StructureBlockInfo newInfo) {
		if (carriage == null)
			return;
		carriage.forEachPresentEntity(cce -> {
			StructureBlockInfo old = cce.getContraption()
				.getBlocks()
				.put(localPos, newInfo);
			if (old != null && old.state() != newInfo.state())
				cce.getContraption()
					.resetClientContraption();
			cce.getContraption()
				.invalidateColliders();
		});
	}

	@Override
	protected void tickContraption() {
		if (nonDamageTicks > 0)
			nonDamageTicks--;
		if (!(contraption instanceof CarriageContraption cc))
			return;

		if (carriage == null) {
			discard();
			return;
		}

		if (!TrackRegistry.RAILWAYS.trains.containsKey(carriage.train.id)) {
			discard();
			return;
		}

		tickActors();
		boolean isStalled = isStalled();
		carriage.stalled = isStalled;

		level().gameEvent(this, GameEvent.RESONATE_8, this.position());

		// Create runs this on the client, from the synced positions: the wheels turn by the distance travelled
		double distanceTo = 0;
		if (!firstPositionUpdate) {
			Vec3 diff = position().subtract(getPrevPositionVec());
			Vec3 relativeDiff = VecHelper.rotate(diff, yaw, Axis.Y);
			double signum = Math.signum(-relativeDiff.x);
			distanceTo = diff.length() * signum;
			movingBackwards = signum < 0;
		}

		carriage.bogeys.getFirst()
			.updateAngles(this, distanceTo);
		if (carriage.isOnTwoBogeys())
			carriage.bogeys.getSecond()
				.updateAngles(this, distanceTo);

		firstPositionUpdate = false;
		validForRender = true;
	}

	@Override
	protected boolean isActorActive(MovementContext context, MovementBehaviour actor) {
		if (!(contraption instanceof CarriageContraption cc))
			return false;
		if (!super.isActorActive(context, actor))
			return false;
		return cc.notInPortal();
	}

	@Override
	protected boolean shouldActorTrigger(MovementContext context, StructureBlockInfo blockInfo, MovementBehaviour actor,
		Vec3 actorPosition, BlockPos gridPosition) {
		Vec3 previousPosition = context.position;
		if (previousPosition == null)
			return false;

		context.motion = actorPosition.subtract(previousPosition);

		if (carriage != null) {
			Train train = carriage.train;
			double actualSpeed = train.speedBeforeStall != null ? train.speedBeforeStall : train.speed;
			context.motion = context.motion.normalize()
				.scale(Math.abs(actualSpeed));
		}

		Vec3 relativeMotion = context.motion;
		relativeMotion = reverseRotation(relativeMotion, 1);
		context.relativeMotion = relativeMotion;

		// the first movement counts on a carriage even before it has moved
		return !BlockPos.containing(previousPosition)
			.equals(gridPosition) || context.firstMovement;
	}

	@Override
	protected void writeAdditional(CompoundTag compound, HolderLookup.Provider registries, boolean spawnPacket) {
		super.writeAdditional(compound, registries, spawnPacket);
		if (trainId != null)
			compound.store("TrainId", net.minecraft.core.UUIDUtil.CODEC, trainId);
		compound.putInt("CarriageIndex", carriageIndex);
	}

	@Override
	protected void readAdditional(CompoundTag compound, boolean spawnPacket) {
		super.readAdditional(compound, spawnPacket);
		trainId = compound.read("TrainId", net.minecraft.core.UUIDUtil.CODEC)
			.orElse(null);
		carriageIndex = compound.getIntOr("CarriageIndex", 0);
	}

	@Override
	public Component getContraptionName() {
		if (carriage != null)
			return carriage.train.name;
		return super.getContraptionName();
	}

	public Couple<Boolean> checkConductors() {
		Couple<Boolean> sides = Couple.create(false, false);
		if (!(contraption instanceof CarriageContraption cc))
			return sides;

		sides.setFirst(cc.blockConductors.getFirst());
		sides.setSecond(cc.blockConductors.getSecond());

		for (Entity entity : getPassengers()) {
			if (entity instanceof Player)
				continue;
			BlockPos seatOf = cc.getSeatOf(entity.getUUID());
			if (seatOf == null)
				continue;
			Couple<Boolean> validSides = cc.conductorSeats.get(seatOf);
			if (validSides == null)
				continue;
			sides.setFirst(sides.getFirst() || validSides.getFirst());
			sides.setSecond(sides.getSecond() || validSides.getSecond());
		}

		return sides;
	}

	@Override
	public boolean startControlling(BlockPos controlsLocalPos, Player player) {
		if (player == null || player.isSpectator())
			return false;
		if (carriage == null)
			return false;
		if (carriage.train.derailed)
			return false;

		Train train = carriage.train;
		if (train.runtime.getSchedule() != null && !train.runtime.paused)
			train.status.manualControls();
		train.navigation.cancelNavigation();
		train.runtime.paused = true;
		train.navigation.waitingForSignal = null;
		return true;
	}

	@Override
	public Component getDisplayName() {
		if (carriage == null)
			return TrackNbt.translateDirect("train.unnamed");
		return carriage.train.name;
	}

	double navDistanceTotal = 0;
	int hudPacketCooldown = 0;

	/**
	 * Create's manual driving. The held controls are the six movement keys of the player (forward, back, left,
	 * right, jump, sneak = 0 to 5), read from the input a vanilla client sends to the server.
	 */
	@Override
	public boolean control(BlockPos controlsLocalPos, Collection<Integer> heldControls, Player player) {
		if (carriage == null)
			return false;
		if (carriage.train.derailed)
			return false;
		if (level().isClientSide())
			return true;
		if (player.isSpectator())
			return false;
		if (!canInteractWithBlock(player, VecHelper.getCenterOf(controlsLocalPos), 8))
			return false;
		if (heldControls.contains(5))
			return false;

		StructureBlockInfo info = contraption.getBlocks()
			.get(controlsLocalPos);
		Direction initialOrientation = getInitialOrientation().getCounterClockWise();
		boolean inverted = false;
		if (info != null && info.state()
			.hasProperty(ControlsBlock.FACING))
			inverted = !info.state()
				.getValue(ControlsBlock.FACING)
				.equals(initialOrientation);

		boolean showHud = hudPacketCooldown-- <= 0;
		if (showHud)
			hudPacketCooldown = 5;

		int targetSpeed = 0;
		if (heldControls.contains(0))
			targetSpeed++;
		if (heldControls.contains(1))
			targetSpeed--;

		int targetSteer = 0;
		if (heldControls.contains(2))
			targetSteer++;
		if (heldControls.contains(3))
			targetSteer--;

		if (inverted) {
			targetSpeed *= -1;
			targetSteer *= -1;
		}

		if (targetSpeed != 0)
			carriage.train.burnFuel(level());

		boolean slow = inverted ^ targetSpeed < 0;
		boolean spaceDown = heldControls.contains(4);
		GlobalStation currentStation = carriage.train.getCurrentStation();
		if (currentStation != null && spaceDown) {
			sendPrompt(player, TrackNbt.translateDirect("train.arrived_at", Component.literal(currentStation.name)
				.withStyle(s -> s.withColor(0xFFFFFF))));
			return true;
		}

		if (carriage.train.speedBeforeStall != null && targetSpeed != 0
			&& Math.signum(carriage.train.speedBeforeStall) != Math.signum(targetSpeed)) {
			carriage.train.cancelStall();
		}

		if (currentStation != null && targetSpeed != 0) {
			stationMessage = false;
			sendPrompt(player, TrackNbt.translateDirect("train.departing_from", Component.literal(currentStation.name)
				.withStyle(s -> s.withColor(0xFFFFFF))));
		}

		if (currentStation == null) {

			Navigation nav = carriage.train.navigation;
			if (nav.destination != null) {
				if (!spaceDown)
					nav.cancelNavigation();
				if (spaceDown) {
					double f = (nav.distanceToDestination / navDistanceTotal);
					int progress = (int) (Mth.clamp(1 - ((1 - f) * (1 - f)), 0, 1) * 30);
					boolean arrived = progress == 0;
					MutableComponent whiteComponent = Component.literal(Strings.repeat("|", progress));
					MutableComponent greenComponent = Component.literal(Strings.repeat("|", 30 - progress));

					int fromColor = 0x00_FFC244;
					int toColor = 0x00_529915;

					int mixedColor = mixColors(toColor, fromColor, progress / 30f);
					int targetColor = arrived ? toColor : 0x00_544D45;

					MutableComponent component = greenComponent.withStyle(st -> st.withColor(mixedColor))
						.append(whiteComponent.withStyle(st -> st.withColor(targetColor)));
					sendPrompt(player, component);
					carriage.train.manualTick = true;
					return true;
				}
			}

			double directedSpeed = targetSpeed != 0 ? targetSpeed : carriage.train.speed;
			GlobalStation lookAhead = nav
				.findNearestApproachable(!carriage.train.doubleEnded || (directedSpeed != 0 ? directedSpeed > 0 : !inverted));

			if (lookAhead != null) {
				if (spaceDown) {
					carriage.train.manualTick = true;
					nav.startNavigation(nav.findPathTo(lookAhead, -1));
					carriage.train.manualTick = false;
					navDistanceTotal = nav.distanceToDestination;
					return true;
				}
				displayApproachStationMessage(player, lookAhead);
			} else
				cleanUpApproachStationMessage(player);
		}

		carriage.train.manualSteer =
			targetSteer < 0 ? SteerDirection.RIGHT : targetSteer > 0 ? SteerDirection.LEFT : SteerDirection.NONE;

		double topSpeed = carriage.train.maxSpeed() * TrainConfig.manualTrainSpeedModifier;
		double cappedTopSpeed = topSpeed * carriage.train.throttle;

		if (carriage.getLeadingPoint().edge != null && carriage.getLeadingPoint().edge.isTurn()
			|| carriage.getTrailingPoint().edge != null && carriage.getTrailingPoint().edge.isTurn())
			topSpeed = carriage.train.maxTurnSpeed();

		if (slow)
			topSpeed /= 4;
		carriage.train.targetSpeed = Math.min(topSpeed, cappedTopSpeed) * targetSpeed;

		boolean counteringAcceleration = Math.abs(Math.signum(targetSpeed) - Math.signum(carriage.train.speed)) > 1.5f;

		if (slow && !counteringAcceleration)
			carriage.train.backwardsDriver = player;

		carriage.train.manualTick = true;
		carriage.train.approachTargetSpeed(counteringAcceleration ? 2 : 1);

		if (showHud && player instanceof ServerPlayer sp && promptKeepAlive <= 0)
			showOverlay(sp, hudLine(carriage.train));
		return true;
	}

	/** Catnip's Color.mixColors for the progress bar. */
	private static int mixColors(int color1, int color2, float w) {
		int r = (int) Mth.lerp(w, (color1 >> 16) & 0xFF, (color2 >> 16) & 0xFF);
		int g = (int) Mth.lerp(w, (color1 >> 8) & 0xFF, (color2 >> 8) & 0xFF);
		int b = (int) Mth.lerp(w, color1 & 0xFF, color2 & 0xFF);
		return (r << 16) | (g << 8) | b;
	}

	/** The speed line that takes the place of Create's train HUD in the action bar. */
	public static Component hudLine(Train train) {
		double topSpeed = train.maxSpeed() * TrainConfig.manualTrainSpeedModifier;
		double speed = train.speedBeforeStall != null ? train.speedBeforeStall : train.speed;
		int bars = (int) Mth.clamp(Math.abs(speed) / topSpeed * 20, 0, 20);
		MutableComponent line = Component.empty()
			.append(train.name.copy()
				.withStyle(s -> s.withColor(0xFFC244)))
			.append(Component.literal("  " + (speed < 0 ? "<< " : ">> ")
				+ String.format("%.1f", Math.abs(speed) * 20) + " b/s  ")
				.withStyle(s -> s.withColor(0xD5ECC2)))
			.append(Component.literal(Strings.repeat("|", bars))
				.withStyle(s -> s.withColor(0x529915)))
			.append(Component.literal(Strings.repeat("|", 20 - bars))
				.withStyle(s -> s.withColor(0x544D45)));
		return line;
	}

	private String lastOverlay = "";
	private int overlayAge;
	private int promptKeepAlive;

	private void showOverlay(ServerPlayer player, Component component) {
		String text = component.getString();
		if (text.equals(lastOverlay) && overlayAge++ < 30)
			return;
		lastOverlay = text;
		overlayAge = 0;
		player.sendOverlayMessage(component);
	}

	private void sendPrompt(Player player, Component component) {
		if (!(player instanceof ServerPlayer sp))
			return;
		showOverlay(sp, component);
		promptKeepAlive = 10;
	}

	boolean stationMessage = false;

	private void displayApproachStationMessage(Player player, GlobalStation station) {
		sendPrompt(player, TrackNbt.translateDirect("contraption.controls.approach_station",
			Component.keybind("key.jump"), station.name));
		stationMessage = true;
	}

	private void cleanUpApproachStationMessage(Player player) {
		if (!stationMessage)
			return;
		stationMessage = false;
		promptKeepAlive = 0;
	}

	public Carriage getCarriage() {
		return carriage;
	}

	public void setCarriage(Carriage carriage) {
		this.carriage = carriage;
		this.trainId = carriage.train.id;
		this.carriageIndex = carriage.train.carriages.indexOf(carriage);
		if (contraption instanceof CarriageContraption cc)
			cc.swapStorageAfterAssembly(this);

		DimensionalCarriageEntity dimensional = carriage.getDimensional(level());
		dimensional.pivot = null;
		carriage.updateContraptionAnchors();
		dimensional.updateRenderedCutoff();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void tickActors() {
		super.tickActors();
		if (promptKeepAlive > 0)
			promptKeepAlive--;
	}
}

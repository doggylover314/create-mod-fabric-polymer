package dev.createpolymer.kinetics.chainConveyor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorPackage.ChainConveyorPackagePhysicsData;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorShape.ChainConveyorBB;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorShape.ChainConveyorOBB;
import dev.createpolymer.logistics.box.PackageEntity;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.packagePort.frogport.FrogportBlockEntity;
import dev.createpolymer.registry.feature.ChainConveyorRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Create's ChainConveyorBlockEntity. Everything Create does on the client (the interaction shapes, the box physics
 * of the visuals, the render bounding box) is done here too, or in {@link ChainConveyorBlock.Model}, because the
 * server is the only one who knows about the chains.
 */
public class ChainConveyorBlockEntity extends KineticBlockEntity implements Clearable {

	public record ConnectionStats(float tangentAngle, float chainLength, Vec3 start, Vec3 end) {
	}

	public record ConnectedPort(float chainPosition, @Nullable BlockPos connection, String filter) {
	}

	public Set<BlockPos> connections = new HashSet<>();
	public Map<BlockPos, ConnectionStats> connectionStats;

	public Map<BlockPos, ConnectedPort> loopPorts = new HashMap<>();
	public Map<BlockPos, ConnectedPort> travelPorts = new HashMap<>();
	public ChainConveyorRoutingTable routingTable = new ChainConveyorRoutingTable();

	List<ChainConveyorPackage> loopingPackages = new ArrayList<>();
	Map<BlockPos, List<ChainConveyorPackage>> travellingPackages = new HashMap<>();

	public boolean reversed;
	public boolean cancelDrops;
	public boolean checkInvalid;

	public ChainConveyorBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		checkInvalid = true;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		updateChainShapes();
	}

	public boolean canAcceptMorePackages() {
		return loopingPackages.size() + travellingPackages.size() < ChainConveyorRegistry.chainConveyorCapacity;
	}

	public boolean canAcceptPackagesFor(@Nullable BlockPos connection) {
		if (connection == null && !canAcceptMorePackages())
			return false;
		if (connection != null
			&& (!(level.getBlockEntity(worldPosition.offset(connection)) instanceof ChainConveyorBlockEntity otherClbe)
			|| !otherClbe.canAcceptMorePackages()))
			return false;
		return true;
	}

	public boolean canAcceptMorePackagesFromOtherConveyor() {
		return loopingPackages.size() < ChainConveyorRegistry.chainConveyorCapacity;
	}

	@Override
	public void tick() {
		super.tick();

		if (checkInvalid && !level.isClientSide()) {
			checkInvalid = false;
			removeInvalidConnections();
		}

		float serverSpeed = 1f;
		float speed = getSpeed() / 360f;
		float radius = 1.5f;
		float distancePerTick = Math.abs(speed);
		float degreesPerTick = (speed / (Mth.PI * radius)) * 360f;
		boolean reversedPreviously = reversed;

		prepareStats();

		if (!level.isClientSide()) {
			routingTable.tick();
			if (routingTable.shouldAdvertise()) {
				for (BlockPos pos : connections)
					if (level.getBlockEntity(worldPosition.offset(pos)) instanceof ChainConveyorBlockEntity clbe)
						routingTable.advertiseTo(pos, clbe.routingTable);
				routingTable.changed = false;
				routingTable.lastUpdate = 0;
			}
		}

		if (speed == 0) {
			updateBoxWorldPositions();
			return;
		}

		if (reversedPreviously != reversed) {
			for (Entry<BlockPos, List<ChainConveyorPackage>> entry : travellingPackages.entrySet()) {
				BlockPos offset = entry.getKey();
				if (!(level.getBlockEntity(worldPosition.offset(offset)) instanceof ChainConveyorBlockEntity otherLift))
					continue;
				for (Iterator<ChainConveyorPackage> iterator = entry.getValue()
					.iterator(); iterator.hasNext(); ) {
					ChainConveyorPackage box = iterator.next();
					if (box.justFlipped)
						continue;
					box.justFlipped = true;
					float length = (float) Vec3.atLowerCornerOf(offset)
						.length() - 22 / 16f;
					box.chainPosition = length - box.chainPosition;
					otherLift.addTravellingPackage(box, offset.multiply(-1));
					iterator.remove();
				}
			}
			notifyUpdate();
		}

		for (Entry<BlockPos, List<ChainConveyorPackage>> entry : travellingPackages.entrySet()) {
			BlockPos target = entry.getKey();
			ConnectionStats stats = connectionStats.get(target);
			if (stats == null)
				continue;

			Travelling:
			for (Iterator<ChainConveyorPackage> iterator = entry.getValue()
				.iterator(); iterator.hasNext(); ) {
				ChainConveyorPackage box = iterator.next();
				box.justFlipped = false;

				float prevChainPosition = box.chainPosition;
				box.chainPosition += serverSpeed * distancePerTick;
				box.chainPosition = Math.min(stats.chainLength, box.chainPosition);

				float anticipatePosition = box.chainPosition;
				anticipatePosition += serverSpeed * distancePerTick * 4;
				anticipatePosition = Math.min(stats.chainLength, anticipatePosition);

				for (Entry<BlockPos, ConnectedPort> portEntry : travelPorts.entrySet()) {
					ConnectedPort port = portEntry.getValue();
					float chainPosition = port.chainPosition();

					if (prevChainPosition > chainPosition)
						continue;
					if (!target.equals(port.connection))
						continue;

					boolean notAtPositionYet = box.chainPosition < chainPosition;
					if (notAtPositionYet && anticipatePosition < chainPosition)
						continue;
					if (!PackageItem.matchAddress(box.item, port.filter()))
						continue;
					if (notAtPositionYet) {
						notifyPortToAnticipate(portEntry.getKey());
						continue;
					}

					if (!exportToPort(box, portEntry.getKey()))
						continue;

					iterator.remove();
					notifyUpdate();
					continue Travelling;
				}

				if (box.chainPosition < stats.chainLength)
					continue;

				// transfer to other
				if (level.getBlockEntity(worldPosition.offset(target)) instanceof ChainConveyorBlockEntity clbe) {
					box.chainPosition = wrapAngle(stats.tangentAngle + 180 + 2 * 35 * (reversed ? -1 : 1));
					clbe.addLoopingPackage(box);
					iterator.remove();
					notifyUpdate();
				}
			}
		}

		Looping:
		for (Iterator<ChainConveyorPackage> iterator = loopingPackages.iterator(); iterator.hasNext(); ) {
			ChainConveyorPackage box = iterator.next();
			box.justFlipped = false;

			float prevChainPosition = box.chainPosition;
			box.chainPosition += serverSpeed * degreesPerTick;
			box.chainPosition = wrapAngle(box.chainPosition);

			float anticipatePosition = box.chainPosition;
			anticipatePosition += serverSpeed * degreesPerTick * 4;
			anticipatePosition = wrapAngle(anticipatePosition);

			for (Entry<BlockPos, ConnectedPort> portEntry : loopPorts.entrySet()) {
				ConnectedPort port = portEntry.getValue();
				float offBranchAngle = port.chainPosition();

				boolean notAtPositionYet = !loopThresholdCrossed(box.chainPosition, prevChainPosition, offBranchAngle);
				if (notAtPositionYet && !loopThresholdCrossed(anticipatePosition, prevChainPosition, offBranchAngle))
					continue;
				if (!PackageItem.matchAddress(box.item, port.filter()))
					continue;
				if (notAtPositionYet) {
					notifyPortToAnticipate(portEntry.getKey());
					continue;
				}

				if (!exportToPort(box, portEntry.getKey()))
					continue;

				iterator.remove();
				notifyUpdate();
				continue Looping;
			}

			for (BlockPos connection : connections) {
				if (level.getBlockEntity(worldPosition.offset(connection)) instanceof ChainConveyorBlockEntity ccbe
					&& !ccbe.canAcceptMorePackagesFromOtherConveyor())
					continue;

				float offBranchAngle = connectionStats.get(connection).tangentAngle;

				if (!loopThresholdCrossed(box.chainPosition, prevChainPosition, offBranchAngle))
					continue;
				if (!routingTable.getExitFor(box.item)
					.equals(connection))
					continue;

				box.chainPosition = 0;
				addTravellingPackage(box, connection);
				iterator.remove();
				continue Looping;
			}
		}

		updateBoxWorldPositions();
	}

	public void removeInvalidConnections() {
		boolean changed = false;
		for (Iterator<BlockPos> iterator = connections.iterator(); iterator.hasNext(); ) {
			BlockPos next = iterator.next();
			BlockPos target = worldPosition.offset(next);
			if (!level.isLoaded(target))
				continue;
			if (level.getBlockEntity(target) instanceof ChainConveyorBlockEntity ccbe
				&& ccbe.connections.contains(next.multiply(-1)))
				continue;
			iterator.remove();
			changed = true;
		}
		if (changed)
			notifyUpdate();
	}

	public void notifyConnectedToValidate() {
		for (BlockPos blockPos : connections) {
			BlockPos target = worldPosition.offset(blockPos);
			if (!level.isLoaded(target))
				continue;
			if (level.getBlockEntity(target) instanceof ChainConveyorBlockEntity ccbe)
				ccbe.checkInvalid = true;
		}
	}

	/** Create's tickBoxVisuals: the physics of the dangling boxes, run by the model of the block. */
	public void tickBoxVisuals() {
		for (ChainConveyorPackage box : loopingPackages)
			tickBoxVisuals(box);
		for (Entry<BlockPos, List<ChainConveyorPackage>> entry : travellingPackages.entrySet())
			for (ChainConveyorPackage box : entry.getValue())
				tickBoxVisuals(box);
	}

	public boolean loopThresholdCrossed(float chainPosition, float prevChainPosition, float offBranchAngle) {
		int sign1 = Mth.sign(AngleHelper.getShortestAngleDiff(offBranchAngle, prevChainPosition));
		int sign2 = Mth.sign(AngleHelper.getShortestAngleDiff(offBranchAngle, chainPosition));
		boolean notCrossed = sign1 >= sign2 && !reversed || sign1 <= sign2 && reversed;
		return !notCrossed;
	}

	private boolean exportToPort(ChainConveyorPackage box, BlockPos offset) {
		BlockPos globalPos = worldPosition.offset(offset);
		if (!(level.getBlockEntity(globalPos) instanceof FrogportBlockEntity ppbe))
			return false;

		if (ppbe.isAnimationInProgress())
			return false;
		if (ppbe.isBackedUp())
			return false;

		ppbe.startAnimation(box.item, false);
		return true;
	}

	private void notifyPortToAnticipate(BlockPos offset) {
		if (level.getBlockEntity(worldPosition.offset(offset)) instanceof FrogportBlockEntity ppbe)
			ppbe.sendAnticipate();
	}

	public boolean addTravellingPackage(ChainConveyorPackage box, BlockPos connection) {
		if (!connections.contains(connection))
			return false;
		travellingPackages.computeIfAbsent(connection, $ -> new ArrayList<>())
			.add(box);
		notifyUpdate();
		return true;
	}

	@Override
	public void notifyUpdate() {
		if (level != null)
			level.blockEntityChanged(worldPosition);
		sendData();
	}

	public boolean addLoopingPackage(ChainConveyorPackage box) {
		loopingPackages.add(box);
		notifyUpdate();
		return true;
	}

	public void prepareStats() {
		float speed = getSpeed();
		if (reversed != speed < 0 && speed != 0) {
			reversed = speed < 0;
			connectionStats = null;
		}
		if (connectionStats == null) {
			connectionStats = new HashMap<>();
			connections.forEach(this::calculateConnectionStats);
		}
	}

	public void updateBoxWorldPositions() {
		prepareStats();

		for (Entry<BlockPos, List<ChainConveyorPackage>> entry : travellingPackages.entrySet()) {
			BlockPos target = entry.getKey();
			ConnectionStats stats = connectionStats.get(target);
			if (stats == null)
				continue;
			Vec3 diff = stats.end.subtract(stats.start)
				.normalize();
			for (ChainConveyorPackage box : entry.getValue()) {
				box.worldPosition = getPackagePosition(box.chainPosition, target);
				box.yaw = Mth.wrapDegrees((float) Mth.atan2(diff.x, diff.z) * Mth.RAD_TO_DEG - 90);
			}
		}

		for (ChainConveyorPackage box : loopingPackages) {
			box.worldPosition = getPackagePosition(box.chainPosition, null);
			box.yaw = Mth.wrapDegrees(box.chainPosition);
			if (reversed)
				box.yaw += 180;
		}
	}

	public Vec3 getPackagePosition(float chainPosition, @Nullable BlockPos travelTarget) {
		if (travelTarget == null)
			return Vec3.atBottomCenterOf(worldPosition)
				.add(VecHelper.rotate(new Vec3(0, 6 / 16f, 0.875), chainPosition, Axis.Y));
		prepareStats();
		ConnectionStats stats = connectionStats.get(travelTarget);
		if (stats == null)
			return Vec3.ZERO;
		Vec3 diff = stats.end.subtract(stats.start)
			.normalize();
		return stats.start.add(diff.scale(Math.min(stats.chainLength, chainPosition)));
	}

	private void tickBoxVisuals(ChainConveyorPackage box) {
		if (box.worldPosition == null)
			return;

		ChainConveyorPackagePhysicsData physicsData = box.physicsData();

		if (!physicsData.shouldTick(level.getGameTime()))
			return;

		physicsData.prevTargetPos = physicsData.targetPos;
		physicsData.prevPos = physicsData.pos;
		physicsData.prevYaw = physicsData.yaw;
		physicsData.flipped = reversed;

		if (physicsData.pos != null) {
			if (physicsData.pos.distanceToSqr(box.worldPosition) > 1.5f * 1.5f)
				physicsData.pos = box.worldPosition.add(physicsData.pos.subtract(box.worldPosition)
					.normalize()
					.scale(1.5));
			physicsData.motion = physicsData.motion.add(0, -0.25, 0)
				.scale(0.75)
				.add((box.worldPosition.subtract(physicsData.pos)).scale(0.25));
			physicsData.pos = physicsData.pos.add(physicsData.motion);
		}

		physicsData.targetPos = box.worldPosition.subtract(0, 9 / 16f, 0);

		if (physicsData.pos == null) {
			physicsData.pos = physicsData.targetPos;
			physicsData.prevPos = physicsData.targetPos;
			physicsData.prevTargetPos = physicsData.targetPos;
		}

		physicsData.yaw = AngleHelper.angleLerp(.25, physicsData.yaw, box.yaw);
	}

	private void calculateConnectionStats(BlockPos connection) {
		boolean reversed = getSpeed() < 0;
		float offBranchDistance = 35f;
		float direction = Mth.RAD_TO_DEG * (float) Mth.atan2(connection.getX(), connection.getZ());
		float angle = wrapAngle(direction - offBranchDistance * (reversed ? -1 : 1));
		float oppositeAngle = wrapAngle(angle + 180 + 2 * offBranchDistance * (reversed ? -1 : 1));

		Vec3 start = Vec3.atBottomCenterOf(worldPosition)
			.add(VecHelper.rotate(new Vec3(0, 0, 1.25), angle, Axis.Y))
			.add(0, 6 / 16f, 0);

		Vec3 end = Vec3.atBottomCenterOf(worldPosition.offset(connection))
			.add(VecHelper.rotate(new Vec3(0, 0, 1.25), oppositeAngle, Axis.Y))
			.add(0, 6 / 16f, 0);

		float length = (float) start.distanceTo(end);
		connectionStats.put(connection, new ConnectionStats(angle, length, start, end));
	}

	public boolean addConnectionTo(BlockPos target) {
		BlockPos localTarget = target.subtract(worldPosition);
		boolean added = connections.add(localTarget);
		if (added) {
			notifyUpdate();
			prepareStats();
			calculateConnectionStats(localTarget);
			updateChainShapes();
		}

		detachKinetics();
		updateSpeed = true;

		return added;
	}

	public void chainDestroyed(BlockPos target, boolean spawnDrops, boolean sendEffect) {
		int chainCount = getChainCost(target);
		if (sendEffect)
			spawnDestroyParticles(target);
		if (!spawnDrops)
			return;

		if (!forPointsAlongChains(target, chainCount,
			vec -> level.addFreshEntity(new ItemEntity(level, vec.x, vec.y, vec.z, new ItemStack(Items.IRON_CHAIN))))) {
			while (chainCount > 0) {
				Block.popResource(level, worldPosition, new ItemStack(Blocks.IRON_CHAIN.asItem(), Math.min(chainCount, 64)));
				chainCount -= 64;
			}
		}
	}

	public boolean removeConnectionTo(BlockPos target) {
		BlockPos localTarget = target.subtract(worldPosition);
		if (!connections.contains(localTarget))
			return false;

		detachKinetics();
		connections.remove(localTarget);
		prepareStats();
		connectionStats.remove(localTarget);
		List<ChainConveyorPackage> packages = travellingPackages.remove(localTarget);
		if (packages != null)
			for (ChainConveyorPackage box : packages)
				drop(box);
		notifyUpdate();
		updateChainShapes();
		updateSpeed = true;

		return true;
	}

	/** Create's updateChainShapes: the shapes players can point at, kept in {@link ChainConveyorInteractionHandler}. */
	private void updateChainShapes() {
		if (level == null || level.isClientSide())
			return;
		prepareStats();

		List<ChainConveyorShape> shapes = new ArrayList<>();
		shapes.add(new ChainConveyorBB(Vec3.atBottomCenterOf(BlockPos.ZERO)));
		for (BlockPos target : connections) {
			ConnectionStats stats = connectionStats.get(target);
			if (stats == null)
				continue;
			Vec3 localStart = stats.start.subtract(Vec3.atLowerCornerOf(worldPosition));
			Vec3 localEnd = stats.end.subtract(Vec3.atLowerCornerOf(worldPosition));
			shapes.add(new ChainConveyorOBB(target, localStart, localEnd));
		}

		ChainConveyorInteractionHandler.loadedChains.get(level)
			.put(worldPosition, shapes);
	}

	@Override
	public void remove() {
		super.remove();
		if (level == null || level.isClientSide())
			return;
		for (BlockPos blockPos : connections)
			spawnDestroyParticles(blockPos);
	}

	private void spawnDestroyParticles(BlockPos blockPos) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		forPointsAlongChains(blockPos, (int) Math.round(Vec3.atLowerCornerOf(blockPos)
				.length() * 2),
			vec -> serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_CHAIN.defaultBlockState()),
				vec.x, vec.y, vec.z, 1, 0, 0, 0, 0));
	}

	@Override
	public void clearContent() {
		connections.clear();
		travellingPackages.clear();
		loopingPackages.clear();
	}

	@Override
	public void destroy() {
		super.destroy();

		for (BlockPos blockPos : connections) {
			chainDestroyed(blockPos, !cancelDrops, false);
			if (level.getBlockEntity(worldPosition.offset(blockPos)) instanceof ChainConveyorBlockEntity clbe)
				clbe.removeConnectionTo(worldPosition);
		}

		for (ChainConveyorPackage box : loopingPackages)
			drop(box);
		for (Entry<BlockPos, List<ChainConveyorPackage>> entry : travellingPackages.entrySet())
			for (ChainConveyorPackage box : entry.getValue())
				drop(box);
	}

	public boolean forPointsAlongChains(BlockPos connection, int positions, Consumer<Vec3> callback) {
		prepareStats();
		ConnectionStats stats = connectionStats.get(connection);
		if (stats == null)
			return false;

		Vec3 start = stats.start;
		Vec3 direction = stats.end.subtract(start);
		Vec3 origin = Vec3.atCenterOf(worldPosition);
		Vec3 normal = direction.cross(new Vec3(0, 1, 0))
			.normalize();
		Vec3 offset = start.subtract(origin);
		Vec3 start2 = origin.add(offset.add(normal.scale(-2 * normal.dot(offset))));

		for (boolean firstChain : Iterate.trueAndFalse) {
			int steps = positions / 2;
			if (firstChain)
				steps += positions % 2;
			for (int i = 0; i < steps; i++)
				callback.accept((firstChain ? start : start2).add(direction.scale((0.5 + i) / steps)));
		}

		return true;
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if (level != null && !level.isClientSide())
			ChainConveyorInteractionHandler.loadedChains.get(level)
				.remove(worldPosition);
	}

	private void drop(ChainConveyorPackage box) {
		if (box.worldPosition != null)
			level.addFreshEntity(PackageEntity.fromItemStack(level, box.worldPosition.subtract(0, 0.5, 0), box.item));
	}

	@Override
	public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
		connections.forEach(p -> neighbours.add(worldPosition.offset(p)));
		return super.addPropagationLocations(block, state, neighbours);
	}

	@Override
	public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
									 boolean connectedViaAxes, boolean connectedViaCogs) {
		if (connections.contains(target.getBlockPos()
			.subtract(worldPosition))) {
			if (!(target instanceof ChainConveyorBlockEntity))
				return 0;
			return 1;
		}
		return super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);

		ValueOutput.TypedOutputList<BlockPos> connectionList = output.list("Connections", BlockPos.CODEC);
		connections.forEach(connectionList::add);

		ValueOutput.ValueOutputList travelling = output.childrenList("TravellingPackages");
		for (Entry<BlockPos, List<ChainConveyorPackage>> entry : travellingPackages.entrySet()) {
			ValueOutput entryOutput = travelling.addChild();
			entryOutput.store("Target", BlockPos.CODEC, entry.getKey());
			ValueOutput.ValueOutputList packages = entryOutput.childrenList("Packages");
			for (ChainConveyorPackage box : entry.getValue())
				box.write(packages.addChild());
		}

		ValueOutput.ValueOutputList looping = output.childrenList("LoopingPackages");
		for (ChainConveyorPackage box : loopingPackages)
			box.write(looping.addChild());
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);

		connections.clear();
		input.listOrEmpty("Connections", BlockPos.CODEC)
			.forEach(connections::add);

		travellingPackages.clear();
		for (ValueInput entry : input.childrenListOrEmpty("TravellingPackages")) {
			BlockPos target = entry.read("Target", BlockPos.CODEC)
				.orElse(null);
			if (target == null)
				continue;
			List<ChainConveyorPackage> packages = new ArrayList<>();
			for (ValueInput box : entry.childrenListOrEmpty("Packages"))
				packages.add(ChainConveyorPackage.read(box));
			travellingPackages.put(target, packages);
		}

		loopingPackages = new ArrayList<>();
		for (ValueInput box : input.childrenListOrEmpty("LoopingPackages"))
			loopingPackages.add(ChainConveyorPackage.read(box));

		connectionStats = null;
		updateBoxWorldPositions();
		updateChainShapes();
	}

	public float wrapAngle(float angle) {
		angle %= 360;
		if (angle < 0)
			angle += 360;
		return angle;
	}

	public static int getChainCost(BlockPos connection) {
		return (int) Math.max(Math.round(Vec3.atLowerCornerOf(connection)
			.length() / 2.5), 1);
	}

	public static boolean getChainsFromInventory(Player player, ItemStack chain, int cost, boolean simulate) {
		int found = 0;

		Inventory inv = player.getInventory();
		int size = inv.getNonEquipmentItems()
			.size();
		for (int j = 0; j <= size + 1; j++) {
			int i = j;
			boolean offhand = j == size + 1;
			if (j == size)
				i = inv.getSelectedSlot();
			else if (offhand)
				i = Inventory.SLOT_OFFHAND;
			else if (j == inv.getSelectedSlot())
				continue;

			ItemStack stackInSlot = inv.getItem(i);
			if (!stackInSlot.is(chain.getItem()))
				continue;
			if (found >= cost)
				continue;

			int count = stackInSlot.getCount();

			if (!simulate) {
				int remainingItems = count - Math.min(cost - found, count);
				ItemStack newItem = stackInSlot.copyWithCount(remainingItems);
				if (offhand)
					player.setItemInHand(InteractionHand.OFF_HAND, newItem);
				else
					inv.setItem(i, newItem);
			}

			found += count;
		}

		return found >= cost;
	}

	public List<ChainConveyorPackage> getLoopingPackages() {
		return loopingPackages;
	}

	public Map<BlockPos, List<ChainConveyorPackage>> getTravellingPackages() {
		return travellingPackages;
	}

}

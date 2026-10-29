package dev.createpolymer.logistics.packagePort;

import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.kinetics.chainConveyor.ChainConveyorBlockEntity;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectedPort;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorPackage;
import dev.createpolymer.registry.feature.ChainConveyorRegistry;
import dev.createpolymer.registry.feature.PackagesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where a package port sends its packages: onto a chain conveyor, or (not ported yet, since there are no trains) to
 * a train station, whose target class below keeps the saved data and the interface but cannot move a package.
 */
public abstract class PackagePortTarget {
	public static final Codec<PackagePortTarget> CODEC = AllPackagePortTargetTypes.BY_NAME_CODEC
		.dispatch(PackagePortTarget::getType, PackagePortTargetType::codec);

	public BlockPos relativePos;

	public PackagePortTarget(BlockPos relativePos) {
		this.relativePos = relativePos;
	}

	public abstract boolean export(LevelAccessor level, BlockPos portPos, ItemStack box, boolean simulate);

	public void setup(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {}

	public void register(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {}

	public void deregister(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {}

	public abstract Vec3 getExactTargetLocation(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos);

	public abstract ItemStack getIcon();

	public abstract boolean canSupport(BlockEntity be);

	public boolean depositImmediately() {
		return false;
	}

	protected abstract PackagePortTargetType getType();

	public BlockEntity be(LevelAccessor level, BlockPos portPos) {
		if (level instanceof Level l && !l.isLoaded(portPos.offset(relativePos)))
			return null;
		return level.getBlockEntity(portPos.offset(relativePos));
	}

	public static class ChainConveyorFrogportTarget extends PackagePortTarget {
		public static final MapCodec<ChainConveyorFrogportTarget> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BlockPos.CODEC.fieldOf("relative_pos").forGetter(i -> i.relativePos),
			Codec.FLOAT.fieldOf("chain_pos").forGetter(i -> i.chainPos),
			BlockPos.CODEC.optionalFieldOf("connection").forGetter(i -> Optional.ofNullable(i.connection)),
			Codec.BOOL.fieldOf("flipped").forGetter(i -> i.flipped)
		).apply(instance, ChainConveyorFrogportTarget::new));

		public float chainPos;
		@Nullable
		public BlockPos connection;
		public boolean flipped;

		public ChainConveyorFrogportTarget(BlockPos relativePos, float chainPos, Optional<BlockPos> connection, boolean flipped) {
			this(relativePos, chainPos, connection.orElse(null), flipped);
		}

		public ChainConveyorFrogportTarget(BlockPos relativePos, float chainPos, @Nullable BlockPos connection, boolean flipped) {
			super(relativePos);
			this.chainPos = chainPos;
			this.connection = connection;
			this.flipped = flipped;
		}

		@Override
		public void setup(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {
			if (be(level, portPos) instanceof ChainConveyorBlockEntity clbe)
				flipped = clbe.getSpeed() < 0;
		}

		@Override
		public ItemStack getIcon() {
			return new ItemStack(ChainConveyorRegistry.CHAIN_CONVEYOR_ITEM);
		}

		@Override
		public boolean export(LevelAccessor level, BlockPos portPos, ItemStack box, boolean simulate) {
			if (!(be(level, portPos) instanceof ChainConveyorBlockEntity clbe))
				return false;
			if (connection != null && !clbe.connections.contains(connection))
				return false;
			if (simulate)
				return clbe.getSpeed() != 0 && clbe.canAcceptPackagesFor(connection);
			ChainConveyorPackage box2 = new ChainConveyorPackage(chainPos, box.copy());
			if (connection == null)
				return clbe.addLoopingPackage(box2);
			return clbe.addTravellingPackage(box2, connection);
		}

		@Override
		public void register(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {
			if (!(be(level, portPos) instanceof ChainConveyorBlockEntity clbe))
				return;
			ChainConveyorBlockEntity actualBe = clbe;

			// Jump to opposite chain if motion reversed
			if (connection != null && clbe.getSpeed() < 0 != flipped) {
				deregister(ppbe, level, portPos);
				actualBe = ChainConveyorRegistry.CHAIN_CONVEYOR.getBlockEntity(level, clbe.getBlockPos()
					.offset(connection));
				if (actualBe == null)
					return;
				clbe.prepareStats();
				ConnectionStats stats = clbe.connectionStats.get(connection);
				if (stats != null)
					chainPos = stats.chainLength() - chainPos;
				connection = connection.multiply(-1);
				flipped = !flipped;
				relativePos = actualBe.getBlockPos()
					.subtract(portPos);
				ppbe.notifyUpdate();
			}

			if (connection != null && !actualBe.connections.contains(connection))
				return;
			String portFilter = ppbe.getFilterString();
			if (portFilter == null)
				return;
			actualBe.routingTable.receivePortInfo(portFilter, connection == null ? BlockPos.ZERO : connection);
			Map<BlockPos, ConnectedPort> portMap = connection == null ? actualBe.loopPorts : actualBe.travelPorts;
			portMap.put(relativePos.multiply(-1), new ConnectedPort(chainPos, connection, portFilter));
		}

		@Override
		public void deregister(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {
			if (!(be(level, portPos) instanceof ChainConveyorBlockEntity clbe))
				return;
			clbe.loopPorts.remove(relativePos.multiply(-1));
			clbe.travelPorts.remove(relativePos.multiply(-1));
			String portFilter = ppbe.getFilterString();
			if (portFilter == null)
				return;
			clbe.routingTable.entriesByDistance.removeIf(e -> e.endOfRoute() && e.port()
				.equals(portFilter));
			clbe.routingTable.changed = true;
		}

		@Override
		public Vec3 getExactTargetLocation(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {
			if (!(be(level, portPos) instanceof ChainConveyorBlockEntity clbe))
				return Vec3.ZERO;
			return clbe.getPackagePosition(chainPos, connection);
		}

		@Override
		public boolean canSupport(BlockEntity be) {
			return be.getType() == PackagesRegistry.PACKAGE_FROGPORT_BLOCK_ENTITY;
		}

		@Override
		protected PackagePortTargetType getType() {
			return AllPackagePortTargetTypes.CHAIN_CONVEYOR;
		}

		public static class Type implements PackagePortTargetType {
			@Override
			public MapCodec<ChainConveyorFrogportTarget> codec() {
				return CODEC;
			}
		}
	}

	public static class TrainStationFrogportTarget extends PackagePortTarget {
		public static MapCodec<TrainStationFrogportTarget> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BlockPos.CODEC.fieldOf("relative_pos").forGetter(i -> i.relativePos)
		).apply(instance, TrainStationFrogportTarget::new));

		public TrainStationFrogportTarget(BlockPos relativePos) {
			super(relativePos);
		}

		@Override
		public ItemStack getIcon() {
			return new ItemStack(Items.RAIL);
		}

		@Override
		public boolean export(LevelAccessor level, BlockPos portPos, ItemStack box, boolean simulate) {
			return false;
		}

		@Override
		public Vec3 getExactTargetLocation(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {
			return Vec3.atCenterOf(portPos.offset(relativePos));
		}

		/** Needs the train station: attaches the port to the station block entity. */
		@Override
		public void register(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {}

		/** Needs the train station: removes the port from the station block entity. */
		@Override
		public void deregister(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {}

		@Override
		public boolean depositImmediately() {
			return true;
		}

		@Override
		public boolean canSupport(BlockEntity be) {
			return be.getType() == PackagesRegistry.PACKAGE_POSTBOX_BLOCK_ENTITY;
		}

		@Override
		protected PackagePortTargetType getType() {
			return AllPackagePortTargetTypes.TRAIN_STATION;
		}

		public static class Type implements PackagePortTargetType {
			@Override
			public MapCodec<TrainStationFrogportTarget> codec() {
				return CODEC;
			}
		}
	}
}

package dev.createpolymer.logistics.packagePort;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.registry.feature.PackagesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where a package port sends its packages. Chain conveyors and train stations, the two things Create ports can
 * point at, are not ported yet: their target classes below keep the saved data and the interface, but cannot move a
 * package (see each method).
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

		/** Needs the chain conveyor: reads the speed direction of the conveyor to set {@link #flipped}. */
		@Override
		public void setup(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {}

		@Override
		public ItemStack getIcon() {
			return new ItemStack(Items.IRON_CHAIN);
		}

		/** Needs the chain conveyor: Create adds the package to the chain's looping or travelling packages. */
		@Override
		public boolean export(LevelAccessor level, BlockPos portPos, ItemStack box, boolean simulate) {
			return false;
		}

		/** Needs the chain conveyor: registers the port with the conveyor's routing table and port maps. */
		@Override
		public void register(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {}

		/** Needs the chain conveyor: removes the port from the conveyor's maps and routing table. */
		@Override
		public void deregister(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {}

		/** Needs the chain conveyor: Create asks it for the package position at {@link #chainPos}. */
		@Override
		public Vec3 getExactTargetLocation(PackagePortBlockEntity ppbe, LevelAccessor level, BlockPos portPos) {
			return Vec3.atCenterOf(portPos.offset(relativePos));
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

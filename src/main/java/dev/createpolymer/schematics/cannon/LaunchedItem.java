package dev.createpolymer.schematics.cannon;

import java.util.Arrays;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.block.BlockHelper;
import dev.createpolymer.kinetics.belt.BeltBlock;
import dev.createpolymer.kinetics.belt.BeltBlockEntity;
import dev.createpolymer.kinetics.belt.BeltBlockEntity.CasingType;
import dev.createpolymer.kinetics.belt.BeltPart;
import dev.createpolymer.kinetics.belt.BeltSlope;
import dev.createpolymer.kinetics.belt.item.BeltConnectorItem;
import dev.createpolymer.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class LaunchedItem {

	public int totalTicks;
	public int ticksRemaining;
	public BlockPos target;
	public ItemStack stack;

	private LaunchedItem(BlockPos start, BlockPos target, ItemStack stack) {
		this(target, stack, ticksForDistance(start, target), ticksForDistance(start, target));
	}

	private static int ticksForDistance(BlockPos start, BlockPos target) {
		return (int) (Math.max(10, Math.sqrt(Math.sqrt(target.distSqr(start))) * 4f));
	}

	LaunchedItem() {}

	private LaunchedItem(BlockPos target, ItemStack stack, int ticksLeft, int total) {
		this.target = target;
		this.stack = stack;
		this.totalTicks = total;
		this.ticksRemaining = ticksLeft;
	}

	public boolean update(Level world) {
		if (ticksRemaining > 0) {
			ticksRemaining--;
			return false;
		}
		if (world.isClientSide())
			return false;

		place(world);
		return true;
	}

	public void write(ValueOutput output) {
		output.putInt("TotalTicks", totalTicks);
		output.putInt("TicksLeft", ticksRemaining);
		output.store("Stack", ItemStack.OPTIONAL_CODEC, stack);
		output.store("Target", BlockPos.CODEC, target);
	}

	public static LaunchedItem fromValues(ValueInput input) {
		LaunchedItem launched = input.getInt("Length")
			.isPresent() ? new LaunchedItem.ForBelt()
				: input.read("BlockState", BlockState.CODEC)
					.isPresent() ? new LaunchedItem.ForBlockState() : new LaunchedItem.ForEntity();
		launched.read(input);
		return launched;
	}

	abstract void place(Level world);

	void read(ValueInput input) {
		target = input.read("Target", BlockPos.CODEC)
			.orElse(BlockPos.ZERO);
		ticksRemaining = input.getIntOr("TicksLeft", 0);
		totalTicks = input.getIntOr("TotalTicks", 0);
		stack = input.read("Stack", ItemStack.OPTIONAL_CODEC)
			.orElse(ItemStack.EMPTY);
	}

	public static class ForBlockState extends LaunchedItem {
		public BlockState state;
		public CompoundTag data;

		ForBlockState() {}

		public ForBlockState(BlockPos start, BlockPos target, ItemStack stack, BlockState state, CompoundTag data) {
			super(start, target, stack);
			this.state = state;
			this.data = data;
		}

		@Override
		public void write(ValueOutput output) {
			super.write(output);
			output.store("BlockState", BlockState.CODEC, state);
			if (data != null) {
				data.remove("x");
				data.remove("y");
				data.remove("z");
				data.remove("id");
				output.store("Data", CompoundTag.CODEC, data);
			}
		}

		@Override
		void read(ValueInput input) {
			super.read(input);
			state = input.read("BlockState", BlockState.CODEC)
				.orElse(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			data = input.read("Data", CompoundTag.CODEC)
				.orElse(null);
		}

		@Override
		void place(Level world) {
			BlockHelper.placeSchematicBlock(world, state, target, stack, data);
		}

	}

	public static class ForBelt extends ForBlockState {
		public int length;
		public CasingType[] casings;

		public ForBelt() {}

		@Override
		public void write(ValueOutput output) {
			super.write(output);
			output.putInt("Length", length);
			output.putIntArray("Casing", Arrays.stream(casings)
				.mapToInt(CasingType::ordinal)
				.toArray());
		}

		@Override
		void read(ValueInput input) {
			length = input.getIntOr("Length", 0);
			int[] intArray = input.getIntArray("Casing")
				.orElse(new int[0]);
			casings = new CasingType[length];
			for (int i = 0; i < casings.length; i++)
				casings[i] = i >= intArray.length ? CasingType.NONE
					: CasingType.values()[Mth.clamp(intArray[i], 0, CasingType.values().length - 1)];
			super.read(input);
		}

		public ForBelt(BlockPos start, BlockPos target, ItemStack stack, BlockState state, CasingType[] casings) {
			super(start, target, stack, state, null);
			this.casings = casings;
			this.length = casings.length;
		}

		@Override
		void place(Level world) {
			boolean isStart = state.getValue(BeltBlock.PART) == BeltPart.START;
			BlockPos offset = BeltBlock.nextSegmentPosition(state, BlockPos.ZERO, isStart);
			int i = length - 1;
			Axis axis = state.getValue(BeltBlock.SLOPE) == BeltSlope.SIDEWAYS ? Axis.Y
				: state.getValue(BeltBlock.HORIZONTAL_FACING)
					.getClockWise()
					.getAxis();
			world.setBlockAndUpdate(target, ModBlocks.SHAFT.defaultBlockState()
				.setValue(BlockStateProperties.AXIS, axis));
			BeltConnectorItem.createBelts(world, target,
				target.offset(offset.getX() * i, offset.getY() * i, offset.getZ() * i));

			for (int segment = 0; segment < length; segment++) {
				if (casings[segment] == CasingType.NONE)
					continue;
				BlockPos casingTarget =
					target.offset(offset.getX() * segment, offset.getY() * segment, offset.getZ() * segment);
				if (world.getBlockEntity(casingTarget) instanceof BeltBlockEntity bbe)
					bbe.setCasingType(casings[segment]);
			}
		}

	}

	public static class ForEntity extends LaunchedItem {
		public Entity entity;
		@Nullable
		private CompoundTag deferredTag;

		ForEntity() {}

		public ForEntity(BlockPos start, BlockPos target, ItemStack stack, Entity entity) {
			super(start, target, stack);
			this.entity = entity;
		}

		@Override
		public boolean update(Level world) {
			if (deferredTag != null && entity == null) {
				try {
					Optional<Entity> loadEntityUnchecked;
					try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(CreatePolymer.LOGGER)) {
						loadEntityUnchecked = EntityType.create(
							TagValueInput.create(reporter, world.registryAccess(), deferredTag), world,
							new net.minecraft.world.entity.EntitySpawnRequest(net.minecraft.world.entity.EntitySpawnReason.LOAD, false));
					}
					if (!loadEntityUnchecked.isPresent())
						return true;
					entity = loadEntityUnchecked.get();
				} catch (Exception var3) {
					return true;
				}
				deferredTag = null;
			}
			return super.update(world);
		}

		@Override
		public void write(ValueOutput output) {
			super.write(output);
			if (entity != null) {
				try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(),
					CreatePolymer.LOGGER)) {
					TagValueOutput tag = TagValueOutput.createWithContext(reporter, entity.registryAccess());
					entity.save(tag);
					output.store("Entity", CompoundTag.CODEC, tag.buildResult());
				}
			} else if (deferredTag != null)
				output.store("Entity", CompoundTag.CODEC, deferredTag);
		}

		@Override
		void read(ValueInput input) {
			super.read(input);
			input.read("Entity", CompoundTag.CODEC)
				.ifPresent(tag -> deferredTag = tag);
		}

		@Override
		void place(Level world) {
			if (entity != null)
				world.addFreshEntity(entity);
		}

	}

}

package dev.createpolymer.fluids;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * A fluid that only exists inside tanks, basins and other containers. It has no block, so it never appears in the
 * world. A source and its flowing counterpart are created together; they refer to each other through {@link Properties}.
 */
public class VirtualFluid extends FlowingFluid {

	/** Links a fluid to its still and flowing variants and to its bucket. Create's BaseFlowingFluid.Properties. */
	public static class Properties {
		private Supplier<? extends Fluid> source;
		private Supplier<? extends Fluid> flowing;
		private Supplier<? extends Item> bucket = () -> Items.AIR;

		public Properties source(Supplier<? extends Fluid> source) {
			this.source = source;
			return this;
		}

		public Properties flowing(Supplier<? extends Fluid> flowing) {
			this.flowing = flowing;
			return this;
		}

		public Properties bucket(Supplier<? extends Item> bucket) {
			this.bucket = bucket;
			return this;
		}
	}

	public static VirtualFluid createSource(Properties properties) {
		return new VirtualFluid(properties, true);
	}

	public static VirtualFluid createFlowing(Properties properties) {
		return new VirtualFluid(properties, false);
	}

	private final Properties properties;
	private final boolean source;

	public VirtualFluid(Properties properties, boolean source) {
		this.properties = properties;
		this.source = source;
	}

	@Override
	public Fluid getSource() {
		if (source)
			return this;
		return properties.source.get();
	}

	@Override
	public Fluid getFlowing() {
		if (source)
			return properties.flowing.get();
		return this;
	}

	@Override
	public Item getBucket() {
		return properties.bucket.get();
	}

	@Override
	public boolean isSame(Fluid other) {
		return other == properties.source.get() || other == properties.flowing.get();
	}

	@Override
	protected BlockState createLegacyBlock(FluidState state) {
		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public boolean isSource(FluidState state) {
		return source;
	}

	@Override
	public int getAmount(FluidState state) {
		return 0;
	}

	@Override
	protected boolean canConvertToSource(ServerLevel level) {
		return false;
	}

	@Override
	protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
	}

	@Override
	protected int getSlopeFindDistance(LevelReader level) {
		return 4;
	}

	@Override
	protected int getDropOff(LevelReader level) {
		return 1;
	}

	@Override
	public int getTickDelay(LevelReader level) {
		return 5;
	}

	@Override
	protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
		return false;
	}

	@Override
	protected float getExplosionResistance() {
		return 100f;
	}
}

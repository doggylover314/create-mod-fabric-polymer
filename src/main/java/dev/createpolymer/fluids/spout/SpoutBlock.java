package dev.createpolymer.fluids.spout;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.ComparatorUtil;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import dev.createpolymer.fluids.FluidBox;
import dev.createpolymer.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import dev.createpolymer.foundation.fluid.FluidHelper;
import dev.createpolymer.foundation.fluid.FluidStack;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SpoutBlock extends Block implements IWrenchable, IBE<SpoutBlockEntity>, PolymerModelBlock {

	/** Create's AllShapes.SPOUT */
	public static final VoxelShape SHAPE = Shapes.or(ShapeUtil.box(1, 2, 1, 15, 14, 15), ShapeUtil.box(2, 0, 2, 14, 16, 14));

	public SpoutBlock(Properties p_i48440_1_) {
		super(p_i48440_1_);
	}

	@Override
	protected VoxelShape getShape(BlockState p_220053_1_, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return SHAPE;
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level worldIn, BlockPos pos, Direction direction) {
		return ComparatorUtil.levelOfSmartFluidTank(worldIn, pos);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public Class<SpoutBlockEntity> getBlockEntityClass() {
		return SpoutBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SpoutBlockEntity> getBlockEntityType() {
		return TanksRegistry.SPOUT_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Create's SpoutRenderer: the body, the three nozzle parts that are squeezed up while the spout is filling, the
	 * fluid in the body and the stream of fluid that falls on the item.
	 */
	public static class Model extends BaseModel {
		private final Part[] bits = new Part[3];
		private final FluidBox tankFluid;
		private final FluidBox stream;
		private float lastSqueeze = Float.NaN;

		public Model(BlockState state) {
			super(state);
			addPart(ModelItems.block("spout/block")).apply(0);
			bits[0] = addPart(ModelItems.block("spout/top"));
			bits[1] = addPart(ModelItems.block("spout/middle"));
			bits[2] = addPart(ModelItems.block("spout/bottom"));
			for (Part bit : bits)
				bit.apply(0);
			tankFluid = new FluidBox(this);
			stream = new FluidBox(this);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof SpoutBlockEntity spout))
				return;
			SmartFluidTankBehaviour tank = spout.tank;
			if (tank == null)
				return;

			TankSegment primaryTank = tank.getPrimaryTank();
			FluidStack fluidStack = primaryTank.getRenderedFluid();
			float fluidLevel = primaryTank.getFluidLevel()
				.getValue();

			if (!fluidStack.isEmpty() && fluidLevel != 0) {
				boolean top = FluidHelper.isLighterThanAir(fluidStack);

				fluidLevel = Math.max(fluidLevel, 0.175f);
				float min = 2.5f / 16f;
				float max = min + (11 / 16f);
				float yOffset = (11 / 16f) * fluidLevel;

				float yMin = top ? max - yOffset : min;
				float yMax = top ? max : min + yOffset;
				tankFluid.show(fluidStack, min - .5f, yMin - .5f, min - .5f, max - .5f, yMax - .5f, max - .5f);
			} else
				tankFluid.hide();

			int processingTicks = spout.processingTicks;
			float processingPT = processingTicks;
			float processingProgress = 1 - (processingPT - 5) / 10;
			processingProgress = Mth.clamp(processingProgress, 0, 1);
			float radius = 0;

			if (!fluidStack.isEmpty() && processingTicks != -1) {
				radius = (float) (Math.pow(((2 * processingProgress) - 1), 2) - 1);
				float half = Math.abs(radius) / 32f;
				stream.show(fluidStack, .5f - half - .5f, -1.2f - .5f, .5f - half - .5f, .5f + half - .5f, 0f - .5f,
					.5f + half - .5f);
			} else
				stream.hide();

			float squeeze = radius;
			if (processingPT < 0)
				squeeze = 0;
			else if (processingPT < 2)
				squeeze = Mth.lerp(processingPT / 2f, 0, -1);
			else if (processingPT < 10)
				squeeze = -1;

			if (squeeze != lastSqueeze) {
				lastSqueeze = squeeze;
				float offset = 0;
				for (Part bit : bits) {
					bit.translation(0, offset, 0);
					bit.apply(1);
					offset += -3 * squeeze / 32f;
				}
			}
		}
	}
}

package dev.createpolymer.fluids.pipes;

import java.util.List;

import org.joml.Matrix4f;

import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.fluids.pipes.StraightPipeBlockEntity.StraightPipeFluidTransportBehaviour;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.fluid.FluidItemHandlers;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.logistics.filter.FilterItemStack.ListFilterItemStack;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

public class SmartFluidPipeBlockEntity extends SmartBlockEntity implements Clearable {
	private FilteringBehaviour filter;
	private ItemStack testedFilter = ItemStack.EMPTY;
	private FilterItemStack testedFilterStack = FilterItemStack.empty();

	public SmartFluidPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new SmartPipeBehaviour(this));
		behaviours.add(filter = new FilteringBehaviour(this, new SmartPipeFilterSlot()).forFluids()
			.withCallback(this::onFilterChanged));
	}

	@Override
	public void clearContent() {
		filter.setFilter(ItemStack.EMPTY);
	}

	/** Create's Block#onRemove: a replaced pipe tells the pipes and pumps around it to rebuild their network. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide())
			FluidPropagator.propagateChangedPipe(level, pos, state);
		super.preRemoveSideEffects(pos, state);
	}

	private void onFilterChanged(ItemStack newFilter) {
		if (!level.isClientSide())
			FluidPropagator.propagateChangedPipe(level, worldPosition, getBlockState());
	}

	/**
	 * Create's FilteringBehaviour#test(FluidStack). The filter item can be a fluid container (a bucket or anything
	 * with a fluid storage), or a list filter holding such items. Containers that Create empties through recipes
	 * (potions, bottles) are not understood.
	 */
	boolean testFluid(FluidStack fluid) {
		if (!filter.isActive())
			return true;
		ItemStack stack = filter.getFilter();
		if (!ItemStack.matches(stack, testedFilter)) {
			testedFilter = stack.copy();
			testedFilterStack = FilterItemStack.of(stack.copy());
		}
		return testFluid(testedFilterStack, fluid, false);
	}

	private static boolean testFluid(FilterItemStack filter, FluidStack fluid, boolean matchNBT) {
		if (filter.isEmpty())
			return true;
		if (filter instanceof ListFilterItemStack list) {
			if (list.containedItems.isEmpty())
				return true;
			for (FilterItemStack contained : list.containedItems)
				if (testFluid(contained, fluid, list.shouldRespectNBT))
					return !list.isBlacklist;
			return list.isBlacklist;
		}
		if (fluid.isEmpty())
			return false;
		FluidStack contained = fluidIn(filter.item());
		if (contained.isEmpty())
			return false;
		if (!matchNBT)
			return contained.getFluid().isSame(fluid.getFluid());
		return FluidStack.isSameFluidSameComponents(contained, fluid);
	}

	private static FluidStack fluidIn(ItemStack stack) {
		return FluidItemHandlers.get(stack.copyWithCount(1))
			.map(handler -> {
				for (int i = 0; i < handler.getTanks(); i++)
					if (!handler.getFluidInTank(i)
						.isEmpty())
						return handler.getFluidInTank(i);
				return FluidStack.EMPTY;
			})
			.orElse(FluidStack.EMPTY);
	}

	class SmartPipeBehaviour extends StraightPipeFluidTransportBehaviour {
		public SmartPipeBehaviour(SmartBlockEntity be) {
			super(be);
		}

		@Override
		public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
			if (fluid.isEmpty() || filter != null && testFluid(fluid))
				return super.canPullFluidFrom(fluid, state, direction);
			return false;
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return state.getBlock() instanceof SmartFluidPipeBlock
				&& SmartFluidPipeBlock.getPipeAxis(state) == direction.getAxis();
		}
	}

	static class SmartPipeFilterSlot extends ValueBoxTransform {
		@Override
		public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
			AttachFace face = state.getValue(SmartFluidPipeBlock.FACE);
			float y = face == AttachFace.CEILING ? 0.55f : face == AttachFace.WALL ? 11.4f : 15.45f;
			float z = face == AttachFace.CEILING ? 4.6f : face == AttachFace.WALL ? 0.55f : 4.625f;
			return VecHelper.rotateCentered(VecHelper.voxelSpace(8, y, z), angleY(state), Axis.Y);
		}

		@Override
		public float getScale() {
			return super.getScale() * 1.02f;
		}

		@Override
		public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
			AttachFace face = state.getValue(SmartFluidPipeBlock.FACE);
			ms.rotateY((float) Math.toRadians(angleY(state)))
				.rotateX((float) Math.toRadians(face == AttachFace.CEILING ? -45 : 45));
		}

		protected float angleY(BlockState state) {
			AttachFace face = state.getValue(SmartFluidPipeBlock.FACE);
			float horizontalAngle = AngleHelper.horizontalAngle(state.getValue(SmartFluidPipeBlock.FACING));
			if (face == AttachFace.WALL)
				horizontalAngle += 180;
			return horizontalAngle;
		}
	}
}

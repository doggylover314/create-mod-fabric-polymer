package dev.createpolymer.trains.observer;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

import dev.createpolymer.api.contraption.transformable.TransformableBlockEntity;
import dev.createpolymer.contraptions.StructureTransform;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlock;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.track.TrackTargetingBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TrackObserverBlockEntity extends SmartBlockEntity implements TransformableBlockEntity, Clearable {
	public TrackTargetingBehaviour<TrackObserver> edgePoint;

	private FilteringBehaviour filtering;

	public TrackObserverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(edgePoint = new TrackTargetingBehaviour<>(this, EdgePointType.OBSERVER));
		behaviours.add(filtering = createFilter().withCallback(this::onFilterChanged));
		filtering.setLabel(TrackNbt.translateDirect("logistics.train_observer.cargo_filter"));
	}

	private void onFilterChanged(ItemStack newFilter) {
		if (level.isClientSide())
			return;
		TrackObserver observer = getObserver();
		if (observer != null)
			observer.setFilterAndNotify(level, newFilter);
	}

	@Override
	public void tick() {
		super.tick();

		if (level.isClientSide())
			return;

		boolean shouldBePowered = false;
		TrackObserver observer = getObserver();
		if (observer != null)
			shouldBePowered = observer.isActivated();
		if (isBlockPowered() == shouldBePowered)
			return;

		BlockState blockState = getBlockState();
		if (blockState.hasProperty(TrackObserverBlock.POWERED))
			level.setBlock(worldPosition, blockState.setValue(TrackObserverBlock.POWERED, shouldBePowered),
				Block.UPDATE_ALL);
		DisplayLinkBlock.notifyGatherers(level, worldPosition);
	}

	@Nullable
	public TrackObserver getObserver() {
		return edgePoint.getEdgePoint();
	}

	public ItemStack getFilter() {
		return filtering.getFilter();
	}

	public boolean isBlockPowered() {
		return getBlockState().getOptionalValue(TrackObserverBlock.POWERED)
			.orElse(false);
	}

	@Override
	public void transform(BlockEntity be, StructureTransform transform) {
		edgePoint.transform(be, transform);
	}

	public FilteringBehaviour createFilter() {
		return new FilteringBehaviour(this, new ValueBoxTransform() {

			@Override
			public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
				ms.rotateX((float) Math.toRadians(90));
			}

			@Override
			public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
				return new Vec3(0.5, 15.5 / 16d, 0.5);
			}

		});
	}

	@Override
	public void clearContent() {
		filtering.setFilter(ItemStack.EMPTY);
	}
}

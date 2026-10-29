package dev.createpolymer.trains.observer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** What players see of a track observer: its box, in the powered texture while a train is passing, and the filter. */
public class TrackObserverModel extends BaseModel {

	private final Part box;
	private final FilterDisplay filter = new FilterDisplay(this);

	public TrackObserverModel(BlockState state) {
		super(state);
		box = addPart(ModelItems.block("track_observer/block"));
		showVariant(box, "track_observer", state);
	}

	@Override
	protected void onStateChanged(BlockState state) {
		showVariant(box, "track_observer", state);
	}

	@Override
	protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		if (be instanceof SmartBlockEntity observer)
			filter.update(observer);
	}
}

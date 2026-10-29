package dev.createpolymer.trains.observer;

import java.util.UUID;

import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.source.SingleLineDisplaySource;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.entity.Train;
import net.minecraft.network.chat.MutableComponent;

public class ObservedTrainNameSource extends SingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		if (!(context.getSourceBlockEntity() instanceof TrackObserverBlockEntity observerBE))
			return EMPTY_LINE;
		TrackObserver observer = observerBE.getObserver();
		if (observer == null)
			return EMPTY_LINE;
		UUID currentTrain = observer.getCurrentTrain();
		if (currentTrain == null)
			return EMPTY_LINE;
		Train train = TrackRegistry.RAILWAYS.trains.get(currentTrain);
		if (train == null)
			return EMPTY_LINE;
		return train.name.copy();
	}

	@Override
	public int getPassiveRefreshTicks() {
		return 400;
	}

	@Override
	protected String getTranslationKey() {
		return "observed_train_name";
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}

}

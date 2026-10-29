package dev.createpolymer.trains.signal;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.trains.signal.SignalBlockEntity.SignalState;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What players see of a signal (Create's SignalRenderer): the box for the signal type, and the lamp across its front,
 * which is lit while the signal is red (an invalid signal blinks).
 */
public class SignalModel extends BaseModel {

	private static final ItemStack SIGNAL_ON = ModelItems.block("track_signal/indicator_on");
	private static final ItemStack SIGNAL_OFF = ModelItems.block("track_signal/indicator_off");

	private final Part box;
	private final Part lamp;
	private boolean lit;

	public SignalModel(BlockState state) {
		super(state);
		box = addPart(ItemStack.EMPTY);
		showVariant(box, "track_signal", state);
		lamp = addPart(SIGNAL_OFF);
		lamp.apply(0);
	}

	@Override
	protected void onStateChanged(BlockState state) {
		showVariant(box, "track_signal", state);
	}

	@Override
	protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		SignalState signalState = be instanceof SignalBlockEntity signal ? signal.getState() : SignalState.INVALID;
		boolean red = signalState.isRedLight(time);
		if (red == lit)
			return;
		lit = red;
		lamp.setItem(red ? SIGNAL_ON : SIGNAL_OFF);
		lamp.element.setBrightness(red ? Brightness.FULL_BRIGHT : null);
	}
}

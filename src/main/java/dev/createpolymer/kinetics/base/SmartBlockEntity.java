package dev.createpolymer.kinetics.base;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Minimal stand-in for Create's SmartBlockEntity: lazy init, lazy ticking and
 * a read/write pair. There is no client copy of these block entities (Polymer
 * keeps them server-side), so "sendData" only bumps a counter that the
 * display-entity models watch.
 */
public abstract class SmartBlockEntity extends BlockEntity {
	private boolean initialized = false;
	private int lazyTickRate = 10;
	private int lazyTickCounter = 10;
	private int dataVersion = 0;

	public SmartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public void initialize() {
	}

	public void tick() {
		if (!initialized && hasLevel()) {
			initialize();
			initialized = true;
		}
		if (lazyTickCounter-- <= 0) {
			lazyTickCounter = lazyTickRate;
			lazyTick();
		}
	}

	public void lazyTick() {
	}

	public void setLazyTickRate(int rate) {
		this.lazyTickRate = rate;
		this.lazyTickCounter = rate;
	}

	protected void write(ValueOutput output) {
	}

	protected void read(ValueInput input) {
	}

	@Override
	protected final void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		write(output);
	}

	@Override
	protected final void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		read(input);
	}

	/** Signals the visual model that something changed. */
	public void sendData() {
		dataVersion++;
	}

	public int getDataVersion() {
		return dataVersion;
	}

	public void notifyUpdate() {
		setChanged();
		sendData();
	}

	/** Called when the block is broken or replaced (not on chunk unload). Drop contents here. */
	public void destroy() {
	}

	/** Called when the block is broken or replaced, after {@link #destroy()}. */
	public void remove() {
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		destroy();
		remove();
	}
}

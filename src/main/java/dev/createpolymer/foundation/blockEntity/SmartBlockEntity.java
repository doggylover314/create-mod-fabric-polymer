package dev.createpolymer.foundation.blockEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.createpolymer.api.schematic.nbt.PartialSafeNBT;
import dev.createpolymer.api.schematic.requirement.SpecialBlockEntityItemRequirement;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Create's SmartBlockEntity: lazy init, lazy ticking, a read/write pair and a set of
 * {@link BlockEntityBehaviour}s that tick and save with it.
 * <p>
 * There is no client copy of these block entities (Polymer keeps them server-side), so {@link #sendData()} only
 * bumps a counter that the display-entity models can watch.
 */
public abstract class SmartBlockEntity extends BlockEntity implements PartialSafeNBT, SpecialBlockEntityItemRequirement {
	private final Map<BehaviourType<?>, BlockEntityBehaviour> behaviours = new Reference2ObjectArrayMap<>();
	private boolean initialized = false;
	private boolean firstNbtRead = true;
	protected int lazyTickRate;
	protected int lazyTickCounter;
	private int dataVersion = 0;

	public SmartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(10);
		ArrayList<BlockEntityBehaviour> list = new ArrayList<>();
		addBehaviours(list);
		list.forEach(b -> behaviours.put(b.getType(), b));
	}

	/** Called from the constructor: subclass fields are not initialised yet. */
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	/** Called just before the first read. Register anything here that depends on your custom BE data. */
	public void addBehavioursDeferred(List<BlockEntityBehaviour> behaviours) {
	}

	public void initialize() {
		if (firstNbtRead)
			firstNbtRead = false;
		forEachBehaviour(BlockEntityBehaviour::initialize);
		lazyTick();
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
		forEachBehaviour(BlockEntityBehaviour::tick);
	}

	public void lazyTick() {
	}

	public void setLazyTickRate(int slowTickRate) {
		this.lazyTickRate = slowTickRate;
		this.lazyTickCounter = slowTickRate;
	}

	/** Hook only these in subclasses. Call super to save behaviours. */
	protected void write(ValueOutput output) {
		forEachBehaviour(tb -> tb.write(output));
	}

	/**
	 * What a schematic may copy of this block entity: the behaviours that are safe. Subclasses add their own settings.
	 */
	@Override
	public void writeSafe(ValueOutput output) {
		forEachBehaviour(tb -> {
			if (tb.isSafeNBT())
				tb.writeSafe(output);
		});
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state) {
		return behaviours.values()
			.stream()
			.reduce(ItemRequirement.NONE, (r, b) -> r.union(b.getRequiredItems()), ItemRequirement::union);
	}

	/** Hook only these in subclasses. Call super to load behaviours. */
	protected void read(ValueInput input) {
		forEachBehaviour(tb -> tb.read(input));
	}

	@Override
	protected final void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		write(output);
	}

	@Override
	protected final void loadAdditional(ValueInput input) {
		if (firstNbtRead) {
			firstNbtRead = false;
			ArrayList<BlockEntityBehaviour> list = new ArrayList<>();
			addBehavioursDeferred(list);
			list.forEach(b -> behaviours.put(b.getType(), b));
		}
		super.loadAdditional(input);
		read(input);
	}

	/** Signals the visual model that something changed. Create used this to sync to clients. */
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

	/** Block destroyed or replaced (not on chunk unload). Drop contents here. */
	public void destroy() {
		forEachBehaviour(BlockEntityBehaviour::destroy);
	}

	/** Block destroyed or replaced, after {@link #destroy()}. Usually detaches kinetics. */
	public void remove() {
	}

	/** Block destroyed or chunk unloaded. Usually invalidates cached handlers. */
	public void invalidate() {
		forEachBehaviour(BlockEntityBehaviour::unload);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		destroy();
		remove();
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		invalidate();
	}

	@SuppressWarnings("unchecked")
	public <T extends BlockEntityBehaviour> T getBehaviour(BehaviourType<T> type) {
		return (T) behaviours.get(type);
	}

	public void forEachBehaviour(Consumer<BlockEntityBehaviour> action) {
		for (BlockEntityBehaviour behaviour : List.copyOf(behaviours.values()))
			action.accept(behaviour);
	}

	public Collection<BlockEntityBehaviour> getAllBehaviours() {
		return behaviours.values();
	}

	public void attachBehaviourLate(BlockEntityBehaviour behaviour) {
		behaviours.put(behaviour.getType(), behaviour);
		behaviour.blockEntity = this;
		behaviour.initialize();
	}

	public void removeBehaviour(BehaviourType<?> type) {
		BlockEntityBehaviour remove = behaviours.remove(type);
		if (remove != null)
			remove.unload();
	}

	public boolean isVirtual() {
		return false;
	}

	public boolean canPlayerUse(Player player) {
		if (level != null && level.getBlockEntity(worldPosition) == this)
			return player.isWithinBlockInteractionRange(worldPosition, 1);
		return false;
	}

	public void refreshBlockState() {
		setBlockState(getLevel().getBlockState(getBlockPos()));
	}
}

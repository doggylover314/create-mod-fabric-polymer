package dev.createpolymer.foundation.blockEntity.behaviour.filtering;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform.Sided;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class SidedFilteringBehaviour extends FilteringBehaviour {

	Map<Direction, FilteringBehaviour> sidedFilters;
	private BiFunction<Direction, FilteringBehaviour, FilteringBehaviour> filterFactory;
	private Predicate<Direction> validDirections;

	public SidedFilteringBehaviour(SmartBlockEntity be, ValueBoxTransform.Sided sidedSlot,
		BiFunction<Direction, FilteringBehaviour, FilteringBehaviour> filterFactory,
		Predicate<Direction> validDirections) {
		super(be, sidedSlot);
		this.filterFactory = filterFactory;
		this.validDirections = validDirections;
		sidedFilters = new IdentityHashMap<>();
		updateFilterPresence();
	}

	@Override
	public void initialize() {
		super.initialize();
	}

	public FilteringBehaviour get(Direction side) {
		return sidedFilters.get(side);
	}

	public void updateFilterPresence() {
		Set<Direction> valid = new HashSet<>();
		for (Direction d : Iterate.directions)
			if (validDirections.test(d))
				valid.add(d);
		for (Direction d : Iterate.directions)
			if (valid.contains(d)) {
				if (!sidedFilters.containsKey(d))
					sidedFilters.put(d, filterFactory.apply(d, new FilteringBehaviour(blockEntity, slotPositioning)));
			} else if (sidedFilters.containsKey(d))
				removeFilter(d);
	}

	@Override
	public void write(ValueOutput nbt) {
		ValueOutput.ValueOutputList list = nbt.childrenList("Filters");
		for (Map.Entry<Direction, FilteringBehaviour> entry : sidedFilters.entrySet()) {
			ValueOutput compound = list.addChild();
			compound.putInt("Side", entry.getKey()
				.get3DDataValue());
			entry.getValue()
				.write(compound);
		}
		super.write(nbt);
	}

	@Override
	public void read(ValueInput nbt) {
		for (ValueInput compound : nbt.childrenListOrEmpty("Filters")) {
			Direction face = Direction.from3DDataValue(compound.getIntOr("Side", 0));
			if (sidedFilters.containsKey(face))
				sidedFilters.get(face)
					.read(compound);
		}
		super.read(nbt);
	}

	@Override
	public void tick() {
		super.tick();
		sidedFilters.values()
			.forEach(FilteringBehaviour::tick);
	}

	@Override
	public boolean setFilter(Direction side, ItemStack stack) {
		if (!sidedFilters.containsKey(side))
			return true;
		sidedFilters.get(side)
			.setFilter(stack);
		return true;
	}

	@Override
	public ItemStack getFilter(Direction side) {
		if (!sidedFilters.containsKey(side))
			return ItemStack.EMPTY;
		return sidedFilters.get(side)
			.getFilter();
	}

	public boolean test(Direction side, ItemStack stack) {
		if (!sidedFilters.containsKey(side))
			return true;
		return sidedFilters.get(side)
			.test(stack);
	}

	@Override
	public void destroy() {
		sidedFilters.values()
			.forEach(FilteringBehaviour::destroy);
		super.destroy();
	}

	@Override
	public ItemRequirement getRequiredItems() {
		return sidedFilters.values()
			.stream()
			.reduce(ItemRequirement.NONE, (a, b) -> a.union(b.getRequiredItems()), (a, b) -> a.union(b));
	}

	public void removeFilter(Direction side) {
		if (!sidedFilters.containsKey(side))
			return;
		sidedFilters.remove(side)
			.destroy();
	}

	public boolean testHit(LevelAccessor level, BlockPos pos, Direction direction, Vec3 hit) {
		ValueBoxTransform.Sided sidedPositioning = (Sided) slotPositioning;
		BlockState state = blockEntity.getBlockState();
		Vec3 localHit = hit.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
		return sidedPositioning.fromSide(direction)
			.testHit(level, pos, state, localHit);
	}

}

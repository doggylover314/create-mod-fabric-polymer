package dev.createpolymer.decoration.bracket;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.StructureTransform;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BehaviourType;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BracketedBlockEntityBehaviour extends BlockEntityBehaviour {

	public static final BehaviourType<BracketedBlockEntityBehaviour> TYPE = new BehaviourType<>();

	private BlockState bracket;

	private Predicate<BlockState> pred;

	public BracketedBlockEntityBehaviour(SmartBlockEntity be) {
		this(be, state -> true);
	}

	public BracketedBlockEntityBehaviour(SmartBlockEntity be, Predicate<BlockState> pred) {
		super(be);
		this.pred = pred;
	}

	@Override
	public BehaviourType<?> getType() {
		return TYPE;
	}

	public void applyBracket(BlockState state) {
		this.bracket = state;
		blockEntity.notifyUpdate();
		Level world = getWorld();
		if (world.isClientSide())
			return;
		blockEntity.getBlockState()
			.updateNeighbourShapes(world, getPos(), 3);
	}

	public void transformBracket(StructureTransform transform) {
		if (isBracketPresent()) {
			BlockState transformedBracket = transform.apply(bracket);
			applyBracket(transformedBracket);
		}
	}

	@Nullable
	public BlockState removeBracket(boolean inOnReplacedContext) {
		if (bracket == null) {
			return null;
		}

		BlockState removed = this.bracket;
		Level world = getWorld();
		if (!world.isClientSide())
			world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, getPos(), Block.getId(bracket));
		this.bracket = null;
		if (inOnReplacedContext) {
			blockEntity.sendData();
			return removed;
		}
		blockEntity.notifyUpdate();
		if (world.isClientSide())
			return removed;
		blockEntity.getBlockState()
			.updateNeighbourShapes(world, getPos(), 3);
		return removed;
	}

	/** Create's Block#onRemove of shafts, cogwheels and pipes: a replaced block drops its bracket. */
	@Override
	public void destroy() {
		super.destroy();
		Level world = getWorld();
		if (world == null || world.isClientSide())
			return;
		BlockState removed = removeBracket(true);
		if (removed != null)
			Block.popResource(world, getPos(), new ItemStack(removed.getBlock()));
	}

	public boolean isBracketPresent() {
		return bracket != null;
	}

	public boolean isBracketValid(BlockState bracketState) {
		return bracketState.getBlock() instanceof BracketBlock;
	}

	@Nullable
	public BlockState getBracket() {
		return bracket;
	}

	public boolean canHaveBracket() {
		return pred.test(blockEntity.getBlockState());
	}

	@Override
	public void write(ValueOutput nbt) {
		if (isBracketPresent() && isBracketValid(bracket))
			nbt.store("Bracket", BlockState.CODEC, bracket);
		super.write(nbt);
	}

	@Override
	public void read(ValueInput nbt) {
		bracket = null;
		nbt.read("Bracket", BlockState.CODEC)
			.filter(this::isBracketValid)
			.ifPresent(state -> bracket = state);
		super.read(nbt);
	}

}

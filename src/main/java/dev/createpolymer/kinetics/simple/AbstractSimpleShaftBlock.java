package dev.createpolymer.kinetics.simple;

import java.util.Optional;

import dev.createpolymer.decoration.bracket.BracketedBlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.item.IWrenchableWithBracket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's AbstractSimpleShaftBlock: the shafts and cogwheels that brackets can hold. The bracket
 * behaviour is added by {@link SimpleKineticBlockEntity} and dropped when the block is replaced.
 */
public abstract class AbstractSimpleShaftBlock extends AbstractShaftBlock implements IWrenchableWithBracket {

	public AbstractSimpleShaftBlock(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		return IWrenchableWithBracket.super.onWrenched(state, context);
	}

	@Override
	public Optional<ItemStack> removeBracket(BlockGetter world, BlockPos pos, boolean inOnReplacedContext) {
		BracketedBlockEntityBehaviour behaviour =
			BlockEntityBehaviour.get(world, pos, BracketedBlockEntityBehaviour.TYPE);
		if (behaviour == null)
			return Optional.empty();
		BlockState bracket = behaviour.removeBracket(inOnReplacedContext);
		if (bracket == null)
			return Optional.empty();
		return Optional.of(new ItemStack(bracket.getBlock()));
	}

}

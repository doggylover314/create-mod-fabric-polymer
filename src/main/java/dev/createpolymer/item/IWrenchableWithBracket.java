package dev.createpolymer.item;

import java.util.Optional;

import dev.createpolymer.fluids.FluidPropagator;
import dev.createpolymer.registry.feature.PipesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Create's IWrenchableWithBracket: the wrench takes the bracket off shafts, cogwheels and pipes before anything else. */
public interface IWrenchableWithBracket extends IWrenchable {

	Optional<ItemStack> removeBracket(BlockGetter world, BlockPos pos, boolean inOnReplacedContext);

	@Override
	default InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (tryRemoveBracket(context))
			return InteractionResult.SUCCESS;
		return IWrenchable.super.onWrenched(state, context);
	}

	default boolean tryRemoveBracket(UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Optional<ItemStack> bracket = removeBracket(world, pos, false);
		BlockState blockState = world.getBlockState(pos);
		if (bracket.isPresent()) {
			Player player = context.getPlayer();
			if (!world.isClientSide() && player != null && !player.isCreative())
				player.getInventory()
					.placeItemBackInInventory(bracket.get(), Prediction.SERVER_ONLY);
			if (!world.isClientSide() && blockState.is(PipesRegistry.FLUID_PIPE)) {
				Axis preferred = FluidPropagator.getStraightPipeAxis(blockState);
				Direction preferredDirection =
					preferred == null ? Direction.UP : Direction.get(AxisDirection.POSITIVE, preferred);
				BlockState updated = PipesRegistry.FLUID_PIPE
					.updateBlockState(blockState, preferredDirection, null, world, pos);
				if (updated != blockState)
					world.setBlockAndUpdate(pos, updated);
			}
			return true;
		}
		return false;
	}

}

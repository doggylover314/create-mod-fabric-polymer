package dev.createpolymer.kinetics.deployer;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import dev.createpolymer.logistics.depot.DepotBlock;
import dev.createpolymer.processing.basin.BasinBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's AssemblyOperatorBlockItem (MIT, The Create Team): placing a machine on top of a basin or depot
 * puts it two blocks above, facing down. Belts and weighted ejectors are not ported yet.
 */
public class AssemblyOperatorBlockItem extends PolymerBlockItem {

	public AssemblyOperatorBlockItem(Block block, Item.Properties builder) {
		super(block, builder, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		BlockPos placedOnPos = context.getClickedPos()
			.relative(context.getClickedFace()
				.getOpposite());
		Level level = context.getLevel();
		BlockState placedOnState = level
			.getBlockState(placedOnPos);
		if (operatesOn(level, placedOnPos, placedOnState) && context.getClickedFace() == Direction.UP) {
			if (level.getBlockState(placedOnPos.above(2))
				.canBeReplaced())
				context = adjustContext(context, placedOnPos);
			else
				return InteractionResult.FAIL;
		}

		return super.place(context);
	}

	protected BlockPlaceContext adjustContext(BlockPlaceContext context, BlockPos placedOnPos) {
		BlockPos up = placedOnPos.above(2);
		return new AssemblyOperatorUseContext(context.getLevel(), context.getPlayer(), context.getHand(),
			context.getItemInHand(), new BlockHitResult(new Vec3((double) up.getX() + 0.5D + (double) Direction.UP.getStepX() * 0.5D,
				(double) up.getY() + 0.5D + (double) Direction.UP.getStepY() * 0.5D,
				(double) up.getZ() + 0.5D + (double) Direction.UP.getStepZ() * 0.5D), Direction.UP, up, false));
	}

	protected boolean operatesOn(LevelReader world, BlockPos pos, BlockState placedOnState) {
		return BasinBlock.isBasin(world, pos) || placedOnState.getBlock() instanceof DepotBlock;
	}

}

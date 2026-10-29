package dev.createpolymer.item;

import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.processing.basin.BasinBlock;
import dev.createpolymer.processing.casing.CasingBlock;
import dev.createpolymer.registry.ModSounds;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported in spirit from Create's WrenchItem / IWrenchable (MIT, The Create Team):
 * right-click rotates a Create block, sneak + right-click picks it up.
 */
public class WrenchItem extends SimplePolymerItem {

	public WrenchItem(Properties settings) {
		super(settings, Items.TRIAL_KEY, true);
	}

	private static boolean isWrenchable(Block block) {
		return block instanceof KineticBlock || block instanceof BasinBlock || block instanceof CasingBlock;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (player == null || !player.mayBuild() || !isWrenchable(state.getBlock()))
			return super.useOn(context);
		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		if (player.isShiftKeyDown()) {
			if (level instanceof ServerLevel serverLevel) {
				if (!player.isCreative())
					Block.getDrops(state, serverLevel, pos, level.getBlockEntity(pos), player, context.getItemInHand())
						.forEach(stack -> player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY));
				state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, true);
				level.destroyBlock(pos, false);
				ModSounds.wrenchRemove(level, pos);
			}
			return InteractionResult.SUCCESS;
		}

		BlockState rotated = getRotatedBlockState(state, context.getClickedFace());
		if (rotated == state || !rotated.canSurvive(level, pos))
			return InteractionResult.SUCCESS;
		KineticBlockEntity.switchToBlockState(level, pos, Block.updateFromNeighbourShapes(rotated, level, pos));
		ModSounds.wrenchRotate(level, pos);
		return InteractionResult.SUCCESS;
	}

	public static BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
		Axis around = targetedFace.getAxis();
		if (state.hasProperty(RotatedPillarKineticBlock.AXIS)) {
			Axis axis = state.getValue(RotatedPillarKineticBlock.AXIS);
			if (axis == around)
				return state;
			for (Axis a : Axis.values())
				if (a != axis && a != around)
					return state.setValue(RotatedPillarKineticBlock.AXIS, a);
		}
		if (state.hasProperty(DirectionalKineticBlock.FACING)) {
			Direction facing = state.getValue(DirectionalKineticBlock.FACING);
			if (facing.getAxis() == around)
				return state.setValue(DirectionalKineticBlock.FACING, facing.getOpposite());
			return state.setValue(DirectionalKineticBlock.FACING, facing.getClockWise(around));
		}
		if (state.hasProperty(HorizontalKineticBlock.HORIZONTAL_FACING))
			return state.setValue(HorizontalKineticBlock.HORIZONTAL_FACING, state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING).getClockWise());
		return state;
	}
}

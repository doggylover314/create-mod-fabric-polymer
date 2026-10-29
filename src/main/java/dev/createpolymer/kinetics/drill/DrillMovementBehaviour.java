package dev.createpolymer.kinetics.drill;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.kinetics.base.BlockBreakingMovementBehaviour;
import dev.createpolymer.registry.feature.CrushingRegistry;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's DrillMovementBehaviour (MIT, The Create Team). Create does not let drills break track blocks;
 * trains are not ported.
 */
public class DrillMovementBehaviour extends BlockBreakingMovementBehaviour {

	@Override
	public boolean isActive(MovementContext context) {
		return super.isActive(context)
			&& !VecHelper.isVecPointingTowards(context.relativeMotion, context.state.getValue(DrillBlock.FACING)
				.getOpposite());
	}

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		return Vec3.atLowerCornerOf(context.state.getValue(DrillBlock.FACING)
			.getUnitVec3i()).scale(.65f);
	}

	@Override
	protected @Nullable DamageSource getDamageSource(Level level) {
		return CrushingRegistry.drillDamage(level);
	}

	@Override
	public boolean canBreak(Level world, BlockPos breakingPos, BlockState state) {
		return super.canBreak(world, breakingPos, state) && !state.getCollisionShape(world, breakingPos)
			.isEmpty();
	}

}

package dev.createpolymer.kinetics.deployer;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Ported from Create's AssemblyOperatorUseContext (MIT, The Create Team). */
public class AssemblyOperatorUseContext extends BlockPlaceContext {
	public AssemblyOperatorUseContext(Level level, @Nullable Player player, InteractionHand hand, ItemStack stack,
		BlockHitResult hitResult) {
		super(level, player, hand, stack, hitResult);
	}
}

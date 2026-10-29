package dev.createpolymer.decoration.copycat;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.piston.WrenchableDirectionalBlock;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's copycat_bars: a block that has no item and is never placed. Its only use in Create is the baked model a
 * copycat panel borrows to look like iron bars; here that look is drawn by {@link CopycatBarsModel}.
 */
public class CopycatBarsBlock extends WrenchableDirectionalBlock implements PolymerBlock {

	public CopycatBarsBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return Blocks.BARRIER.defaultBlockState();
	}

}

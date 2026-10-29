package dev.createpolymer.decoration;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.slidingDoor.SlidingDoorBlock;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.visual.BlockstateModels;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Ported from Create's TrainTrapdoorBlock. Players see Polymer's trapdoor states with Create's models, one client
 * state for every closed and open look; when Polymer has none left they see a vanilla iron trapdoor.
 */
public class TrainTrapdoorBlock extends TrapDoorBlock implements IWrenchable, PolymerTexturedBlock {
	private final Map<BlockState, BlockState> clientStates = new IdentityHashMap<>();

	public TrainTrapdoorBlock(BlockSetType type, Properties properties, String blockstateFile) {
		super(type, properties);
		for (BlockState state : getStateDefinition().getPossibleStates()) {
			// closed trapdoors have the half's states, open ones the facing's
			BlockModelType modelType = BlockModelType.getTrapdoor(!state.getValue(OPEN)
				? (state.getValue(HALF) == Half.TOP ? Direction.DOWN : Direction.UP) : state.getValue(FACING),
				state.getValue(WATERLOGGED));
			BlockstateModels.Variant variant = BlockstateModels.get(blockstateFile, state);
			if (variant == null)
				continue;
			BlockState client = DecorationStates.request(modelType,
				PolymerBlockModel.of(variant.model(), variant.x(), variant.y()));
			if (client != null)
				clientStates.put(state, client);
		}
	}

	public static TrainTrapdoorBlock metal(Properties properties, String blockstateFile) {
		return new TrainTrapdoorBlock(SlidingDoorBlock.TRAIN_SET_TYPE.get(), properties, blockstateFile);
	}

	public static TrainTrapdoorBlock glass(Properties properties, String blockstateFile) {
		return new TrainTrapdoorBlock(SlidingDoorBlock.GLASS_SET_TYPE.get(), properties, blockstateFile);
	}

	public boolean isTextured() {
		return !clientStates.isEmpty();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hitResult) {
		state = state.cycle(OPEN);
		level.setBlock(pos, state, UPDATE_CLIENTS);
		if (state.getValue(WATERLOGGED))
			level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		playSound(player, level, pos, state.getValue(OPEN));
		return InteractionResult.SUCCESS;
	}

	public static boolean isConnected(BlockState state, BlockState other, Direction pDirection) {
		state = state.setValue(WATERLOGGED, false)
			.setValue(POWERED, false);
		other = other.setValue(WATERLOGGED, false)
			.setValue(POWERED, false);

		boolean open = state.getValue(OPEN);
		Half half = state.getValue(HALF);
		Direction facing = state.getValue(FACING);

		if (open != other.getValue(OPEN))
			return false;
		if (!open && half == other.getValue(HALF))
			return pDirection.getAxis() != Axis.Y;
		if (!open && half != other.getValue(HALF) && pDirection.getAxis() == Axis.Y)
			return true;
		if (open && facing.getOpposite() == other.getValue(FACING) && pDirection.getAxis() == facing.getAxis())
			return true;
		if ((open ? state.setValue(HALF, Half.TOP) : state) != (open ? other.setValue(HALF, Half.TOP) : other))
			return false;

		return pDirection.getAxis() != facing.getAxis();
	}

	// ---- Polymer ----

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		BlockState client = clientStates.get(state);
		return client != null ? client : Blocks.IRON_TRAPDOOR.withPropertiesOf(state);
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.IRON_BLOCK.defaultBlockState();
	}

}

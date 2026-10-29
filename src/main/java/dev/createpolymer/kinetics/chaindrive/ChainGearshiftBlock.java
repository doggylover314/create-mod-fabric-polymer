package dev.createpolymer.kinetics.chaindrive;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.KineticsExtraRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

/** Ported from Create's ChainGearshiftBlock (MIT, The Create Team). */
public class ChainGearshiftBlock extends ChainDriveBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public ChainGearshiftBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(POWERED));
	}

	@Override
	protected void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, worldIn, pos, oldState, isMoving);
		if (oldState.getBlock() == state.getBlock())
			return;
		if (worldIn.getBlockEntity(pos) instanceof ChainGearshiftBlockEntity be)
			be.neighbourChanged();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return super.getStateForPlacement(context).setValue(POWERED, context.getLevel()
			.hasNeighborSignal(context.getClickedPos()));
	}

	@Override
	protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
		return super.areStatesKineticallyEquivalent(oldState, newState)
			&& oldState.getValue(POWERED) == newState.getValue(POWERED);
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
								   @Nullable Orientation orientation, boolean isMoving) {
		if (worldIn.isClientSide())
			return;

		if (worldIn.getBlockEntity(pos) instanceof ChainGearshiftBlockEntity be)
			be.neighbourChanged();

		boolean previouslyPowered = state.getValue(POWERED);
		if (previouslyPowered != worldIn.hasNeighborSignal(pos))
			worldIn.setBlock(pos, state.cycle(POWERED), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return KineticsExtraRegistry.ADJUSTABLE_CHAIN_GEARSHIFT_BE;
	}

	@Override
	protected String blockstateFile() {
		return "adjustable_chain_gearshift";
	}

}

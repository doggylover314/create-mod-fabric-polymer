package dev.createpolymer.redstone;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.redstone.diodes.BrassDiodeBlock;
import dev.createpolymer.util.Iterate;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

/**
 * Create's rose quartz lamp. Create's {@code shouldCheckWeakPower = false} (the lamp does not pass on power that
 * reaches it from outside) is the {@code isRedstoneConductor} property of the registration here, 26.3 has no
 * separate hook for it. Players see it as a re-skinned vanilla full block.
 */
public class RoseQuartzLampBlock extends Block implements RedstoneWrenchable, PolymerTexturedBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty POWERING = BrassDiodeBlock.POWERING;
	public static final BooleanProperty ACTIVATE = BooleanProperty.create("activate");

	private final @Nullable BlockState clientOff;
	private final @Nullable BlockState clientOn;

	public RoseQuartzLampBlock(Properties p_49795_) {
		super(p_49795_);
		registerDefaultState(defaultBlockState().setValue(POWERED, false)
			.setValue(POWERING, false)
			.setValue(ACTIVATE, false));
		clientOff = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK,
			PolymerBlockModel.of(CreatePolymer.id("block/rose_quartz_lamp")));
		clientOn = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK,
			PolymerBlockModel.of(CreatePolymer.id("block/rose_quartz_lamp_powered")));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		BlockState client = state.getValue(POWERING) ? clientOn : clientOff;
		if (client != null)
			return client;
		return (state.getValue(POWERING) ? Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true)
			: Blocks.REDSTONE_LAMP.defaultBlockState());
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.DYED_TERRACOTTA.pink().defaultBlockState();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
		return stateForPlacement.setValue(POWERED, pContext.getLevel()
			.hasNeighborSignal(pContext.getClickedPos()));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(POWERED, POWERING, ACTIVATE));
	}

	@Override
	protected void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock,
		@Nullable Orientation orientation, boolean pIsMoving) {
		if (pLevel.isClientSide())
			return;

		boolean isPowered = pState.getValue(POWERED);
		if (isPowered == pLevel.hasNeighborSignal(pPos))
			return;
		if (isPowered) {
			pLevel.setBlock(pPos, pState.cycle(POWERED), Block.UPDATE_CLIENTS);
			return;
		}

		forEachInCluster(pLevel, pPos, (currentPos, currentState) -> {
			pLevel.setBlock(currentPos, currentState.setValue(POWERING, false), Block.UPDATE_CLIENTS);
			scheduleActivation(pLevel, currentPos);
		});

		pLevel.setBlock(pPos, pState.setValue(POWERED, true)
			.setValue(POWERING, true)
			.setValue(ACTIVATE, true), Block.UPDATE_CLIENTS);
		pLevel.updateNeighborsAt(pPos, this);
		scheduleActivation(pLevel, pPos);
	}

	private void scheduleActivation(Level pLevel, BlockPos pPos) {
		if (!pLevel.getBlockTicks()
			.hasScheduledTick(pPos, this))
			pLevel.scheduleTick(pPos, this, 1);
	}

	private void forEachInCluster(Level pLevel, BlockPos pPos, BiConsumer<BlockPos, BlockState> callback) {
		List<BlockPos> frontier = new LinkedList<>();
		Set<BlockPos> visited = new HashSet<>();
		frontier.add(pPos);
		visited.add(pPos);

		while (!frontier.isEmpty()) {
			BlockPos pos = frontier.remove(0);
			for (Direction d : Iterate.directions) {
				BlockPos currentPos = pos.relative(d);
				if (currentPos.distManhattan(pPos) > 16)
					continue;
				if (!visited.add(currentPos))
					continue;
				BlockState currentState = pLevel.getBlockState(currentPos);
				if (!currentState.is(this))
					continue;
				callback.accept(currentPos, currentState);
				frontier.add(currentPos);
			}
		}
	}

	@Override
	protected boolean isSignalSource(BlockState pState) {
		return true;
	}

	@Override
	protected int getSignal(BlockState pState, BlockGetter pLevel, BlockPos pPos, Direction pDirection) {
		if (pDirection == null)
			return 0;
		BlockState toState = pLevel.getBlockState(pPos.relative(pDirection.getOpposite()));
		if (toState.is(this))
			return 0;
		if (toState.is(Blocks.COMPARATOR))
			return getDistanceToPowered(pLevel, pPos, pDirection);
		return pState.getValue(POWERING) ? 15 : 0;
	}

	private int getDistanceToPowered(BlockGetter level, BlockPos pos, Direction column) {
		MutableBlockPos currentPos = pos.mutable();
		for (int power = 15; power > 0; power--) {
			BlockState blockState = level.getBlockState(currentPos);
			if (!blockState.is(this))
				return 0;
			if (blockState.getValue(POWERING))
				return power;
			currentPos.move(column);
		}
		return 0;
	}

	@Override
	protected void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRand) {
		boolean wasPowering = pState.getValue(POWERING);
		boolean shouldBePowering = pState.getValue(ACTIVATE);

		if (wasPowering || shouldBePowering) {
			pLevel.setBlock(pPos, pState.setValue(ACTIVATE, false)
				.setValue(POWERING, shouldBePowering), Block.UPDATE_CLIENTS);
		}

		pLevel.updateNeighborsAt(pPos, this);
	}

	@Override
	public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
		return originalState.cycle(POWERING);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		InteractionResult onWrenched = RedstoneWrenchable.super.onWrenched(state, context);
		if (!onWrenched.consumesAction())
			return onWrenched;

		forEachInCluster(context.getLevel(), context.getClickedPos(), (currentPos, currentState) -> context.getLevel()
			.updateNeighborsAt(currentPos, this));
		return onWrenched;
	}

}

package dev.createpolymer.trains.signal;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.SignalsRegistry;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;

public class SignalBlock extends Block implements IBE<SignalBlockEntity>, IWrenchable, PolymerModelBlock {

	public static final EnumProperty<SignalType> TYPE = EnumProperty.create("type", SignalType.class);
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public enum SignalType implements StringRepresentable {
		ENTRY_SIGNAL, CROSS_SIGNAL;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public SignalBlock(Properties p_53182_) {
		super(p_53182_);
		registerDefaultState(defaultBlockState().setValue(TYPE, SignalType.ENTRY_SIGNAL)
			.setValue(POWERED, false));
	}

	@Override
	public Class<SignalBlockEntity> getBlockEntityClass() {
		return SignalBlockEntity.class;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(TYPE, POWERED));
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		return this.defaultBlockState()
			.setValue(POWERED, Boolean.valueOf(pContext.getLevel()
				.hasNeighborSignal(pContext.getClickedPos())));
	}

	@Override
	protected void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock,
		@Nullable Orientation orientation, boolean pIsMoving) {
		if (pLevel.isClientSide())
			return;
		boolean powered = pState.getValue(POWERED);
		boolean neighborPowered = pLevel.hasNeighborSignal(pPos);
		if (powered == neighborPowered)
			return;
		if (powered) {
			pLevel.scheduleTick(pPos, this, 4);
		} else {
			pLevel.setBlock(pPos, pState.cycle(POWERED), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRand) {
		if (pState.getValue(POWERED) && !pLevel.hasNeighborSignal(pPos))
			pLevel.setBlock(pPos, pState.cycle(POWERED), Block.UPDATE_CLIENTS);
	}

	@Override
	public BlockEntityType<? extends SignalBlockEntity> getBlockEntityType() {
		return SignalsRegistry.TRACK_SIGNAL_BLOCK_ENTITY;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		withBlockEntityDo(level, pos, ste -> {
			Player player = context.getPlayer();
			SignalBoundary signal = ste.getSignal();
			if (signal != null) {
				signal.cycleSignalType(pos);
				if (player != null)
					player.sendOverlayMessage(TrackNbt.translateDirect("track_signal.mode_change." + signal.getTypeFor(pos)
						.getSerializedName()));
			} else if (player != null)
				player.sendOverlayMessage(TrackNbt.translateDirect("track_signal.cannot_change_mode"));
		});
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState pState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState pState, Level blockAccess, BlockPos pPos, Direction direction) {
		return getBlockEntityOptional(blockAccess, pPos).filter(SignalBlockEntity::isPowered)
			.map($ -> 15)
			.orElse(0);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.POLISHED_ANDESITE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new SignalModel(state);
	}

}

package dev.createpolymer.redstone.diodes;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class BrassDiodeBlock extends AbstractDiodeBlock implements IBE<BrassDiodeBlockEntity> {

	public static final BooleanProperty POWERING = BooleanProperty.create("powering");
	public static final BooleanProperty INVERTED = BooleanProperty.create("inverted");

	public BrassDiodeBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false)
			.setValue(POWERING, false)
			.setValue(INVERTED, false));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		return toggle(level, pos, state, player, hand);
	}

	public InteractionResult toggle(Level pLevel, BlockPos pPos, BlockState pState, Player player,
									InteractionHand pHand) {
		if (!player.mayBuild())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (player.isShiftKeyDown())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (player.getItemInHand(pHand).is(ModItems.WRENCH))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (pLevel.isClientSide())
			return InteractionResult.SUCCESS;
		pLevel.setBlock(pPos, pState.cycle(INVERTED), Block.UPDATE_ALL);
		float f = !pState.getValue(INVERTED) ? 0.6F : 0.5F;
		pLevel.playSound(null, pPos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, f);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(POWERED, POWERING, FACING, INVERTED);
	}

	@Override
	protected int getOutputSignal(BlockGetter worldIn, BlockPos pos, BlockState state) {
		return state.getValue(POWERING) ^ state.getValue(INVERTED) ? 15 : 0;
	}

	@Override
	protected int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
		return blockState.getValue(FACING) == side ? this.getOutputSignal(blockAccess, pos, blockState) : 0;
	}

	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return this.getOutputSignal(level, pos, state);
	}

	@Override
	protected int getDelay(BlockState state) {
		return 2;
	}

	@Override
	protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction side) {
		if (side == null)
			return false;
		return side.getAxis() == state.getValue(FACING)
			.getAxis();
	}

	@Override
	public Class<BrassDiodeBlockEntity> getBlockEntityClass() {
		return BrassDiodeBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends BrassDiodeBlockEntity> getBlockEntityType() {
		return this == RedstoneRegistry.PULSE_TIMER ? RedstoneRegistry.PULSE_TIMER_BLOCK_ENTITY
			: this == RedstoneRegistry.PULSE_EXTENDER ? RedstoneRegistry.PULSE_EXTENDER_BLOCK_ENTITY
				: RedstoneRegistry.PULSE_REPEATER_BLOCK_ENTITY;
	}

	@Override
	protected String blockstateFile() {
		return BuiltInRegistries.BLOCK.getKey(this)
			.getPath();
	}
}

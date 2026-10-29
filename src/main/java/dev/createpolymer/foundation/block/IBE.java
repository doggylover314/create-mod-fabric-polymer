package dev.createpolymer.foundation.block;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntityTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A block with a block entity of type T. Ticks SmartBlockEntities on the server only. */
public interface IBE<T extends BlockEntity> extends EntityBlock {

	Class<T> getBlockEntityClass();

	BlockEntityType<? extends T> getBlockEntityType();

	default void withBlockEntityDo(BlockGetter world, BlockPos pos, Consumer<T> action) {
		getBlockEntityOptional(world, pos).ifPresent(action);
	}

	default InteractionResult onBlockEntityUse(BlockGetter world, BlockPos pos, Function<T, InteractionResult> action) {
		return getBlockEntityOptional(world, pos).map(action)
			.orElse(InteractionResult.PASS);
	}

	/** Replaces Create's ItemInteractionResult variant; 26.3 has a single InteractionResult. */
	default InteractionResult onBlockEntityUseItemOn(BlockGetter world, BlockPos pos, Function<T, InteractionResult> action) {
		return getBlockEntityOptional(world, pos).map(action)
			.orElse(InteractionResult.TRY_WITH_EMPTY_HAND);
	}

	default Optional<T> getBlockEntityOptional(BlockGetter world, BlockPos pos) {
		return Optional.ofNullable(getBlockEntity(world, pos));
	}

	@Override
	default BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return getBlockEntityType().create(pos, state);
	}

	@Override
	default <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
		if (level.isClientSide() || type != getBlockEntityType())
			return null;
		if (SmartBlockEntity.class.isAssignableFrom(getBlockEntityClass()))
			return new SmartBlockEntityTicker<>();
		return null;
	}

	@Nullable
	@SuppressWarnings("unchecked")
	default T getBlockEntity(BlockGetter worldIn, BlockPos pos) {
		BlockEntity blockEntity = worldIn.getBlockEntity(pos);
		if (blockEntity == null || !getBlockEntityClass().isInstance(blockEntity))
			return null;
		return (T) blockEntity;
	}
}

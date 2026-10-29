package dev.createpolymer.contraptions.bearing;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.registry.feature.BearingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class ClockworkBearingBlock extends BearingBlock implements IBE<ClockworkBearingBlockEntity> {

	public ClockworkBearingBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!player.mayBuild())
			return InteractionResult.FAIL;
		if (player.isShiftKeyDown())
			return InteractionResult.FAIL;
		if (stack.isEmpty()) {
			if (!level.isClientSide()) {
				withBlockEntityDo(level, pos, be -> {
					if (be.running) {
						be.disassemble();
						return;
					}
					be.assembleNextTick = true;
				});
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	public Class<ClockworkBearingBlockEntity> getBlockEntityClass() {
		return ClockworkBearingBlockEntity.class;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (!context.getLevel().isClientSide())
			withBlockEntityDo(context.getLevel(), context.getClickedPos(), ClockworkBearingBlockEntity::disassemble);
		return null;
	}

	@Override
	public BlockEntityType<? extends ClockworkBearingBlockEntity> getBlockEntityType() {
		return BearingRegistry.CLOCKWORK_BEARING_BLOCK_ENTITY;
	}

	@Override
	protected String bodyModel() {
		return "clockwork_bearing";
	}

	@Override
	protected BlockState particleState() {
		return Blocks.TERRACOTTA.defaultBlockState();
	}
}

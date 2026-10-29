package dev.createpolymer.equipment.bell;

import dev.createpolymer.registry.feature.ExtrasRegistry;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's haunted bell (ported from Create's HauntedBellBlock, MIT, The Create Team). Ringing it sends a soul pulse
 * through the area; placing it starts the soul effect. Its advancement is not ported.
 */
public class HauntedBellBlock extends AbstractBellBlock<HauntedBellBlockEntity> {

	public HauntedBellBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends HauntedBellBlockEntity> getBlockEntityType() {
		return ExtrasRegistry.HAUNTED_BELL_BLOCK_ENTITY;
	}

	@Override
	public Class<HauntedBellBlockEntity> getBlockEntityClass() {
		return HauntedBellBlockEntity.class;
	}

	@Override
	public void playSound(Level world, BlockPos pos) {
		world.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 4f, 1f);
	}

	@Override
	public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		if (oldState.getBlock() != this && !world.isClientSide())
			withBlockEntityDo(world, pos, hbte -> {
				hbte.effectTicks = HauntedBellBlockEntity.EFFECT_TICKS;
				hbte.sendData();
			});
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new BellModel(state, "haunted_bell");
	}

	@Override
	public BlockState particleState() {
		return Blocks.SAND.defaultBlockState();
	}
}

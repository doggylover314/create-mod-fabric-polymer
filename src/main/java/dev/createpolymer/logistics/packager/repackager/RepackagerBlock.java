package dev.createpolymer.logistics.packager.repackager;

import dev.createpolymer.logistics.packager.PackagerBlock;
import dev.createpolymer.logistics.packager.PackagerBlockEntity;
import dev.createpolymer.registry.feature.PackagesRegistry;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;

public class RepackagerBlock extends PackagerBlock {

	public RepackagerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
		return PackagesRegistry.REPACKAGER_BLOCK_ENTITY;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, true);
	}

}

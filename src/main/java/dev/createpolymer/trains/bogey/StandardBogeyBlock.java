package dev.createpolymer.trains.bogey;

import dev.createpolymer.registry.feature.TrainsRegistry;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class StandardBogeyBlock extends AbstractBogeyBlock<StandardBogeyBlockEntity>
	implements IBE<StandardBogeyBlockEntity>, ProperWaterloggedBlock {

	public StandardBogeyBlock(Properties props, BogeySizes.BogeySize size) {
		super(props, size);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
	}

	@Override
	public TrackMaterial.TrackType getTrackType(BogeyStyle style) {
		return TrackMaterial.TrackType.STANDARD;
	}

	@Override
	public double getWheelPointSpacing() {
		return 2;
	}

	@Override
	public double getWheelRadius() {
		return (size == BogeySizes.LARGE ? 12.5 : 6.5) / 16d;
	}

	@Override
	public Vec3 getConnectorAnchorOffset() {
		return new Vec3(0, 7 / 32f, 1);
	}

	@Override
	public BogeyStyle getDefaultStyle() {
		return AllBogeyStyles.STANDARD;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(TrainsRegistry.RAILWAY_CASING_ITEM);
	}

	@Override
	public Class<StandardBogeyBlockEntity> getBlockEntityClass() {
		return StandardBogeyBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends StandardBogeyBlockEntity> getBlockEntityType() {
		return TrainsRegistry.BOGEY_BLOCK_ENTITY;
	}

}

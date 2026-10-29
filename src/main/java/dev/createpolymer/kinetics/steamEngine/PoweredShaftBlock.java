package dev.createpolymer.kinetics.steamEngine;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.simple.AbstractShaftBlock;
import dev.createpolymer.kinetics.simple.ShaftBlock;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.feature.SteamRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PoweredShaftBlock extends AbstractShaftBlock {

	/** Create's AllShapes.EIGHT_VOXEL_POLE */
	private static final java.util.Map<Axis, VoxelShape> SHAPE = ShapeUtil.forAxis(ShapeUtil.box(4, 0, 4, 12, 16, 12));

	public PoweredShaftBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE.get(state.getValue(AXIS));
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return SteamRegistry.POWERED_SHAFT_BE;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!stillValid(state, level, pos))
			level.setBlock(pos, ModBlocks.SHAFT.defaultBlockState()
				.setValue(ShaftBlock.AXIS, state.getValue(AXIS))
				.setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED)), Block.UPDATE_ALL);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(ModBlocks.SHAFT);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return stillValid(state, level, pos);
	}

	public static boolean stillValid(BlockState state, LevelReader level, BlockPos pos) {
		for (Direction d : Iterate.directions) {
			if (d.getAxis() == state.getValue(AXIS))
				continue;
			BlockPos enginePos = pos.relative(d, 2);
			BlockState engineState = level.getBlockState(enginePos);
			if (!(engineState.getBlock() instanceof SteamEngineBlock))
				continue;
			if (!SteamEngineBlock.getShaftPos(engineState, enginePos)
				.equals(pos))
				continue;
			if (SteamEngineBlock.isShaftValid(engineState, state))
				return true;
		}
		return false;
	}

	public static BlockState getEquivalent(BlockState stateForPlacement) {
		return SteamRegistry.POWERED_SHAFT.defaultBlockState()
			.setValue(PoweredShaftBlock.AXIS, stateForPlacement.getValue(ShaftBlock.AXIS))
			.setValue(BlockStateProperties.WATERLOGGED, stateForPlacement.getValue(BlockStateProperties.WATERLOGGED));
	}

	// ---- Polymer ----

	/** Players see the same thin invisible hitbox as for a shaft. */
	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return ModBlocks.SHAFT.getPolymerBlockState(ModBlocks.SHAFT.defaultBlockState()
			.setValue(AXIS, state.getValue(AXIS))
			.setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED)), context);
	}

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** A shaft model that spins around the block's axis, with the extra half turn Create gives alternate powered shafts. */
	public static class Model extends BaseModel {
		private final Part part;

		public Model(BlockState state) {
			super(state);
			this.part = addPart(ModelItems.block("powered_shaft"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Axis axis = state.getValue(AXIS);
			part.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof KineticBlockEntity kbe))
				return;
			Axis axis = state.getValue(AXIS);
			float offset = Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()) + kbe.getRotationAngleOffset(axis);
			part.spin(time, kbe.getSpeed(), offset);
		}
	}
}

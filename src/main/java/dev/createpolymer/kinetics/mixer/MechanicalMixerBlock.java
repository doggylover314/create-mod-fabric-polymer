package dev.createpolymer.kinetics.mixer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.KineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.press.MechanicalPressBlock;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's MechanicalMixerBlock (MIT, The Create Team). */
public class MechanicalMixerBlock extends KineticBlock implements ICogWheel {

	public MechanicalMixerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return ModBlockEntities.MECHANICAL_MIXER;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
		return !(worldIn.getBlockEntity(pos.below()) instanceof BasinBlockEntity);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof Player)
			return MechanicalPressBlock.CASING_14PX;
		return MechanicalPressBlock.PROCESSOR_SHAPE;
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return Axis.Y;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return false;
	}

	@Override
	public SpeedLevel getMinimumRequiredSpeedLevel() {
		return SpeedLevel.MEDIUM;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part cog;
		private final Part pole;
		private final Part head;

		public Model(BlockState state) {
			super(state);
			addPart(ModelItems.block("mechanical_mixer/block")).apply(0);
			cog = addPart(ModelItems.block("cogwheel_shaftless"));
			pole = addPart(ModelItems.block("mechanical_mixer/pole"));
			head = addPart(ModelItems.block("mechanical_mixer/head"));
			cog.spinAxis(Axis.Y).apply(0);
			head.spinAxis(Axis.Y).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof MechanicalMixerBlockEntity mixer))
				return;
			cog.spin(time, mixer.getSpeed(), Orientation.cogOffset(false, Axis.Y, pos.getX(), pos.getY(), pos.getZ()));
			float offset = mixer.getRenderedHeadOffset(0);
			pole.translation(0, -offset, 0).apply(1);
			head.translation(0, -offset, 0);
			head.spinStep(time, mixer.getRenderedHeadRotationSpeed() * 2, 0);
			head.apply(1);
		}
	}
}

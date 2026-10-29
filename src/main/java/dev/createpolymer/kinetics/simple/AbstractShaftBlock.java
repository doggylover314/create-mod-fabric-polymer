package dev.createpolymer.kinetics.simple;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.bracket.BracketDisplay;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.RotatedPillarKineticBlock;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;

/** Ported from Create's AbstractShaftBlock (MIT, The Create Team). */
public abstract class AbstractShaftBlock extends RotatedPillarKineticBlock implements SimpleWaterloggedBlock {

	public AbstractShaftBlock(Properties properties) {
		super(properties);
		registerDefaultState(super.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return ModBlockEntities.SIMPLE_KINETIC;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(AXIS);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(BlockStateProperties.WATERLOGGED));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
									 Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withWater(super.getStateForPlacement(context), context);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hitResult) {
		if (player.isShiftKeyDown() || !player.mayBuild())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		BlockState encased = getEncasedState(stack, state, level, pos);
		if (encased == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!level.isClientSide()) {
			KineticBlockEntity.switchToBlockState(level, pos, encased);
			var sound = encased.getSoundType();
			level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * .8f);
		}
		return InteractionResult.SUCCESS;
	}

	/** The encased version of this block for the held casing, or null. (Create's EncasableBlock.tryEncase) */
	protected @Nullable BlockState getEncasedState(ItemStack held, BlockState state, Level level, BlockPos pos) {
		return null;
	}

	public static BlockState withWater(BlockState state, BlockPlaceContext context) {
		if (state == null || !state.hasProperty(BlockStateProperties.WATERLOGGED))
			return state;
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
		return state.setValue(BlockStateProperties.WATERLOGGED, water);
	}

	/** Model: one part that spins around the block's axis. */
	public static class SpinningAxisModel extends BaseModel {
		private final Part part;
		private final boolean largeCog;
		private final BracketDisplay bracket = new BracketDisplay(this);

		public SpinningAxisModel(BlockState state, ItemStack model, boolean largeCog) {
			super(state);
			this.largeCog = largeCog;
			this.part = addPart(model);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Axis axis = state.getValue(AXIS);
			part.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			bracket.update(be);
			if (!(be instanceof KineticBlockEntity kbe))
				return;
			Axis axis = state.getValue(AXIS);
			float offset = Orientation.cogOffset(largeCog, axis, pos.getX(), pos.getY(), pos.getZ());
			part.spin(time, kbe.getSpeed(), offset);
		}
	}

	public static ElementHolder model(BlockState state, ItemStack model, boolean largeCog) {
		return new SpinningAxisModel(state, model, largeCog);
	}
}

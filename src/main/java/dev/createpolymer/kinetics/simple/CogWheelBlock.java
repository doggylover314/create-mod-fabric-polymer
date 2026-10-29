package dev.createpolymer.kinetics.simple;

import java.util.Map;

import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.ModelItems;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's CogWheelBlock (MIT, The Create Team). Encasing is not ported yet. */
public class CogWheelBlock extends AbstractShaftBlock implements ICogWheel {
	private static final VoxelShape POLE = ShapeUtil.box(5, 0, 5, 11, 16, 11);
	public static final Map<Axis, VoxelShape> SMALL = ShapeUtil.forAxis(Shapes.or(ShapeUtil.box(2, 6, 2, 14, 10, 14), POLE));
	public static final Map<Axis, VoxelShape> LARGE = ShapeUtil.forAxis(Shapes.or(ShapeUtil.box(0, 6, 0, 16, 10, 16), POLE));

	private final boolean isLarge;

	protected CogWheelBlock(boolean large, Properties properties) {
		super(properties);
		isLarge = large;
	}

	public static CogWheelBlock small(Properties properties) {
		return new CogWheelBlock(false, properties);
	}

	public static CogWheelBlock large(Properties properties) {
		return new CogWheelBlock(true, properties);
	}

	@Override
	public boolean isLargeCog() {
		return isLarge;
	}

	@Override
	public boolean isSmallCog() {
		return !isLarge;
	}

	@Override
	public boolean isDedicatedCogWheel() {
		return true;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return (isLarge ? LARGE : SMALL).get(state.getValue(AXIS));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
		return isValidCogwheelPosition(ICogWheel.isLargeCog(state), worldIn, pos, state.getValue(AXIS));
	}

	public static boolean isValidCogwheelPosition(boolean large, LevelReader worldIn, BlockPos pos, Axis cogAxis) {
		for (Direction facing : Direction.values()) {
			if (facing.getAxis() == cogAxis)
				continue;

			BlockPos offsetPos = pos.relative(facing);
			BlockState blockState = worldIn.getBlockState(offsetPos);
			if (blockState.hasProperty(AXIS) && facing.getAxis() == blockState.getValue(AXIS))
				continue;

			if (ICogWheel.isLargeCog(blockState) || large && ICogWheel.isSmallCog(blockState))
				return false;
		}
		return true;
	}

	protected Axis getAxisForPlacement(BlockPlaceContext context) {
		if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())
			return context.getClickedFace().getAxis();

		Level world = context.getLevel();
		BlockPos placedOnPos = context.getClickedPos().relative(context.getClickedFace().getOpposite());
		BlockState placedAgainst = world.getBlockState(placedOnPos);

		Block block = placedAgainst.getBlock();
		if (ICogWheel.isSmallCog(placedAgainst))
			return ((IRotate) block).getRotationAxis(placedAgainst);

		Axis preferredAxis = getPreferredAxis(context);
		return preferredAxis != null ? preferredAxis : context.getClickedFace().getAxis();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		boolean shouldWaterlog = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
		BlockState state = this.defaultBlockState()
			.setValue(AXIS, getAxisForPlacement(context))
			.setValue(BlockStateProperties.WATERLOGGED, shouldWaterlog);
		return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.SPRUCE_PLANKS.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return model(state, ModelItems.block(isLarge ? "large_cogwheel" : "cogwheel"), isLarge);
	}
}

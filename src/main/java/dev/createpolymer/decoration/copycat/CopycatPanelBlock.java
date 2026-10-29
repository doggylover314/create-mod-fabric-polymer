package dev.createpolymer.decoration.copycat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.decoration.DecorationPlacement;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.VoxelShaper;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CopycatPanelBlock extends WaterloggedCopycatBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	/** Create's AllShapes.CASING_3PX */
	public static final VoxelShaper CASING_3PX =
		VoxelShaper.forDirectional(Block.box(0, 0, 0, 16, 3, 16), Direction.UP);

	public CopycatPanelBlock(Properties pProperties) {
		super(pProperties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
	}

	@Override
	public boolean isAcceptedRegardless(BlockState material) {
		return CopycatSpecialCases.isBarsMaterial(material) || CopycatSpecialCases.isTrapdoorMaterial(material);
	}

	@Override
	public BlockState prepareMaterial(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer,
		InteractionHand pHand, BlockHitResult pHit, BlockState material) {
		if (!CopycatSpecialCases.isTrapdoorMaterial(material))
			return super.prepareMaterial(pLevel, pPos, pState, pPlayer, pHand, pHit, material);

		Direction panelFacing = pState.getValue(FACING);
		if (panelFacing == Direction.DOWN)
			material = material.setValue(TrapDoorBlock.HALF, Half.TOP);
		if (panelFacing.getAxis() == Axis.Y)
			return material.setValue(TrapDoorBlock.FACING, pPlayer.getDirection())
				.setValue(TrapDoorBlock.OPEN, false);

		boolean clickedNearTop = pHit.getLocation().y - .5 > pPos.getY();
		return material.setValue(TrapDoorBlock.OPEN, true)
			.setValue(TrapDoorBlock.HALF, clickedNearTop ? Half.TOP : Half.BOTTOM)
			.setValue(TrapDoorBlock.FACING, panelFacing);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (!player.isShiftKeyDown() && player.mayBuild()) {
			// Create's PlacementHelper: a panel in hand goes next to this one, facing the same way
			if (stack.is(asItem()) && stack.getItem() instanceof BlockItem blockItem) {
				List<Direction> directions = orderedByDistanceExceptAxis(pos, hitResult.getLocation(),
					state.getValue(FACING)
						.getAxis(),
					dir -> level.getBlockState(pos.relative(dir))
						.canBeReplaced());

				if (!directions.isEmpty())
					DecorationPlacement.placeInWorld(level, blockItem, player, hand, pos.relative(directions.get(0)),
						s -> s.setValue(FACING, state.getValue(FACING)));
				return InteractionResult.SUCCESS;
			}
		}

		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}

	/** Catnip's IPlacementHelper.orderedByDistanceExceptAxis. */
	static List<Direction> orderedByDistanceExceptAxis(BlockPos pos, Vec3 hit, Axis axis,
		Predicate<Direction> includeDirection) {
		return orderedByDistance(pos, hit, dir -> dir.getAxis() != axis && includeDirection.test(dir));
	}

	/** Catnip's IPlacementHelper.orderedByDistance: the directions, nearest to the point that was clicked first. */
	static List<Direction> orderedByDistance(BlockPos pos, Vec3 hit, Predicate<Direction> includeDirection) {
		Vec3 centerToHit = hit.subtract(Vec3.atCenterOf(pos));
		List<Direction> directions = new ArrayList<>();
		for (Direction dir : Iterate.directions)
			if (includeDirection.test(dir))
				directions.add(dir);
		directions.sort(Comparator.comparingDouble(dir -> Vec3.atLowerCornerOf(dir.getUnitVec3i())
			.distanceTo(centerToHit)));
		return directions;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
		return stateForPlacement.setValue(FACING, pContext.getNearestLookingDirection()
			.getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACING));
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return CASING_3PX.get(pState.getValue(FACING));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	@SuppressWarnings("deprecation")
	protected BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
	}

	/** Players bump into an invisible 3 pixel plate on the side the panel sits on. */
	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return RedstoneClientStates.plate(state.getValue(FACING)
			.getOpposite(), state.getValue(WATERLOGGED));
	}

	@Override
	public eu.pb4.polymer.virtualentity.api.ElementHolder createModel(ServerLevel world, BlockPos pos,
		BlockState state) {
		return new CopycatPanelModel(state, CopycatBlock.getMaterial(world, pos));
	}

}

package dev.createpolymer.redstone.analogLever;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.registry.feature.RedstoneRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.createpolymer.visual.PolymerModelBlock;

public class AnalogLeverBlock extends FaceAttachedHorizontalDirectionalBlock
	implements IBE<AnalogLeverBlockEntity>, PolymerModelBlock {

	public AnalogLeverBlock(Properties p_i48402_1_) {
		super(p_i48402_1_);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level worldIn, BlockPos pos, Player player, BlockHitResult hit) {
		if (worldIn.isClientSide())
			return InteractionResult.SUCCESS;

		return onBlockEntityUse(worldIn, pos, be -> {
			boolean sneak = player.isShiftKeyDown();
			be.changeState(sneak);
			float f = .25f + ((be.state + 5) / 15f) * .5f;
			worldIn.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.2F, f);
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	protected int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
		return getBlockEntityOptional(blockAccess, pos).map(al -> al.state)
			.orElse(0);
	}

	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return getSignal(state, level, pos, Direction.UP);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getDirectSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
		return getConnectedDirection(blockState) == side ? getSignal(blockState, blockAccess, pos, side) : 0;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (!movedByPiston)
			updateNeighbors(state, level, pos);
	}

	static void updateNeighbors(BlockState state, Level world, BlockPos pos) {
		world.updateNeighborsAt(pos, state.getBlock());
		world.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), state.getBlock());
	}

	/** Same shape as the vanilla lever. */
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return Blocks.LEVER.defaultBlockState()
			.setValue(FACE, state.getValue(FACE))
			.setValue(FACING, state.getValue(FACING))
			.getShape(worldIn, pos, context);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(FACING, FACE));
	}

	@Override
	public Class<AnalogLeverBlockEntity> getBlockEntityClass() {
		return AnalogLeverBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends AnalogLeverBlockEntity> getBlockEntityType() {
		return RedstoneRegistry.ANALOG_LEVER_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	// ---- Polymer ----

	/** A thin plate on the block the lever is attached to, which is all the room the lever takes up. */
	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return RedstoneClientStates.plate(getConnectedDirection(state).getOpposite(), false);
	}

	@Override
	public BlockState particleState() {
		return Blocks.STONE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's analog lever visual: the handle swings up to 90 degrees further as the signal grows. */
	public static class Model extends BaseModel {
		private static final int MAX_STATE = 15;

		private final Part base;
		private final Part handle;
		private int shownState = -1;
		private int shownVersion = -1;

		public Model(BlockState state) {
			super(state);
			base = addPart(ModelItems.block("analog_lever/block"));
			handle = addPart(ModelItems.block("analog_lever/handle"));
			showBase(state);
			pose(0, 0);
		}

		private void showBase(BlockState state) {
			showVariant(base, "analog_lever", state);
			shownState = -1;
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showBase(state);
		}

		private void pose(int level, int interpolation) {
			BlockstateModels.Variant v = BlockstateModels.get("analog_lever", state);
			if (v == null)
				return;
			float angle = (float) ((level / (float) MAX_STATE) * 90 / 180 * Math.PI);
			Matrix4f m = new Matrix4f()
				.rotate(v.rotation())
				.translate(0, -7 / 16f, 0)
				.rotateX(angle)
				.translate(0, 7 / 16f, 0);
			handle.setMatrix(m, interpolation);
			shownState = level;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof AnalogLeverBlockEntity lever))
				return;
			if (lever.getState() != shownState)
				pose(lever.getState(), 4);
		}
	}

}

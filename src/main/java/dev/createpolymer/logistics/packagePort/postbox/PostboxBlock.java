package dev.createpolymer.logistics.packagePort.postbox;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PostboxBlock extends HorizontalDirectionalBlock
	implements IBE<PostboxBlockEntity>, IWrenchable, ProperWaterloggedBlock, PolymerModelBlock {

	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

	/** Create's AllShapes.POSTBOX */
	public static final VoxelShaper POSTBOX = VoxelShaper.forHorizontal(ShapeUtil.box(2, 0, 0, 14, 14, 16), Direction.SOUTH);

	protected final DyeColor color;

	public PostboxBlock(Properties properties, DyeColor color) {
		super(properties);
		this.color = color;
		registerDefaultState(defaultBlockState().setValue(OPEN, false)
			.setValue(WATERLOGGED, false));
	}

	public DyeColor getColor() {
		return color;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		Direction facing = pContext.getHorizontalDirection()
			.getOpposite();
		return withWater(super.getStateForPlacement(pContext).setValue(FACING, facing), pContext);
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader level, ScheduledTickAccess ticks, BlockPos pPos,
		Direction pDirection, BlockPos pNeighborPos, BlockState pNeighborState, RandomSource random) {
		updateWater(level, ticks, pState, pPos);
		return pState;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return POSTBOX.get(pState.getValue(FACING));
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		return POSTBOX.get(pState.getValue(FACING));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACING, OPEN, WATERLOGGED));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hitResult) {
		InteractionResult result = onBlockEntityUse(level, pos, be -> be.use(player));
		return result == InteractionResult.TRY_WITH_EMPTY_HAND ? InteractionResult.PASS : result;
	}

	@Override
	public Class<PostboxBlockEntity> getBlockEntityClass() {
		return PostboxBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PostboxBlockEntity> getBlockEntityType() {
		return PackagesRegistry.PACKAGE_POSTBOX_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState pState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState pState, Level pLevel, BlockPos pPos, Direction direction) {
		return getBlockEntityOptional(pLevel, pPos).map(pbe -> pbe.getComparatorOutput())
			.orElse(0);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.OAK_PLANKS.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, color.getSerializedName() + "_postbox");
	}

	/** Create's PostboxRenderer: the box in its dye colour, open or closed, and the flag that swings up. */
	public static class Model extends BaseModel {
		private final String blockstateFile;
		private final Part body;
		private final Part flag;
		private float lastProgress = Float.NaN;

		public Model(BlockState state, String blockstateFile) {
			super(state);
			this.blockstateFile = blockstateFile;
			body = addPart(ModelItems.block(blockstateFile + "_closed"));
			flag = addPart(ModelItems.block("package_postbox/flag"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(body, blockstateFile, state);
			lastProgress = Float.NaN;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof PostboxBlockEntity postbox))
				return;
			float progress = flagProgress(postbox, 1);
			if (progress == lastProgress)
				return;
			boolean fresh = Float.isNaN(lastProgress);
			lastProgress = progress;

			// Create: rotateCentered(180 - facing.toYRot()), then swing the flag about the hinge at its back
			float pivotY = 10 / 16f - .5f, pivotZ = 2 / 16f - .5f;
			Matrix4f m = new Matrix4f()
				.rotateY((float) Math.toRadians(180 - state.getValue(FACING).toYRot()))
				.translate(-.5f, pivotY, pivotZ)
				.rotateX((float) Math.toRadians(-progress * 90))
				.translate(.5f, -pivotY, -pivotZ);
			flag.setMatrix(m, fresh ? 0 : 1);
		}

		/** Create's PostboxRenderer.transformFlag: how far up the flag is, 0 to 1 (plus a wiggle at the top). */
		public static float flagProgress(PostboxBlockEntity be, float partialTicks) {
			float value = be.flag.getValue(partialTicks);
			float progress = (float) (Math.pow(Math.min(value * 5, 1), 2));
			if (be.flag.getChaseTarget() > 0 && !be.flag.settled() && progress == 1) {
				float wiggleProgress = (value - .2f) / .8f;
				progress += (Math.sin(wiggleProgress * (2 * Math.PI) * 4) / 8f) / Math.max(1, 8f * wiggleProgress);
			}
			return progress;
		}
	}
}

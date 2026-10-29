package dev.createpolymer.contraptions.chassis;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.bearing.BearingSounds;
import dev.createpolymer.contraptions.piston.WrenchableDirectionalBlock;
import dev.createpolymer.contraptions.render.ContraptionDisplay;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.registry.feature.BearingRegistry;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

public class StickerBlock extends WrenchableDirectionalBlock implements IBE<StickerBlockEntity>, PolymerModelBlock, ContraptionDisplay {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty EXTENDED = BlockStateProperties.EXTENDED;

	public StickerBlock(Properties p_i48415_1_) {
		super(p_i48415_1_);
		registerDefaultState(defaultBlockState().setValue(POWERED, false)
			.setValue(EXTENDED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction nearestLookingDirection = context.getNearestLookingDirection();
		boolean shouldPower = context.getLevel()
			.hasNeighborSignal(context.getClickedPos());
		Direction facing = context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown() ? nearestLookingDirection : nearestLookingDirection.getOpposite();

		return defaultBlockState().setValue(FACING, facing)
			.setValue(POWERED, shouldPower);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(POWERED, EXTENDED));
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		if (worldIn.isClientSide())
			return;

		boolean previouslyPowered = state.getValue(POWERED);
		if (previouslyPowered != worldIn.hasNeighborSignal(pos)) {
			state = state.cycle(POWERED);
			if (state.getValue(POWERED))
				state = state.cycle(EXTENDED);
			worldIn.setBlock(pos, state, Block.UPDATE_CLIENTS);
			announce(worldIn, pos, state);
		}
	}

	/**
	 * Create plays these on the client when the head arrives: the squish and the slime particles of a head that
	 * sticks to a block, and a squish when it lets go.
	 */
	private void announce(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		if (!(level.getBlockEntity(pos) instanceof StickerBlockEntity sticker) || !sticker.isAttachedToBlock())
			return;
		if (state.getValue(EXTENDED)) {
			spawnParticles(serverLevel, pos, state.getValue(FACING), true);
			BearingSounds.slimeAdded(level, pos, 0.35f, 0.75f);
		} else
			BearingSounds.slimeAdded(level, pos, 0.35f, 0.2f);
	}

	/** Create's SuperGlueItem.spawnParticles. */
	public static void spawnParticles(ServerLevel level, BlockPos pos, Direction direction, boolean fullBlock) {
		Vec3 vec = Vec3.atLowerCornerOf(direction.getUnitVec3i());
		Vec3 plane = VecHelper.axisAlingedPlaneOf(vec);
		Vec3 facePos = VecHelper.getCenterOf(pos)
			.add(vec.scale(.5f));

		float distance = fullBlock ? 1f : .25f;
		for (int i = 0; i < 20; i++) {
			Vec3 offset = VecHelper.offsetRandomly(Vec3.ZERO, level.getRandom(), distance);
			Vec3 motion = offset.multiply(plane)
				.normalize()
				.scale(.075f);
			Vec3 particlePos = facePos.add(offset.multiply(plane));
			level.sendParticles(ParticleTypes.ITEM_SLIME, particlePos.x, particlePos.y, particlePos.z, 0, motion.x,
				motion.y, motion.z, 1);
		}
	}

	@Override
	public Class<StickerBlockEntity> getBlockEntityClass() {
		return StickerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends StickerBlockEntity> getBlockEntityType() {
		return BearingRegistry.STICKER_BLOCK_ENTITY;
	}

	// Slime block stuff

	private boolean isUprightSticker(BlockGetter world, BlockPos pos) {
		BlockState blockState = world.getBlockState(pos);
		return blockState.getBlock() instanceof StickerBlock && blockState.getValue(FACING) == Direction.UP;
	}

	/**
	 * Create's sticker also bounces whoever lands on it while it faces up. Bouncing is a property of the whole block
	 * in 26.3, so only the slowdown remains.
	 */
	@Override
	public void stepOn(Level p_152431_, BlockPos p_152432_, BlockState p_152433_, Entity p_152434_) {
		double d0 = Math.abs(p_152434_.getDeltaMovement().y);
		if (d0 < 0.1D && !p_152434_.isSteppingCarefully() && isUprightSticker(p_152431_, p_152432_)) {
			double d1 = 0.4D + d0 * 0.2D;
			p_152434_.setDeltaMovement(p_152434_.getDeltaMovement()
				.multiply(d1, 1.0D, d1));
		}
		super.stepOn(p_152431_, p_152432_, p_152433_, p_152434_);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	@Override
	public List<Piece> getContraptionPieces(BlockState state) {
		Direction facing = state.getValue(FACING);
		return List.of(new Piece(ModelItems.block(bodyModel(state)), Orientation.forFacing(facing)),
			new Piece(ModelItems.block("sticker/head"), Orientation.forFacing(facing)));
	}

	private static String bodyModel(BlockState state) {
		return state.getValue(POWERED) ? "sticker/block_powered" : "sticker/block";
	}

	/** The head slides out 4 pixels when extended. */
	public static class Model extends BaseModel {
		private final Part body;
		private final Part head;
		private boolean first = true;

		public Model(BlockState state) {
			super(state);
			body = addPart(ModelItems.block(bodyModel(state)));
			head = addPart(ModelItems.block("sticker/head"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(FACING);
			body.setItem(ModelItems.block(bodyModel(state)));
			body.orientation(Orientation.forFacing(facing)).apply(0);
			float offset = state.getValue(EXTENDED) ? 4 / 16f : 0;
			head.orientation(Orientation.forFacing(facing))
				.translation(facing.getStepX() * offset, facing.getStepY() * offset, facing.getStepZ() * offset)
				.apply(first ? 0 : 3);
			first = false;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}
}

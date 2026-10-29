package dev.createpolymer.kinetics.fan;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.logistics.chute.AbstractChuteBlock;
import dev.createpolymer.registry.feature.FanRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class EncasedFanBlock extends DirectionalKineticBlock implements IBE<EncasedFanBlockEntity> {

	public EncasedFanBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, worldIn, pos, oldState, isMoving);
		blockUpdate(state, worldIn, pos);
	}

	@Override
	protected void updateIndirectNeighbourShapes(BlockState stateIn, LevelAccessor worldIn, BlockPos pos, int flags, int count) {
		super.updateIndirectNeighbourShapes(stateIn, worldIn, pos, flags, count);
		blockUpdate(stateIn, worldIn, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		blockUpdate(state, worldIn, pos);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction face = context.getClickedFace();

		BlockState placedOn = world.getBlockState(pos.relative(face.getOpposite()));
		BlockState placedOnOpposite = world.getBlockState(pos.relative(face));
		if (AbstractChuteBlock.isChute(placedOn))
			return defaultBlockState().setValue(FACING, face.getOpposite());
		if (AbstractChuteBlock.isChute(placedOnOpposite))
			return defaultBlockState().setValue(FACING, face);

		Direction preferredFacing = getPreferredFacing(context);
		if (preferredFacing == null)
			preferredFacing = context.getNearestLookingDirection();
		return defaultBlockState().setValue(FACING, context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown() ? preferredFacing : preferredFacing.getOpposite());
	}

	protected void blockUpdate(BlockState state, LevelAccessor worldIn, BlockPos pos) {
		notifyFanBlockEntity(worldIn, pos);
	}

	protected void notifyFanBlockEntity(LevelAccessor world, BlockPos pos) {
		withBlockEntityDo(world, pos, EncasedFanBlockEntity::blockInFrontChanged);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING)
			.getAxis();
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(FACING)
			.getOpposite();
	}

	@Override
	public Class<EncasedFanBlockEntity> getBlockEntityClass() {
		return EncasedFanBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends EncasedFanBlockEntity> getBlockEntityType() {
		return FanRegistry.ENCASED_FAN_BLOCK_ENTITY;
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
		private final Part casing;
		private final Part shaft;
		private final Part fan;

		public Model(BlockState state) {
			super(state);
			casing = addPart(ModelItems.block("encased_fan/block"));
			shaft = addPart(ModelItems.block("shaft_half"));
			fan = addPart(ModelItems.block("encased_fan/propeller"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(casing, "encased_fan", state);
			Direction direction = state.getValue(FACING);
			Direction opposite = direction.getOpposite();
			shaft.orientation(Orientation.fromSouth(opposite)).spinAxis(direction.getAxis()).apply(0);
			fan.orientation(Orientation.fromSouth(opposite)).spinAxis(direction.getAxis()).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof EncasedFanBlockEntity fanBE))
				return;
			Axis axis = state.getValue(FACING).getAxis();
			shaft.spin(time, fanBE.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			fan.spin(time, getFanSpeed(fanBE), 0);
		}

		/** Create's FanVisual.getFanSpeed, additionally limited so the client's interpolation cannot spin backwards. */
		private static float getFanSpeed(KineticBlockEntity be) {
			float speed = be.getSpeed() * 5;
			if (speed > 0)
				speed = Mth.clamp(speed, 80, 64 * 20);
			if (speed < 0)
				speed = Mth.clamp(speed, -64 * 20, -80);
			return Mth.clamp(speed, -MAX_VISUAL_SPEED, MAX_VISUAL_SPEED);
		}

		private static final float MAX_VISUAL_SPEED = 300;
	}

}

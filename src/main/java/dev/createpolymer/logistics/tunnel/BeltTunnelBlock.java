package dev.createpolymer.logistics.tunnel;

import java.util.EnumMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.belt.BeltBlock;
import dev.createpolymer.kinetics.belt.BeltSlope;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.registry.feature.BeltRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BeltTunnelBlock extends Block implements IBE<BeltTunnelBlockEntity>, IWrenchable, PolymerModelBlock {

	public static final Property<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
	public static final Property<Axis> HORIZONTAL_AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	public BeltTunnelBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(SHAPE, Shape.STRAIGHT));
	}

	public enum Shape implements StringRepresentable {
		STRAIGHT, WINDOW, CLOSED, T_LEFT, T_RIGHT, CROSS;

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return BeltTunnelShapes.getShape(state);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
		BlockState blockState = worldIn.getBlockState(pos.below());
		if (!isValidPositionForPlacement(state, worldIn, pos))
			return false;
		if (!blockState.getValue(BeltBlock.CASING))
			return false;
		return true;
	}

	public boolean isValidPositionForPlacement(BlockState state, LevelReader worldIn, BlockPos pos) {
		BlockState blockState = worldIn.getBlockState(pos.below());
		if (!(blockState.getBlock() instanceof BeltBlock))
			return false;
		if (blockState.getValue(BeltBlock.SLOPE) != BeltSlope.HORIZONTAL)
			return false;
		return true;
	}

	public static boolean hasWindow(BlockState state) {
		return state.getValue(SHAPE) == Shape.WINDOW || state.getValue(SHAPE) == Shape.CLOSED;
	}

	public static boolean isStraight(BlockState state) {
		return hasWindow(state) || state.getValue(SHAPE) == Shape.STRAIGHT;
	}

	public static boolean isJunction(BlockState state) {
		Shape shape = state.getValue(SHAPE);
		return shape == Shape.CROSS || shape == Shape.T_LEFT || shape == Shape.T_RIGHT;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return getTunnelState(context.getLevel(), context.getClickedPos());
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!world.isClientSide())
			withBlockEntityDo(world, pos, BeltTunnelBlockEntity::updateTunnelConnections);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader worldIn, ScheduledTickAccess ticks, BlockPos currentPos,
									 Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) {
		// Create checks this in neighborChanged, which no longer tells where the change came from
		if (facing == Direction.DOWN && !canSurvive(state, worldIn, currentPos))
			return Blocks.AIR.defaultBlockState();
		if (facing.getAxis()
			.isVertical())
			return state;
		if (!worldIn.isClientSide())
			withBlockEntityDo(worldIn, currentPos, BeltTunnelBlockEntity::updateTunnelConnections);
		BlockState tunnelState = getTunnelState(worldIn, currentPos);
		if (tunnelState.getValue(HORIZONTAL_AXIS) == state.getValue(HORIZONTAL_AXIS)) {
			if (hasWindow(tunnelState) == hasWindow(state))
				return state;
		}

		return tunnelState;
	}

	public void updateTunnel(LevelAccessor world, BlockPos pos) {
		BlockState tunnel = world.getBlockState(pos);
		BlockState newTunnel = getTunnelState(world, pos);
		if (tunnel != newTunnel && !world.isClientSide()) {
			world.setBlock(pos, newTunnel, 3);
			BlockEntity be = world.getBlockEntity(pos);
			if (be != null && (be instanceof BeltTunnelBlockEntity))
				((BeltTunnelBlockEntity) be).updateTunnelConnections();
		}
	}

	private BlockState getTunnelState(BlockGetter reader, BlockPos pos) {
		BlockState state = defaultBlockState();
		BlockState belt = reader.getBlockState(pos.below());
		if (belt.getBlock() instanceof BeltBlock)
			state = state.setValue(HORIZONTAL_AXIS, belt.getValue(BeltBlock.HORIZONTAL_FACING)
				.getAxis());
		Axis axis = state.getValue(HORIZONTAL_AXIS);

		// T and Cross
		Direction left = Direction.get(AxisDirection.POSITIVE, axis)
			.getClockWise();
		boolean onLeft = hasValidOutput(reader, pos.below(), left);
		boolean onRight = hasValidOutput(reader, pos.below(), left.getOpposite());

		if (onLeft && onRight)
			state = state.setValue(SHAPE, Shape.CROSS);
		else if (onLeft)
			state = state.setValue(SHAPE, Shape.T_LEFT);
		else if (onRight)
			state = state.setValue(SHAPE, Shape.T_RIGHT);

		if (state.getValue(SHAPE) == Shape.STRAIGHT) {
			boolean canHaveWindow = canHaveWindow(reader, pos, axis);
			if (canHaveWindow)
				state = state.setValue(SHAPE, Shape.WINDOW);
		}

		return state;
	}

	protected boolean canHaveWindow(BlockGetter reader, BlockPos pos, Axis axis) {
		Direction fw = Direction.get(AxisDirection.POSITIVE, axis);
		BlockState blockState1 = reader.getBlockState(pos.relative(fw));
		BlockState blockState2 = reader.getBlockState(pos.relative(fw.getOpposite()));

		// Replaced when funnels are ported: an extended belt funnel facing the tunnel also allows a window
		boolean funnel1 = false;
		boolean funnel2 = false;

		boolean valid1 = blockState1.getBlock() instanceof BeltTunnelBlock || funnel1;
		boolean valid2 = blockState2.getBlock() instanceof BeltTunnelBlock || funnel2;
		boolean canHaveWindow = valid1 && valid2 && !(funnel1 && funnel2);
		return canHaveWindow;
	}

	private boolean hasValidOutput(BlockGetter world, BlockPos pos, Direction side) {
		BlockState blockState = world.getBlockState(pos.relative(side));
		if (blockState.getBlock() instanceof BeltBlock)
			return blockState.getValue(BeltBlock.HORIZONTAL_FACING)
				.getAxis() == side.getAxis();
		DirectBeltInputBehaviour behaviour =
			BlockEntityBehaviour.get(world, pos.relative(side), DirectBeltInputBehaviour.TYPE);
		return behaviour != null && behaviour.canInsertFromSide(side);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (!hasWindow(state))
			return InteractionResult.PASS;

		// Toggle windows
		Shape shape = state.getValue(SHAPE);
		shape = shape == Shape.CLOSED ? Shape.WINDOW : Shape.CLOSED;
		Level world = context.getLevel();
		if (!world.isClientSide())
			world.setBlock(context.getClickedPos(), state.setValue(SHAPE, shape), Block.UPDATE_CLIENTS);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		Direction fromAxis = Direction.get(AxisDirection.POSITIVE, state.getValue(HORIZONTAL_AXIS));
		Direction rotated = rotation.rotate(fromAxis);

		return state.setValue(HORIZONTAL_AXIS, rotated.getAxis());
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(HORIZONTAL_AXIS, SHAPE);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public Class<BeltTunnelBlockEntity> getBlockEntityClass() {
		return BeltTunnelBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends BeltTunnelBlockEntity> getBlockEntityType() {
		return BeltRegistry.ANDESITE_TUNNEL_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	/** Create's blockstate file for this tunnel; the models and rotations are read from it. */
	protected String blockstateFile() {
		return "andesite_tunnel";
	}

	@Override
	public BlockState particleState() {
		return Blocks.POLISHED_ANDESITE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, blockstateFile());
	}

	/**
	 * The tunnel body from Create's blockstate, a filter display for brass tunnels and the four flap segments on
	 * every open side.
	 */
	public static class Model extends BaseModel {
		private static final float X_OFFSET = 0.075f / 16f;
		private static final float SEGMENT_STEP = -3.05f / 16f;
		private static final Vec3 PIVOT = VecHelper.voxelSpace(0, 10, 1f);
		private static final int FLAP_COUNT = 4;

		private final String blockstate;
		private final Part body;
		private final FilterDisplay filter = new FilterDisplay(this);
		private final Map<Direction, FlapParts> flaps = new EnumMap<>(Direction.class);

		public Model(BlockState state, String blockstate) {
			super(state);
			this.blockstate = blockstate;
			body = addPart(ItemStack.EMPTY);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(body, blockstate, state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof BeltTunnelBlockEntity tunnel))
				return;
			filter.update(tunnel);

			flaps.entrySet().removeIf(entry -> {
				if (tunnel.flaps.containsKey(entry.getKey()))
					return false;
				entry.getValue().remove(this);
				return true;
			});
			tunnel.flaps.forEach((direction, flapness) -> {
				FlapParts parts = flaps.computeIfAbsent(direction, d -> new FlapParts(this));
				parts.update(direction, flapness.getValue());
			});
		}

		private static class FlapParts {
			private final Part[] parts = new Part[FLAP_COUNT];
			private final float[] sent = new float[FLAP_COUNT];

			FlapParts(BaseModel holder) {
				for (int i = 0; i < FLAP_COUNT; i++) {
					parts[i] = new Part(ModelItems.block("belt_tunnel/flap"));
					holder.addElement(parts[i].element);
					sent[i] = Float.NaN;
				}
			}

			void remove(BaseModel holder) {
				for (Part part : parts)
					holder.removeElement(part.element);
			}

			void update(Direction side, float flapness) {
				float horizontalAngle = AngleHelper.horizontalAngle(side.getOpposite());
				for (int segment = 0; segment < FLAP_COUNT; segment++) {
					float angle = flapAngle(flapness, segment);
					if (sent[segment] == angle)
						continue;
					boolean first = Float.isNaN(sent[segment]);
					sent[segment] = angle;
					Matrix4f m = new Matrix4f()
						.rotateY(Mth.DEG_TO_RAD * horizontalAngle)
						.translate(-.5f, -.5f, -.5f)
						.translate(X_OFFSET, 0, 0)
						.translate((float) PIVOT.x, (float) PIVOT.y, (float) PIVOT.z)
						.rotateX(Mth.DEG_TO_RAD * angle)
						.translate((float) -PIVOT.x, (float) -PIVOT.y, (float) -PIVOT.z)
						.translate(segment * SEGMENT_STEP, 0, 0);
					parts[segment].setMatrix(m, first ? 0 : 1);
				}
			}
		}

		/** Create's FlapStuffs.flapAngle */
		private static float flapAngle(float flapness, int segment) {
			float intensity = segment == 3 ? 1.5f : segment + 1;
			float abs = Math.abs(flapness);
			float flapAngle = Mth.sin((float) ((1 - abs) * Math.PI * intensity)) * 30 * flapness;
			if (flapness < 0)
				flapAngle *= .5f;
			return flapAngle;
		}
	}

}

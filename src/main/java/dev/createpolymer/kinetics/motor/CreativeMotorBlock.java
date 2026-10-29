package dev.createpolymer.kinetics.motor;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.kinetics.base.DirectionalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's CreativeMotorBlock (MIT, The Create Team). */
public class CreativeMotorBlock extends DirectionalKineticBlock {
	public static final Map<Direction, VoxelShape> SHAPE = ShapeUtil.forDirectional(ShapeUtil.box(3, 0, 3, 13, 14, 13));

	/** Speeds the motor steps through when right-clicked (sneak to go down). */
	private static final int[] STEPS = { -256, -128, -96, -64, -48, -32, -24, -16, -12, -8, -4, -2, -1, 0, 1, 2, 4, 8, 12, 16, 24, 32, 48, 64, 96, 128, 256 };

	public CreativeMotorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return ModBlockEntities.CREATIVE_MOTOR;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPE.get(state.getValue(FACING));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction preferred = getPreferredFacing(context);
		if ((context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) || preferred == null)
			return super.getStateForPlacement(context);
		return defaultBlockState().setValue(FACING, preferred);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!(level.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor))
			return InteractionResult.PASS;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		int current = motor.getSetting();
		int next = current;
		if (player.isShiftKeyDown()) {
			for (int i = STEPS.length - 1; i >= 0; i--)
				if (STEPS[i] < current) {
					next = STEPS[i];
					break;
				}
		} else {
			for (int step : STEPS)
				if (step > current) {
					next = step;
					break;
				}
		}
		motor.setSetting(next);
		player.sendOverlayMessage(Component.translatable("create.kinetics.creative_motor.rotation_speed")
			.append(Component.literal(": " + next + " RPM").withStyle(ChatFormatting.AQUA)));
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(FACING);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING).getAxis();
	}

	@Override
	public boolean hideStressImpact() {
		return true;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected BlockState particleState() {
		return Blocks.PURPUR_BLOCK.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part body;
		private final Part shaft;
		private boolean vertical;

		public Model(BlockState state) {
			super(state);
			Direction facing = state.getValue(FACING);
			vertical = facing.getAxis() == Axis.Y;
			body = addPart(ModelItems.block(vertical ? "creative_motor/block_vertical" : "creative_motor/block"));
			shaft = addPart(ModelItems.block("shaft_half"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(FACING);
			boolean nowVertical = facing.getAxis() == Axis.Y;
			if (nowVertical != vertical) {
				vertical = nowVertical;
				body.setItem(ModelItems.block(vertical ? "creative_motor/block_vertical" : "creative_motor/block"));
			}
			// creative_motor blockstate: south=0, west=90, north=180, east=270; up=0, down=x180
			body.orientation(switch (facing) {
				case UP -> Orientation.variant(0, 0);
				case DOWN -> Orientation.variant(180, 0);
				case SOUTH -> Orientation.variant(0, 0);
				case WEST -> Orientation.variant(0, 90);
				case NORTH -> Orientation.variant(0, 180);
				case EAST -> Orientation.variant(0, 270);
			}).apply(0);
			shaft.orientation(Orientation.fromSouth(facing)).spinAxis(facing.getAxis()).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof KineticBlockEntity kbe))
				return;
			Axis axis = state.getValue(FACING).getAxis();
			shaft.spin(time, kbe.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
		}
	}
}

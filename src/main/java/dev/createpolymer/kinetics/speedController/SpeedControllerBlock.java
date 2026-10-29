package dev.createpolymer.kinetics.speedController;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.HorizontalAxisKineticBlock;
import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.simple.CogWheelBlock;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.MiscRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's SpeedControllerBlock (MIT, The Create Team). */
public class SpeedControllerBlock extends HorizontalAxisKineticBlock implements IBE<SpeedControllerBlockEntity> {

	private static final VoxelShape SHAPE = Shapes.or(ShapeUtil.box(0, 0, 0, 16, 4, 16), ShapeUtil.box(1, 1, 1, 15, 13, 15),
		ShapeUtil.box(0, 8, 0, 16, 14, 16));

	public SpeedControllerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState above = context.getLevel().getBlockState(context.getClickedPos().above());
		if (ICogWheel.isLargeCog(above) && above.getValue(BlockStateProperties.AXIS).isHorizontal())
			return defaultBlockState().setValue(HORIZONTAL_AXIS, above.getValue(BlockStateProperties.AXIS) == Axis.X ? Axis.Z : Axis.X);
		return super.getStateForPlacement(context);
	}

	/**
	 * Create's placement helper: right-clicking the controller with a large cogwheel places it on top, on the
	 * controller's axis-perpendicular position. Catnip's PlacementHelpers is not ported, so this is done directly.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (ICogWheel.isLargeCogItem(stack) && stack.getItem() instanceof BlockItem bi && ICogWheel.isDedicatedCogWheel(bi.getBlock())) {
			BlockPos newPos = pos.above();
			if (!level.getBlockState(newPos).canBeReplaced())
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			Axis newAxis = state.getValue(HORIZONTAL_AXIS) == Axis.X ? Axis.Z : Axis.X;
			if (!CogWheelBlock.isValidCogwheelPosition(true, level, newPos, newAxis))
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			BlockState cog = bi.getBlock().defaultBlockState().setValue(BlockStateProperties.AXIS, newAxis);
			level.setBlockAndUpdate(newPos, cog);
			if (!player.isCreative())
				stack.shrink(1);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public Class<SpeedControllerBlockEntity> getBlockEntityClass() {
		return SpeedControllerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SpeedControllerBlockEntity> getBlockEntityType() {
		return MiscRegistry.ROTATION_SPEED_CONTROLLER_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * The controller body is a static blockstate model. Create draws its shaft (a rotating shaft along the controller's
	 * axis) with the kinetic renderer, here a shaft part that spins with the controller's speed. The bracket that holds
	 * the large cogwheel above is shown while that cogwheel is present.
	 */
	public static class Model extends BaseModel {
		private final Part body;
		private final Part shaft;
		private final Part bracket;
		@Nullable
		private Boolean bracketShown;

		public Model(BlockState state) {
			super(state);
			body = addPart(ModelItems.block("rotation_speed_controller/block"));
			shaft = addPart(ModelItems.block("shaft"));
			bracket = addPart(ModelItems.block("rotation_speed_controller/bracket"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(body, "rotation_speed_controller", state);
			Axis axis = state.getValue(HORIZONTAL_AXIS);
			shaft.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);
			bracketShown = null;
			setBracket(false, state);
		}

		private void setBracket(boolean shown, BlockState state) {
			if (bracketShown != null && bracketShown == shown)
				return;
			bracketShown = shown;
			Matrix4f matrix = new Matrix4f();
			if (shown)
				matrix.translate(0, 1, 0).rotateY(state.getValue(HORIZONTAL_AXIS) == Axis.X ? (float) Math.PI : (float) (Math.PI / 2));
			else
				matrix.scale(0);
			bracket.setMatrix(matrix, 1);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof SpeedControllerBlockEntity controller))
				return;
			Axis axis = state.getValue(HORIZONTAL_AXIS);
			shaft.spin(time, controller.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
			controller.updateBracket();
			if (bracketShown == null || controller.hasBracket() != bracketShown)
				setBracket(controller.hasBracket(), state);
		}
	}
}

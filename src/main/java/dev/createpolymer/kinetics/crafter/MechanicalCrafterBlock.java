package dev.createpolymer.kinetics.crafter;

import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.crafter.ConnectedInputHandler.ConnectedInput;
import dev.createpolymer.kinetics.crafter.MechanicalCrafterBlockEntity.Phase;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.CrafterRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Pointing;
import dev.createpolymer.util.VecHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class MechanicalCrafterBlock extends HorizontalKineticBlock
	implements IBE<MechanicalCrafterBlockEntity>, ICogWheel, IWrenchable {

	public static final EnumProperty<Pointing> POINTING = EnumProperty.create("pointing", Pointing.class);

	public MechanicalCrafterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(POINTING, Pointing.UP));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(POINTING));
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(HORIZONTAL_FACING)
			.getAxis();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		BlockPos placedOnPos = context.getClickedPos()
			.relative(face.getOpposite());
		BlockState blockState = context.getLevel()
			.getBlockState(placedOnPos);

		if ((blockState.getBlock() != this) || (context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown())) {
			BlockState stateForPlacement = super.getStateForPlacement(context);
			Direction direction = stateForPlacement.getValue(HORIZONTAL_FACING);
			if (direction != face)
				stateForPlacement = stateForPlacement.setValue(POINTING, pointingFromFacing(face, direction));
			return stateForPlacement;
		}

		Direction otherFacing = blockState.getValue(HORIZONTAL_FACING);
		Pointing pointing = pointingFromFacing(face, otherFacing);
		return defaultBlockState().setValue(HORIZONTAL_FACING, otherFacing)
			.setValue(POINTING, pointing);
	}

	/**
	 * Create's onRemove tells the block entity when the target direction changes within the same block. The
	 * removal half of onRemove is MechanicalCrafterBlockEntity.preRemoveSideEffects.
	 */
	@Override
	protected void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
		if (oldState.getBlock() == state.getBlock()) {
			if (getTargetDirection(oldState) != getTargetDirection(state)) {
				MechanicalCrafterBlockEntity crafter = CrafterHelper.getCrafter(worldIn, pos);
				if (crafter != null)
					crafter.blockChanged();
			}
		}
		super.onPlace(state, worldIn, pos, oldState, isMoving);
	}

	public static Pointing pointingFromFacing(Direction pointingFace, Direction blockFacing) {
		boolean positive = blockFacing.getAxisDirection() == AxisDirection.POSITIVE;

		Pointing pointing = pointingFace == Direction.DOWN ? Pointing.UP : Pointing.DOWN;
		if (pointingFace == Direction.EAST)
			pointing = positive ? Pointing.LEFT : Pointing.RIGHT;
		if (pointingFace == Direction.WEST)
			pointing = positive ? Pointing.RIGHT : Pointing.LEFT;
		if (pointingFace == Direction.NORTH)
			pointing = positive ? Pointing.LEFT : Pointing.RIGHT;
		if (pointingFace == Direction.SOUTH)
			pointing = positive ? Pointing.RIGHT : Pointing.LEFT;
		return pointing;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		if (context.getClickedFace() == state.getValue(HORIZONTAL_FACING)) {
			if (!context.getLevel().isClientSide())
				KineticBlockEntity.switchToBlockState(context.getLevel(), context.getClickedPos(),
					state.cycle(POINTING));
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof MechanicalCrafterBlockEntity crafter))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		// Mechanical arm: placed against the crafter, not used on it
		if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(CreatePolymer.id("mechanical_arm")))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		boolean isHand = stack.isEmpty() && hand == InteractionHand.MAIN_HAND;
		boolean wrenched = stack.is(ModItems.WRENCH);

		if (hitResult.getDirection() == state.getValue(HORIZONTAL_FACING)) {

			if (crafter.phase != Phase.IDLE && !wrenched) {
				crafter.ejectWholeGrid();
				return InteractionResult.SUCCESS;
			}

			if (crafter.phase == Phase.IDLE && !isHand && !wrenched) {
				if (level.isClientSide())
					return InteractionResult.SUCCESS;

				if (stack.is(CrafterRegistry.CRAFTER_SLOT_COVER)) {
					if (crafter.covered)
						return InteractionResult.TRY_WITH_EMPTY_HAND;
					if (!crafter.inventory.isEmpty())
						return InteractionResult.TRY_WITH_EMPTY_HAND;
					crafter.covered = true;
					crafter.setChanged();
					crafter.sendData();
					if (!player.isCreative())
						stack.shrink(1);
					return InteractionResult.SUCCESS;
				}

				IItemHandler capability = ItemHandlerLookup.get(level, crafter.getBlockPos(), null);
				if (capability == null)
					return InteractionResult.TRY_WITH_EMPTY_HAND;
				ItemStack remainder =
					ItemHandlerHelper.insertItem(capability, stack.copy(), false);
				if (remainder.getCount() != stack.getCount())
					player.setItemInHand(hand, remainder);
				return InteractionResult.SUCCESS;
			}

			ItemStack inSlot = crafter.getInventory()
				.getItem(0);
			if (inSlot.isEmpty()) {
				if (crafter.covered && !wrenched) {
					if (level.isClientSide())
						return InteractionResult.SUCCESS;
					crafter.covered = false;
					crafter.setChanged();
					crafter.sendData();
					if (!player.isCreative())
						player.getInventory()
							.placeItemBackInInventory(new ItemStack(CrafterRegistry.CRAFTER_SLOT_COVER), Prediction.SERVER_ONLY);
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (!isHand && !ItemStack.isSameItemSameComponents(stack, inSlot))
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			player.getInventory()
				.placeItemBackInInventory(inSlot, Prediction.SERVER_ONLY);
			crafter.getInventory()
				.setStackInSlot(0, ItemStack.EMPTY);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** 26.3 does not say which neighbour changed, so the behaviour is told that its target did. */
	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		InvManipulationBehaviour behaviour = BlockEntityBehaviour.get(worldIn, pos, InvManipulationBehaviour.TYPE);
		if (behaviour != null)
			behaviour.onNeighborChanged(behaviour.getTarget()
				.getConnectedPos());
	}

	@Override
	protected BlockState particleState() {
		return Blocks.GOLD_BLOCK.defaultBlockState();
	}

	public static Direction getTargetDirection(BlockState state) {
		if (!state.is(CrafterRegistry.MECHANICAL_CRAFTER))
			return Direction.UP;
		Direction facing = state.getValue(HORIZONTAL_FACING);
		Pointing point = state.getValue(POINTING);
		Vec3 targetVec = new Vec3(0, 1, 0);
		targetVec = VecHelper.rotate(targetVec, -point.getXRotation(), Axis.Z);
		targetVec = VecHelper.rotate(targetVec, AngleHelper.horizontalAngle(facing), Axis.Y);
		return Direction.getApproximateNearest(targetVec.x, targetVec.y, targetVec.z);
	}

	public static boolean isValidTarget(Level world, BlockPos targetPos, BlockState crafterState) {
		if (!world.isLoaded(targetPos))
			return false;
		BlockState targetState = world.getBlockState(targetPos);
		if (!targetState.is(CrafterRegistry.MECHANICAL_CRAFTER))
			return false;
		if (crafterState.getValue(HORIZONTAL_FACING) != targetState.getValue(HORIZONTAL_FACING))
			return false;
		if (Math.abs(crafterState.getValue(POINTING)
			.getXRotation()
			- targetState.getValue(POINTING)
				.getXRotation()) == 180)
			return false;
		return true;
	}

	@Override
	public Class<MechanicalCrafterBlockEntity> getBlockEntityClass() {
		return MechanicalCrafterBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends MechanicalCrafterBlockEntity> getBlockEntityType() {
		return CrafterRegistry.MECHANICAL_CRAFTER_BE;
	}

	// ---- Polymer ----

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Create draws the casing as the block model, the cog as a Flywheel visual and the lid, belt and arrow in the
	 * renderer (MechanicalCrafterRenderer.renderFast). Each of them is a display entity here; the held items
	 * are MechanicalCrafterRenderer.
	 */
	public static class Model extends BaseModel {
		private final Part body;
		private final Part cog;
		private final Part lid;
		private final Part beltFrame;
		private final Part belt;
		private final Part arrow;
		private final MechanicalCrafterRenderer items;

		private boolean lidShown, beltShown, arrowShown;

		public Model(BlockState state) {
			super(state);
			body = addPart(ModelItems.block("mechanical_crafter/block"));
			cog = addPart(ModelItems.block("cogwheel_shaftless"));
			lid = addPart(ItemStack.EMPTY);
			beltFrame = addPart(ItemStack.EMPTY);
			belt = addPart(ItemStack.EMPTY);
			arrow = addPart(ItemStack.EMPTY);
			items = new MechanicalCrafterRenderer(this);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(HORIZONTAL_FACING);
			body.orientation(Orientation.forHorizontal(facing)).apply(0);

			// SHAFTLESS_COGWHEEL turns about Y; lay it down onto the facing axis
			cog.orientation(new Quaternionf()
				.rotationY(facing.getAxis() == Axis.X ? (float) (Math.PI / 2) : 0)
				.rotateX((float) (Math.PI / 2)))
				.spinAxis(facing.getAxis())
				.apply(0);

			// MechanicalCrafterRenderer.renderAndTransform
			Quaternionf rotation = new Quaternionf()
				.rotationY((float) Math.toRadians(AngleHelper.horizontalAngle(facing) + 90))
				.rotateX((float) Math.toRadians(state.getValue(POINTING)
					.getXRotation()));
			lid.orientation(rotation).apply(0);
			beltFrame.orientation(rotation).apply(0);
			belt.orientation(rotation).apply(0);
			arrow.orientation(rotation).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof MechanicalCrafterBlockEntity crafter))
				return;

			Axis axis = state.getValue(HORIZONTAL_FACING)
				.getAxis();
			cog.spin(time, crafter.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));

			boolean lidVisible = (crafter.covered || crafter.phase != Phase.IDLE) && crafter.phase != Phase.CRAFTING
				&& crafter.phase != Phase.INSERTING;
			boolean validTarget = isValidTarget(level, pos.relative(getTargetDirection(state)), state);

			if (lidVisible != lidShown) {
				lidShown = lidVisible;
				lid.setItem(lidVisible ? ModelItems.block("mechanical_crafter/lid") : ItemStack.EMPTY);
			}
			if (validTarget != beltShown) {
				beltShown = validTarget;
				beltFrame.setItem(validTarget ? ModelItems.block("mechanical_crafter/belt") : ItemStack.EMPTY);
				belt.setItem(validTarget ? ModelItems.block("mechanical_crafter/belt_animated") : ItemStack.EMPTY);
			}
			if (!validTarget != arrowShown) {
				arrowShown = !validTarget;
				arrow.setItem(arrowShown ? ModelItems.block("mechanical_crafter/arrow") : ItemStack.EMPTY);
			}

			items.render(crafter, time);
		}
	}
}

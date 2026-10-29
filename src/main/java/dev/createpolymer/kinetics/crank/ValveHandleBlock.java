package dev.createpolymer.kinetics.crank;

import java.util.Map;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.item.WrenchItem;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.KineticsExtraRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's ValveHandleBlock (MIT, The Create Team).
 * Right-click turns the handle by its configured angle (sneak: the other way); right-click with a dye colours it.
 * Create sets the angle with a scroll value on the handle's face: here a wrench (not sneaking) steps through
 * 45, 90, 135, 180 and the same turning the other way.
 */
public class ValveHandleBlock extends HandCrankBlock {
	public static final Map<Direction, VoxelShape> VALVE_SHAPE = ShapeUtil.forDirectional(
		Shapes.or(ShapeUtil.box(5, 0, 5, 11, 4, 11), ShapeUtil.box(1, 3, 1, 15, 8, 15)));

	private static final int[] ANGLE_STEPS = { 45, 90, 135, 180, -45, -90, -135, -180 };

	public final @Nullable DyeColor color;

	public static ValveHandleBlock copper(Properties properties) {
		return new ValveHandleBlock(properties, null);
	}

	public static ValveHandleBlock dyed(Properties properties, DyeColor color) {
		return new ValveHandleBlock(properties, color);
	}

	private ValveHandleBlock(Properties properties, @Nullable DyeColor color) {
		super(properties);
		this.color = color;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return VALVE_SHAPE.get(pState.getValue(FACING));
	}

	/** Create's PlayerInteractEvent.RightClickBlock subscriber; runs before the block's own use so sneaking with items works. */
	public static InteractionResult onBlockActivated(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState blockState = level.getBlockState(pos);

		if (!(blockState.getBlock() instanceof ValveHandleBlock vhb))
			return InteractionResult.PASS;
		if (player.isSpectator() || !player.mayBuild())
			return InteractionResult.PASS;
		boolean wrench = player.getItemInHand(hand).getItem() instanceof WrenchItem;
		if (wrench && player.isShiftKeyDown())
			return InteractionResult.PASS;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		if (wrench)
			vhb.nextAngle(level, pos, player);
		else
			vhb.clicked(level, pos, blockState, player, hand);
		return InteractionResult.SUCCESS;
	}

	public static void registerEvents() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> onBlockActivated(player, level, hand, hit));
	}

	@Override
	protected boolean shouldChangedStateKeepBlockEntity(BlockState oldState) {
		// dyeing swaps the block but keeps the handle's settings
		return oldState.getBlock() instanceof ValveHandleBlock;
	}

	public boolean clicked(Level level, BlockPos pos, BlockState blockState, Player player, InteractionHand hand) {
		ItemStack heldItem = player.getItemInHand(hand);
		DyeColor color = heldItem.get(DataComponents.DYE);

		if (color != null && color != this.color) {
			if (!level.isClientSide())
				level.setBlockAndUpdate(pos,
					copyProperties(blockState, KineticsExtraRegistry.DYED_VALVE_HANDLES.get(color).defaultBlockState()));
			return true;
		}

		if (level.getBlockEntity(pos) instanceof ValveHandleBlockEntity vhbe)
			vhbe.activate(player.isShiftKeyDown());
		return true;
	}

	private void nextAngle(Level level, BlockPos pos, Player player) {
		if (!(level.getBlockEntity(pos) instanceof ValveHandleBlockEntity vhbe))
			return;
		int next = ANGLE_STEPS[0];
		for (int i = 0; i < ANGLE_STEPS.length; i++)
			if (ANGLE_STEPS[i] == vhbe.getAngleInput()) {
				next = ANGLE_STEPS[(i + 1) % ANGLE_STEPS.length];
				break;
			}
		vhbe.setAngleInput(next);
		player.sendOverlayMessage(Component.translatable("create.kinetics.valve_handle.rotated_angle")
			.append(": " + Math.abs(next)).append(Component.translatable("create.generic.unit.degrees"))
			.append(next < 0 ? " ⟲" : " ⟳"));
	}

	/** Create's BlockHelper.copyProperties. */
	private static BlockState copyProperties(BlockState fromState, BlockState toState) {
		for (Property<?> property : fromState.getProperties())
			toState = copyProperty(fromState, toState, property);
		return toState;
	}

	private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
		return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hitResult) {
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return InteractionResult.PASS;
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return KineticsExtraRegistry.VALVE_HANDLE_BE;
	}

	@Override
	public int getRotationSpeed() {
		return 32;
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new ValveModel(state, color);
	}

	/** Create's ValveHandleVisual: one model that turns by the handle's own angle around its facing axis. */
	public static class ValveModel extends BaseModel {
		private final Part handle;
		private boolean spinning;
		private boolean wasInUse;

		public ValveModel(BlockState state, @Nullable DyeColor color) {
			super(state);
			handle = addPart(ModelItems.block((color == null ? "copper" : color.getSerializedName()) + "_valve_handle"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(FACING);
			handle.orientation(new Quaternionf().rotateTo(0, 1, 0, facing.getStepX(), facing.getStepY(), facing.getStepZ()))
				.spinAxis(facing.getAxis()).apply(0);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof ValveHandleBlockEntity valve))
				return;
			Axis axis = state.getValue(FACING).getAxis();
			if (valve.isDrivenByNetwork()) {
				spinning = true;
				handle.spin(time, valve.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));
				return;
			}
			boolean inUse = valve.inUse > 0;
			// angle of the next tick; the first step of a use starts from the idle angle without turning there
			boolean jump = spinning || (inUse && !wasInUse);
			spinning = false;
			wasInUse = inUse;
			handle.angle(valve.getIndependentAngle(1)).apply(jump ? 0 : 1);
		}
	}

}

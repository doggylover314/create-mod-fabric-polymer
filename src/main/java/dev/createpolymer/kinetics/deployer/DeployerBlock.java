package dev.createpolymer.kinetics.deployer;

import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilterDisplay;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.DirectionalAxisKineticBlock;
import dev.createpolymer.kinetics.deployer.DeployerBlockEntity.HandPose;
import dev.createpolymer.kinetics.deployer.DeployerBlockEntity.Mode;
import dev.createpolymer.logistics.depot.DepotRenderer;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.DeployerRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's DeployerBlock (MIT, The Create Team). */
public class DeployerBlock extends DirectionalAxisKineticBlock implements IBE<DeployerBlockEntity>, IWrenchable {

	/** Create's AllShapes.CASING_12PX and DEPLOYER_INTERACTION */
	public static final Map<Direction, VoxelShape> CASING_12PX = ShapeUtil.forDirectional(ShapeUtil.box(0, 0, 0, 16, 12, 16));
	public static final Map<Direction, VoxelShape> DEPLOYER_INTERACTION = ShapeUtil.forDirectional(
		Shapes.or(ShapeUtil.box(0, 0, 0, 16, 12, 16), ShapeUtil.box(5, 0, 5, 11, 16, 11)));

	public DeployerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return DEPLOYER_INTERACTION.get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return CASING_12PX.get(state.getValue(FACING));
	}

	@Override
	public @Nullable InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Vec3 normal = Vec3.atLowerCornerOf(state.getValue(FACING)
			.getUnitVec3i());
		Vec3 location = context.getClickLocation()
			.subtract(Vec3.atCenterOf(context.getClickedPos())
				.subtract(normal.scale(.5)))
			.multiply(normal);
		if (location.length() > .75f) {
			if (!context.getLevel().isClientSide())
				withBlockEntityDo(context.getLevel(), context.getClickedPos(), DeployerBlockEntity::changeMode);
			return InteractionResult.SUCCESS;
		}
		return null;
	}

	@Override
	public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(worldIn, pos, state, placer, stack);
		if (placer instanceof ServerPlayer)
			withBlockEntityDo(worldIn, pos, dbe -> dbe.owner = placer.getUUID());
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		ItemStack heldByPlayer = stack.copy();

		if (heldByPlayer.is(ModItems.WRENCH))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		Vec3 normal = Vec3.atLowerCornerOf(state.getValue(FACING)
			.getUnitVec3i());
		Vec3 location = hitResult.getLocation()
			.subtract(Vec3.atCenterOf(pos)
				.subtract(normal.scale(.5)))
			.multiply(normal);
		if (location.length() < .75f)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		withBlockEntityDo(level, pos, be -> {
			ItemStack heldByDeployer = be.player.getMainHandItem()
				.copy();
			if (heldByDeployer.isEmpty() && heldByPlayer.isEmpty())
				return;

			player.setItemInHand(hand, heldByDeployer);
			be.player.setItemInHand(InteractionHand.MAIN_HAND, heldByPlayer);
			be.notifyUpdate();
		});

		return InteractionResult.SUCCESS;
	}

	@Override
	public Class<DeployerBlockEntity> getBlockEntityClass() {
		return DeployerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends DeployerBlockEntity> getBlockEntityType() {
		return DeployerRegistry.DEPLOYER_BLOCK_ENTITY;
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, world, pos, oldState, isMoving);
		withBlockEntityDo(world, pos, DeployerBlockEntity::redstoneUpdate);
	}

	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		withBlockEntityDo(world, pos, DeployerBlockEntity::redstoneUpdate);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected Direction getFacingForPlacement(BlockPlaceContext context) {
		if (context instanceof AssemblyOperatorUseContext)
			return Direction.DOWN;
		else
			return super.getFacingForPlacement(context);
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.ANDESITE.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's DeployerVisual, DeployerRenderer.renderItem and the filter renderer. */
	public static class Model extends BaseModel {
		private final Part body;
		private final Part shaft;
		private final Part pole;
		private final Part hand;
		private final Part heldItem;
		private final FilterDisplay filter = new FilterDisplay(this);

		private HandPose shownPose;
		private float shownOffset = Float.NaN;
		private ItemStack shownItem = ItemStack.EMPTY;
		private boolean shownDisplayMode;
		private boolean heldItemVisible;
		private float heldOffset = Float.NaN;
		private boolean heldPunching;

		public Model(BlockState state) {
			super(state);
			body = addPart(ModelItems.block("deployer/horizontal"));
			shaft = addPart(ModelItems.block("shaft"));
			pole = addPart(ModelItems.block("deployer/pole"));
			hand = addPart(ModelItems.block(HandPose.POINTING.model));
			heldItem = new Part(ItemStack.EMPTY);
			addElement(heldItem.element);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			Direction facing = state.getValue(FACING);
			showVariant(body, "deployer", state);

			Axis axis = ((DeployerBlock) state.getBlock()).getRotationAxis(state);
			shaft.orientation(Orientation.forAxis(axis)).spinAxis(axis).apply(0);

			boolean rotatePole = state.getValue(AXIS_ALONG_FIRST_COORDINATE) ^ facing.getAxis() == Axis.Z;
			float yRot = AngleHelper.horizontalAngle(facing);
			float xRot = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;
			float zRot = rotatePole ? 90 : 0;
			Quaternionf handRotation = new Quaternionf().rotationY((float) Math.toRadians(yRot))
				.rotateX((float) Math.toRadians(xRot));
			hand.orientation(handRotation).apply(0);
			pole.orientation(new Quaternionf(handRotation).rotateZ((float) Math.toRadians(zRot))).apply(0);
			shownOffset = Float.NaN;
			shownPose = null;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof DeployerBlockEntity deployer))
				return;
			filter.update(deployer);

			Direction facing = state.getValue(FACING);
			Axis axis = ((DeployerBlock) state.getBlock()).getRotationAxis(state);
			shaft.spin(time, deployer.getSpeed(), Orientation.cogOffset(false, axis, pos.getX(), pos.getY(), pos.getZ()));

			// the hand is where it will be at the end of this tick
			HandPose pose = deployer.getHandPose();
			float offset = deployer.getHandOffset(1);
			if (pose != shownPose) {
				shownPose = pose;
				hand.setItem(ModelItems.block(pose.model));
			}
			if (offset != shownOffset) {
				shownOffset = offset;
				Vec3 move = Vec3.atLowerCornerOf(facing.getUnitVec3i())
					.scale(offset);
				pole.translation((float) move.x, (float) move.y, (float) move.z)
					.apply(1);
				hand.translation((float) move.x, (float) move.y, (float) move.z)
					.apply(1);
			}

			updateHeldItem(deployer, facing, pose, offset);
		}

		private void updateHeldItem(DeployerBlockEntity deployer, Direction facing, HandPose pose, float distance) {
			ItemStack held = deployer.getHeldItem();
			if (held.isEmpty()) {
				if (heldItemVisible) {
					heldItemVisible = false;
					shownItem = ItemStack.EMPTY;
					heldItem.setItem(ItemStack.EMPTY);
				}
				return;
			}

			boolean punching = deployer.mode == Mode.PUNCH;
			boolean displayMode = facing == Direction.UP && deployer.getSpeed() == 0 && !punching;
			boolean sameItem = heldItemVisible && ItemStack.isSameItemSameComponents(shownItem, held);
			if (sameItem && !Float.isNaN(heldOffset) && heldOffset == distance && displayMode == shownDisplayMode
				&& heldPunching == punching)
				return;
			heldOffset = distance;
			heldPunching = punching;
			shownDisplayMode = displayMode;

			Vec3 offset = Vec3.atLowerCornerOf(facing.getUnitVec3i())
				.scale(distance);
			Matrix4f m = new Matrix4f().translate((float) offset.x, (float) offset.y, (float) offset.z);

			float yRot = AngleHelper.horizontalAngle(facing) + 180;
			float xRot = facing == Direction.UP ? 90 : facing == Direction.DOWN ? 270 : 0;

			m.rotateY((float) Math.toRadians(yRot));
			if (!displayMode) {
				m.rotateX((float) Math.toRadians(xRot));
				m.translate(0, 0, -11 / 16f);
			}

			if (punching)
				m.translate(0, 1 / 8f, -1 / 16f);

			boolean isBlockItem = (held.getItem() instanceof BlockItem) && DepotRenderer.isBlockItem(held);
			ItemDisplayContext context;
			if (displayMode) {
				float scale = isBlockItem ? 1.25f : 1;
				m.translate(0, isBlockItem ? 9 / 16f : 11 / 16f, 0);
				m.scale(scale);
				context = ItemDisplayContext.GROUND;
			} else {
				float scale = punching ? .75f : isBlockItem ? .75f - 1 / 64f : .5f;
				m.scale(scale);
				context = punching ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.FIXED;
			}
			// Display entities turn their item around before drawing it
			m.rotateY((float) Math.PI);

			if (!sameItem) {
				shownItem = held.copyWithCount(1);
				heldItem.setItem(shownItem);
			}
			heldItem.element.setItemDisplayContext(context);
			heldItemVisible = true;
			heldItem.setMatrix(m, 1);
		}
	}
}

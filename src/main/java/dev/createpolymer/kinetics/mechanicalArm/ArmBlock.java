package dev.createpolymer.kinetics.mechanicalArm;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.ICogWheel;
import dev.createpolymer.kinetics.base.KineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.mechanicalArm.ArmBlockEntity.Phase;
import dev.createpolymer.logistics.depot.DepotRenderer;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.ArmRegistry;
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
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's ArmBlock (MIT, The Create Team). */
public class ArmBlock extends KineticBlock implements IBE<ArmBlockEntity>, ICogWheel {

	public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");

	/** Create's AllShapes.MECHANICAL_ARM and MECHANICAL_ARM_CEILING */
	public static final VoxelShape MECHANICAL_ARM = Shapes.or(ShapeUtil.box(2, 0, 2, 14, 10, 14),
		ShapeUtil.box(3, 0, 3, 13, 14, 13), ShapeUtil.box(0, 0, 0, 16, 6, 16));
	public static final VoxelShape MECHANICAL_ARM_CEILING = Shapes.or(ShapeUtil.box(2, 6, 2, 14, 16, 14),
		ShapeUtil.box(3, 2, 3, 13, 16, 13), ShapeUtil.box(0, 10, 0, 16, 16, 16));

	public ArmBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(CEILING, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> p_206840_1_) {
		super.createBlockStateDefinition(p_206840_1_.add(CEILING));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(CEILING, ctx.getClickedFace() == Direction.DOWN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter p_220053_2_, BlockPos p_220053_3_,
		CollisionContext p_220053_4_) {
		return state.getValue(CEILING) ? MECHANICAL_ARM_CEILING : MECHANICAL_ARM;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof Player player && !level.isClientSide())
			ArmInteractionPointHandler.flushSettings(player, level, pos, stack);
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, world, pos, oldState, isMoving);
		withBlockEntityDo(world, pos, ArmBlockEntity::redstoneUpdate);
	}

	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean isMoving) {
		withBlockEntityDo(world, pos, ArmBlockEntity::redstoneUpdate);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return Axis.Y;
	}

	@Override
	public Class<ArmBlockEntity> getBlockEntityClass() {
		return ArmBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ArmBlockEntity> getBlockEntityType() {
		return ArmRegistry.MECHANICAL_ARM_BLOCK_ENTITY;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (stack.is(ModItems.GOGGLES)) {
			InteractionResult gogglesResult = onBlockEntityUseItemOn(level, pos, ate -> {
				if (ate.goggles)
					return InteractionResult.TRY_WITH_EMPTY_HAND;
				ate.goggles = true;
				ate.notifyUpdate();
				return InteractionResult.SUCCESS;
			});
			if (gogglesResult.consumesAction())
				return gogglesResult;
		}

		MutableBoolean success = new MutableBoolean(false);
		withBlockEntityDo(level, pos, be -> {
			if (be.heldItem.isEmpty())
				return;
			success.setTrue();
			if (level.isClientSide())
				return;
			player.getInventory()
				.placeItemBackInInventory(be.heldItem, Prediction.SERVER_ONLY);
			be.heldItem = ItemStack.EMPTY;
			be.phase = Phase.SEARCH_INPUTS;
			be.setChanged();
			be.sendData();
		});

		return success.booleanValue() ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.GOLD_BLOCK.defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's ArmVisual and ArmRenderer: the casing, the spinning cog and the articulated arm. */
	public static class Model extends BaseModel {
		private final Part casing;
		private final Part cog;
		private final Part base;
		private final Part lowerBody;
		private final Part upperBody;
		private final Part claw;
		private final Part gripUpper;
		private final Part gripLower;
		private final Part heldItemPart;

		private boolean ceiling;
		private boolean shownGoggles;
		private boolean shownHasItem;
		private boolean shownBlockItem;
		private ItemStack shownItem = ItemStack.EMPTY;
		private float shownBase = Float.NaN;
		private float shownLower = Float.NaN;
		private float shownUpper = Float.NaN;
		private float shownHead = Float.NaN;

		public Model(BlockState state) {
			super(state);
			casing = addPart(ModelItems.block("mechanical_arm/block"));
			cog = addPart(ModelItems.block("mechanical_arm/cog"));
			base = addPart(ModelItems.block("mechanical_arm/base"));
			lowerBody = addPart(ModelItems.block("mechanical_arm/lower_body"));
			upperBody = addPart(ModelItems.block("mechanical_arm/upper_body"));
			claw = addPart(ModelItems.block("mechanical_arm/claw_base"));
			gripUpper = addPart(ModelItems.block("mechanical_arm/upper_claw_grip"));
			gripLower = addPart(ModelItems.block("mechanical_arm/lower_claw_grip"));
			heldItemPart = new Part(ItemStack.EMPTY);
			heldItemPart.element.setItemDisplayContext(ItemDisplayContext.FIXED);
			addElement(heldItemPart.element);
			cog.spinAxis(Axis.Y);
			onStateChanged(state);
			// the resting pose, before the block entity is first read
			updateAngles(0, 0, -45, 0, 0);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			ceiling = state.getValue(CEILING);
			// Create's blockstate rotates the casing 180 degrees around X for the ceiling
			casing.orientation(Orientation.variant(ceiling ? 180 : 0, 0))
				.apply(0);
			shownBase = Float.NaN;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof ArmBlockEntity arm))
				return;
			cog.spin(time, arm.getSpeed(), Orientation.cogOffset(false, Axis.Y, pos.getX(), pos.getY(), pos.getZ()));

			float baseAngle;
			float lowerArmAngle;
			float upperArmAngle;
			float headAngle;
			if (arm.phase == Phase.DANCING && arm.getSpeed() != 0) {
				// the model shows the end of this tick
				float renderTick = time + 1 + (pos.hashCode() % 64);
				baseAngle = (renderTick * 10) % 360;
				lowerArmAngle = Mth.lerp((Mth.sin(renderTick / 4) + 1) / 2, -45, 15);
				upperArmAngle = Mth.lerp((Mth.sin(renderTick / 8) + 1) / 4, -45, 95);
				headAngle = -lowerArmAngle;
			} else {
				baseAngle = arm.baseAngle.getValue();
				lowerArmAngle = arm.lowerArmAngle.getValue() - 135;
				upperArmAngle = arm.upperArmAngle.getValue() - 90;
				headAngle = arm.headAngle.getValue();
			}

			ItemStack held = arm.heldItem;
			boolean hasItem = !held.isEmpty();
			boolean blockItem = hasItem && held.getItem() instanceof BlockItem && DepotRenderer.isBlockItem(held);
			boolean sameItem = hasItem ? ItemStack.isSameItemSameComponents(held, shownItem) : shownItem.isEmpty();

			if (arm.goggles != shownGoggles) {
				shownGoggles = arm.goggles;
				claw.setItem(ModelItems.block(shownGoggles ? "mechanical_arm/claw_base_goggles" : "mechanical_arm/claw_base"));
				shownBase = Float.NaN;
			}

			if (baseAngle == shownBase && lowerArmAngle == shownLower && upperArmAngle == shownUpper
				&& headAngle == shownHead && hasItem == shownHasItem && blockItem == shownBlockItem && sameItem)
				return;

			if (!sameItem) {
				shownItem = hasItem ? held.copyWithCount(1) : ItemStack.EMPTY;
				heldItemPart.setItem(shownItem);
			}
			shownHasItem = hasItem;
			shownBlockItem = blockItem;
			updateAngles(baseAngle, lowerArmAngle, upperArmAngle, headAngle, 1);
		}

		private void updateAngles(float baseAngle, float lowerArmAngle, float upperArmAngle, float headAngle,
			int interpolation) {
			shownBase = baseAngle;
			shownLower = lowerArmAngle;
			shownUpper = upperArmAngle;
			shownHead = headAngle;

			// block-centred, like Create's TransformStack after center()
			Matrix4f m = new Matrix4f();
			if (ceiling)
				m.rotateX((float) Math.PI);

			ArmRenderer.transformBase(m, baseAngle);
			set(base, m, interpolation);

			ArmRenderer.transformLowerArm(m, lowerArmAngle);
			set(lowerBody, m, interpolation);

			ArmRenderer.transformUpperArm(m, upperArmAngle);
			set(upperBody, m, interpolation);

			ArmRenderer.transformHead(m, headAngle);

			if (ceiling && shownGoggles)
				m.rotateZ((float) Math.PI);
			set(claw, m, interpolation);
			if (ceiling && shownGoggles)
				m.rotateZ((float) Math.PI);

			for (int index = 0; index < 2; index++) {
				Matrix4f grip = new Matrix4f(m);
				ArmRenderer.transformClawHalf(grip, shownHasItem, shownBlockItem, index * 2 - 1);
				set(index == 0 ? gripUpper : gripLower, grip, interpolation);
			}

			if (shownHasItem) {
				float itemScale = shownBlockItem ? .5f : .625f;
				Matrix4f item = new Matrix4f(m)
					.rotateX((float) Math.PI / 2)
					.translate(0, shownBlockItem ? -9 / 16f : -10 / 16f, 0)
					.scale(itemScale)
					// display entities turn their item around before drawing it
					.rotateY((float) Math.PI);
				heldItemPart.setMatrix(item, interpolation);
			}
		}

		/**
		 * Create's models are drawn around the point they are transformed about (px 0,0,0 of the file is the
		 * pivot); a display entity draws the file centred on its origin, so shift by half a block.
		 */
		private static void set(Part part, Matrix4f chain, int interpolation) {
			part.setMatrix(new Matrix4f(chain).translate(.5f, .5f, .5f), interpolation);
		}
	}

}

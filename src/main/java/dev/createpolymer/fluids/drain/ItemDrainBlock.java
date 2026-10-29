package dev.createpolymer.fluids.drain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.fluids.FluidBox;
import dev.createpolymer.fluids.transfer.GenericItemEmptying;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.ComparatorUtil;
import dev.createpolymer.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import dev.createpolymer.foundation.fluid.FluidHelper;
import dev.createpolymer.foundation.fluid.FluidItemHandlers;
import dev.createpolymer.foundation.fluid.FluidStack;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.logistics.depot.DepotRenderer;
import dev.createpolymer.registry.feature.TanksRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.VoxelShape;

public class ItemDrainBlock extends Block implements IWrenchable, IBE<ItemDrainBlockEntity>, PolymerModelBlock {

	/** Create's AllShapes.CASING_13PX */
	public static final VoxelShape SHAPE = ShapeUtil.box(0, 0, 0, 16, 13, 16);

	public ItemDrainBlock(Properties p_i48440_1_) {
		super(p_i48440_1_);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
										  InteractionHand hand, BlockHitResult hitResult) {
		if (stack.getItem() instanceof BlockItem && FluidItemHandlers.get(stack).isEmpty())
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		return onBlockEntityUseItemOn(level, pos, be -> use(stack, level, pos, player, hand, be));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
											   BlockHitResult hitResult) {
		return onBlockEntityUse(level, pos, be -> use(ItemStack.EMPTY, level, pos, player, InteractionHand.MAIN_HAND, be));
	}

	private InteractionResult use(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand,
								  ItemDrainBlockEntity be) {
		if (!stack.isEmpty()) {
			be.internalTank.allowInsertion();
			InteractionResult tryExchange = tryExchange(level, player, hand, stack, be);
			be.internalTank.forbidInsertion();
			if (tryExchange.consumesAction())
				return tryExchange;
		}

		ItemStack heldItemStack = be.getHeldItemStack();
		if (!level.isClientSide() && !heldItemStack.isEmpty()) {
			player.getInventory()
				.placeItemBackInInventory(heldItemStack, Prediction.SERVER_ONLY);
			be.heldItem = null;
			be.notifyUpdate();
		}
		return InteractionResult.SUCCESS;
	}

	protected InteractionResult tryExchange(Level worldIn, Player player, InteractionHand handIn, ItemStack heldItem,
											ItemDrainBlockEntity be) {
		if (FluidHelper.tryEmptyItemIntoBE(worldIn, player, handIn, heldItem, be))
			return InteractionResult.SUCCESS;
		if (GenericItemEmptying.canItemBeEmptied(worldIn, heldItem))
			return InteractionResult.SUCCESS;
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected VoxelShape getShape(BlockState p_220053_1_, BlockGetter p_220053_2_, BlockPos p_220053_3_,
								  CollisionContext p_220053_4_) {
		return SHAPE;
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public Class<ItemDrainBlockEntity> getBlockEntityClass() {
		return ItemDrainBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ItemDrainBlockEntity> getBlockEntityType() {
		return TanksRegistry.ITEM_DRAIN_BLOCK_ENTITY;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState blockState, Level worldIn, BlockPos pos, Direction direction) {
		return ComparatorUtil.levelOfSmartFluidTank(worldIn, pos);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's ItemDrainRenderer: the fluid in the drain, the stream of fluid from the item and the item itself. */
	public static class Model extends BaseModel {
		private final FluidBox tankFluid;
		private final FluidBox stream;
		private final List<Part> layers = new ArrayList<>();
		private final List<Matrix4f> lastMatrices = new ArrayList<>();
		private ItemStack shownItem = ItemStack.EMPTY;
		private ItemStack lastHeld = ItemStack.EMPTY;
		private float lastPosition, lastSide;
		private Direction lastFrom;

		public Model(BlockState state) {
			super(state);
			addPart(ModelItems.block("item_drain")).apply(0);
			tankFluid = new FluidBox(this);
			stream = new FluidBox(this);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof ItemDrainBlockEntity drain))
				return;
			updateFluid(level, drain);
			updateItem(drain);
		}

		private void updateFluid(ServerLevel level, ItemDrainBlockEntity be) {
			SmartFluidTankBehaviour tank = be.internalTank;
			if (tank == null)
				return;

			TankSegment primaryTank = tank.getPrimaryTank();
			FluidStack fluidStack = primaryTank.getRenderedFluid();
			float fluidLevel = primaryTank.getFluidLevel()
				.getValue();

			if (!fluidStack.isEmpty() && fluidLevel != 0) {
				float yMin = 5f / 16f;
				float min = 2f / 16f;
				float max = min + (12 / 16f);
				float yOffset = (7 / 16f) * fluidLevel;
				tankFluid.show(fluidStack, min - .5f, yMin - .5f, min - .5f, max - .5f, yMin + yOffset - .5f, max - .5f);
			} else
				tankFluid.hide();

			ItemStack heldItemStack = be.getHeldItemStack();
			if (heldItemStack.isEmpty()) {
				stream.hide();
				return;
			}
			FluidStack fluidStack2 = GenericItemEmptying.emptyItem(level, heldItemStack, true)
				.getFirst();
			if (fluidStack2.isEmpty()) {
				if (fluidStack.isEmpty()) {
					stream.hide();
					return;
				}
				fluidStack2 = fluidStack;
			}

			int processingTicks = be.processingTicks;
			float processingPT = be.processingTicks;
			float processingProgress = 1 - (processingPT - 5) / 10;
			processingProgress = Mth.clamp(processingProgress, 0, 1);

			if (processingTicks != -1 && processingTicks != 0) {
				float radius = (float) (Math.pow(((2 * processingProgress) - 1), 2) - 1);
				float half = Math.abs(radius) / 32f;
				stream.show(fluidStack2, .5f - half - .5f, .25f - .5f, .5f - half - .5f, .5f + half - .5f, 1f - .5f,
					.5f + half - .5f);
			} else
				stream.hide();
		}

		private void updateItem(ItemDrainBlockEntity be) {
			TransportedItemStack transported = be.heldItem;
			if (transported == null || !transported.insertedFrom.getAxis().isHorizontal()) {
				hideItem();
				return;
			}
			if (!layers.isEmpty() && lastMatrices.get(0) != null && ItemStack.matches(lastHeld, transported.stack)
				&& lastPosition == transported.beltPosition && lastSide == transported.sideOffset
				&& lastFrom == transported.insertedFrom)
				return;
			lastHeld = transported.stack.copy();
			lastPosition = transported.beltPosition;
			lastSide = transported.sideOffset;
			lastFrom = transported.insertedFrom;

			Direction insertedFrom = transported.insertedFrom;
			Matrix4f base = new Matrix4f().translate(0, 15 / 16f - .5f, 0);
			float offset = transported.beltPosition;
			float sideOffset = transported.sideOffset;

			Vec3 offsetVec = Vec3.atLowerCornerOf(insertedFrom.getOpposite()
				.getUnitVec3i())
				.scale(.5f - offset);
			base.translate((float) offsetVec.x, (float) offsetVec.y, (float) offsetVec.z);
			boolean alongX = insertedFrom.getClockWise()
				.getAxis() == Direction.Axis.X;
			if (!alongX)
				sideOffset *= -1;
			base.translate(alongX ? sideOffset : 0, 0, alongX ? 0 : sideOffset);

			int positive = insertedFrom.getAxisDirection()
				.getStep();
			float verticalAngle = positive * offset * 360;
			if (insertedFrom.getAxis() != Direction.Axis.X)
				base.rotateX((float) Math.toRadians(verticalAngle));
			if (insertedFrom.getAxis() != Direction.Axis.Z)
				base.rotateZ((float) Math.toRadians(-verticalAngle));

			List<Matrix4f> matrices = DepotRenderer.renderItem(base, transported.stack, 0, new Random(0), false);
			ItemStack one = transported.stack.copyWithCount(1);
			boolean fresh = lastMatrices.isEmpty() || lastMatrices.get(0) == null;
			for (int i = 0; i < matrices.size(); i++) {
				if (i == layers.size()) {
					Part part = new Part(ItemStack.EMPTY);
					part.element.setItemDisplayContext(ItemDisplayContext.FIXED);
					addElement(part.element);
					layers.add(part);
					lastMatrices.add(null);
				}
				Part part = layers.get(i);
				boolean layerFresh = fresh || lastMatrices.get(i) == null;
				if (layerFresh || !ItemStack.isSameItemSameComponents(shownItem, one))
					part.setItem(one);
				part.setMatrix(matrices.get(i), layerFresh ? 0 : 1);
				lastMatrices.set(i, matrices.get(i));
			}
			shownItem = one;
			for (int i = matrices.size(); i < layers.size(); i++)
				hideLayer(i);
		}

		private void hideItem() {
			lastHeld = ItemStack.EMPTY;
			shownItem = ItemStack.EMPTY;
			for (int i = 0; i < layers.size(); i++)
				hideLayer(i);
		}

		private void hideLayer(int i) {
			if (lastMatrices.get(i) == null)
				return;
			layers.get(i).setItem(ItemStack.EMPTY);
			lastMatrices.set(i, null);
		}
	}
}

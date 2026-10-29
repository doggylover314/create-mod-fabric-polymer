package dev.createpolymer.logistics.itemHatch;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.MiscRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's ItemHatchBlock (MIT, The Create Team). The packager package check is not ported. */
public class ItemHatchBlock extends HorizontalDirectionalBlock
	implements IBE<ItemHatchBlockEntity>, IWrenchable, ProperWaterloggedBlock, PolymerModelBlock {

	public static final BooleanProperty OPEN = BooleanProperty.create("open");

	// Create's AllShapes.ITEM_HATCH: the plate is on the FACING side; the other facings are this shape turned.
	private static final VoxelShape NORTH_SHAPE = Shapes.or(ShapeUtil.box(1, 0, 0, 15, 16, 2), ShapeUtil.box(2, 2, 0, 14, 13, 3.8),
		ShapeUtil.box(2, 4, 0, 14, 11, 5.8), ShapeUtil.box(2, 6, 0, 14, 9, 7.8));
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
	static {
		VoxelShape shape = NORTH_SHAPE;
		for (Direction d : new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST }) {
			SHAPES.put(d, shape);
			shape = ShapeUtil.rotateY90(shape);
		}
	}

	public ItemHatchBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(OPEN, false).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(OPEN, FACING, WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (context.getClickedFace().getAxis().isVertical())
			return null;

		return withWater(defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite()).setValue(OPEN, false), context);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, net.minecraft.util.RandomSource random) {
		updateWater(level, ticks, state, pos);
		return state;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (player instanceof FakePlayer)
			return InteractionResult.SUCCESS;

		BlockEntity blockEntity = level.getBlockEntity(pos.relative(state.getValue(FACING)));
		if (blockEntity == null)
			return InteractionResult.FAIL;
		IItemHandler targetInv = ItemHandlerLookup.get(level, blockEntity.getBlockPos(), null);
		if (targetInv == null)
			return InteractionResult.FAIL;

		FilteringBehaviour filter = BlockEntityBehaviour.get(level, pos, FilteringBehaviour.TYPE);
		if (filter == null)
			return InteractionResult.FAIL;

		Inventory inventory = player.getInventory();
		List<ItemStack> failedInsertions = new ArrayList<>();
		boolean anyInserted = false;
		boolean depositItemInHand = !player.isShiftKeyDown();

		if (!depositItemInHand && stack.is(ModItems.WRENCH))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
		for (int i = 0; i < items.size(); i++) {
			if (Inventory.isHotbarSlot(i) != depositItemInHand)
				continue;
			if (depositItemInHand && i != inventory.getSelectedSlot())
				continue;
			ItemStack item = inventory.getItem(i);
			if (item.isEmpty())
				continue;
			if (!item.getItem().canFitInsideContainerItems())
				continue;
			if (!filter.getFilter().isEmpty() && !filter.test(item))
				continue;

			ItemStack remainder = ItemHandlerHelper.insertItemStacked(targetInv, item, true);
			if (remainder.getCount() == item.getCount())
				continue;

			ItemStack extracted = inventory.removeItem(i, item.getCount() - remainder.getCount());
			remainder = ItemHandlerHelper.insertItemStacked(targetInv, extracted, false);
			anyInserted = true;

			// remainder might not be empty in itemhandler edge cases
			if (!remainder.isEmpty())
				failedInsertions.add(remainder);
		}

		failedInsertions.forEach(s -> inventory.placeItemBackInInventory(s, Prediction.SERVER_ONLY));

		if (!anyInserted)
			return InteractionResult.SUCCESS;

		// Create's AllSoundEvents.ITEM_HATCH: the barrel open sound
		level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 1, 1);
		level.setBlockAndUpdate(pos, state.setValue(OPEN, true));
		level.scheduleTick(pos, this, 10);

		Lang.builder(CreatePolymer.MOD_ID)
			.translate(depositItemInHand ? "item_hatch.deposit_item" : "item_hatch.deposit_inventory")
			.sendStatus(player);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(OPEN))
			level.setBlockAndUpdate(pos, state.setValue(OPEN, false));
	}

	@Override
	public Class<ItemHatchBlockEntity> getBlockEntityClass() {
		return ItemHatchBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ItemHatchBlockEntity> getBlockEntityType() {
		return MiscRegistry.ITEM_HATCH_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType pathComputationType) {
		return false;
	}


	// ---- Polymer ----

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's blockstate: closed or open plate for the facing, drawn with the state's variant. */
	public static class Model extends BaseModel {
		private final Part hatch;

		public Model(BlockState state) {
			super(state);
			hatch = addPart(ModelItems.block("item_hatch/block_closed"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(hatch, "item_hatch", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {}
	}
}

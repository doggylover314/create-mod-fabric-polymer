package dev.createpolymer.logistics.stockTicker;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBlockItem;
import dev.createpolymer.registry.feature.StockRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.createpolymer.util.Lang;
import dev.createpolymer.CreatePolymer;

public class StockTickerBlock extends HorizontalDirectionalBlock
	implements IBE<StockTickerBlockEntity>, IWrenchable, PolymerModelBlock {

	/** Create's AllShapes.STOCK_TICKER */
	public static final VoxelShape SHAPE = Shapes.or(ShapeUtil.box(1, 0, 1, 15, 4, 15), ShapeUtil.box(2, 0, 2, 14, 16, 14));

	public StockTickerBlock(Properties pProperties) {
		super(pProperties);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		Direction facing = pContext.getHorizontalDirection()
			.getOpposite();
		boolean reverse = pContext.getPlayer() != null && pContext.getPlayer()
			.isShiftKeyDown();
		return super.getStateForPlacement(pContext).setValue(FACING, reverse ? facing.getOpposite() : facing);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACING));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (stack.getItem() instanceof LogisticallyLinkedBlockItem)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		return onBlockEntityUseItemOn(level, pos, stbe -> {
			if (!stbe.behaviour.mayInteractMessage(player))
				return InteractionResult.SUCCESS;

			if (!level.isClientSide() && !stbe.receivedPayments.isEmpty()) {
				for (int i = 0; i < stbe.receivedPayments.getSlots(); i++)
					player.getInventory()
						.placeItemBackInInventory(
							stbe.receivedPayments.extractItem(i, stbe.receivedPayments.getStackInSlot(i)
								.getCount(), false),
							net.minecraft.util.Prediction.SERVER_ONLY);
				StockSounds.itemPickup(player);
				return InteractionResult.SUCCESS;
			}

			if (player instanceof ServerPlayer sp) {
				if (stbe.isKeeperPresent())
					new StockKeeperCategoryGui(sp, stbe).open();
				else
					player.sendOverlayMessage(translate("stock_ticker.keeper_missing"));
			}

			return InteractionResult.SUCCESS;
		});
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return SHAPE;
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos,
		CollisionContext pContext) {
		return SHAPE;
	}

	@Override
	public Class<StockTickerBlockEntity> getBlockEntityClass() {
		return StockTickerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends StockTickerBlockEntity> getBlockEntityType() {
		return StockRegistry.STOCK_TICKER_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	static Component translate(String key) {
		return Lang.builder(CreatePolymer.MOD_ID)
			.translate(key)
			.component();
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.GLASS.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's block model; the stock keeper's hat (an entity model on the client) is not shown. */
	public static class Model extends BaseModel {
		private final Part body;

		public Model(BlockState state) {
			super(state);
			body = addPart(ModelItems.block("stock_ticker"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(body, "stock_ticker", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}
}

package dev.createpolymer.logistics.redstoneRequester;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.logistics.stockTicker.StockTickerBlockEntity;
import dev.createpolymer.logistics.tableCloth.TableClothBlock;
import dev.createpolymer.registry.feature.StockRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

public class RedstoneRequesterBlock extends Block implements IBE<RedstoneRequesterBlockEntity>, IWrenchable, PolymerModelBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final EnumProperty<Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	public RedstoneRequesterBlock(Properties pProperties) {
		super(pProperties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(
		net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(POWERED, AXIS));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
		if (stateForPlacement == null)
			return null;
		return stateForPlacement.setValue(AXIS, pContext.getHorizontalDirection()
			.getAxis())
			.setValue(POWERED, pContext.getLevel()
				.hasNeighborSignal(pContext.getClickedPos()));
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState pState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState pBlockState, Level pLevel, BlockPos pPos, Direction direction) {
		RedstoneRequesterBlockEntity req = getBlockEntity(pLevel, pPos);
		return req != null && req.lastRequestSucceeded ? 15 : 0;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return onBlockEntityUse(level, pos, be -> be.use(player));
	}

	/** Create's PackageOrderRequestPacket with {@code encodeRequester}: writes the order onto the held item. */
	public static void programRequester(ServerPlayer player, StockTickerBlockEntity be, PackageOrderWithCrafts order, String address) {
		ItemStack stack = player.getMainHandItem();
		boolean isRequester = isRequester(stack);
		boolean isShopCloth = isTableCloth(stack);
		if (!isRequester && !isShopCloth)
			return;

		String targetDim = player.level()
			.dimension()
			.identifier()
			.toString();
		AutoRequestData autoRequestData = new AutoRequestData(order, address, be.getBlockPos(), targetDim, false);

		autoRequestData.writeToItem(BlockPos.ZERO, stack);

		if (isRequester) {
			TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
			CompoundTag beTag = data == null ? new CompoundTag() : data.copyTagWithoutId();
			beTag.store("Freq", UUIDUtil.CODEC, be.behaviour.freqId);
			stack.set(DataComponents.BLOCK_ENTITY_DATA,
				TypedEntityData.of(StockRegistry.REDSTONE_REQUESTER_BLOCK_ENTITY, beTag));
		}

		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
	}

	public static boolean isRequester(ItemStack stack) {
		return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof RedstoneRequesterBlock;
	}

	public static boolean isTableCloth(ItemStack stack) {
		return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof TableClothBlock;
	}

	public static void appendRequesterTooltip(ItemStack pStack, Consumer<Component> pTooltip) {
		AutoRequestData data = pStack.get(StockRegistry.AUTO_REQUEST_DATA);
		if (data == null)
			return;

		for (BigItemStack entry : data.encodedRequest().stacks()) {
			pTooltip.accept(entry.stack.getHoverName()
				.copy()
				.append(" x")
				.append(String.valueOf(entry.count))
				.withStyle(ChatFormatting.GRAY));
		}

		pTooltip.accept(Lang.builder(CreatePolymer.MOD_ID)
			.translate("logistically_linked.tooltip_clear")
			.style(ChatFormatting.DARK_GRAY)
			.component());
	}

	@Override
	public void setPlacedBy(Level pLevel, BlockPos requesterPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
		Player player = pPlacer instanceof Player ? (Player) pPlacer : null;
		withBlockEntityDo(pLevel, requesterPos, rrbe -> {
			AutoRequestData data = AutoRequestData.readFromItem(pLevel, player, requesterPos, pStack);
			if (data == null)
				return;
			rrbe.encodedRequest = data.encodedRequest();
			rrbe.encodedTargetAdress = data.encodedTargetAddress();
		});
	}

	@Override
	protected void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pNeighborBlock,
		@Nullable Orientation orientation, boolean pMovedByPiston) {
		if (pLevel.isClientSide())
			return;
		pLevel.setBlockAndUpdate(pPos, pState.setValue(POWERED, pLevel.hasNeighborSignal(pPos)));
		withBlockEntityDo(pLevel, pPos, RedstoneRequesterBlockEntity::onRedstonePowerChanged);
	}

	@Override
	public Class<RedstoneRequesterBlockEntity> getBlockEntityClass() {
		return RedstoneRequesterBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends RedstoneRequesterBlockEntity> getBlockEntityType() {
		return StockRegistry.REDSTONE_REQUESTER_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public BlockState rotate(BlockState pState, Rotation pRotation) {
		return pState.setValue(AXIS, pRotation.rotate(Direction.get(AxisDirection.POSITIVE, pState.getValue(AXIS)))
			.getAxis());
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.STONE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part body;

		public Model(BlockState state) {
			super(state);
			body = addPart(ModelItems.block("redstone_requester/block"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(body, "redstone_requester", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}
}

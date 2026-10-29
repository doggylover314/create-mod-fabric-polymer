package dev.createpolymer.equipment.clipboard;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.redstone.RedstoneClientStates;
import dev.createpolymer.registry.feature.ToolsRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ClipboardBlock extends FaceAttachedHorizontalDirectionalBlock
	implements IBE<ClipboardBlockEntity>, IWrenchable, ProperWaterloggedBlock, PolymerModelBlock {

	public static final BooleanProperty WRITTEN = BooleanProperty.create("written");

	/** Create's AllShapes.CLIPBOARD_FLOOR / CEILING / WALL */
	private static final VoxelShaper CLIPBOARD_FLOOR = VoxelShaper.forHorizontal(ShapeUtil.box(3, 0, 1, 13, 1, 15), Direction.SOUTH);
	private static final VoxelShaper CLIPBOARD_CEILING = VoxelShaper.forHorizontal(ShapeUtil.box(3, 15, 1, 13, 16, 15), Direction.SOUTH);
	private static final VoxelShaper CLIPBOARD_WALL = VoxelShaper.forHorizontal(ShapeUtil.box(3, 1, 0, 13, 15, 1), Direction.SOUTH);

	public ClipboardBlock(Properties pProperties) {
		super(pProperties);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false)
			.setValue(WRITTEN, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(WRITTEN, FACE, FACING, WATERLOGGED));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
		if (stateForPlacement == null)
			return null;
		if (stateForPlacement.getValue(FACE) != AttachFace.WALL)
			stateForPlacement = stateForPlacement.setValue(FACING, stateForPlacement.getValue(FACING)
				.getOpposite());
		return withWater(stateForPlacement, pContext).setValue(WRITTEN, !pContext.getItemInHand().getComponentsPatch().isEmpty());
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return (switch (pState.getValue(FACE)) {
		case FLOOR -> CLIPBOARD_FLOOR;
		case CEILING -> CLIPBOARD_CEILING;
		default -> CLIPBOARD_WALL;
		}).get(pState.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return getShape(pState, pLevel, pPos, pContext);
	}

	@Override
	protected boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		return !pLevel.getBlockState(pPos.relative(getConnectedDirection(pState).getOpposite()))
			.canBeReplaced();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (player.isShiftKeyDown()) {
			breakAndCollect(state, level, pos, player);
			return InteractionResult.SUCCESS;
		}

		return onBlockEntityUse(level, pos, cbe -> {
			if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer)
				ClipboardGui.open(serverPlayer, ClipboardGui.Target.block(cbe));
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	protected void attack(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer) {
		breakAndCollect(pState, pLevel, pPos, pPlayer);
	}

	private void breakAndCollect(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer) {
		if (pPlayer instanceof FakePlayer)
			return;
		if (pLevel.isClientSide())
			return;
		ItemStack cloneItemStack = getCloneItemStack(pLevel, pPos, pState, true);
		pLevel.destroyBlock(pPos, false);
		if (pLevel.getBlockState(pPos) != pState) {
			Inventory inv = pPlayer.getInventory();
			ItemStack selected = inv.getSelectedItem();
			if (selected.isEmpty()) {
				inv.setItem(inv.getSelectedSlot(), cloneItemStack);
			} else {
				inv.placeItemBackInInventory(cloneItemStack, Prediction.SERVER_ONLY);
			}
		}
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return applyComponentsToDropStack(new ItemStack(this), level.getBlockEntity(pos));
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!(level.getBlockEntity(pos) instanceof ClipboardBlockEntity cbe))
			return super.playerWillDestroy(level, pos, state, player);
		if (level.isClientSide() || player.isCreative())
			return super.playerWillDestroy(level, pos, state, player);
		Block.popResource(level, pos, applyComponentsToDropStack(new ItemStack(this), cbe));

		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
		if (!(pBuilder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ClipboardBlockEntity cbe))
			return super.getDrops(pState, pBuilder);
		ItemStack drop = applyComponentsToDropStack(new ItemStack(this), cbe);
		pBuilder.withDynamicDrop(ShulkerBoxBlock.CONTENTS, c -> c.accept(drop.copy()));
		return ImmutableList.of(drop);
	}

	private ItemStack applyComponentsToDropStack(ItemStack stack, @Nullable BlockEntity blockEntity) {
		if (blockEntity instanceof ClipboardBlockEntity cbe) {
			stack.applyComponents(cbe.components());
			return stack;
		}
		return stack;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		if (level.isClientSide())
			return;
		if (level.getBlockEntity(pos) instanceof ClipboardBlockEntity cbe)
			cbe.notifyUpdate();
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		updateWater(level, ticks, state, pos);
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	public Class<ClipboardBlockEntity> getBlockEntityClass() {
		return ClipboardBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ClipboardBlockEntity> getBlockEntityType() {
		return ToolsRegistry.CLIPBOARD_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	/** The side of the block the clipboard is attached to */
	private static Direction supportSide(BlockState state) {
		return getConnectedDirection(state).getOpposite();
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		return RedstoneClientStates.plate(supportSide(state), state.getValue(WATERLOGGED));
	}

	@Override
	public BlockState particleState() {
		return Blocks.OAK_PLANKS.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** Create's block_empty / block_written models, picked by the blockstate file. */
	public static class Model extends BaseModel {
		private final Part block;

		public Model(BlockState state) {
			super(state);
			block = addPart(ModelItems.block("clipboard/block_empty"));
			showVariant(block, "clipboard", state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(block, "clipboard", state);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}
}

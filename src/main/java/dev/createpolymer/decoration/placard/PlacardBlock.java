package dev.createpolymer.decoration.placard;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.schematic.requirement.SpecialBlockItemRequirement;
import dev.createpolymer.decoration.girder.GirderShapes;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.block.ProperWaterloggedBlock;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.logistics.filter.FilterItem;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.registry.feature.DoorsRegistry;
import dev.createpolymer.schematics.requirement.ItemRequirement.ItemUseType;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PlacardBlock extends FaceAttachedHorizontalDirectionalBlock
	implements SpecialBlockItemRequirement, ProperWaterloggedBlock, IBE<PlacardBlockEntity>, IWrenchable, PolymerModelBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	// Invisible thin plates on the side the placard sits on: players see (and bump into) a trapdoor-sized plate.
	private static final Map<Direction, @Nullable BlockState> CLIENT_DRY = new EnumMap<>(Direction.class);
	private static final Map<Direction, @Nullable BlockState> CLIENT_WET = new EnumMap<>(Direction.class);

	public PlacardBlock(Properties p_53182_) {
		super(p_53182_);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false)
			.setValue(POWERED, false));
	}

	/** Reserves the client states; must run while the mod initialises. */
	public static void requestClientStates() {
		for (Direction d : Direction.values()) {
			CLIENT_DRY.put(d, PolymerBlockResourceUtils.requestEmpty(BlockModelType.getTrapdoor(d, false)));
			CLIENT_WET.put(d, PolymerBlockResourceUtils.requestEmpty(BlockModelType.getTrapdoor(d, true)));
		}
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(FACE, FACING, WATERLOGGED, POWERED));
	}

	@Override
	protected boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		return canAttachLenient(pLevel, pPos, getConnectedDirection(pState).getOpposite());
	}

	public static boolean canAttachLenient(LevelReader pReader, BlockPos pPos, Direction pDirection) {
		BlockPos blockpos = pPos.relative(pDirection);
		return !pReader.getBlockState(blockpos)
			.getCollisionShape(pReader, blockpos)
			.isEmpty();
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext pContext) {
		BlockState stateForPlacement = super.getStateForPlacement(pContext);
		if (stateForPlacement == null)
			return null;
		if (stateForPlacement.getValue(FACE) == AttachFace.FLOOR)
			stateForPlacement = stateForPlacement.setValue(FACING, stateForPlacement.getValue(FACING)
				.getOpposite());
		return withWater(stateForPlacement, pContext);
	}

	@Override
	protected boolean isSignalSource(BlockState pState) {
		return true;
	}

	@Override
	protected int getSignal(BlockState pBlockState, BlockGetter pBlockAccess, BlockPos pPos, Direction pSide) {
		return pBlockState.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState pBlockState, BlockGetter pBlockAccess, BlockPos pPos, Direction pSide) {
		return pBlockState.getValue(POWERED) && getConnectedDirection(pBlockState) == pSide ? 15 : 0;
	}

	@Override
	protected VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return GirderShapes.PLACARD.get(getConnectedDirection(pState));
	}

	/** Polymer would otherwise answer with the collision of the barrier players see. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
		CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected BlockState updateShape(BlockState pState, LevelReader pLevel, ScheduledTickAccess ticks,
		BlockPos pCurrentPos, Direction pFacing, BlockPos pFacingPos, BlockState pFacingState, RandomSource random) {
		updateWater(pLevel, ticks, pState, pCurrentPos);
		return super.updateShape(pState, pLevel, ticks, pCurrentPos, pFacing, pFacingPos, pFacingState, random);
	}

	@Override
	protected FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player.isShiftKeyDown())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		ItemStack inHand = player.getItemInHand(hand);
		return onBlockEntityUseItemOn(level, pos, pte -> {
			ItemStack inBlock = pte.getHeldItem();

			if (!player.mayBuild() || inHand.isEmpty() || !inBlock.isEmpty()) {
				if (inBlock.isEmpty())
					return InteractionResult.FAIL;
				if (inHand.isEmpty())
					return InteractionResult.FAIL;
				if (state.getValue(POWERED))
					return InteractionResult.FAIL;

				boolean test = inBlock.getItem() instanceof FilterItem ? FilterItemStack.of(inBlock)
					.test(level, inHand) : ItemStack.isSameItemSameComponents(inHand, inBlock);
				if (!test) {
					// Create's AllSoundEvents.DENY
					level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1, .5f);
					return InteractionResult.SUCCESS;
				}

				// Create's AllSoundEvents.CONFIRM
				level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, .5f, .8f);
				level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
				updateNeighbours(state, level, pos);
				pte.pulse();
				return InteractionResult.SUCCESS;
			}

			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1, 1);
			pte.setHeldItem(inHand.copyWithCount(1));

			if (!player.isCreative()) {
				inHand.shrink(1);
				if (inHand.isEmpty())
					player.setItemInHand(hand, ItemStack.EMPTY);
			}

			return InteractionResult.SUCCESS;
		});
	}

	public static Direction connectedDirection(BlockState state) {
		return getConnectedDirection(state);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState pState, ServerLevel pLevel, BlockPos pPos,
		boolean movedByPiston) {
		if (!movedByPiston && pState.getValue(POWERED))
			updateNeighbours(pState, pLevel, pPos);
		super.affectNeighborsAfterRemoval(pState, pLevel, pPos, movedByPiston);
	}

	public static void updateNeighbours(BlockState pState, Level pLevel, BlockPos pPos) {
		pLevel.updateNeighborsAt(pPos, pState.getBlock());
		pLevel.updateNeighborsAt(pPos.relative(getConnectedDirection(pState).getOpposite()), pState.getBlock());
	}

	@Override
	protected void attack(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer) {
		if (pLevel.isClientSide())
			return;
		withBlockEntityDo(pLevel, pPos, pte -> {
			ItemStack heldItem = pte.getHeldItem();
			if (heldItem.isEmpty())
				return;
			pPlayer.getInventory()
				.placeItemBackInInventory(heldItem, Prediction.SERVER_ONLY);
			pLevel.playSound(null, pPos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1, 1);
			pte.setHeldItem(ItemStack.EMPTY);
		});
	}

	@Override
	public Class<PlacardBlockEntity> getBlockEntityClass() {
		return PlacardBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PlacardBlockEntity> getBlockEntityType() {
		return DoorsRegistry.PLACARD_BLOCK_ENTITY;
	}

	// ---- Polymer ----

	@Override
	public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
		Direction side = getConnectedDirection(state);
		BlockState client = (state.getValue(WATERLOGGED) ? CLIENT_WET : CLIENT_DRY).get(side);
		return client != null ? client : PolymerModelBlock.super.getPolymerBlockState(state, context);
	}

	@Override
	public BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * The placard plate (Create's blockstate) and, drawn like PlacardRenderer does, the item it holds.
	 */
	public static class Model extends BaseModel {
		private final Part plate;
		private final Part item = new Part(ItemStack.EMPTY);
		private ItemStack shownItem = ItemStack.EMPTY;
		private boolean itemAdded;
		private int shownVersion = -1;

		public Model(BlockState state) {
			super(state);
			plate = showVariant(addPart(ModelItems.block("placard")), "placard", state);
			item.element.setItemDisplayContext(ItemDisplayContext.FIXED);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(plate, "placard", state);
			shownVersion = -1;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof PlacardBlockEntity pbe))
				return;
			if (shownVersion == pbe.getDataVersion() && ItemStack.matches(shownItem, pbe.getHeldItem()))
				return;
			shownVersion = pbe.getDataVersion();
			ItemStack held = pbe.getHeldItem();
			shownItem = held.copy();
			if (held.isEmpty()) {
				if (itemAdded) {
					removeElement(item.element);
					itemAdded = false;
				}
				return;
			}
			if (!itemAdded) {
				addElement(item.element);
				itemAdded = true;
			}
			item.setItem(held.copyWithCount(1));
			item.setMatrix(itemTransform(state, held), 0);
		}

		/**
		 * PlacardRenderer's pose: turned to the face, 4.5/16 out of the plate and scaled down. The display entity turns
		 * its items half way about the vertical axis before the transform, which Create's renderer does not, hence the
		 * last turn.
		 */
		static Matrix4f itemTransform(BlockState state, ItemStack held) {
			Direction facing = state.getValue(FACING);
			AttachFace face = state.getValue(FACE);
			boolean blockItem = held.getItem() instanceof BlockItem;
			float scale = blockItem ? .5f : .375f;
			return new Matrix4f()
				.rotateY((face == AttachFace.CEILING ? (float) Math.PI : 0)
					+ AngleHelper.rad(180 + AngleHelper.horizontalAngle(facing)))
				.rotateX(face == AttachFace.CEILING ? (float) -Math.PI / 2 : face == AttachFace.FLOOR ? (float) Math.PI / 2 : 0)
				.translate(0, 0, 4.5f / 16f)
				.scale(scale)
				.rotateY((float) Math.PI);
		}
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity be) {
		ItemStack placardStack = new ItemStack(DoorsRegistry.PLACARD);
		if (be instanceof PlacardBlockEntity pbe) {
			ItemStack heldItem = pbe.getHeldItem();
			if (!heldItem.isEmpty()) {
				return new ItemRequirement(List.of(
					new ItemRequirement.StackRequirement(placardStack, ItemUseType.CONSUME),
					new ItemRequirement.StrictNbtStackRequirement(heldItem, ItemUseType.CONSUME)
				));
			}
		}
		return new ItemRequirement(ItemUseType.CONSUME, placardStack);
	}
}

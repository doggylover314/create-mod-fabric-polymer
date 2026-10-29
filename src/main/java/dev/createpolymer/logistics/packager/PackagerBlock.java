package dev.createpolymer.logistics.packager;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.box.PackageSounds;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.FactoryGaugeRegistry;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

public class PackagerBlock extends DirectionalBlock implements IBE<PackagerBlockEntity>, IWrenchable, PolymerModelBlock {

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty LINKED = BooleanProperty.create("linked");

	public PackagerBlock(Properties properties) {
		super(properties);
		BlockState defaultBlockState = defaultBlockState();
		if (defaultBlockState.hasProperty(LINKED))
			defaultBlockState = defaultBlockState.setValue(LINKED, false);
		registerDefaultState(defaultBlockState.setValue(POWERED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction preferredFacing = null;
		for (Direction face : context.getNearestLookingDirections()) {
			BlockEntity be = context.getLevel()
				.getBlockEntity(context.getClickedPos()
					.relative(face));
			if (be instanceof PackagerBlockEntity)
				continue;
			if (be != null && be.hasLevel() && ItemHandlerLookup.get(be.getLevel(), be.getBlockPos(), null) != null) {
				preferredFacing = face.getOpposite();
				break;
			}
		}

		Player player = context.getPlayer();
		if (preferredFacing == null) {
			Direction facing = context.getNearestLookingDirection();
			preferredFacing = player != null && player
				.isShiftKeyDown() ? facing : facing.getOpposite();
		}

		return super.getStateForPlacement(context).setValue(POWERED, context.getLevel()
				.hasNeighborSignal(context.getClickedPos()))
			.setValue(FACING, preferredFacing);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (stack.is(ModItems.WRENCH))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (stack.is(PackagesRegistry.STOCK_LINK_ITEM) && !(state.hasProperty(LINKED) && state.getValue(LINKED)))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (stack.is(FactoryGaugeRegistry.FACTORY_GAUGE_ITEM))
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (stack.is(PackagesRegistry.PACKAGE_FROGPORT_ITEM))
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		onBlockEntityUseItemOn(level, pos, be -> {
			if (be.heldBox.isEmpty()) {
				if (be.animationTicks > 0)
					return InteractionResult.SUCCESS;
				if (PackageItem.isPackage(stack)) {
					if (level.isClientSide())
						return InteractionResult.SUCCESS;
					if (!be.unwrapBox(stack.copy(), true))
						return InteractionResult.SUCCESS;
					be.unwrapBox(stack.copy(), false);
					be.triggerStockCheck();
					stack.shrink(1);
					PackageSounds.depotPlop(level, pos);
					if (stack.isEmpty())
						player.setItemInHand(hand, ItemStack.EMPTY);
					return InteractionResult.SUCCESS;
				}
				return InteractionResult.SUCCESS;
			}
			if (be.animationTicks > 0)
				return InteractionResult.SUCCESS;
			if (!level.isClientSide()) {
				player.getInventory()
					.placeItemBackInInventory(be.heldBox.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
				PackageSounds.itemPickup(player);
				be.heldBox = ItemStack.EMPTY;
				be.notifyUpdate();
			}
			return InteractionResult.SUCCESS;
		});

		return InteractionResult.SUCCESS;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(FACING, POWERED, LINKED));
	}

	/** 26.3 does not say which neighbour changed, so the behaviour is told that its target did. */
	@Override
	protected void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
		@Nullable Orientation orientation, boolean isMoving) {
		if (worldIn.isClientSide())
			return;

		InvManipulationBehaviour behaviour = BlockEntityBehaviour.get(worldIn, pos, InvManipulationBehaviour.TYPE);
		if (behaviour != null)
			behaviour.onNeighborChanged(behaviour.getTarget()
				.getConnectedPos());

		boolean previouslyPowered = state.getValue(POWERED);
		if (previouslyPowered == worldIn.hasNeighborSignal(pos))
			return;
		worldIn.setBlock(pos, state.cycle(POWERED), Block.UPDATE_CLIENTS);
		if (!previouslyPowered)
			withBlockEntityDo(worldIn, pos, PackagerBlockEntity::activate);
	}

	@Override
	public Class<PackagerBlockEntity> getBlockEntityClass() {
		return PackagerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
		return PackagesRegistry.PACKAGER_BLOCK_ENTITY;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState pState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState pState, Level pLevel, BlockPos pPos, Direction direction) {
		return getBlockEntityOptional(pLevel, pPos).map(pbe -> {
				boolean empty = pbe.inventory.getStackInSlot(0)
					.isEmpty();
				if (pbe.animationTicks != 0)
					empty = false;
				return empty ? 0 : 15;
			})
			.orElse(0);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.IRON_BLOCK.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state, false);
	}

	/** The packager's body, its hatch and tray, and the box riding on the tray (Create's PackagerRenderer). */
	public static class Model extends BaseModel {
		private final String blockstateFile;
		private final String trayModel;
		private final Part body;
		private final Part hatch;
		private final Part tray;
		private final Part box;
		private final ItemStack hatchOpen = ModelItems.block("packager/hatch_open");
		private final ItemStack hatchClosed = ModelItems.block("packager/hatch_closed");
		private @Nullable Boolean lastOpen;
		private float lastTrayOffset = Float.NaN;
		private ItemStack lastBox = ItemStack.EMPTY;

		public Model(BlockState state, boolean repackager) {
			super(state);
			blockstateFile = repackager ? "repackager" : "packager";
			trayModel = repackager ? "repackager/tray" : "packager/tray";
			body = addPart(ModelItems.block(repackager ? "repackager/block" : "packager/block"));
			hatch = addPart(hatchClosed);
			tray = addPart(ModelItems.block(trayModel));
			box = addPart(ItemStack.EMPTY);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			showVariant(body, blockstateFile, state);
			Direction facing = state.getValue(FACING)
				.getOpposite();
			hatch.setMatrix(new Matrix4f().translate(facing.getStepX() * .49999f, facing.getStepY() * .49999f,
				facing.getStepZ() * .49999f)
				.rotateY((float) Math.toRadians(AngleHelper.horizontalAngle(facing)))
				.rotateX((float) Math.toRadians(AngleHelper.verticalAngle(facing))), 0);
			lastTrayOffset = Float.NaN;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof PackagerBlockEntity packager))
				return;
			boolean open = isHatchOpen(packager);
			if (lastOpen == null || open != lastOpen) {
				lastOpen = open;
				hatch.setItem(open ? hatchOpen : hatchClosed);
			}

			ItemStack renderedBox = packager.getRenderedBox();
			ItemStack shownBox = renderedBox.isEmpty() ? ItemStack.EMPTY : new ItemStack(renderedBox.getItem());
			float trayOffset = packager.getTrayOffset(0);
			boolean boxChanged = !ItemStack.isSameItem(lastBox, shownBox);
			if (trayOffset == lastTrayOffset && !boxChanged)
				return;
			boolean fresh = Float.isNaN(lastTrayOffset);
			lastTrayOffset = trayOffset;

			Direction facing = state.getValue(FACING)
				.getOpposite();
			float yRot = facing.getAxis()
				.isHorizontal() ? facing.toYRot() : 0;
			Matrix4f trayTransform = new Matrix4f()
				.translate(facing.getStepX() * trayOffset, facing.getStepY() * trayOffset, facing.getStepZ() * trayOffset)
				.rotateY((float) Math.toRadians(yRot));
			tray.setMatrix(trayTransform, fresh ? 0 : 1);

			if (boxChanged) {
				lastBox = shownBox;
				box.setItem(shownBox);
			}
			if (!shownBox.isEmpty()) {
				// Create draws the box with the FIXED display context at 1.49 times, which halves the model first
				box.setMatrix(new Matrix4f(trayTransform).translate(0, 2 / 16f, 0)
					.scale(1.49f * .5f), boxChanged ? 0 : 1);
			}
		}

		public static boolean isHatchOpen(PackagerBlockEntity be) {
			return be.animationTicks > (be.animationInward ? 1 : 5)
				&& be.animationTicks < PackagerBlockEntity.CYCLE - (be.animationInward ? 5 : 1);
		}
	}
}

package dev.createpolymer.logistics.vault;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import dev.createpolymer.api.connectivity.ConnectivityHandler;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.registry.feature.VaultRegistry;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class ItemVaultBlock extends Block implements IWrenchable, IBE<ItemVaultBlockEntity>, PolymerModelBlock {

	public static final Property<Axis> HORIZONTAL_AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final BooleanProperty LARGE = BooleanProperty.create("large");

	public ItemVaultBlock(Properties p_i48440_1_) {
		super(p_i48440_1_);
		registerDefaultState(defaultBlockState().setValue(LARGE, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		pBuilder.add(HORIZONTAL_AXIS, LARGE);
		super.createBlockStateDefinition(pBuilder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext pContext) {
		if (pContext.getPlayer() == null || !pContext.getPlayer()
			.isShiftKeyDown()) {
			BlockState placedOn = pContext.getLevel()
				.getBlockState(pContext.getClickedPos()
					.relative(pContext.getClickedFace()
						.getOpposite()));
			Axis preferredAxis = getVaultBlockAxis(placedOn);
			if (preferredAxis != null)
				return this.defaultBlockState()
					.setValue(HORIZONTAL_AXIS, preferredAxis);
		}
		return this.defaultBlockState()
			.setValue(HORIZONTAL_AXIS, pContext.getHorizontalDirection()
				.getAxis());
	}

	@Override
	protected void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
		if (pOldState.getBlock() == pState.getBlock())
			return;
		if (pIsMoving)
			return;
		withBlockEntityDo(pLevel, pPos, ItemVaultBlockEntity::updateConnectivity);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (context.getClickedFace()
			.getAxis()
			.isVertical()) {
			BlockEntity be = level
				.getBlockEntity(pos);
			if (be instanceof ItemVaultBlockEntity vault) {
				ConnectivityHandler.splitMulti(vault);
				vault.removeController(true);
			}
			state = state.setValue(LARGE, false);
		}

		// Create's IWrenchable default: turn the block around the clicked face
		BlockState rotated = state;
		if (context.getClickedFace().getAxis() == Axis.Y)
			rotated = state.setValue(HORIZONTAL_AXIS, state.getValue(HORIZONTAL_AXIS) == Axis.X ? Axis.Z : Axis.X);
		if (!rotated.canSurvive(level, pos))
			return InteractionResult.PASS;

		KineticBlockEntity.switchToBlockState(level, pos, Block.updateFromNeighbourShapes(rotated, level, pos));
		if (level.getBlockState(pos) != state)
			ModSounds.wrenchRotate(level, pos);
		return InteractionResult.SUCCESS;
	}

	public static boolean isVault(BlockState state) {
		return state.is(VaultRegistry.ITEM_VAULT);
	}

	@Nullable
	public static Axis getVaultBlockAxis(BlockState state) {
		if (!isVault(state))
			return null;
		return state.getValue(HORIZONTAL_AXIS);
	}

	public static boolean isLarge(BlockState state) {
		if (!isVault(state))
			return false;
		return state.getValue(LARGE);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		Axis axis = state.getValue(HORIZONTAL_AXIS);
		return state.setValue(HORIZONTAL_AXIS, rot.rotate(Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE))
			.getAxis());
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state;
	}

	// Vaults are less noisy when placed in batch
	public static final SoundType SILENCED_METAL =
		new SoundType(0.1F, 1.5F, SoundEvents.NETHERITE_BLOCK_BREAK, SoundEvents.NETHERITE_BLOCK_STEP,
			SoundEvents.NETHERITE_BLOCK_PLACE, SoundEvents.NETHERITE_BLOCK_HIT,
			SoundEvents.NETHERITE_BLOCK_FALL);

	/** Set by {@link ItemVaultItem} while it places the rest of a layer (Create flags the player's persistent data). */
	static boolean silenceVaultSound = false;

	@Override
	protected SoundType getSoundType(BlockState state) {
		if (silenceVaultSound)
			return SILENCED_METAL;
		return super.getSoundType(state);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState p_149740_1_) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState pState, Level pLevel, BlockPos pPos, Direction direction) {
		if (getBlockEntity(pLevel, pPos) == null)
			return 0;
		return ItemHandlerHelper.calcRedstoneFromInventory(ItemHandlerLookup.get(pLevel, pPos, null));
	}

	@Override
	public BlockEntityType<? extends ItemVaultBlockEntity> getBlockEntityType() {
		return VaultRegistry.ITEM_VAULT_BLOCK_ENTITY;
	}

	@Override
	public Class<ItemVaultBlockEntity> getBlockEntityClass() {
		return ItemVaultBlockEntity.class;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.NETHERITE_BLOCK.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/**
	 * Vanilla clients cannot do Create's connected textures, so each vault block shows one cube model chosen by where
	 * it sits in its multiblock. The model has the connected-texture tile of every face baked in:
	 * {@code item_vault/polymer_<axis><size>_<bits>}, generated by tools/asset-scripts/make_vault_models.py.
	 * The models are authored for a vault along Z; vaults along X use the same models turned by 90 degrees.
	 */
	public static class Model extends BaseModel {
		private static final Quaternionf ALONG_X = Orientation.variant(0, 90);

		private final Part cube;
		private String shown = "";
		private int lastVersion = -1;

		public Model(BlockState state) {
			super(state);
			cube = addPart(ModelItems.block(modelName(state, BlockPos.ZERO, BlockPos.ZERO, 1, 1)));
			applyAxis(state);
		}

		private void applyAxis(BlockState state) {
			Axis axis = getVaultBlockAxis(state);
			cube.orientation(axis == Axis.X ? ALONG_X : new Quaternionf()).apply(0);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			applyAxis(state);
			lastVersion = -1;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof ItemVaultBlockEntity vault) || vault.getDataVersion() == lastVersion)
				return;
			lastVersion = vault.getDataVersion();
			ItemVaultBlockEntity controller = vault.getControllerBE();
			String name = controller == null ? modelName(vault.getBlockState(), pos, pos, 1, 1)
				: modelName(vault.getBlockState(), pos, controller.getBlockPos(), controller.radius, controller.length);
			if (name.equals(shown))
				return;
			shown = name;
			cube.setItem(ModelItems.block(name));
		}

		/**
		 * Model path (under models/block) for the vault block at {@code pos} of a multiblock whose controller is at
		 * {@code origin}, {@code width} blocks across and {@code length} long.
		 */
		public static String modelName(BlockState state, BlockPos pos, BlockPos origin, int width, int length) {
			Axis axis = getVaultBlockAxis(state);
			if (axis == null)
				axis = Axis.Z;
			int dx = pos.getX() - origin.getX();
			int dy = pos.getY() - origin.getY();
			int dz = pos.getZ() - origin.getZ();
			int along = axis == Axis.X ? dx : dz;
			int across = axis == Axis.X ? dz : dx;
			if (width < 1 || width > 3 || length < 1 || length > 3 * width || along < 0 || along >= length || across < 0
				|| across >= width || dy < 0 || dy >= width) {
				width = 1;
				length = 1;
				along = across = dy = 0;
			}
			// Neighbours in the frame of the models, which run along Z. A vault along X is such a model turned 90 degrees
			// clockwise seen from above: the model's x runs along world z and its z along world -x, so its ends swap.
			boolean xLow = across > 0, xHigh = across < width - 1;
			boolean yLow = dy > 0, yHigh = dy < width - 1;
			boolean zLow = axis == Axis.X ? along < length - 1 : along > 0;
			boolean zHigh = axis == Axis.X ? along > 0 : along < length - 1;
			return "item_vault/polymer_" + (width > 2 ? "l" : "m") + "_" + bit(xLow) + bit(xHigh) + bit(yLow)
				+ bit(yHigh) + bit(zLow) + bit(zHigh);
		}

		private static char bit(boolean b) {
			return b ? '1' : '0';
		}
	}
}

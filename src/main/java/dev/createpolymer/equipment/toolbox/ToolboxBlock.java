package dev.createpolymer.equipment.toolbox;

import java.util.Optional;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.util.VoxelShaper;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's ToolboxBlock (MIT, The Create Team). Right-click opens the toolbox, right-click with a dye
 * recolors it, hitting it picks it up with its contents.
 */
public class ToolboxBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, IBE<ToolboxBlockEntity>, PolymerModelBlock {

	protected final DyeColor color;

	/** Create's AllShapes.TOOLBOX */
	public static final VoxelShaper SHAPE = VoxelShaper.forHorizontal(ShapeUtil.box(1, 0, 4, 15, 9, 12), Direction.NORTH);

	public ToolboxBlock(Properties properties, DyeColor color) {
		super(properties);
		this.color = color;
		registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(BlockStateProperties.WATERLOGGED)
			.add(FACING));
	}

	@Override
	public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(worldIn, pos, state, placer, stack);
		if (worldIn.isClientSide())
			return;
		if (stack == null)
			return;
		withBlockEntityDo(worldIn, pos, be -> {
			be.readInventory(stack.get(EquipmentRegistry.TOOLBOX_INVENTORY));
			if (stack.has(EquipmentRegistry.TOOLBOX_UUID))
				be.setUniqueId(stack.get(EquipmentRegistry.TOOLBOX_UUID));
			if (stack.has(DataComponents.CUSTOM_NAME))
				be.setCustomName(stack.getHoverName());
		});
	}

	/** Create's onRemove: recoloring a toolbox keeps its block entity */
	@Override
	protected boolean shouldChangedStateKeepBlockEntity(BlockState oldState) {
		return oldState.getBlock() instanceof ToolboxBlock;
	}

	@Override
	protected void attack(BlockState state, Level world, BlockPos pos, Player player) {
		if (world.isClientSide())
			return;
		if (world instanceof ServerLevel) {
			ItemStack cloneItemStack = getCloneItemStack(world, pos, state, true);
			withBlockEntityDo(world, pos, i -> {
				cloneItemStack.applyComponents(i.collectComponents());
			});
			world.destroyBlock(pos, false);
			if (world.getBlockState(pos) != state)
				player.getInventory()
					.placeItemBackInInventory(cloneItemStack, Prediction.SERVER_ONLY);
		}
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		ItemStack item = new ItemStack(this);
		Optional<ToolboxBlockEntity> blockEntityOptional = getBlockEntityOptional(level, pos);

		blockEntityOptional.map(tb -> {
			item.set(EquipmentRegistry.TOOLBOX_INVENTORY, tb.inventory.copy());
			return tb;
		});

		blockEntityOptional.map(ToolboxBlockEntity::getUniqueId)
			.ifPresent(uid -> item.set(EquipmentRegistry.TOOLBOX_UUID, uid));
		blockEntityOptional.map(ToolboxBlockEntity::getCustomName)
			.ifPresent(name -> item.set(DataComponents.CUSTOM_NAME, name));
		return item;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SHAPE.get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SHAPE.get(state.getValue(FACING));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hitResult) {
		if (player == null || player.isCrouching())
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		DyeColor color = stack.get(DataComponents.DYE);
		if (color != null && color != this.color) {
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			BlockState newState = EquipmentRegistry.TOOLBOXES.get(color)
				.defaultBlockState()
				.setValue(FACING, state.getValue(FACING))
				.setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
			level.setBlockAndUpdate(pos, newState);
			return InteractionResult.SUCCESS;
		}

		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer))
			return InteractionResult.SUCCESS;

		withBlockEntityDo(level, pos, toolbox -> new ToolboxGui(serverPlayer, toolbox).open());
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState ifluidstate = context.getLevel()
			.getFluidState(context.getClickedPos());
		return super.getStateForPlacement(context)
			.setValue(FACING, context.getHorizontalDirection()
				.getOpposite())
			.setValue(BlockStateProperties.WATERLOGGED, ifluidstate.getType() == Fluids.WATER);
	}

	@Override
	public Class<ToolboxBlockEntity> getBlockEntityClass() {
		return ToolboxBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ToolboxBlockEntity> getBlockEntityType() {
		return EquipmentRegistry.TOOLBOX;
	}

	public DyeColor getColor() {
		return color;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState pState) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState pState, Level pLevel, BlockPos pPos, Direction direction) {
		return ItemHelper.calcRedstoneFromBlockEntity(this, pLevel, pPos);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.DARK_OAK_PLANKS.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** The box, its lid and the two drawers that slide out (ToolBoxVisual) */
	public static class Model extends BaseModel {
		private final Part box;
		private final Part lid;
		private final Part[] drawers = new Part[2];

		private float lastLidAngle = Float.NaN;
		private float lastDrawerOffset = Float.NaN;

		public Model(BlockState state) {
			super(state);
			DyeColor color = ((ToolboxBlock) state.getBlock()).getColor();
			box = addPart(ModelItems.block(color.getSerializedName() + "_toolbox"));
			lid = addPart(ModelItems.block("toolbox/lid/" + color.getSerializedName()));
			for (int i = 0; i < 2; i++)
				drawers[i] = addPart(ModelItems.block("toolbox/drawer"));
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			box.orientation(Orientation.forHorizontal(state.getValue(FACING)))
				.apply(0);
			lastLidAngle = Float.NaN;
			lastDrawerOffset = Float.NaN;
			animate(0, 0);
		}

		private void animate(float lidAngle, float drawerOffset) {
			var rotation = Orientation.forHorizontal(state.getValue(FACING));

			if (lidAngle != lastLidAngle) {
				// rotates around the hinge at (0, 6, 12) of the box
				float py = 6 / 16f - .5f, pz = 12 / 16f - .5f;
				Matrix4f m = new Matrix4f().rotate(rotation)
					.translate(0, py, pz)
					.rotateX((float) Math.toRadians(135 * lidAngle))
					.translate(0, -py, -pz);
				lid.setMatrix(m, 1);
			}

			if (drawerOffset != lastDrawerOffset) {
				for (int offset = 0; offset < 2; offset++) {
					Matrix4f m = new Matrix4f().rotate(rotation)
						.translate(0, offset * 1 / 8f, -drawerOffset * .175f * (2 - offset));
					drawers[offset].setMatrix(m, 1);
				}
			}

			lastLidAngle = lidAngle;
			lastDrawerOffset = drawerOffset;
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof ToolboxBlockEntity toolbox))
				return;
			animate(toolbox.lid.getValue(1), toolbox.drawers.getValue(1));
		}
	}
}

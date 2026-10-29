package dev.createpolymer.equipment.armor;

import java.util.List;
import java.util.Optional;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.api.schematic.requirement.SpecialBlockItemRequirement;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.kinetics.base.HorizontalKineticBlock;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import dev.createpolymer.schematics.requirement.ItemRequirement.ItemUseType;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Orientation;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's BacktankBlock (MIT, The Create Team). Right-clicking the placed backtank with an empty chest
 * slot puts it on, with its air and enchantments.
 */
public class BacktankBlock extends HorizontalKineticBlock implements SpecialBlockItemRequirement, IBE<BacktankBlockEntity>, SimpleWaterloggedBlock {

	/** Create's AllShapes.BACKTANK */
	public static final VoxelShape SHAPE = Shapes.or(ShapeUtil.box(3, 0, 3, 13, 12, 13), ShapeUtil.box(5, 0, 5, 11, 16, 11));

	public BacktankBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false)
			: Fluids.EMPTY.defaultFluidState();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
		return getBlockEntityOptional(world, pos).map(BacktankBlockEntity::getComparatorOutput)
			.orElse(0);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluidState = context.getLevel()
			.getFluidState(context.getClickedPos());
		return super.getStateForPlacement(context).setValue(BlockStateProperties.WATERLOGGED,
			fluidState.getType() == Fluids.WATER);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == Direction.UP;
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return Axis.Y;
	}

	@Override
	public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(worldIn, pos, state, placer, stack);
		if (worldIn.isClientSide())
			return;
		if (stack == null)
			return;
		withBlockEntityDo(worldIn, pos, be -> {
			be.setCapacityEnchantLevel(EquipmentRegistry.capacityLevel(worldIn, stack));
			be.setAirLevel(stack.getOrDefault(EquipmentRegistry.BACKTANK_AIR, 0));
			if (stack.has(DataComponents.CUSTOM_NAME))
				be.setCustomName(stack.getHoverName());

			be.setComponentPatch(stack.getComponentsPatch());
		});
	}

	@Override
	protected List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
		List<ItemStack> lootDrops = super.getDrops(pState, pBuilder);

		BlockEntity blockEntity = pBuilder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (!(blockEntity instanceof BacktankBlockEntity bbe))
			return lootDrops;

		DataComponentPatch components = bbe.getComponentPatch()
			.forget(c -> c.equals(EquipmentRegistry.BACKTANK_AIR));
		if (components.isEmpty())
			return lootDrops;

		return lootDrops.stream()
			.peek(stack -> {
				if (stack.getItem() instanceof BacktankItem)
					stack.applyComponents(components);
			})
			.toList();
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (player == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (player.isShiftKeyDown())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (player.getMainHandItem()
			.getItem() instanceof BlockItem)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!player.getItemBySlot(EquipmentSlot.CHEST)
			.isEmpty())
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!level.isClientSide()) {
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .75f, 1);
			player.setItemSlot(EquipmentSlot.CHEST, getCloneItemStack(level, pos, state, true));
			level.destroyBlock(pos, false);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader pLevel, BlockPos pos, BlockState state, boolean includeData) {
		Item item = asItem();
		if (item instanceof BacktankItem.BacktankBlockItem placeable)
			item = placeable.getActualItem();

		Optional<BacktankBlockEntity> blockEntityOptional = getBlockEntityOptional(pLevel, pos);

		DataComponentPatch components = blockEntityOptional.map(BacktankBlockEntity::getComponentPatch)
			.orElse(DataComponentPatch.EMPTY);
		int air = blockEntityOptional.map(BacktankBlockEntity::getAirLevel)
			.orElse(0);

		ItemStack stack = new ItemStack(item.builtInRegistryHolder(), 1, components);
		stack.set(EquipmentRegistry.BACKTANK_AIR, air);
		return stack;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public Class<BacktankBlockEntity> getBlockEntityClass() {
		return BacktankBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends BacktankBlockEntity> getBlockEntityType() {
		return EquipmentRegistry.BACKTANK;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	// ---- Polymer ----

	@Override
	protected BlockState particleState() {
		return Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState();
	}

	@Override
	protected ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	/** The tank, the shaft on top (turns with the rotation) and the cogs on its back (BacktankRenderer). */
	public static class Model extends BaseModel {
		private final Part tank;
		private final Part shaft;
		private final Part cogs;
		private final String name;

		private float lastSpeed;
		private long nextUpdate = Long.MIN_VALUE;

		public Model(BlockState state) {
			super(state);
			name = state.is(EquipmentRegistry.NETHERITE_BACKTANK) ? "netherite_backtank" : "copper_backtank";
			tank = addPart(ModelItems.block(name + "/block"));
			shaft = addPart(ModelItems.block(name + "/block_shaft_input"));
			cogs = addPart(ModelItems.block(name + "/block_cogs"));
			shaft.spinAxis(Axis.Y);
			onStateChanged(state);
		}

		@Override
		protected void onStateChanged(BlockState state) {
			tank.orientation(Orientation.forHorizontal(state.getValue(HORIZONTAL_FACING)))
				.apply(0);
			shaft.apply(0);
			setCogs(0, 0);
		}

		/** Cogs spin around the east-west axis at (0, 6.5, 11) of the tank, turned to the block's facing. */
		private void setCogs(float angleDegrees, int interpolation) {
			Direction facing = state.getValue(HORIZONTAL_FACING);
			float yaw = 180 + AngleHelper.horizontalAngle(facing);
			Matrix4f m = new Matrix4f().rotateY((float) Math.toRadians(yaw))
				.translate(0, 6.5f / 16 - .5f, 11f / 16 - .5f)
				.rotateX((float) Math.toRadians(angleDegrees))
				.translate(0, -(6.5f / 16 - .5f), -(11f / 16 - .5f));
			cogs.setMatrix(m, interpolation);
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
			if (!(be instanceof KineticBlockEntity kbe))
				return;
			float speed = kbe.getSpeed();
			shaft.spin(time, speed, Orientation.cogOffset(false, Axis.Y, pos.getX(), pos.getY(), pos.getZ()));

			if (speed == 0) {
				if (lastSpeed != 0) {
					lastSpeed = 0;
					setCogs(0, 0);
				}
				return;
			}
			float degPerTick = Math.abs(speed) / 4f;
			int interval = Mth.clamp((int) (90f / degPerTick), 1, 10);
			if (speed != lastSpeed || time >= nextUpdate) {
				lastSpeed = speed;
				nextUpdate = time + interval;
				setCogs(((time + interval) * speed / 4f) % 360f, interval);
			}
		}
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity) {
		Item item = asItem();
		if (item instanceof BacktankItem.BacktankBlockItem placeable)
			item = placeable.getActualItem();
		return new ItemRequirement(ItemUseType.CONSUME, item);
	}
}

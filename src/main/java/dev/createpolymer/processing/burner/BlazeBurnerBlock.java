package dev.createpolymer.processing.burner;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.createpolymer.api.schematic.requirement.SpecialBlockItemRequirement;
import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.schematics.requirement.ItemRequirement.ItemUseType;
import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.util.Lang;
import dev.createpolymer.util.ShapeUtil;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ported from Create's BlazeBurnerBlock (MIT, The Create Team). */
public class BlazeBurnerBlock extends HorizontalDirectionalBlock implements SpecialBlockItemRequirement, IBE<BlazeBurnerBlockEntity>, IWrenchable, PolymerModelBlock {

	public static final EnumProperty<HeatLevel> HEAT_LEVEL = EnumProperty.create("blaze", HeatLevel.class);

	public static final VoxelShape HEATER_BLOCK_SHAPE = ShapeUtil.box(1, 0, 1, 15, 14, 15);
	public static final VoxelShape HEATER_BLOCK_SPECIAL_COLLISION_SHAPE = ShapeUtil.box(0, 0, 0, 16, 4, 16);

	public BlazeBurnerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(HEAT_LEVEL, HeatLevel.NONE));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(HEAT_LEVEL, FACING);
	}

	@Override
	protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (world.isClientSide())
			return;
		BlockEntity blockEntity = world.getBlockEntity(pos.above());
		if (!(blockEntity instanceof BasinBlockEntity basin))
			return;
		basin.notifyChangeOfContents();
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return getLitOrUnlitStack(state);
	}

	@Override
	public Class<BlazeBurnerBlockEntity> getBlockEntityClass() {
		return BlazeBurnerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends BlazeBurnerBlockEntity> getBlockEntityType() {
		return BurnerRegistry.HEATER;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		if (state.getValue(HEAT_LEVEL) == HeatLevel.NONE)
			return null;
		return IBE.super.newBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		HeatLevel heat = state.getValue(HEAT_LEVEL);

		if (stack.is(ModItems.GOGGLES) && heat != HeatLevel.NONE)
			return onBlockEntityUseItemOn(level, pos, bbte -> {
				if (bbte.goggles)
					return InteractionResult.TRY_WITH_EMPTY_HAND;
				bbte.goggles = true;
				bbte.notifyUpdate();
				return InteractionResult.SUCCESS;
			});

		if (stack.isEmpty() && heat != HeatLevel.NONE)
			return onBlockEntityUseItemOn(level, pos, bbte -> {
				if (!bbte.goggles)
					return InteractionResult.TRY_WITH_EMPTY_HAND;
				bbte.goggles = false;
				bbte.notifyUpdate();
				return InteractionResult.SUCCESS;
			});

		if (heat == HeatLevel.NONE) {
			if (stack.getItem() instanceof FlintAndSteelItem) {
				level.playSound(player, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F,
					level.getRandom().nextFloat() * 0.4F + 0.8F);
				if (level.isClientSide())
					return InteractionResult.SUCCESS;
				stack.hurtAndBreak(1, player, hand);
				level.setBlockAndUpdate(pos, BurnerRegistry.LIT_BLAZE_BURNER.defaultBlockState());
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		boolean doNotConsume = player.isCreative();
		boolean forceOverflow = !(player instanceof FakePlayer);

		TryInsertResult res = tryInsert(state, level, pos, stack, doNotConsume, forceOverflow, false);
		ItemStack leftover = res.object();
		if (!level.isClientSide() && !doNotConsume && !leftover.isEmpty()) {
			if (stack.isEmpty()) {
				player.setItemInHand(hand, leftover);
			} else if (!player.getInventory()
				.add(leftover)) {
				player.drop(leftover, false, Prediction.SERVER_ONLY);
			}
		}

		return res.result() == InteractionResult.SUCCESS ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** Create's InteractionResultHolder&lt;ItemStack&gt;: the result plus the container item left over from the fuel. */
	public record TryInsertResult(InteractionResult result, ItemStack object) {
		public static TryInsertResult success(ItemStack stack) {
			return new TryInsertResult(InteractionResult.SUCCESS, stack);
		}

		public static TryInsertResult fail(ItemStack stack) {
			return new TryInsertResult(InteractionResult.FAIL, stack);
		}
	}

	public static TryInsertResult tryInsert(BlockState state, Level world, BlockPos pos,
											ItemStack stack, boolean doNotConsume, boolean forceOverflow, boolean simulate) {
		if (!state.hasBlockEntity())
			return TryInsertResult.fail(ItemStack.EMPTY);

		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof BlazeBurnerBlockEntity burnerBE))
			return TryInsertResult.fail(ItemStack.EMPTY);

		if (burnerBE.isCreativeFuel(stack)) {
			if (!simulate)
				burnerBE.applyCreativeFuel();
			return TryInsertResult.success(ItemStack.EMPTY);
		}
		if (!burnerBE.tryUpdateFuel(stack, forceOverflow, simulate))
			return TryInsertResult.fail(ItemStack.EMPTY);

		if (!doNotConsume) {
			ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
			ItemStack container = remainder != null ? remainder.create() : ItemStack.EMPTY;
			if (!world.isClientSide()) {
				stack.shrink(1);
			}
			return TryInsertResult.success(container);
		}
		return TryInsertResult.success(ItemStack.EMPTY);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		ItemStack stack = context.getItemInHand();
		Item item = stack.getItem();
		BlockState defaultState = defaultBlockState();
		if (!(item instanceof BlazeBurnerBlockItem))
			return defaultState;
		HeatLevel initialHeat =
			((BlazeBurnerBlockItem) item).hasCapturedBlaze() ? HeatLevel.SMOULDERING : HeatLevel.NONE;
		return defaultState.setValue(HEAT_LEVEL, initialHeat)
			.setValue(FACING, context.getHorizontalDirection()
				.getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter reader, BlockPos pos, CollisionContext context) {
		return HEATER_BLOCK_SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState p_220071_1_, BlockGetter p_220071_2_, BlockPos p_220071_3_,
										   CollisionContext p_220071_4_) {
		if (p_220071_4_ == CollisionContext.empty())
			return HEATER_BLOCK_SPECIAL_COLLISION_SHAPE;
		return getShape(p_220071_1_, p_220071_2_, p_220071_3_, p_220071_4_);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState p_149740_1_) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level p_180641_2_, BlockPos p_180641_3_, net.minecraft.core.Direction direction) {
		return Math.max(0, state.getValue(HEAT_LEVEL)
			.ordinal() - 1);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	/** Replaces Create's ProjectileImpactEvent handler: thrown eggs are eaten by a burner holding a blaze. */
	@Override
	protected void onProjectileHit(Level level, BlockState state, BlockHitResult blockHit, Projectile projectile) {
		super.onProjectileHit(level, state, blockHit, projectile);
		BlazeBurnerHandler.thrownEggsGetEatenByBurner(level, blockHit, projectile);
		BlazeBurnerHandler.splashExtinguishesBurner(level, blockHit, projectile);
	}

	public static HeatLevel getHeatLevelOf(BlockState blockState) {
		return blockState.hasProperty(BlazeBurnerBlock.HEAT_LEVEL) ? blockState.getValue(BlazeBurnerBlock.HEAT_LEVEL)
			: HeatLevel.NONE;
	}

	public static int getLight(BlockState state) {
		HeatLevel level = state.getValue(HEAT_LEVEL);
		return switch (level) {
			case NONE -> 0;
			case SMOULDERING -> 8;
			default -> 15;
		};
	}

	private static ItemStack getLitOrUnlitStack(BlockState state) {
		boolean isLit = state.getValue(HEAT_LEVEL) != HeatLevel.NONE;
		return new ItemStack(isLit ? BurnerRegistry.BLAZE_BURNER_ITEM : BurnerRegistry.EMPTY_BLAZE_BURNER);
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new BlazeBurnerModel(state);
	}

	public enum HeatLevel implements StringRepresentable {
		NONE, SMOULDERING, FADING, KINDLED, SEETHING;

		public static final Codec<HeatLevel> CODEC = StringRepresentable.fromEnum(HeatLevel::values);

		public static HeatLevel byIndex(int index) {
			return values()[index];
		}

		public HeatLevel nextActiveLevel() {
			return byIndex(ordinal() % (values().length - 1) + 1);
		}

		public boolean isAtLeast(HeatLevel heatLevel) {
			return this.ordinal() >= heatLevel.ordinal();
		}

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity) {
		return new ItemRequirement(ItemUseType.CONSUME, getLitOrUnlitStack(state));
	}
}

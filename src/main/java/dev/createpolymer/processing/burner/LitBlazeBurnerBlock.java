package dev.createpolymer.processing.burner;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.item.IWrenchable;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.util.Lang;
import dev.createpolymer.visual.BaseModel;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import dev.createpolymer.visual.PolymerModelBlock;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ported from Create's LitBlazeBurnerBlock (MIT, The Create Team).
 * Create's EXTINGUISH_FLAME_ACTION item ability (a NeoForge concept) is replaced by the shovels item tag.
 */
public class LitBlazeBurnerBlock extends Block implements IWrenchable, PolymerModelBlock {

	public static final EnumProperty<FlameType> FLAME_TYPE = EnumProperty.create("flame_type", FlameType.class);

	public LitBlazeBurnerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FLAME_TYPE, FlameType.REGULAR));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FLAME_TYPE);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (stack.is(ItemTags.SHOVELS)) {
			level.playSound(player, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.5f, 2);
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			level.setBlockAndUpdate(pos, BurnerRegistry.BLAZE_BURNER.defaultBlockState());
			return InteractionResult.SUCCESS;
		}

		if (state.getValue(FLAME_TYPE) == FlameType.REGULAR) {
			if (stack.is(ItemTags.SOUL_FIRE_BASE_BLOCKS)) {
				level.playSound(player, pos, SoundEvents.SOUL_SAND_PLACE, SoundSource.BLOCKS, 1.0f, level.getRandom().nextFloat() * 0.4F + 0.8F);
				if (level.isClientSide())
					return InteractionResult.SUCCESS;
				level.setBlockAndUpdate(pos, defaultBlockState().setValue(FLAME_TYPE, FlameType.SOUL));
				return InteractionResult.SUCCESS;
			}
		}

		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter reader, BlockPos pos, CollisionContext context) {
		return BlazeBurnerBlock.HEATER_BLOCK_SHAPE;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(BurnerRegistry.EMPTY_BLAZE_BURNER);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState p_149740_1_) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level p_180641_2_, BlockPos p_180641_3_, net.minecraft.core.Direction direction) {
		return state.getValue(FLAME_TYPE) == FlameType.REGULAR ? 1 : 2;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter reader, BlockPos pos,
										   CollisionContext context) {
		if (context == CollisionContext.empty())
			return BlazeBurnerBlock.HEATER_BLOCK_SPECIAL_COLLISION_SHAPE;
		return getShape(state, reader, pos, context);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	/** Replaces Create's ProjectileImpactEvent handler: water splashes put the flame out. */
	@Override
	protected void onProjectileHit(Level level, BlockState state, BlockHitResult blockHit, Projectile projectile) {
		super.onProjectileHit(level, state, blockHit, projectile);
		BlazeBurnerHandler.splashExtinguishesBurner(level, blockHit, projectile);
	}

	public static int getLight(BlockState state) {
		if (state.getValue(FLAME_TYPE) == FlameType.SOUL)
			return 9;
		else
			return 12;
	}

	// ---- Polymer ----

	@Override
	public BlockState particleState() {
		return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
	}

	@Override
	public ElementHolder createModel(ServerLevel world, BlockPos pos, BlockState state) {
		return new Model(state);
	}

	public static class Model extends BaseModel {
		private final Part flame;

		public Model(BlockState state) {
			super(state);
			flame = addPart(modelFor(state));
		}

		private static ItemStack modelFor(BlockState state) {
			boolean soul = state.getValue(FLAME_TYPE) == FlameType.SOUL;
			return ModelItems.block("blaze_burner/" + (soul ? "block_with_soul_fire" : "block_with_fire"));
		}

		@Override
		protected void onStateChanged(BlockState state) {
			flame.setItem(modelFor(state));
		}

		@Override
		protected void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time) {
		}
	}

	public enum FlameType implements StringRepresentable {

		REGULAR, SOUL;

		@Override
		public String getSerializedName() {
			return Lang.asId(name());
		}

	}

}

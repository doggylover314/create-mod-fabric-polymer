package dev.createpolymer.kinetics.base;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's BlockBreakingKineticBlockEntity (MIT, The Create Team). */
public abstract class BlockBreakingKineticBlockEntity extends KineticBlockEntity {

	/** Create's AllBlockTags.NON_BREAKABLE (no entries by default; packs can fill it). */
	public static final TagKey<Block> NON_BREAKABLE = TagKey.create(Registries.BLOCK, CreatePolymer.id("non_breakable"));

	public static final AtomicInteger NEXT_BREAKER_ID = new AtomicInteger();
	protected int ticksUntilNextProgress;
	protected int destroyProgress;
	protected int breakerId = -NEXT_BREAKER_ID.incrementAndGet();
	protected @Nullable BlockPos breakingPos;

	public BlockBreakingKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		if (destroyProgress == -1)
			destroyNextTick();
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (ticksUntilNextProgress == -1)
			destroyNextTick();
	}

	public void destroyNextTick() {
		ticksUntilNextProgress = 1;
	}

	protected abstract BlockPos getBreakingPos();

	protected boolean shouldRun() {
		return true;
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putInt("Progress", destroyProgress);
		compound.putInt("NextTick", ticksUntilNextProgress);
		if (breakingPos != null)
			compound.store("Breaking", BlockPos.CODEC, breakingPos);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		destroyProgress = compound.getIntOr("Progress", 0);
		ticksUntilNextProgress = compound.getIntOr("NextTick", 0);
		breakingPos = compound.read("Breaking", BlockPos.CODEC).orElse(null);
		super.read(compound);
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if (level != null && !level.isClientSide() && destroyProgress != 0 && breakingPos != null)
			level.destroyBlockProgress(breakerId, breakingPos, -1);
	}

	@Override
	public void tick() {
		super.tick();

		if (level.isClientSide())
			return;
		if (!shouldRun())
			return;
		if (getSpeed() == 0)
			return;

		breakingPos = getBreakingPos();

		if (ticksUntilNextProgress < 0)
			return;
		if (ticksUntilNextProgress-- > 0)
			return;

		BlockState stateToBreak = level.getBlockState(breakingPos);
		float blockHardness = stateToBreak.getDestroySpeed(level, breakingPos);

		if (!canBreak(stateToBreak, blockHardness)) {
			if (destroyProgress != 0) {
				destroyProgress = 0;
				level.destroyBlockProgress(breakerId, breakingPos, -1);
			}
			return;
		}

		float breakSpeed = getBreakSpeed();
		destroyProgress += Mth.clamp((int) (breakSpeed / blockHardness), 1, 10 - destroyProgress);
		level.playSound(null, worldPosition, stateToBreak.getSoundType()
			.getHitSound(), SoundSource.BLOCKS, .25f, 1);

		if (destroyProgress >= 10) {
			onBlockBroken(stateToBreak);
			destroyProgress = 0;
			ticksUntilNextProgress = -1;
			level.destroyBlockProgress(breakerId, breakingPos, -1);
			return;
		}

		ticksUntilNextProgress = (int) (blockHardness / breakSpeed);
		level.destroyBlockProgress(breakerId, breakingPos, (int) destroyProgress);
	}

	public boolean canBreak(BlockState stateToBreak, float blockHardness) {
		return isBreakable(stateToBreak, blockHardness);
	}

	public static boolean isBreakable(BlockState stateToBreak, float blockHardness) {
		return !(stateToBreak.liquid() || stateToBreak.getBlock() instanceof AirBlock || blockHardness == -1 || stateToBreak.is(NON_BREAKABLE));
	}

	public void onBlockBroken(BlockState stateToBreak) {
		Vec3 vec = VecHelper.offsetRandomly(VecHelper.getCenterOf(breakingPos), level.getRandom(), .125f);
		destroyBlock(level, breakingPos, 1f, (stack) -> {
			if (stack.isEmpty())
				return;
			if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getGameRules().get(GameRules.BLOCK_DROPS))
				return;

			ItemEntity itementity = new ItemEntity(level, vec.x, vec.y, vec.z, stack);
			itementity.setDefaultPickUpDelay();
			itementity.setDeltaMovement(Vec3.ZERO);
			level.addFreshEntity(itementity);
		});
	}

	protected float getBreakSpeed() {
		return Math.abs(getSpeed() / 100f);
	}

	/**
	 * Create's BlockHelper.destroyBlock without a player: plays the break effect, hands the drops to the callback
	 * and replaces the block with its fluid.
	 */
	public static void destroyBlock(Level level, BlockPos pos, float effectChance, Consumer<ItemStack> droppedItemCallback) {
		FluidState fluidState = level.getFluidState(pos);
		BlockState state = level.getBlockState(pos);

		if (level.getRandom().nextFloat() < effectChance)
			level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

		if (level instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.BLOCK_DROPS)) {
			for (ItemStack itemStack : Block.getDrops(state, serverLevel, pos, blockEntity))
				droppedItemCallback.accept(itemStack);

			// Simulating IceBlock#playerDestroy. Not calling method directly as it would drop item
			// entities as a side-effect
			if (state.getBlock() instanceof IceBlock) {
				if (!level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
					BlockState below = level.getBlockState(pos.below());
					if (below.is(BlockTags.ICE_MELTS_WHEN_DESTROYED_ABOVE) || below.liquid())
						fluidState = IceBlock.meltsInto().getFluidState();
				}
			}

			state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, false);
		}

		level.setBlockAndUpdate(pos, fluidState.createLegacyBlock());
	}

}

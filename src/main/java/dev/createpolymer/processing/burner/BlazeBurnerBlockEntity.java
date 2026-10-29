package dev.createpolymer.processing.burner;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.fluids.tank.FluidTankBlock;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.processing.basin.BasinBlock;
import dev.createpolymer.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's BlazeBurnerBlockEntity (MIT, The Create Team).
 * The head animation (which Create runs on the client) is ticked on the server here, and the head turns towards the
 * nearest player within {@link #HEAD_TRACKING_RANGE} blocks. Stock ticker and train hat extras are not ported.
 */
public class BlazeBurnerBlockEntity extends SmartBlockEntity {

	public static final int MAX_HEAT_CAPACITY = 10000;
	public static final int INSERTION_THRESHOLD = 500;
	/** Players further away than this do not make the head turn. */
	public static final double HEAD_TRACKING_RANGE = 8;

	public LerpedFloat headAnimation;
	public boolean isCreative;
	public boolean goggles;

	protected FuelType activeFuel;
	protected int remainingBurnTime;
	protected LerpedFloat headAngle;

	/** Target of the head, refreshed every few ticks from the nearest player. */
	private float trackedAngle;
	private boolean tracking;

	public BlazeBurnerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		activeFuel = FuelType.NONE;
		remainingBurnTime = 0;
		headAnimation = LerpedFloat.linear();
		headAngle = LerpedFloat.angular();
		isCreative = false;
		goggles = false;

		headAngle.startWithValue((AngleHelper.horizontalAngle(state.getOptionalValue(BlazeBurnerBlock.FACING)
			.orElse(Direction.SOUTH)) + 180) % 360);
	}

	public FuelType getActiveFuel() {
		return activeFuel;
	}

	public int getRemainingBurnTime() {
		return remainingBurnTime;
	}

	public boolean isCreative() {
		return isCreative;
	}

	@Override
	public void tick() {
		super.tick();

		tickAnimation();
		spawnParticles(getHeatLevelFromBlock(), 1);

		if (isCreative)
			return;

		if (remainingBurnTime > 0)
			remainingBurnTime--;

		if (activeFuel == FuelType.NORMAL)
			updateBlockState();
		if (remainingBurnTime > 0)
			return;

		if (activeFuel == FuelType.SPECIAL) {
			activeFuel = FuelType.NORMAL;
			remainingBurnTime = MAX_HEAT_CAPACITY / 2;
		} else
			activeFuel = FuelType.NONE;

		updateBlockState();
	}

	void tickAnimation() {
		boolean active = getHeatLevelFromBlock().isAtLeast(HeatLevel.FADING) && isValidBlockAbove();

		if (!active) {
			if (level.getGameTime() % 4 == 0)
				updateTrackedPlayer();
			if (tracking) {
				float target = trackedAngle;
				target = headAngle.getValue() + AngleHelper.getShortestAngleDiff(headAngle.getValue(), target);
				headAngle.chase(target, .25f, Chaser.exp(5));
			} else {
				headAngle.chase(headAngle.getValue(), .25f, Chaser.exp(5));
			}
			headAngle.tickChaser();
		} else {
			headAngle.chase((AngleHelper.horizontalAngle(getBlockState().getOptionalValue(BlazeBurnerBlock.FACING)
				.orElse(Direction.SOUTH)) + 180) % 360, .125f, Chaser.EXP);
			headAngle.tickChaser();
		}

		headAnimation.chase(active ? 1 : 0, .25f, Chaser.exp(.25f));
		headAnimation.tickChaser();
	}

	private void updateTrackedPlayer() {
		Player player = level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
			worldPosition.getZ() + 0.5, HEAD_TRACKING_RANGE, p -> !p.isInvisible() && !p.isSpectator());
		tracking = player != null;
		if (player == null)
			return;
		double dx = player.getX() - (getBlockPos().getX() + 0.5);
		double dz = player.getZ() - (getBlockPos().getZ() + 0.5);
		trackedAngle = AngleHelper.deg(-Mth.atan2(dz, dx)) - 90;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

	@Override
	protected void write(ValueOutput compound) {
		if (!isCreative) {
			compound.putInt("fuelLevel", activeFuel.ordinal());
			compound.putInt("burnTimeRemaining", remainingBurnTime);
		} else
			compound.putBoolean("isCreative", true);
		if (goggles)
			compound.putBoolean("Goggles", true);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		activeFuel = FuelType.values()[Mth.clamp(compound.getIntOr("fuelLevel", 0), 0, FuelType.values().length - 1)];
		remainingBurnTime = compound.getIntOr("burnTimeRemaining", 0);
		isCreative = compound.getBooleanOr("isCreative", false);
		goggles = compound.getBooleanOr("Goggles", false);
		super.read(compound);
	}

	public BlazeBurnerBlock.HeatLevel getHeatLevelFromBlock() {
		return BlazeBurnerBlock.getHeatLevelOf(getBlockState());
	}

	public BlazeBurnerBlock.HeatLevel getHeatLevelForRender() {
		return getHeatLevelFromBlock();
	}

	public void updateBlockState() {
		setBlockHeat(getHeatLevel());
	}

	protected void setBlockHeat(HeatLevel heat) {
		HeatLevel inBlockState = getHeatLevelFromBlock();
		if (inBlockState == heat)
			return;
		level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
		notifyUpdate();
	}

	/**
	 * @return true if the heater updated its burn time and an item should be
	 *         consumed
	 */
	protected boolean tryUpdateFuel(ItemStack itemStack, boolean forceOverflow, boolean simulate) {
		if (isCreative)
			return false;

		FuelType newFuel = FuelType.NONE;
		int newBurnTime;

		Integer superheatedFuel = BlazeBurnerFuels.getSuperheated(itemStack);
		Integer normalFuel = BlazeBurnerFuels.getRegular(itemStack);

		if (superheatedFuel != null) {
			newBurnTime = superheatedFuel;
			newFuel = FuelType.SPECIAL;
		} else if (normalFuel != null) {
			newBurnTime = normalFuel;
			newFuel = FuelType.NORMAL;
		} else if (itemStack.is(BlazeBurnerFuels.BLAZE_BURNER_FUEL_SPECIAL)) {
			newBurnTime = 3200;
			newFuel = FuelType.SPECIAL;
		} else {
			newBurnTime = getBurnTime(itemStack);
			if (newBurnTime > 0) {
				newFuel = FuelType.NORMAL;
			} else if (itemStack.is(BlazeBurnerFuels.BLAZE_BURNER_FUEL_REGULAR)) {
				newBurnTime = 1600; // Same as coal
				newFuel = FuelType.NORMAL;
			}
		}

		if (newFuel == FuelType.NONE)
			return false;
		if (newFuel.ordinal() < activeFuel.ordinal())
			return false;

		if (newFuel == activeFuel) {
			if (remainingBurnTime <= INSERTION_THRESHOLD) {
				newBurnTime += remainingBurnTime;
			} else if (forceOverflow && newFuel == FuelType.NORMAL) {
				if (remainingBurnTime < MAX_HEAT_CAPACITY) {
					newBurnTime = Math.min(remainingBurnTime + newBurnTime, MAX_HEAT_CAPACITY);
				} else {
					newBurnTime = remainingBurnTime;
				}
			} else {
				return false;
			}
		}

		if (simulate)
			return true;

		activeFuel = newFuel;
		remainingBurnTime = newBurnTime;

		spawnParticleBurst(activeFuel == FuelType.SPECIAL);

		HeatLevel prev = getHeatLevelFromBlock();
		playSound();
		updateBlockState();

		if (prev != getHeatLevelFromBlock())
			level.playSound(null, worldPosition, SoundEvents.BLAZE_AMBIENT, SoundSource.BLOCKS,
				.125f + level.getRandom().nextFloat() * .125f, 1.15f - level.getRandom().nextFloat() * .25f);

		return true;
	}

	/** The furnace burn time of the item (Create's ItemStack.getBurnTime(null)). */
	protected int getBurnTime(ItemStack stack) {
		if (!(level instanceof ServerLevel serverLevel))
			return 0;
		LootContext context = new LootContext.Builder(new LootParams.Builder(serverLevel).create(LootContextParamSets.EMPTY))
			.create(Optional.empty());
		return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
	}

	protected void applyCreativeFuel() {
		activeFuel = FuelType.NONE;
		remainingBurnTime = 0;
		isCreative = true;

		HeatLevel next = getHeatLevelFromBlock().nextActiveLevel();

		spawnParticleBurst(next.isAtLeast(HeatLevel.SEETHING));

		playSound();
		if (next == HeatLevel.FADING)
			next = next.nextActiveLevel();
		setBlockHeat(next);
	}

	public boolean isCreativeFuel(ItemStack stack) {
		return stack.is(BurnerRegistry.CREATIVE_BLAZE_CAKE);
	}

	public boolean isValidBlockAbove() {
		if (isVirtual())
			return false;
		BlockState blockState = level.getBlockState(worldPosition.above());
		return BasinBlock.isBasin(level, worldPosition.above()) || blockState.getBlock() instanceof FluidTankBlock;
	}

	protected void playSound() {
		level.playSound(null, worldPosition, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS,
			.125f + level.getRandom().nextFloat() * .125f, .75f - level.getRandom().nextFloat() * .25f);
	}

	protected HeatLevel getHeatLevel() {
		HeatLevel level = HeatLevel.SMOULDERING;
		switch (activeFuel) {
			case SPECIAL -> level = HeatLevel.SEETHING;
			case NORMAL -> {
				boolean lowPercent = (double) remainingBurnTime / MAX_HEAT_CAPACITY < 0.0125;
				level = lowPercent ? HeatLevel.FADING : HeatLevel.KINDLED;
			}
			default -> {
			}
		}
		return level;
	}

	protected void spawnParticles(HeatLevel heatLevel, double burstMult) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		if (heatLevel == BlazeBurnerBlock.HeatLevel.NONE)
			return;

		RandomSource r = level.getRandom();

		Vec3 c = VecHelper.getCenterOf(worldPosition);
		Vec3 v = c.add(VecHelper.offsetRandomly(Vec3.ZERO, r, .125f)
			.multiply(1, 0, 1));

		if (r.nextInt(4) != 0)
			return;

		boolean empty = level.getBlockState(worldPosition.above())
			.getCollisionShape(level, worldPosition.above())
			.isEmpty();

		if (empty || r.nextInt(8) == 0)
			serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, v.x, v.y, v.z, 0, 0, 0, 0, 1);

		double yMotion = empty ? .0625f : r.nextDouble() * .0125f;
		Vec3 v2 = c.add(VecHelper.offsetRandomly(Vec3.ZERO, r, .5f)
			.multiply(1, .25f, 1)
			.normalize()
			.scale((empty ? .25f : .5) + r.nextDouble() * .125f))
			.add(0, .5, 0);

		if (heatLevel.isAtLeast(HeatLevel.SEETHING)) {
			serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, v2.x, v2.y, v2.z, 0, 0, yMotion, 0, 1);
		} else if (heatLevel.isAtLeast(HeatLevel.FADING)) {
			serverLevel.sendParticles(ParticleTypes.FLAME, v2.x, v2.y, v2.z, 0, 0, yMotion, 0, 1);
		}
	}

	public void spawnParticleBurst(boolean soulFlame) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		Vec3 c = VecHelper.getCenterOf(worldPosition);
		RandomSource r = level.getRandom();
		for (int i = 0; i < 20; i++) {
			Vec3 offset = VecHelper.offsetRandomly(Vec3.ZERO, r, .5f)
				.multiply(1, .25f, 1)
				.normalize();
			Vec3 v = c.add(offset.scale(.5 + r.nextDouble() * .125f))
				.add(0, .125, 0);
			Vec3 m = offset.scale(1 / 32f);

			serverLevel.sendParticles(soulFlame ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, v.x, v.y, v.z, 0,
				m.x, m.y, m.z, 1);
		}
	}

	public enum FuelType {
		NONE, NORMAL, SPECIAL
	}

}

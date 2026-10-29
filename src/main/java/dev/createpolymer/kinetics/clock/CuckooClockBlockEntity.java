package dev.createpolymer.kinetics.clock;

import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.pig.PigSoundVariants;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's CuckooClockBlockEntity (MIT, The Create Team).
 * Create runs the hands, sounds and animation on the client and only the explosion on the server;
 * here there is no client, so everything runs in the server tick.
 */
public class CuckooClockBlockEntity extends KineticBlockEntity {

	public LerpedFloat hourHand = LerpedFloat.angular();
	public LerpedFloat minuteHand = LerpedFloat.angular();
	public LerpedFloat animationProgress = LerpedFloat.linear();
	public Animation animationType;

	public enum Animation {
		PIG, CREEPER, SURPRISE, NONE;
	}

	public CuckooClockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		animationType = Animation.NONE;
	}

	@Override
	public void tick() {
		super.tick();
		if (level.isClientSide() || getSpeed() == 0)
			return;

		// 26.3 dropped DimensionType.natural(): dimensions with a clock of their own have a natural day
		boolean isNatural = level.dimensionType().defaultClock().isPresent();
		long time = isNatural ? level.getDefaultClockTime() : level.getOverworldClockTime() * 24;
		int dayTime = (int) (time % 24000);
		int hours = (dayTime / 1000 + 6) % 24;
		int minutes = (dayTime % 1000) * 60 / 1000;

		moveHands(hours, minutes);
		long ticks = level.getGameTime();

		if (!isNatural) {
			if (ticks % 6 == 0)
				playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1 / 16f, 2f);
			else if (ticks % 3 == 0)
				playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1 / 16f, 1.5f);
			return;
		}

		if (animationType == Animation.NONE) {
			if (hours == 12 && minutes < 5)
				startAnimation(Animation.PIG);
			if (hours == 18 && minutes < 36 && minutes > 31)
				startAnimation(Animation.CREEPER);

			if (animationType == Animation.NONE) {
				if (ticks % 32 == 0)
					playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1 / 16f, 2f);
				else if (ticks % 16 == 0)
					playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1 / 16f, 1.5f);
			}
			return;
		}

		boolean isSurprise = animationType == Animation.SURPRISE;
		float value = animationProgress.getValue();
		animationProgress.setValue(value + 1);

		if (isSurprise && Mth.equal(animationProgress.getValue(), 50)) {
			Vec3 center = VecHelper.getCenterOf(worldPosition);
			level.destroyBlock(worldPosition, false);
			level.explode(null, center.x, center.y, center.z, 3, Level.ExplosionInteraction.BLOCK);
			return;
		}

		Animation animation = animationType;
		if (value > 100)
			animationType = Animation.NONE;

		// sounds

		if (value == 1)
			playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 2, .5f);
		if (value == 21)
			playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 2, 0.793701f);

		if (value > 30 && isSurprise && level instanceof ServerLevel serverLevel) {
			Vec3 pos = VecHelper.offsetRandomly(VecHelper.getCenterOf(this.worldPosition), level.getRandom(), .5f);
			serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		}
		if (value == 40 && isSurprise)
			playSound(SoundEvents.TNT_PRIMED, 1f, 1f);

		int step = isSurprise ? 3 : 15;
		for (int phase = 30; phase <= 60; phase += step) {
			if (value == phase - step / 3)
				playSound(SoundEvents.CHEST_OPEN, 1 / 16f, 2f);
			if (value == phase) {
				if (animation == Animation.PIG)
					playSound(SoundEvents.PIG_SOUNDS.get(PigSoundVariants.SoundSet.CLASSIC).adultSounds().ambientSound().value(), 1 / 4f, 1f);
				else
					playSound(SoundEvents.CREEPER_HURT, 1 / 4f, 3f);
			}
			if (value == phase + step / 3)
				playSound(SoundEvents.CHEST_CLOSE, 1 / 16f, 2f);
		}
	}

	public void startAnimation(Animation animation) {
		animationType = animation;
		if (animation != null && CuckooClockBlock.containsSurprise(getBlockState()))
			animationType = Animation.SURPRISE;
		animationProgress.startWithValue(0);
		sendData();
	}

	public void moveHands(int hours, int minutes) {
		float hourTarget = (float) (360 / 12 * (hours % 12));
		float minuteTarget = (float) (360 / 60 * minutes);

		hourHand.chase(hourTarget, .2f, Chaser.EXP);
		minuteHand.chase(minuteTarget, .2f, Chaser.EXP);

		hourHand.tickChaser();
		minuteHand.tickChaser();
	}

	private void playSound(SoundEvent sound, float volume, float pitch) {
		Vec3 vec = VecHelper.getCenterOf(worldPosition);
		level.playSound(null, vec.x, vec.y, vec.z, sound, SoundSource.BLOCKS, volume, pitch);
	}
}

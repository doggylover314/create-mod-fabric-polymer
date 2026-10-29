package dev.createpolymer.kinetics.mixer;

import java.util.Optional;

import dev.createpolymer.Config;
import dev.createpolymer.kinetics.press.MechanicalPressBlockEntity;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.processing.basin.BasinOperatingBlockEntity;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModRecipeTypes;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's MechanicalMixerBlockEntity (MIT, The Create Team).
 * Potion brewing in the mixer needs fluids and is not ported yet.
 */
public class MechanicalMixerBlockEntity extends BasinOperatingBlockEntity {
	private static final Object shapelessOrMixingRecipesKey = new Object();

	public int runningTicks;
	public int processingTicks;
	public boolean running;

	public MechanicalMixerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MECHANICAL_MIXER, pos, state);
	}

	public float getRenderedHeadOffset(float partialTicks) {
		int localTick;
		float offset = 0;
		if (running) {
			if (runningTicks < 20) {
				localTick = runningTicks;
				float num = (localTick + partialTicks) / 20f;
				num = ((2 - Mth.cos((float) (num * Math.PI))) / 2);
				offset = num - .5f;
			} else if (runningTicks <= 20) {
				offset = 1;
			} else {
				localTick = 40 - runningTicks;
				float num = (localTick - partialTicks) / 20f;
				num = ((2 - Mth.cos((float) (num * Math.PI))) / 2);
				offset = num - .5f;
			}
		}
		return offset + 7 / 16f;
	}

	public float getRenderedHeadRotationSpeed() {
		float speed = getSpeed();
		if (running) {
			if (runningTicks < 15)
				return speed;
			if (runningTicks <= 20)
				return speed * 2;
			return speed;
		}
		return speed / 2;
	}

	@Override
	protected void read(ValueInput compound) {
		running = compound.getBooleanOr("Running", false);
		runningTicks = compound.getIntOr("Ticks", 0);
		super.read(compound);
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putBoolean("Running", running);
		compound.putInt("Ticks", runningTicks);
		super.write(compound);
	}

	@Override
	public void tick() {
		super.tick();

		if (runningTicks >= 40) {
			running = false;
			runningTicks = 0;
			scheduleBasinUpdate();
			return;
		}

		float speed = Math.abs(getSpeed());
		if (running && level != null) {
			if (runningTicks == 20)
				renderParticles();

			if (getSpeed() == 0 || !isSpeedRequirementFulfilled()) {
				if (runningTicks < 20)
					runningTicks = 40 - runningTicks;
				else if (runningTicks == 20)
					runningTicks++;
			}

			if (runningTicks == 20) {
				if (level.getGameTime() % (speed < 65 ? 4 : 2) == 0)
					ModSounds.mixing(level, worldPosition, .75f, 1);

				if (processingTicks < 0) {
					float recipeSpeed = 1;
					if (currentRecipe instanceof ProcessingRecipe<?> pr) {
						int t = pr.getProcessingDuration();
						if (t != 0)
							recipeSpeed = t / 100f;
					}

					processingTicks = Math.max(Mth.log2((int) (512 / Math.max(speed, 1))) * Mth.ceil(recipeSpeed * 15) + 1, 1);
				} else {
					processingTicks--;
					if (processingTicks == 0) {
						runningTicks++;
						processingTicks = -1;
						applyBasinRecipe();
						sendData();
					}
				}
			}

			if (runningTicks != 20)
				runningTicks++;
		}
	}

	public void renderParticles() {
		Optional<BasinBlockEntity> basin = getBasin();
		if (basin.isEmpty() || !(level instanceof ServerLevel serverLevel))
			return;

		for (ItemStack stackInSlot : basin.get().inputInventory.stacks()) {
			if (stackInSlot.isEmpty())
				continue;
			float angle = level.getRandom().nextFloat() * 360;
			Vec3 offset = VecHelper.rotate(new Vec3(0, 0, 0.25f), angle, net.minecraft.core.Direction.Axis.Y);
			Vec3 center = offset.add(VecHelper.getCenterOf(worldPosition));
			serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stackInSlot.getItem()),
				center.x, center.y - 1.75f, center.z, 2, .1, .05, .1, .05);
		}
	}

	@Override
	protected boolean matchStaticFilters(RecipeHolder<?> recipe) {
		Recipe<?> r = recipe.value();
		return (r instanceof ShapelessRecipe && Config.allowShapelessInMixer
			&& r.placementInfo().ingredients().size() > 1
			&& !MechanicalPressBlockEntity.canCompress(r))
			|| r.getType() == ModRecipeTypes.MIXING;
	}

	@Override
	public void startProcessingBasin() {
		if (running && runningTicks <= 20)
			return;
		super.startProcessingBasin();
		running = true;
		runningTicks = 0;
	}

	@Override
	public boolean continueWithPreviousRecipe() {
		runningTicks = 20;
		return true;
	}

	@Override
	protected void onBasinRemoved() {
		if (!running)
			return;
		runningTicks = 40;
		running = false;
	}

	@Override
	protected Object getRecipeCacheKey() {
		return shapelessOrMixingRecipesKey;
	}

	@Override
	protected boolean isRunning() {
		return running;
	}
}

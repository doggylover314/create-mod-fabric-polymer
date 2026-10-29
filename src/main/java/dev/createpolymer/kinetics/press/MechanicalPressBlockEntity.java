package dev.createpolymer.kinetics.press;

import java.util.List;
import java.util.Optional;

import dev.createpolymer.Config;
import dev.createpolymer.processing.basin.BasinBlockEntity;
import dev.createpolymer.processing.basin.BasinOperatingBlockEntity;
import dev.createpolymer.processing.recipe.PressingRecipe;
import dev.createpolymer.processing.recipe.RecipeApplier;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModRecipeTypes;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's MechanicalPressBlockEntity and PressingBehaviour (MIT, The Create Team).
 * Belt pressing and sequenced assembly are not ported yet.
 */
public class MechanicalPressBlockEntity extends BasinOperatingBlockEntity {
	private static final Object compressingRecipesKey = new Object();

	public static final int CYCLE = 240;
	public static final int ENTITY_SCAN = 10;

	public enum Mode {
		WORLD(1), BELT(19f / 16f), BASIN(22f / 16f);

		public final float headOffset;

		Mode(float headOffset) {
			this.headOffset = headOffset;
		}
	}

	public int prevRunningTicks;
	public int runningTicks;
	public boolean running;
	public boolean finished;
	public Mode mode = Mode.WORLD;
	private int entityScanCooldown = ENTITY_SCAN;

	public MechanicalPressBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MECHANICAL_PRESS, pos, state);
	}

	// ---- PressingBehaviour ----

	public float getRenderedHeadOffset(float partialTicks) {
		if (!running)
			return 0;
		int runningTicks = Math.abs(this.runningTicks);
		float ticks = Mth.lerp(partialTicks, prevRunningTicks, runningTicks);
		if (runningTicks < (CYCLE * 2) / 3)
			return (float) Mth.clamp(Math.pow(ticks / CYCLE * 2, 3), 0, 1);
		return Mth.clamp((CYCLE - ticks) / CYCLE * 3, 0, 1);
	}

	public void start(Mode mode) {
		this.mode = mode;
		running = true;
		prevRunningTicks = 0;
		runningTicks = 0;
		sendData();
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide())
			return;

		if (!running) {
			if (getSpeed() == 0)
				return;
			if (entityScanCooldown > 0)
				entityScanCooldown--;
			if (entityScanCooldown <= 0) {
				entityScanCooldown = ENTITY_SCAN;
				if (level.getBlockEntity(worldPosition.below(2)) instanceof BasinBlockEntity)
					return;
				for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, new AABB(worldPosition.below()).deflate(.125f))) {
					if (!itemEntity.isAlive() || !itemEntity.onGround())
						continue;
					if (!tryProcessInWorld(itemEntity, true))
						continue;
					start(Mode.WORLD);
					return;
				}
			}
			return;
		}

		if (runningTicks == CYCLE / 2 && getSpeed() != 0) {
			if (mode == Mode.WORLD)
				applyInWorld();
			if (mode == Mode.BASIN)
				applyOnBasin();

			if (level.getBlockState(worldPosition.below(2)).getSoundType() == SoundType.WOOL)
				ModSounds.pressActivationOnBelt(level, worldPosition);
			else
				ModSounds.pressActivation(level, worldPosition, .5f, .75f + (Math.abs(getSpeed()) / 1024f));
			sendData();
		}

		if (runningTicks > CYCLE) {
			finished = true;
			running = false;
			onPressingCompleted();
			sendData();
			return;
		}

		prevRunningTicks = runningTicks;
		runningTicks += getRunningTickSpeed();
		if (prevRunningTicks < CYCLE / 2 && runningTicks >= CYCLE / 2)
			runningTicks = CYCLE / 2;
	}

	public int getRunningTickSpeed() {
		float speed = getSpeed();
		if (speed == 0)
			return 0;
		return (int) Mth.lerp(Mth.clamp(Math.abs(speed) / 512f, 0, 1), 1, 60);
	}

	protected void applyOnBasin() {
		Optional<BasinBlockEntity> basin = getBasin();
		basin.ifPresent(b -> {
			for (ItemStack stack : b.inputInventory.stacks())
				if (!stack.isEmpty())
					particles(VecHelper.getCenterOf(worldPosition.below(2)), stack, 20, .175f, .25f);
		});
		applyBasinRecipe();
		sendData();
	}

	protected void applyInWorld() {
		AABB bb = new AABB(worldPosition.below(1));
		boolean bulk = canProcessInBulk();
		for (Entity entity : level.getEntities((Entity) null, bb)) {
			if (!(entity instanceof ItemEntity itemEntity))
				continue;
			if (!entity.isAlive() || !entity.onGround())
				continue;
			entityScanCooldown = 0;
			if (tryProcessInWorld(itemEntity, false))
				sendData();
			if (!bulk)
				break;
		}
	}

	public boolean tryProcessInWorld(ItemEntity itemEntity, boolean simulate) {
		ItemStack item = itemEntity.getItem();
		Optional<PressingRecipe> recipe = getRecipe(item);
		if (recipe.isEmpty())
			return false;
		if (simulate)
			return true;

		particles(VecHelper.getCenterOf(worldPosition.below(1)).add(0, -1 / 4f, 0), item, 15, .125f, .125f);
		if (canProcessInBulk() || item.getCount() == 1) {
			RecipeApplier.applyRecipeOn(itemEntity, recipe.get(), true);
		} else {
			for (ItemStack result : RecipeApplier.applyRecipeOn(level, item.copyWithCount(1), recipe.get(), true)) {
				ItemEntity created = new ItemEntity(level, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), result);
				created.setDefaultPickUpDelay();
				created.setDeltaMovement(VecHelper.offsetRandomly(Vec3.ZERO, level.getRandom(), .05f));
				level.addFreshEntity(created);
			}
			item.shrink(1);
		}
		return true;
	}

	private void particles(Vec3 pos, ItemStack stack, int amount, float spread, float up) {
		if (level instanceof ServerLevel serverLevel && !stack.isEmpty())
			serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()), pos.x, pos.y, pos.z, amount, spread, .05, spread, up);
	}

	public void onPressingCompleted() {
		if (mode == Mode.BASIN && matchBasinRecipe(currentRecipe)
			&& getBasin().filter(BasinBlockEntity::canContinueProcessing).isPresent())
			startProcessingBasin();
		else
			scheduleBasinUpdate();
	}

	public Optional<PressingRecipe> getRecipe(ItemStack item) {
		if (!(level instanceof ServerLevel serverLevel))
			return Optional.empty();
		return serverLevel.recipeAccess().getRecipeFor(ModRecipeTypes.PRESSING, new SingleRecipeInput(item), level)
			.map(RecipeHolder::value);
	}

	public static boolean canCompress(Recipe<?> recipe) {
		if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) || !Config.allowShapedSquareInPress)
			return false;
		List<Ingredient> ingredients = recipe.placementInfo().ingredients();
		return (ingredients.size() == 4 || ingredients.size() == 9) && allSame(ingredients);
	}

	private static boolean allSame(List<Ingredient> ingredients) {
		Ingredient first = ingredients.getFirst();
		for (Ingredient i : ingredients)
			if (!i.equals(first))
				return false;
		return true;
	}

	@Override
	protected boolean matchStaticFilters(RecipeHolder<?> recipe) {
		Recipe<?> r = recipe.value();
		return (r instanceof CraftingRecipe && canCompress(r)) || r.getType() == ModRecipeTypes.COMPACTING;
	}

	public boolean canProcessInBulk() {
		return Config.bulkPressing;
	}

	@Override
	protected Object getRecipeCacheKey() {
		return compressingRecipesKey;
	}

	@Override
	public void startProcessingBasin() {
		if (running && runningTicks <= CYCLE / 2)
			return;
		super.startProcessingBasin();
		start(Mode.BASIN);
	}

	@Override
	protected void onBasinRemoved() {
		running = false;
		runningTicks = 0;
		sendData();
	}

	@Override
	protected boolean isRunning() {
		return running;
	}

	@Override
	protected void write(ValueOutput compound) {
		compound.putBoolean("Running", running);
		compound.putInt("Mode", mode.ordinal());
		compound.putBoolean("Finished", finished);
		compound.putInt("Ticks", runningTicks);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		running = compound.getBooleanOr("Running", false);
		mode = Mode.values()[Mth.clamp(compound.getIntOr("Mode", 0), 0, Mode.values().length - 1)];
		finished = compound.getBooleanOr("Finished", false);
		prevRunningTicks = runningTicks = compound.getIntOr("Ticks", 0);
		super.read(compound);
	}
}

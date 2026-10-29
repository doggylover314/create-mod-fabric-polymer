package dev.createpolymer.kinetics.fan.processing;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.processing.burner.BlazeBurnerBlock;
import dev.createpolymer.processing.burner.LitBlazeBurnerBlock;
import dev.createpolymer.processing.recipe.RecipeApplier;
import dev.createpolymer.registry.feature.FanRegistry;
import dev.createpolymer.util.VecHelper;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public class AllFanProcessingTypes {
	public static final BlastingType BLASTING = register("blasting", new BlastingType());
	public static final HauntingType HAUNTING = register("haunting", new HauntingType());
	public static final SmokingType SMOKING = register("smoking", new SmokingType());
	public static final SplashingType SPLASHING = register("splashing", new SplashingType());

	/**
	 * Create's block/fluid tags (data/create/tags/{block,fluid}/fan_processing_catalysts/*). Entries of blocks
	 * that are not ported yet are optional in the tag files.
	 */
	public static final TagKey<Block> FAN_PROCESSING_CATALYSTS_BLASTING_BLOCKS = blockTag("fan_processing_catalysts/blasting");
	public static final TagKey<Block> FAN_PROCESSING_CATALYSTS_HAUNTING_BLOCKS = blockTag("fan_processing_catalysts/haunting");
	public static final TagKey<Block> FAN_PROCESSING_CATALYSTS_SMOKING_BLOCKS = blockTag("fan_processing_catalysts/smoking");
	public static final TagKey<Block> FAN_PROCESSING_CATALYSTS_SPLASHING_BLOCKS = blockTag("fan_processing_catalysts/splashing");
	public static final TagKey<Fluid> FAN_PROCESSING_CATALYSTS_BLASTING_FLUIDS = fluidTag("fan_processing_catalysts/blasting");
	public static final TagKey<Fluid> FAN_PROCESSING_CATALYSTS_HAUNTING_FLUIDS = fluidTag("fan_processing_catalysts/haunting");
	public static final TagKey<Fluid> FAN_PROCESSING_CATALYSTS_SMOKING_FLUIDS = fluidTag("fan_processing_catalysts/smoking");
	public static final TagKey<Fluid> FAN_PROCESSING_CATALYSTS_SPLASHING_FLUIDS = fluidTag("fan_processing_catalysts/splashing");

	/** Create's AllRecipeTypes.CAN_BE_AUTOMATED */
	private static final Predicate<RecipeHolder<?>> CAN_BE_AUTOMATED = r -> !r.id()
		.identifier()
		.getPath()
		.endsWith("_manual_only");

	private static final Map<String, FanProcessingType> LEGACY_NAME_MAP;

	static {
		Object2ReferenceOpenHashMap<String, FanProcessingType> map = new Object2ReferenceOpenHashMap<>();
		map.put("BLASTING", BLASTING);
		map.put("HAUNTING", HAUNTING);
		map.put("SMOKING", SMOKING);
		map.put("SPLASHING", SPLASHING);
		map.trim();
		LEGACY_NAME_MAP = map;
	}

	private static TagKey<Block> blockTag(String path) {
		return TagKey.create(Registries.BLOCK, CreatePolymer.id(path));
	}

	private static TagKey<Fluid> fluidTag(String path) {
		return TagKey.create(Registries.FLUID, CreatePolymer.id(path));
	}

	private static <T extends FanProcessingType> T register(String name, T type) {
		return FanProcessingTypeRegistry.register(CreatePolymer.id(name), type);
	}

	public static void init() {
	}

	@Nullable
	public static FanProcessingType ofLegacyName(String name) {
		return LEGACY_NAME_MAP.get(name);
	}

	@Nullable
	public static FanProcessingType parseLegacy(String str) {
		FanProcessingType type = ofLegacyName(str);
		if (type != null) {
			return type;
		}
		return FanProcessingType.parse(str);
	}

	/** Looks a recipe up through the server's recipe manager (Create: level.getRecipeManager().getRecipeFor). */
	private static <I extends RecipeInput, R extends Recipe<I>> Optional<RecipeHolder<R>> find(Level level, RecipeType<R> type, I input) {
		if (!(level instanceof ServerLevel serverLevel))
			return Optional.empty();
		return serverLevel.recipeAccess()
			.getRecipeFor(type, input, level);
	}

	private static <R extends Recipe<SingleRecipeInput>> Optional<RecipeHolder<R>> findAutomated(Level level, RecipeType<R> type, ItemStack stack) {
		return find(level, type, new SingleRecipeInput(stack)).filter(CAN_BE_AUTOMATED);
	}

	/** Create's RecipeApplier.applyRecipeOn for a vanilla (non processing) recipe. */
	private static List<ItemStack> applyRecipeOn(ItemStack stackIn, AbstractCookingRecipe recipe) {
		return ItemHelper.multipliedOutput(stackIn, recipe.assemble(new SingleRecipeInput(stackIn)));
	}

	private static int mixColors(int color1, int color2, float w) {
		int r = Math.round(((color1 >> 16) & 0xFF) * (1 - w) + ((color2 >> 16) & 0xFF) * w);
		int g = Math.round(((color1 >> 8) & 0xFF) * (1 - w) + ((color2 >> 8) & 0xFF) * w);
		int b = Math.round((color1 & 0xFF) * (1 - w) + (color2 & 0xFF) * w);
		return (r << 16) | (g << 8) | b;
	}

	private static boolean isLitCampfire(BlockState blockState) {
		return !blockState.is(BlockTags.CAMPFIRES) || !blockState.hasProperty(CampfireBlock.LIT) || blockState.getValue(CampfireBlock.LIT);
	}

	public static class BlastingType implements FanProcessingType {
		@Override
		public boolean isValidAt(Level level, BlockPos pos) {
			FluidState fluidState = level.getFluidState(pos);
			if (fluidState.is(FAN_PROCESSING_CATALYSTS_BLASTING_FLUIDS)) {
				return true;
			}
			BlockState blockState = level.getBlockState(pos);
			if (blockState.is(FAN_PROCESSING_CATALYSTS_BLASTING_BLOCKS)) {
				return !blockState.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)
					|| blockState.getValue(BlazeBurnerBlock.HEAT_LEVEL).isAtLeast(BlazeBurnerBlock.HeatLevel.FADING);
			}
			return false;
		}

		@Override
		public int getPriority() {
			return 100;
		}

		@Override
		public boolean canProcess(ItemStack stack, Level level) {
			Optional<RecipeHolder<SmeltingRecipe>> smeltingRecipe = findAutomated(level, RecipeType.SMELTING, stack);

			if (smeltingRecipe.isPresent())
				return true;

			Optional<RecipeHolder<BlastingRecipe>> blastingRecipe = findAutomated(level, RecipeType.BLASTING, stack);

			if (blastingRecipe.isPresent())
				return true;

			return !stack.has(DataComponents.DAMAGE_RESISTANT);
		}

		@Override
		@Nullable
		public List<ItemStack> process(ItemStack stack, Level level) {
			Optional<RecipeHolder<SmokingRecipe>> smokingRecipe = findAutomated(level, RecipeType.SMOKING, stack);

			Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> smeltingRecipe = findAutomated(level, RecipeType.SMELTING, stack);

			if (smeltingRecipe.isEmpty()) {
				smeltingRecipe = findAutomated(level, RecipeType.BLASTING, stack);
			}

			if (smeltingRecipe.isPresent()) {
				SingleRecipeInput input = new SingleRecipeInput(stack);
				if (smokingRecipe.isEmpty() || !ItemStack.isSameItem(smokingRecipe.get().value()
						.assemble(input),
					smeltingRecipe.get().value()
						.assemble(input))) {
					return applyRecipeOn(stack, smeltingRecipe.get().value());
				}
			}

			return Collections.emptyList();
		}

		@Override
		public void spawnProcessingParticles(Level level, Vec3 pos) {
			if (level.getRandom().nextInt(8) != 0)
				return;
			if (level instanceof ServerLevel serverLevel)
				serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + .25f, pos.z, 0, 0, 1 / 16f, 0, 1);
		}

		@Override
		public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
			particleAccess.setColor(mixColors(0xFF4400, 0xFF8855, random.nextFloat()));
			particleAccess.setAlpha(.5f);
			if (random.nextFloat() < 1 / 32f)
				particleAccess.spawnExtraParticle(ParticleTypes.FLAME, .25f);
			if (random.nextFloat() < 1 / 16f)
				particleAccess.spawnExtraParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.LAVA.defaultBlockState()), .25f);
		}

		@Override
		public void affectEntity(Entity entity, Level level) {
			if (!(level instanceof ServerLevel serverLevel))
				return;

			if (!entity.fireImmune()) {
				entity.igniteForSeconds(10);
				// Create: CreateDamageSources.fanLava (a data pack damage type not ported)
				entity.hurtServer(serverLevel, level.damageSources().lava(), 4);
			}
		}
	}

	public static class HauntingType implements FanProcessingType {
		@Override
		public boolean isValidAt(Level level, BlockPos pos) {
			FluidState fluidState = level.getFluidState(pos);
			if (fluidState.is(FAN_PROCESSING_CATALYSTS_HAUNTING_FLUIDS)) {
				return true;
			}
			BlockState blockState = level.getBlockState(pos);
			if (blockState.is(FAN_PROCESSING_CATALYSTS_HAUNTING_BLOCKS)) {
				if (!isLitCampfire(blockState)) {
					return false;
				}
				return !blockState.hasProperty(LitBlazeBurnerBlock.FLAME_TYPE)
					|| blockState.getValue(LitBlazeBurnerBlock.FLAME_TYPE) == LitBlazeBurnerBlock.FlameType.SOUL;
			}
			return false;
		}

		@Override
		public int getPriority() {
			return 300;
		}

		@Override
		public boolean canProcess(ItemStack stack, Level level) {
			return find(level, FanRegistry.HAUNTING, new SingleRecipeInput(stack))
				.isPresent();
		}

		@Override
		@Nullable
		public List<ItemStack> process(ItemStack stack, Level level) {
			return find(level, FanRegistry.HAUNTING, new SingleRecipeInput(stack))
				.map(RecipeHolder::value)
				.map(r -> RecipeApplier.applyRecipeOn(level, stack, r, true))
				.orElse(null);
		}

		@Override
		public void spawnProcessingParticles(Level level, Vec3 pos) {
			if (level.getRandom().nextInt(8) != 0)
				return;
			if (!(level instanceof ServerLevel serverLevel))
				return;
			pos = pos.add(VecHelper.offsetRandomly(Vec3.ZERO, level.getRandom(), 1)
				.multiply(1, 0.05f, 1)
				.normalize()
				.scale(0.15f));
			serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + .45f, pos.z, 0, 0, 0, 0, 1);
			if (level.getRandom().nextInt(2) == 0)
				serverLevel.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + .25f, pos.z, 0, 0, 0, 0, 1);
		}

		@Override
		public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
			particleAccess.setColor(mixColors(0x0, 0x126568, random.nextFloat()));
			particleAccess.setAlpha(1f);
			if (random.nextFloat() < 1 / 128f)
				particleAccess.spawnExtraParticle(ParticleTypes.SOUL_FIRE_FLAME, .125f);
			if (random.nextFloat() < 1 / 32f)
				particleAccess.spawnExtraParticle(ParticleTypes.SMOKE, .125f);
		}

		@Override
		public void affectEntity(Entity entity, Level level) {
			if (!(level instanceof ServerLevel serverLevel))
				return;

			if (entity instanceof LivingEntity livingEntity) {
				livingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
				livingEntity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1, false, false));
			}
			if (entity instanceof Horse horse) {
				int progress = horse.getAttachedOrElse(FanRegistry.HAUNTING_PROGRESS, 0);
				if (progress < 100) {
					if (progress % 10 == 0) {
						level.playSound(null, entity.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL,
							1f, 1.5f * progress / 100f);
					}
					horse.setAttached(FanRegistry.HAUNTING_PROGRESS, progress + 1);
					return;
				}

				level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE,
					SoundSource.NEUTRAL, 1.25f, 0.65f);

				SkeletonHorse skeletonHorse = EntityTypes.SKELETON_HORSE.create(serverLevel, EntitySpawnReason.CONVERSION);
				if (skeletonHorse == null)
					return;
				if (!horse.getBodyArmorItem()
					.isEmpty()) {
					horse.spawnAtLocation(serverLevel, horse.getBodyArmorItem());
					horse.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
				}

				try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(horse.problemPath(), CreatePolymer.LOGGER)) {
					TagValueOutput output = TagValueOutput.createWithContext(reporter, horse.registryAccess());
					horse.saveWithoutId(output);
					CompoundTag serializeNBT = output.buildResult();
					serializeNBT.remove("UUID");
					skeletonHorse.load(TagValueInput.create(reporter, skeletonHorse.registryAccess(), serializeNBT));
				}
				skeletonHorse.setPos(horse.position());
				level.addFreshEntity(skeletonHorse);
				horse.discard();
			}
		}
	}

	public static class SmokingType implements FanProcessingType {
		@Override
		public boolean isValidAt(Level level, BlockPos pos) {
			FluidState fluidState = level.getFluidState(pos);
			if (fluidState.is(FAN_PROCESSING_CATALYSTS_SMOKING_FLUIDS)) {
				return true;
			}
			BlockState blockState = level.getBlockState(pos);
			if (blockState.is(FAN_PROCESSING_CATALYSTS_SMOKING_BLOCKS)) {
				if (!isLitCampfire(blockState)) {
					return false;
				}
				if (blockState.hasProperty(LitBlazeBurnerBlock.FLAME_TYPE)
					&& blockState.getValue(LitBlazeBurnerBlock.FLAME_TYPE) != LitBlazeBurnerBlock.FlameType.REGULAR) {
					return false;
				}
				return !blockState.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)
					|| blockState.getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.SMOULDERING;
			}
			return false;
		}

		@Override
		public int getPriority() {
			return 200;
		}

		@Override
		public boolean canProcess(ItemStack stack, Level level) {
			return findAutomated(level, RecipeType.SMOKING, stack)
				.isPresent();
		}

		@Override
		@Nullable
		public List<ItemStack> process(ItemStack stack, Level level) {
			return findAutomated(level, RecipeType.SMOKING, stack)
				.map(RecipeHolder::value)
				.map(r -> applyRecipeOn(stack, r))
				.orElse(null);
		}

		@Override
		public void spawnProcessingParticles(Level level, Vec3 pos) {
			if (level.getRandom().nextInt(8) != 0)
				return;
			if (level instanceof ServerLevel serverLevel)
				serverLevel.sendParticles(ParticleTypes.POOF, pos.x, pos.y + .25f, pos.z, 0, 0, 1 / 16f, 0, 1);
		}

		@Override
		public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
			particleAccess.setColor(mixColors(0x0, 0x555555, random.nextFloat()));
			particleAccess.setAlpha(1f);
			if (random.nextFloat() < 1 / 32f)
				particleAccess.spawnExtraParticle(ParticleTypes.SMOKE, .125f);
			if (random.nextFloat() < 1 / 32f)
				particleAccess.spawnExtraParticle(ParticleTypes.LARGE_SMOKE, .125f);
		}

		@Override
		public void affectEntity(Entity entity, Level level) {
			if (!(level instanceof ServerLevel serverLevel))
				return;

			if (!entity.fireImmune()) {
				entity.igniteForSeconds(2);
				// Create: CreateDamageSources.fanFire (a data pack damage type not ported)
				entity.hurtServer(serverLevel, level.damageSources().inFire(), 2);
			}
		}
	}

	public static class SplashingType implements FanProcessingType {
		@Override
		public boolean isValidAt(Level level, BlockPos pos) {
			FluidState fluidState = level.getFluidState(pos);
			if (fluidState.is(FAN_PROCESSING_CATALYSTS_SPLASHING_FLUIDS)) {
				return true;
			}
			BlockState blockState = level.getBlockState(pos);
			return blockState.is(FAN_PROCESSING_CATALYSTS_SPLASHING_BLOCKS);
		}

		@Override
		public int getPriority() {
			return 400;
		}

		@Override
		public boolean canProcess(ItemStack stack, Level level) {
			return find(level, FanRegistry.SPLASHING, new SingleRecipeInput(stack))
				.isPresent();
		}

		@Override
		@Nullable
		public List<ItemStack> process(ItemStack stack, Level level) {
			return find(level, FanRegistry.SPLASHING, new SingleRecipeInput(stack))
				.map(RecipeHolder::value)
				.map(r -> RecipeApplier.applyRecipeOn(level, stack, r, true))
				.orElse(null);
		}

		@Override
		public void spawnProcessingParticles(Level level, Vec3 pos) {
			if (level.getRandom().nextInt(8) != 0)
				return;
			if (!(level instanceof ServerLevel serverLevel))
				return;
			serverLevel.sendParticles(new DustParticleOptions(0x0055FF, 1), pos.x + (level.getRandom().nextFloat() - .5f) * .5f,
				pos.y + .5f, pos.z + (level.getRandom().nextFloat() - .5f) * .5f, 0, 0, 1 / 8f, 0, 1);
			serverLevel.sendParticles(ParticleTypes.SPIT, pos.x + (level.getRandom().nextFloat() - .5f) * .5f, pos.y + .5f,
				pos.z + (level.getRandom().nextFloat() - .5f) * .5f, 0, 0, 1 / 8f, 0, 1);
		}

		@Override
		public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
			particleAccess.setColor(mixColors(0x4499FF, 0x2277FF, random.nextFloat()));
			particleAccess.setAlpha(1f);
			if (random.nextFloat() < 1 / 32f)
				particleAccess.spawnExtraParticle(ParticleTypes.BUBBLE, .125f);
			if (random.nextFloat() < 1 / 32f)
				particleAccess.spawnExtraParticle(ParticleTypes.BUBBLE_POP, .125f);
		}

		@Override
		public void affectEntity(Entity entity, Level level) {
			if (!(level instanceof ServerLevel serverLevel))
				return;

			if (entity instanceof Enderman || entity.getType() == EntityTypes.SNOW_GOLEM
				|| entity.getType() == EntityTypes.BLAZE) {
				entity.hurtServer(serverLevel, entity.damageSources().drown(), 2);
			}
			if (entity.isOnFire()) {
				entity.clearFire();
				level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE,
					SoundSource.NEUTRAL, 0.7F, 1.6F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.4F);
			}
		}
	}
}

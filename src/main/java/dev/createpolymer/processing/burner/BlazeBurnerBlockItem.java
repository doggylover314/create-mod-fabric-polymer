package dev.createpolymer.processing.burner;

import java.util.List;
import java.util.Optional;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.registry.feature.BurnerRegistry;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/** Ported from Create's BlazeBurnerBlockItem (MIT, The Create Team). */
public class BlazeBurnerBlockItem extends PolymerBlockItem {

	public static final TagKey<EntityType<?>> BLAZE_BURNER_CAPTURABLE =
		TagKey.create(Registries.ENTITY_TYPE, CreatePolymer.id("blaze_burner_capturable"));

	private final boolean capturedBlaze;

	public static BlazeBurnerBlockItem empty(Properties properties) {
		return new BlazeBurnerBlockItem(BurnerRegistry.BLAZE_BURNER, properties, false);
	}

	public static BlazeBurnerBlockItem withBlaze(Block block, Properties properties) {
		return new BlazeBurnerBlockItem(block, properties, true);
	}

	private BlazeBurnerBlockItem(Block block, Properties properties, boolean capturedBlaze) {
		super(block, properties, Items.TRIAL_KEY, true);
		this.capturedBlaze = capturedBlaze;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (hasCapturedBlaze())
			return super.useOn(context);

		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockEntity be = world.getBlockEntity(pos);
		Player player = context.getPlayer();

		if (!(be instanceof SpawnerBlockEntity) || !(world instanceof ServerLevel serverLevel))
			return super.useOn(context);

		BaseSpawner spawner = ((SpawnerBlockEntity) be).getSpawner();

		for (SpawnData e : possibleSpawns(serverLevel, spawner)) {
			Optional<EntityType<?>> optionalEntity = entityTypeOf(serverLevel, e.entityToSpawn());
			if (optionalEntity.isEmpty() || !optionalEntity.get().builtInRegistryHolder().is(BLAZE_BURNER_CAPTURABLE))
				continue;

			spawnCaptureEffects(world, VecHelper.getCenterOf(pos));
			if (player == null)
				return InteractionResult.SUCCESS;

			giveBurnerItemTo(player, context.getItemInHand(), context.getHand());
			return InteractionResult.SUCCESS;
		}

		return super.useOn(context);
	}

	/** The spawner's spawn potentials, or its next spawn data. Create reads these fields directly; they are private now. */
	private static List<SpawnData> possibleSpawns(ServerLevel level, BaseSpawner spawner) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(CreatePolymer.LOGGER)) {
			TagValueOutput out = TagValueOutput.createWithContext(reporter, level.registryAccess());
			spawner.save(out);
			ValueInput in = TagValueInput.create(reporter, level.registryAccess(), out.buildResult());
			List<SpawnData> possibleSpawns = in.read("SpawnPotentials", SpawnData.LIST_CODEC)
				.map(list -> list.unwrap().stream().map(Weighted::value).toList())
				.orElse(List.of());
			if (!possibleSpawns.isEmpty())
				return possibleSpawns;
			return in.read("SpawnData", SpawnData.CODEC).map(List::of).orElse(List.of());
		}
	}

	private static Optional<EntityType<?>> entityTypeOf(ServerLevel level, CompoundTag entityToSpawn) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(CreatePolymer.LOGGER)) {
			return EntityType.by(TagValueInput.create(reporter, level.registryAccess(), entityToSpawn));
		}
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack heldItem, Player player, LivingEntity entity,
		InteractionHand hand) {
		if (hasCapturedBlaze())
			return InteractionResult.PASS;
		if (!entity.typeHolder().is(BLAZE_BURNER_CAPTURABLE))
			return InteractionResult.PASS;

		Level world = player.level();
		spawnCaptureEffects(world, entity.position());
		if (world.isClientSide())
			return InteractionResult.FAIL;

		giveBurnerItemTo(player, heldItem, hand);
		entity.discard();
		return InteractionResult.FAIL;
	}

	protected void giveBurnerItemTo(Player player, ItemStack heldItem, InteractionHand hand) {
		ItemStack filled = new ItemStack(BurnerRegistry.BLAZE_BURNER_ITEM);
		if (!player.isCreative())
			heldItem.shrink(1);
		if (heldItem.isEmpty()) {
			player.setItemInHand(hand, filled);
			return;
		}
		player.getInventory()
			.placeItemBackInInventory(filled, Prediction.SERVER_ONLY);
	}

	private void spawnCaptureEffects(Level world, Vec3 vec) {
		if (!(world instanceof ServerLevel serverLevel))
			return;

		for (int i = 0; i < 40; i++) {
			Vec3 motion = VecHelper.offsetRandomly(Vec3.ZERO, world.getRandom(), .125f);
			serverLevel.sendParticles(ParticleTypes.FLAME, vec.x, vec.y, vec.z, 0, motion.x, motion.y, motion.z, 1);
			Vec3 circle = motion.multiply(1, 0, 1)
				.normalize()
				.scale(.5f);
			serverLevel.sendParticles(ParticleTypes.SMOKE, vec.x + circle.x, vec.y, vec.z + circle.z, 0, 0, -0.125, 0, 1);
		}

		BlockPos soundPos = BlockPos.containing(vec);
		world.playSound(null, soundPos, SoundEvents.BLAZE_HURT, SoundSource.HOSTILE, .25f, .75f);
		world.playSound(null, soundPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, .5f, .75f);
	}

	public boolean hasCapturedBlaze() {
		return capturedBlaze;
	}

}

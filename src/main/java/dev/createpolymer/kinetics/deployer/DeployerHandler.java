package dev.createpolymer.kinetics.deployer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;

import com.google.common.collect.HashMultimap;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.kinetics.deployer.DeployerBlockEntity.Mode;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's DeployerHandler (MIT, The Create Team).
 * <p>
 * Where Create uses NeoForge hooks, this uses the equivalent Fabric events (use block, use entity, attack block,
 * block break) and vanilla's own player code paths. NeoForge's drop capturing is replaced by {@link DropCapture}.
 */
public class DeployerHandler {
	public static final TagKey<Item> DEPLOYABLE_DRINK = TagKey.create(Registries.ITEM, CreatePolymer.id("deployable_drink"));

	/**
	 * Create captures drops with NeoForge's Entity.captureDrops and a mixin on Block.popResource. Here, the item
	 * entities (and experience orbs) that appear in an area while an action runs are the drops of that action.
	 */
	static final class DropCapture {
		private final ServerLevel level;
		private final AABB area;
		private final Set<Entity> before = Collections.newSetFromMap(new IdentityHashMap<>());

		DropCapture(ServerLevel level, AABB area) {
			this.level = level;
			this.area = area;
			before.addAll(level.getEntitiesOfClass(ItemEntity.class, area));
			before.addAll(level.getEntitiesOfClass(ExperienceOrb.class, area));
		}

		/** Item drops go to the inventory of the player; experience is discarded if requested. */
		void collect(Player player, boolean discardExperience) {
			for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, area)) {
				if (before.contains(itemEntity))
					continue;
				player.getInventory()
					.placeItemBackInInventory(itemEntity.getItem(), Prediction.SERVER_ONLY);
				itemEntity.discard();
			}
			if (!discardExperience)
				return;
			for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area))
				if (!before.contains(orb))
					orb.discard();
		}
	}

	static boolean shouldActivate(ItemStack held, Level world, BlockPos targetPos, @Nullable Direction facing) {
		if (held.getItem() instanceof BlockItem)
			if (world.getBlockState(targetPos)
				.getBlock() == ((BlockItem) held.getItem()).getBlock())
				return false;

		if (held.getItem() instanceof BucketItem bucketItem) {
			Fluid fluid = bucketContent(bucketItem);
			if (fluid != Fluids.EMPTY && world.getFluidState(targetPos)
				.getType() == fluid)
				return false;
		}

		if (!held.isEmpty() && facing == Direction.DOWN
			&& BlockEntityBehaviour.get(world, targetPos, TransportedItemStackHandlerBehaviour.TYPE) != null)
			return false;

		return true;
	}

	/** BucketItem.content is protected. */
	private static Fluid bucketContent(BucketItem bucket) {
		if (bucket == Items.WATER_BUCKET || bucket instanceof MobBucketItem)
			return Fluids.WATER;
		if (bucket == Items.LAVA_BUCKET)
			return Fluids.LAVA;
		return Fluids.EMPTY;
	}

	static void activate(DeployerFakePlayer player, Vec3 vec, BlockPos clickedPos, Vec3 extensionVector, Mode mode) {
		HashMultimap<Holder<Attribute>, AttributeModifier> attributeModifiers = HashMultimap.create();
		ItemStack mainHandItem = player.getMainHandItem();
		mainHandItem.forEachModifier(EquipmentSlot.MAINHAND, attributeModifiers::put);

		player.getAttributes()
			.addTransientAttributeModifiers(attributeModifiers);
		try {
			activateInner(player, vec, clickedPos, extensionVector, mode);
		} finally {
			player.getAttributes()
				.removeAttributeModifiers(attributeModifiers);
		}
	}

	private static void activateInner(DeployerFakePlayer player, Vec3 vec, BlockPos clickedPos, Vec3 extensionVector,
		Mode mode) {
		Vec3 rayOrigin = vec.add(extensionVector.scale(3 / 2f + 1 / 64f));
		Vec3 rayTarget = vec.add(extensionVector.scale(5 / 2f - 1 / 64f));
		player.setPos(rayOrigin.x, rayOrigin.y, rayOrigin.z);
		ItemStack stack = player.getMainHandItem();
		Item item = stack.getItem();

		// Check for entities
		final ServerLevel level = player.level();
		List<Entity> entities = level.getEntitiesOfClass(Entity.class, new AABB(clickedPos));
		InteractionHand hand = InteractionHand.MAIN_HAND;
		if (!entities.isEmpty()) {
			Entity entity = entities.get(level.getRandom()
				.nextInt(entities.size()));
			DropCapture capturedDrops = new DropCapture(level, entity.getBoundingBox()
				.inflate(3));
			boolean success = false;

			// Use on entity
			if (mode == Mode.USE) {
				InteractionResult cancelResult = UseEntityCallback.EVENT.invoker()
					.interact(player, level, hand, entity, new EntityHitResult(entity));
				if (cancelResult == InteractionResult.FAIL) {
					capturedDrops.collect(player, true);
					return;
				}
				if (cancelResult.consumesAction()) {
					success = true;
				} else if (player.interactOn(entity, hand, entity.position())
					.consumesAction()) {
					if (entity instanceof AbstractVillager villager) {
						if (villager.getTradingPlayer() instanceof DeployerFakePlayer)
							villager.setTradingPlayer(null);
					}
					success = true;
				}
				if (!success && entity instanceof Player playerEntity) {
					FoodProperties foodProperties = stack.get(DataComponents.FOOD);
					if (foodProperties != null && playerEntity.canEat(foodProperties.canAlwaysEat())) {
						ItemStack copy = stack.copy();
						player.setItemInHand(hand, stack.finishUsingItem(level, playerEntity));
						player.spawnedItemEffects = copy;
						success = true;
					}
					if (stack.is(DEPLOYABLE_DRINK)) {
						player.spawnedItemEffects = stack.copy();
						player.setItemInHand(hand, stack.finishUsingItem(level, playerEntity));
						success = true;
					}
				}
			}

			// Punch entity
			if (mode == Mode.PUNCH) {
				player.resetAttackStrengthTicker();
				player.attack(entity);
				if (entity instanceof LivingEntity livingEntity)
					DeployerFakePlayer.makeEntityIgnoreAttack(livingEntity);
				success = true;
			}

			capturedDrops.collect(player, true);
			if (success)
				return;
		}

		// Shoot ray
		ClipContext rayTraceContext =
			new ClipContext(rayOrigin, rayTarget, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
		BlockHitResult result = level.clip(rayTraceContext);
		if (!result.getBlockPos()
			.equals(clickedPos))
			result = new BlockHitResult(result.getLocation(), result.getDirection(), clickedPos, result.isInside());
		BlockState clickedState = level.getBlockState(clickedPos);
		Direction face = result.getDirection();

		// Left click
		if (mode == Mode.PUNCH) {
			if (!level.mayInteract(player, clickedPos))
				return;
			if (clickedState.getShape(level, clickedPos)
				.isEmpty()) {
				player.blockBreakingProgress = null;
				return;
			}
			if (AttackBlockCallback.EVENT.invoker()
				.interact(player, level, hand, clickedPos, face) != InteractionResult.PASS)
				return;
			if (DeployerBlockHelper.extinguishFire(level, player, clickedPos, face))
				return;
			clickedState.attack(level, clickedPos, player);
			if (stack.isEmpty())
				return;

			float progress = clickedState.getDestroyProgress(player, level, clickedPos) * 16;
			float before = 0;
			Pair<BlockPos, Float> blockBreakingProgress = player.blockBreakingProgress;
			if (blockBreakingProgress != null)
				before = blockBreakingProgress.getValue();
			progress += before;
			level.playSound(null, clickedPos, clickedState.getSoundType()
				.getHitSound(), SoundSource.NEUTRAL, .25f, 1);

			if (progress >= 1) {
				tryHarvestBlock(player, player.gameMode, clickedPos);
				level.destroyBlockProgress(player.getId(), clickedPos, -1);
				player.blockBreakingProgress = null;
				return;
			}
			if (progress <= 0) {
				player.blockBreakingProgress = null;
				return;
			}

			if ((int) (before * 10) != (int) (progress * 10))
				level.destroyBlockProgress(player.getId(), clickedPos, (int) (progress * 10));
			player.blockBreakingProgress = Pair.of(clickedPos, progress);
			return;
		}

		// Right click
		UseOnContext itemusecontext = new UseOnContext(player, hand, result);
		if (!clickedState.getShape(level, clickedPos)
			.isEmpty()) {
			if (UseBlockCallback.EVENT.invoker()
				.interact(player, level, hand, result) != InteractionResult.PASS)
				return;
		}

		// Use on block
		if (safeOnUse(clickedState, level, clickedPos, player, hand, result).consumesAction())
			return;
		if (stack.isEmpty())
			return;

		// Reposition fire placement for convenience
		if (item == Items.FLINT_AND_STEEL) {
			Direction newFace = result.getDirection();
			BlockPos newPos = result.getBlockPos();
			if (!BaseFireBlock.canBePlacedAt(level, clickedPos, newFace))
				newFace = Direction.UP;
			if (clickedState.isAir())
				newPos = newPos.relative(face.getOpposite());
			result = new BlockHitResult(result.getLocation(), newFace, newPos, result.isInside());
			itemusecontext = new UseOnContext(player, hand, result);
		}

		// 'Inert' item use behaviour & block placement
		InteractionResult onItemUse = stack.useOn(itemusecontext);
		if (onItemUse.consumesAction())
			return;

		if (item == Items.ENDER_PEARL)
			return;
		if (stack.is(DEPLOYABLE_DRINK))
			return;

		// Buckets create their own ray. Create contains it with a fake wall around the active area, here the reach of
		// the player is shortened to the clicked block.
		AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		double previousReach = reach.getBaseValue();
		if (item instanceof BucketItem)
			reach.setBaseValue(1);

		InteractionResult onItemRightClick;
		try {
			onItemRightClick = stack.use(level, player, hand);
		} finally {
			reach.setBaseValue(previousReach);
		}

		ItemStack resultStack = onItemRightClick instanceof InteractionResult.Success success
			&& success.heldItemTransformedTo() != null ? success.heldItemTransformedTo() : player.getItemInHand(hand);
		if (resultStack != stack || resultStack.getUseDuration(player) > 0) {
			player.setItemInHand(hand, resultStack);
		}

		if (!player.getUseItem()
			.isEmpty())
			player.setItemInHand(hand, stack.finishUsingItem(level, player));

		player.stopUsingItem();
	}

	/**
	 * Create's version of the vanilla block breaking, done with the player's game mode. The drops end up in the
	 * deployer's inventory.
	 */
	public static boolean tryHarvestBlock(ServerPlayer player, ServerPlayerGameMode interactionManager, BlockPos pos) {
		DropCapture capturedDrops = new DropCapture(player.level(), new AABB(pos).inflate(1));
		boolean destroyed = interactionManager.destroyBlock(pos);
		capturedDrops.collect(player, false);
		return destroyed;
	}

	/** Create's version captures the drops of the block, so that they end up in the deployer. */
	public static InteractionResult safeOnUse(BlockState state, Level world, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult ray) {
		DropCapture capturedDrops = world instanceof ServerLevel serverLevel
			? new DropCapture(serverLevel, new AABB(pos).inflate(1)) : null;
		try {
			return invokeUse(state, world, player, hand, ray);
		} finally {
			if (capturedDrops != null)
				capturedDrops.collect(player, false);
		}
	}

	/** Create's BlockHelper.invokeUse */
	private static InteractionResult invokeUse(BlockState state, Level level, Player player, InteractionHand hand,
		BlockHitResult ray) {
		InteractionResult itemInteraction = state.useItemOn(player.getItemInHand(hand), level, player, hand, ray);
		if (itemInteraction.consumesAction())
			return itemInteraction;

		if (itemInteraction instanceof InteractionResult.TryEmptyHandInteraction && hand == InteractionHand.MAIN_HAND) {
			InteractionResult interactionResult = state.useWithoutItem(level, player, ray);
			if (interactionResult.consumesAction())
				return interactionResult;
		}

		return InteractionResult.PASS;
	}

}

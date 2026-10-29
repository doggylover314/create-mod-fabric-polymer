package dev.createpolymer.equipment.sandPaper;

import dev.createpolymer.equipment.EquipmentSounds;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Ported from Create's SandPaperItem (MIT, The Create Team). The item being polished is kept in a server-only data
 * component. Vanilla clients get a consumable component on their copy of the item, so they play the eating animation
 * and the sanding sound while it is used.
 */
public class SandPaperItem extends SimplePolymerItem {

	public SandPaperItem(Properties properties) {
		super(properties.durability(8)
			.enchantable(1), Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult use(Level worldIn, Player playerIn, InteractionHand handIn) {
		ItemStack itemstack = playerIn.getItemInHand(handIn);

		if (itemstack.has(EquipmentRegistry.SAND_PAPER_POLISHING)) {
			playerIn.startUsingItem(handIn);
			return InteractionResult.CONSUME;
		}

		InteractionHand otherHand =
			handIn == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack itemInOtherHand = playerIn.getItemInHand(otherHand);
		if (SandPaperPolishingRecipe.canPolish(worldIn, itemInOtherHand)) {
			ItemStack item = itemInOtherHand.copy();
			ItemStack toPolish = item.split(1);
			playerIn.startUsingItem(handIn);
			itemstack.set(EquipmentRegistry.SAND_PAPER_POLISHING, new SandPaperItemComponent(toPolish));
			playerIn.setItemInHand(otherHand, item);
			return InteractionResult.SUCCESS;
		}

		BlockHitResult raytraceresult = getPlayerPOVHitResult(worldIn, playerIn, ClipContext.Fluid.NONE);
		Vec3 hitVec = raytraceresult.getLocation();

		AABB bb = new AABB(hitVec, hitVec).inflate(1f);
		ItemEntity pickUp = null;
		for (ItemEntity itemEntity : worldIn.getEntitiesOfClass(ItemEntity.class, bb)) {
			if (!itemEntity.isAlive())
				continue;
			if (itemEntity.position()
				.distanceTo(playerIn.position()) > 3)
				continue;
			ItemStack stack = itemEntity.getItem();
			if (!SandPaperPolishingRecipe.canPolish(worldIn, stack))
				continue;
			pickUp = itemEntity;
			break;
		}

		if (pickUp == null)
			return InteractionResult.FAIL;

		ItemStack item = pickUp.getItem()
			.copy();
		ItemStack toPolish = item.split(1);

		playerIn.startUsingItem(handIn);

		if (!worldIn.isClientSide()) {
			itemstack.set(EquipmentRegistry.SAND_PAPER_POLISHING, new SandPaperItemComponent(toPolish));
			if (item.isEmpty())
				pickUp.discard();
			else
				pickUp.setItem(item);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entityLiving) {
		if (!(entityLiving instanceof Player player))
			return stack;
		SandPaperItemComponent component = stack.get(EquipmentRegistry.SAND_PAPER_POLISHING);
		if (component != null) {
			ItemStack toPolish = component.item();
			ItemStack polished =
				SandPaperPolishingRecipe.applyPolish(level, entityLiving.position(), toPolish, stack);

			if (level.isClientSide()) {
				spawnParticles(entityLiving.getEyePosition(1)
					.add(entityLiving.getLookAngle().scale(.5f)), toPolish, level);
				return stack;
			}

			Inventory playerInv = player.getInventory();
			if (!polished.isEmpty()) {
				playerInv.placeItemBackInInventory(polished, Prediction.SERVER_ONLY);
			}

			ItemStackTemplate remainder = toPolish.getItem()
				.getCraftingRemainder();
			if (remainder != null) {
				playerInv.placeItemBackInInventory(remainder.create(), Prediction.SERVER_ONLY);
			}

			stack.remove(EquipmentRegistry.SAND_PAPER_POLISHING);
			stack.hurtAndBreak(1, entityLiving, entityLiving.getUsedItemHand());
		}

		return stack;
	}

	public static void spawnParticles(Vec3 location, ItemStack polishedStack, Level world) {
		if (!(world instanceof ServerLevel serverLevel) || polishedStack.isEmpty())
			return;
		serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(polishedStack)),
			location.x, location.y, location.z, 20, 1 / 8f, 1 / 8f, 1 / 8f, 0.05);
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
		// Create triggers its use effects every tick: one item particle per tick, sanding sound every 7th tick
		SandPaperItemComponent component = stack.get(EquipmentRegistry.SAND_PAPER_POLISHING);
		if (component == null || !(level instanceof ServerLevel serverLevel))
			return;
		ItemStack polishing = component.item();
		if (!polishing.isEmpty()) {
			Vec3 pos = entity.getEyePosition()
				.add(entity.getLookAngle()
					.scale(.5f));
			serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(polishing)),
				pos.x, pos.y - .2, pos.z, 1, .1, .1, .1, .02);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level worldIn, LivingEntity entityLiving, int timeLeft) {
		if (!(entityLiving instanceof Player player))
			return false;
		SandPaperItemComponent component = stack.get(EquipmentRegistry.SAND_PAPER_POLISHING);
		if (component != null) {
			player.getInventory()
				.placeItemBackInInventory(component.item(), Prediction.SERVER_ONLY);
			stack.remove(EquipmentRegistry.SAND_PAPER_POLISHING);
			return true;
		}
		return false;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);

		// Create's AXE_SCRAPE and AXE_WAX_OFF tool actions
		Optional<BlockState> newState = WeatheringCopper.getPrevious(state);
		if (newState.isPresent()) {
			EquipmentSounds.sandingLong(level, player, pos, 1, 1 + (level.getRandom().nextFloat() * 0.5f - 1f) / 5f);
			level.levelEvent(player, LevelEvent.PARTICLES_SCRAPE, pos, 0);
		} else {
			newState = Optional.ofNullable(HoneycombItem.WAX_OFF_BY_BLOCK.get()
				.get(state.getBlock()))
				.map(block -> block.withPropertiesOf(state));
			if (newState.isPresent()) {
				EquipmentSounds.sandingLong(level, player, pos, 1, 1 + (level.getRandom().nextFloat() * 0.5f - 1f) / 5f);
				level.levelEvent(player, LevelEvent.PARTICLES_WAX_OFF, pos, 0);
			}
		}

		if (newState.isPresent()) {
			level.setBlockAndUpdate(pos, newState.get());
			if (player != null)
				stack.hurtAndBreak(1, player, context.getHand());
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.EAT;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 32;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		// What Create does in triggerUseEffects, vanilla clients do for consumables: play the eating animation and a
		// sound every few ticks while the item is used
		out.set(DataComponents.CONSUMABLE, Consumable.builder()
			.consumeSeconds(32 / 20f)
			.animation(ItemUseAnimation.EAT)
			.sound(net.minecraft.core.Holder.direct(SoundEvents.SAND_HIT))
			.hasConsumeParticles(false)
			.build());
	}
}

package dev.createpolymer.equipment.tool;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * Create's cardboard sword, ported from Create's CardboardSwordItem (MIT, The Create Team). Hitting with it makes a
 * soft noise and never hurts: the target is knocked back as a player's hit would knock it back, and a non-arthropod
 * creature is slowed. Create's knockback packet to the player is not sent: vanilla sends the new velocity itself.
 */
public class CardboardSwordItem extends SimplePolymerItem {

	public CardboardSwordItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	/** Create's cardboardSwordsMakeNoiseOnClick: a bonk when a block is hit with the sword. */
	public static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
		if (player.getItemInHand(hand).getItem() instanceof CardboardSwordItem && level instanceof ServerLevel serverLevel)
			bonk(serverLevel, pos, 0.5f, 1.85f);
		return InteractionResult.PASS;
	}

	/** Create's cardboardSwordsCannotHurtYou: a main-hand attack with the sword is only a knockback. */
	public static InteractionResult onAttackEntity(Player attacker, Level level, Entity entity) {
		if (!(entity instanceof LivingEntity target) || target.is(EntityTypeTags.ARTHROPOD))
			return InteractionResult.PASS;
		ItemStack stack = attacker.getItemInHand(InteractionHand.MAIN_HAND);
		if (!(stack.getItem() instanceof CardboardSwordItem))
			return InteractionResult.PASS;

		if (level instanceof ServerLevel serverLevel)
			bonk(serverLevel, attacker.blockPosition(), 0.75f, 1.85f);

		// Reference player.attack(): the knockback without the damage
		float knockbackStrength = (float) (attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK) + 2);
		if (level instanceof ServerLevel serverLevel)
			knockbackStrength = EnchantmentHelper.modifyKnockback(serverLevel, stack, target,
				serverLevel.damageSources().playerAttack(attacker), knockbackStrength);
		if (attacker.isSprinting() && attacker.getAttackStrengthScale(0.5f) > 0.9f)
			++knockbackStrength;

		if (knockbackStrength <= 0)
			return InteractionResult.SUCCESS;

		float yRot = attacker.getYRot();
		knockback(target, knockbackStrength, yRot, attacker);

		boolean targetIsPlayer = target instanceof Player;
		MobCategory targetType = target.getType().getCategory();
		if ((targetType == MobCategory.MISC || targetType == MobCategory.CREATURE) && !targetIsPlayer)
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 9, true, false, false));

		attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
		attacker.setSprinting(false);
		return InteractionResult.SUCCESS;
	}

	public static void knockback(LivingEntity target, double knockbackStrength, float yRot, Player attacker) {
		target.stopRiding();
		target.knockback(knockbackStrength * 0.5F, Mth.sin(yRot * Mth.DEG_TO_RAD), -Mth.cos(yRot * Mth.DEG_TO_RAD),
			attacker.damageSources().playerAttack(attacker), 0F, false);
	}

	/** Create's cardboard_bonk sound, which is not in vanilla: a soft wool hit stands in for it. */
	private static void bonk(ServerLevel level, BlockPos pos, float volume, float pitch) {
		level.playSound(null, pos, SoundEvents.WOOL_HIT, SoundSource.PLAYERS, volume, pitch);
	}

	/** Registers the two handlers of the sword (Create's @EventBusSubscriber methods). */
	public static void register() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> onAttackBlock(player, level, hand, pos, direction));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> onAttackEntity(player, level, entity));
	}
}

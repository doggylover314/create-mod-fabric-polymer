package dev.createpolymer.equipment;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

/**
 * Ported from Create's BuildersTeaItem (MIT, The Create Team). 26.3 items describe food and drinks with components:
 * the drinking animation, the 42 tick duration, the haste effect and the empty glass bottle that Create's
 * finishUsingItem hands back are all declared in {@link #properties()}.
 */
public class BuildersTeaItem extends SimplePolymerItem {

	public BuildersTeaItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	/** Create's AllItems.BUILDERS_TEA properties. */
	public static Item.Properties properties() {
		return new Item.Properties().stacksTo(16)
			.food(new FoodProperties(1, .6f, true), Consumable.builder()
				.consumeSeconds(42 / 20f)
				.animation(ItemUseAnimation.DRINK)
				.sound(SoundEvents.GENERIC_DRINK)
				.hasConsumeParticles(false)
				.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HASTE, 3 * 60 * 20, 0, false, false, false), 1F))
				.build())
			.usingConvertsTo(Items.GLASS_BOTTLE);
	}
}

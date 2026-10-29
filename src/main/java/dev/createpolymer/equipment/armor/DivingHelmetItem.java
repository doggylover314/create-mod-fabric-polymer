package dev.createpolymer.equipment.armor;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;
import dev.createpolymer.CreatePolymer;

/**
 * Ported from Create's DivingHelmetItem (MIT, The Create Team). Create hooks NeoForge's LivingBreatheEvent; there is
 * no such event here, so {@link #breatheUnderwater(LivingEntity)} runs for every player each server tick.
 * Create's aqua affinity override is the same attribute modifier the enchantment applies.
 */
public class DivingHelmetItem extends BaseArmorItem {
	public static final EquipmentSlot SLOT = EquipmentSlot.HEAD;
	public static final ArmorType TYPE = ArmorType.HELMET;

	public DivingHelmetItem(ArmorMaterial material, Properties properties) {
		super(material, TYPE, properties, DivingHelmetItem::withAquaAffinity);
	}

	private static ItemAttributeModifiers withAquaAffinity(ItemAttributeModifiers base) {
		return base.withModifierAdded(Attributes.SUBMERGED_MINING_SPEED,
			new AttributeModifier(CreatePolymer.id("diving_helmet_aqua_affinity"), 4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
			EquipmentSlotGroup.HEAD);
	}

	public static boolean isWornBy(Entity entity) {
		return !getWornItem(entity).isEmpty();
	}

	public static ItemStack getWornItem(Entity entity) {
		if (!(entity instanceof LivingEntity livingEntity)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = livingEntity.getItemBySlot(SLOT);
		if (!(stack.getItem() instanceof DivingHelmetItem)) {
			return ItemStack.EMPTY;
		}
		return stack;
	}

	public static void breatheUnderwater(LivingEntity entity) {
		Level level = entity.level();

		ItemStack helmet = getWornItem(entity);
		if (helmet.isEmpty())
			return;

		boolean lavaDiving = entity.isInLava();
		if (!helmet.has(DataComponents.DAMAGE_RESISTANT) && lavaDiving)
			return;

		boolean canBreathe = !entity.isEyeInFluid(FluidTags.WATER);
		if (canBreathe && !lavaDiving)
			return;

		List<ItemStack> backtanks = BacktankUtil.getAllWithAir(entity);
		if (backtanks.isEmpty())
			return;

		if (lavaDiving) {
			if (backtanks.stream()
				.noneMatch(backtank -> backtank.has(DataComponents.DAMAGE_RESISTANT)))
				return;
		}

		if (level.getGameTime() % 20 == 0)
			BacktankUtil.consumeAir(entity, backtanks.get(0), 1);

		if (lavaDiving)
			return;

		entity.setAirSupply(entity.getMaxAirSupply());
	}

	/** Runs the helmet's breathing for players, see {@link #breatheUnderwater(LivingEntity)}. */
	public static void tick(ServerPlayer player) {
		if (player.isSpectator())
			return;
		breatheUnderwater(player);
	}
}

package dev.createpolymer.equipment.armor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.createpolymer.equipment.EquipmentConfig;
import dev.createpolymer.equipment.EquipmentSounds;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Ported from Create's BacktankUtil (MIT, The Create Team). The item bar helpers (isBarVisible, getBarWidth,
 * getBarColor) only exist to draw on the client, where the player's backtanks are known; vanilla clients draw the
 * durability bar of the tool itself instead.
 */
public class BacktankUtil {

	private static final List<Function<LivingEntity, List<ItemStack>>> BACKTANK_SUPPLIERS = new ArrayList<>();

	static {
		addBacktankSupplier(entity -> {
			List<ItemStack> stacks = new ArrayList<>();
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR)
					continue;
				ItemStack itemStack = entity.getItemBySlot(slot);
				if (itemStack.is(EquipmentRegistry.PRESSURIZED_AIR_SOURCES))
					stacks.add(itemStack);
			}

			return stacks;
		});
	}

	public static List<ItemStack> getAllWithAir(LivingEntity entity) {
		List<ItemStack> all = new ArrayList<>();

		for (Function<LivingEntity, List<ItemStack>> supplier : BACKTANK_SUPPLIERS) {
			List<ItemStack> result = supplier.apply(entity);

			for (ItemStack stack : result)
				if (hasAirRemaining(stack))
					all.add(stack);
		}

		// Sort with ascending order (we want to prioritize the most empty so things actually run out)
		all.sort((a, b) -> Float.compare(getAir(a), getAir(b)));

		return all;
	}

	public static boolean hasAirRemaining(ItemStack backtank) {
		return getAir(backtank) > 0;
	}

	public static int getAir(ItemStack backtank) {
		return Math.min(backtank.getOrDefault(EquipmentRegistry.BACKTANK_AIR, 0), maxAir(backtank));
	}

	public static void consumeAir(LivingEntity entity, ItemStack backtank, int i) {
		int maxAir = maxAir(backtank);
		int air = getAir(backtank);
		int newAir = Math.max(air - i, 0);
		backtank.set(EquipmentRegistry.BACKTANK_AIR, Math.min(newAir, maxAir));

		if (!(entity instanceof ServerPlayer player))
			return;

		sendWarning(player, air, newAir, maxAir / 10f);
		sendWarning(player, air, newAir, 1);
	}

	private static void sendWarning(ServerPlayer player, float air, float newAir, float threshold) {
		if (newAir > threshold)
			return;
		if (air <= threshold)
			return;

		boolean depleted = threshold == 1;
		MutableComponent component = Component.translatable(depleted ? "create.backtank.depleted" : "create.backtank.low");

		EquipmentSounds.deny(player.level(), player.blockPosition(), 1, 1.25f);
		EquipmentSounds.steam(player.level(), player.blockPosition(), .5f, .5f);

		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
			Component.literal("⚠ ")
				.withStyle(depleted ? ChatFormatting.RED : ChatFormatting.GOLD)
				.append(component.withStyle(ChatFormatting.GRAY))));
		player.connection.send(new ClientboundSetTitleTextPacket(CommonComponents.EMPTY));
	}

	public static int maxAir(ItemStack backtank) {
		int enchantLevel = 0;
		ItemEnchantments enchants = backtank.getEnchantments();
		for (Entry<Holder<Enchantment>> entry : enchants.entrySet()) {
			if (entry.getKey()
				.is(EquipmentRegistry.CAPACITY)) {
				enchantLevel = entry.getIntValue();
				break;
			}
		}
		return maxAir(enchantLevel);
	}

	public static int maxAir(int enchantLevel) {
		return EquipmentConfig.airInBacktank + EquipmentConfig.enchantedBacktankCapacity * enchantLevel;
	}

	public static int maxAirWithoutEnchants() {
		return EquipmentConfig.airInBacktank;
	}

	public static boolean canAbsorbDamage(LivingEntity entity, int usesPerTank) {
		if (usesPerTank == 0)
			return true;
		if (entity instanceof Player && ((Player) entity).isCreative())
			return true;
		List<ItemStack> backtanks = getAllWithAir(entity);
		if (backtanks.isEmpty())
			return false;
		int cost = Math.max(maxAirWithoutEnchants() / usesPerTank, 1);
		consumeAir(entity, backtanks.getFirst(), cost);
		return true;
	}

	/**
	 * Use this method to add custom entry points to the backtank item stack supplier, e.g. getting them from custom
	 * slots or items.
	 */
	public static void addBacktankSupplier(Function<LivingEntity, List<ItemStack>> supplier) {
		BACKTANK_SUPPLIERS.add(supplier);
	}
}

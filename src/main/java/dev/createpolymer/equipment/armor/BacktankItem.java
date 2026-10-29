package dev.createpolymer.equipment.armor;

import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import dev.createpolymer.registry.feature.EquipmentRegistry;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Ported from Create's BacktankItem (MIT, The Create Team). Right-clicking a block places the backtank block, like
 * Create does; right-clicking the air wears it. The client copy of the item gets a durability bar that shows the
 * remaining air, as Create draws its own bar.
 */
public class BacktankItem extends BaseArmorItem {
	public static final EquipmentSlot SLOT = EquipmentSlot.CHEST;
	public static final ArmorType TYPE = ArmorType.CHESTPLATE;
	public static final int BAR_COLOR = 0xEFEFEF;

	private final Supplier<BacktankBlockItem> blockItem;

	/** The placing right click also reaches use(), where vanilla would wear the item. This is the player and tick of the last placement. */
	private static UUID lastPlacer;
	private static long lastPlacedTick;

	public BacktankItem(ArmorMaterial material, Properties properties, Supplier<BacktankBlockItem> placeable) {
		super(material, TYPE, properties, true);
		this.blockItem = placeable;
	}

	@Nullable
	public static BacktankItem getWornBy(Entity entity) {
		if (!(entity instanceof LivingEntity livingEntity)) {
			return null;
		}
		if (!(livingEntity.getItemBySlot(SLOT).getItem() instanceof BacktankItem item)) {
			return null;
		}
		return item;
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		InteractionResult result = blockItem.get()
			.useOn(ctx);
		Player player = ctx.getPlayer();
		if (player != null && result.consumesAction()) {
			lastPlacer = player.getUUID();
			lastPlacedTick = ctx.getLevel()
				.getGameTime();
		}
		return result;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.getUUID()
			.equals(lastPlacer) && level.getGameTime() == lastPlacedTick)
			return InteractionResult.PASS;
		return super.use(level, player, hand);
	}

	public Block getBlock() {
		return blockItem.get().getBlock();
	}

	public static int getRemainingAir(ItemStack stack) {
		return stack.getOrDefault(EquipmentRegistry.BACKTANK_AIR, 0);
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		// Players see the remaining air as a durability bar
		int max = BacktankUtil.maxAir(stack);
		out.set(DataComponents.MAX_DAMAGE, max);
		out.set(DataComponents.DAMAGE, max - Mth.clamp(getRemainingAir(stack), 0, max));
	}

	/** The block item that places the backtank block; the item players hold is the backtank itself. */
	public static class BacktankBlockItem extends PolymerBlockItem {
		private final Supplier<Item> actualItem;

		public BacktankBlockItem(Block block, Supplier<Item> actualItem, Properties properties) {
			super(block, properties, Items.TRIAL_KEY, true);
			this.actualItem = actualItem;
		}

		public Item getActualItem() {
			return actualItem.get();
		}
	}
}

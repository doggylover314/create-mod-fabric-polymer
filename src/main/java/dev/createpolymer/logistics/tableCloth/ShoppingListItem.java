package dev.createpolymer.logistics.tableCloth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.packager.InventorySummary;
import dev.createpolymer.registry.feature.StockRegistry;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.IntAttached;
import dev.createpolymer.util.Lang;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class ShoppingListItem extends SimplePolymerItem {
	public record ShoppingList(@Unmodifiable List<IntAttached<BlockPos>> purchases, UUID shopOwner, UUID shopNetwork) {
		public static final Codec<ShoppingList> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.list(IntAttached.codec(BlockPos.CODEC)).fieldOf("purchases").forGetter(ShoppingList::purchases),
			UUIDUtil.CODEC.fieldOf("shop_owner").forGetter(ShoppingList::shopOwner),
			UUIDUtil.CODEC.fieldOf("shop_network").forGetter(ShoppingList::shopNetwork)
		).apply(instance, ShoppingList::new));

		public ShoppingList duplicate() {
			return new ShoppingList(new ArrayList<>(purchases.stream()
				.map(ia -> IntAttached.with(ia.getFirst(), ia.getSecond()))
				.toList()), shopOwner, shopNetwork);
		}

		public int getPurchases(BlockPos clothPos) {
			for (IntAttached<BlockPos> entry : purchases)
				if (clothPos.equals(entry.getValue()))
					return entry.getFirst();
			return 0;
		}

		public Couple<InventorySummary> bakeEntries(LevelAccessor level, @Nullable BlockPos clothPosToIgnore) {
			InventorySummary input = new InventorySummary();
			InventorySummary output = new InventorySummary();

			for (IntAttached<BlockPos> entry : purchases) {
				if (clothPosToIgnore != null && clothPosToIgnore.equals(entry.getValue()))
					continue;
				if (!(level.getBlockEntity(entry.getValue()) instanceof TableClothBlockEntity dcbe))
					continue;
				input.add(dcbe.getPaymentItem(), dcbe.getPaymentAmount() * entry.getFirst());
				for (BigItemStack stackEntry : dcbe.requestData.encodedRequest().stacks())
					output.add(stackEntry.stack, stackEntry.count * entry.getFirst());
			}

			return Couple.create(output, input);
		}

		public static class Mutable {
			private final List<IntAttached<BlockPos>> purchases = new ArrayList<>();
			private final UUID shopOwner;
			private final UUID shopNetwork;

			public Mutable(ShoppingList list) {
				this.purchases.addAll(list.purchases);
				this.shopOwner = list.shopOwner;
				this.shopNetwork = list.shopNetwork;
			}

			// Y value of clothPos is pixel perfect (x16)
			public void addPurchases(BlockPos clothPos, int amount) {
				for (IntAttached<BlockPos> entry : purchases) {
					if (clothPos.equals(entry.getValue())) {
						entry.setFirst(entry.getFirst() + amount);
						return;
					}
				}
				purchases.add(IntAttached.with(amount, clothPos));
			}

			public ShoppingList toImmutable() {
				return new ShoppingList(purchases, shopOwner, shopNetwork);
			}
		}
	}

	public ShoppingListItem(Properties pProperties) {
		super(pProperties, Items.TRIAL_KEY, true);
	}

	@Nullable
	public static ShoppingList getList(ItemStack stack) {
		return stack.get(StockRegistry.SHOPPING_LIST);
	}

	public static ItemStack saveList(ItemStack stack, ShoppingList list, String address) {
		stack.set(StockRegistry.SHOPPING_LIST, list);
		stack.set(StockRegistry.SHOPPING_LIST_ADDRESS, address);
		return stack;
	}

	public static String getAddress(ItemStack stack) {
		return stack.getOrDefault(StockRegistry.SHOPPING_LIST_ADDRESS, "");
	}

	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
		Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		ShoppingList list = getList(stack);

		Couple<InventorySummary> lists = list == null ? null : bakeForTooltip(list);
		if (lists != null) {

			{
				for (InventorySummary items : lists) {
					List<BigItemStack> entries = items.getStacksByCount();
					boolean cost = items == lists.getSecond();

					if (cost)
						tooltipComponents.accept(Component.empty());

					if (entries.size() == 1) {
						BigItemStack entry = entries.get(0);
						tooltipComponents.accept((cost ? translate("table_cloth.total_cost") : Lang.builder(CreatePolymer.MOD_ID).text(""))
							.style(ChatFormatting.GOLD)
							.add(Lang.builder(CreatePolymer.MOD_ID)
								.add(entry.stack.getHoverName().plainCopy())
								.text(" x")
								.text(String.valueOf(entry.count))
								.style(cost ? ChatFormatting.YELLOW : ChatFormatting.GRAY))
							.component());

					} else {
						if (cost)
							tooltipComponents.accept(translate("table_cloth.total_cost")
								.style(ChatFormatting.GOLD)
								.component());
						for (BigItemStack entry : entries) {
							tooltipComponents.accept(Lang.builder(CreatePolymer.MOD_ID)
								.add(entry.stack.getHoverName().plainCopy())
								.text(" x")
								.text(String.valueOf(entry.count))
								.style(cost ? ChatFormatting.YELLOW : ChatFormatting.GRAY)
								.component());
						}
					}
				}
			}
		}

		tooltipComponents.accept(translate("table_cloth.hand_to_shop_keeper")
			.style(ChatFormatting.GRAY)
			.component());

		tooltipComponents.accept(translate("table_cloth.sneak_click_discard")
			.style(ChatFormatting.DARK_GRAY)
			.component());
	}

	/** Tooltips have no level. The shops are looked for in the levels of the server that is sending the tooltip. */
	@Nullable
	private static Couple<InventorySummary> bakeForTooltip(ShoppingList list) {
		PacketContext context = PacketContext.get();
		MinecraftServer server = context == null ? null : context.get(PacketContext.SERVER_INSTANCE);
		if (server == null)
			return null;
		for (ServerLevel level : server.getAllLevels()) {
			Couple<InventorySummary> baked = list.bakeEntries(level, null);
			if (!baked.getFirst()
				.isEmpty())
				return baked;
		}
		return null;
	}

	private static dev.createpolymer.util.LangBuilder translate(String key) {
		return Lang.builder(CreatePolymer.MOD_ID)
			.translate(key);
	}

	@Override
	public InteractionResult use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
		if (pUsedHand == InteractionHand.OFF_HAND || pPlayer == null || !pPlayer.isShiftKeyDown())
			return InteractionResult.PASS;

		if (!pLevel.isClientSide()) {
			translate("table_cloth.shopping_list_discarded").sendStatus(pPlayer);
			pPlayer.playSound(SoundEvents.BOOK_PAGE_TURN);
		}
		return InteractionResult.SUCCESS.heldItemTransformedTo(ItemStack.EMPTY);
	}

	@Override
	public InteractionResult useOn(UseOnContext pContext) {
		InteractionHand pUsedHand = pContext.getHand();
		Player pPlayer = pContext.getPlayer();
		if (pUsedHand == InteractionHand.OFF_HAND || pPlayer == null || !pPlayer.isShiftKeyDown())
			return InteractionResult.PASS;
		pPlayer.setItemInHand(pUsedHand, ItemStack.EMPTY);

		translate("table_cloth.shopping_list_discarded").sendStatus(pPlayer);
		pPlayer.playSound(SoundEvents.BOOK_PAGE_TURN);
		return InteractionResult.SUCCESS;
	}
}

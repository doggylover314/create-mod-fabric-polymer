package dev.createpolymer.logistics.stockTicker;

import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import dev.createpolymer.logistics.packagerLink.WiFiEffectPacket;
import dev.createpolymer.logistics.redstoneRequester.RedstoneRequesterBlock;
import net.minecraft.server.level.ServerPlayer;

/**
 * Create's PackageOrderRequestPacket, which the stock keeper screen sends to the server: here the GUI calls
 * {@link #apply} itself.
 */
public final class PackageOrderRequest {
	private PackageOrderRequest() {}

	public static void apply(ServerPlayer player, StockTickerBlockEntity be, PackageOrderWithCrafts order, String address,
		boolean encodeRequester) {
		if (encodeRequester) {
			if (!order.isEmpty())
				StockSounds.confirm(be.getLevel(), be.getBlockPos(), 1, 1);
			player.closeContainer();
			RedstoneRequesterBlock.programRequester(player, be, order, address);
			return;
		}

		if (!order.isEmpty()) {
			StockSounds.stockTickerRequest(be.getLevel(), be.getBlockPos());
			WiFiEffectPacket.send(player.level(), be.getBlockPos());
		}

		be.broadcastPackageRequest(RequestType.PLAYER, order, null, address);
	}
}

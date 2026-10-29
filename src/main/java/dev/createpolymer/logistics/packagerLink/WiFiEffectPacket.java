package dev.createpolymer.logistics.packagerLink;

import dev.createpolymer.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Create sends this packet to the clients around a stock link (or stock ticker) that just took part in a request, so
 * they play its effect. The effect is played from the server here, as sound and particles.
 */
public final class WiFiEffectPacket {
	private WiFiEffectPacket() {}

	public static void send(Level level, BlockPos pos) {
		if (level.isClientSide())
			return;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof PackagerLinkBlockEntity plbe)
			plbe.playEffect();
		else if (blockEntity instanceof StockTickerBlockEntity stbe)
			stbe.playEffect();
	}
}

package dev.createpolymer.logistics.packagerLink;

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
		// The stock ticker has the same effect; it will call this class from its own block entity.
	}
}

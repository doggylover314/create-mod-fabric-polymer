package dev.createpolymer.kinetics.turntable;

import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's TurntableHandler (MIT, The Create Team). Create turns the local player from the client's
 * render loop; here the server does it once per tick with a relative rotation packet, so the camera moves in
 * one step per tick instead of per frame.
 */
public class TurntableHandler {

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers())
			tickPlayer(player);
	}

	public static void tickPlayer(ServerPlayer player) {
		if (player.isSpectator() || !player.onGround())
			return;

		Level level = player.level();
		BlockPos pos = player.getOnPos();
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof TurntableBlock))
			return;
		if (!(level.getBlockEntity(pos) instanceof TurntableBlockEntity turnTable))
			return;

		float speed = turnTable.getSpeed() * (2 / 3f);
		if (speed == 0)
			return;

		Vec3 origin = VecHelper.getCenterOf(pos);
		Vec3 offset = player.position().subtract(origin);

		if (offset.length() > 1 / 4f)
			speed *= (float) Mth.clamp((1 / 2f - offset.length()) * 2, 0, 1);
		if (speed == 0)
			return;

		player.forceSetRotation(-speed, true, 0, true);
		player.yBodyRot -= speed;
	}

}

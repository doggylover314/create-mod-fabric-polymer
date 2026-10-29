package dev.createpolymer.item;

import dev.createpolymer.kinetics.base.IRotate;
import dev.createpolymer.kinetics.base.IRotate.SpeedLevel;
import dev.createpolymer.kinetics.base.IRotate.StressImpact;
import dev.createpolymer.kinetics.base.KineticBlockEntity;
import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.base.GeneratingKineticBlockEntity;
import dev.createpolymer.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Server-side stand-in for Create's goggle overlay: shows kinetic stats of the
 * block the player looks at in the action bar.
 */
public final class GogglesInfo {
	private GogglesInfo() {}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 5 != 0)
			return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!wearsOrHolds(player))
				continue;
			HitResult hit = player.pick(player.blockInteractionRange(), 1, false);
			if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK)
				continue;
			if (!(player.level().getBlockEntity(bhr.getBlockPos()) instanceof KineticBlockEntity kbe))
				continue;
			player.sendOverlayMessage(describe(kbe));
		}
	}

	private static boolean wearsOrHolds(ServerPlayer player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.GOGGLES)
			|| player.getMainHandItem().is(ModItems.GOGGLES)
			|| player.getOffhandItem().is(ModItems.GOGGLES);
	}

	private static MutableComponent describe(KineticBlockEntity kbe) {
		float speed = kbe.getTheoreticalSpeed();
		MutableComponent text = Component.literal("");
		if (kbe.isOverStressed() && StressImpact.isEnabled())
			return text.append(Component.translatable("create.gui.stressometer.overstressed").withStyle(ChatFormatting.GOLD));

		SpeedLevel level = SpeedLevel.of(speed);
		text.append(Component.literal(Math.abs(Math.round(speed)) + " RPM").withStyle(level.getTextColor()));

		boolean hide = kbe.getBlockState().getBlock() instanceof IRotate rotate && rotate.hideStressImpact();
		if (!StressImpact.isEnabled() || hide)
			return text;

		float impact = (float) BlockStressValues.getImpact(kbe.getBlockState().getBlock());
		if (impact > 0)
			text.append(Component.literal("  Uses " + Math.round(impact * Math.abs(speed)) + " su").withStyle(ChatFormatting.AQUA));
		if (kbe instanceof GeneratingKineticBlockEntity gen && gen.getGeneratedSpeed() != 0) {
			float cap = (float) BlockStressValues.getCapacity(kbe.getBlockState().getBlock());
			text.append(Component.literal("  Provides " + Math.round(cap * Math.abs(gen.getGeneratedSpeed())) + " su").withStyle(ChatFormatting.AQUA));
		}
		if (kbe.hasNetwork()) {
			float cap = kbe.getNetworkCapacity();
			float stress = kbe.getNetworkStress();
			StressImpact si = StressImpact.of(cap == 0 ? 0 : stress / cap);
			text.append(Component.literal("  Network " + Math.round(stress) + "/" + Math.round(cap) + " su").withStyle(si.getColor()));
		}
		return text;
	}
}

package dev.createpolymer.item;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.api.equipment.goggles.IHaveGoggleInformation;
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
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Server-side stand-in for Create's goggle overlay: shows kinetic stats and the lines of
 * {@link IHaveGoggleInformation} of the block the player looks at in the action bar. The action bar has one line, so
 * the tooltip lines are packed into pages that follow each other every {@link #PAGE_TICKS} ticks.
 */
public final class GogglesInfo {
	private GogglesInfo() {}

	private static final int PAGE_TICKS = 40;
	private static final int PAGE_WIDTH = 80;
	private static final String SEPARATOR = "  |  ";

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 5 != 0)
			return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!wearsOrHolds(player))
				continue;
			HitResult hit = player.pick(player.blockInteractionRange(), 1, false);
			if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK)
				continue;
			BlockEntity be = player.level().getBlockEntity(bhr.getBlockPos());
			List<Component> pages = new ArrayList<>();
			if (be instanceof KineticBlockEntity kbe)
				pages.add(describe(kbe));
			if (be instanceof IHaveGoggleInformation info) {
				List<Component> tooltip = new ArrayList<>();
				if (info.addToGoggleTooltip(tooltip, player.isShiftKeyDown()))
					pages.addAll(paginate(tooltip));
			}
			if (pages.isEmpty())
				continue;
			int page = (int) ((server.getTickCount() / PAGE_TICKS) % pages.size());
			player.sendOverlayMessage(pages.get(page));
		}
	}

	/** Packs the non-empty lines of a goggle tooltip into action bar lines of about {@link #PAGE_WIDTH} characters. */
	public static List<Component> paginate(List<Component> tooltip) {
		List<Component> pages = new ArrayList<>();
		MutableComponent page = null;
		int length = 0;
		for (Component raw : tooltip) {
			Component line = stripIndent(raw);
			int lineLength = line.getString().length();
			if (lineLength == 0)
				continue;
			if (page != null && length + SEPARATOR.length() + lineLength > PAGE_WIDTH) {
				pages.add(page);
				page = null;
			}
			if (page == null) {
				page = Component.literal("").append(line);
				length = lineLength;
			} else {
				page.append(Component.literal(SEPARATOR).withStyle(ChatFormatting.DARK_GRAY)).append(line);
				length += SEPARATOR.length() + lineLength;
			}
		}
		if (page != null)
			pages.add(page);
		return pages;
	}

	/** LangBuilder.forGoggles indents with a literal of spaces whose sibling is the text; the action bar has no use for it. */
	private static Component stripIndent(Component line) {
		if (line.getContents() instanceof PlainTextContents.LiteralContents literal && literal.text().isBlank()
			&& !line.getSiblings().isEmpty()) {
			MutableComponent stripped = Component.literal("");
			line.getSiblings().forEach(stripped::append);
			return stripped;
		}
		return line;
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

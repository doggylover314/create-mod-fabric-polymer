package dev.createpolymer.kinetics.mechanicalArm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.kinetics.mechanicalArm.ArmInteractionPoint.Mode;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.ArmRegistry;
import dev.createpolymer.util.Lang;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Server side of Create's ArmInteractionPointHandler and ArmPlacementPacket.
 * <p>
 * Create tracks the selection in the client while the arm item is in the main hand. Here it lives in the item stack
 * ({@link Selection} in {@link ArmRegistry#ARM_POINTS}): right-clicking a block with the arm cycles it from input
 * to output to removed, left-clicking removes it, and particles outline the selected blocks. When the arm is placed,
 * {@link #flushSettings} checks the range and gives the points to the new block entity.
 */
public class ArmInteractionPointHandler {

	/** The blocks picked with an arm item. Points are absolute and only valid in the dimension they were picked in. */
	public record Selection(Identifier dimension, List<Entry> points) {
		public static final Codec<Selection> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("dimension").forGetter(Selection::dimension),
			Entry.CODEC.listOf().fieldOf("points").forGetter(Selection::points))
			.apply(i, Selection::new));

		public Selection {
			points = List.copyOf(points);
		}

		public record Entry(BlockPos pos, Mode mode) {
			public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				Codec.STRING.xmap(Mode::valueOf, Mode::name).fieldOf("mode").forGetter(Entry::mode))
				.apply(i, Entry::new));
		}
	}

	private static final int OUTLINE_INTERVAL = 4;

	private static boolean initialized;

	public static void init() {
		if (initialized)
			return;
		initialized = true;

		// before other right-click handlers (value boxes, edge interaction) so that selecting wins, as it does
		// in Create where the client cancels the event before the server sees it
		Identifier early = Identifier.fromNamespaceAndPath("create", "mechanical_arm_early");
		UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		UseBlockCallback.EVENT.register(early, ArmInteractionPointHandler::rightClickingBlocksSelectsThem);
		AttackBlockCallback.EVENT.register(early, ArmInteractionPointHandler::leftClickingBlocksDeselectsThem);
		ServerTickEvents.END_SERVER_TICK.register(ArmInteractionPointHandler::tick);
	}

	@Nullable
	private static Selection getSelection(ItemStack stack, Level level) {
		Selection selection = stack.get(ArmRegistry.ARM_POINTS);
		if (selection == null || !selection.dimension().equals(level.dimension().identifier()))
			return null;
		return selection;
	}

	private static void setSelection(ItemStack stack, Level level, List<Selection.Entry> entries) {
		if (entries.isEmpty())
			stack.remove(ArmRegistry.ARM_POINTS);
		else
			stack.set(ArmRegistry.ARM_POINTS, new Selection(level.dimension().identifier(), entries));
	}

	private static int indexOf(List<Selection.Entry> entries, BlockPos pos) {
		for (int i = 0; i < entries.size(); i++)
			if (entries.get(i).pos().equals(pos))
				return i;
		return -1;
	}

	public static InteractionResult rightClickingBlocksSelectsThem(Player player, Level world, InteractionHand hand,
		BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof ArmItem) || world.isClientSide() || player.isSpectator())
			return InteractionResult.PASS;

		BlockPos pos = hit.getBlockPos();
		BlockState state = world.getBlockState(pos);
		Selection selection = getSelection(stack, world);
		List<Selection.Entry> entries = new ArrayList<>(selection == null ? List.of() : selection.points());

		int index = indexOf(entries, pos);
		ArmInteractionPoint selected = ArmInteractionPoint.create(world, pos, state);
		if (selected == null) {
			// the block stopped being a valid target; forget it and let the item place normally
			if (index >= 0) {
				entries.remove(index);
				setSelection(stack, world, entries);
			}
			return InteractionResult.PASS;
		}

		if (index >= 0)
			selected.mode = entries.get(index)
				.mode();

		if (index < 0) {
			selected.cycleMode();
			entries.add(new Selection.Entry(pos, selected.getMode()));
		} else if (entries.get(index)
			.mode() == Mode.DEPOSIT) {
			// output, the last step of the cycle: deselect
			entries.remove(index);
			setSelection(stack, world, entries);
			sendSummary(player, entries);
			return InteractionResult.SUCCESS;
		} else {
			selected.cycleMode();
			entries.set(index, new Selection.Entry(pos, selected.getMode()));
		}

		setSelection(stack, world, entries);
		Mode mode = selected.getMode();
		Lang.builder("create")
			.translate(mode.getTranslationKey(), state.getBlock()
				.getName()
				.withStyle(ChatFormatting.WHITE))
			.color(mode.getColor())
			.sendStatus(player);
		return InteractionResult.SUCCESS;
	}

	public static InteractionResult leftClickingBlocksDeselectsThem(Player player, Level world, InteractionHand hand,
		BlockPos pos, Direction direction) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof ArmItem) || world.isClientSide() || player.isSpectator())
			return InteractionResult.PASS;
		Selection selection = getSelection(stack, world);
		if (selection == null)
			return InteractionResult.PASS;
		List<Selection.Entry> entries = new ArrayList<>(selection.points());
		int index = indexOf(entries, pos);
		if (index < 0)
			return InteractionResult.PASS;
		entries.remove(index);
		setSelection(stack, world, entries);
		sendSummary(player, entries);
		return InteractionResult.SUCCESS;
	}

	private static void sendSummary(Player player, List<Selection.Entry> entries) {
		int inputs = 0;
		int outputs = 0;
		for (Selection.Entry entry : entries) {
			if (entry.mode() == Mode.DEPOSIT)
				outputs++;
			else
				inputs++;
		}
		Lang.builder("create")
			.translate("mechanical_arm.summary", inputs, outputs)
			.style(ChatFormatting.WHITE)
			.sendStatus(player);
	}

	/**
	 * Create's flushSettings plus ArmPlacementPacket.handle: drops the points that are out of range of the new arm
	 * at {@code pos}, tells the player, and gives the rest to the arm.
	 */
	public static void flushSettings(Player player, Level world, BlockPos pos, ItemStack stack) {
		Selection selection = getSelection(stack, world);
		if (selection == null)
			return;
		if (!(world.getBlockEntity(pos) instanceof ArmBlockEntity arm))
			return;

		List<ArmInteractionPoint> points = new ArrayList<>();
		int removed = 0;
		for (Selection.Entry entry : selection.points()) {
			ArmInteractionPoint point = ArmInteractionPoint.create(world, entry.pos(), world.getBlockState(entry.pos()));
			if (point == null)
				continue;
			if (!point.getPos()
				.closerThan(pos, ArmBlockEntity.getRange())) {
				removed++;
				continue;
			}
			point.mode = entry.mode();
			points.add(point);
		}

		if (removed > 0) {
			Lang.builder("create")
				.translate("mechanical_arm.points_outside_range", removed)
				.style(ChatFormatting.RED)
				.sendStatus(player);
		} else {
			int inputs = 0;
			int outputs = 0;
			for (ArmInteractionPoint armInteractionPoint : points) {
				if (armInteractionPoint.getMode() == Mode.DEPOSIT)
					outputs++;
				else
					inputs++;
			}
			if (inputs + outputs > 0)
				Lang.builder("create")
					.translate("mechanical_arm.summary", inputs, outputs)
					.style(ChatFormatting.WHITE)
					.sendStatus(player);
		}

		arm.setInteractionPoints(points.stream()
			.map(point -> point.serialize(pos))
			.toList());
	}

	/** Outlines the selection of an arm item in the main hand, and the targets of the arm a wrench points at. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % OUTLINE_INTERVAL != 0)
			return;
		for (ServerPlayer player : server.getPlayerList()
			.getPlayers()) {
			ItemStack held = player.getMainHandItem();
			if (held.getItem() instanceof ArmItem) {
				Selection selection = getSelection(held, player.level());
				if (selection == null)
					continue;
				List<ArmInteractionPoint> points = new ArrayList<>();
				for (Selection.Entry entry : selection.points()) {
					ArmInteractionPoint point = ArmInteractionPoint.create(player.level(), entry.pos(),
						player.level().getBlockState(entry.pos()));
					if (point == null)
						continue;
					point.mode = entry.mode();
					points.add(point);
				}
				drawOutlines(player, points);
			} else if (held.is(ModItems.WRENCH)) {
				HitResult hit = player.pick(player.blockInteractionRange(), 0, false);
				if (!(hit instanceof BlockHitResult result) || hit.getType() != HitResult.Type.BLOCK)
					continue;
				BlockEntity be = player.level()
					.getBlockEntity(result.getBlockPos());
				if (!(be instanceof ArmBlockEntity arm))
					continue;
				drawOutlines(player, arm.inputs);
				drawOutlines(player, arm.outputs);
			}
		}
	}

	private static void drawOutlines(ServerPlayer player, Collection<ArmInteractionPoint> selection) {
		if (!(player.level() instanceof ServerLevel level))
			return;
		for (ArmInteractionPoint point : selection) {
			BlockPos pos = point.getPos();
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getShape(level, pos);
			if (shape.isEmpty())
				continue;
			AABB box = shape.bounds()
				.move(pos);
			DustParticleOptions particle = new DustParticleOptions(point.getMode()
				.getColor(), .6f);
			for (int corner = 0; corner < 8; corner++) {
				double x = (corner & 1) == 0 ? box.minX : box.maxX;
				double y = (corner & 2) == 0 ? box.minY : box.maxY;
				double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
				level.sendParticles(player, particle, false, true, x, y, z, 1, 0, 0, 0, 0);
			}
			// the middle of every edge, so the box reads as a frame
			for (int a = 0; a < 2; a++) {
				for (int b = 0; b < 2; b++) {
					double x = a == 0 ? box.minX : box.maxX;
					double y = b == 0 ? box.minY : box.maxY;
					level.sendParticles(player, particle, false, true, x, y, (box.minZ + box.maxZ) / 2, 1, 0, 0, 0, 0);
					level.sendParticles(player, particle, false, true, x, (box.minY + box.maxY) / 2,
						b == 0 ? box.minZ : box.maxZ, 1, 0, 0, 0, 0);
					level.sendParticles(player, particle, false, true, (box.minX + box.maxX) / 2,
						a == 0 ? box.minY : box.maxY, b == 0 ? box.minZ : box.maxZ, 1, 0, 0, 0, 0);
				}
			}
		}
	}

}

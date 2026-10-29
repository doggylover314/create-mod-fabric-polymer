package dev.createpolymer.trains.track;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.TrainConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What a player can do to a curve. Create's client works out which piece of a curve is under the cursor and sends
 * packets for it; here the invisible interaction boxes along the curve (see {@link TrackModel}) report the clicks.
 */
public class CurvedTrackInteraction {

	/** Create's BezierPointSelection: a piece of a curve, with where it is and which way it points. */
	public record BezierPointSelection(TrackBlockEntity blockEntity, BezierTrackPointLocation loc, Vec3 vec,
		Vec3 direction) {
	}

	/** How far a player has broken a curve by attacking it, per player. */
	private static final Map<UUID, BreakProgress> BREAKING = new HashMap<>();

	private static class BreakProgress {
		BlockPos pos;
		BlockPos target;
		float progress;
		long lastHit;
	}

	/**
	 * The piece of the curve closest to a point on it. Piece {@code i} is the one between the curve's positions at
	 * steps {@code i} and {@code i + 2}, like Create's outline picks it.
	 */
	@Nullable
	public static BezierPointSelection select(TrackBlockEntity be, BezierConnection bc, Vec3 near) {
		float[] stepLUT = bc.getStepLUT();
		int segments = (int) (bc.getLength() * 2);
		BezierPointSelection best = null;
		double bestDistance = Double.MAX_VALUE;

		for (int i = 0; i < stepLUT.length - 2; i++) {
			float t = stepLUT[i] * i / segments;
			float t2 = stepLUT[i + 2] * (i + 2) / segments;

			Vec3 v1 = bc.getPosition(t);
			Vec3 v2 = bc.getPosition(t2);
			Vec3 diff = v2.subtract(v1);
			Vec3 anchor = v1.add(diff.scale(.5));
			double distance = anchor.distanceToSqr(near);
			if (distance >= bestDistance)
				continue;
			bestDistance = distance;
			best = new BezierPointSelection(be, new BezierTrackPointLocation(bc.getKey(), i), anchor,
				diff.normalize());
		}
		return best;
	}

	/** The player right-clicked the curve that starts at {@code pos} and ends at {@code curveTarget}. */
	public static void onInteract(ServerPlayer player, InteractionHand hand, ServerLevel level, BlockPos pos,
		BlockPos curveTarget, Vec3 hit) {
		if (!(level.getBlockEntity(pos) instanceof TrackBlockEntity be))
			return;
		BezierConnection bc = be.getConnections()
			.get(curveTarget);
		if (bc == null || !bc.isPrimary())
			return;
		if (!player.mayBuild())
			return;

		ItemStack heldItem = player.getItemInHand(hand);
		Item item = heldItem.getItem();
		if (item instanceof TrackBlockItem) {
			player.sendOverlayMessage(TrackNbt.translateDirect("track.turn_start")
				.withStyle(ChatFormatting.RED));
			player.swing(hand, SwingAnimation.DEFAULT, true);
			return;
		}
		if (item instanceof TrackTargetingBlockItem ttbi) {
			BezierPointSelection selection = select(be, bc, hit);
			if (selection == null)
				return;
			boolean front = player.getLookAngle()
				.dot(selection.direction()) < 0;
			ttbi.useOnCurve(player, be, selection.loc(), front, heldItem);
			player.swing(hand, SwingAnimation.DEFAULT, true);
			return;
		}
		if (heldItem.is(ModItems.WRENCH) && player.isShiftKeyDown()) {
			destroyCurve(player, be, curveTarget, BlockPos.containing(hit), true);
			BREAKING.remove(player.getUUID());
			player.swing(hand, SwingAnimation.DEFAULT, true);
		}
	}

	/** The player attacked the curve: breaking it takes as long as the track would, times eight. */
	public static void onAttack(ServerPlayer player, ServerLevel level, BlockPos pos, BlockPos curveTarget) {
		if (!(level.getBlockEntity(pos) instanceof TrackBlockEntity be))
			return;
		if (!be.getConnections()
			.containsKey(curveTarget))
			return;
		if (!player.mayBuild())
			return;

		BlockState blockState = level.getBlockState(pos);
		BezierConnection bc = be.getConnections()
			.get(curveTarget);
		Vec3 soundAt = bc.getPosition(.5);
		BlockPos soundSource = BlockPos.containing(soundAt);

		if (player.isCreative()) {
			destroyCurve(player, be, curveTarget, soundSource, false);
			return;
		}

		long now = level.getGameTime();
		BreakProgress state = BREAKING.computeIfAbsent(player.getUUID(), id -> new BreakProgress());
		if (!pos.equals(state.pos) || !curveTarget.equals(state.target) || now - state.lastHit > 20) {
			state.pos = pos;
			state.target = curveTarget;
			state.progress = 0;
			state.lastHit = now - 4;
		}

		SoundType soundtype = blockState.getSoundType();
		level.playSound(null, soundSource, soundtype.getHitSound(), SoundSource.BLOCKS,
			(soundtype.getVolume() + 1.0F) / 8.0F, soundtype.getPitch() * 0.5F);

		long ticks = Math.min(10, Math.max(1, now - state.lastHit));
		state.lastHit = now;
		state.progress += blockState.getDestroyProgress(player, level, pos) * ticks / 8f;

		if (state.progress >= 1) {
			BREAKING.remove(player.getUUID());
			destroyCurve(player, be, curveTarget, soundSource, false);
		}
	}

	/** Create's CurvedTrackDestroyPacket. */
	public static void destroyCurve(ServerPlayer player, TrackBlockEntity be, BlockPos targetPos, BlockPos soundSource,
		boolean wrench) {
		int verifyDistance = TrainConfig.maxTrackPlacementLength * 4;
		BlockPos pos = be.getBlockPos();
		if (!player.isWithinBlockInteractionRange(pos, verifyDistance)) {
			dev.createpolymer.CreatePolymer.LOGGER.warn("{} too far away from destroyed Curve track",
				player.getScoreboardName());
			return;
		}

		Level level = be.getLevel();
		BezierConnection bezierConnection = be.getConnections()
			.get(targetPos);
		if (bezierConnection == null)
			return;

		be.removeConnection(targetPos);
		if (level.getBlockEntity(targetPos) instanceof TrackBlockEntity other)
			other.removeConnection(pos);

		BlockState blockState = be.getBlockState();
		TrackPropagator.onRailRemoved(level, pos, blockState);

		if (wrench) {
			ModSounds.wrenchRemove(level, soundSource);
			if (!player.isCreative())
				bezierConnection.addItemsToPlayer(player);
		} else if (!player.isCreative())
			bezierConnection.spawnItems(level);

		bezierConnection.spawnDestroyParticles(level);
		SoundType soundtype = blockState.getSoundType();
		level.playSound(null, soundSource, soundtype.getBreakSound(), SoundSource.BLOCKS,
			(soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
	}

}

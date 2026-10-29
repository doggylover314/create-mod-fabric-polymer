package dev.createpolymer.trains.track;

import java.util.List;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.mutable.MutableObject;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.TrainConfig;
import dev.createpolymer.trains.TrainSounds;
import dev.createpolymer.trains.graph.EdgeData;
import dev.createpolymer.trains.graph.EdgePointType;
import dev.createpolymer.trains.graph.TrackEdge;
import dev.createpolymer.trains.graph.TrackGraphHelper;
import dev.createpolymer.trains.graph.TrackGraphLocation;
import dev.createpolymer.trains.graph.TrackNode;
import dev.createpolymer.trains.signal.TrackEdgePoint;
import dev.createpolymer.util.Couple;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Create's TrackTargetingBlockItem: the item of the blocks that sit next to a track and act on it (stations, signals,
 * observers). Using it on a track remembers the spot; using it on a block places the block with that spot as target.
 */
public class TrackTargetingBlockItem extends PolymerBlockItem {

	private EdgePointType<?> type;

	public static java.util.function.BiFunction<Block, Item.Properties, TrackTargetingBlockItem> ofType(
		EdgePointType<?> type) {
		return (b, p) -> new TrackTargetingBlockItem(b, p, type);
	}

	public TrackTargetingBlockItem(Block pBlock, Properties pProperties, EdgePointType<?> type) {
		super(pBlock, pProperties, Items.TRIAL_KEY, true);
		this.type = type;
	}

	@Override
	public InteractionResult useOn(UseOnContext pContext) {
		ItemStack stack = pContext.getItemInHand();
		BlockPos pos = pContext.getClickedPos();
		Level level = pContext.getLevel();
		BlockState state = level.getBlockState(pos);
		Player player = pContext.getPlayer();

		if (player == null)
			return InteractionResult.FAIL;

		if (player.isShiftKeyDown() && stack.has(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_POS)) {
			if (level.isClientSide())
				return InteractionResult.SUCCESS;
			player.sendOverlayMessage(TrackNbt.translateDirect("track_target.clear"));
			clearSelection(stack);
			TrainSounds.controllerClick(level, null, pos, 1, .5f);
			return InteractionResult.SUCCESS;
		}

		if (state.getBlock() instanceof ITrackBlock track) {
			if (level.isClientSide())
				return InteractionResult.SUCCESS;

			Vec3 lookAngle = player.getLookAngle();
			boolean front = track.getNearestTrackAxis(level, pos, state, lookAngle)
				.getSecond() == AxisDirection.POSITIVE;
			EdgePointType<?> type = getType(stack);

			MutableObject<OverlapResult> result = new MutableObject<>(null);
			withGraphLocation(level, pos, front, null, type, (overlap, location) -> result.setValue(overlap));

			if (result.getValue().feedback != null) {
				player.sendOverlayMessage(TrackNbt.translateDirect(result.getValue().feedback)
					.withStyle(ChatFormatting.RED));
				TrainSounds.deny(level, null, pos, .5f, 1);
				return InteractionResult.FAIL;
			}

			stack.set(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_POS, pos);
			stack.set(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_DIRECTION, front);
			stack.remove(TrackRegistry.TRACK_TARGETING_ITEM_BEZIER);
			player.sendOverlayMessage(TrackNbt.translateDirect("track_target.set"));
			TrainSounds.controllerClick(level, null, pos, 1, 1);
			return InteractionResult.SUCCESS;
		}

		if (!stack.has(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_POS)) {
			player.sendOverlayMessage(TrackNbt.translateDirect("track_target.missing")
				.withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}

		CompoundTag blockEntityData = new CompoundTag();
		blockEntityData.putBoolean("TargetDirection",
			stack.getOrDefault(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_DIRECTION, false));

		BlockPos selectedPos = stack.get(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_POS);
		BlockPos placedPos = pos.relative(pContext.getClickedFace(), state.canBeReplaced() ? 0 : 1);

		boolean bezier = stack.has(TrackRegistry.TRACK_TARGETING_ITEM_BEZIER);

		if (!selectedPos.closerThan(placedPos, bezier ? TrainConfig.maxTrackPlacementLength + 16 : 16)) {
			player.sendOverlayMessage(TrackNbt.translateDirect("track_target.too_far")
				.withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}

		if (bezier) {
			BezierTrackPointLocation bezierTrackPointLocation =
				stack.get(TrackRegistry.TRACK_TARGETING_ITEM_BEZIER);
			CompoundTag bezierNbt = new CompoundTag();
			bezierNbt.putInt("Segment", bezierTrackPointLocation.segment());
			TrackNbt.putBlockPos(bezierNbt, "Key", bezierTrackPointLocation.curveTarget()
				.subtract(placedPos));
			blockEntityData.put("Bezier", bezierNbt);
		}

		TrackNbt.putBlockPos(blockEntityData, "TargetTrack", selectedPos.subtract(placedPos));

		stack.set(DataComponents.BLOCK_ENTITY_DATA,
			TypedEntityData.of(((IBE<?>) this.getBlock()).getBlockEntityType(), blockEntityData));
		clearSelection(stack);

		InteractionResult useOn = super.useOn(pContext);
		stack.remove(DataComponents.BLOCK_ENTITY_DATA);

		if (level.isClientSide() || useOn == InteractionResult.FAIL)
			return useOn;

		ItemStack itemInHand = player.getItemInHand(pContext.getHand());
		if (!itemInHand.isEmpty())
			clearSelection(itemInHand);
		player.sendOverlayMessage(TrackNbt.translateDirect("track_target.success")
			.withStyle(ChatFormatting.GREEN));

		return useOn;
	}

	public static void clearSelection(ItemStack stack) {
		stack.remove(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_POS);
		stack.remove(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_DIRECTION);
		stack.remove(TrackRegistry.TRACK_TARGETING_ITEM_BEZIER);
	}

	public EdgePointType<?> getType(ItemStack stack) {
		return type;
	}

	/**
	 * Create's useOnCurve (and CurvedTrackSelectionPacket): the player used the item on a piece of a curve.
	 */
	public boolean useOnCurve(Player player, TrackBlockEntity be, BezierTrackPointLocation loc, boolean front,
		ItemStack stack) {
		BlockPos pos = be.getBlockPos();
		if (player.isShiftKeyDown() && stack.has(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_POS)) {
			player.sendOverlayMessage(TrackNbt.translateDirect("track_target.clear"));
			clearSelection(stack);
			TrainSounds.controllerClick(player.level(), null, pos, 1, .5f);
			return true;
		}

		EdgePointType<?> type = getType(stack);
		MutableObject<OverlapResult> result = new MutableObject<>(null);
		withGraphLocation(player.level(), pos, front, loc, type, (overlap, location) -> result.setValue(overlap));

		if (result.getValue() == null)
			return true;
		if (result.getValue().feedback != null) {
			player.sendOverlayMessage(TrackNbt.translateDirect(result.getValue().feedback)
				.withStyle(ChatFormatting.RED));
			TrainSounds.deny(player.level(), null, pos, .5f, 1);
			return true;
		}

		stack.set(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_POS, pos);
		stack.set(TrackRegistry.TRACK_TARGETING_ITEM_SELECTED_DIRECTION, front);
		stack.set(TrackRegistry.TRACK_TARGETING_ITEM_BEZIER, loc);

		player.sendOverlayMessage(TrackNbt.translateDirect("track_target.set"));
		TrainSounds.controllerClick(player.level(), null, pos, 1, 1);
		return true;
	}

	public static enum OverlapResult {

		VALID,
		OCCUPIED("track_target.occupied"),
		JUNCTION("track_target.no_junctions"),
		NO_TRACK("track_target.invalid");

		public String feedback;

		private OverlapResult() {}

		private OverlapResult(String feedback) {
			this.feedback = feedback;
		}

	}

	public static void withGraphLocation(Level level, BlockPos pos, boolean front,
		BezierTrackPointLocation targetBezier, EdgePointType<?> type,
		BiConsumer<OverlapResult, TrackGraphLocation> callback) {

		BlockState state = level.getBlockState(pos);

		if (!(state.getBlock() instanceof ITrackBlock track)) {
			callback.accept(OverlapResult.NO_TRACK, null);
			return;
		}

		List<Vec3> trackAxes = track.getTrackAxes(level, pos, state);
		if (targetBezier == null && trackAxes.size() > 1) {
			callback.accept(OverlapResult.JUNCTION, null);
			return;
		}

		AxisDirection targetDirection = front ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;
		TrackGraphLocation location =
			targetBezier != null ? TrackGraphHelper.getBezierGraphLocationAt(level, pos, targetDirection, targetBezier)
				: TrackGraphHelper.getGraphLocationAt(level, pos, targetDirection, trackAxes.get(0));

		if (location == null) {
			callback.accept(OverlapResult.NO_TRACK, null);
			return;
		}

		Couple<TrackNode> nodes = location.edge.map(location.graph::locateNode);
		TrackEdge edge = location.graph.getConnection(nodes);
		if (edge == null)
			return;

		EdgeData edgeData = edge.getEdgeData();
		double edgePosition = location.position;

		for (TrackEdgePoint edgePoint : edgeData.getPoints()) {
			double otherEdgePosition = edgePoint.getLocationOn(edge);
			double distance = Math.abs(edgePosition - otherEdgePosition);
			if (distance > .75)
				continue;
			if (edgePoint.canCoexistWith(type, front) && distance < .25)
				continue;

			callback.accept(OverlapResult.OCCUPIED, location);
			return;
		}

		callback.accept(OverlapResult.VALID, location);
	}

}

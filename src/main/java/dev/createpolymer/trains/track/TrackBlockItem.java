package dev.createpolymer.trains.track;

import dev.createpolymer.registry.feature.TrackRegistry;
import dev.createpolymer.trains.TrackNbt;
import dev.createpolymer.trains.TrainSounds;
import dev.createpolymer.trains.track.TrackPlacement.PlacementInfo;
import dev.createpolymer.util.Pair;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Create's TrackBlockItem. Using it on a track selects the start of a curve (or a long stretch of straight track);
 * using it on a second track, or on the ground, places the connection. Create's client previews and the sprint key
 * of its client are gone: sprinting while placing makes the largest possible curve.
 */
public class TrackBlockItem extends PolymerBlockItem {

	public TrackBlockItem(Block pBlock, Properties pProperties) {
		super(pBlock, pProperties, Items.TRIAL_KEY, true);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (player.isShiftKeyDown() && isFoil(stack)) {
			return clearSelection(stack, level, player);
		} else {
			return super.use(level, player, usedHand);
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext pContext) {
		ItemStack stack = pContext.getItemInHand();
		BlockPos pos = pContext.getClickedPos();
		Level level = pContext.getLevel();
		BlockState state = level.getBlockState(pos);
		Player player = pContext.getPlayer();

		if (player == null)
			return super.useOn(pContext);
		if (pContext.getHand() == InteractionHand.OFF_HAND)
			return super.useOn(pContext);

		Vec3 lookAngle = player.getLookAngle();

		if (!isFoil(stack)) {
			if (state.getBlock() instanceof TrackBlock track && track.getTrackAxes(level, pos, state)
				.size() > 1) {
				if (!level.isClientSide())
					player.sendOverlayMessage(TrackNbt.translateDirect("track.junction_start")
						.withStyle(ChatFormatting.RED));
				return InteractionResult.SUCCESS;
			}

			if (level.getBlockEntity(pos) instanceof TrackBlockEntity tbe && tbe.isTilted()) {
				if (!level.isClientSide())
					player.sendOverlayMessage(TrackNbt.translateDirect("track.turn_start")
						.withStyle(ChatFormatting.RED));
				return InteractionResult.SUCCESS;
			}

			if (select(level, pos, lookAngle, stack)) {
				level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 1);
				return InteractionResult.SUCCESS;
			}
			return super.useOn(pContext);
		} else if (player.isShiftKeyDown()) {
			return clearSelection(stack, level, player);
		}

		boolean placing = !(state.getBlock() instanceof ITrackBlock);
		boolean extend = stack.getOrDefault(TrackRegistry.TRACK_EXTENDED_CURVE, false) || player.isSprinting();
		stack.remove(TrackRegistry.TRACK_EXTENDED_CURVE);

		if (placing) {
			if (!state.canBeReplaced())
				pos = pos.relative(pContext.getClickedFace());
			state = getPlacementState(pContext);
			if (state == null)
				return InteractionResult.FAIL;
		}

		// Create's girder in the off hand makes the curve girdered; the girders are not part of this port.
		boolean hasGirder = false;
		PlacementInfo info = TrackPlacement.tryConnect(level, player, pos, state, stack, hasGirder, extend);

		if (info.message != null && !level.isClientSide())
			player.sendOverlayMessage(TrackNbt.translateDirect(info.message));
		if (!info.valid) {
			TrainSounds.deny(level, null, player.blockPosition(), 1, 1);
			return InteractionResult.FAIL;
		}

		if (level.isClientSide())
			return InteractionResult.SUCCESS;

		stack = player.getMainHandItem();
		if (stack.getItem() instanceof TrackBlockItem) {
			stack.remove(TrackRegistry.TRACK_CONNECTING_FROM);
			stack.remove(TrackRegistry.TRACK_EXTENDED_CURVE);
			player.setItemInHand(pContext.getHand(), stack);
		}

		SoundType soundtype = state.getSoundType();
		if (soundtype != null)
			level.playSound(null, pos, soundtype.getPlaceSound(), SoundSource.BLOCKS,
				(soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);

		return InteractionResult.SUCCESS;
	}

	public static InteractionResult clearSelection(ItemStack stack, Level level, Player player) {
		if (level.isClientSide()) {
			level.playSound(player, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS,
				0.75f, 1.0f);
		} else {
			player.sendOverlayMessage(TrackNbt.translateDirect("track.selection_cleared"));
			stack.remove(TrackRegistry.TRACK_CONNECTING_FROM);
		}
		return InteractionResult.SUCCESS;
	}

	public BlockState getPlacementState(UseOnContext pContext) {
		return getPlacementState(updatePlacementContext(new BlockPlaceContext(pContext)));
	}

	public static boolean select(LevelAccessor world, BlockPos pos, Vec3 lookVec, ItemStack heldItem) {
		BlockState blockState = world.getBlockState(pos);
		Block block = blockState.getBlock();
		if (!(block instanceof ITrackBlock track))
			return false;

		Pair<Vec3, AxisDirection> nearestTrackAxis = track.getNearestTrackAxis(world, pos, blockState, lookVec);
		Vec3 axis = nearestTrackAxis.getFirst()
			.scale(nearestTrackAxis.getSecond() == AxisDirection.POSITIVE ? -1 : 1);
		Vec3 end = track.getCurveStart(world, pos, blockState, axis);
		Vec3 normal = track.getUpNormal(world, pos, blockState)
			.normalize();

		heldItem.set(TrackRegistry.TRACK_CONNECTING_FROM, new TrackPlacement.ConnectingFrom(pos, axis, normal, end));
		return true;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return stack.has(TrackRegistry.TRACK_CONNECTING_FROM);
	}

}

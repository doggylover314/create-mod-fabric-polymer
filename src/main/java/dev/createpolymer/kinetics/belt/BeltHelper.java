package dev.createpolymer.kinetics.belt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.fluid.FluidItemHandlers;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BeltHelper {

	public static final TagKey<Item> UPRIGHT_ON_BELT = TagKey.create(Registries.ITEM, CreatePolymer.id("upright_on_belt"));
	public static final TagKey<Item> NOT_UPRIGHT_ON_BELT = TagKey.create(Registries.ITEM, CreatePolymer.id("not_upright_on_belt"));

	public static Map<Item, Boolean> uprightCache = new ConcurrentHashMap<>();

	/** Cleared when data packs reload, because the answer depends on item tags. */
	public static void clearUprightCache() {
		uprightCache.clear();
	}

	public static boolean isItemUpright(ItemStack stack) {
		return uprightCache.computeIfAbsent(
			stack.getItem(),
			item -> {
				boolean isFluidHandler = FluidItemHandlers.get(stack).isPresent();
				boolean useUpright = stack.is(UPRIGHT_ON_BELT);
				boolean forceDisableUpright = !stack.is(NOT_UPRIGHT_ON_BELT);

				return (isFluidHandler || useUpright) && forceDisableUpright;
			}
		);
	}

	@Nullable
	public static BeltBlockEntity getSegmentBE(LevelAccessor world, BlockPos pos) {
		if (world instanceof Level l && !l.isLoaded(pos))
			return null;
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (!(blockEntity instanceof BeltBlockEntity))
			return null;
		return (BeltBlockEntity) blockEntity;
	}

	@Nullable
	public static BeltBlockEntity getControllerBE(LevelAccessor world, BlockPos pos) {
		BeltBlockEntity segment = getSegmentBE(world, pos);
		if (segment == null)
			return null;
		BlockPos controllerPos = segment.controller;
		if (controllerPos == null)
			return null;
		return getSegmentBE(world, controllerPos);
	}

	@Nullable
	public static BeltBlockEntity getBeltForOffset(BeltBlockEntity controller, float offset) {
		return getBeltAtSegment(controller, (int) Math.floor(offset));
	}

	@Nullable
	public static BeltBlockEntity getBeltAtSegment(BeltBlockEntity controller, int segment) {
		BlockPos pos = getPositionForOffset(controller, segment);
		BlockEntity be = controller.getLevel()
			.getBlockEntity(pos);
		if (be == null || !(be instanceof BeltBlockEntity))
			return null;
		return (BeltBlockEntity) be;
	}

	public static BlockPos getPositionForOffset(BeltBlockEntity controller, int offset) {
		BlockPos pos = controller.getBlockPos();
		Vec3i vec = controller.getBeltFacing()
			.getUnitVec3i();
		BeltSlope slope = controller.getBlockState()
			.getValue(BeltBlock.SLOPE);
		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;

		return pos.offset(offset * vec.getX(), Mth.clamp(offset, 0, controller.beltLength - 1) * verticality,
			offset * vec.getZ());
	}

	public static Vec3 getVectorForOffset(BeltBlockEntity controller, float offset) {
		BeltSlope slope = controller.getBlockState()
			.getValue(BeltBlock.SLOPE);
		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;
		float verticalMovement = verticality;
		if (offset < .5)
			verticalMovement = 0;
		verticalMovement = verticalMovement * (Math.min(offset, controller.beltLength - .5f) - .5f);
		Vec3 vec = VecHelper.getCenterOf(controller.getBlockPos());
		Vec3 horizontalMovement = Vec3.atLowerCornerOf(controller.getBeltFacing()
			.getUnitVec3i())
			.scale(offset - .5f);

		if (slope == BeltSlope.VERTICAL)
			horizontalMovement = Vec3.ZERO;

		vec = vec.add(horizontalMovement)
			.add(0, verticalMovement, 0);
		return vec;
	}

	public static Vec3 getBeltVector(BlockState state) {
		BeltSlope slope = state.getValue(BeltBlock.SLOPE);
		int verticality = slope == BeltSlope.DOWNWARD ? -1 : slope == BeltSlope.UPWARD ? 1 : 0;
		Vec3 horizontalMovement = Vec3.atLowerCornerOf(state.getValue(BeltBlock.HORIZONTAL_FACING)
			.getUnitVec3i());
		if (slope == BeltSlope.VERTICAL)
			return new Vec3(0, state.getValue(BeltBlock.HORIZONTAL_FACING)
				.getAxisDirection()
				.getStep(), 0);
		return new Vec3(0, verticality, 0).add(horizontalMovement);
	}

	/** Create's BlockHelper.hasBlockSolidSide. */
	public static boolean hasBlockSolidSide(BlockState state, BlockGetter blockGetter, BlockPos pos, Direction dir) {
		return !state.is(BlockTags.LEAVES)
			&& Block.isFaceFull(state.getCollisionShape(blockGetter, pos), dir);
	}

}

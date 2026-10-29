package dev.createpolymer.impl.contraption;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.api.connectivity.ConnectivityHandler;
import dev.createpolymer.api.contraption.BlockMovementChecks;
import dev.createpolymer.api.contraption.BlockMovementChecks.AttachedCheck;
import dev.createpolymer.api.contraption.BlockMovementChecks.BrittleCheck;
import dev.createpolymer.api.contraption.BlockMovementChecks.CheckResult;
import dev.createpolymer.api.contraption.BlockMovementChecks.MovementAllowedCheck;
import dev.createpolymer.api.contraption.BlockMovementChecks.MovementNecessaryCheck;
import dev.createpolymer.api.contraption.BlockMovementChecks.NotSupportiveCheck;
import dev.createpolymer.api.contraption.ContraptionMovementSetting;
import dev.createpolymer.kinetics.crank.HandCrankBlock;
import dev.createpolymer.kinetics.drill.DrillBlock;
import dev.createpolymer.kinetics.fan.NozzleBlock;
import dev.createpolymer.kinetics.saw.SawBlock;
import dev.createpolymer.logistics.vault.ItemVaultBlock;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock;
import dev.createpolymer.contraptions.piston.MechanicalPistonBlock.PistonState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;

/**
 * Create's BlockMovementChecksImpl. Branches for Create blocks that are not ported yet (chassis, bearings, pulleys,
 * sails, belts, stickers, bogeys, ...) are left out: register them from the block's own feature with
 * {@link BlockMovementChecks#registerBrittleCheck} and friends, or add them to the fallbacks below next to the
 * vanilla ones.
 */
public class BlockMovementChecksImpl {
	private static final List<MovementNecessaryCheck> MOVEMENT_NECESSARY_CHECKS = new ArrayList<>();
	private static final List<MovementAllowedCheck> MOVEMENT_ALLOWED_CHECKS = new ArrayList<>();
	private static final List<BrittleCheck> BRITTLE_CHECKS = new ArrayList<>();
	private static final List<AttachedCheck> ATTACHED_CHECKS = new ArrayList<>();
	private static final List<NotSupportiveCheck> NOT_SUPPORTIVE_CHECKS = new ArrayList<>();

	// registration adds to the start so newer ones are queried first

	public static synchronized void registerMovementNecessaryCheck(MovementNecessaryCheck check) {
		MOVEMENT_NECESSARY_CHECKS.add(0, check);
	}

	public static synchronized void registerMovementAllowedCheck(MovementAllowedCheck check) {
		MOVEMENT_ALLOWED_CHECKS.add(0, check);
	}

	public static synchronized void registerBrittleCheck(BrittleCheck check) {
		BRITTLE_CHECKS.add(0, check);
	}

	public static synchronized void registerAttachedCheck(AttachedCheck check) {
		ATTACHED_CHECKS.add(0, check);
	}

	public static synchronized void registerNotSupportiveCheck(NotSupportiveCheck check) {
		NOT_SUPPORTIVE_CHECKS.add(0, check);
	}

	// queries

	public static boolean isMovementNecessary(BlockState state, Level world, BlockPos pos) {
		for (MovementNecessaryCheck check : MOVEMENT_NECESSARY_CHECKS) {
			CheckResult result = check.isMovementNecessary(state, world, pos);
			if (result != CheckResult.PASS) {
				return result.toBoolean();
			}
		}
		return BlockMovementChecksImpl.isMovementNecessaryFallback(state, world, pos);
	}

	public static boolean isMovementAllowed(BlockState state, Level world, BlockPos pos) {
		for (MovementAllowedCheck check : MOVEMENT_ALLOWED_CHECKS) {
			CheckResult result = check.isMovementAllowed(state, world, pos);
			if (result != CheckResult.PASS) {
				return result.toBoolean();
			}
		}
		return BlockMovementChecksImpl.isMovementAllowedFallback(state, world, pos);
	}

	public static boolean isBrittle(BlockState state) {
		for (BrittleCheck check : BRITTLE_CHECKS) {
			CheckResult result = check.isBrittle(state);
			if (result != CheckResult.PASS) {
				return result.toBoolean();
			}
		}
		return BlockMovementChecksImpl.isBrittleFallback(state);
	}

	public static boolean isBlockAttachedTowards(BlockState state, Level world, BlockPos pos, Direction direction) {
		for (AttachedCheck check : ATTACHED_CHECKS) {
			CheckResult result = check.isBlockAttachedTowards(state, world, pos, direction);
			if (result != CheckResult.PASS) {
				return result.toBoolean();
			}
		}
		return BlockMovementChecksImpl.isBlockAttachedTowardsFallback(state, world, pos, direction);
	}

	public static boolean isNotSupportive(BlockState state, Direction facing) {
		for (NotSupportiveCheck check : NOT_SUPPORTIVE_CHECKS) {
			CheckResult result = check.isNotSupportive(state, facing);
			if (result != CheckResult.PASS) {
				return result.toBoolean();
			}
		}
		return BlockMovementChecksImpl.isNotSupportiveFallback(state, facing);
	}

	// fallbacks

	private static boolean isMovementNecessaryFallback(BlockState state, Level world, BlockPos pos) {
		if (BlockMovementChecks.isBrittle(state))
			return true;
		if (ContraptionBlockTags.MOVABLE_EMPTY_COLLIDER.matches(state))
			return true;
		if (state.getCollisionShape(world, pos)
			.isEmpty())
			return false;

		return !state.canBeReplaced();
	}

	private static boolean isMovementAllowedFallback(BlockState state, Level world, BlockPos pos) {
		Block block = state.getBlock();
		if (state.getDestroySpeed(world, pos) == -1)
			return false;
		if (ContraptionBlockTags.NON_MOVABLE.matches(state))
			return false;
		if (ContraptionMovementSetting.get(state) == ContraptionMovementSetting.UNMOVABLE)
			return false;

		// Move controllers only when they aren't moving
		if (block instanceof MechanicalPistonBlock && state.getValue(MechanicalPistonBlock.STATE) != PistonState.MOVING)
			return true;

		if (state.getBlock() instanceof GrindstoneBlock)
			return true;
		return state.getPistonPushReaction() != PushReaction.IMMOVEABLE;
	}

	private static boolean isBrittleFallback(BlockState state) {
		Block block = state.getBlock();
		if (state.hasProperty(BlockStateProperties.HANGING))
			return true;

		if (block instanceof LadderBlock)
			return true;
		if (block instanceof BaseTorchBlock)
			return true;
		if (block instanceof SignBlock)
			return true;
		if (block instanceof BasePressurePlateBlock)
			return true;
		if (block instanceof FaceAttachedHorizontalDirectionalBlock && !(block instanceof GrindstoneBlock))
			return true;
		if (block instanceof BaseRailBlock)
			return true;
		if (block instanceof DiodeBlock)
			return true;
		if (block instanceof RedstoneWireBlock)
			return true;
		if (block instanceof WoolCarpetBlock)
			return true;
		return ContraptionBlockTags.BRITTLE.matches(state);
	}

	private static boolean isBlockAttachedTowardsFallback(BlockState state, Level world, BlockPos pos, Direction direction) {
		Block block = state.getBlock();
		if (block instanceof LadderBlock)
			return state.getValue(LadderBlock.FACING) == direction.getOpposite();
		if (block instanceof WallTorchBlock)
			return state.getValue(WallTorchBlock.FACING) == direction.getOpposite();
		if (block instanceof WallSignBlock)
			return state.getValue(WallSignBlock.FACING) == direction.getOpposite();
		if (block instanceof StandingSignBlock)
			return direction == Direction.DOWN;
		if (block instanceof BasePressurePlateBlock)
			return direction == Direction.DOWN;
		if (block instanceof DoorBlock) {
			if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && direction == Direction.UP)
				return true;
			return direction == Direction.DOWN;
		}
		if (block instanceof BedBlock) {
			Direction facing = state.getValue(BedBlock.FACING);
			if (state.getValue(BedBlock.PART) == BedPart.HEAD)
				facing = facing.getOpposite();
			return direction == facing;
		}
		if (block instanceof FlowerPotBlock)
			return direction == Direction.DOWN;
		if (block instanceof DiodeBlock)
			return direction == Direction.DOWN;
		if (block instanceof RedstoneWireBlock)
			return direction == Direction.DOWN;
		if (block instanceof WoolCarpetBlock)
			return direction == Direction.DOWN;
		if (block instanceof RedstoneWallTorchBlock)
			return state.getValue(RedstoneWallTorchBlock.FACING) == direction.getOpposite();
		if (block instanceof BaseTorchBlock)
			return direction == Direction.DOWN;
		if (block instanceof FaceAttachedHorizontalDirectionalBlock) {
			AttachFace attachFace = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
			if (attachFace == AttachFace.CEILING)
				return direction == Direction.UP;
			if (attachFace == AttachFace.FLOOR)
				return direction == Direction.DOWN;
			if (attachFace == AttachFace.WALL)
				return direction.getOpposite() == state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING);
		}
		if (state.hasProperty(BlockStateProperties.HANGING))
			return direction == (state.getValue(BlockStateProperties.HANGING) ? Direction.UP : Direction.DOWN);
		if (block instanceof BaseRailBlock)
			return direction == Direction.DOWN;
		if (block instanceof HandCrankBlock)
			return direction == state.getValue(HandCrankBlock.FACING)
				.getOpposite();
		if (block instanceof NozzleBlock)
			return direction == state.getValue(NozzleBlock.FACING)
				.getOpposite();
		if (block instanceof BellBlock) {
			BellAttachType attachment = state.getValue(BlockStateProperties.BELL_ATTACHMENT);
			if (attachment == BellAttachType.FLOOR)
				return direction == Direction.DOWN;
			if (attachment == BellAttachType.CEILING)
				return direction == Direction.UP;
			return direction == state.getValue(HorizontalDirectionalBlock.FACING);
		}
		if (state.getBlock() instanceof ItemVaultBlock)
			return ConnectivityHandler.isConnected(world, pos, pos.relative(direction));
		return false;
	}

	private static boolean isNotSupportiveFallback(BlockState state, Direction facing) {
		Block block = state.getBlock();
		if (block instanceof DrillBlock)
			return state.getValue(BlockStateProperties.FACING) == facing;
		if (block instanceof SawBlock)
			return state.getValue(BlockStateProperties.FACING) == facing;
		if (block instanceof WoolCarpetBlock)
			return facing == Direction.UP;
		if (block instanceof dev.createpolymer.contraptions.piston.PistonExtensionPoleBlock)
			return facing.getAxis() != state.getValue(BlockStateProperties.FACING)
				.getAxis();
		if (block instanceof dev.createpolymer.contraptions.piston.MechanicalPistonHeadBlock)
			return facing.getAxis() != state.getValue(BlockStateProperties.FACING)
				.getAxis();
		return BlockMovementChecks.isBrittle(state);
	}
}

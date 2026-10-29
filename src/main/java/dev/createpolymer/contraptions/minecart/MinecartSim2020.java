package dev.createpolymer.contraptions.minecart;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;

import dev.createpolymer.contraptions.minecart.capability.MinecartController;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * Useful methods for dealing with Minecarts.
 * <p>
 * The cart physics methods follow the way 26.3's {@code OldMinecartBehavior} moves a cart along a rail (Create
 * used NeoForge hooks that vanilla does not have: {@code getPos}, {@code moveMinecartOnRail}, the
 * {@code getMaxCartSpeedOnRail} constant). Carts using the experimental {@code NewMinecartBehavior} are moved
 * freely instead of along the rail.
 */
public class MinecartSim2020 {
	/** NeoForge's AbstractMinecart#getMaxCartSpeedOnRail. */
	public static final float MAX_CART_SPEED_ON_RAIL = 1.2f;
	/** NeoForge's BaseRailBlock#getRailMaxSpeed. */
	public static final float RAIL_MAX_SPEED = 0.4f;

	private static final Map<RailShape, Pair<Vec3i, Vec3i>> MATRIX =
		Util.make(Maps.newEnumMap(RailShape.class), (map) -> {
			Vec3i west = Direction.WEST.getUnitVec3i();
			Vec3i east = Direction.EAST.getUnitVec3i();
			Vec3i north = Direction.NORTH.getUnitVec3i();
			Vec3i south = Direction.SOUTH.getUnitVec3i();
			map.put(RailShape.NORTH_SOUTH, Pair.of(north, south));
			map.put(RailShape.EAST_WEST, Pair.of(west, east));
			map.put(RailShape.ASCENDING_EAST, Pair.of(west.below(), east));
			map.put(RailShape.ASCENDING_WEST, Pair.of(west, east.below()));
			map.put(RailShape.ASCENDING_NORTH, Pair.of(north, south.below()));
			map.put(RailShape.ASCENDING_SOUTH, Pair.of(north.below(), south));
			map.put(RailShape.SOUTH_EAST, Pair.of(south, east));
			map.put(RailShape.SOUTH_WEST, Pair.of(south, west));
			map.put(RailShape.NORTH_WEST, Pair.of(north, west));
			map.put(RailShape.NORTH_EAST, Pair.of(north, east));
		});

	public static Vec3 predictNextPositionOf(AbstractMinecart cart) {
		Vec3 position = cart.position();
		Vec3 motion = VecHelper.clamp(cart.getDeltaMovement(), 1f);
		return position.add(motion);
	}

	public static boolean canAddMotion(AbstractMinecart c) {
		if (c instanceof MinecartFurnace furnace)
			return Mth.equal(furnace.push.x, 0)
				&& Mth.equal(furnace.push.z, 0);

		MinecartController controller = MinecartController.get(c);
		if (controller.isPresent())
			return !controller.isStalled();
		return true;
	}

	/** The block a cart is currently on, or null if that is not a rail (Create: getCurrentRailPosition). */
	@Nullable
	public static BlockPos getRailPosition(Level world, AbstractMinecart cart) {
		return getRailPosition(world, cart.getX(), cart.getY(), cart.getZ());
	}

	@Nullable
	public static BlockPos getRailPosition(Level world, double x, double y, double z) {
		int xt = Mth.floor(x);
		int yt = Mth.floor(y);
		int zt = Mth.floor(z);
		if (world.getBlockState(new BlockPos(xt, yt - 1, zt))
			.is(BlockTags.RAILS))
			yt--;
		BlockPos pos = new BlockPos(xt, yt, zt);
		return BaseRailBlock.isRail(world.getBlockState(pos)) ? pos : null;
	}

	@Nullable
	public static RailShape getRailShape(BlockState state) {
		if (!(state.getBlock() instanceof BaseRailBlock rail) || !BaseRailBlock.isRail(state))
			return null;
		return state.getValue(rail.getShapeProperty());
	}

	/** The fastest a cart goes along a rail in one tick, as vanilla's {@code getMaxSpeed} gives it. */
	public static double getMaxSpeed(AbstractMinecart cart) {
		if (!(cart.level() instanceof ServerLevel level))
			return 0.4;
		double speed = cart.getBehavior()
			.getMaxSpeed(level);
		if (cart instanceof MinecartFurnace)
			speed *= cart.isInWater() ? 0.75 : 0.5;
		return speed;
	}

	public static void moveCartAlongTrack(AbstractMinecart cart, Vec3 forcedMovement, BlockPos railPos,
		BlockState trackState) {

		if (forcedMovement.equals(Vec3.ZERO))
			return;

		if (!(cart.getBehavior() instanceof net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior)) {
			cart.move(MoverType.SELF, forcedMovement);
			return;
		}

		Vec3 previousMotion = cart.getDeltaMovement();
		cart.resetFallDistance();

		double x = cart.getX();
		double y = cart.getY();
		double z = cart.getZ();

		double actualX = x;
		double actualY = y;
		double actualZ = z;

		Vec3 actualVec = getPos(cart, actualX, actualY, actualZ);
		actualY = railPos.getY();

		BaseRailBlock abstractrailblock = (BaseRailBlock) trackState.getBlock();
		RailShape railshape = trackState.getValue(abstractrailblock.getShapeProperty());
		double slopeAdjustment = cart.isInWater() ? 0.0078125 * 0.2 : 0.0078125;
		switch (railshape) {
		case ASCENDING_EAST:
			forcedMovement = forcedMovement.add(-1 * slopeAdjustment, 0.0D, 0.0D);
			actualY++;
			break;
		case ASCENDING_WEST:
			forcedMovement = forcedMovement.add(slopeAdjustment, 0.0D, 0.0D);
			actualY++;
			break;
		case ASCENDING_NORTH:
			forcedMovement = forcedMovement.add(0.0D, 0.0D, slopeAdjustment);
			actualY++;
			break;
		case ASCENDING_SOUTH:
			forcedMovement = forcedMovement.add(0.0D, 0.0D, -1 * slopeAdjustment);
			actualY++;
		default:
			break;
		}

		Pair<Vec3i, Vec3i> pair = MATRIX.get(railshape);
		Vec3i Vector3i = pair.getFirst();
		Vec3i Vector3i1 = pair.getSecond();
		double d4 = (double) (Vector3i1.getX() - Vector3i.getX());
		double d5 = (double) (Vector3i1.getZ() - Vector3i.getZ());
//		double d6 = Math.sqrt(d4 * d4 + d5 * d5);
		double d7 = forcedMovement.x * d4 + forcedMovement.z * d5;
		if (d7 < 0.0D) {
			d4 = -d4;
			d5 = -d5;
		}

		double d23 = (double) railPos.getX() + 0.5D + (double) Vector3i.getX() * 0.5D;
		double d10 = (double) railPos.getZ() + 0.5D + (double) Vector3i.getZ() * 0.5D;
		double d12 = (double) railPos.getX() + 0.5D + (double) Vector3i1.getX() * 0.5D;
		double d13 = (double) railPos.getZ() + 0.5D + (double) Vector3i1.getZ() * 0.5D;
		d4 = d12 - d23;
		d5 = d13 - d10;
		double d14;
		if (d4 == 0.0D) {
			d14 = actualZ - (double) railPos.getZ();
		} else if (d5 == 0.0D) {
			d14 = actualX - (double) railPos.getX();
		} else {
			double d15 = actualX - d23;
			double d16 = actualZ - d10;
			d14 = (d15 * d4 + d16 * d5) * 2.0D;
		}

		actualX = d23 + d4 * d14;
		actualZ = d10 + d5 * d14;

		cart.setPos(actualX, actualY, actualZ);
		cart.setDeltaMovement(forcedMovement);
		moveMinecartOnRail(cart);

		x = cart.getX();
		y = cart.getY();
		z = cart.getZ();

		if (Vector3i.getY() != 0 && Mth.floor(x) - railPos.getX() == Vector3i.getX()
			&& Mth.floor(z) - railPos.getZ() == Vector3i.getZ()) {
			cart.setPos(x, y + (double) Vector3i.getY(), z);
		} else if (Vector3i1.getY() != 0 && Mth.floor(x) - railPos.getX() == Vector3i1.getX()
			&& Mth.floor(z) - railPos.getZ() == Vector3i1.getZ()) {
			cart.setPos(x, y + (double) Vector3i1.getY(), z);
		}

		x = cart.getX();
		y = cart.getY();
		z = cart.getZ();

		Vec3 Vector3d3 = getPos(cart, x, y, z);
		if (Vector3d3 != null && actualVec != null) {
			double d17 = (actualVec.y - Vector3d3.y) * 0.05D;
			Vec3 Vector3d4 = cart.getDeltaMovement();
			double d18 = Math.sqrt(Vector3d4.horizontalDistanceSqr());
			if (d18 > 0.0D) {
				cart.setDeltaMovement(Vector3d4.multiply((d18 + d17) / d18, 1.0D, (d18 + d17) / d18));
			}

			cart.setPos(x, Vector3d3.y, z);
		}

		x = cart.getX();
		y = cart.getY();
		z = cart.getZ();

		int j = Mth.floor(x);
		int i = Mth.floor(z);
		if (j != railPos.getX() || i != railPos.getZ()) {
			Vec3 Vector3d5 = cart.getDeltaMovement();
			double d26 = Math.sqrt(Vector3d5.horizontalDistanceSqr());
			cart.setDeltaMovement(d26 * (double) (j - railPos.getX()), Vector3d5.y,
				d26 * (double) (i - railPos.getZ()));
		}

		cart.setDeltaMovement(previousMotion);
	}

	/** NeoForge's AbstractMinecart#moveMinecartOnRail: move by the cart's motion, at most its top speed. */
	private static void moveMinecartOnRail(AbstractMinecart cart) {
		double scale = cart.isVehicle() ? 0.75D : 1.0D;
		double maxSpeed = getMaxSpeed(cart);
		Vec3 motion = cart.getDeltaMovement();
		cart.move(MoverType.SELF,
			new Vec3(Mth.clamp(scale * motion.x, -maxSpeed, maxSpeed), 0.0D, Mth.clamp(scale * motion.z, -maxSpeed, maxSpeed)));
	}

	/** OldMinecartBehavior#getPos: where on its rail a cart at the given position would be. */
	@Nullable
	public static Vec3 getPos(AbstractMinecart cart, double x, double y, double z) {
		Level level = cart.level();
		int xt = Mth.floor(x);
		int yt = Mth.floor(y);
		int zt = Mth.floor(z);
		if (level.getBlockState(new BlockPos(xt, yt - 1, zt))
			.is(BlockTags.RAILS)) {
			yt--;
		}

		BlockState state = level.getBlockState(new BlockPos(xt, yt, zt));
		RailShape shape = getRailShape(state);
		if (shape == null)
			return null;

		Pair<Vec3i, Vec3i> exits = MATRIX.get(shape);
		Vec3i exit0 = exits.getFirst();
		Vec3i exit1 = exits.getSecond();
		double x0 = xt + 0.5 + exit0.getX() * 0.5;
		double y0 = yt + 0.0625 + exit0.getY() * 0.5;
		double z0 = zt + 0.5 + exit0.getZ() * 0.5;
		double x1 = xt + 0.5 + exit1.getX() * 0.5;
		double y1 = yt + 0.0625 + exit1.getY() * 0.5;
		double z1 = zt + 0.5 + exit1.getZ() * 0.5;
		double xD = x1 - x0;
		double yD = (y1 - y0) * 2.0;
		double zD = z1 - z0;
		double progress;
		if (xD == 0.0) {
			progress = z - zt;
		} else if (zD == 0.0) {
			progress = x - xt;
		} else {
			double xx = x - x0;
			double zz = z - z0;
			progress = (xx * xD + zz * zD) * 2.0;
		}

		x = x0 + xD * progress;
		y = y0 + yD * progress;
		z = z0 + zD * progress;
		if (yD < 0.0) {
			y++;
		} else if (yD > 0.0) {
			y += 0.5;
		}

		return new Vec3(x, y, z);
	}

	public static Vec3 getRailVec(RailShape shape) {
		switch (shape) {
		case ASCENDING_NORTH:
		case ASCENDING_SOUTH:
		case NORTH_SOUTH:
			return new Vec3(0, 0, 1);
		case ASCENDING_EAST:
		case ASCENDING_WEST:
		case EAST_WEST:
			return new Vec3(1, 0, 0);
		case NORTH_EAST:
		case SOUTH_WEST:
			return new Vec3(1, 0, 1).normalize();
		case NORTH_WEST:
		case SOUTH_EAST:
			return new Vec3(1, 0, -1).normalize();
		default:
			return new Vec3(0, 1, 0);
		}
	}

}

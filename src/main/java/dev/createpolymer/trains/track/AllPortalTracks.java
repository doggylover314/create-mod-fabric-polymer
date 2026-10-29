package dev.createpolymer.trains.track;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.contraption.train.PortalTrackProvider;
import dev.createpolymer.contraptions.glue.SuperGlueEntity;
import dev.createpolymer.util.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;

/**
 * Manages portal track integrations for various dimensions within the Create mod.
 * <p>
 * Portals must be entered from the side and must lead to a different dimension than the one entered from.
 * This class handles the registration and functionality of portal tracks for standard portals.
 * </p>
 */
public class AllPortalTracks {
	/**
	 * Registers a portal track integration for a given block identified by its {@link Identifier}, if it exists.
	 * If it does not, a warning will be logged.
	 *
	 * @param id       The identifier of the portal block.
	 * @param provider The portal track provider for the block.
	 */
	public static void tryRegisterIntegration(Identifier id, PortalTrackProvider provider) {
		if (BuiltInRegistries.BLOCK.containsKey(id)) {
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			PortalTrackProvider.REGISTRY.register(block, provider);
		} else {
			CreatePolymer.LOGGER.warn("Portal for integration wasn't found: {}. Compat outdated?", id);
		}
	}

	/**
	 * Registers a simple portal track integration for a given block identified by its {@link Block}.
	 * <p>
	 * Note: This only allows registering integrations that go from the Overworld to another dimension and vice versa.
	 *
	 * @param portalBlock The portal block.
	 * @param levelKey    The resource key of the dimension to travel to
	 */
	private static void tryRegisterSimpleInteraction(Block portalBlock, ResourceKey<Level> levelKey) {
		PortalTrackProvider p = (level, face) ->
			PortalTrackProvider.fromPortal(level, face, Level.OVERWORLD, levelKey, (Portal) portalBlock);
		PortalTrackProvider.REGISTRY.register(portalBlock, p);
	}

	// Built-in handlers

	/**
	 * Registers default portal track integrations for built-in dimensions.
	 * This includes the Nether.
	 */
	public static void registerDefaults() {
		tryRegisterSimpleInteraction(Blocks.NETHER_PORTAL, Level.NETHER);
	}

	public static PortalTrackProvider.Exit fromPortal(ServerLevel level, BlockFace inboundTrack,
		ResourceKey<Level> firstDimension, ResourceKey<Level> secondDimension, Portal portal) {
		ResourceKey<Level> resourceKey = level.dimension() == secondDimension ? firstDimension : secondDimension;

		MinecraftServer minecraftServer = level.getServer();
		ServerLevel otherLevel = minecraftServer.getLevel(resourceKey);

		if (otherLevel == null)
			return null;

		BlockPos portalPos = inboundTrack.getConnectedPos();
		BlockState portalState = level.getBlockState(portalPos);

		SuperGlueEntity probe = new SuperGlueEntity(level, new AABB(portalPos));
		probe.setYRot(inboundTrack.getFace()
			.toYRot());

		TeleportTransition dimensiontransition = portal.getPortalDestination(level, probe, probe.blockPosition());
		if (dimensiontransition == null)
			return null;

		BlockPos otherPortalPos = BlockPos.containing(dimensiontransition.position());
		BlockState otherPortalState = otherLevel.getBlockState(otherPortalPos);
		if (!otherPortalState.is(portalState.getBlock()))
			return null;

		Direction targetDirection = inboundTrack.getFace();
		if (targetDirection.getAxis() == otherPortalState.getValue(BlockStateProperties.HORIZONTAL_AXIS))
			targetDirection = targetDirection.getClockWise();
		BlockPos otherPos = otherPortalPos.relative(targetDirection);
		return new PortalTrackProvider.Exit(otherLevel, new BlockFace(otherPos, targetDirection.getOpposite()));
	}
}

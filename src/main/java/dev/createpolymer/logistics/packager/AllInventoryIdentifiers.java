package dev.createpolymer.logistics.packager;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import dev.createpolymer.api.packager.InventoryIdentifier;
import dev.createpolymer.logistics.vault.ItemVaultBlock;
import dev.createpolymer.logistics.vault.ItemVaultBlockEntity;
import dev.createpolymer.registry.feature.VaultRegistry;
import dev.createpolymer.util.BlockFace;
import dev.createpolymer.util.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class AllInventoryIdentifiers {
	public static void registerDefaults() {
		// identify known single blocks. Create's SINGLE_BLOCK_INVENTORIES block tag: shulker boxes, hoppers,
		// dispensers and droppers, chiseled bookshelves, jukeboxes and barrels
		InventoryIdentifier.REGISTRY.registerProvider(block -> {
			if (block instanceof ShulkerBoxBlock || block instanceof HopperBlock || block instanceof DispenserBlock
				|| block instanceof ChiseledBookShelfBlock || block instanceof JukeboxBlock
				|| block instanceof BarrelBlock)
				return AllInventoryIdentifiers::single;
			return null;
		});

		// connect double chests
		InventoryIdentifier.REGISTRY.registerProvider(block -> {
			Collection<Property<?>> properties = block.getStateDefinition().getProperties();
			if (properties.contains(ChestBlock.TYPE) && properties.contains(ChestBlock.FACING)) {
				return AllInventoryIdentifiers::chest;
			}
			return null;
		});

		// best-effort for WorldlyContainerHolders (just composters in vanilla)
		InventoryIdentifier.REGISTRY.registerProvider(block -> {
			if (block instanceof WorldlyContainerHolder) {
				return AllInventoryIdentifiers::worldlyContainerBlock;
			}
			return null;
		});

		// connect vaults
		InventoryIdentifier.REGISTRY.register(VaultRegistry.ITEM_VAULT, (level, state, face) -> {
			BlockEntity be = level.getBlockEntity(face.getPos());
			return be instanceof ItemVaultBlockEntity vault ? vaultId(vault) : null;
		});
	}

	/**
	 * Create caches this on the vault controller when it builds the combined handler: an identifier encompassing all
	 * component vaults (its bounds include the block one step past the far end, as in Create).
	 */
	private static InventoryIdentifier vaultId(ItemVaultBlockEntity vault) {
		ItemVaultBlockEntity controller = vault.getControllerBE();
		if (controller == null)
			return null;
		BlockPos pos = controller.getBlockPos();
		int radius = controller.getWidth();
		int length = controller.getHeight();
		boolean alongZ = ItemVaultBlock.getVaultBlockAxis(controller.getBlockState()) == Axis.Z;
		BlockPos farCorner = alongZ ? pos.offset(radius, radius, length) : pos.offset(length, radius, radius);
		return new InventoryIdentifier.Bounds(BoundingBox.fromCorners(pos, farCorner));
	}

	private static InventoryIdentifier single(Level level, BlockState state, BlockFace face) {
		return new InventoryIdentifier.Single(face.getPos());
	}

	private static InventoryIdentifier chest(Level level, BlockState state, BlockFace face) {
		ChestType type = state.getValue(ChestBlock.TYPE);

		if (type != ChestType.SINGLE) {
			Direction toOther = ChestBlock.getConnectedDirection(state);
			BlockPos otherPos = face.getPos().relative(toOther);
			BlockState otherState = level.getBlockState(otherPos);
			if (otherState.is(state.getBlock()) && ChestBlock.getConnectedDirection(otherState) == toOther.getOpposite()) {
				return new InventoryIdentifier.Pair(face.getPos(), otherPos);
			}
		}

		return new InventoryIdentifier.Single(face.getPos());
	}

	private static InventoryIdentifier worldlyContainerBlock(Level level, BlockState state, BlockFace face) {
		WorldlyContainerHolder holder = (WorldlyContainerHolder) state.getBlock();
		WorldlyContainer container = holder.getContainer(state, level, face.getPos());
		return ofWorldlyContainer(container, face);
	}

	private static InventoryIdentifier ofWorldlyContainer(WorldlyContainer container, BlockFace face) {
		Direction side = face.getFace();
		int[] slots = container.getSlotsForFace(side);
		// get all faces that have the same slots as the given one
		Set<Direction> directions = EnumSet.of(side);
		for (Direction direction : Iterate.directions) {
			if (direction != side) {
				int[] faceSlots = container.getSlotsForFace(direction);
				if (Arrays.equals(slots, faceSlots)) {
					directions.add(direction);
				}
			}
		}
		return new InventoryIdentifier.MultiFace(face.getPos(), directions);
	}

	// called manually when no other Finder is found.
	// currently just checks for WorldlyContainer BlockEntities, which would
	// fill the registry with Finders pointlessly if done through a provider.
	public static InventoryIdentifier fallback(Level level, BlockState state, BlockFace face) {
		BlockEntity be = level.getBlockEntity(face.getPos());
		if (be instanceof WorldlyContainer container) {
			return ofWorldlyContainer(container, face);
		}

		return null;
	}
}

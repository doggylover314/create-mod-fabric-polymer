package dev.createpolymer.registry.feature;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import dev.createpolymer.kinetics.base.BlockStressValues;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorBlock;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorBlockEntity;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorConnectionHandler;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import dev.createpolymer.kinetics.chainConveyor.ChainConveyorRidingHandler;
import dev.createpolymer.kinetics.chainConveyor.ChainPackageInteractionHandler;
import dev.createpolymer.kinetics.chainConveyor.ServerChainConveyorHandler;
import dev.createpolymer.registry.ModBlockEntities;
import dev.createpolymer.registry.ModBlocks;
import dev.createpolymer.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The chain conveyor: Create's block, block entity, item and the handlers that stand in for its client side.
 */
public final class ChainConveyorRegistry {
	private ChainConveyorRegistry() {}

	// Create's CKinetics and CLogistics settings
	/** Maximum length in blocks of chain conveyor connections. */
	public static int maxChainConveyorLength = 32;
	/** Maximum amount of connections each chain conveyor can have. */
	public static int maxChainConveyorConnections = 4;
	/** The amount of packages a chain conveyor can carry at a time. */
	public static int chainConveyorCapacity = 20;

	public static final ChainConveyorBlock CHAIN_CONVEYOR = ModBlocks.register("chain_conveyor", ChainConveyorBlock::new,
		ModBlocks.stone().mapColor(MapColor.PODZOL));

	public static final BlockEntityType<ChainConveyorBlockEntity> CHAIN_CONVEYOR_BLOCK_ENTITY = ModBlockEntities.register("chain_conveyor",
		(pos, state) -> new ChainConveyorBlockEntity(ChainConveyorRegistry.CHAIN_CONVEYOR_BLOCK_ENTITY, pos, state), CHAIN_CONVEYOR);

	public static final Item CHAIN_CONVEYOR_ITEM = ModItems.block(CHAIN_CONVEYOR);

	public static void init() {
		BlockStressValues.setImpact(CHAIN_CONVEYOR, 1);

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || player.isSpectator() || !(player instanceof ServerPlayer serverPlayer))
				return InteractionResult.PASS;
			if (handledThisTick.contains(player.getUUID()))
				return InteractionResult.SUCCESS;
			InteractionResult result = onUseBlock(serverPlayer, hand, hit);
			if (result != InteractionResult.PASS)
				handledThisTick.add(player.getUUID());
			return result;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || player.isSpectator() || !(player instanceof ServerPlayer serverPlayer))
				return InteractionResult.PASS;
			if (handledThisTick.contains(player.getUUID()))
				return InteractionResult.SUCCESS;
			InteractionResult result = hand == InteractionHand.MAIN_HAND
				&& ChainConveyorConnectionHandler.onRightClick(serverPlayer, null) ? InteractionResult.SUCCESS
					: onUse(serverPlayer, hand);
			if (result != InteractionResult.PASS)
				handledThisTick.add(player.getUUID());
			return result;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			handledThisTick.clear();
			ServerChainConveyorHandler.tick();
		});
		// Seats of rides that a crash left behind
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ArmorStand stand && stand.entityTags().contains(ChainConveyorRidingHandler.SEAT_TAG)
				&& !ChainConveyorRidingHandler.isSeat(stand))
				level.getServer().execute(stand::discard);
		});
	}

	/**
	 * A player who points at a block and does not use it gets two packets from the client: one to use the block and
	 * one to use the item. Whatever the first one did with a chain, the second one must not undo.
	 */
	private static final Set<UUID> handledThisTick = new HashSet<>();

	private static InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
		if (hand == InteractionHand.MAIN_HAND && ChainConveyorConnectionHandler.onRightClick(player, hit))
			return InteractionResult.SUCCESS;
		InteractionResult result = ChainConveyorConnectionHandler.onItemUsedOnBlock(player, player.level(), hand, hit);
		if (result != InteractionResult.PASS)
			return result;
		return onUse(player, hand);
	}

	/** Create's InputEvents: what using an item does with the chains in the line of sight. */
	private static InteractionResult onUse(ServerPlayer player, InteractionHand hand) {
		InteractionResult result = ChainConveyorInteractionHandler.onUse(player, hand);
		if (result != InteractionResult.PASS)
			return result;

		ItemStack itemInHand = player.getItemInHand(hand);
		if (itemInHand.is(ModItems.WRENCH))
			return InteractionResult.PASS;
		if (itemInHand.is(Items.IRON_CHAIN) || itemInHand.is(PackagesRegistry.PACKAGE_FROGPORT_ITEM))
			return InteractionResult.PASS;

		return ChainPackageInteractionHandler.onUse(player, hand);
	}
}

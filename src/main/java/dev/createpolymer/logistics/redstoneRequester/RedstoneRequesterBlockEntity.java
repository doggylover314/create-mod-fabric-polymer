package dev.createpolymer.logistics.redstoneRequester;

import dev.createpolymer.logistics.BigItemStack;
import dev.createpolymer.logistics.packager.InventorySummary;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.logistics.stockTicker.StockCheckingBlockEntity;
import dev.createpolymer.logistics.stockTicker.StockSounds;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class RedstoneRequesterBlockEntity extends StockCheckingBlockEntity {

	public boolean allowPartialRequests;
	public PackageOrderWithCrafts encodedRequest = PackageOrderWithCrafts.empty();
	public String encodedTargetAdress = "";

	public boolean lastRequestSucceeded;

	protected boolean redstonePowered;

	public RedstoneRequesterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		allowPartialRequests = false;
	}

	protected void onRedstonePowerChanged() {
		boolean hasNeighborSignal = level.hasNeighborSignal(worldPosition);
		if (redstonePowered == hasNeighborSignal)
			return;

		lastRequestSucceeded = false;
		if (hasNeighborSignal)
			triggerRequest();

		redstonePowered = hasNeighborSignal;
		notifyUpdate();
	}

	public void triggerRequest() {
		if (encodedRequest.isEmpty())
			return;

		boolean anySucceeded = false;

		InventorySummary summaryOfOrder = new InventorySummary();
		encodedRequest.stacks()
			.forEach(summaryOfOrder::add);

		InventorySummary summary = getAccurateSummary();
		for (BigItemStack entry : summaryOfOrder.getStacks()) {
			if (summary.getCountOf(entry.stack) >= entry.count) {
				anySucceeded = true;
				continue;
			}
			if (!allowPartialRequests && level instanceof ServerLevel) {
				playEffect(false);
				return;
			}
		}

		broadcastPackageRequest(RequestType.REDSTONE, encodedRequest, null, encodedTargetAdress);
		if (level instanceof ServerLevel)
			playEffect(anySucceeded);
		lastRequestSucceeded = true;
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);
		redstonePowered = input.getBooleanOr("Powered", false);
		lastRequestSucceeded = input.getBooleanOr("Success", false);
		allowPartialRequests = input.getBooleanOr("AllowPartial", false);
		encodedRequest = input.read("EncodedRequest", PackageOrderWithCrafts.CODEC)
			.orElse(PackageOrderWithCrafts.empty());
		encodedTargetAdress = input.getStringOr("EncodedAddress", "");
	}

	@Override
	public void writeSafe(ValueOutput output) {
		super.writeSafe(output);
		output.putBoolean("AllowPartial", allowPartialRequests);
		output.putString("EncodedAddress", encodedTargetAdress);
		output.store("EncodedRequest", PackageOrderWithCrafts.CODEC, encodedRequest);
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);
		output.putBoolean("Powered", redstonePowered);
		output.putBoolean("Success", lastRequestSucceeded);
		output.putBoolean("AllowPartial", allowPartialRequests);
		output.putString("EncodedAddress", encodedTargetAdress);
		output.store("EncodedRequest", PackageOrderWithCrafts.CODEC, encodedRequest);
	}

	public InteractionResult use(Player player) {
		if (player == null || player.isCrouching())
			return InteractionResult.PASS;
		if (player instanceof FakePlayer)
			return InteractionResult.PASS;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (!behaviour.mayInteractMessage(player))
			return InteractionResult.SUCCESS;

		if (player instanceof ServerPlayer serverPlayer)
			new RedstoneRequesterGui(serverPlayer, this).open();
		return InteractionResult.SUCCESS;
	}

	/** Create sends this to the clients around the requester. */
	public void playEffect(boolean success) {
		Vec3 vec3 = Vec3.atCenterOf(worldPosition);
		if (success) {
			StockSounds.confirm(level, worldPosition, 0.5f, 1.5f);
			StockSounds.stockLink(level, worldPosition, 1.0f, 1.0f);
			if (level instanceof ServerLevel serverLevel)
				serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, vec3.x, vec3.y, vec3.z, 6, .2, .2, .2, .05);
		} else {
			StockSounds.deny(level, worldPosition, 0.5f, 1);
			if (level instanceof ServerLevel serverLevel)
				serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, vec3.x, vec3.y + 1, vec3.z, 5, .1, .1, .1, 0);
		}
	}

}

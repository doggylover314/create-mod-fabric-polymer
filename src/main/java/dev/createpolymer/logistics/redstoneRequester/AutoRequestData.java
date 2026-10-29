package dev.createpolymer.logistics.redstoneRequester;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.registry.feature.StockRegistry;
import dev.createpolymer.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public record AutoRequestData(PackageOrderWithCrafts encodedRequest, String encodedTargetAddress, BlockPos targetOffset, String targetDim, boolean isValid) {

	public static final Codec<AutoRequestData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		PackageOrderWithCrafts.CODEC.fieldOf("encoded_request").forGetter(i -> i.encodedRequest),
		Codec.STRING.fieldOf("encoded_target_address").forGetter(i -> i.encodedTargetAddress),
		BlockPos.CODEC.fieldOf("target_offset").forGetter(i -> i.targetOffset),
		Codec.STRING.fieldOf("target_dim").forGetter(i -> i.targetDim),
		Codec.BOOL.fieldOf("is_valid").forGetter(i -> i.isValid)
	).apply(instance, AutoRequestData::new));

	public AutoRequestData() {
		this(PackageOrderWithCrafts.empty(), "", BlockPos.ZERO, "null", false);
	}

	public void writeToItem(BlockPos position, ItemStack itemStack) {
		Mutable mutable = new Mutable(this);
		mutable.targetOffset = position.offset(targetOffset);
		itemStack.set(StockRegistry.AUTO_REQUEST_DATA, mutable.toImmutable());
	}

	@Nullable
	public static AutoRequestData readFromItem(Level level, @Nullable Player player, BlockPos position, ItemStack itemStack) {
		AutoRequestData requestData = itemStack.get(StockRegistry.AUTO_REQUEST_DATA);
		if (requestData == null)
			return null;

		Mutable mutable = new Mutable(requestData);

		mutable.targetOffset = mutable.targetOffset.subtract(position);
		mutable.isValid =
			mutable.targetOffset.closerThan(BlockPos.ZERO, 128) && requestData.targetDim.equals(level.dimension()
				.identifier()
				.toString());

		if (player != null)
			Lang.builder(CreatePolymer.MOD_ID)
				.translate(mutable.isValid ? "redstone_requester.keeper_connected"
					: "redstone_requester.keeper_too_far_away")
				.style(mutable.isValid ? ChatFormatting.WHITE : ChatFormatting.RED)
				.sendStatus(player);

		return mutable.toImmutable();
	}

	public static class Mutable {
		public PackageOrderWithCrafts encodedRequest = PackageOrderWithCrafts.empty();
		public String encodedTargetAddress = "";
		public BlockPos targetOffset = BlockPos.ZERO;
		public String targetDim = "null";
		public boolean isValid = false;

		public Mutable() {
		}

		public Mutable(AutoRequestData data) {
			encodedRequest = data.encodedRequest;
			encodedTargetAddress = data.encodedTargetAddress;
			targetOffset = data.targetOffset;
			targetDim = data.targetDim;
			isValid = data.isValid;
		}

		public AutoRequestData toImmutable() {
			return new AutoRequestData(encodedRequest, encodedTargetAddress, targetOffset, targetDim, isValid);
		}
	}
}

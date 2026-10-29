package dev.createpolymer.logistics.packagerLink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.logistics.box.PackageSounds;
import dev.createpolymer.logistics.packager.IdentifiedInventory;
import dev.createpolymer.logistics.packager.InventorySummary;
import dev.createpolymer.logistics.packager.PackagerBlockEntity;
import dev.createpolymer.logistics.packager.PackagingRequest;
import dev.createpolymer.logistics.packager.repackager.RepackagerBlockEntity;
import dev.createpolymer.logistics.stockTicker.PackageOrderWithCrafts;
import dev.createpolymer.util.AngleHelper;
import dev.createpolymer.util.Pair;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Create's PackagerLinkBlockEntity (the stock link). It is Create's LinkWithBulbBlockEntity without the glowing
 * bulb, which is a client-side effect.
 */
public class PackagerLinkBlockEntity extends SmartBlockEntity {

	public LogisticallyLinkedBehaviour behaviour;
	public UUID placedBy;

	public PackagerLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(10);
		placedBy = null;
	}

	public InventorySummary fetchSummaryFromPackager(@Nullable IdentifiedInventory ignoredHandler) {
		PackagerBlockEntity packager = getPackager();
		if (packager == null)
			return InventorySummary.EMPTY;
		if (packager.isTargetingSameInventory(ignoredHandler))
			return InventorySummary.EMPTY;
		return packager.getAvailableItems();
	}

	/** Create plays this on the clients when a request goes through the link: its sound and a burst of wifi. */
	public void playEffect() {
		PackageSounds.stockLink(level, worldPosition, 0.75f, 1.25f);
		Vec3 vec3 = Vec3.atCenterOf(worldPosition);

		BlockState state = getBlockState();
		float f = 1;

		AttachFace face = state.getOptionalValue(PackagerLinkBlock.FACE)
			.orElse(AttachFace.FLOOR);
		if (face != AttachFace.FLOOR)
			f = -1;
		if (face == AttachFace.WALL)
			vec3 = vec3.add(0, 0.25, 0);

		vec3 = vec3.add(Vec3.atLowerCornerOf(state.getOptionalValue(PackagerLinkBlock.FACING)
				.orElse(Direction.SOUTH)
				.getUnitVec3i())
			.scale(f * 0.125));

		if (level instanceof ServerLevel serverLevel)
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, vec3.x, vec3.y, vec3.z, 6, .1, .1, .1, .05);
	}

	@Nullable
	public Pair<PackagerBlockEntity, PackagingRequest> processRequest(ItemStack stack, int amount, String address,
		int linkIndex, MutableBoolean finalLink, int orderId, @Nullable PackageOrderWithCrafts context,
		@Nullable IdentifiedInventory ignoredHandler) {
		PackagerBlockEntity packager = getPackager();
		if (packager == null)
			return null;
		if (packager.isTargetingSameInventory(ignoredHandler))
			return null;

		InventorySummary summary = packager.getAvailableItems();
		int availableCount = summary.getCountOf(stack);
		if (availableCount == 0)
			return null;
		int toWithdraw = Math.min(amount, availableCount);
		return Pair.of(packager,
			PackagingRequest.create(stack, toWithdraw, address, linkIndex, finalLink, 0, orderId, context));
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);
		if (placedBy != null)
			output.store("PlacedBy", UUIDUtil.CODEC, placedBy);
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);
		placedBy = input.read("PlacedBy", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(behaviour = new LogisticallyLinkedBehaviour(this, true));
	}

	@Override
	public void initialize() {
		super.initialize();
		behaviour.redstonePowerChanged(PackagerLinkBlock.getPower(getBlockState(), level, worldPosition));
		PackagerBlockEntity packager = getPackager();
		if (packager != null)
			packager.recheckIfLinksPresent();
	}

	@Nullable
	public PackagerBlockEntity getPackager() {
		BlockState blockState = getBlockState();
		if (behaviour.redstonePower == 15)
			return null;
		BlockPos source = worldPosition.relative(PackagerLinkBlock.getConnectedDirection(blockState)
			.getOpposite());
		if (!(level.getBlockEntity(source) instanceof PackagerBlockEntity packager))
			return null;
		if (packager instanceof RepackagerBlockEntity)
			return null;
		return packager;
	}

	public Direction getBulbFacing(BlockState state) {
		return PackagerLinkBlock.getConnectedDirection(state);
	}

	private static final Map<BlockState, Vec3> bulbOffsets = new HashMap<>();

	public Vec3 getBulbOffset(BlockState state) {
		return bulbOffsets.computeIfAbsent(state, s -> {
			Vec3 offset = VecHelper.voxelSpace(5, 6, 11);
			Vec3 wallOffset = VecHelper.voxelSpace(11, 6, 5);
			AttachFace face = s.getValue(PackagerLinkBlock.FACE);
			Vec3 vec = face == AttachFace.WALL ? wallOffset : offset;
			float angle = AngleHelper.horizontalAngle(s.getValue(PackagerLinkBlock.FACING));
			if (face == AttachFace.CEILING)
				angle = -angle;
			if (face == AttachFace.WALL)
				angle = 0;
			return VecHelper.rotateCentered(vec, angle, Axis.Y);
		});
	}

}

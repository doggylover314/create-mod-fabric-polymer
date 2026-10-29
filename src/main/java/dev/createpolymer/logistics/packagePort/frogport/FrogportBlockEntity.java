package dev.createpolymer.logistics.packagePort.frogport;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.ItemHandlerHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.logistics.box.PackageItem;
import dev.createpolymer.logistics.box.PackageSounds;
import dev.createpolymer.logistics.box.PackageStyles;
import dev.createpolymer.logistics.packager.PackagerItemHandler;
import dev.createpolymer.logistics.packagePort.PackagePortBlockEntity;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Create's FrogportBlockEntity. Create runs its animation on the client; here it runs in the server's tick and the
 * block's display model reads {@link #animationProgress}, {@link #anticipationProgress} and
 * {@link #manualOpenAnimationProgress}. The sound effects come from the server.
 */
public class FrogportBlockEntity extends PackagePortBlockEntity {

	public ItemStack animatedPackage;
	/** What the model draws; unlike {@link #animatedPackage} it stays until the package has left the frog's tongue. */
	public ItemStack renderedPackage = ItemStack.EMPTY;
	public LerpedFloat manualOpenAnimationProgress;
	public LerpedFloat animationProgress;
	public LerpedFloat anticipationProgress;
	public boolean currentlyDepositing;
	public boolean goggles;

	public float passiveYaw;

	private boolean failedLastExport;

	private ItemStack deferAnimationStart;
	private boolean deferAnimationInward;

	public FrogportBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		animationProgress = LerpedFloat.linear();
		anticipationProgress = LerpedFloat.linear();
		manualOpenAnimationProgress = LerpedFloat.linear()
			.startWithValue(0)
			.chase(0, 0.35, Chaser.LINEAR);
		goggles = false;
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(PackagesRegistry.PACKAGE_FROGPORT_BLOCK_ENTITY, (be, side) -> be.itemHandler);
	}

	public boolean isAnimationInProgress() {
		return animationProgress.getChaseTarget() == 1;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (isAnimationInProgress())
			return;

		boolean prevFail = failedLastExport;
		tryPushingToAdjacentInventories();
		tryPullingFromOwnAndAdjacentInventories();

		if (failedLastExport != prevFail)
			sendData();
	}

	/** Create sends this to the client when a package approaches on a chain; the frog opens its mouth. */
	public void sendAnticipate() {
		if (isAnimationInProgress())
			return;
		for (int i = 0; i < inventory.getSlots(); i++)
			if (inventory.getStackInSlot(i)
				.isEmpty()) {
				anticipate();
				return;
			}
	}

	public void anticipate() {
		anticipationProgress.chase(1, 0.1, Chaser.LINEAR);
	}

	@Override
	public void tick() {
		super.tick();

		if (deferAnimationStart != null) {
			startAnimation(deferAnimationStart, deferAnimationInward);
			deferAnimationStart = null;
		}

		if (anticipationProgress.getValue() == 1)
			anticipationProgress.startWithValue(0);

		manualOpenAnimationProgress.updateChaseTarget(openTracker.openCount > 0 ? 1 : 0);
		boolean wasOpen = manualOpenAnimationProgress.getValue() > 0;

		anticipationProgress.tickChaser();
		manualOpenAnimationProgress.tickChaser();

		if (wasOpen && manualOpenAnimationProgress.getValue() == 0)
			PackageSounds.frogportClose(level, worldPosition);

		if (!isAnimationInProgress())
			return;

		animationProgress.tickChaser();

		float value = animationProgress.getValue();
		if (currentlyDepositing) {
			if (value > 0.5 && animatedPackage != null) {
				if (target == null
					|| !target.depositImmediately() && !target.export(level, worldPosition, animatedPackage, false))
					drop(animatedPackage);
				animatedPackage = null;
			}
			if (value > 0.7)
				renderedPackage = ItemStack.EMPTY;
			if (animationProgress.getValue(0) < 0.2 && value > 0.2 && target != null) {
				Vec3 v = target.getExactTargetLocation(this, level, worldPosition);
				PackageSounds.chainStep(level, v);
			}
		}

		if (value < 1)
			return;

		anticipationProgress.startWithValue(0);
		animationProgress.startWithValue(0);

		if (!currentlyDepositing && animatedPackage != null) {
			if (!ItemHandlerHelper.insertItem(inventory, animatedPackage.copy(), false)
				.isEmpty())
				drop(animatedPackage);
		}

		animatedPackage = null;
		renderedPackage = ItemStack.EMPTY;
	}

	public void startAnimation(ItemStack box, boolean deposit) {
		if (!PackageItem.isPackage(box))
			return;

		if (deposit && (target == null
			|| target.depositImmediately() && !target.export(level, worldPosition, box.copy(), false)))
			return;

		animationProgress.startWithValue(0);
		animationProgress.chase(1, 0.1, Chaser.LINEAR);
		animatedPackage = box;
		renderedPackage = box;
		currentlyDepositing = deposit;

		if (level != null && !level.isClientSide()) {
			PackageSounds.frogportOpen(level, worldPosition);

			if (currentlyDepositing) {
				PackageSounds.frogportDeposit(level, worldPosition);

			} else {
				PackageSounds.frogportCatch(level, worldPosition);
				Vec3 vec = target == null ? null : target.getExactTargetLocation(this, level, worldPosition);
				if (vec != null && level instanceof ServerLevel serverLevel)
					for (int i = 0; i < 5; i++)
						serverLevel.sendParticles(
							new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
								net.minecraft.world.level.block.Blocks.IRON_CHAIN.defaultBlockState()),
							vec.x, vec.y - level.getRandom().nextFloat() * 0.25, vec.z, 1, 0, 0, 0, 0);
			}

			level.blockEntityChanged(worldPosition);
			sendData();
		}
	}

	protected void tryPushingToAdjacentInventories() {
		failedLastExport = false;

		if (itemHandler == null)
			return;

		boolean empty = true;
		for (int i = 0; i < itemHandler.getSlots(); i++)
			if (!itemHandler.getStackInSlot(i)
				.isEmpty())
				empty = false;
		if (empty)
			return;
		IItemHandler handler = getAdjacentInventory(Direction.DOWN);
		if (handler == null)
			return;

		for (int i = 0; i < itemHandler.getSlots(); i++) {
			ItemStack stackInSlot = itemHandler.extractItem(i, 1, true);
			if (stackInSlot.isEmpty())
				continue;
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, stackInSlot, false);
			if (remainder.isEmpty()) {
				itemHandler.extractItem(i, 1, false);
				level.blockEntityChanged(worldPosition);
			} else
				failedLastExport = true;
		}
	}

	@Override
	protected void onOpenChange(boolean open) {
		if (open)
			PackageSounds.frogportOpen(level, worldPosition);
	}

	public void tryPullingFromOwnAndAdjacentInventories() {
		if (isAnimationInProgress())
			return;
		if (target == null || !target.export(level, worldPosition, PackageStyles.getDefaultBox(), true))
			return;
		if (tryPullingFrom(inventory))
			return;
		for (Direction side : Iterate.directions) {
			if (side != Direction.DOWN)
				continue;
			IItemHandler handler = getAdjacentInventory(side);
			if (handler == null)
				continue;
			if (tryPullingFrom(handler))
				return;
		}
	}

	public boolean tryPullingFrom(IItemHandler handler) {
		ItemStack extract = ItemHelper.extract(handler, stack -> {
			if (!PackageItem.isPackage(stack))
				return false;
			String filterString = getFilterString();
			return filterString == null || handler instanceof PackagerItemHandler
				|| !PackageItem.matchAddress(stack, filterString);
		}, false);
		if (extract.isEmpty())
			return false;
		startAnimation(extract, true);
		return true;

	}

	protected @Nullable IItemHandler getAdjacentInventory(Direction side) {
		BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(side));
		if (blockEntity == null || blockEntity instanceof FrogportBlockEntity)
			return null;
		return ItemHandlerLookup.get(level, blockEntity.getBlockPos(), side.getOpposite());
	}

	@Override
	protected void write(ValueOutput output) {
		super.write(output);
		output.putFloat("PlacedYaw", passiveYaw);
		if (animatedPackage != null && isAnimationInProgress()) {
			output.store("AnimatedPackage", ItemStack.OPTIONAL_CODEC, animatedPackage);
			output.putBoolean("Deposit", currentlyDepositing);
		}
		output.putBoolean("FailedLastExport", failedLastExport);
		output.putBoolean("Goggles", goggles);
	}

	@Override
	protected void read(ValueInput input) {
		super.read(input);
		passiveYaw = input.getFloatOr("PlacedYaw", 0);
		failedLastExport = input.getBooleanOr("FailedLastExport", false);
		goggles = input.getBooleanOr("Goggles", false);
		animatedPackage = null;
		input.read("AnimatedPackage", ItemStack.OPTIONAL_CODEC).ifPresent(box -> {
			deferAnimationInward = input.getBooleanOr("Deposit", false);
			deferAnimationStart = box;
		});
	}

	public float getYaw() {
		if (target == null)
			return passiveYaw;
		Vec3 diff = target.getExactTargetLocation(this, level, worldPosition)
			.subtract(Vec3.atCenterOf(worldPosition));
		return (float) (Mth.atan2(diff.x, diff.z) * Mth.RAD_TO_DEG) + 180;
	}

	public boolean hasFailedLastExport() {
		return failedLastExport;
	}

	@Override
	public InteractionResult use(Player player) {
		if (player == null)
			return InteractionResult.TRY_WITH_EMPTY_HAND;

		ItemStack mainHandItem = player.getMainHandItem();
		if (!goggles && mainHandItem.is(ModItems.GOGGLES)) {
			goggles = true;
			if (!level.isClientSide()) {
				notifyUpdate();
				level.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.BLOCKS, 0.5f, 1.0f);
			}
			return InteractionResult.SUCCESS;
		}

		return super.use(player);
	}

}

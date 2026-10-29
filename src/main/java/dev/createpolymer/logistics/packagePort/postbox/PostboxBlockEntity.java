package dev.createpolymer.logistics.packagePort.postbox;

import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.logistics.packagePort.PackagePortBlockEntity;
import dev.createpolymer.registry.feature.PackagesRegistry;
import dev.createpolymer.util.LerpedFloat;
import dev.createpolymer.util.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's PostboxBlockEntity. The flag animation runs in the server's tick and is drawn by the block's display model.
 * The train station connection (GlobalStation / GlobalPackagePort offline buffer) is not ported.
 */
public class PostboxBlockEntity extends PackagePortBlockEntity {

	public LerpedFloat flag;
	public boolean forceFlag;

	private boolean sendParticles;

	public PostboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		flag = LerpedFloat.linear()
			.startWithValue(0);
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(PackagesRegistry.PACKAGE_POSTBOX_BLOCK_ENTITY, (be, side) -> be.itemHandler);
	}

	@Override
	public void tick() {
		super.tick();

		float currentTarget = flag.getChaseTarget();
		if (currentTarget == 0 || flag.settled()) {
			int target = (inventory.isEmpty() && !forceFlag) ? 0 : 1;
			if (target != currentTarget) {
				flag.chase(target, 0.1f, Chaser.LINEAR);
				if (target == 1)
					level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, .25f, 2);
			}
		}
		boolean settled = flag.getValue() > .15f;
		flag.tickChaser();
		if (currentTarget == 0 && settled != flag.getValue() > .15f)
			level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, .2f, 1.5f);

		if (sendParticles) {
			sendParticles = false;
			BoneMealItem.addGrowthParticles(level, worldPosition, 40);
		}
	}

	@Override
	protected void onOpenChange(boolean open) {
		// cached getBlockState doesn't update if we're exploded in the meantime, refreshBlockState crashes validation
		BlockState state = level.getBlockState(worldPosition);
		if (!(state.getBlock() instanceof PostboxBlock))
			return;

		level.setBlockAndUpdate(worldPosition, state.setValue(PostboxBlock.OPEN, open));
		level.playSound(null, worldPosition, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE,
			SoundSource.BLOCKS);
	}

	public void spawnParticles() {
		sendParticles = true;
	}

}

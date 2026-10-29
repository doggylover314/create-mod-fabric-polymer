package dev.createpolymer.decoration.placard;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class PlacardBlockEntity extends SmartBlockEntity {

	ItemStack heldItem;
	int poweredTicks;

	public PlacardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		heldItem = ItemStack.EMPTY;
		poweredTicks = 0;
	}

	@Override
	public void tick() {
		super.tick();
		if (level.isClientSide())
			return;
		if (poweredTicks == 0)
			return;

		poweredTicks--;
		if (poweredTicks > 0)
			return;

		BlockState blockState = getBlockState();
		level.setBlock(worldPosition, blockState.setValue(PlacardBlock.POWERED, false), Block.UPDATE_ALL);
		PlacardBlock.updateNeighbours(blockState, level, worldPosition);
	}

	public ItemStack getHeldItem() {
		return heldItem;
	}

	public void setHeldItem(ItemStack heldItem) {
		this.heldItem = heldItem;
		notifyUpdate();
	}

	public int getPoweredTicks() {
		return poweredTicks;
	}

	/** Starts the redstone pulse of a matching item: powered for 19 more ticks. */
	void pulse() {
		poweredTicks = 19;
		notifyUpdate();
		spawnParticles();
	}

	@Override
	protected void write(ValueOutput tag) {
		tag.putInt("PoweredTicks", poweredTicks);
		tag.store("Item", ItemStack.OPTIONAL_CODEC, heldItem);
		super.write(tag);
	}

	@Override
	protected void read(ValueInput tag) {
		poweredTicks = tag.getIntOr("PoweredTicks", 0);
		heldItem = tag.read("Item", ItemStack.OPTIONAL_CODEC)
			.orElse(ItemStack.EMPTY);
		super.read(tag);
	}

	/** Create shows these on the client when the pulse starts; here the server sends them. */
	private void spawnParticles() {
		BlockState blockState = getBlockState();
		if (!(level instanceof ServerLevel serverLevel) || !(blockState.getBlock() instanceof PlacardBlock))
			return;

		DustParticleOptions pParticleData = new DustParticleOptions(0xFF3300, 1);
		Vec3 centerOf = VecHelper.getCenterOf(worldPosition);
		Vec3 normal = Vec3.atLowerCornerOf(PlacardBlock.connectedDirection(blockState)
			.getUnitVec3i());
		Vec3 offset = VecHelper.axisAlingedPlaneOf(normal);

		for (int i = 0; i < 10; i++) {
			Vec3 v = VecHelper.offsetRandomly(Vec3.ZERO, level.getRandom(), .5f)
				.multiply(offset)
				.normalize()
				.scale(.45f)
				.add(normal.scale(-.45f))
				.add(centerOf);
			serverLevel.sendParticles(pParticleData, v.x, v.y, v.z, 1, 0, 0, 0, 0);
		}
	}

	/** The held item is dropped when the placard is broken (Create's PlacardBlock#onRemove). */
	@Override
	public void destroy() {
		super.destroy();
		if (level != null && !level.isClientSide() && !heldItem.isEmpty())
			Block.popResource(level, worldPosition, heldItem);
	}

}

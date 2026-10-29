package dev.createpolymer.kinetics.chainConveyor;

import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Create's ChainConveyorPackage. Create keeps the physics of the dangling box on the client, in a cache by net id,
 * because the client's copies of the packages are replaced on every sync. The server's package objects move between
 * the conveyors themselves, so the physics data lives in the package.
 */
public class ChainConveyorPackage {

	// Server creates unique ids for chain boxes
	public static final AtomicInteger netIdGenerator = new AtomicInteger();

	public static class ChainConveyorPackagePhysicsData {
		public Vec3 targetPos;
		public Vec3 prevTargetPos;
		public Vec3 prevPos;
		public Vec3 pos;

		public Vec3 motion;
		public long lastTick;
		public float yaw;
		public float prevYaw;
		public boolean flipped;

		public ChainConveyorPackagePhysicsData() {
			this.targetPos = null;
			this.prevTargetPos = null;
			this.pos = null;
			this.prevPos = null;

			this.motion = Vec3.ZERO;
			this.lastTick = Long.MIN_VALUE;
		}

		public boolean shouldTick(long gameTime) {
			if (lastTick == gameTime)
				return false;
			lastTick = gameTime;
			return true;
		}
	}

	public float chainPosition;
	public ItemStack item;
	public int netId;
	public boolean justFlipped;

	public Vec3 worldPosition;
	public float yaw;

	private ChainConveyorPackagePhysicsData physicsData;

	public ChainConveyorPackage(float chainPosition, ItemStack item) {
		this(chainPosition, item, netIdGenerator.incrementAndGet());
	}

	public ChainConveyorPackage(float chainPosition, ItemStack item, int netId) {
		this.chainPosition = chainPosition;
		this.item = item;
		this.netId = netId;
		this.physicsData = null;
	}

	public void write(ValueOutput output) {
		output.putFloat("Position", chainPosition);
		output.store("Item", ItemStack.OPTIONAL_CODEC, item);
	}

	public static ChainConveyorPackage read(ValueInput input) {
		float pos = input.getFloatOr("Position", 0);
		ItemStack item = input.read("Item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		return new ChainConveyorPackage(pos, item);
	}

	public ChainConveyorPackagePhysicsData physicsData() {
		if (physicsData == null)
			physicsData = new ChainConveyorPackagePhysicsData();
		return physicsData;
	}

	@Nullable
	public ChainConveyorPackagePhysicsData existingPhysicsData() {
		return physicsData;
	}

}

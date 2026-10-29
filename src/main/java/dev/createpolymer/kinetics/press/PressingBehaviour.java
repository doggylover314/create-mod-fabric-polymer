package dev.createpolymer.kinetics.press;

import java.util.ArrayList;
import java.util.List;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.BeltProcessingBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.processing.basin.BasinBlock;
import dev.createpolymer.registry.ModSounds;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PressingBehaviour extends BeltProcessingBehaviour {

	public static final int CYCLE = 240;
	public static final int ENTITY_SCAN = 10;

	public List<ItemStack> particleItems = new ArrayList<>();

	public PressingBehaviourSpecifics specifics;
	public int prevRunningTicks;
	public int runningTicks;
	public boolean running;
	public boolean finished;
	public Mode mode;

	int entityScanCooldown;

	public interface PressingBehaviourSpecifics {
		public boolean tryProcessInBasin(boolean simulate);

		public boolean tryProcessOnBelt(TransportedItemStack input, List<ItemStack> outputList, boolean simulate);

		public boolean tryProcessInWorld(ItemEntity itemEntity, boolean simulate);

		public boolean canProcessInBulk();

		public void onPressingCompleted();

		public int getParticleAmount();

		public float getKineticSpeed();
	}

	public <T extends SmartBlockEntity & PressingBehaviourSpecifics> PressingBehaviour(T be) {
		super(be);
		this.specifics = be;
		mode = Mode.WORLD;
		entityScanCooldown = ENTITY_SCAN;
		whenItemEnters((s, i) -> BeltPressingCallbacks.onItemReceived(s, i, this));
		whileItemHeld((s, i) -> BeltPressingCallbacks.whenItemHeld(s, i, this));
	}

	@Override
	public void read(ValueInput compound) {
		running = compound.getBooleanOr("Running", false);
		mode = Mode.values()[Mth.clamp(compound.getIntOr("Mode", 0), 0, Mode.values().length - 1)];
		finished = compound.getBooleanOr("Finished", false);
		prevRunningTicks = runningTicks = compound.getIntOr("Ticks", 0);
		super.read(compound);
	}

	@Override
	public void write(ValueOutput compound) {
		compound.putBoolean("Running", running);
		compound.putInt("Mode", mode.ordinal());
		compound.putBoolean("Finished", finished);
		compound.putInt("Ticks", runningTicks);
		super.write(compound);
	}

	public float getRenderedHeadOffset(float partialTicks) {
		if (!running)
			return 0;
		int runningTicks = Math.abs(this.runningTicks);
		float ticks = Mth.lerp(partialTicks, prevRunningTicks, runningTicks);
		if (runningTicks < (CYCLE * 2) / 3)
			return (float) Mth.clamp(Math.pow(ticks / CYCLE * 2, 3), 0, 1);
		return Mth.clamp((CYCLE - ticks) / CYCLE * 3, 0, 1);
	}

	public void start(Mode mode) {
		this.mode = mode;
		running = true;
		prevRunningTicks = 0;
		runningTicks = 0;
		particleItems.clear();
		blockEntity.sendData();
	}

	public boolean inWorld() {
		return mode == Mode.WORLD;
	}

	public boolean onBasin() {
		return mode == Mode.BASIN;
	}

	@Override
	public void tick() {
		super.tick();

		Level level = getWorld();
		BlockPos worldPosition = getPos();

		if (!running || level == null) {
			if (level != null && !level.isClientSide()) {

				if (specifics.getKineticSpeed() == 0)
					return;
				if (entityScanCooldown > 0)
					entityScanCooldown--;
				if (entityScanCooldown <= 0) {
					entityScanCooldown = ENTITY_SCAN;

					if (BlockEntityBehaviour.get(level, worldPosition.below(2),
						TransportedItemStackHandlerBehaviour.TYPE) != null)
						return;
					if (BasinBlock.isBasin(level, worldPosition.below(2)))
						return;

					for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class,
						new AABB(worldPosition.below()).deflate(.125f))) {
						if (!itemEntity.isAlive() || !itemEntity.onGround())
							continue;
						if (!specifics.tryProcessInWorld(itemEntity, true))
							continue;
						start(Mode.WORLD);
						return;
					}
				}

			}
			return;
		}

		if (runningTicks == CYCLE / 2 && specifics.getKineticSpeed() != 0) {
			if (inWorld())
				applyInWorld();
			if (onBasin())
				applyOnBasin();

			if (level.getBlockState(worldPosition.below(2))
				.getSoundType() == SoundType.WOOL)
				ModSounds.pressActivationOnBelt(level, worldPosition);
			else
				ModSounds.pressActivation(level, worldPosition, .5f,
					.75f + (Math.abs(specifics.getKineticSpeed()) / 1024f));

			if (!level.isClientSide())
				blockEntity.sendData();
		}

		if (!level.isClientSide() && runningTicks > CYCLE) {
			finished = true;
			running = false;
			particleItems.clear();
			specifics.onPressingCompleted();
			blockEntity.sendData();
			return;
		}

		prevRunningTicks = runningTicks;
		runningTicks += getRunningTickSpeed();
		if (prevRunningTicks < CYCLE / 2 && runningTicks >= CYCLE / 2) {
			runningTicks = CYCLE / 2;
		}
	}

	protected void applyOnBasin() {
		Level level = getWorld();
		if (level.isClientSide())
			return;
		particleItems.clear();
		if (specifics.tryProcessInBasin(false)) {
			blockEntity.sendData();
			spawnParticles();
		}
	}

	protected void applyInWorld() {
		Level level = getWorld();
		BlockPos worldPosition = getPos();
		AABB bb = new AABB(worldPosition.below(1));
		boolean bulk = specifics.canProcessInBulk();

		particleItems.clear();

		if (level.isClientSide())
			return;

		for (Entity entity : level.getEntities((Entity) null, bb)) {
			if (!(entity instanceof ItemEntity itemEntity))
				continue;
			if (!entity.isAlive() || !entity.onGround())
				continue;

			entityScanCooldown = 0;
			if (specifics.tryProcessInWorld(itemEntity, false)) {
				blockEntity.sendData();
				spawnParticles();
			}
			if (!bulk)
				break;
		}
	}

	public int getRunningTickSpeed() {
		float speed = specifics.getKineticSpeed();
		if (speed == 0)
			return 0;
		return (int) Mth.lerp(Mth.clamp(Math.abs(speed) / 512f, 0, 1), 1, 60);
	}

	/**
	 * Create ships {@link #particleItems} to the client with the next data packet, which then draws the particles.
	 * Here the server sends the particles itself, so call this right where Create would call sendData().
	 */
	public void spawnParticles() {
		if (particleItems.isEmpty())
			return;

		BlockPos worldPosition = getPos();

		if (mode == Mode.BASIN)
			particleItems
				.forEach(stack -> makeCompactingParticleEffect(VecHelper.getCenterOf(worldPosition.below(2)), stack));
		if (mode == Mode.BELT)
			particleItems.forEach(stack -> makePressingParticleEffect(VecHelper.getCenterOf(worldPosition.below(2))
				.add(0, 8 / 16f, 0), stack));
		if (mode == Mode.WORLD)
			particleItems.forEach(stack -> makePressingParticleEffect(VecHelper.getCenterOf(worldPosition.below(1))
				.add(0, -1 / 4f, 0), stack));

		particleItems.clear();
	}

	public void makePressingParticleEffect(Vec3 pos, ItemStack stack) {
		makePressingParticleEffect(pos, stack, specifics.getParticleAmount());
	}

	public void makePressingParticleEffect(Vec3 pos, ItemStack stack, int amount) {
		if (!(getWorld() instanceof ServerLevel level) || stack.isEmpty())
			return;
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()), pos.x, pos.y - .25f, pos.z,
			amount, .125f, .05, .125f, .125f);
	}

	public void makeCompactingParticleEffect(Vec3 pos, ItemStack stack) {
		if (!(getWorld() instanceof ServerLevel level) || stack.isEmpty())
			return;
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.getItem()), pos.x, pos.y, pos.z, 20,
			.175f, .05, .175f, .25f);
	}

	public enum Mode {
		WORLD(1), BELT(19f / 16f), BASIN(22f / 16f);

		public float headOffset;

		Mode(float headOffset) {
			this.headOffset = headOffset;
		}
	}

}

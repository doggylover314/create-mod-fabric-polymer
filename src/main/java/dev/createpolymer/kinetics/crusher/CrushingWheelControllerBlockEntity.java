package dev.createpolymer.kinetics.crusher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.processing.recipe.ProcessingInventory;
import dev.createpolymer.processing.recipe.ProcessingRecipe;
import dev.createpolymer.registry.feature.CrushingRegistry;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's CrushingWheelControllerBlockEntity (MIT, The Create Team).
 * <p>
 * Not ported: the DirectBeltInputBehaviour (belts are not part of this port yet), so the controller only hands its
 * results out as item entities, and the client-only ambience sound.
 */
public class CrushingWheelControllerBlockEntity extends SmartBlockEntity implements Clearable {
	/** Create keeps this block position in the item entity's persistent data; here it is a scoreboard tag. */
	private static final String BYPASS_TAG = "create:bypass_crushing_wheel/";

	public @Nullable Entity processingEntity;
	private @Nullable UUID entityUUID;
	protected boolean searchForEntity;

	public ProcessingInventory inventory;
	public float crushingspeed;

	public CrushingWheelControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		inventory = new ProcessingInventory(this::itemInserted) {

			@Override
			public boolean isItemValid(int slot, ItemStack stack) {
				return super.isItemValid(slot, stack) && processingEntity == null;
			}

		};
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(CrushingRegistry.CRUSHING_WHEEL_CONTROLLER_BLOCK_ENTITY, (be, side) -> be.inventory);
	}

	/** Marks an item that was just ejected from the crusher at pos so that it is not pulled back in. */
	public static void setBypass(Entity entity, BlockPos pos) {
		entity.entityTags().removeIf(tag -> tag.startsWith(BYPASS_TAG));
		entity.addTag(BYPASS_TAG + pos.getX() + "," + pos.getY() + "," + pos.getZ());
	}

	public static boolean isBypassing(Entity entity, BlockPos pos) {
		if (entity.entityTags().isEmpty())
			return false;
		return entity.entityTags().contains(BYPASS_TAG + pos.getX() + "," + pos.getY() + "," + pos.getZ());
	}

	@Override
	public void tick() {
		super.tick();
		if (searchForEntity) {
			searchForEntity = false;
			List<Entity> search = level.getEntities((Entity) null, new AABB(getBlockPos()),
				e -> entityUUID.equals(e.getUUID()));
			if (search.isEmpty())
				clear();
			else
				processingEntity = search.get(0);
		}

		if (!isOccupied())
			return;
		if (crushingspeed == 0)
			return;

		if (!(level instanceof ServerLevel serverLevel))
			return;

		float speed = crushingspeed * 4;

		Vec3 centerPos = VecHelper.getCenterOf(worldPosition);
		Direction facing = getBlockState().getValue(CrushingWheelControllerBlock.FACING);
		int offset = facing.getAxisDirection()
			.getStep();
		Vec3 outSpeed = new Vec3((facing.getAxis() == Axis.X ? 0.25D : 0.0D) * offset,
			offset == 1 ? (facing.getAxis() == Axis.Y ? 0.5D : 0.0D) : 0.0D // Increased upwards speed so upwards
			// crushing wheels shoot out the item
			// properly.
			, (facing.getAxis() == Axis.Z ? 0.25D : 0.0D) * offset); // No downwards speed, so downwards crushing wheels
		// drop the items as before.
		Vec3 outPos = centerPos.add((facing.getAxis() == Axis.X ? .55f * offset : 0f),
			(facing.getAxis() == Axis.Y ? .55f * offset : 0f), (facing.getAxis() == Axis.Z ? .55f * offset : 0f));

		if (!hasEntity()) {

			float processingSpeed =
				Mth.clamp((speed) / (!inventory.appliedRecipe ? (float) Math.log(inventory.getStackInSlot(0)
					.getCount()) / (float) Math.log(2) : 1), .25f, 20);
			inventory.remainingTime -= processingSpeed;
			spawnParticles(inventory.getStackInSlot(0));

			if (inventory.remainingTime < 20 && !inventory.appliedRecipe) {
				applyRecipe();
				inventory.appliedRecipe = true;
				notifyUpdate();
				return;
			}

			if (inventory.remainingTime > 0) {
				return;
			}
			inventory.remainingTime = 0;

			// Eject Items
			for (int slot = 0; slot < inventory.getSlots(); slot++) {
				ItemStack stack = inventory.getStackInSlot(slot);
				if (stack.isEmpty())
					continue;
				ItemEntity entityIn = new ItemEntity(level, outPos.x, outPos.y, outPos.z, stack);
				entityIn.setDeltaMovement(outSpeed);
				setBypass(entityIn, worldPosition);
				level.addFreshEntity(entityIn);
			}
			inventory.clear();
			notifyUpdate();

			return;
		}

		if (!processingEntity.isAlive() || !processingEntity.getBoundingBox()
			.intersects(new AABB(worldPosition).inflate(.5f))) {
			clear();
			return;
		}

		double xMotion = ((worldPosition.getX() + .5f) - processingEntity.getX()) / 2f;
		double zMotion = ((worldPosition.getZ() + .5f) - processingEntity.getZ()) / 2f;
		if (processingEntity.isShiftKeyDown())
			xMotion = zMotion = 0;
		double movement = Math.max(-speed / 4f, -.5f) * -offset;
		processingEntity.setDeltaMovement(
			new Vec3(facing.getAxis() == Axis.X ? movement : xMotion, facing.getAxis() == Axis.Y ? movement : 0f // Do
				// not
				// move
				// entities
				// upwards
				// or
				// downwards
				// for
				// horizontal
				// crushers,
				, facing.getAxis() == Axis.Z ? movement : zMotion)); // Or they'll only get their feet crushed.
		if (processingEntity instanceof ServerPlayer)
			processingEntity.syncVelocity = true;

		if (!(processingEntity instanceof ItemEntity itemEntity)) {
			Vec3 entityOutPos = outPos.add(facing.getAxis() == Axis.X ? .5f * offset : 0f,
				facing.getAxis() == Axis.Y ? .5f * offset : 0f, facing.getAxis() == Axis.Z ? .5f * offset : 0f);
			int crusherDamage = CrushingRegistry.crushingDamage;

			if (processingEntity instanceof LivingEntity livingEntity) {
				if (livingEntity.getHealth() - crusherDamage <= 0 // Takes LivingEntity instances
					// as exception, so it can
					// move them before it would
					// kill them.
					&& livingEntity.hurtTime <= 0) { // This way it can actually output the items
					// to the right spot.
					processingEntity.setPos(entityOutPos.x, entityOutPos.y, entityOutPos.z);
				}
			}
			processingEntity.hurtServer(serverLevel, CrushingRegistry.crushDamage(level), crusherDamage);
			if (!processingEntity.isAlive()) {
				processingEntity.setPos(entityOutPos.x, entityOutPos.y, entityOutPos.z);
			}
			return;
		}

		itemEntity.setPickUpDelay(20);
		if (facing.getAxis() == Axis.Y) {
			if (processingEntity.getY() * -offset < (centerPos.y - .25f) * -offset) {
				intakeItem(itemEntity);
			}
		} else if (facing.getAxis() == Axis.Z) {
			if (processingEntity.getZ() * -offset < (centerPos.z - .25f) * -offset) {
				intakeItem(itemEntity);
			}
		} else {
			if (processingEntity.getX() * -offset < (centerPos.x - .25f) * -offset) {
				intakeItem(itemEntity);
			}
		}
	}

	private void intakeItem(ItemEntity itemEntity) {
		inventory.clear();
		inventory.setStackInSlot(0, itemEntity.getItem()
			.copy());
		itemInserted(inventory.getStackInSlot(0));
		itemEntity.discard();
		notifyUpdate();
	}

	protected void spawnParticles(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(level instanceof ServerLevel serverLevel))
			return;

		ParticleOptions particleData = null;
		if (stack.getItem() instanceof BlockItem)
			particleData = new BlockParticleOption(ParticleTypes.BLOCK, ((BlockItem) stack.getItem()).getBlock()
				.defaultBlockState());
		else
			particleData = new ItemParticleOption(ParticleTypes.ITEM, stack.getItem());

		RandomSource r = level.getRandom();
		for (int i = 0; i < 4; i++)
			serverLevel.sendParticles(particleData, worldPosition.getX() + r.nextFloat(), worldPosition.getY() + r.nextFloat(),
				worldPosition.getZ() + r.nextFloat(), 0, 0, 0, 0, 1);
	}

	private void applyRecipe() {
		Optional<ProcessingRecipe<SingleRecipeInput>> recipe = findRecipe();

		List<ItemStack> list = new ArrayList<>();
		if (recipe.isPresent()) {
			ItemStack input = inventory.getStackInSlot(0);
			int rolls = input.getCount();
			inventory.clear();
			for (int roll = 0; roll < rolls; roll++) {
				List<ItemStack> rolledResults = recipe.get()
					.rollResults(level.getRandom());
				for (ItemStack stack : rolledResults) {
					addToList(stack, list);
				}
			}
			ItemStackTemplate remainder = input.getItem().getCraftingRemainder();
			if (remainder != null) {
				addToList(remainder.create(), list);
			}
			for (int slot = 0; slot < list.size() && slot + 1 < inventory.getSlots(); slot++)
				inventory.setStackInSlot(slot + 1, list.get(slot));
		} else {
			inventory.clear();
		}

	}

	/** Create's ItemHelper.addToList */
	private static void addToList(ItemStack stack, List<ItemStack> stacks) {
		for (ItemStack s : stacks) {
			if (!ItemStack.isSameItemSameComponents(stack, s))
				continue;
			int transferred = Math.min(s.getMaxStackSize() - s.getCount(), stack.getCount());
			s.grow(transferred);
			stack.shrink(transferred);
		}
		if (stack.getCount() > 0)
			stacks.add(stack);
	}

	/** The crushing recipe for the input, or Create's fallback, the milling recipe. */
	public Optional<ProcessingRecipe<SingleRecipeInput>> findRecipe() {
		if (!(level instanceof ServerLevel serverLevel))
			return Optional.empty();
		SingleRecipeInput input = new SingleRecipeInput(inventory.getStackInSlot(0));
		Optional<ProcessingRecipe<SingleRecipeInput>> crushingRecipe = serverLevel.recipeAccess()
			.getRecipeFor(CrushingRegistry.CRUSHING, input, serverLevel)
			.<ProcessingRecipe<SingleRecipeInput>>map(RecipeHolder::value);
		if (crushingRecipe.isEmpty())
			crushingRecipe = serverLevel.recipeAccess()
				.getRecipeFor(dev.createpolymer.registry.ModRecipeTypes.MILLING, input, serverLevel)
				.<ProcessingRecipe<SingleRecipeInput>>map(RecipeHolder::value);
		return crushingRecipe;
	}

	@Override
	protected void write(ValueOutput compound) {
		if (hasEntity())
			compound.store("Entity", UUIDUtil.CODEC, entityUUID);
		inventory.serialize(compound.child("Inventory"));
		compound.putFloat("Speed", crushingspeed);
		super.write(compound);
	}

	@Override
	protected void read(ValueInput compound) {
		super.read(compound);
		Optional<UUID> uuid = compound.read("Entity", UUIDUtil.CODEC);
		if (uuid.isPresent() && !isOccupied()) {
			entityUUID = uuid.get();
			this.searchForEntity = true;
		}
		crushingspeed = compound.getFloatOr("Speed", 0);
		inventory.deserialize(compound.childOrEmpty("Inventory"));
	}

	@Override
	public void destroy() {
		super.destroy();
		for (int slot = 0; slot < inventory.getSlots(); slot++)
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
				inventory.getStackInSlot(slot));
	}

	@Override
	public void clearContent() {
		inventory.clear();
	}

	public void startCrushing(Entity entity) {
		processingEntity = entity;
		entityUUID = entity.getUUID();
	}

	private void itemInserted(ItemStack stack) {
		Optional<ProcessingRecipe<SingleRecipeInput>> recipe = findRecipe();
		inventory.remainingTime = recipe.isPresent() ? recipe.get()
			.getProcessingDuration() : 100;
		inventory.appliedRecipe = false;
	}

	public void clear() {
		processingEntity = null;
		entityUUID = null;
	}

	public boolean isOccupied() {
		return hasEntity() || !inventory.isEmpty();
	}

	public boolean hasEntity() {
		return processingEntity != null;
	}

	/** The first stack inside, for the display entity. */
	public ItemStack getDisplayedItem() {
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if (!stack.isEmpty())
				return stack;
		}
		return ItemStack.EMPTY;
	}
}

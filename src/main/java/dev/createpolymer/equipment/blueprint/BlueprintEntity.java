package dev.createpolymer.equipment.blueprint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.foundation.item.handler.InvWrapper;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.registry.ModItems;
import dev.createpolymer.registry.feature.ToolsRegistry;
import dev.createpolymer.util.Couple;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.NonNullList;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Create's crafting blueprint: a flat 1x1, 2x2 or 3x3 panel on a wall, floor or ceiling, each square of which holds a
 * recipe. Right-click on a square with a recipe crafts it from the player's inventory (sneak crafts a stack),
 * right-click on an empty square (or with a wrench) opens the recipe editor ({@link BlueprintGui}), and hitting a
 * square with a recipe clears it.
 * <p>
 * Create's entity is a HangingEntity whose facing may be up or down; the horizontal-only HangingEntity of Minecraft
 * 26.3 cannot do that, so this is a {@link BlockAttachedEntity} that keeps the facing itself. Players are not told
 * about the entity: {@link BlueprintModel} draws the panel and the recipes' icons with display entities and catches
 * clicks with interaction entities, which hand them to this entity.
 */
public class BlueprintEntity extends BlockAttachedEntity implements PolymerEntity {

	private static final Codec<Map<String, List<ItemStack>>> RECIPES_CODEC =
		Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf());

	protected int size;
	protected Direction direction = Direction.SOUTH;
	protected Direction verticalOrientation = Direction.DOWN;

	/** The recipes by square, as Create keeps them in the entity's persistent data ("Recipes"). */
	private final Map<Integer, List<ItemStack>> recipes = new HashMap<>();
	private boolean inferredIcon;
	private int dataVersion;
	private Map<Integer, BlueprintSection> sectionCache = new HashMap<>();
	private @Nullable BlueprintModel model;

	public BlueprintEntity(EntityType<?> type, Level level) {
		super(castType(type), level);
		size = 1;
	}

	@SuppressWarnings("unchecked")
	private static EntityType<? extends BlockAttachedEntity> castType(EntityType<?> type) {
		return (EntityType<? extends BlockAttachedEntity>) type;
	}

	public BlueprintEntity(EntityType<?> type, Level world, BlockPos pos, Direction facing, Direction verticalOrientation) {
		super(castType(type), world, pos);

		for (int size = 3; size > 0; size--) {
			this.size = size;
			this.updateFacingWithBoundingBox(facing, verticalOrientation);
			if (this.survives())
				break;
		}
	}

	public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
		@SuppressWarnings("unchecked")
		EntityType.Builder<BlueprintEntity> entityBuilder = (EntityType.Builder<BlueprintEntity>) builder;
		return entityBuilder.sized(0.5f, 0.5f)
			.clientTrackingRange(10)
			.updateInterval(Integer.MAX_VALUE)
			.fireImmune();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {}

	public int getSize() {
		return size;
	}

	public Direction getFacing() {
		return direction;
	}

	public Direction getVerticalOrientation() {
		return verticalOrientation;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("Facing", this.direction.get3DDataValue());
		output.putInt("Orientation", this.verticalOrientation.get3DDataValue());
		output.putInt("Size", size);
		Map<String, List<ItemStack>> saved = new HashMap<>();
		recipes.forEach((index, items) -> saved.put(String.valueOf(index), items));
		output.store("Recipes", RECIPES_CODEC, saved);
		output.putBoolean("InferredIcon", inferredIcon);
		super.addAdditionalSaveData(output);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		if (input.getInt("Facing")
			.isPresent()) {
			this.direction = Direction.from3DDataValue(input.getIntOr("Facing", 3));
			this.verticalOrientation = Direction.from3DDataValue(input.getIntOr("Orientation", 0));
			this.size = input.getIntOr("Size", 1);
		} else {
			this.direction = Direction.SOUTH;
			this.verticalOrientation = Direction.DOWN;
			this.size = 1;
		}
		recipes.clear();
		input.read("Recipes", RECIPES_CODEC)
			.ifPresent(saved -> saved.forEach((index, items) -> recipes.put(Integer.parseInt(index), items)));
		inferredIcon = input.getBooleanOr("InferredIcon", false);
		sectionCache.clear();
		super.readAdditionalSaveData(input);
		this.updateFacingWithBoundingBox(this.direction, this.verticalOrientation);
	}

	protected void updateFacingWithBoundingBox(Direction facing, Direction verticalOrientation) {
		Objects.requireNonNull(facing);
		this.direction = facing;
		this.verticalOrientation = verticalOrientation;
		if (facing.getAxis()
			.isHorizontal()) {
			setXRot(0.0F);
			setYRot(this.direction.get2DDataValue() * 90);
		} else {
			setXRot(-90 * facing.getAxisDirection()
				.getStep());
			setYRot(verticalOrientation.getAxis()
				.isHorizontal() ? 180 + verticalOrientation.toYRot() : 0);
		}

		this.xRotO = getXRot();
		this.yRotO = getYRot();
		this.recalculateBoundingBox();
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return super.getDimensions(pose).withEyeHeight(0);
	}

	protected AABB calculateBoundingBox(BlockPos blockPos, Direction direction) {
		Vec3 pos = Vec3.atLowerCornerOf(getPos())
				.add(.5, .5, .5)
				.subtract(Vec3.atLowerCornerOf(direction.getUnitVec3i())
						.scale(0.46875));
		double d1 = pos.x;
		double d2 = pos.y;
		double d3 = pos.z;
		this.setPosRaw(d1, d2, d3);

		Axis axis = direction.getAxis();
		if (size == 2)
			pos = pos.add(Vec3.atLowerCornerOf(axis.isHorizontal() ? direction.getCounterClockWise()
									.getUnitVec3i()
									: verticalOrientation.getClockWise()
									.getUnitVec3i())
							.scale(0.5))
					.add(Vec3
							.atLowerCornerOf(axis.isHorizontal() ? Direction.UP.getUnitVec3i()
									: direction == Direction.UP ? verticalOrientation.getUnitVec3i()
									: verticalOrientation.getOpposite()
									.getUnitVec3i())
							.scale(0.5));

		d1 = pos.x;
		d2 = pos.y;
		d3 = pos.z;

		double d4 = (double) this.getWidth();
		double d5 = (double) this.getHeight();
		double d6 = (double) this.getWidth();
		Direction.Axis direction$axis = this.direction.getAxis();
		switch (direction$axis) {
			case X:
				d4 = 1.0D;
				break;
			case Y:
				d5 = 1.0D;
				break;
			case Z:
				d6 = 1.0D;
		}

		d4 = d4 / 32.0D;
		d5 = d5 / 32.0D;
		d6 = d6 / 32.0D;

		return new AABB(d1 - d4, d2 - d5, d3 - d6, d1 + d4, d2 + d5, d3 + d6);
	}

	@Override
	protected void recalculateBoundingBox() {
		if (this.direction != null && this.verticalOrientation != null && this.pos != null) {
			setBoundingBox(calculateBoundingBox(pos, direction));
		}
	}

	@Override
	public void setPos(double pX, double pY, double pZ) {
		setPosRaw(pX, pY, pZ);
		super.setPos(pX, pY, pZ);
	}

	@Override
	public boolean survives() {
		if (!level().noCollision(this))
			return false;

		int i = Math.max(1, this.getWidth() / 16);
		int j = Math.max(1, this.getHeight() / 16);
		BlockPos blockpos = this.pos.relative(this.direction.getOpposite());
		Direction upDirection = direction.getAxis()
			.isHorizontal() ? Direction.UP
			: direction == Direction.UP ? verticalOrientation : verticalOrientation.getOpposite();
		Direction newDirection = direction.getAxis()
			.isVertical() ? verticalOrientation.getClockWise() : direction.getCounterClockWise();
		BlockPos.MutableBlockPos blockpos$mutable = new BlockPos.MutableBlockPos();

		for (int k = 0; k < i; ++k) {
			for (int l = 0; l < j; ++l) {
				int i1 = (i - 1) / -2;
				int j1 = (j - 1) / -2;
				blockpos$mutable.set(blockpos)
					.move(newDirection, k + i1)
					.move(upDirection, l + j1);
				BlockState blockstate = this.level().getBlockState(blockpos$mutable);
				if (Block.canSupportCenter(this.level(), blockpos$mutable, this.direction))
					continue;
				if (!blockstate.isSolid() && !DiodeBlock.isDiode(blockstate)) {
					return false;
				}
			}
		}

		return this.level().getEntities(this, this.getBoundingBox(), e -> e instanceof BlockAttachedEntity)
			.isEmpty();
	}

	public int getWidth() {
		return 16 * size;
	}

	public int getHeight() {
		return 16 * size;
	}

	@Override
	public boolean skipAttackInteraction(Entity source) {
		if (!(source instanceof Player player) || level().isClientSide())
			return super.skipAttackInteraction(source);

		double attrib = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + (player.isCreative() ? 0 : -0.5F);

		Vec3 eyePos = source.getEyePosition(1);
		Vec3 look = source.getViewVector(1);
		Vec3 target = eyePos.add(look.scale(attrib));

		Optional<Vec3> rayTrace = getBoundingBox().clip(eyePos, target);
		if (!rayTrace.isPresent())
			return super.skipAttackInteraction(source);

		Vec3 hitVec = rayTrace.get();
		BlueprintSection sectionAt = getSectionAt(hitVec.subtract(position()));
		ItemStackHandler items = sectionAt.getItems();

		if (items.getStackInSlot(9)
			.isEmpty())
			return super.skipAttackInteraction(source);
		for (int i = 0; i < items.getSlots(); i++)
			items.setStackInSlot(i, ItemStack.EMPTY);
		sectionAt.save(items);
		return true;
	}

	@Override
	public void dropItem(ServerLevel level, @Nullable Entity causedBy) {
		if (!level.getGameRules()
			.get(GameRules.ENTITY_DROPS))
			return;

		playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
		if (causedBy instanceof Player playerentity) {
			if (playerentity.getAbilities().instabuild)
				return;
		}

		spawnAtLocation(level, new ItemStack(ToolsRegistry.CRAFTING_BLUEPRINT));
	}

	@Override
	public @Nullable ItemStack getPickResult() {
		return new ItemStack(ToolsRegistry.CRAFTING_BLUEPRINT);
	}

	public void playPlacementSound() {
		this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
	}

	/**
	 * Create's interactAt. {@code location} is the spot that was hit, relative to this entity; it is found again by
	 * tracing the player's view, because the interaction entities that catch the click are not at this entity's position.
	 */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player instanceof FakePlayer)
			return InteractionResult.PASS;

		Vec3 vec = traceHit(player, location);
		return interactAt(player, vec, hand);
	}

	private Vec3 traceHit(Player player, Vec3 fallback) {
		double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
		Vec3 eyePos = player.getEyePosition(1);
		Vec3 target = eyePos.add(player.getViewVector(1)
			.scale(reach));
		Optional<Vec3> hit = getBoundingBox().inflate(.01)
			.clip(eyePos, target);
		return hit.map(h -> h.subtract(position()))
			.orElse(fallback);
	}

	public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
		boolean holdingWrench = player.getItemInHand(hand)
			.is(ModItems.WRENCH);
		BlueprintSection section = getSectionAt(vec);
		ItemStackHandler items = section.getItems();

		if (!holdingWrench && !level().isClientSide() && !items.getStackInSlot(9)
			.isEmpty()) {

			IItemHandlerModifiable playerInv = new InvWrapper(player.getInventory());
			boolean firstPass = true;
			int amountCrafted = 0;
			Optional<RecipeHolder<CraftingRecipe>> recipe = Optional.empty();
			ServerLevel serverLevel = (ServerLevel) level();

			do {
				Map<Integer, ItemStack> stacksTaken = new HashMap<>();
				Map<Integer, ItemStack> craftingGrid = new HashMap<>();
				boolean success = true;

				Search:
				for (int i = 0; i < 9; i++) {
					FilterItemStack requestedItem = FilterItemStack.of(items.getStackInSlot(i));
					if (requestedItem.isEmpty()) {
						craftingGrid.put(i, ItemStack.EMPTY);
						continue;
					}

					for (int slot = 0; slot < playerInv.getSlots(); slot++) {
						if (!requestedItem.test(level(), playerInv.getStackInSlot(slot)))
							continue;
						ItemStack currentItem = playerInv.extractItem(slot, 1, false);
						if (stacksTaken.containsKey(slot))
							stacksTaken.get(slot)
								.grow(1);
						else
							stacksTaken.put(slot, currentItem.copy());
						craftingGrid.put(i, currentItem);
						continue Search;
					}

					success = false;
					break;
				}

				if (success) {
					CraftingInput craftInput = craftingInput(craftingGrid);

					if (!recipe.isPresent())
						recipe = serverLevel.recipeAccess()
							.getRecipeFor(RecipeType.CRAFTING, craftInput, level());
					ItemStack result = recipe.filter(r -> r.value().matches(craftInput, level()))
						.map(r -> r.value().assemble(craftInput))
						.orElse(ItemStack.EMPTY);

					if (result.isEmpty()) {
						success = false;
					} else if (result.getCount() + amountCrafted > 64) {
						success = false;
					} else {
						amountCrafted += result.getCount();
						result.onCraftedBy(player, 1);
						NonNullList<ItemStack> nonnulllist = recipe.get()
							.value()
							.getRemainingItems(craftInput);

						if (firstPass)
							level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
								.2f, 1f + level().getRandom().nextFloat());
						player.getInventory()
							.placeItemBackInInventory(result, Prediction.SERVER_ONLY);
						for (ItemStack itemStack : nonnulllist)
							player.getInventory()
								.placeItemBackInInventory(itemStack, Prediction.SERVER_ONLY);
						firstPass = false;
					}
				}

				if (!success) {
					for (Entry<Integer, ItemStack> entry : stacksTaken.entrySet())
						playerInv.insertItem(entry.getKey(), entry.getValue(), false);
					break;
				}

			} while (player.isShiftKeyDown());
			return InteractionResult.SUCCESS;
		}

		if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer)
			new BlueprintGui(serverPlayer, section).open();

		return InteractionResult.SUCCESS;
	}

	/** The 3x3 grid of the crafted ingredients, as Create's BlueprintCraftingInventory. */
	static CraftingInput craftingInput(Map<Integer, ItemStack> items) {
		NonNullList<ItemStack> list = NonNullList.withSize(9, ItemStack.EMPTY);
		for (int i = 0; i < 9; i++) {
			ItemStack stack = items.get(i);
			list.set(i, stack == null ? ItemStack.EMPTY : stack.copy());
		}
		return CraftingInput.of(3, 3, list);
	}

	public BlueprintSection getSectionAt(Vec3 vec) {
		int index = 0;
		if (size > 1) {
			vec = VecHelper.rotate(vec, getYRot(), Axis.Y);
			vec = VecHelper.rotate(vec, -getXRot(), Axis.X);
			vec = vec.add(0.5, 0.5, 0);
			if (size == 3)
				vec = vec.add(1, 1, 0);
			int x = Mth.clamp(Mth.floor(vec.x), 0, size - 1);
			int y = Mth.clamp(Mth.floor(vec.y), 0, size - 1);
			index = x + y * size;
		}

		return getSection(index);
	}

	public BlueprintSection getSection(int index) {
		return sectionCache.computeIfAbsent(index, i -> new BlueprintSection(i));
	}

	/** Bumped whenever a recipe changes, so the model knows to redraw the icons. */
	public int getDataVersion() {
		return dataVersion;
	}

	public boolean isInferredIcon() {
		return inferredIcon;
	}

	public class BlueprintSection {
		int index;
		Couple<ItemStack> cachedDisplayItems;

		public BlueprintSection(int index) {
			this.index = index;
		}

		public int getIndex() {
			return index;
		}

		public BlueprintEntity getBlueprint() {
			return BlueprintEntity.this;
		}

		public boolean isInferredIcon() {
			return inferredIcon;
		}

		public void setInferredIcon(boolean inferred) {
			inferredIcon = inferred;
		}

		public Couple<ItemStack> getDisplayItems() {
			if (cachedDisplayItems != null)
				return cachedDisplayItems;
			ItemStackHandler items = getItems();
			return cachedDisplayItems = Couple.create(items.getStackInSlot(9), items.getStackInSlot(10));
		}

		public ItemStackHandler getItems() {
			ItemStackHandler newInv = new ItemStackHandler(11);
			List<ItemStack> saved = recipes.get(index);
			if (saved != null)
				for (int i = 0; i < saved.size() && i < newInv.getSlots(); i++)
					newInv.setStackInSlot(i, saved.get(i)
						.copy());
			return newInv;
		}

		public void save(ItemStackHandler inventory) {
			List<ItemStack> items = new java.util.ArrayList<>();
			boolean any = false;
			for (int i = 0; i < inventory.getSlots(); i++) {
				ItemStack stack = inventory.getStackInSlot(i)
					.copy();
				any |= !stack.isEmpty();
				items.add(stack);
			}
			if (any)
				recipes.put(index, items);
			else
				recipes.remove(index);
			cachedDisplayItems = null;
			dataVersion++;
		}

		public boolean isEntityAlive() {
			return isAlive();
		}

		public Level getBlueprintWorld() {
			return level();
		}

		public boolean canPlayerUse(Player player) {
			return BlueprintEntity.this.canPlayerUse(player);
		}
	}

	public boolean canPlayerUse(Player player) {
		return player.isWithinEntityInteractionRange(this, 8);
	}

	@Override
	public void tick() {
		super.tick();
		if (model == null && level() instanceof ServerLevel && !isRemoved()) {
			model = new BlueprintModel(this);
			EntityAttachment.ofTicking(model, this);
		}
	}

	public @Nullable BlueprintModel getModel() {
		return model;
	}

	// ---- Polymer ----

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.MARKER;
	}

	/** Players only see the display and interaction entities of the {@link BlueprintModel}. */
	@Override
	public boolean sendPacketsTo(ServerPlayer player) {
		return false;
	}
}

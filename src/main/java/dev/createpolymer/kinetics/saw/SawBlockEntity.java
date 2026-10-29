package dev.createpolymer.kinetics.saw;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.kinetics.base.BlockBreakingKineticBlockEntity;
import dev.createpolymer.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import dev.createpolymer.processing.basin.RecipeCache;
import dev.createpolymer.processing.recipe.CuttingRecipe;
import dev.createpolymer.processing.recipe.ProcessingInventory;
import dev.createpolymer.registry.feature.SawRegistry;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's SawBlockEntity (MIT, The Create Team). Horizontal saws break the block in front of them
 * (and fell trees); upward saws cut the items they are given. Not ported: packages, sequenced assembly and belt
 * funnels (none of them exist in this port yet).
 */
public class SawBlockEntity extends BlockBreakingKineticBlockEntity implements Clearable {
	private static final Object cuttingRecipesKey = new Object();

	public ProcessingInventory inventory;
	private int recipeIndex;
	private FilteringBehaviour filtering;

	public SawBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		inventory = new ProcessingInventory(this::start).withSlotLimit(!SawRegistry.bulkCutting);
		inventory.remainingTime = -1;
		recipeIndex = 0;
	}

	public static void registerCapabilities() {
		ItemHandlerLookup.registerBlockEntity(SawRegistry.SAW, (be, side) -> {
			if (side != Direction.DOWN)
				return be.inventory;
			return null;
		});
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		filtering = new FilteringBehaviour(this, new SawFilterSlot()).forRecipes();
		behaviours.add(filtering);
		behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnelsWhen(this::canProcess));
	}

	@Override
	public void write(ValueOutput compound) {
		inventory.serialize(compound.child("Inventory"));
		compound.putInt("RecipeIndex", recipeIndex);
		super.write(compound);
	}

	@Override
	public void read(ValueInput compound) {
		super.read(compound);
		inventory.deserialize(compound.childOrEmpty("Inventory"));
		recipeIndex = compound.getIntOr("RecipeIndex", 0);
	}

	@Override
	public void tick() {
		if (shouldRun() && ticksUntilNextProgress < 0)
			destroyNextTick();
		super.tick();

		if (level == null || level.isClientSide())
			return;

		if (!canProcess())
			return;
		if (getSpeed() == 0)
			return;
		if (inventory.remainingTime == -1) {
			if (!inventory.isEmpty() && !inventory.appliedRecipe)
				start(inventory.getStackInSlot(0));
			return;
		}

		float processingSpeed = Mth.clamp(Math.abs(getSpeed()) / 24, 1, 128);
		inventory.remainingTime -= processingSpeed;

		if (inventory.remainingTime > 0 && level.getGameTime() % 2 == 0)
			spawnParticles(inventory.getStackInSlot(0));

		if (inventory.remainingTime < 5 && !inventory.appliedRecipe) {
			ItemStack cut = inventory.getStackInSlot(0);
			applyRecipe();
			playActivationEffects(cut);
			inventory.appliedRecipe = true;
			inventory.recipeDuration = 20;
			inventory.remainingTime = 20;
			sendData();
			return;
		}

		Vec3 itemMovement = getItemMovementVec();
		Direction itemMovementFacing = Direction.getApproximateNearest(itemMovement.x, itemMovement.y, itemMovement.z);
		if (inventory.remainingTime > 0)
			return;
		inventory.remainingTime = 0;

		BlockPos nextPos = worldPosition.offset(BlockPos.containing(itemMovement));
		DirectBeltInputBehaviour behaviour = BlockEntityBehaviour.get(level, nextPos, DirectBeltInputBehaviour.TYPE);
		if (behaviour != null) {
			boolean changed = false;
			if (!behaviour.canInsertFromSide(itemMovementFacing))
				return;
			for (int slot = 0; slot < inventory.getSlots(); slot++) {
				ItemStack stack = inventory.getStackInSlot(slot);
				if (stack.isEmpty())
					continue;
				ItemStack remainder = behaviour.handleInsertion(stack, itemMovementFacing, false);
				if (ItemStack.matches(remainder, stack))
					continue;
				inventory.setStackInSlot(slot, remainder);
				changed = true;
			}
			if (changed) {
				setChanged();
				sendData();
			}
			return;
		}

		// Eject Items
		Vec3 outPos = VecHelper.getCenterOf(worldPosition)
			.add(itemMovement.scale(.5f)
				.add(0, .5, 0));
		Vec3 outMotion = itemMovement.scale(.0625)
			.add(0, .125, 0);
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if (stack.isEmpty())
				continue;
			ItemEntity entityIn = new ItemEntity(level, outPos.x, outPos.y, outPos.z, stack);
			entityIn.setDeltaMovement(outMotion);
			level.addFreshEntity(entityIn);
		}
		inventory.clear();
		level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
		inventory.remainingTime = -1;
		sendData();
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (level != null && canProcess() && inventory.isEmpty())
			collectItemEntities();
	}

	/**
	 * Create's SawBlock.updateEntityAfterFallOn runs whenever an item entity rests on the saw; vanilla only
	 * calls fallOn when the entity actually fell, so items that were placed or pushed onto the saw are
	 * picked up here.
	 */
	private void collectItemEntities() {
		AABB box = new AABB(worldPosition.above()).inflate(-.05, 0, -.05).move(0, -.3, 0);
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, box)) {
			if (getSpeed() == 0 || !inventory.isEmpty())
				return;
			insertItem(itemEntity);
		}
	}

	@Override
	public void clearContent() {
		inventory.clear();
		filtering.setFilter(ItemStack.EMPTY);
	}

	@Override
	public void destroy() {
		super.destroy();
		ItemHelper.dropContents(level, worldPosition, inventory);
	}

	protected void playActivationEffects(ItemStack stack) {
		if (!(level instanceof ServerLevel serverLevel) || stack.isEmpty())
			return;
		boolean isWood = false;
		if (stack.getItem() instanceof BlockItem blockItem)
			isWood = blockItem.getBlock()
				.defaultBlockState()
				.getSoundType() == SoundType.WOOD;
		spawnEventParticles(serverLevel, stack);
		// Create's saw_activate_stone / saw_activate_wood sound events
		if (!isWood)
			level.playSound(null, worldPosition, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 3 * .125f, 1.25f);
		else
			level.playSound(null, worldPosition, SoundEvents.BOAT_PADDLE_LAND, SoundSource.BLOCKS, 3 * .75f, 1.5f);
	}

	private static ParticleOptions particleFor(ItemStack stack) {
		if (stack.getItem() instanceof BlockItem blockItem)
			return new BlockParticleOption(ParticleTypes.BLOCK, blockItem.getBlock()
				.defaultBlockState());
		return new ItemParticleOption(ParticleTypes.ITEM, stack.getItem());
	}

	protected void spawnEventParticles(ServerLevel serverLevel, ItemStack stack) {
		if (stack == null || stack.isEmpty())
			return;

		ParticleOptions particleData = particleFor(stack);

		RandomSource r = level.getRandom();
		Vec3 v = VecHelper.getCenterOf(this.worldPosition)
			.add(0, 5 / 16f, 0);
		for (int i = 0; i < 10; i++) {
			Vec3 m = VecHelper.offsetRandomly(new Vec3(0, 0.25f, 0), r, .125f);
			serverLevel.sendParticles(particleData, v.x, v.y, v.z, 0, m.x, m.y, m.y, 1);
		}
	}

	protected void spawnParticles(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(level instanceof ServerLevel serverLevel))
			return;

		ParticleOptions particleData = particleFor(stack);
		float speed = 1;
		if (!(stack.getItem() instanceof BlockItem))
			speed = .125f;

		RandomSource r = level.getRandom();
		Vec3 vec = getItemMovementVec();
		Vec3 pos = VecHelper.getCenterOf(this.worldPosition);
		float offset = inventory.recipeDuration != 0 ? (float) (inventory.remainingTime) / inventory.recipeDuration : 0;
		offset /= 2;
		if (inventory.appliedRecipe)
			offset -= .5f;
		serverLevel.sendParticles(particleData, pos.x() + -vec.x * offset, pos.y() + .45f, pos.z() + -vec.z * offset, 0,
			-vec.x * speed, r.nextFloat() * speed, -vec.z * speed, 1);
	}

	public Vec3 getItemMovementVec() {
		boolean alongX = !getBlockState().getValue(SawBlock.AXIS_ALONG_FIRST_COORDINATE);
		int offset = getSpeed() < 0 ? -1 : 1;
		return new Vec3(offset * (alongX ? 1 : 0), 0, offset * (alongX ? 0 : -1));
	}

	private void applyRecipe() {
		ItemStack input = inventory.getStackInSlot(0);
		List<ItemStack> list = new ArrayList<>();

		List<Recipe<SingleRecipeInput>> recipes = getRecipes();
		if (recipes.isEmpty())
			return;
		if (recipeIndex >= recipes.size())
			recipeIndex = 0;

		Recipe<SingleRecipeInput> recipe = recipes.get(recipeIndex);

		int rolls = input.getCount();
		inventory.clear();

		for (int roll = 0; roll < rolls; roll++) {
			List<ItemStack> results = new LinkedList<>();
			if (recipe instanceof CuttingRecipe cutting)
				results = cutting.rollResults(level.getRandom());
			else if (recipe instanceof StonecutterRecipe)
				results.add(recipe.assemble(new SingleRecipeInput(input.copyWithCount(1))));

			for (ItemStack stack : results) {
				ItemHelper.addToList(stack, list);
			}
			ItemStackTemplate remainder = input.getItem()
				.getCraftingRemainder();
			if (remainder != null)
				ItemHelper.addToList(remainder.create(), list);
		}

		for (int slot = 0; slot < list.size() && slot + 1 < inventory.getSlots(); slot++)
			inventory.setStackInSlot(slot + 1, list.get(slot));
	}

	private static Recipe<SingleRecipeInput> sawRecipe(Recipe<?> recipe) {
		@SuppressWarnings("unchecked")
		Recipe<SingleRecipeInput> single = (Recipe<SingleRecipeInput>) recipe;
		return single;
	}

	private static boolean isCuttingRecipe(RecipeHolder<?> holder) {
		RecipeType<?> type = holder.value()
			.getType();
		return type == SawRegistry.CUTTING || (SawRegistry.allowStonecuttingOnSaw && type == RecipeType.STONECUTTING);
	}

	private static Ingredient firstIngredient(Recipe<?> recipe) {
		if (recipe instanceof CuttingRecipe cutting)
			return cutting.getIngredients()
				.isEmpty() ? null : cutting.getIngredients()
					.getFirst();
		if (recipe instanceof SingleItemRecipe single)
			return single.input();
		return null;
	}

	/** Create's RecipeConditions.firstIngredientMatches */
	private static boolean firstIngredientMatches(Recipe<?> recipe, ItemStack stack) {
		Ingredient ingredient = firstIngredient(recipe);
		return ingredient != null && ingredient.test(stack);
	}

	/** Create's Recipe.getResultItem: the first result of a cutting recipe, the result of a stonecutting recipe. */
	private static ItemStack resultItem(Recipe<SingleRecipeInput> recipe, ItemStack input) {
		return recipe.assemble(new SingleRecipeInput(input.copyWithCount(1)));
	}

	private List<Recipe<SingleRecipeInput>> getRecipes() {
		if (!(level instanceof ServerLevel serverLevel))
			return List.of();
		ItemStack input = inventory.getStackInSlot(0);

		List<Recipe<?>> startedSearch = RecipeCache.get(cuttingRecipesKey, serverLevel, SawBlockEntity::isCuttingRecipe);
		return startedSearch.stream()
			.filter(r -> filtering.test(resultItem(sawRecipe(r), input)))
			.filter(r -> firstIngredientMatches(r, input))
			.map(SawBlockEntity::sawRecipe)
			.collect(Collectors.toList());
	}

	public void insertItem(ItemEntity entity) {
		if (!canProcess())
			return;
		if (!inventory.isEmpty())
			return;
		if (!entity.isAlive())
			return;
		if (level.isClientSide())
			return;

		inventory.clear();
		ItemStack remainder = inventory.insertItem(0, entity.getItem()
			.copy(), false);
		if (remainder.isEmpty())
			entity.discard();
		else
			entity.setItem(remainder);
	}

	public void start(ItemStack inserted) {
		if (!canProcess())
			return;
		if (inventory.isEmpty())
			return;
		if (level.isClientSide())
			return;

		List<Recipe<SingleRecipeInput>> recipes = getRecipes();
		boolean valid = !recipes.isEmpty();
		int time = 50;

		if (recipes.isEmpty()) {
			inventory.remainingTime = inventory.recipeDuration = 10;
			inventory.appliedRecipe = false;
			sendData();
			return;
		}

		if (valid) {
			recipeIndex++;
			if (recipeIndex >= recipes.size())
				recipeIndex = 0;
		}

		Recipe<SingleRecipeInput> recipe = recipes.get(recipeIndex);
		if (recipe instanceof CuttingRecipe cutting) {
			time = cutting.getProcessingDuration();
		}

		inventory.remainingTime = time * Math.max(1, (inserted.getCount() / 5));
		inventory.recipeDuration = inventory.remainingTime;
		inventory.appliedRecipe = false;
		sendData();
	}

	protected boolean canProcess() {
		return getBlockState().getValue(SawBlock.FACING) == Direction.UP;
	}

	// Block Breaker

	@Override
	protected boolean shouldRun() {
		return getBlockState().getValue(SawBlock.FACING)
			.getAxis()
			.isHorizontal();
	}

	@Override
	protected BlockPos getBreakingPos() {
		return getBlockPos().relative(getBlockState().getValue(SawBlock.FACING));
	}

	@Override
	public void onBlockBroken(BlockState stateToBreak) {
		super.onBlockBroken(stateToBreak);
		TreeCutter.findTree(level, breakingPos, stateToBreak)
			.destroyBlocks(level, null, this::dropItemFromCutTree);
	}

	public void dropItemFromCutTree(BlockPos pos, ItemStack stack) {
		float distance = (float) Math.sqrt(pos.distSqr(breakingPos));
		Vec3 dropPos = VecHelper.getCenterOf(pos);
		ItemEntity entity = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, stack);
		entity.setDeltaMovement(Vec3.atLowerCornerOf(breakingPos.subtract(this.worldPosition))
			.scale(distance / 20f));
		level.addFreshEntity(entity);
	}

	@Override
	public boolean canBreak(BlockState stateToBreak, float blockHardness) {
		boolean sawable = isSawable(stateToBreak);
		return super.canBreak(stateToBreak, blockHardness) && sawable;
	}

	public static boolean isSawable(BlockState stateToBreak) {
		if (stateToBreak.is(BlockTags.SAPLINGS))
			return false;
		if (TreeCutter.isLog(stateToBreak) || (stateToBreak.is(BlockTags.LEAVES)))
			return true;
		if (TreeCutter.isRoot(stateToBreak))
			return true;
		Block block = stateToBreak.getBlock();
		if (block instanceof BambooStalkBlock)
			return true;
		if (stateToBreak.is(TreeCutter.SOLID_CROPS))
			return true;
		if (block instanceof CactusBlock)
			return true;
		if (block instanceof SugarCaneBlock)
			return true;
		if (block instanceof KelpPlantBlock)
			return true;
		if (block instanceof KelpBlock)
			return true;
		if (block instanceof ChorusPlantBlock)
			return true;
		return false;
	}
}

package dev.createpolymer.legacy;

import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.registry.feature.ExtrasRegistry;
import dev.createpolymer.util.VecHelper;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Create's legacy chromatic compound (ported from Create's ChromaticCompoundItem, MIT, The Create Team). Floating
 * in the light, it eats placed light sources and turns into refined radiance after enough of them, into shadow steel
 * in the void, or into refined radiance inside a beacon beam. The bar and the tint are client rendering and are not
 * ported. A collecting stack does not stack: it gets a maximum stack size of 1, as Create's getMaxStackSize gives.
 */
public class ChromaticCompoundItem extends SimplePolymerItem implements ItemEntityUpdate {

	// Create's CRecipes defaults
	public static final int LIGHT_SOURCE_COUNT_FOR_REFINED_RADIANCE = 10;
	public static final boolean ENABLE_REFINED_RADIANCE_RECIPE = true;
	public static final boolean ENABLE_SHADOW_STEEL_RECIPE = true;

	public ChromaticCompoundItem(Properties properties) {
		super(properties, Items.TRIAL_KEY, true);
	}

	public int getLight(ItemStack stack) {
		return stack.getOrDefault(ExtrasRegistry.CHROMATIC_COMPOUND_COLLECTING_LIGHT, 0);
	}

	public int getLight(DataComponentMap components) {
		return components.getOrDefault(ExtrasRegistry.CHROMATIC_COMPOUND_COLLECTING_LIGHT, 0);
	}

	@Override
	public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
		Level world = entity.level();
		ItemStack itemStack = entity.getItem();
		Vec3 positionVec = entity.position();

		double y = entity.getY();
		double yMotion = entity.getDeltaMovement().y;
		int minHeight = world.getMinY();

		// Convert to Shadow steel if in void
		if (y < minHeight && y - yMotion < -10 + minHeight && ENABLE_SHADOW_STEEL_RECIPE) {
			ItemStack newStack = ExtrasRegistry.SHADOW_STEEL.getDefaultInstance();
			newStack.setCount(stack.getCount());
			entity.addTag(NoGravMagicalDohickyItem.JUST_CREATED);
			entity.setItem(newStack);
		}

		if (!ENABLE_REFINED_RADIANCE_RECIPE)
			return false;

		// Convert to Refined Radiance if eaten enough light sources
		if (getLight(itemStack) >= LIGHT_SOURCE_COUNT_FOR_REFINED_RADIANCE) {
			ItemStack newStack = ExtrasRegistry.REFINED_RADIANCE.getDefaultInstance();
			ItemEntity newEntity = new ItemEntity(world, entity.getX(), entity.getY(), entity.getZ(), newStack);
			newEntity.setDeltaMovement(entity.getDeltaMovement());
			newEntity.addTag(NoGravMagicalDohickyItem.JUST_CREATED);
			itemStack.remove(ExtrasRegistry.CHROMATIC_COMPOUND_COLLECTING_LIGHT);
			world.addFreshEntity(newEntity);

			stack.split(1);
			entity.setItem(stack);
			if (stack.isEmpty())
				entity.discard();
			return false;
		}

		// Is inside beacon beam?
		boolean isOverBeacon = false;
		int entityX = Mth.floor(entity.getX());
		int entityZ = Mth.floor(entity.getZ());
		int localWorldHeight = world.getHeight(Heightmap.Types.WORLD_SURFACE, entityX, entityZ);

		BlockPos.MutableBlockPos testPos =
			new BlockPos.MutableBlockPos(entityX, Math.min(Mth.floor(entity.getY()), localWorldHeight), entityZ);

		while (testPos.getY() > minHeight) {
			testPos.move(Direction.DOWN);
			BlockState state = world.getBlockState(testPos);
			if (state.getLightDampening() >= 15 && state.getBlock() != Blocks.BEDROCK)
				break;
			if (state.getBlock() == Blocks.BEACON) {
				BlockEntity be = world.getBlockEntity(testPos);

				if (!(be instanceof BeaconBlockEntity bte))
					break;

				if (!bte.getBeamSections().isEmpty())
					isOverBeacon = true;

				break;
			}
		}

		if (isOverBeacon) {
			ItemStack newStack = ExtrasRegistry.REFINED_RADIANCE.getDefaultInstance();
			newStack.setCount(stack.getCount());
			entity.addTag(NoGravMagicalDohickyItem.JUST_CREATED);
			entity.setItem(newStack);
			return false;
		}

		// Find a light source and eat it.
		RandomSource r = world.getRandom();
		int range = 3;
		float rate = 1 / 2f;
		if (r.nextFloat() > rate)
			return false;

		BlockPos randomOffset = BlockPos.containing(VecHelper.offsetRandomly(positionVec, r, range));
		BlockState state = world.getBlockState(randomOffset);

		TransportedItemStackHandlerBehaviour behaviour =
			BlockEntityBehaviour.get(world, randomOffset, TransportedItemStackHandlerBehaviour.TYPE);

		// Find a placed light source
		if (behaviour == null) {
			if (checkLight(stack, entity, world, itemStack, positionVec, randomOffset, state))
				world.destroyBlock(randomOffset, false);
			return false;
		}

		// Find a light source from a depot/belt (chunk rebuild safe)
		boolean[] success = { false };
		behaviour.handleProcessingOnAllItems(ts -> {
			ItemStack heldStack = ts.stack;
			if (!(heldStack.getItem() instanceof BlockItem blockItem))
				return TransportedResult.doNothing();

			if (blockItem.getBlock() == null)
				return TransportedResult.doNothing();

			BlockState stateToCheck = blockItem.getBlock().defaultBlockState();

			if (!success[0] && checkLight(stack, entity, world, itemStack, positionVec, randomOffset, stateToCheck)) {
				success[0] = true;
				if (ts.stack.getCount() == 1)
					return TransportedResult.removeItem();
				TransportedItemStack left = ts.copy();
				left.stack.shrink(1);
				return TransportedResult.convertTo(left);
			}

			return TransportedResult.doNothing();
		});
		return false;
	}

	public boolean checkLight(ItemStack stack, ItemEntity entity, Level world, ItemStack itemStack, Vec3 positionVec,
		BlockPos randomOffset, BlockState state) {
		if (state.getLightEmission() == 0)
			return false;
		if (state.getDestroySpeed(world, randomOffset) == -1)
			return false;
		if (state.getBlock() == Blocks.BEACON)
			return false;

		ClipContext context = new ClipContext(positionVec.add(new Vec3(0, 0.5, 0)), VecHelper.getCenterOf(randomOffset),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
		if (!randomOffset.equals(world.clip(context).getBlockPos()))
			return false;

		DataComponentMap components = itemStack.getComponents();
		ItemStack newStack = stack.split(1);
		newStack.set(ExtrasRegistry.CHROMATIC_COMPOUND_COLLECTING_LIGHT, getLight(components) + 1);
		newStack.set(DataComponents.MAX_STACK_SIZE, 1);
		ItemEntity newEntity = new ItemEntity(world, entity.getX(), entity.getY(), entity.getZ(), newStack);
		newEntity.setDeltaMovement(entity.getDeltaMovement());
		newEntity.setDefaultPickUpDelay();
		world.addFreshEntity(newEntity);
		if (stack.isEmpty())
			entity.discard();
		return true;
	}
}

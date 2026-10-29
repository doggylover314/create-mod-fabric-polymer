package dev.createpolymer.contraptions.actors.harvester;

import org.apache.commons.lang3.mutable.MutableBoolean;

import dev.createpolymer.api.behaviour.movement.MovementBehaviour;
import dev.createpolymer.contraptions.ContraptionCollider;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.fluids.PipeHelper;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's HarvesterMovementBehaviour (MIT, The Create Team). The check for plants that implement
 * NeoForge's SpecialPlantable and the Farmer's Delight mushroom harvesting are not ported.
 */
public class HarvesterMovementBehaviour implements MovementBehaviour, ContraptionCollider.BlockBreaker {

	@Override
	public boolean isActive(MovementContext context) {
		return MovementBehaviour.super.isActive(context)
			&& !VecHelper.isVecPointingTowards(context.relativeMotion, context.state.getValue(HarvesterBlock.FACING)
				.getOpposite());
	}

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		return Vec3.atLowerCornerOf(context.state.getValue(HarvesterBlock.FACING)
			.getUnitVec3i())
			.scale(.45);
	}

	@Override
	public void visitNewPosition(MovementContext context, BlockPos pos) {
		Level world = context.world;
		if (world.isClientSide())
			return;

		BlockState stateVisited = world.getBlockState(pos);
		if (stateVisited.isAir() || stateVisited.is(ActorsRegistry.NON_HARVESTABLE))
			return;

		boolean notCropButCuttable = false;

		if (!isValidCrop(world, pos, stateVisited)) {
			if (isValidOther(world, pos, stateVisited))
				notCropButCuttable = true;
			else
				return;
		}

		ItemStack item = ItemStack.EMPTY;
		float effectChance = 1;

		if (stateVisited.is(BlockTags.LEAVES)) {
			item = new ItemStack(Items.SHEARS);
			effectChance = .45f;
		}

		MutableBoolean seedSubtracted = new MutableBoolean(notCropButCuttable);
		BlockState state = stateVisited;
		PipeHelper.destroyBlockAs(world, pos, null, item, effectChance, stack -> {
			if (ActorsRegistry.harvesterReplants && !seedSubtracted.getValue()
				&& ItemHelper.sameItem(stack, new ItemStack(state.getBlock()))) {
				stack.shrink(1);
				seedSubtracted.setTrue();
			}
			collectOrDropItem(context, stack);
		});

		BlockState cutCrop = cutCrop(world, pos, stateVisited);
		world.setBlockAndUpdate(pos, cutCrop.canSurvive(world, pos) ? cutCrop : Blocks.AIR.defaultBlockState());
	}

	@Override
	public boolean canBreakAt(Level world, BlockPos pos, BlockState state) {
		return isValidCrop(world, pos, state) || isValidOther(world, pos, state);
	}

	public boolean isValidCrop(Level world, BlockPos pos, BlockState state) {
		boolean harvestPartial = ActorsRegistry.harvestPartiallyGrown;
		boolean replant = ActorsRegistry.harvesterReplants;

		if (state.getBlock() instanceof CropBlock crop) {
			if (harvestPartial)
				return state != crop.getStateForAge(0) || !replant;
			return crop.isMaxAge(state);
		}

		if (state.getCollisionShape(world, pos)
			.isEmpty() || state.getBlock() instanceof CocoaBlock) {
			for (Property<?> property : state.getProperties()) {
				if (!(property instanceof IntegerProperty ageProperty))
					continue;
				if (!property.getName()
					.equals(BlockStateProperties.AGE_1.getName()))
					continue;
				int age = state.getValue(ageProperty)
					.intValue();
				if (state.getBlock() instanceof SweetBerryBushBlock && age <= 1 && replant)
					continue;
				if (age == 0 && replant || !harvestPartial && (ageProperty.getPossibleValues()
					.size() - 1 != age))
					continue;
				return true;
			}
		}

		return false;
	}

	public boolean isValidOther(Level world, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof CropBlock)
			return false;
		if (state.getBlock() instanceof SugarCaneBlock)
			return true;
		if (state.is(BlockTags.LEAVES))
			return true;
		if (state.getBlock() instanceof CocoaBlock)
			return state.getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE;

		if (state.getCollisionShape(world, pos)
			.isEmpty()) {
			if (state.getBlock() instanceof GrowingPlantBlock)
				return true;

			for (Property<?> property : state.getProperties()) {
				if (!(property instanceof IntegerProperty))
					continue;
				if (!property.getName()
					.equals(BlockStateProperties.AGE_1.getName()))
					continue;
				return false;
			}

			// Create: BushBlock (VegetationBlock since 1.21.5). Mushrooms are only harvested with Farmer's Delight
			if (state.getBlock() instanceof MushroomBlock)
				return false;
			if (state.getBlock() instanceof VegetationBlock)
				return true;
		}

		return false;
	}

	private BlockState cutCrop(Level world, BlockPos pos, BlockState state) {
		if (!ActorsRegistry.harvesterReplants) {
			if (state.getFluidState()
				.isEmpty())
				return Blocks.AIR.defaultBlockState();
			return state.getFluidState()
				.createLegacyBlock();
		}

		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			BlockState newState = crop.getStateForAge(0);
			if (!newState.is(block))
				return newState;
			// the age property of a crop is protected: it is the integer "age" property
			for (Property<?> property : state.getProperties())
				if (property instanceof IntegerProperty ageProperty && property.getName()
					.equals("age"))
					return state.setValue(ageProperty, 0);
			return newState;
		}
		if (block == Blocks.SWEET_BERRY_BUSH) {
			return state.setValue(BlockStateProperties.AGE_3, Integer.valueOf(1));
		}
		if (state.is(ActorsRegistry.SUGAR_CANE_VARIANTS) || block instanceof GrowingPlantBlock) {
			if (state.getFluidState()
				.isEmpty())
				return Blocks.AIR.defaultBlockState();
			return state.getFluidState()
				.createLegacyBlock();
		}
		if (state.getCollisionShape(world, pos)
			.isEmpty() || block instanceof CocoaBlock) {
			for (Property<?> property : state.getProperties()) {
				if (!(property instanceof IntegerProperty))
					continue;
				if (!property.getName()
					.equals(BlockStateProperties.AGE_1.getName()))
					continue;
				return state.setValue((IntegerProperty) property, Integer.valueOf(0));
			}
		}

		if (state.getFluidState()
			.isEmpty())
			return Blocks.AIR.defaultBlockState();
		return state.getFluidState()
			.createLegacyBlock();
	}

}

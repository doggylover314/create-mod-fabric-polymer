package dev.createpolymer.contraptions.actors.roller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.contraptions.actors.roller.RollerBlockEntity.RollingMode;
import dev.createpolymer.contraptions.behaviour.MovementContext;
import dev.createpolymer.fluids.PipeHelper;
import dev.createpolymer.foundation.item.ItemHelper;
import dev.createpolymer.kinetics.base.BlockBreakingMovementBehaviour;
import dev.createpolymer.logistics.filter.FilterItemStack;
import dev.createpolymer.registry.feature.ActorsRegistry;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.util.Pair;
import dev.createpolymer.util.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Create's RollerMovementBehaviour (MIT, The Create Team). Create also lays the road along train tracks;
 * trains are not ported, so only the paving in front of the roller is.
 */
public class RollerMovementBehaviour extends BlockBreakingMovementBehaviour {

	@Override
	public boolean isActive(MovementContext context) {
		return super.isActive(context) && !isPulleyContraption(context)
			&& VecHelper.isVecPointingTowards(context.relativeMotion, context.state.getValue(RollerBlock.FACING));
	}

	/** Create: {@code context.contraption instanceof PulleyContraption} */
	private static boolean isPulleyContraption(MovementContext context) {
		return context.contraption.getType().id.getPath().equals("pulley");
	}

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		return Vec3.atLowerCornerOf(context.state.getValue(RollerBlock.FACING)
			.getUnitVec3i())
			.scale(.45)
			.subtract(0, 2, 0);
	}

	@Override
	protected float getBlockBreakingSpeed(MovementContext context) {
		return Mth.clamp(super.getBlockBreakingSpeed(context) * 1.5f, 1 / 128f, 16f);
	}

	@Override
	public boolean canBreak(Level world, BlockPos breakingPos, BlockState state) {
		for (Direction side : Iterate.directions)
			if (world.getBlockState(breakingPos.relative(side))
				.is(BlockTags.PORTALS))
				return false;

		return super.canBreak(world, breakingPos, state) && !state.getCollisionShape(world, breakingPos)
			.isEmpty();
	}

	@Override
	protected @Nullable DamageSource getDamageSource(Level level) {
		return ActorsRegistry.rollerDamage(level);
	}

	@Override
	public void visitNewPosition(MovementContext context, BlockPos pos) {
		Level world = context.world;
		BlockState stateVisited = world.getBlockState(pos);
		if (!stateVisited.isRedstoneConductor(world, pos))
			damageEntities(context, pos, world);
		if (world.isClientSide())
			return;

		List<BlockPos> positionsToBreak = getPositionsToBreak(context, pos);
		if (positionsToBreak.isEmpty()) {
			triggerPaver(context, pos);
			return;
		}

		BlockPos argMax = null;
		double max = -1;
		for (BlockPos toBreak : positionsToBreak) {
			float hardness = context.world.getBlockState(toBreak)
				.getDestroySpeed(world, toBreak);
			if (hardness < max)
				continue;
			max = hardness;
			argMax = toBreak;
		}

		if (argMax == null) {
			triggerPaver(context, pos);
			return;
		}

		context.data.store("ReferencePos", BlockPos.CODEC, pos);
		context.data.store("BreakingPos", BlockPos.CODEC, argMax);
		context.stall = true;
	}

	@Override
	protected void onBlockBroken(MovementContext context, BlockPos pos, BlockState brokenState) {
		super.onBlockBroken(context, pos, brokenState);
		if (!context.data.contains("ReferencePos"))
			return;

		BlockPos referencePos = context.data.read("ReferencePos", BlockPos.CODEC)
			.orElse(BlockPos.ZERO);
		for (BlockPos otherPos : getPositionsToBreak(context, referencePos))
			if (!otherPos.equals(pos))
				destroyBlock(context, otherPos);

		triggerPaver(context, referencePos);
		context.data.remove("ReferencePos");
	}

	@Override
	protected void destroyBlock(MovementContext context, BlockPos breakingPos) {
		BlockState blockState = context.world.getBlockState(breakingPos);
		boolean noHarvest = blockState.is(BlockTags.NEEDS_IRON_TOOL) || blockState.is(BlockTags.NEEDS_STONE_TOOL)
			|| blockState.is(BlockTags.NEEDS_DIAMOND_TOOL);

		PipeHelper.destroyBlock(context.world, breakingPos, 1f, stack -> {
			if (noHarvest || context.world.getRandom()
				.nextBoolean())
				return;
			this.collectOrDropItem(context, stack);
		});

		super.destroyBlock(context, breakingPos);
	}

	protected List<BlockPos> getPositionsToBreak(MovementContext context, BlockPos visitedPos) {
		ArrayList<BlockPos> positions = new ArrayList<>();

		RollingMode mode = getMode(context);
		if (mode != RollingMode.TUNNEL_PAVE)
			return positions;

		int startingY = 1;
		if (!getStateToPaveWith(context).isAir()) {
			FilterItemStack filter = context.getFilterFromBE();
			if (!ItemHelper
				.extract(context.contraption.getStorage()
					.getAllItems(), stack -> filter.test(context.world, stack), 1, true)
				.isEmpty())
				startingY = 0;
		}

		for (int i = startingY; i <= 2; i++)
			if (testBreakerTarget(context, visitedPos.above(i), i))
				positions.add(visitedPos.above(i));

		return positions;
	}

	protected boolean testBreakerTarget(MovementContext context, BlockPos target, int columnY) {
		BlockState stateToPaveWith = getStateToPaveWith(context);
		BlockState stateToPaveWithAsSlab = getStateToPaveWithAsSlab(context);
		BlockState stateAbove = context.world.getBlockState(target);
		if (columnY == 0 && stateAbove.is(stateToPaveWith.getBlock()))
			return false;
		if (stateToPaveWithAsSlab != null && columnY == 1 && stateAbove.is(stateToPaveWithAsSlab.getBlock()))
			return false;
		return canBreak(context.world, target, stateAbove);
	}

	protected void triggerPaver(MovementContext context, BlockPos pos) {
		BlockState stateToPaveWith = getStateToPaveWith(context);
		BlockState stateToPaveWithAsSlab = getStateToPaveWithAsSlab(context);
		RollingMode mode = getMode(context);

		if (mode != RollingMode.TUNNEL_PAVE && stateToPaveWith.isAir())
			return;

		PaveResult paveResult = PaveResult.PASS;
		int yOffset = 0;

		List<Pair<BlockPos, Boolean>> paveSet = new ArrayList<>();
		paveSet.add(Pair.of(pos, false));

		while (paveResult == PaveResult.PASS) {
			if (yOffset > ActorsRegistry.rollerFillDepth) {
				paveResult = PaveResult.FAIL;
				break;
			}

			Set<Pair<BlockPos, Boolean>> currentLayer = new HashSet<>();
			if (mode == RollingMode.WIDE_FILL) {
				for (Pair<BlockPos, Boolean> anchor : paveSet) {
					int radius = (yOffset + 1) / 2;
					for (int i = -radius; i <= radius; i++)
						for (int j = -radius; j <= radius; j++)
							if (BlockPos.ZERO.distManhattan(new BlockPos(i, 0, j)) <= radius)
								currentLayer.add(Pair.of(anchor.getFirst()
									.offset(i, -yOffset, j), anchor.getSecond()));
				}
			} else
				for (Pair<BlockPos, Boolean> anchor : paveSet)
					currentLayer.add(Pair.of(anchor.getFirst()
						.below(yOffset), anchor.getSecond()));

			boolean completelyBlocked = true;
			boolean anyBlockPlaced = false;

			for (Pair<BlockPos, Boolean> currentPos : currentLayer) {
				if (stateToPaveWithAsSlab != null && yOffset == 0 && currentPos.getSecond())
					tryFill(context, currentPos.getFirst()
						.above(), stateToPaveWithAsSlab);
				paveResult = tryFill(context, currentPos.getFirst(), stateToPaveWith);
				if (paveResult != PaveResult.FAIL)
					completelyBlocked = false;
				if (paveResult == PaveResult.SUCCESS)
					anyBlockPlaced = true;
			}

			if (anyBlockPlaced)
				paveResult = PaveResult.SUCCESS;
			else if (!completelyBlocked || yOffset == 0)
				paveResult = PaveResult.PASS;

			if (paveResult == PaveResult.SUCCESS && stateToPaveWith.getBlock() instanceof FallingBlock)
				paveResult = PaveResult.PASS;
			if (paveResult != PaveResult.PASS)
				break;
			if (mode == RollingMode.TUNNEL_PAVE)
				break;

			yOffset++;
		}

		if (paveResult == PaveResult.SUCCESS) {
			context.data.putInt("WaitingTicks", 2);
			context.data.store("LastPos", BlockPos.CODEC, pos);
			context.stall = true;
		}
	}

	public static BlockState getStateToPaveWith(ItemStack itemStack) {
		if (itemStack.getItem() instanceof BlockItem bi) {
			BlockState defaultBlockState = bi.getBlock()
				.defaultBlockState();
			if (defaultBlockState.hasProperty(SlabBlock.TYPE))
				defaultBlockState = defaultBlockState.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
			return defaultBlockState;
		}
		return Blocks.AIR.defaultBlockState();
	}

	protected BlockState getStateToPaveWith(MovementContext context) {
		return getStateToPaveWith(context.getFilterFromBE()
			.item());
	}

	@Nullable
	protected BlockState getStateToPaveWithAsSlab(MovementContext context) {
		BlockState stateToPaveWith = getStateToPaveWith(context);
		if (stateToPaveWith.hasProperty(SlabBlock.TYPE))
			return stateToPaveWith.setValue(SlabBlock.TYPE, SlabType.BOTTOM);

		Block block = stateToPaveWith.getBlock();
		if (block == null)
			return null;

		Identifier rl = BuiltInRegistries.BLOCK.getKey(block);
		String namespace = rl.getNamespace();
		String blockName = rl.getPath();
		int nameLength = blockName.length();

		List<String> possibleSlabLocations = new ArrayList<>();
		possibleSlabLocations.add(blockName + "_slab");

		if (blockName.endsWith("s") && nameLength > 1)
			possibleSlabLocations.add(blockName.substring(0, nameLength - 1) + "_slab");
		if (blockName.endsWith("planks") && nameLength > 7)
			possibleSlabLocations.add(blockName.substring(0, nameLength - 7) + "_slab");

		for (String locationAttempt : possibleSlabLocations) {
			Optional<Block> result = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(namespace, locationAttempt));
			if (result.isEmpty())
				continue;
			return result.get()
				.defaultBlockState();
		}

		return null;
	}

	protected RollingMode getMode(MovementContext context) {
		int value = context.blockEntityData == null ? 0 : context.blockEntityData.getIntOr("ScrollValue", 0);
		RollingMode[] modes = RollingMode.values();
		return modes[Mth.clamp(value, 0, modes.length - 1)];
	}

	private enum PaveResult {
		FAIL, PASS, SUCCESS;
	}

	protected PaveResult tryFill(MovementContext context, BlockPos targetPos, BlockState toPlace) {
		Level level = context.world;
		if (!level.isLoaded(targetPos))
			return PaveResult.FAIL;
		BlockState existing = level.getBlockState(targetPos);
		if (existing.is(toPlace.getBlock()))
			return PaveResult.PASS;
		if (!existing.is(BlockTags.LEAVES) && !existing.canBeReplaced()
			&& (!existing.getCollisionShape(level, targetPos)
				.isEmpty()
				|| existing.is(BlockTags.PORTALS)))
			return PaveResult.FAIL;

		FilterItemStack filter = context.getFilterFromBE();
		ItemStack held = ItemHelper.extract(context.contraption.getStorage()
			.getAllItems(), stack -> filter.test(context.world, stack), 1, false);
		if (held.isEmpty())
			return PaveResult.FAIL;

		level.setBlockAndUpdate(targetPos, toPlace);
		return PaveResult.SUCCESS;
	}

}

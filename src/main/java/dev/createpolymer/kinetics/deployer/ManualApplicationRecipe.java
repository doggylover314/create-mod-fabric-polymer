package dev.createpolymer.kinetics.deployer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.createpolymer.processing.recipe.ProcessingOutput;
import dev.createpolymer.registry.feature.DeployerRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ported from Create's ManualApplicationRecipe (MIT, The Create Team). Create handles the right click with a
 * NeoForge event; here it is Fabric's {@link UseBlockCallback}, which the deployer fires as well.
 */
public class ManualApplicationRecipe extends ItemApplicationRecipe {

	private static RecipeManager cachedManager;
	private static List<ManualApplicationRecipe> cachedRecipes = List.of();

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> manualApplicationRecipesApplyInWorld(player, level,
			hand, hitResult.getBlockPos()));
	}

	private static synchronized List<ManualApplicationRecipe> getAllRecipes(ServerLevel level) {
		RecipeManager manager = level.recipeAccess();
		if (manager != cachedManager) {
			List<ManualApplicationRecipe> all = new ArrayList<>();
			for (RecipeHolder<?> holder : manager.getRecipes())
				if (holder.value() instanceof ManualApplicationRecipe recipe)
					all.add(recipe);
			cachedRecipes = all;
			cachedManager = manager;
		}
		return cachedRecipes;
	}

	public static InteractionResult manualApplicationRecipesApplyInWorld(Player player, Level level, InteractionHand hand,
		BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel))
			return InteractionResult.PASS;
		ItemStack heldItem = player.getItemInHand(hand);
		BlockState blockState = level.getBlockState(pos);

		if (heldItem.isEmpty())
			return InteractionResult.PASS;
		if (blockState.isAir())
			return InteractionResult.PASS;

		Optional<ManualApplicationRecipe> foundRecipe = getAllRecipes(serverLevel).stream()
			.filter(mar -> mar.testBlock(blockState) && mar.getIngredients()
				.get(1)
				.test(heldItem))
			.findFirst();

		if (foundRecipe.isEmpty())
			return InteractionResult.PASS;

		level.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.PLAYERS, 1, 1.45f);
		ManualApplicationRecipe recipe = foundRecipe.get();
		level.destroyBlock(pos, false);

		BlockState transformedBlock = recipe.transformBlock(blockState, level.getRandom());
		level.setBlock(pos, transformedBlock, Block.UPDATE_ALL);
		recipe.rollResults(level.getRandom())
			.forEach(stack -> Block.popResource(level, pos, stack));

		boolean creative = player.isCreative();
		boolean unbreakable = heldItem.has(DataComponents.UNBREAKABLE);
		boolean keepHeld = recipe.shouldKeepHeldItem() || creative;

		if (!unbreakable && !keepHeld) {
			if (heldItem.getMaxDamage() > 0) {
				heldItem.hurtAndBreak(1, player, hand);
			} else {
				ItemStackTemplate remainder = heldItem.getItem()
					.getCraftingRemainder();
				ItemStack leftover = remainder == null ? ItemStack.EMPTY : remainder.create();
				heldItem.shrink(1);
				if (heldItem.isEmpty()) {
					player.setItemInHand(hand, leftover);
				} else {
					if (!player.getInventory()
						.add(leftover)) {
						player.drop(leftover, false, Prediction.SERVER_ONLY);
					}
				}
			}
		}

		return InteractionResult.SUCCESS;
	}

	public ManualApplicationRecipe(Params params, boolean keepHeldItem) {
		super(params, keepHeldItem);
	}

	public boolean testBlock(BlockState in) {
		return getIngredients().get(0)
			.test(new ItemStack(in.getBlock()
				.asItem()));
	}

	public BlockState transformBlock(BlockState in, RandomSource randomSource) {
		ProcessingOutput mainOutput = getRollableResults().get(0);
		ItemStack output = mainOutput.rollOutput(randomSource);
		if (output.getItem() instanceof BlockItem bi)
			return DeployerBlockHelper.copyProperties(in, bi.getBlock()
				.defaultBlockState());
		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public List<ItemStack> rollResults(RandomSource randomSource) {
		return rollResults(getRollableResultsExceptBlock(), randomSource);
	}

	public List<ProcessingOutput> getRollableResultsExceptBlock() {
		List<ProcessingOutput> results = getRollableResults();
		ProcessingOutput mainOutput = results.get(0);
		if (mainOutput.getStack()
			.getItem() instanceof BlockItem)
			return results.subList(1, results.size());
		return results;
	}

	@Override
	public RecipeSerializer<ManualApplicationRecipe> getSerializer() {
		return DeployerRegistry.ITEM_APPLICATION_SERIALIZER;
	}

	@Override
	public RecipeType<ManualApplicationRecipe> getType() {
		return DeployerRegistry.ITEM_APPLICATION;
	}
}

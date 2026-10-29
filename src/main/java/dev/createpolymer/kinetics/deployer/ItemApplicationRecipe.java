package dev.createpolymer.kinetics.deployer;

import java.util.List;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.processing.recipe.ProcessingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * Ported from Create's ItemApplicationRecipe (MIT, The Create Team). The serialised form is the processing recipe
 * plus {@code keep_held_item}.
 */
public abstract class ItemApplicationRecipe extends ProcessingRecipe<ItemApplicationInput> {

	private final boolean keepHeldItem;
	@Nullable
	private Ingredient alternativeProcessedItem;

	public ItemApplicationRecipe(Params params, boolean keepHeldItem) {
		super(params);
		this.keepHeldItem = keepHeldItem;
	}

	public static <R extends ItemApplicationRecipe> MapCodec<R> codec(BiFunction<Params, Boolean, R> factory) {
		return RecordCodecBuilder.<R>mapCodec(i -> i.group(
			Params.CODEC.forGetter(ItemApplicationRecipe::getParams),
			Codec.BOOL.optionalFieldOf("keep_held_item", false).forGetter(ItemApplicationRecipe::shouldKeepHeldItem)
		).apply(i, factory)).validate(recipe -> {
			List<String> errors = recipe.validate();
			if (errors.isEmpty())
				return DataResult.success(recipe);
			return DataResult.error(() -> String.join("\n", errors), recipe);
		});
	}

	@Override
	public boolean matches(ItemApplicationInput inv, Level level) {
		return testProcessedItem(inv.getItem(0)) && getRequiredHeldItem().test(inv.getItem(1));
	}

	private boolean testProcessedItem(ItemStack stack) {
		return getProcessedItem().test(stack) || alternativeProcessedItem != null && alternativeProcessedItem.test(stack);
	}

	/** Sequenced assembly: the first step may also be applied to the assembly's starting ingredient. */
	public void setAlternativeProcessedItem(Ingredient ingredient) {
		this.alternativeProcessedItem = ingredient;
	}

	@Override
	protected int getMaxInputCount() {
		return 2;
	}

	@Override
	protected int getMaxOutputCount() {
		return 4;
	}

	public boolean shouldKeepHeldItem() {
		return keepHeldItem;
	}

	public Ingredient getRequiredHeldItem() {
		if (getIngredients().size() < 2)
			throw new IllegalStateException("Item Application Recipe has no tool!");
		return getIngredients().get(1);
	}

	public Ingredient getProcessedItem() {
		if (getIngredients().isEmpty())
			throw new IllegalStateException("Item Application Recipe has no ingredient!");
		return getIngredients().get(0);
	}
}

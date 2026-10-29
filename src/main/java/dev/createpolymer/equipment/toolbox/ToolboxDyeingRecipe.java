package dev.createpolymer.equipment.toolbox;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.registry.feature.EquipmentRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Ported from Create's ToolboxDyeingRecipe (MIT, The Create Team). */
public class ToolboxDyeingRecipe extends CustomRecipe {
	public static final ToolboxDyeingRecipe INSTANCE = new ToolboxDyeingRecipe();
	public static final MapCodec<ToolboxDyeingRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, ToolboxDyeingRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<ToolboxDyeingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	@Override
	public boolean matches(CraftingInput input, Level level) {
		int toolboxes = 0;
		int dyes = 0;

		for (int i = 0; i < input.size(); ++i) {
			ItemStack stack = input.getItem(i);
			if (!stack.isEmpty()) {
				if (Block.byItem(stack.getItem()) instanceof ToolboxBlock) {
					++toolboxes;
				} else {
					if (!stack.has(DataComponents.DYE))
						return false;
					++dyes;
				}

				if (dyes > 1 || toolboxes > 1) {
					return false;
				}
			}
		}

		return toolboxes == 1 && dyes == 1;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack toolbox = ItemStack.EMPTY;
		DyeColor color = DyeColor.BROWN;

		for (int i = 0; i < input.size(); ++i) {
			ItemStack stack = input.getItem(i);
			if (!stack.isEmpty()) {
				if (Block.byItem(stack.getItem()) instanceof ToolboxBlock) {
					toolbox = stack;
				} else {
					DyeColor color1 = stack.get(DataComponents.DYE);
					if (color1 != null) {
						color = color1;
					}
				}
			}
		}

		ItemStack dyedToolbox = new ItemStack(EquipmentRegistry.TOOLBOXES.get(color));
		if (!toolbox.getComponentsPatch().isEmpty()) {
			dyedToolbox.applyComponents(toolbox.getComponentsPatch());
		}

		return dyedToolbox;
	}

	@Override
	public RecipeSerializer<ToolboxDyeingRecipe> getSerializer() {
		return SERIALIZER;
	}
}

package dev.createpolymer.logistics.item.filter.attribute;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.fluid.FluidItemHandlers;
import dev.createpolymer.kinetics.fan.processing.AllFanProcessingTypes;
import dev.createpolymer.logistics.item.filter.attribute.attributes.AddedByAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.BookAuthorAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.BookCopyAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.ColorAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.EnchantAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.FluidContentsAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.InItemGroupAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.InTagAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.ItemNameAttribute;
import dev.createpolymer.logistics.item.filter.attribute.attributes.ShulkerFillLevelAttribute;
import dev.createpolymer.registry.ModRecipeTypes;
import dev.createpolymer.registry.feature.CrushingRegistry;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;

/**
 * Create's attribute types. Create keeps them in a NeoForge registry (CreateBuiltInRegistries.ITEM_ATTRIBUTE_TYPE);
 * here it is a plain registry that exists only on the server, as with the other Create registries of this port.
 */
public class AllItemAttributeTypes {
	public static final MappedRegistry<ItemAttributeType> REGISTRY =
		new MappedRegistry<>(ResourceKey.createRegistryKey(CreatePolymer.id("item_attribute_type")), Lifecycle.stable());

	public static final ItemAttributeType
		PLACEABLE = singleton("placeable", s -> s.getItem() instanceof BlockItem),
		CONSUMABLE = singleton("consumable", s -> s.has(DataComponents.FOOD)),
		FLUID_CONTAINER = singleton("fluid_container", s -> FluidItemHandlers.get(s.copyWithCount(1)).isPresent()),
		ENCHANTED = singleton("enchanted", ItemStack::isEnchanted),
		MAX_ENCHANTED = singleton("max_enchanted", AllItemAttributeTypes::maxEnchanted),
		RENAMED = singleton("renamed", s -> s.has(DataComponents.CUSTOM_NAME)),
		DAMAGED = singleton("damaged", ItemStack::isDamaged),
		BADLY_DAMAGED = singleton("badly_damaged", s -> s.isDamaged() && (float) s.getDamageValue() / s.getMaxDamage() > 3 / 4f),
		NOT_STACKABLE = singleton("not_stackable", ((Predicate<ItemStack>) ItemStack::isStackable).negate()),
		EQUIPABLE = singleton("equipable", s -> {
			Equippable equippable = s.get(DataComponents.EQUIPPABLE);
			EquipmentSlot.Type type = equippable != null ? equippable.slot().getType() : EquipmentSlot.MAINHAND.getType();
			return type != EquipmentSlot.Type.HAND;
		}),
		FURNACE_FUEL = singleton("furnace_fuel", s -> s.has(DataComponents.COOKING_FUEL)),
		WASHABLE = singleton("washable", AllFanProcessingTypes.SPLASHING::canProcess),
		HAUNTABLE = singleton("hauntable", AllFanProcessingTypes.HAUNTING::canProcess),
		CRUSHABLE = singleton("crushable", (s, w) -> testRecipe(s, w, CrushingRegistry.CRUSHING)
			|| testRecipe(s, w, ModRecipeTypes.MILLING)),
		SMELTABLE = singleton("smeltable", (s, w) -> testRecipe(s, w, RecipeType.SMELTING)),
		SMOKABLE = singleton("smokable", (s, w) -> testRecipe(s, w, RecipeType.SMOKING)),
		BLASTABLE = singleton("blastable", (s, w) -> testRecipe(s, w, RecipeType.BLASTING)),
		COMPOSTABLE = singleton("compostable", s -> s.has(DataComponents.COMPOSTABLE)),

	IN_TAG = register("in_tag", new InTagAttribute.Type()),
		IN_ITEM_GROUP = register("in_item_group", new InItemGroupAttribute.Type()),
		ADDED_BY = register("added_by", new AddedByAttribute.Type()),
		HAS_ENCHANT = register("has_enchant", new EnchantAttribute.Type()),
		SHULKER_FILL_LEVEL = register("shulker_fill_level", new ShulkerFillLevelAttribute.Type()),
		HAS_COLOR = register("has_color", new ColorAttribute.Type()),
		HAS_FLUID = register("has_fluid", new FluidContentsAttribute.Type()),
		HAS_NAME = register("has_name", new ItemNameAttribute.Type()),
		BOOK_AUTHOR = register("book_author", new BookAuthorAttribute.Type()),
		BOOK_COPY = register("book_copy", new BookCopyAttribute.Type());

	private static <T extends Recipe<SingleRecipeInput>> boolean testRecipe(ItemStack s, Level w, RecipeType<T> type) {
		if (!(w instanceof ServerLevel serverLevel))
			return false;
		return serverLevel.recipeAccess()
			.getRecipeFor(type, new SingleRecipeInput(s.copy()), w)
			.isPresent();
	}

	private static boolean maxEnchanted(ItemStack s) {
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
			if (entry.getKey().value().getMaxLevel() <= entry.getIntValue())
				return true;
		}

		return false;
	}

	private static ItemAttributeType singleton(String id, Predicate<ItemStack> predicate) {
		return register(id, new SingletonItemAttribute.Type(type -> new SingletonItemAttribute(type, (stack, level) -> predicate.test(stack), id)));
	}

	private static ItemAttributeType singleton(String id, BiPredicate<ItemStack, Level> predicate) {
		return register(id, new SingletonItemAttribute.Type(type -> new SingletonItemAttribute(type, predicate, id)));
	}

	private static ItemAttributeType register(String id, ItemAttributeType type) {
		return Registry.register(REGISTRY, CreatePolymer.id(id), type);
	}

	public static void init() {
	}

}

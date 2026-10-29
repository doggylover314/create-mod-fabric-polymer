package dev.createpolymer.logistics.item.filter.attribute.attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.logistics.item.filter.attribute.AllItemAttributeTypes;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttribute;
import dev.createpolymer.logistics.item.filter.attribute.ItemAttributeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class InItemGroupAttribute implements ItemAttribute {
	public static final MapCodec<InItemGroupAttribute> CODEC = BuiltInRegistries.CREATIVE_MODE_TAB.byNameCodec()
			.xmap(InItemGroupAttribute::new, i -> i.group)
			.fieldOf("value");

	@Nullable
	private CreativeModeTab group;

	public InItemGroupAttribute(@Nullable CreativeModeTab group) {
		this.group = group;
	}

	private static boolean tabContainsItem(CreativeModeTab tab, ItemStack stack) {
		return tab.contains(stack) || tab.contains(new ItemStack(stack.getItem()));
	}

	/**
	 * Dedicated servers never build the creative tabs, nobody opens a creative inventory there. They are built here,
	 * once, the first time an attribute needs them.
	 */
	private static void ensureBuilt(Level level) {
		boolean anyBuilt = false;
		for (CreativeModeTab tab : CreativeModeTabs.allTabs())
			if (!tab.getDisplayItems().isEmpty() || !tab.getSearchTabDisplayItems().isEmpty()) {
				anyBuilt = true;
				break;
			}
		if (anyBuilt)
			return;
		try {
			CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), false, level.registryAccess());
		} catch (RuntimeException | LinkageError e) {
			CreatePolymer.LOGGER.error("Attribute Filter: could not build the creative tabs", e);
		}
	}

	@Override
	public boolean appliesTo(ItemStack stack, Level world) {
		if (group == null)
			return false;

		ensureBuilt(world);
		if (group.getDisplayItems()
			.isEmpty()
			&& group.getSearchTabDisplayItems()
			.isEmpty()) {

			try {
				group.buildContents(new CreativeModeTab.ItemDisplayParameters(world.enabledFeatures(), false,
					world.registryAccess()));
			} catch (RuntimeException | LinkageError e) {
				CreatePolymer.LOGGER.error("Attribute Filter: Item Group {} crashed while building contents.",
					group.getDisplayName().getString(), e);
				group = null;
				return false;
			}

		}

		return tabContainsItem(group, stack);
	}

	@Override
	public String getTranslationKey() {
		return "in_item_group";
	}

	@Override
	public Object[] getTranslationParameters() {
		return new Object[]{group == null ? "<none>" : group.getDisplayName().getString()};
	}

	@Override
	public ItemAttributeType getType() {
		return AllItemAttributeTypes.IN_ITEM_GROUP;
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof InItemGroupAttribute that)) return false;

		return Objects.equals(group, that.group);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(group);
	}

	public static class Type implements ItemAttributeType {
		@Override
		public @NotNull ItemAttribute createAttribute() {
			return new InItemGroupAttribute(null);
		}

		@Override
		public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
			List<ItemAttribute> list = new ArrayList<>();

			ensureBuilt(level);
			for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
				if (tab.getType() == CreativeModeTab.Type.CATEGORY && tabContainsItem(tab, stack)) {
					list.add(new InItemGroupAttribute(tab));
				}
			}

			return list;
		}

		@Override
		public MapCodec<? extends ItemAttribute> codec() {
			return CODEC;
		}
	}
}

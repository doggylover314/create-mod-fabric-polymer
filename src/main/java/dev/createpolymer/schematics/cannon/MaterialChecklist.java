package dev.createpolymer.schematics.cannon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.Sets;

import dev.createpolymer.schematics.requirement.ItemRequirement;
import dev.createpolymer.schematics.requirement.ItemRequirement.ItemUseType;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

public class MaterialChecklist {

	public static final int MAX_ENTRIES_PER_PAGE = 5;

	/**
	 * Create also prints the checklist onto a clipboard; the clipboard is not part of this feature, so whoever ports it
	 * sets this to write one (see {@link dev.createpolymer.registry.feature.SchematicsRegistry#isChecklistPaper}).
	 */
	public static Function<MaterialChecklist, ItemStack> clipboardWriter = null;

	public Object2IntMap<Item> gathered = new Object2IntArrayMap<>();
	public Object2IntMap<Item> required = new Object2IntArrayMap<>();
	public Object2IntMap<Item> damageRequired = new Object2IntArrayMap<>();
	public boolean blocksNotLoaded;

	public void warnBlockNotLoaded() {
		blocksNotLoaded = true;
	}

	public void require(ItemRequirement requirement) {
		if (requirement.isEmpty())
			return;
		if (requirement.isInvalid())
			return;

		for (ItemRequirement.StackRequirement stack : requirement.getRequiredItems()) {
			if (stack.usage == ItemUseType.DAMAGE)
				putOrIncrement(damageRequired, stack.stack);
			if (stack.usage == ItemUseType.CONSUME)
				putOrIncrement(required, stack.stack);
		}
	}

	private void putOrIncrement(Object2IntMap<Item> map, ItemStack stack) {
		Item item = stack.getItem();
		if (item == Items.AIR)
			return;
		if (map.containsKey(item))
			map.put(item, map.getInt(item) + stack.getCount());
		else
			map.put(item, stack.getCount());
	}

	public void collect(ItemStack stack) {
		Item item = stack.getItem();
		if (required.containsKey(item) || damageRequired.containsKey(item))
			if (gathered.containsKey(item))
				gathered.put(item, gathered.getInt(item) + stack.getCount());
			else
				gathered.put(item, stack.getCount());
	}

	public ItemStack createWrittenBook() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

		List<Filterable<Component>> pages = new ArrayList<>();

		int itemsWritten = 0;
		MutableComponent textComponent;

		if (blocksNotLoaded) {
			textComponent = Component.literal("\n" + ChatFormatting.RED);
			textComponent = textComponent.append(Component.translatable("create.materialChecklist.blocksNotLoaded"));
			pages.add(Filterable.passThrough(textComponent));
		}

		List<Item> keys = sortedKeys();

		textComponent = Component.empty();
		List<Item> completed = new ArrayList<>();
		for (Item item : keys) {
			int amount = getRequiredAmount(item);
			if (gathered.containsKey(item))
				amount -= gathered.getInt(item);

			if (amount <= 0) {
				completed.add(item);
				continue;
			}

			if (itemsWritten == MAX_ENTRIES_PER_PAGE) {
				itemsWritten = 0;
				textComponent.append(Component.literal("\n >>>")
					.withStyle(ChatFormatting.BLUE));
				pages.add(Filterable.passThrough(textComponent));
				textComponent = Component.empty();
			}

			itemsWritten++;
			textComponent.append(entry(new ItemStack(item), amount, true, true));
		}

		for (Item item : completed) {
			if (itemsWritten == MAX_ENTRIES_PER_PAGE) {
				itemsWritten = 0;
				textComponent.append(Component.literal("\n >>>")
					.withStyle(ChatFormatting.DARK_GREEN));
				pages.add(Filterable.passThrough(textComponent));
				textComponent = Component.empty();
			}

			itemsWritten++;
			textComponent.append(entry(new ItemStack(item), getRequiredAmount(item), false, true));
		}

		pages.add(Filterable.passThrough(textComponent));

		WrittenBookContent contents = new WrittenBookContent(
			Filterable.passThrough(ChatFormatting.BLUE + "Material Checklist"),
			"Schematicannon",
			0,
			pages,
			true
		);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, contents);
		textComponent = Component.translatable("create.materialChecklist")
			.setStyle(Style.EMPTY.withColor(ChatFormatting.BLUE)
				.withItalic(Boolean.FALSE));
		book.set(DataComponents.CUSTOM_NAME, textComponent);

		return book;
	}

	/** The checklist for the paper the printer was given: a clipboard when one can be written, else a book */
	public ItemStack createWritten(ItemStack paper) {
		if (clipboardWriter != null && !paper.is(Items.BOOK) && !paper.is(Items.WRITTEN_BOOK))
			return clipboardWriter.apply(this);
		return createWrittenBook();
	}

	private List<Item> sortedKeys() {
		Set<Item> union = Sets.union(required.keySet(), damageRequired.keySet());
		List<Item> keys = new ArrayList<>(union);
		Collections.sort(keys, (item1, item2) -> {
			Locale locale = Locale.ENGLISH;
			String name1 = new ItemStack(item1).getHoverName()
				.getString()
				.toLowerCase(locale);
			String name2 = new ItemStack(item2).getHoverName()
				.getString()
				.toLowerCase(locale);
			return name1.compareTo(name2);
		});
		return keys;
	}

	public int getRequiredAmount(Item item) {
		int amount = required.getOrDefault(item, 0);
		if (damageRequired.containsKey(item))
			amount += (int) Math.ceil(damageRequired.getInt(item) / (float) new ItemStack(item).getMaxDamage());
		return amount;
	}

	private MutableComponent entry(ItemStack item, int amount, boolean unfinished, boolean forBook) {
		int stacks = amount / 64;
		int remainder = amount % 64;
		MutableComponent tc = Component.empty();
		Style nameStyle = Style.EMPTY;
		// the hover shows the item: only vanilla clients' items can be sent in a hover event
		if (BuiltInRegistries.ITEM.getKey(item.getItem())
			.getNamespace()
			.equals("minecraft"))
			nameStyle = nameStyle.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(item)));
		tc.append(Component.translatable(item.getItem()
			.getDescriptionId())
			.setStyle(nameStyle));

		if (!unfinished && forBook)
			tc.append(" ✔");
		if (!unfinished || forBook)
			tc.withStyle(unfinished ? ChatFormatting.BLUE : ChatFormatting.DARK_GREEN);
		return tc.append(Component.literal("\n" + " x" + amount)
			.withStyle(ChatFormatting.BLACK))
			.append(Component.literal(" | " + stacks + "▤ +" + remainder + (forBook ? "\n" : ""))
				.withStyle(ChatFormatting.GRAY));
	}
}

package dev.createpolymer.schematics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.redstone.displayLink.DisplayText;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's NBTProcessors (from Catnip) with CreateNBTProcessors: filters block entity data that is copied by
 * schematics, so that nothing a player could use to run commands (click events in signs and books) gets through.
 */
public final class NBTProcessors {

	private static final Map<BlockEntityType<?>, UnaryOperator<CompoundTag>> processors = new HashMap<>();
	private static final Map<BlockEntityType<?>, UnaryOperator<CompoundTag>> survivalProcessors = new HashMap<>();

	public static synchronized void addProcessor(BlockEntityType<?> type, UnaryOperator<CompoundTag> processor) {
		processors.put(type, processor);
	}

	public static synchronized void addSurvivalProcessor(BlockEntityType<?> type,
		UnaryOperator<CompoundTag> processor) {
		survivalProcessors.put(type, processor);
	}

	// Triggered by block tag, not BE type
	private static UnaryOperator<CompoundTag> signProcessor(HolderLookup.Provider registries) {
		return data -> {
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
			for (String key : java.util.List.of("front_text", "back_text")) {
				Tag textTag = data.get(key);
				if (textTag == null)
					continue;
				SignText text = SignText.CODEC.parse(ops, textTag)
					.result()
					.orElse(null);
				if (text == null)
					continue;
				for (boolean filtered : new boolean[] { false, true })
					for (Component message : text.getMessages(filtered))
						if (textComponentHasClickEvent(message))
							return null;
			}
			return data;
		};
	}

	/** Strips the components of an item stored under tagKey that could carry something unsafe. */
	public static UnaryOperator<CompoundTag> itemProcessor(String tagKey) {
		return data -> {
			CompoundTag compound = data.getCompoundOrEmpty(tagKey);
			CompoundTag itemComponents = compound.getCompoundOrEmpty("components");
			if (itemComponents.isEmpty())
				return data;
			Set<String> keys = new HashSet<>(itemComponents.keySet());
			for (String key : keys) {
				Identifier id = Identifier.tryParse(key.startsWith("!") ? key.substring(1) : key);
				DataComponentType<?> type = id == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
				if (type != null && SchematicHelper.isUnsafeItemComponent(type))
					itemComponents.remove(key);
			}
			if (itemComponents.isEmpty())
				compound.remove("components");
			return data;
		};
	}

	public static boolean textComponentHasClickEvent(Component component) {
		return component != null && DisplayText.hasClickEvent(component);
	}

	private NBTProcessors() {}

	@Nullable
	public static CompoundTag process(BlockState state, BlockEntity blockEntity, @Nullable CompoundTag compound,
		boolean survival, HolderLookup.Provider registries) {
		if (compound == null)
			return null;
		BlockEntityType<?> type = blockEntity.getType();
		if (survival && survivalProcessors.containsKey(type))
			compound = survivalProcessors.get(type)
				.apply(compound);
		if (compound != null && processors.containsKey(type))
			return processors.get(type)
				.apply(compound);
		if (blockEntity instanceof SpawnerBlockEntity)
			return compound;
		if (state.is(BlockTags.ALL_SIGNS))
			return signProcessor(registries).apply(compound);
		if (blockEntity.getType()
			.onlyOpCanSetNbt())
			return null;
		return compound;
	}

	/** Create's CreateNBTProcessors.register, for the block entities that exist here. */
	public static void registerDefaults() {
		addProcessor(net.minecraft.world.level.block.entity.BlockEntityTypes.LECTERN, data -> {
			CompoundTag book = data.getCompoundOrEmpty("Book");
			if (book.isEmpty())
				return data;

			// Writable books can't have click events, so they're safe to keep
			if (book.getStringOr("id", "")
				.equals(BuiltInRegistries.ITEM.getKey(Items.WRITABLE_BOOK)
					.toString()))
				return data;

			CompoundTag components = book.getCompoundOrEmpty("components");
			WrittenBookContent bookContent = components.read(Identifier.withDefaultNamespace("written_book_content")
				.toString(), WrittenBookContent.CODEC)
				.orElse(null);
			if (bookContent == null)
				return data;

			for (var page : bookContent.pages())
				if (textComponentHasClickEvent(page.raw()))
					return null;

			return data;
		});
	}
}

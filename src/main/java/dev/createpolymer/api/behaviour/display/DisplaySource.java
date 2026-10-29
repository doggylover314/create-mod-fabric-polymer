package dev.createpolymer.api.behaviour.display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.DisplayText;
import dev.createpolymer.redstone.displayLink.ModularGuiLineBuilder;
import dev.createpolymer.redstone.displayLink.target.DisplayBoardTarget;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import dev.createpolymer.trains.display.FlapDisplayBlockEntity;
import dev.createpolymer.trains.display.FlapDisplayLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Something a display link can read from. Associate sources with the blocks (or block entity types) they read with
 * {@link #BY_BLOCK} and {@link #BY_BLOCK_ENTITY}; the sources of a block are offered in the order they were added.
 */
public abstract class DisplaySource {
	/** Create's SimpleRegistry.Multi: every key has a list of sources. */
	public static class Multi<K> {
		private final Map<K, List<DisplaySource>> map = new IdentityHashMap<>();

		public synchronized void add(K key, DisplaySource source) {
			List<DisplaySource> list = map.computeIfAbsent(key, k -> new ArrayList<>());
			if (!list.contains(source))
				list.add(source);
		}

		public synchronized List<DisplaySource> get(K key) {
			List<DisplaySource> list = map.get(key);
			return list == null ? Collections.emptyList() : List.copyOf(list);
		}
	}

	public static final Multi<Block> BY_BLOCK = new Multi<>();
	public static final Multi<BlockEntityType<?>> BY_BLOCK_ENTITY = new Multi<>();

	private static final Map<Identifier, DisplaySource> REGISTRY = new LinkedHashMap<>();
	private static final Map<DisplaySource, Identifier> IDS = new IdentityHashMap<>();
	/** Create's AllDisplaySources.LEGACY_NAMES: ids of Create versions before the sources got their own registry. */
	public static final Map<String, DisplaySource> LEGACY_NAMES = new LinkedHashMap<>();

	public static final List<MutableComponent> EMPTY = ImmutableList.of(Component.empty());
	public static final MutableComponent EMPTY_LINE = Component.empty();
	public static final MutableComponent WHITESPACE = CommonComponents.space();

	/** Registers a source under the id {@code create:<path>}. */
	public static <T extends DisplaySource> T register(String path, T source) {
		Identifier id = CreatePolymer.id(path);
		if (REGISTRY.putIfAbsent(id, source) != null)
			throw new IllegalArgumentException("Duplicate display source " + id);
		IDS.put(source, id);
		return source;
	}

	public static List<DisplaySource> all() {
		return List.copyOf(REGISTRY.values());
	}

	public abstract List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats);

	public void transferData(DisplayLinkContext context, DisplayTarget activeTarget, int line) {
		DisplayTargetStats stats = activeTarget.provideStats(context);

		if (activeTarget instanceof DisplayBoardTarget fddt) {
			List<List<MutableComponent>> flapDisplayText = provideFlapDisplayText(context, stats).stream()
				.map(l -> l.stream()
					.map(DisplayText::resolve)
					.toList())
				.toList();
			fddt.acceptFlapText(line, flapDisplayText, context);
		}

		List<MutableComponent> text = provideText(context, stats);
		if (text.isEmpty())
			text = EMPTY;
		text = text.stream()
			.map(DisplayText::resolve)
			.toList();

		if (activeTarget.requiresComponentSanitization())
			for (MutableComponent component : text)
				if (DisplayText.hasClickEvent(component))
					return; // Naughty

		activeTarget.acceptText(line, text, context);
	}

	public void onSignalReset(DisplayLinkContext context) {
	}

	public void populateData(DisplayLinkContext context) {
	}

	public int getPassiveRefreshTicks() {
		return 100;
	}

	public boolean shouldPassiveReset() {
		return true;
	}

	protected final Identifier getId() {
		return IDS.get(this);
	}

	protected String getTranslationKey() {
		return this.getId().getPath();
	}

	public Component getName() {
		return Component.translatable(this.getId().getNamespace() + ".display_source." + getTranslationKey());
	}

	public void loadFlapDisplayLayout(DisplayLinkContext context, FlapDisplayBlockEntity flapDisplay, FlapDisplayLayout layout, int lineIndex) {
		loadFlapDisplayLayout(context, flapDisplay, layout);
	}

	public void loadFlapDisplayLayout(DisplayLinkContext context, FlapDisplayBlockEntity flapDisplay,
									  FlapDisplayLayout layout) {
		if (!layout.isLayout("Default"))
			layout.loadDefault(flapDisplay.getMaxCharCount());
	}

	public List<List<MutableComponent>> provideFlapDisplayText(DisplayLinkContext context, DisplayTargetStats stats) {
		return provideText(context, stats).stream()
			.map(Arrays::asList)
			.toList();
	}

	/** Describes the options of this source for the given line (Create: initConfigurationWidgets). */
	public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder,
										 boolean isFirstLine) {
	}

	/**
	 * Get the DisplaySource with the given ID, accounting for legacy names.
	 */
	@Nullable
	public static DisplaySource get(@Nullable Identifier id) {
		if (id == null)
			return null;

		if (id.getNamespace().equals(CreatePolymer.MOD_ID) && LEGACY_NAMES.containsKey(id.getPath()))
			return LEGACY_NAMES.get(id.getPath());

		return REGISTRY.get(id);
	}

	/** The id a source was registered under. */
	@Nullable
	public static Identifier getKey(DisplaySource source) {
		return IDS.get(source);
	}

	/**
	 * Get all DisplaySources applicable to the block at the given location, checking both the Block and BlockEntity.
	 * Returns an empty list if none are present, not null.
	 */
	public static List<DisplaySource> getAll(LevelAccessor level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		List<DisplaySource> byBlock = BY_BLOCK.get(state.getBlock());

		BlockEntity be = level.getBlockEntity(pos);
		if (be == null)
			return byBlock;

		List<DisplaySource> byBe = BY_BLOCK_ENTITY.get(be.getType());

		if (byBlock.isEmpty()) {
			if (byBe.isEmpty()) {
				// none
				return List.of();
			} else {
				// only BlockEntity
				return byBe;
			}
		} else if (byBe.isEmpty()) {
			// only Block
			return byBlock;
		} else {
			// both present, combine
			List<DisplaySource> combined = new ArrayList<>(byBlock);
			combined.addAll(byBe);
			return combined;
		}
	}
}

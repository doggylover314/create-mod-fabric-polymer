package dev.createpolymer.api.behaviour.display;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.api.registry.SimpleRegistry;
import dev.createpolymer.redstone.displayLink.DisplayLinkBlock;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.target.DisplayTargetStats;
import dev.createpolymer.registry.feature.DisplayRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Something a display link can write to. Associate targets with their blocks (or block entity types) using
 * {@link #BY_BLOCK} and {@link #BY_BLOCK_ENTITY}.
 */
public abstract class DisplayTarget {
	public static final SimpleRegistry<Block, DisplayTarget> BY_BLOCK = SimpleRegistry.create();
	public static final SimpleRegistry<BlockEntityType<?>, DisplayTarget> BY_BLOCK_ENTITY = SimpleRegistry.create();

	private static final Map<Identifier, DisplayTarget> REGISTRY = new LinkedHashMap<>();
	private static final Map<DisplayTarget, Identifier> IDS = new IdentityHashMap<>();
	/** Create's AllDisplayTargets.LEGACY_NAMES. */
	public static final Map<String, DisplayTarget> LEGACY_NAMES = new LinkedHashMap<>();

	/**
	 * Lines of a target that links have written to. Create keeps this in the persistent data of the block entity;
	 * Fabric block entities have none, so it lives here until the block entity is unloaded (the link claims its lines
	 * again at its next update).
	 */
	private static final Map<BlockEntity, Map<Integer, Long>> RESERVATIONS = Collections.synchronizedMap(new WeakHashMap<>());

	/** Registers a target under the id {@code create:<path>}. */
	public static <T extends DisplayTarget> T register(String path, T target) {
		Identifier id = CreatePolymer.id(path);
		if (REGISTRY.putIfAbsent(id, target) != null)
			throw new IllegalArgumentException("Duplicate display target " + id);
		IDS.put(target, id);
		return target;
	}

	public static List<DisplayTarget> all() {
		return List.copyOf(REGISTRY.values());
	}

	public abstract void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context);

	public abstract DisplayTargetStats provideStats(DisplayLinkContext context);

	public AABB getMultiblockBounds(LevelAccessor level, BlockPos pos) {
		VoxelShape shape = level.getBlockState(pos)
			.getShape(level, pos);
		if (shape.isEmpty())
			return new AABB(pos);
		return shape.bounds()
			.move(pos);
	}

	public Component getLineOptionText(int line) {
		return Component.translatable("create.display_target.line", line + 1);
	}

	public static void reserve(int line, BlockEntity target, DisplayLinkContext context) {
		if (line == 0)
			return;

		RESERVATIONS.computeIfAbsent(target, be -> new java.util.concurrent.ConcurrentHashMap<>())
			.put(line, context.blockEntity()
				.getBlockPos()
				.asLong());
	}

	public boolean isReserved(int line, BlockEntity target, DisplayLinkContext context) {
		Map<Integer, Long> reserved = RESERVATIONS.get(target);
		if (reserved == null || !reserved.containsKey(line))
			return false;

		BlockPos reservedBy = BlockPos.of(reserved.get(line));

		if (!reservedBy.equals(context.blockEntity()
			.getBlockPos()) && target.getLevel()
				.getBlockState(reservedBy)
				.getBlock() instanceof DisplayLinkBlock)
			return true;

		reserved.remove(line);
		if (reserved.isEmpty())
			RESERVATIONS.remove(target);
		return false;
	}

	public boolean requiresComponentSanitization() {
		return false;
	}

	/**
	 * Get the DisplayTarget with the given ID, accounting for legacy names.
	 */
	@Nullable
	public static DisplayTarget get(@Nullable Identifier id) {
		if (id == null)
			return null;

		if (id.getNamespace().equals(CreatePolymer.MOD_ID) && LEGACY_NAMES.containsKey(id.getPath()))
			return LEGACY_NAMES.get(id.getPath());

		return REGISTRY.get(id);
	}

	/** The id a target was registered under. */
	@Nullable
	public static Identifier getKey(DisplayTarget target) {
		return IDS.get(target);
	}

	/**
	 * Get the DisplayTarget applicable to the given location, or null if there isn't one.
	 */
	@Nullable
	public static DisplayTarget get(LevelAccessor level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		DisplayTarget byBlock = BY_BLOCK.get(state);
		// block takes priority if present, it's more granular
		if (byBlock != null)
			return byBlock;

		BlockEntity be = level.getBlockEntity(pos);
		if (be == null)
			return null;

		DisplayTarget byBe = BY_BLOCK_ENTITY.get(be.getType());
		if (byBe != null)
			return byBe;

		// special case: modded signs are common
		return be instanceof SignBlockEntity ? DisplayRegistry.SIGN : null;
	}
}

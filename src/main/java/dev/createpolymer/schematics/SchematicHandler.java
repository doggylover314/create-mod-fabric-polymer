package dev.createpolymer.schematics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.foundation.block.BlockHelper;
import dev.createpolymer.registry.feature.SchematicsRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Create's SchematicHandler and its deploy tool, for a server: the position, rotation and mirror of the schematic a
 * player holds live in the item (SchematicSyncPacket's job on a Create client); the outline of the box the
 * structure takes up is shown to the player holding it, and {@link SchematicPlaceGui} has the tools to change it.
 */
public final class SchematicHandler {
	private static final Map<UUID, PlayerBoxOutline> OUTLINES = new HashMap<>();

	private SchematicHandler() {}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList()
			.getPlayers()) {
			ItemStack held = player.getMainHandItem();
			AABB box = null;
			if (SchematicItem.isSchematic(held) && held.has(SchematicsRegistry.SCHEMATIC_FILE)
				&& held.getOrDefault(SchematicsRegistry.SCHEMATIC_DEPLOYED, false))
				box = worldBox(held);

			PlayerBoxOutline outline = OUTLINES.get(player.getUUID());
			if (box == null) {
				if (outline != null) {
					outline.remove();
					OUTLINES.remove(player.getUUID());
				}
				continue;
			}
			if (outline == null)
				OUTLINES.put(player.getUUID(), outline = new PlayerBoxOutline(player));
			outline.set(box);
			outline.tick();
		}
	}

	public static void onDisconnect(ServerPlayer player) {
		PlayerBoxOutline outline = OUTLINES.remove(player.getUUID());
		if (outline != null)
			outline.remove();
	}

	public static void clear() {
		OUTLINES.values()
			.forEach(PlayerBoxOutline::remove);
		OUTLINES.clear();
	}

	/** Whether the player is being shown the outline of a deployed schematic */
	public static boolean isShowingOutline(ServerPlayer player) {
		PlayerBoxOutline outline = OUTLINES.get(player.getUUID());
		return outline != null && outline.isShowing();
	}

	@Nullable
	public static AABB getOutlineBox(ServerPlayer player) {
		PlayerBoxOutline outline = OUTLINES.get(player.getUUID());
		return outline == null ? null : outline.getBox();
	}

	/** The box a deployed schematic takes up in the world */
	@Nullable
	public static AABB worldBox(ItemStack schematic) {
		Vec3i size = schematic.get(SchematicsRegistry.SCHEMATIC_BOUNDS);
		if (size == null || size.getX() < 1 || size.getY() < 1 || size.getZ() < 1)
			return null;
		BlockPos anchor = schematic.getOrDefault(SchematicsRegistry.SCHEMATIC_ANCHOR, BlockPos.ZERO);
		StructurePlaceSettings settings = SchematicItem.getSettings(schematic, false);
		Vec3i delta = size.offset(-1, -1, -1);
		BlockPos corner1 = StructureTemplate.transform(BlockPos.ZERO, settings.getMirror(), settings.getRotation(),
			settings.getRotationPivot());
		BlockPos corner2 = StructureTemplate.transform(BlockPos.ZERO.offset(delta), settings.getMirror(),
			settings.getRotation(), settings.getRotationPivot());
		BoundingBox bb = BoundingBox.fromCorners(corner1, corner2)
			.moved(anchor.getX(), anchor.getY(), anchor.getZ());
		return new AABB(bb.minX(), bb.minY(), bb.minZ(), bb.maxX() + 1, bb.maxY() + 1, bb.maxZ() + 1);
	}

	/** Create's SchematicHandler.loadSettings */
	@Nullable
	public static SchematicTransformation loadTransformation(Level level, ItemStack blueprint) {
		Vec3i size = blueprint.get(SchematicsRegistry.SCHEMATIC_BOUNDS);
		if (size == null) {
			SchematicItem.writeSize(level, blueprint);
			size = blueprint.get(SchematicsRegistry.SCHEMATIC_BOUNDS);
		}
		if (size == null)
			return null;
		SchematicTransformation transformation = new SchematicTransformation();
		transformation.init(blueprint.getOrDefault(SchematicsRegistry.SCHEMATIC_ANCHOR, BlockPos.ZERO),
			SchematicItem.getSettings(blueprint), size);
		return transformation;
	}

	/** Create's SchematicSyncPacket.handle: what the tools changed goes into the item */
	public static void store(ItemStack blueprint, SchematicTransformation transformation, boolean deployed) {
		StructurePlaceSettings settings = transformation.toSettings();
		blueprint.set(SchematicsRegistry.SCHEMATIC_DEPLOYED, deployed);
		blueprint.set(SchematicsRegistry.SCHEMATIC_ANCHOR, transformation.getAnchor());
		blueprint.set(SchematicsRegistry.SCHEMATIC_ROTATION, settings.getRotation());
		blueprint.set(SchematicsRegistry.SCHEMATIC_MIRROR, settings.getMirror());
		SchematicInstances.clearHash(blueprint);
	}

	/**
	 * Create's SchematicToolBase.updateTargetPos: the block a player points at, or the block next to it when that is
	 * the top or bottom of a block that can't be built into
	 */
	@Nullable
	public static BlockPos selectedPos(ServerPlayer player, double range) {
		Level level = player.level();
		Vec3 origin = player.getEyePosition();
		Vec3 target = origin.add(player.getLookAngle()
			.scale(range));
		BlockHitResult trace = level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		if (trace == null || trace.getType() != HitResult.Type.BLOCK)
			return null;
		return selectedPos(player, trace.getBlockPos(), trace.getDirection(), trace);
	}

	public static BlockPos selectedPos(ServerPlayer player, BlockPos clicked, Direction face,
		@Nullable BlockHitResult trace) {
		BlockPos hit = clicked;
		BlockState state = player.level()
			.getBlockState(hit);
		boolean replaceable = trace == null ? state.canBeReplaced()
			: state.canBeReplaced(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, trace)));
		if (face.getAxis()
			.isVertical() && !replaceable)
			hit = hit.relative(face);
		return hit;
	}

	/**
	 * Create's DeployTool.handleRightClick: the structure is placed with the middle of its box at the position and
	 * the player gets to see it.
	 */
	public static void deployAt(ServerPlayer player, BlockPos clicked, Direction face) {
		deployAtSelected(player, selectedPos(player, clicked, face, null));
	}

	public static boolean deployAtSelected(ServerPlayer player, BlockPos selectedPos) {
		ItemStack item = player.getMainHandItem();
		if (!SchematicItem.isSchematic(item) || !item.has(SchematicsRegistry.SCHEMATIC_FILE))
			return false;
		SchematicTransformation transformation = loadTransformation(player.level(), item);
		if (transformation == null)
			return false;
		Vec3i size = item.get(SchematicsRegistry.SCHEMATIC_BOUNDS);
		if (size == null || size.getX() < 1) {
			player.sendOverlayMessage(Component.translatable("create.schematic.error")
				.withStyle(ChatFormatting.RED));
			return false;
		}

		BlockPos target = selectedPos.offset(-(size.getX() / 2), 0, -(size.getZ() / 2));
		transformation.moveTo(target);
		store(item, transformation, true);
		player.sendOverlayMessage(Component.translatable("create.schematic.position")
			.append(Component.literal(": " + item.get(SchematicsRegistry.SCHEMATIC_ANCHOR)
				.toShortString())));
		return true;
	}

	/**
	 * Create's SchematicPlacePacket.handle (the print tool): places the whole structure at once. Creative players
	 * only; operators in creative also place what only they may place.
	 */
	public static boolean printInstantly(ServerPlayer player, ItemStack stack) {
		return printInstantly(player, stack, SchematicsRegistry.creativePrintIncludesAir);
	}

	/** The same with the setting that decides whether air replaces blocks given */
	public static boolean printInstantly(ServerPlayer player, ItemStack stack, boolean includeAir) {
		if (!player.isCreative())
			return false;

		Level level = player.level();
		SchematicPrinter printer = new SchematicPrinter();
		printer.loadSchematic(stack, level, !player.canUseGameMasterBlocks());
		if (!printer.isLoaded() || printer.isErrored())
			return false;

		while (printer.advanceCurrentPos()) {
			if (!printer.shouldPlaceCurrent(level))
				continue;

			printer.handleCurrentTarget((pos, state, blockEntity) -> {
				boolean placingAir = state.isAir();
				if (placingAir && !includeAir)
					return;

				CompoundTag data = BlockHelper.prepareBlockEntityData(level, state, blockEntity);
				BlockHelper.placeSchematicBlock(level, state, pos, ItemStack.EMPTY, data);
			}, (pos, entity) -> {
				level.addFreshEntity(entity);
			});
		}
		return true;
	}
}

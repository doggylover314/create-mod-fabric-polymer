package dev.createpolymer.equipment.zapper.terrainzapper;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.equipment.zapper.PlacementPatterns;
import dev.createpolymer.equipment.zapper.ZapperItem;
import dev.createpolymer.registry.feature.WandsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WorldshaperItem extends ZapperItem {

	public WorldshaperItem(Properties properties) {
		super(properties);
	}

	@Override
	protected void openHandgunGUI(ServerPlayer player, ItemStack item, InteractionHand hand) {
		new WorldshaperGui(player, item, hand).open();
	}

	@Override
	protected int getZappingRange(ItemStack stack) {
		return 128;
	}

	@Override
	protected int getCooldownDelay(ItemStack item) {
		return 2;
	}

	@Override
	public @Nullable Component validateUsage(ItemStack item) {
		if (!item.has(WandsRegistry.SHAPER_BRUSH_PARAMS))
			return Component.translatable("create.terrainzapper.shiftRightClickToSet");
		return super.validateUsage(item);
	}

	@Override
	protected boolean canActivateWithoutSelectedBlock(ItemStack stack) {
		TerrainTools tool = stack.getOrDefault(WandsRegistry.SHAPER_TOOL, TerrainTools.Fill);
		return !tool.requiresSelectedBlock();
	}

	@Override
	protected boolean activate(Level level, Player player, ItemStack stack, BlockState stateToUse,
		BlockHitResult raytrace, CompoundTag data) {

		BlockPos targetPos = raytrace.getBlockPos();
		List<BlockPos> affectedPositions = new ArrayList<>();

		Brush brush = stack.getOrDefault(WandsRegistry.SHAPER_BRUSH, TerrainBrushes.Cuboid).get();
		BlockPos params = stack.get(WandsRegistry.SHAPER_BRUSH_PARAMS);
		PlacementOptions option = stack.getOrDefault(WandsRegistry.SHAPER_PLACEMENT_OPTIONS, PlacementOptions.Merged);
		TerrainTools tool = stack.getOrDefault(WandsRegistry.SHAPER_TOOL, TerrainTools.Fill);

		// the sizes are limited by the brush, whatever the item holds
		brush.set(brush.clampParam(0, params.getX()), brush.clampParam(1, params.getY()),
			brush.clampParam(2, params.getZ()));
		targetPos = targetPos.offset(brush.getOffset(player.getLookAngle(), raytrace.getDirection(), option));
		brush.addToGlobalPositions(level, targetPos, raytrace.getDirection(), affectedPositions, tool);
		PlacementPatterns.applyPattern(affectedPositions, stack, level.getRandom());
		brush.redirectTool(tool)
			.run(level, affectedPositions, raytrace.getDirection(), stateToUse, data, player);

		return true;
	}

	/**
	 * Stores the settings on the item. The sizes are limited to what the brush allows (Create's screen does that
	 * with its scroll inputs, the server never sees them out of range there).
	 */
	public static void configureSettings(ItemStack stack, PlacementPatterns pattern, TerrainBrushes brush,
		int brushParamX, int brushParamY, int brushParamZ, TerrainTools tool, PlacementOptions placement) {
		Brush b = brush.get();
		// like the screen, which switches to the first tool the brush supports
		if (!java.util.Arrays.asList(b.getSupportedTools())
			.contains(tool))
			tool = b.getSupportedTools()[0];
		stack.set(WandsRegistry.PLACEMENT_PATTERN, pattern);
		stack.set(WandsRegistry.SHAPER_BRUSH, brush);
		stack.set(WandsRegistry.SHAPER_BRUSH_PARAMS,
			new BlockPos(b.clampParam(0, brushParamX), b.clampParam(1, brushParamY), b.clampParam(2, brushParamZ)));
		stack.set(WandsRegistry.SHAPER_TOOL, tool);
		stack.set(WandsRegistry.SHAPER_PLACEMENT_OPTIONS, placement);
	}
}

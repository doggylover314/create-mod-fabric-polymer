package dev.createpolymer.equipment.zapper.terrainzapper;

import dev.createpolymer.foundation.block.BlockHelper;
import dev.createpolymer.equipment.zapper.ZapperGui;
import dev.createpolymer.registry.feature.WandsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

/**
 * Server side stand-in for Create's WorldshaperScreen, a 9x6 chest GUI:
 * <pre>
 * row 0     Brush label, the five brushes, [the block that is used]
 * row 1-3   the parameters of the brush: label, -10 -1 value +1 +10 (or the two switches of the dynamic brushes)
 * row 4     Tool label, the tools of the brush, [done]
 * row 5     the placement options (if the brush has them), the six patterns
 * </pre>
 */
public class WorldshaperGui extends ZapperGui {

	private static final int LABEL = 0;
	private static final int MINUS_10 = 2;
	private static final int MINUS_1 = 3;
	private static final int VALUE = 4;
	private static final int PLUS_1 = 5;
	private static final int PLUS_10 = 6;

	protected TerrainBrushes currentBrush;
	protected int[] currentBrushParams = new int[] { 1, 1, 1 };
	protected boolean currentFollowDiagonals;
	protected boolean currentAcrossMaterials;
	protected TerrainTools currentTool;
	protected PlacementOptions currentPlacement;

	public WorldshaperGui(ServerPlayer player, ItemStack zapper, InteractionHand hand) {
		super(MenuType.GENERIC_9x6, player, zapper, hand);

		currentBrush = zapper.getOrDefault(WandsRegistry.SHAPER_BRUSH, TerrainBrushes.Cuboid);
		if (zapper.has(WandsRegistry.SHAPER_BRUSH_PARAMS)) {
			BlockPos paramsData = zapper.get(WandsRegistry.SHAPER_BRUSH_PARAMS);
			currentBrushParams[0] = paramsData.getX();
			currentBrushParams[1] = paramsData.getY();
			currentBrushParams[2] = paramsData.getZ();
			if (currentBrushParams[1] == 0) {
				currentFollowDiagonals = true;
			}
			if (currentBrushParams[2] == 0) {
				currentAcrossMaterials = true;
			}
		}
		currentTool = zapper.getOrDefault(WandsRegistry.SHAPER_TOOL, TerrainTools.Fill);
		currentPlacement = zapper.getOrDefault(WandsRegistry.SHAPER_PLACEMENT_OPTIONS, PlacementOptions.Merged);
		initBrushParams();
		refresh();
	}

	/** Keeps the parameters and the tool within what the current brush allows. */
	protected void initBrushParams() {
		Brush brush = currentBrush.get();
		for (int index = 0; index < 3; index++)
			if (index < brush.getAmtParams())
				currentBrushParams[index] = brush.clampParam(index, currentBrushParams[index]);

		TerrainTools[] toolValues = brush.getSupportedTools();
		boolean supported = false;
		for (TerrainTools tool : toolValues)
			supported |= tool == currentTool;
		if (!supported)
			currentTool = toolValues[0];
	}

	@Override
	protected void refresh() {
		for (int i = 0; i < getSize(); i++)
			clearSlot(i);
		Brush brush = currentBrush.get();

		// Brush
		setSlot(LABEL, label(Component.translatable("create.gui.terrainzapper.brush")));
		for (TerrainBrushes b : TerrainBrushes.values())
			setSlot(1 + b.ordinal(), button(b.icon, 1,
				Component.translatable("create.gui.terrainzapper.brush." + b.getSerializedName()), b == currentBrush, () -> {
					currentBrush = b;
					initBrushParams();
					save();
					refresh();
				}));

		if (zapper.has(WandsRegistry.SHAPER_BLOCK_USED)
			&& !BlockHelper.getRequiredItem(zapper.get(WandsRegistry.SHAPER_BLOCK_USED))
				.isEmpty()) {
			BlockState used = zapper.get(WandsRegistry.SHAPER_BLOCK_USED);
			setSlot(8, new GuiElementBuilder(BlockHelper.getRequiredItem(used)
				.getItem())
				.setName(Component.translatable("create.terrainzapper.usingBlock", used.getBlock()
					.getName()))
				.hideDefaultTooltip());
		}

		// Brush Params
		for (int index = 0; index < 3; index++) {
			int row = (1 + index) * 9;
			if (index < brush.getAmtParams()) {
				sizeRow(brush, index, row);
			} else if (brush.hasConnectivityOptions() && index == 1) {
				setSlot(row + LABEL, label(Component.translatable("create.gui.terrainzapper.searchDiagonal")));
				setSlot(row + VALUE, toggle(Component.translatable("create.gui.terrainzapper.searchDiagonal"),
					currentFollowDiagonals, () -> {
						currentFollowDiagonals = !currentFollowDiagonals;
						save();
						refresh();
					}));
			} else if (brush.hasConnectivityOptions() && index == 2) {
				setSlot(row + LABEL, label(Component.translatable("create.gui.terrainzapper.searchFuzzy")));
				setSlot(row + VALUE, toggle(Component.translatable("create.gui.terrainzapper.searchFuzzy"),
					currentAcrossMaterials, () -> {
						currentAcrossMaterials = !currentAcrossMaterials;
						save();
						refresh();
					}));
			}
		}

		// Tools
		int toolRow = 4 * 9;
		setSlot(toolRow + LABEL, label(Component.translatable("create.gui.terrainzapper.tool")));
		TerrainTools[] toolValues = brush.getSupportedTools();
		for (int id = 0; id < toolValues.length; id++) {
			TerrainTools tool = toolValues[id];
			setSlot(toolRow + 1 + id, button(tool.icon, 1,
				Component.translatable("create.gui.terrainzapper.tool." + tool.translationKey), tool == currentTool, () -> {
					currentTool = tool;
					save();
					refresh();
				}));
		}
		setSlot(toolRow + 8, doneButton());

		// Placement Options and Patterns
		int lastRow = 5 * 9;
		if (brush.hasPlacementOptions())
			for (PlacementOptions option : PlacementOptions.values())
				setSlot(lastRow + option.ordinal(), button(option.icon, 1,
					Component.translatable("create.gui.terrainzapper.placement")
						.append(": ")
						.append(Component.translatable("create.gui.terrainzapper.placement." + option.translationKey)),
					option == currentPlacement, () -> {
						currentPlacement = option;
						save();
						refresh();
					}));
		patternButtons(lastRow + 3);
	}

	private void sizeRow(Brush brush, int index, int row) {
		int min = brush.getMin(index);
		int max = brush.getMax(index);
		int value = currentBrushParams[index];

		setSlot(row + LABEL, label(brush.getParamLabel(index)));
		setSlot(row + VALUE, new GuiElementBuilder(net.minecraft.world.item.Items.PAPER)
			.setCount(Math.max(1, Math.min(64, value)))
			.setName(brush.getParamLabel(index)
				.copy()
				.append(": " + value)
				.withStyle(net.minecraft.ChatFormatting.GOLD))
			.hideDefaultTooltip());
		if (value > min) {
			setSlot(row + MINUS_1, stepButton(false, -1, () -> step(brush, index, -1)));
			if (max - min >= 10)
				setSlot(row + MINUS_10, stepButton(false, -10, () -> step(brush, index, -10)));
		}
		if (value < max) {
			setSlot(row + PLUS_1, stepButton(true, 1, () -> step(brush, index, 1)));
			if (max - min >= 10)
				setSlot(row + PLUS_10, stepButton(true, 10, () -> step(brush, index, 10)));
		}
	}

	private void step(Brush brush, int index, int delta) {
		currentBrushParams[index] = brush.clampParam(index, currentBrushParams[index] + delta);
		save();
		refresh();
	}

	@Override
	protected void save() {
		Brush brush = currentBrush.get();
		int brushParamX = currentBrushParams[0];
		int brushParamY = brush.hasConnectivityOptions() ? currentFollowDiagonals ? 0 : 1 : currentBrushParams[1];
		int brushParamZ = brush.hasConnectivityOptions() ? currentAcrossMaterials ? 0 : 1 : currentBrushParams[2];
		WorldshaperItem.configureSettings(zapper, currentPattern, currentBrush, brushParamX, brushParamY, brushParamZ,
			currentTool, currentPlacement);
	}

}

package dev.createpolymer.schematics;

import dev.createpolymer.registry.feature.SchematicsRegistry;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The tools Create's client shows while a schematic is held (deploy, move, move vertically, rotate, mirror and print
 * in creative), as a chest GUI. Every button changes the schematic item in the main hand of the player, the way
 * Create's SchematicSyncPacket does; the outline of the structure follows.
 * <pre>
 *  . . . . info . . . .
 *  step | -X +X | . | -Y +Y | . | -Z +Z
 *  rotate left | rotate right | . | mirror left-right | mirror front-back | . | position at looking | position at feet | print
 *  close | . | . | . | pick up | . | . | . | .
 * </pre>
 */
public class SchematicPlaceGui extends SimpleGui {

	public static final int INFO = 4;
	public static final int STEP = 9;
	public static final int MOVE_X = 10;
	public static final int MOVE_Y = 13;
	public static final int MOVE_Z = 16;
	public static final int ROTATE = 18;
	public static final int MIRROR = 21;
	public static final int LOOKING = 24;
	public static final int FEET = 25;
	public static final int PRINT = 26;
	public static final int CLOSE = 27;
	public static final int PICK_UP = 31;

	private static final int[] STEPS = { 1, 5, 20 };

	private int stepIndex;

	public static void open(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (!SchematicItem.isSchematic(stack) || !stack.has(SchematicsRegistry.SCHEMATIC_FILE))
			return;
		new SchematicPlaceGui(player).open();
	}

	public SchematicPlaceGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x4, player, false);
		setLockPlayerInventory(true);
		setTitle(Component.translatable("create.schematic.position"));
		refresh();
	}

	private ItemStack held() {
		ItemStack stack = player.getMainHandItem();
		return SchematicItem.isSchematic(stack) && stack.has(SchematicsRegistry.SCHEMATIC_FILE) ? stack : ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid() {
		return !held().isEmpty();
	}

	private boolean deployed() {
		return held().getOrDefault(SchematicsRegistry.SCHEMATIC_DEPLOYED, false);
	}

	private int step() {
		return STEPS[stepIndex];
	}

	private static GuiElementBuilder filler() {
		return new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray())
			.setName(Component.empty())
			.hideTooltip();
	}

	private void refresh() {
		for (int i = 0; i < 36; i++)
			setSlot(i, filler());
		ItemStack stack = held();
		if (stack.isEmpty())
			return;
		boolean deployed = deployed();

		// the schematic
		BlockPos anchor = stack.getOrDefault(SchematicsRegistry.SCHEMATIC_ANCHOR, BlockPos.ZERO);
		Vec3i size = stack.get(SchematicsRegistry.SCHEMATIC_BOUNDS);
		Rotation rotation = stack.getOrDefault(SchematicsRegistry.SCHEMATIC_ROTATION, Rotation.NONE);
		Mirror mirror = stack.getOrDefault(SchematicsRegistry.SCHEMATIC_MIRROR, Mirror.NONE);
		GuiElementBuilder info = new GuiElementBuilder(Items.PAPER)
			.setName(Component.literal(stack.get(SchematicsRegistry.SCHEMATIC_FILE))
				.withStyle(ChatFormatting.GOLD))
			.hideDefaultTooltip();
		if (size != null)
			info.addLoreLine(Component.translatable("create.schematicAndQuill.dimensions", size.getX(), size.getY(),
				size.getZ())
				.withStyle(ChatFormatting.GRAY));
		if (deployed) {
			info.addLoreLine(label("create.schematic.position", Component.literal(anchor.toShortString())));
			info.addLoreLine(label("create.schematic.rotation", Component.translatable(rotationKey(rotation))));
			info.addLoreLine(label("create.schematic.mirror", Component.translatable(mirrorKey(mirror))));
		} else
			info.addLoreLine(Component.translatable("create.schematicannon.status.schematicNotPlaced")
				.withStyle(ChatFormatting.RED));
		setSlot(INFO, info);

		// how far the move buttons move
		setSlot(STEP, new GuiElementBuilder(Items.COMPASS)
			.setName(Component.translatableWithFallback("create.schematic.step", "Step: %s", step())
				.withStyle(ChatFormatting.WHITE))
			.hideDefaultTooltip()
			.setCallback(() -> {
				stepIndex = (stepIndex + 1) % STEPS.length;
				refresh();
			}));

		// the tools: Create's move (horizontally and vertically)
		axisButtons(MOVE_X, Items.CONCRETE.pick(DyeColor.RED), "X", 1, 0, 0, deployed);
		axisButtons(MOVE_Y, Items.CONCRETE.pick(DyeColor.LIME), "Y", 0, 1, 0, deployed);
		axisButtons(MOVE_Z, Items.CONCRETE.pick(DyeColor.BLUE), "Z", 0, 0, 1, deployed);

		// rotate
		setSlot(ROTATE, tool(Items.ARROW, "create.schematic.tool.rotate", deployed, ChatFormatting.WHITE, () -> rotate(false))
			.addLoreLine(Component.translatableWithFallback("create.schematic.counterclockwise", "Counterclockwise")
				.withStyle(ChatFormatting.GRAY)));
		setSlot(ROTATE + 1, tool(Items.ARROW, "create.schematic.tool.rotate", deployed, ChatFormatting.WHITE, () -> rotate(true))
			.addLoreLine(Component.translatableWithFallback("create.schematic.clockwise", "Clockwise")
				.withStyle(ChatFormatting.GRAY)));

		// mirror
		setSlot(MIRROR, tool(Items.GLASS_PANE, "create.schematic.mirror.leftRight", deployed, ChatFormatting.WHITE,
			() -> mirror(Axis.X)));
		setSlot(MIRROR + 1, tool(Items.GLASS_PANE, "create.schematic.mirror.frontBack", deployed, ChatFormatting.WHITE,
			() -> mirror(Axis.Z)));

		// Create's deploy tool, where the player points and where the player stands
		setSlot(LOOKING, tool(Items.ENDER_EYE, "create.schematic.tool.deploy", true, ChatFormatting.WHITE, this::deployLooking)
			.addLoreLine(Component.translatableWithFallback("create.schematic.deploy.looking",
				"At the block you are looking at")
				.withStyle(ChatFormatting.GRAY)));
		setSlot(FEET, tool(Items.LEATHER_BOOTS, "create.schematic.tool.deploy", true, ChatFormatting.WHITE, this::deployFeet)
			.addLoreLine(Component.translatableWithFallback("create.schematic.deploy.feet", "At your feet")
				.withStyle(ChatFormatting.GRAY)));

		// Create's print tool
		boolean print = deployed && player.isCreative();
		setSlot(PRINT, tool(Items.TNT, "create.schematic.tool.print", print, print ? ChatFormatting.RED : ChatFormatting.DARK_GRAY,
			this::print)
			.addLoreLine(Component.translatable("create.schematic.tool.print.description.0")
				.withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.translatable("create.schematic.tool.print.description.2")
				.withStyle(ChatFormatting.GRAY)));

		setSlot(PICK_UP, tool(Items.BARRIER, "create.schematic.tool.deploy", deployed, ChatFormatting.RED, this::pickUp)
			.addLoreLine(Component.translatableWithFallback("create.schematic.pickUp", "Remove the hologram")
				.withStyle(ChatFormatting.GRAY)));
		setSlot(CLOSE, new GuiElementBuilder(Items.EMERALD_BLOCK)
			.setName(Component.translatable("create.gui.value_settings.confirm"))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	private static MutableComponent label(String key, Component value) {
		return Component.translatable(key)
			.append(Component.literal(": "))
			.append(value)
			.withStyle(ChatFormatting.GRAY);
	}

	static String rotationKey(Rotation rotation) {
		return switch (rotation) {
			case NONE -> "create.schematic.rotation.none";
			case CLOCKWISE_90 -> "create.schematic.rotation.cw90";
			case CLOCKWISE_180 -> "create.schematic.rotation.cw180";
			case COUNTERCLOCKWISE_90 -> "create.schematic.rotation.cw270";
		};
	}

	static String mirrorKey(Mirror mirror) {
		return switch (mirror) {
			case NONE -> "create.schematic.mirror.none";
			case FRONT_BACK -> "create.schematic.mirror.frontBack";
			case LEFT_RIGHT -> "create.schematic.mirror.leftRight";
		};
	}

	private GuiElementBuilder tool(Item icon, String key, boolean enabled, ChatFormatting color, Runnable action) {
		GuiElementBuilder button = new GuiElementBuilder(enabled ? icon : Items.DYE.pick(DyeColor.GRAY))
			.setName(Component.translatable(key)
				.withStyle(enabled ? color : ChatFormatting.DARK_GRAY))
			.hideDefaultTooltip();
		if (enabled)
			button.setCallback(action);
		return button;
	}

	private void axisButtons(int slot, Item icon, String axis, int x, int y, int z, boolean enabled) {
		for (int sign = -1; sign <= 1; sign += 2) {
			int dx = x * sign * step();
			int dy = y * sign * step();
			int dz = z * sign * step();
			GuiElementBuilder button = new GuiElementBuilder(enabled ? icon : Items.DYE.pick(DyeColor.GRAY))
				.setName(Component.literal((sign < 0 ? "-" : "+") + step() + " " + axis)
					.withStyle(enabled ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY))
				.setCount(Math.min(64, step()))
				.hideDefaultTooltip();
			if (enabled)
				button.setCallback(() -> move(dx, dy, dz));
			setSlot(slot + (sign < 0 ? 0 : 1), button);
		}
	}

	// ---- the tools ----

	/** Runs a change on the transformation of the held schematic and puts the result back into the item */
	private void change(java.util.function.Consumer<SchematicTransformation> tool) {
		ItemStack stack = held();
		if (stack.isEmpty())
			return;
		SchematicTransformation transformation = SchematicHandler.loadTransformation(player.level(), stack);
		if (transformation == null)
			return;
		tool.accept(transformation);
		SchematicHandler.store(stack, transformation, true);
		refresh();
	}

	/** Create's MoveTool and MoveVerticalTool, along the world axes */
	public void move(int x, int y, int z) {
		change(t -> t.move(x, y, z));
	}

	/** Create's RotateTool */
	public void rotate(boolean clockwise) {
		change(t -> t.rotate90(clockwise));
	}

	/** Create's FlipTool: X is the left-right mirror, Z the front-back one */
	public void mirror(Axis axis) {
		change(t -> t.flip(axis));
	}

	/** Create's DeployTool, at the block the player points at */
	public void deployLooking() {
		BlockPos selected = SchematicHandler.selectedPos(player, 75);
		if (selected == null) {
			player.sendOverlayMessage(Component.translatable("create.schematicAndQuill.noTarget")
				.withStyle(ChatFormatting.RED));
			return;
		}
		if (SchematicHandler.deployAtSelected(player, selected))
			refresh();
	}

	public void deployFeet() {
		if (SchematicHandler.deployAtSelected(player, player.blockPosition()))
			refresh();
	}

	public void pickUp() {
		ItemStack stack = held();
		if (stack.isEmpty())
			return;
		stack.set(SchematicsRegistry.SCHEMATIC_DEPLOYED, false);
		SchematicInstances.clearHash(stack);
		refresh();
	}

	public void print() {
		ItemStack stack = held();
		if (stack.isEmpty() || !player.isCreative())
			return;
		if (SchematicHandler.printInstantly(player, stack.copy())) {
			stack.set(SchematicsRegistry.SCHEMATIC_DEPLOYED, false);
			SchematicInstances.clearHash(stack);
		}
		refresh();
	}
}

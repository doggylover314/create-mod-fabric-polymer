package dev.createpolymer.equipment.symmetryWand;

import java.util.List;

import dev.createpolymer.equipment.symmetryWand.mirror.CrossPlaneMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.EmptyMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.PlaneMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.SymmetryMirror;
import dev.createpolymer.equipment.symmetryWand.mirror.TriplePlaneMirror;
import dev.createpolymer.equipment.zapper.ZapperGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Server side stand-in for Create's SymmetryWandScreen, a 9x3 chest GUI: the first row picks the type of the mirror,
 * the second its orientation. The choice is written to the wand when it is made and when the GUI closes, like the
 * screen does when it is removed.
 */
public class SymmetryWandGui extends SimpleGui {

	/** The slots of the three mirror types, and the first slot of the orientations */
	private static final int TYPE_SLOT = 2;
	private static final int ALIGN_SLOT = 11;
	private static final Item[] TYPE_ICONS = { Items.GLASS_PANE, Items.GLASS, Items.AMETHYST_CLUSTER };
	private static final Item[] ALIGN_ICONS = { Items.COMPASS, Items.RECOVERY_COMPASS };

	private final ItemStack wand;
	private final InteractionHand hand;
	private SymmetryMirror currentElement;

	public SymmetryWandGui(ServerPlayer player, ItemStack wand, InteractionHand hand) {
		super(MenuType.GENERIC_9x3, player, false);
		this.wand = wand;
		this.hand = hand;
		setLockPlayerInventory(true);
		setTitle(wand.getHoverName());

		currentElement = SymmetryWandItem.getMirror(wand)
			.copy();
		if (currentElement instanceof EmptyMirror) {
			currentElement = new PlaneMirror(Vec3.ZERO);
		}
		refresh();
	}

	@Override
	public boolean stillValid() {
		return player.getItemInHand(hand) == wand;
	}

	private static int typeIndex(SymmetryMirror mirror) {
		return mirror instanceof TriplePlaneMirror ? 2 : mirror instanceof CrossPlaneMirror ? 1 : 0;
	}

	private void refresh() {
		for (int i = 0; i < getSize(); i++)
			clearSlot(i);

		setSlot(0, ZapperGui.label(Component.translatable("create.gui.symmetryWand.mirrorType")));
		List<Component> mirrors = SymmetryMirror.getMirrors();
		for (int type = 0; type < mirrors.size(); type++) {
			int chosen = type;
			setSlot(TYPE_SLOT + type, ZapperGui.button(TYPE_ICONS[type], 1, mirrors.get(type), typeIndex(currentElement) == type,
				() -> {
					if (typeIndex(currentElement) != chosen)
						setType(chosen);
				}));
		}

		setSlot(9, ZapperGui.label(Component.translatable("create.gui.symmetryWand.orientation")));
		List<Component> aligns = currentElement.getAlignToolTips();
		for (int align = 0; align < aligns.size(); align++) {
			int chosen = align;
			setSlot(ALIGN_SLOT + align, ZapperGui.button(ALIGN_ICONS[Math.min(align, ALIGN_ICONS.length - 1)], 1,
				aligns.get(align), currentElement.getOrientationIndex() == align, () -> {
					currentElement.setOrientation(chosen);
					save();
					refresh();
				}));
		}

		setSlot(26, new GuiElementBuilder(Items.EMERALD)
			.setName(Component.translatable("gui.done")
				.withStyle(ChatFormatting.GREEN))
			.hideDefaultTooltip()
			.setCallback(() -> close()));
	}

	private void setType(int type) {
		Vec3 position = currentElement.getPosition();
		currentElement = switch (type) {
			case 1 -> new CrossPlaneMirror(position);
			case 2 -> new TriplePlaneMirror(position);
			default -> new PlaneMirror(position);
		};
		save();
		refresh();
	}

	private void save() {
		SymmetryWandItem.configureSettings(wand, currentElement.copy());
	}

	@Override
	public void onRemoved() {
		if (stillValid())
			save();
	}
}

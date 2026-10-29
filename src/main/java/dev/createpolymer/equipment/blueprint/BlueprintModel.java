package dev.createpolymer.equipment.blueprint;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import dev.createpolymer.equipment.blueprint.BlueprintEntity.BlueprintSection;
import dev.createpolymer.visual.ModelItems;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What players see of a {@link BlueprintEntity}: Create's panel model (BlueprintRenderer), the icon items of the
 * recipes on it, and thin rows of interaction entities in front of it that hand clicks to the entity.
 * <p>
 * The icons are drawn the way the renderer draws them: in the GUI display context, flattened against the panel.
 */
public class BlueprintModel extends ElementHolder {
	/** How far the interaction entities reach in front of the panel, and how wide each is. */
	private static final float HIT_DEPTH = .25f;

	private final BlueprintEntity entity;
	private final ItemDisplayElement panel;
	private final List<ItemDisplayElement> icons = new ArrayList<>();
	private int shownVersion = -1;

	public BlueprintModel(BlueprintEntity entity) {
		this.entity = entity;
		int size = entity.getSize();

		panel = new ItemDisplayElement(ModelItems.block("crafting_blueprint/" + (size == 3 ? "large" : size == 2 ? "medium" : "small")));
		panel.setItemDisplayContext(ItemDisplayContext.NONE);
		panel.setViewRange(1f);
		panel.setTeleportDuration(0);
		panel.setInterpolationDuration(0);
		panel.setTransformation(panelTransform());
		addElement(panel);

		addHitboxes();
		refreshIcons();
	}

	/**
	 * Create: sbb.rotateY(-yaw).rotateX(90 + xRot).translate(-.5, -1/32, -.5), and for the 2x2 panel another
	 * translate(.5, 0, -.5). The model is drawn around the centre of the display, Create's renderer places its corner.
	 */
	private Matrix4f panelTransform() {
		Matrix4f m = new Matrix4f().rotateY((float) Math.toRadians(-entity.getYRot()))
			.rotateX((float) Math.toRadians(90.0F + entity.getXRot()))
			.translate(-.5f, -1 / 32f, -.5f);
		if (entity.getSize() == 2)
			m.translate(.5f, 0, -.5f);
		return flip(m.translate(.5f, .5f, .5f));
	}

	/** The client turns every item display half a turn; the models of Create are authored without it. */
	private static Matrix4f flip(Matrix4f m) {
		return m.rotateY((float) Math.PI);
	}

	private void addHitboxes() {
		int size = entity.getSize();
		Direction facing = entity.getFacing();
		VirtualElement.InteractionHandler handler = VirtualElement.InteractionHandler.redirect(entity);
		Vec3 front = Vec3.atLowerCornerOf(facing.getUnitVec3i());

		// the middle of the panel's front, relative to the entity's position
		Vec3 center = Vec3.ZERO;
		if (size == 2)
			center = center.add(sizeTwoOffset());

		if (facing.getAxis() == Axis.Y) {
			// floor and ceiling: one square, as deep as the other hitboxes
			float depth = HIT_DEPTH;
			InteractionElement hit = new InteractionElement(handler);
			hit.setSize(size, depth);
			hit.setOffset(center.add(front.scale(.0))
				.add(0, facing == Direction.UP ? 0 : -depth, 0));
			addElement(hit);
			return;
		}

		// walls: columns along the panel, each as wide as it is deep
		int columns = Math.round(size / HIT_DEPTH);
		Direction along = facing.getClockWise();
		Vec3 alongVec = Vec3.atLowerCornerOf(along.getUnitVec3i());
		for (int i = 0; i < columns; i++) {
			double offsetAlong = (i + .5) * HIT_DEPTH - size / 2.0;
			InteractionElement hit = new InteractionElement(handler);
			hit.setSize(HIT_DEPTH, size);
			// an interaction entity stands on its position; its box reaches half its width to each side
			Vec3 offset = center.add(alongVec.scale(offsetAlong))
				.add(front.scale(HIT_DEPTH / 2))
				.add(0, -size / 2.0, 0);
			hit.setOffset(offset);
			addElement(hit);
		}
	}

	/** The 2x2 panel's entity sits in one corner; this is where its middle is. */
	private Vec3 sizeTwoOffset() {
		Direction facing = entity.getFacing();
		Direction up = facing.getAxis()
			.isHorizontal() ? Direction.UP
			: facing == Direction.UP ? entity.getVerticalOrientation() : entity.getVerticalOrientation()
				.getOpposite();
		Direction side = facing.getAxis()
			.isHorizontal() ? facing.getCounterClockWise() : entity.getVerticalOrientation()
				.getClockWise();
		return Vec3.atLowerCornerOf(up.getUnitVec3i())
			.add(Vec3.atLowerCornerOf(side.getUnitVec3i()))
			.scale(.5);
	}

	private void refreshIcons() {
		for (ItemDisplayElement icon : icons)
			removeElement(icon);
		icons.clear();

		int size = entity.getSize();
		Matrix4f frame = new Matrix4f().rotateY((float) Math.toRadians(-entity.getYRot()))
			.rotateX((float) Math.toRadians(entity.getXRot()))
			.translate(0, 0, 1 / 32f + .001f);
		if (size == 3)
			frame.translate(-1, -1, 0);

		for (int row = 0; row < size; row++) {
			for (int col = 0; col < size; col++) {
				BlueprintSection section = entity.getSection(row * size + col);
				section.getDisplayItems()
					.forEachWithContext((stack, primary) -> {
						if (stack.isEmpty())
							return;
						Matrix4f m = new Matrix4f(frame).translate(sectionX(section), sectionY(section), 0)
							.scale(.5f, .5f, 1 / 1024f);
						if (!primary) {
							m.translate(0.325f, -0.325f, 1);
							m.scale(.625f, .625f, 1);
						}
						ItemDisplayElement icon = new ItemDisplayElement(stack.copy());
						icon.setItemDisplayContext(ItemDisplayContext.GUI);
						icon.setViewRange(1f);
						icon.setTeleportDuration(0);
						icon.setInterpolationDuration(0);
						icon.setTransformation(flip(m));
						addElement(icon);
						icons.add(icon);
					});
			}
		}
		shownVersion = entity.getDataVersion();
	}

	private float sectionX(BlueprintSection section) {
		return section.getIndex() % entity.getSize();
	}

	private float sectionY(BlueprintSection section) {
		return section.getIndex() / entity.getSize();
	}

	@Override
	protected void onTick() {
		if (entity.isRemoved())
			return;
		if (shownVersion != entity.getDataVersion())
			refreshIcons();
	}

	/** The item an icon shows, for the tests: the recipes' result icons. */
	public List<ItemStack> shownIcons() {
		List<ItemStack> stacks = new ArrayList<>();
		for (ItemDisplayElement icon : icons)
			stacks.add(icon.getItem());
		return stacks;
	}
}

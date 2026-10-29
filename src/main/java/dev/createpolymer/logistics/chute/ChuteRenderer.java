package dev.createpolymer.logistics.chute;

import org.joml.Matrix4f;

import dev.createpolymer.logistics.chute.ChuteBlock.Shape;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shows the item a chute carries with a display entity, placed like Create's ChuteRenderer does for the client.
 * Create draws it from a PoseStack every frame; this sends the transform of every tick, interpolated over one tick.
 */
public class ChuteRenderer {
	private final Part part;
	private ItemStack shown = ItemStack.EMPTY;
	private float lastPosition;
	private boolean visible;

	public ChuteRenderer(ElementHolder holder) {
		part = new Part(ItemStack.EMPTY);
		part.element.setItemDisplayContext(ItemDisplayContext.FIXED);
		holder.addElement(part.element);
	}

	/** Create's ChuteRenderer.renderSafe: items are only seen through windows and in the draught of a fan. */
	public static boolean isVisibleInChute(ChuteBlockEntity be, BlockState blockState) {
		if (be.item.isEmpty())
			return false;
		if (blockState.getValue(ChuteBlock.FACING) != Direction.DOWN)
			return false;
		return blockState.getValue(ChuteBlock.SHAPE) == Shape.WINDOW
			|| be.bottomPullDistance != 0 && be.itemPosition.getValue() <= .5f;
	}

	/** Create's SmartChuteRenderer.renderSafe: the item shows once it has reached the bottom. */
	public static boolean isVisibleInSmartChute(ChuteBlockEntity be) {
		return !be.item.isEmpty() && be.itemPosition.getValue() <= 0;
	}

	/** Call once per tick. */
	public void render(ChuteBlockEntity be, boolean show) {
		if (!show) {
			if (visible) {
				visible = false;
				shown = ItemStack.EMPTY;
				part.setItem(ItemStack.EMPTY);
			}
			return;
		}

		float itemPosition = be.itemPosition.getValue();
		boolean appearing = !visible;
		if (appearing || !ItemStack.isSameItemSameComponents(shown, be.item)) {
			shown = be.item.copyWithCount(1);
			part.setItem(shown);
		} else if (itemPosition == lastPosition)
			return;
		visible = true;
		lastPosition = itemPosition;
		part.setMatrix(transform(itemPosition), appearing ? 0 : 1);
	}

	/** Create's ChuteRenderer.renderItem; display coordinates are block-centred. */
	public static Matrix4f transform(float itemPosition) {
		float itemScale = .5f;
		return new Matrix4f().translate(0, -.5f + itemPosition, 0)
			.scale(itemScale)
			.rotateX((float) Math.toRadians(itemPosition * 180))
			.rotateY((float) Math.toRadians(itemPosition * 180));
	}

}

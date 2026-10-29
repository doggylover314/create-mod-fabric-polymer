package dev.createpolymer.redstone.displayLink;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DisplayLinkBlockItem extends ClickToLinkBlockItem {

	/** Create's logistics.displayLinkRange: how far from the selected block the link can be placed. */
	public static int displayLinkRange = 64;

	public DisplayLinkBlockItem(Block pBlock, Properties pProperties) {
		super(pBlock, pProperties);
	}

	@Override
	protected void applyLink(BlockEntity placed, BlockPos selectedPos, Identifier selectedDim) {
		if (!(placed instanceof DisplayLinkBlockEntity link))
			return;
		link.target(selectedPos);
		link.notifyUpdate();
	}

	@Override
	public int getMaxDistanceFromSelection() {
		return displayLinkRange;
	}

	@Override
	public String getMessageTranslationKey() {
		return "display_link";
	}

}

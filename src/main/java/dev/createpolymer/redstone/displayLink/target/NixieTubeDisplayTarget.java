package dev.createpolymer.redstone.displayLink.target;

import org.apache.commons.lang3.mutable.MutableInt;

import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlock;
import dev.createpolymer.redstone.nixieTube.NixieTubeBlockEntity;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class NixieTubeDisplayTarget extends SingleLineDisplayTarget {

	@Override
	protected void acceptLine(MutableComponent text, DisplayLinkContext context) {
		NixieTubeBlock.walkNixies(context.level(), context.getTargetPos(), (currentPos, rowPosition) -> {
			BlockEntity blockEntity = context.level()
				.getBlockEntity(currentPos);
			if (blockEntity instanceof NixieTubeBlockEntity nixie)
				nixie.displayCustomText(text, rowPosition);
		});
	}

	@Override
	protected int getWidth(DisplayLinkContext context) {
		MutableInt count = new MutableInt(0);
		NixieTubeBlock.walkNixies(context.level(), context.getTargetPos(),
				(currentPos, rowPosition) -> count.add(2));
		return count.intValue();
	}

}

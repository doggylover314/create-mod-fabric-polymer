package dev.createpolymer.redstone.displayLink.target;

import java.util.List;

import dev.createpolymer.api.behaviour.display.DisplayTarget;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

public class SignDisplayTarget extends DisplayTarget {

	@Override
	public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
		BlockEntity be = context.getTargetBlockEntity();
		if (!(be instanceof SignBlockEntity sign))
			return;

		boolean changed = false;
		SignText.Mutable front = sign.getText(SignTextSlot.FRONT)
			.asMutable();
		SignText.Mutable back = sign.getText(SignTextSlot.BACK)
			.asMutable();
		for (int i = 0; i < text.size() && i + line < SignText.LINES; i++) {
			if (i == 0)
				reserve(i + line, sign, context);
			if (i > 0 && isReserved(i + line, sign, context))
				break;

			String content = text.get(i)
				.getString(sign.getMaxTextLineWidth());
			front.setLine(i + line, Component.literal(content));
			back.setLine(i + line, Component.literal(content));
			changed = true;
		}

		if (changed) {
			sign.setText(front.asImmutable(), SignTextSlot.FRONT);
			sign.setText(back.asImmutable(), SignTextSlot.BACK);
			context.level()
				.sendBlockUpdated(context.getTargetPos(), sign.getBlockState(), sign.getBlockState(), 2);
		}
	}

	@Override
	public DisplayTargetStats provideStats(DisplayLinkContext context) {
		return new DisplayTargetStats(4, 15, this);
	}

	@Override
	public boolean requiresComponentSanitization() {
		return true;
	}

}

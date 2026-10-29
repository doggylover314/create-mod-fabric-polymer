package dev.createpolymer.redstone.displayLink.target;

import java.util.List;

import dev.createpolymer.api.behaviour.display.DisplaySource;
import dev.createpolymer.api.behaviour.display.DisplayTarget;
import dev.createpolymer.redstone.displayLink.DisplayLinkContext;
import dev.createpolymer.redstone.displayLink.source.SingleLineDisplaySource;
import dev.createpolymer.trains.display.FlapDisplayBlockEntity;
import dev.createpolymer.trains.display.FlapDisplayLayout;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DisplayBoardTarget extends DisplayTarget {

	@Override
	public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {}

	public void acceptFlapText(int line, List<List<MutableComponent>> text, DisplayLinkContext context) {
		FlapDisplayBlockEntity controller = getController(context);
		if (controller == null)
			return;
		if (!controller.isSpeedRequirementFulfilled())
			return;

		DisplaySource source = context.blockEntity().activeSource;
		List<FlapDisplayLayout> lines = controller.getLines();
		for (int i = 0; i + line < lines.size(); i++) {

			if (i == 0)
				reserve(i + line, controller, context);
			if (i > 0 && isReserved(i + line, controller, context))
				break;

			FlapDisplayLayout layout = lines.get(i + line);

			if (i >= text.size()) {
				if (source instanceof SingleLineDisplaySource)
					break;
				controller.applyTextManually(i + line, null);
				continue;
			}

			source.loadFlapDisplayLayout(context, controller, layout, i);

			for (int sectionIndex = 0; sectionIndex < layout.getSections()
				.size(); sectionIndex++) {
				List<MutableComponent> textLine = text.get(i);
				if (textLine.size() <= sectionIndex)
					break;
				layout.getSections()
					.get(sectionIndex)
					.setText(textLine.get(sectionIndex));
			}
		}

		controller.setChanged();
		controller.sendData();
	}

	@Override
	public boolean isReserved(int line, BlockEntity target, DisplayLinkContext context) {
		return super.isReserved(line, target, context)
			|| target instanceof FlapDisplayBlockEntity fdte && fdte.manualLines.length > line && fdte.manualLines[line];
	}

	@Override
	public DisplayTargetStats provideStats(DisplayLinkContext context) {
		FlapDisplayBlockEntity controller = getController(context);
		if (controller == null)
			return new DisplayTargetStats(1, 1, this);
		return new DisplayTargetStats(controller.ySize * 2, controller.getMaxCharCount(), this);
	}

	private FlapDisplayBlockEntity getController(DisplayLinkContext context) {
		BlockEntity teIn = context.getTargetBlockEntity();
		if (!(teIn instanceof FlapDisplayBlockEntity be))
			return null;
		return be.getController();
	}

}

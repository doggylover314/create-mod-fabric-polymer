package dev.createpolymer.redstone.nixieTube;

import dev.createpolymer.redstone.displayLink.DisplayLinkBlock;

import java.util.Optional;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.util.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Create's nixie tube. The custom text of a row of tubes is kept as a plain {@link Component} (Create resolves
 * selectors and scoreboard scores in it as well); the signal strength is shown as two digits.
 */
public class NixieTubeBlockEntity extends SmartBlockEntity {

	private static final Couple<String> EMPTY = Couple.create("", "");

	private int redstoneStrength;
	private Optional<Component> customText;
	private int nixieIndex;
	private Couple<String> displayedStrings;

	public NixieTubeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		customText = Optional.empty();
		redstoneStrength = 0;
		updateDisplayedStrings();
	}

	public boolean reactsToRedstone() {
		return customText.isEmpty();
	}

	public Couple<String> getDisplayedStrings() {
		if (displayedStrings == null)
			return EMPTY;
		return displayedStrings;
	}

	public MutableComponent getFullText() {
		return customText.map(Component::copy)
			.orElse(Component.literal("" + redstoneStrength));
	}

	public void updateRedstoneStrength(int signalStrength) {
		clearCustomText();
		redstoneStrength = signalStrength;
		updateDisplayedStrings();
		DisplayLinkBlock.notifyGatherers(level, worldPosition);
		notifyUpdate();
	}

	public void displayCustomText(Component text, int nixiePositionInRow) {
		if (text == null)
			return;
		if (customText.filter(t -> t.equals(text))
			.isPresent() && nixieIndex == nixiePositionInRow)
			return;

		customText = Optional.of(text);
		nixieIndex = nixiePositionInRow;
		updateDisplayedStrings();
		DisplayLinkBlock.notifyGatherers(level, worldPosition);
		notifyUpdate();
	}

	public void displayEmptyText(int nixiePositionInRow) {
		displayCustomText(Component.empty(), nixiePositionInRow);
	}

	public void updateDisplayedStrings() {
		customText.map(Component::getString)
			.ifPresentOrElse(
				fullText -> displayedStrings = Couple.create(charOrEmpty(fullText, nixieIndex * 2),
					charOrEmpty(fullText, nixieIndex * 2 + 1)),
				() -> displayedStrings = Couple.create(redstoneStrength < 10 ? "0" : "1",
					String.valueOf(redstoneStrength % 10)));
	}

	public void clearCustomText() {
		nixieIndex = 0;
		customText = Optional.empty();
	}

	public int getRedstoneStrength() {
		return redstoneStrength;
	}

	@Override
	protected void read(ValueInput nbt) {
		super.read(nbt);

		Optional<Component> text = nbt.read("CustomText", ComponentSerialization.CODEC);
		if (text.isPresent()) {
			customText = text;
			nixieIndex = nbt.getIntOr("CustomTextIndex", 0);
		} else {
			customText = Optional.empty();
			nixieIndex = 0;
		}

		if (customText.isEmpty())
			redstoneStrength = nbt.getIntOr("RedstoneStrength", 0);
		updateDisplayedStrings();
	}

	@Override
	protected void write(ValueOutput nbt) {
		super.write(nbt);

		if (customText.isPresent()) {
			nbt.putInt("CustomTextIndex", nixieIndex);
			nbt.store("CustomText", ComponentSerialization.CODEC, customText.get());
		} else
			nbt.putInt("RedstoneStrength", redstoneStrength);
	}

	private String charOrEmpty(String string, int index) {
		return string.length() <= index ? " " : string.substring(index, index + 1);
	}

}

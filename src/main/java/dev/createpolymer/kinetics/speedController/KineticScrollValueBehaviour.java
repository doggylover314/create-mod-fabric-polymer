package dev.createpolymer.kinetics.speedController;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBoard;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.createpolymer.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Ported from Create's KineticScrollValueBehaviour (MIT, The Create Team): a signed speed whose first row sets a
 * negative (counter-clockwise) value and whose second row sets a positive one. Create keeps it in kinetics.motor;
 * it lives here until the motor is ported.
 */
public class KineticScrollValueBehaviour extends ScrollValueBehaviour {

	public KineticScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
		super(label, be, slot);
		withFormatter(v -> String.valueOf(Math.abs(v)));
	}

	@Override
	public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
		ImmutableList<Component> rows = ImmutableList.of(Component.literal("⟳").withStyle(ChatFormatting.BOLD),
			Component.literal("⟲").withStyle(ChatFormatting.BOLD));
		ValueSettingsFormatter formatter = new ValueSettingsFormatter(this::formatSettings);
		return new ValueSettingsBoard(label, 256, 32, rows, formatter);
	}

	@Override
	public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
		int value = Math.max(1, valueSetting.value());
		if (!valueSetting.equals(getValueSettings()))
			playFeedbackSound(this);
		setValue(valueSetting.row() == 0 ? -value : value);
	}

	@Override
	public ValueSettings getValueSettings() {
		return new ValueSettings(value < 0 ? 0 : 1, Math.abs(value));
	}

	public MutableComponent formatSettings(ValueSettings settings) {
		return Lang.builder(CreatePolymer.MOD_ID)
			.text(String.valueOf(Math.max(1, Math.abs(settings.value()))))
			.add(Lang.builder(CreatePolymer.MOD_ID).text(ChatFormatting.BOLD, settings.row() == 0 ? "⟳" : "⟲"))
			.component();
	}
	@Override
	public String getClipboardKey() {
		return "Speed";
	}

}

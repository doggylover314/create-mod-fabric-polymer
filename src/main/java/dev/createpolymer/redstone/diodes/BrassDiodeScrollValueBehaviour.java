package dev.createpolymer.redstone.diodes;

import java.util.List;

import dev.createpolymer.foundation.blockEntity.SmartBlockEntity;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBoard;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BrassDiodeScrollValueBehaviour extends ScrollValueBehaviour {

	public BrassDiodeScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
		super(label, be, slot);
	}

	@Override
	public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
		return new ValueSettingsBoard(label, 60, 10,
			List.of(Component.translatable("create.generic.unit.ticks"), Component.translatable("create.generic.unit.seconds"),
				Component.translatable("create.generic.unit.minutes")),
			new ValueSettingsFormatter(this::formatSettings));
	}

	@Override
	public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
		if (getWorld().isClientSide())
			return;
		BlockState blockState = blockEntity.getBlockState();
		if (blockState.getBlock()instanceof BrassDiodeBlock bdb)
			bdb.toggle(getWorld(), getPos(), blockState, player, hand);
	}

	@Override
	public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
		int value = valueSetting.value();
		int multiplier = switch (valueSetting.row()) {
		case 0 -> 1;
		case 1 -> 20;
		default -> 60 * 20;
		};
		if (!valueSetting.equals(getValueSettings()))
			playFeedbackSound(this);
		setValue(Math.max(2, Math.max(1, value) * multiplier));
	}

	@Override
	public ValueSettings getValueSettings() {
		int row = 0;
		int value = this.value;

		if (value > 60 * 20) {
			value = value / (60 * 20);
			row = 2;
		} else if (value > 60) {
			value = value / 20;
			row = 1;
		}

		return new ValueSettings(row, value);
	}

	public MutableComponent formatSettings(ValueSettings settings) {
		int value = Math.max(1, settings.value());
		return Component.literal(switch (settings.row()) {
		case 0 -> Math.max(2, value) + "t";
		case 1 -> "0:" + (value < 10 ? "0" : "") + value;
		default -> value + ":00";
		});
	}

	@Override
	public String getClipboardKey() {
		return "Timings";
	}

}

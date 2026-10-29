package dev.createpolymer.foundation.blockEntity.behaviour;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public interface ValueSettingsBehaviour {

	record ValueSettings(int row, int value) {
		public MutableComponent format() {
			return Component.literal(String.valueOf(value));
		}
	}

	boolean testHit(Vec3 hit);

	boolean isActive();

	default boolean onlyVisibleWithWrench() {
		return false;
	}

	default void newSettingHovered(ValueSettings valueSetting) {}

	ValueBoxTransform getSlotPositioning();

	ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult);

	void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlDown);

	ValueSettings getValueSettings();

	default boolean acceptsValueSettings() {
		return true;
	}

	default void playFeedbackSound(BlockEntityBehaviour origin) {
		origin.getWorld()
			.playSound(null, origin.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.25f, 2f);
		origin.getWorld()
			.playSound(null, origin.getPos(), SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), SoundSource.BLOCKS, 0.03f,
				1.125f);
	}

	default void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {}

	default boolean bypassesInput(ItemStack mainhandItem) {
		return false;
	}

	default boolean mayInteract(Player player) {
		return true;
	}

	default int netId() {
		return 0;
	}

}

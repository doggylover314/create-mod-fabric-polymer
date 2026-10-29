package dev.createpolymer.contraptions;

import dev.createpolymer.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import dev.createpolymer.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public interface IControlContraption {

	public boolean isAttachedTo(AbstractContraptionEntity contraption);

	public void attach(ControlledContraptionEntity contraption);

	public void onStall();

	public boolean isValid();

	public BlockPos getBlockPosition();

	static enum MovementMode implements INamedIconOptions {

		MOVE_PLACE(Items.OAK_PLANKS),
		MOVE_PLACE_RETURNED(Items.OAK_DOOR),
		MOVE_NEVER_PLACE(Items.BARRIER),

		;

		private String translationKey;
		private Item icon;

		private MovementMode(Item icon) {
			this.icon = icon;
			translationKey = "create.contraptions.movement_mode." + Lang.asId(name());
		}

		@Override
		public Item getIconItem() {
			return icon;
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}

	}

	static enum RotationMode implements INamedIconOptions {

		ROTATE_PLACE(Items.OAK_PLANKS),
		ROTATE_PLACE_RETURNED(Items.OAK_DOOR),
		ROTATE_NEVER_PLACE(Items.BARRIER),

		;

		private String translationKey;
		private Item icon;

		private RotationMode(Item icon) {
			this.icon = icon;
			translationKey = "create.contraptions.movement_mode." + Lang.asId(name());
		}

		@Override
		public Item getIconItem() {
			return icon;
		}

		@Override
		public String getTranslationKey() {
			return translationKey;
		}

	}

}

package dev.createpolymer.decoration.bracket;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.createpolymer.visual.BlockstateModels;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the bracket models of Create's PipeAttachmentModel and BracketedKineticBlockModel add to a pipe or shaft:
 * a display piece with the bracket's model that exists while the block entity's
 * {@link BracketedBlockEntityBehaviour} holds a bracket. Call {@link #update} every tick.
 */
public class BracketDisplay {
	private final ElementHolder holder;
	private @Nullable Part part;
	private @Nullable BlockState shown;

	public BracketDisplay(ElementHolder holder) {
		this.holder = holder;
	}

	public void update(@Nullable BlockEntity be) {
		BracketedBlockEntityBehaviour behaviour = BlockEntityBehaviour.get(be, BracketedBlockEntityBehaviour.TYPE);
		BlockState bracket = behaviour == null ? null : behaviour.getBracket();
		if (bracket == shown)
			return;
		shown = bracket;
		BlockstateModels.Variant variant = null;
		if (bracket != null && bracket.getBlock() instanceof BracketBlock block)
			variant = BlockstateModels.get(block.blockstateFile(), bracket);
		if (variant == null) {
			if (part != null) {
				holder.removeElement(part.element);
				part = null;
			}
			return;
		}
		if (part == null) {
			part = new Part(ItemStack.EMPTY);
			holder.addElement(part.element);
		}
		part.setItem(variant.stack());
		part.orientation(variant.rotation())
			.apply(0);
	}

	public @Nullable BlockState getShown() {
		return shown;
	}
}

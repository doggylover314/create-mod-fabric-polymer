package dev.createpolymer.visual;

import org.jspecify.annotations.Nullable;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Display-entity model for one block. It reads the block entity each tick and
 * moves its parts; nothing is sent when nothing changes.
 */
public abstract class BaseModel extends ElementHolder {
	protected BlockState state;

	protected BaseModel(BlockState state) {
		this.state = state;
	}

	protected Part addPart(ItemStack stack) {
		Part part = new Part(stack);
		addElement(part.element);
		return part;
	}

	@Override
	protected void onTick() {
		BlockAwareAttachment attachment = BlockAwareAttachment.get(this);
		if (attachment == null || !attachment.isPartOfTheWorld())
			return;
		ServerLevel level = getAttachment().getWorld();
		BlockPos pos = attachment.getBlockPos();
		BlockEntity be = level.getBlockEntity(pos);
		update(level, pos, be, level.getGameTime());
	}

	@Override
	public void notifyUpdate(HolderAttachment.UpdateType updateType) {
		super.notifyUpdate(updateType);
		if (updateType == BlockAwareAttachment.BLOCK_STATE_UPDATE) {
			BlockAwareAttachment attachment = BlockAwareAttachment.get(this);
			if (attachment != null) {
				this.state = attachment.getBlockState();
				onStateChanged(this.state);
			}
		}
	}

	protected void onStateChanged(BlockState state) {
	}

	protected abstract void update(ServerLevel level, BlockPos pos, @Nullable BlockEntity be, long time);
}

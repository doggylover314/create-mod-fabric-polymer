package dev.createpolymer.api.contraption.storage.item.simple;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.api.contraption.storage.item.MountedItemStorageType;
import dev.createpolymer.api.contraption.storage.item.WrapperMountedItemStorage;
import dev.createpolymer.foundation.item.handler.IItemHandler;
import dev.createpolymer.foundation.item.handler.IItemHandlerModifiable;
import dev.createpolymer.foundation.item.handler.InvWrapper;
import dev.createpolymer.foundation.item.handler.ItemHandlerLookup;
import dev.createpolymer.foundation.item.handler.ItemStackHandler;
import dev.createpolymer.registry.feature.ContraptionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Widely-applicable mounted storage implementation.
 * Gets an item handler from the mounted block, copies it to an ItemStackHandler,
 * and then copies the inventory back to the target when unmounting.
 * Vanilla containers (barrels, shulker boxes, ...) are read directly; every other block must provide an
 * {@link IItemHandlerModifiable} through {@link ItemHandlerLookup}.
 */
public class SimpleMountedStorage extends WrapperMountedItemStorage<ItemStackHandler> {
	private record SlotStack(int slot, ItemStack stack) {
		static final Codec<SlotStack> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("Slot").forGetter(SlotStack::slot),
			ItemStack.CODEC.fieldOf("Item").forGetter(SlotStack::stack)
		).apply(i, SlotStack::new));
	}

	/** Create's CreateCodecs.ITEM_STACK_HANDLER: the size and the non-empty slots. */
	public static final Codec<ItemStackHandler> ITEM_STACK_HANDLER = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("Size").forGetter(ItemStackHandler::getSlots),
		SlotStack.CODEC.listOf().optionalFieldOf("Items", List.of()).forGetter(handler -> {
			List<SlotStack> list = new java.util.ArrayList<>();
			for (int slot = 0; slot < handler.getSlots(); slot++)
				if (!handler.getStackInSlot(slot).isEmpty())
					list.add(new SlotStack(slot, handler.getStackInSlot(slot)));
			return list;
		})
	).apply(i, (size, items) -> {
		ItemStackHandler handler = new ItemStackHandler(size);
		for (SlotStack entry : items)
			if (entry.slot() >= 0 && entry.slot() < size)
				handler.setStackInSlot(entry.slot(), entry.stack());
		return handler;
	}));

	public static final MapCodec<SimpleMountedStorage> CODEC = codec(SimpleMountedStorage::new);

	public SimpleMountedStorage(MountedItemStorageType<?> type, IItemHandler handler) {
		super(type, copyToItemStackHandler(handler));
	}

	public SimpleMountedStorage(IItemHandler handler) {
		this(ContraptionRegistry.SIMPLE_MOUNTED_STORAGE, handler);
	}

	@Override
	public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		if (be == null)
			return;

		IItemHandler cap = be instanceof Container container ? new InvWrapper(container)
			: ItemHandlerLookup.get(level, pos, null);
		if (cap != null) {
			validate(cap).ifPresent(handler -> {
				for (int i = 0; i < handler.getSlots(); i++) {
					handler.setStackInSlot(i, this.getStackInSlot(i));
				}
			});
		}
	}

	/**
	 * Make sure the targeted handler is valid for copying items back into.
	 * It is highly recommended to call super in overrides.
	 */
	protected Optional<IItemHandlerModifiable> validate(IItemHandler handler) {
		if (handler.getSlots() == this.getSlots() && handler instanceof IItemHandlerModifiable modifiable) {
			return Optional.of(modifiable);
		} else {
			return Optional.empty();
		}
	}

	public static <T extends SimpleMountedStorage> MapCodec<T> codec(Function<IItemHandler, T> factory) {
		return ITEM_STACK_HANDLER.xmap(factory, storage -> storage.wrapped).fieldOf("value");
	}
}

package dev.createpolymer.logistics.factoryBoard;

import java.util.UUID;

import dev.createpolymer.foundation.block.IBE;
import dev.createpolymer.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import dev.createpolymer.logistics.packagerLink.LogisticallyLinkedBlockItem;
import dev.createpolymer.registry.feature.FactoryGaugeRegistry;
import dev.createpolymer.util.Lang;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class FactoryPanelBlockItem extends LogisticallyLinkedBlockItem {

	public FactoryPanelBlockItem(Block pBlock, Properties pProperties) {
		super(pBlock, pProperties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext pContext) {
		ItemStack stack = pContext.getItemInHand();

		if (!isTuned(stack)) {
			if (!pContext.getLevel()
				.isClientSide()) {
				FactoryGaugeRegistry.deny(pContext.getLevel(), pContext.getClickedPos());
				if (pContext.getPlayer() != null)
					pContext.getPlayer()
						.sendOverlayMessage(Component.translatable("create.factory_panel.tune_before_placing"));
			}
			return InteractionResult.FAIL;
		}

		// Create does this in updateCustomBlockEntityTag, which is static in 26.3
		fixCtrlCopiedStack(stack);
		return super.place(pContext);
	}

	public static ItemStack fixCtrlCopiedStack(ItemStack stack) {
		// Salvage frequency data from one of the panel slots
		if (isTuned(stack) && networkFromStack(stack) == null) {
			TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
			CompoundTag original = data == null ? new CompoundTag() : data.copyTagWithoutId();
			UUID frequency = UUID.randomUUID();

			for (PanelSlot slot : PanelSlot.values()) {
				CompoundTag panelTag = original.getCompoundOrEmpty(Lang.asId(slot.name()));
				UUID found = panelTag.read("Freq", UUIDUtil.CODEC)
					.orElse(null);
				if (found != null)
					frequency = found;
			}

			CompoundTag bet = new CompoundTag();
			bet.store("Freq", UUIDUtil.CODEC, frequency);

			BlockEntityType<?> type = ((IBE<?>) ((BlockItem) stack.getItem()).getBlock()).getBlockEntityType();
			stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(type, bet));
		}

		return stack;
	}

}

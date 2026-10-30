package dev.createpolymer.logistics.tableCloth;

import java.util.List;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsBoard;
import dev.createpolymer.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import dev.createpolymer.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.createpolymer.logistics.filter.FilterItem;
import dev.createpolymer.util.Lang;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public class TableClothFilteringBehaviour extends FilteringBehaviour {

	public TableClothFilteringBehaviour(TableClothBlockEntity be) {
		super(be, new TableClothFilterSlot(be));
		withPredicate(is -> !(is.getItem() instanceof FilterItem) && !(is.getItem() instanceof ShoppingListItem));
		count = 1;
	}

	@Override
	public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
		super.onShortInteract(player, hand, side, hitResult);
	}

	private TableClothBlockEntity dbe() {
		return (TableClothBlockEntity) blockEntity;
	}

	@Override
	public boolean mayInteract(Player player) {
		return dbe().owner == null || player.getUUID()
			.equals(dbe().owner);
	}

	@Override
	public boolean isSafeNBT() {
		return false;
	}

	@Override
	public MutableComponent getLabel() {
		return translate("table_cloth.price_per_order");
	}

	@Override
	public boolean isCountVisible() {
		return !filter.isEmpty();
	}

	@Override
	public boolean setFilter(ItemStack stack) {
		int before = count;
		boolean result = super.setFilter(stack);
		count = before;
		return result;
	}

	@Override
	public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
		if (getValueSettings().equals(settings))
			return;
		count = Math.max(1, settings.value());
		blockEntity.setChanged();
		blockEntity.sendData();
		playFeedbackSound(this);
	}

	@Override
	public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
		return new ValueSettingsBoard(getLabel(), 100, 10, List.of(translate("table_cloth.amount")),
			new ValueSettingsFormatter(this::formatValue));
	}

	@Override
	public MutableComponent formatValue(ValueSettings value) {
		return Component.literal(String.valueOf(Math.max(1, value.value())));
	}

	@Override
	public MutableComponent getCountLabelForValueBox() {
		return Component.literal(isCountVisible() ? String.valueOf(count) : "");
	}

	@Override
	public boolean isActive() {
		return dbe().isShop();
	}

	private static MutableComponent translate(String key) {
		return Lang.builder(CreatePolymer.MOD_ID)
			.translate(key)
			.component();
	}

}

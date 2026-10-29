package dev.createpolymer.trains.schedule;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import dev.createpolymer.util.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public interface IScheduleInput {

	public abstract Pair<ItemStack, Component> getSummary();

	public abstract Identifier getId();

	public abstract CompoundTag getData();

	public abstract void setData(HolderLookup.Provider registries, CompoundTag data);

	public default int slotsTargeted() {
		return 0;
	}

	public default List<Component> getTitleAs(String type) {
		Identifier id = getId();
		return ImmutableList
			.of(Component.translatable(id.getNamespace() + ".schedule." + type + "." + id.getPath()));
	}

	public default ItemStack getSecondLineIcon() {
		return ItemStack.EMPTY;
	}

	public default void setItem(int slot, ItemStack stack) {}

	public default ItemStack getItem(int slot) {
		return ItemStack.EMPTY;
	}

	@Nullable
	public default List<Component> getSecondLineTooltip(int slot) {
		return null;
	}

	/** Create: initConfigurationWidgets(ModularGuiLineBuilder), for the editor of this port. */
	public default void initConfigurationWidgets(ScheduleConfigBuilder builder) {}

}

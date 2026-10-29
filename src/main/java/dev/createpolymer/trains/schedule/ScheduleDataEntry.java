package dev.createpolymer.trains.schedule;

import com.mojang.serialization.DataResult;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public abstract class ScheduleDataEntry implements IScheduleInput {

	protected CompoundTag data;

	public ScheduleDataEntry() {
		data = new CompoundTag();
	}

	@Override
	public CompoundTag getData() {
		return data;
	}

	@Override
	public void setData(HolderLookup.Provider registries, CompoundTag data) {
		this.data = data;
		readAdditional(registries, data);
	}

	protected void writeAdditional(HolderLookup.Provider registries, CompoundTag tag) {}

	protected void readAdditional(HolderLookup.Provider registries, CompoundTag tag) {}

	protected <T> T enumData(String key, Class<T> enumClass) {
		T[] enumConstants = enumClass.getEnumConstants();
		return enumConstants[Math.floorMod(data.getIntOr(key, 0), enumConstants.length)];
	}

	protected String textData(String key) {
		return data.getStringOr(key, "");
	}

	protected int intData(String key) {
		return data.getIntOr(key, 0);
	}

	/** Create: ItemStack#saveOptional */
	protected static Tag writeStack(HolderLookup.Provider registries, ItemStack stack) {
		DataResult<Tag> result = ItemStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack);
		return result.result()
			.orElseGet(CompoundTag::new);
	}

	/** Create: ItemStack#parseOptional */
	protected static ItemStack readStack(HolderLookup.Provider registries, Tag tag) {
		return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
			.result()
			.orElse(ItemStack.EMPTY);
	}

}

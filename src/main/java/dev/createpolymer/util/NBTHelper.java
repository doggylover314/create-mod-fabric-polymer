package dev.createpolymer.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class NBTHelper {

	public static void putMarker(CompoundTag nbt, String marker) {
		nbt.putBoolean(marker, true);
	}

	public static <T extends Enum<?>> T readEnum(CompoundTag nbt, String key, Class<T> enumClass) {
		T[] enumConstants = enumClass.getEnumConstants();
		if (enumConstants == null)
			throw new IllegalArgumentException("Non-Enum class passed to readEnum: " + enumClass.getName());
		return matchEnum(enumConstants, nbt.getString(key));
	}

	public static <T extends Enum<?>> void writeEnum(CompoundTag nbt, String key, T enumConstant) {
		nbt.putString(key, enumConstant.name());
	}

	public static <T> ListTag writeCompoundList(Iterable<T> list, Function<T, CompoundTag> serializer) {
		ListTag listNBT = new ListTag();
		list.forEach(t -> {
			CompoundTag apply = serializer.apply(t);
			if (apply == null)
				return;
			listNBT.add(apply);
		});
		return listNBT;
	}

	public static <T> List<T> readCompoundList(ListTag listNBT, Function<CompoundTag, T> deserializer) {
		List<T> list = new ArrayList<>(listNBT.size());
		listNBT.forEach(inbt -> list.add(deserializer.apply((CompoundTag) inbt)));
		return list;
	}

	public static void iterateCompoundList(ListTag listNBT, Consumer<CompoundTag> consumer) {
		listNBT.forEach(inbt -> consumer.accept((CompoundTag) inbt));
	}

	public static ListTag writeAABB(AABB bb) {
		ListTag bbtag = new ListTag();
		bbtag.add(FloatTag.valueOf((float) bb.minX));
		bbtag.add(FloatTag.valueOf((float) bb.minY));
		bbtag.add(FloatTag.valueOf((float) bb.minZ));
		bbtag.add(FloatTag.valueOf((float) bb.maxX));
		bbtag.add(FloatTag.valueOf((float) bb.maxY));
		bbtag.add(FloatTag.valueOf((float) bb.maxZ));
		return bbtag;
	}

	@Nullable
	public static AABB readAABB(ListTag bbTag) {
		if (bbTag.isEmpty())
			return null;
		return new AABB(bbTag.getFloatOr(0, 0), bbTag.getFloatOr(1, 0), bbTag.getFloatOr(2, 0), bbTag.getFloatOr(3, 0),
			bbTag.getFloatOr(4, 0), bbTag.getFloatOr(5, 0));
	}

	public static ListTag writeVec3i(Vec3i vec) {
		ListTag tag = new ListTag();
		tag.add(IntTag.valueOf(vec.getX()));
		tag.add(IntTag.valueOf(vec.getY()));
		tag.add(IntTag.valueOf(vec.getZ()));
		return tag;
	}

	public static Vec3i readVec3i(ListTag tag) {
		return new Vec3i(tag.getIntOr(0, 0), tag.getIntOr(1, 0), tag.getIntOr(2, 0));
	}

	public static Tag getINBT(CompoundTag nbt, String id) {
		Tag inbt = nbt.get(id);
		if (inbt != null)
			return inbt;
		return new CompoundTag();
	}

	public static CompoundTag intToCompound(int i) {
		CompoundTag compoundTag = new CompoundTag();
		compoundTag.putInt("V", i);
		return compoundTag;
	}

	public static int intFromCompound(CompoundTag compoundTag) {
		return compoundTag.getIntOr("V", 0);
	}

	public static void writeResourceLocation(CompoundTag nbt, String key, Identifier location) {
		nbt.putString(key, location.toString());
	}

	public static Identifier readResourceLocation(CompoundTag nbt, String key) {
		return Identifier.parse(nbt.getStringOr(key, ""));
	}

	// ValueOutput / ValueInput forms (block entity save and load in 26.3)

	public static <T extends Enum<?>> void writeEnum(ValueOutput output, String key, T enumConstant) {
		output.putString(key, enumConstant.name());
	}

	public static <T extends Enum<?>> T readEnum(ValueInput input, String key, Class<T> enumClass) {
		T[] enumConstants = enumClass.getEnumConstants();
		if (enumConstants == null)
			throw new IllegalArgumentException("Non-Enum class passed to readEnum: " + enumClass.getName());
		return matchEnum(enumConstants, input.getString(key));
	}

	public static void writeVec3(ValueOutput output, String key, Vec3 vec) {
		output.store(key, Vec3.CODEC, vec);
	}

	public static Vec3 readVec3(ValueInput input, String key) {
		return input.read(key, Vec3.CODEC).orElse(Vec3.ZERO);
	}

	public static void writeAABB(ValueOutput output, String key, AABB bb) {
		output.store(key, Codec.FLOAT.listOf(), List.of((float) bb.minX, (float) bb.minY, (float) bb.minZ,
			(float) bb.maxX, (float) bb.maxY, (float) bb.maxZ));
	}

	@Nullable
	public static AABB readAABB(ValueInput input, String key) {
		List<Float> values = input.read(key, Codec.FLOAT.listOf()).orElse(List.of());
		if (values.isEmpty())
			return null;
		return new AABB(floatAt(values, 0), floatAt(values, 1), floatAt(values, 2), floatAt(values, 3),
			floatAt(values, 4), floatAt(values, 5));
	}

	public static void writeItemList(ValueOutput output, String key, List<ItemStack> stacks) {
		ValueOutput.TypedOutputList<ItemStack> list = output.list(key, ItemStack.OPTIONAL_CODEC);
		for (ItemStack stack : stacks)
			list.add(stack);
	}

	public static List<ItemStack> readItemList(ValueInput input, String key) {
		return input.listOrEmpty(key, ItemStack.OPTIONAL_CODEC).stream().toList();
	}

	private static <T extends Enum<?>> T matchEnum(T[] enumConstants, Optional<String> name) {
		if (name.isPresent()) {
			for (T t : enumConstants) {
				if (t.name().equals(name.get()))
					return t;
			}
		}
		return enumConstants[0];
	}

	private static float floatAt(List<Float> values, int index) {
		return index < values.size() ? values.get(index) : 0;
	}

}

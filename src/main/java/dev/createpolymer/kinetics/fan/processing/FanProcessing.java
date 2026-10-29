package dev.createpolymer.kinetics.fan.processing;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.createpolymer.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import dev.createpolymer.kinetics.belt.transport.TransportedItemStack;
import dev.createpolymer.kinetics.fan.FanConfig;
import dev.createpolymer.registry.feature.FanRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FanProcessing {

	/**
	 * The processing progress of an item entity (Create keeps it in the entity's persistent data under
	 * CreateData/Processing); stored as a Fabric data attachment here.
	 */
	public record Data(String type, int time) {
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("Type").forGetter(Data::type),
			Codec.INT.fieldOf("Time").forGetter(Data::time)
		).apply(i, Data::new));
	}

	public static boolean canProcess(ItemEntity entity, FanProcessingType type) {
		Data processing = entity.getAttached(FanRegistry.PROCESSING_DATA);
		if (processing != null) {
			if (AllFanProcessingTypes.parseLegacy(processing.type()) != type)
				return type.canProcess(entity.getItem(), entity.level());
			else if (processing.time() >= 0)
				return true;
			else if (processing.time() == -1)
				return false;
		}
		return type.canProcess(entity.getItem(), entity.level());
	}

	public static boolean applyProcessing(ItemEntity entity, FanProcessingType type) {
		if (decrementProcessingTime(entity, type) != 0)
			return false;
		List<ItemStack> stacks = type.process(entity.getItem(), entity.level());
		if (stacks == null)
			return false;
		if (stacks.isEmpty()) {
			entity.discard();
			return false;
		}
		entity.setItem(stacks.remove(0));
		for (ItemStack additional : stacks) {
			ItemEntity entityIn = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), additional);
			entityIn.setDeltaMovement(entity.getDeltaMovement());
			entity.level().addFreshEntity(entityIn);
		}
		return true;
	}

	public static TransportedResult applyProcessing(TransportedItemStack transported, Level world, FanProcessingType type) {
		TransportedResult ignore = TransportedResult.doNothing();
		if (transported.processedBy != type) {
			transported.processedBy = type;
			int timeModifierForStackSize = ((transported.stack.getCount() - 1) / 16) + 1;
			int processingTime = (int) (FanConfig.fanProcessingTime * timeModifierForStackSize) + 1;
			transported.processingTime = processingTime;
			if (!type.canProcess(transported.stack, world))
				transported.processingTime = -1;
			return ignore;
		}
		if (transported.processingTime == -1)
			return ignore;
		if (transported.processingTime-- > 0)
			return ignore;

		List<ItemStack> stacks = type.process(transported.stack, world);
		if (stacks == null)
			return ignore;

		List<TransportedItemStack> transportedStacks = new ArrayList<>();
		for (ItemStack additional : stacks) {
			TransportedItemStack newTransported = transported.getSimilar();
			newTransported.stack = additional.copy();
			transportedStacks.add(newTransported);
		}
		return TransportedResult.convertTo(transportedStacks);
	}

	private static int decrementProcessingTime(ItemEntity entity, FanProcessingType type) {
		Data processing = entity.getAttached(FanRegistry.PROCESSING_DATA);

		if (processing == null || AllFanProcessingTypes.parseLegacy(processing.type()) != type) {
			Identifier key = FanProcessingTypeRegistry.getKey(type);
			if (key == null)
				throw new IllegalArgumentException("Could not get id for FanProcessingType " + type + "!");

			int timeModifierForStackSize = ((entity.getItem()
				.getCount() - 1) / 16) + 1;
			int processingTime = (int) (FanConfig.fanProcessingTime * timeModifierForStackSize) + 1;
			processing = new Data(key.toString(), processingTime);
		}

		int value = processing.time() - 1;
		entity.setAttached(FanRegistry.PROCESSING_DATA, new Data(processing.type(), value));
		return value;
	}
}

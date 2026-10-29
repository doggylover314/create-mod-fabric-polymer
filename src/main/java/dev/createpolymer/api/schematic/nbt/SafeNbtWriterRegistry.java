package dev.createpolymer.api.schematic.nbt;

import dev.createpolymer.api.registry.SimpleRegistry;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Registry for safe NBT writers, used for filtering unsafe BlockEntity data out of schematics.
 * <p>
 * This is used to exclude specific tags that would result in exploits, ex. signs that execute commands when clicked.
 * <p>
 * This is provided as an alternative to {@link PartialSafeNBT}.
 */
public class SafeNbtWriterRegistry {
	public static final SimpleRegistry<BlockEntityType<?>, SafeNbtWriter> REGISTRY = SimpleRegistry.create();

	@FunctionalInterface
	public interface SafeNbtWriter {
		/**
		 * Write filtered, safe NBT to the given output. This is always called on the logical server.
		 * @param output the output to write to
		 */
		void writeSafe(BlockEntity be, ValueOutput output);
	}

	private SafeNbtWriterRegistry() {
		throw new AssertionError("This class should not be instantiated");
	}
}

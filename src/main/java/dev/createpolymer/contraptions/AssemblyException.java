package dev.createpolymer.contraptions;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.registry.feature.ContraptionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class AssemblyException extends Exception {

	private static final long serialVersionUID = 1L;
	public final Component component;
	private BlockPos position = null;

	public static void write(ValueOutput output, @Nullable AssemblyException exception) {
		if (exception == null)
			return;

		ValueOutput nbt = output.child("LastException");
		nbt.store("Component", ComponentSerialization.CODEC, exception.component);
		if (exception.hasPosition())
			nbt.putLong("Position", exception.getPosition()
				.asLong());
	}

	@Nullable
	public static AssemblyException read(ValueInput input) {
		ValueInput nbt = input.child("LastException").orElse(null);
		if (nbt == null)
			return null;

		Component component = nbt.read("Component", ComponentSerialization.CODEC).orElse(null);
		if (component == null)
			return null;
		AssemblyException exception = new AssemblyException(component);
		if (nbt.getLong("Position").isPresent())
			exception.position = BlockPos.of(nbt.getLongOr("Position", 0));

		return exception;
	}

	public AssemblyException(Component component) {
		this.component = component;
	}

	public AssemblyException(String langKey, Object... objects) {
		this(Component.translatable("create.gui.assembly.exception." + langKey, objects));
	}

	public static AssemblyException unmovableBlock(BlockPos pos, BlockState state) {
		AssemblyException e = new AssemblyException("unmovableBlock", pos.getX(), pos.getY(), pos.getZ(),
			state.getBlock().getName());
		e.position = pos;
		return e;
	}

	public static AssemblyException unloadedChunk(BlockPos pos) {
		AssemblyException e = new AssemblyException("chunkNotLoaded", pos.getX(), pos.getY(), pos.getZ());
		e.position = pos;
		return e;
	}

	public static AssemblyException structureTooLarge() {
		return new AssemblyException("structureTooLarge", ContraptionRegistry.maxBlocksMoved);
	}

	public static AssemblyException tooManyPistonPoles() {
		return new AssemblyException("tooManyPistonPoles", ContraptionRegistry.maxPistonPoles);
	}

	public static AssemblyException noPistonPoles() {
		return new AssemblyException("noPistonPoles");
	}

	public static AssemblyException notEnoughSails(int sails) {
		return new AssemblyException("not_enough_sails", sails, ContraptionRegistry.minimumWindmillSails);
	}

	public boolean hasPosition() {
		return position != null;
	}

	public BlockPos getPosition() {
		return position;
	}
}

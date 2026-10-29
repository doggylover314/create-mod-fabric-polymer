package dev.createpolymer.fluids.pipes;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.createpolymer.fluids.FluidTransportBehaviour;
import dev.createpolymer.fluids.FluidTransportBehaviour.AttachmentTypes;
import dev.createpolymer.util.Iterate;
import dev.createpolymer.visual.ModelItems;
import dev.createpolymer.visual.Part;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What PipeAttachmentModel adds around a pipe: for every side the connection arm, rim or drain that the pipe's
 * {@link FluidTransportBehaviour#getRenderedRimAttachment} asks for. Create stacks up to two partial models per
 * side; the build script merges them (models {@code fluid_pipe/polymer_attachment/<type>_<side>}), so a side is
 * a single display entity, which only exists while the side has something to draw.
 */
public class PipeAttachments {
	private final ElementHolder holder;
	private final Map<Direction, Part> parts = new EnumMap<>(Direction.class);
	private final Map<Direction, AttachmentTypes> shown = new EnumMap<>(Direction.class);

	public PipeAttachments(ElementHolder holder) {
		this.holder = holder;
	}

	public void update(BlockGetter level, BlockPos pos, BlockState state, @Nullable FluidTransportBehaviour transport) {
		for (Direction d : Iterate.directions) {
			AttachmentTypes type = transport == null ? AttachmentTypes.NONE
				: transport.getRenderedRimAttachment(level, pos, state, d);
			if (type == shown.getOrDefault(d, AttachmentTypes.NONE))
				continue;
			shown.put(d, type);
			if (type == AttachmentTypes.NONE) {
				Part part = parts.remove(d);
				if (part != null)
					holder.removeElement(part.element);
				continue;
			}
			Part part = parts.get(d);
			if (part == null) {
				part = new Part(ModelItems.block(model(type, d)));
				parts.put(d, part);
				holder.addElement(part.element);
			} else
				part.setItem(ModelItems.block(model(type, d)));
		}
	}

	public static String model(AttachmentTypes type, Direction side) {
		String name = switch (type) {
			case NONE -> throw new IllegalArgumentException();
			case CONNECTION -> "connection";
			case DETAILED_CONNECTION -> "rim_connector";
			case RIM -> "rim";
			case PARTIAL_RIM -> "partial_rim";
			case DRAIN -> "drain";
			case PARTIAL_DRAIN -> "partial_drain";
		};
		return "fluid_pipe/polymer_attachment/" + name + "_" + side.getName();
	}
}

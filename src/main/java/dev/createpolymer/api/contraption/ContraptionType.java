package dev.createpolymer.api.contraption;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import dev.createpolymer.CreatePolymer;
import dev.createpolymer.contraptions.Contraption;
import net.minecraft.resources.Identifier;

/**
 * A kind of contraption, saved by id with every contraption entity. Create keeps these in a custom registry; here
 * they are a plain map filled by {@code ContraptionRegistry}.
 */
public final class ContraptionType {
	private static final Map<Identifier, ContraptionType> TYPES = new LinkedHashMap<>();
	/** Create's AllContraptionTypes.BY_LEGACY_NAME: the short names older worlds saved. */
	private static final Map<String, ContraptionType> BY_LEGACY_NAME = new LinkedHashMap<>();

	public final Supplier<? extends Contraption> factory;
	public final Identifier id;

	public ContraptionType(Identifier id, Supplier<? extends Contraption> factory) {
		this.id = id;
		this.factory = factory;
	}

	/** Registers a contraption type as create:&lt;name&gt; (Create's AllContraptionTypes.register). */
	public static ContraptionType register(String name, Supplier<? extends Contraption> factory) {
		ContraptionType type = new ContraptionType(CreatePolymer.id(name), factory);
		if (TYPES.putIfAbsent(type.id, type) != null)
			throw new IllegalArgumentException("Duplicate contraption type " + type.id);
		BY_LEGACY_NAME.put(name, type);
		return type;
	}

	/**
	 * Lookup the ContraptionType with the given ID, and create a new Contraption from it if present.
	 * If it doesn't exist, returns null.
	 */
	@Nullable
	public static Contraption fromType(String typeId) {
		ContraptionType legacy = BY_LEGACY_NAME.get(typeId);
		if (legacy != null) {
			return legacy.factory.get();
		}

		Identifier id = Identifier.tryParse(typeId);
		ContraptionType type = id == null ? null : TYPES.get(id);
		return type == null ? null : type.factory.get();
	}
}

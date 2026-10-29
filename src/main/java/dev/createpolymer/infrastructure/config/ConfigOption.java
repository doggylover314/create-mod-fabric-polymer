package dev.createpolymer.infrastructure.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * One setting of create.json: the section and key it has in Create's server config, its default and range, and the
 * static field it drives.
 */
public abstract class ConfigOption<T> {
	public final String section;
	public final String key;
	public final T defaultValue;
	private final Supplier<T> getter;
	private final Consumer<T> setter;

	protected ConfigOption(String section, String key, T defaultValue, Supplier<T> getter, Consumer<T> setter) {
		this.section = section;
		this.key = key;
		this.defaultValue = defaultValue;
		this.getter = getter;
		this.setter = setter;
	}

	public String path() {
		return section + "." + key;
	}

	public T current() {
		return getter.get();
	}

	public void set(T value) {
		setter.accept(value);
	}

	public abstract JsonElement toJson(T value);

	/**
	 * Reads a value from the file, clamped to the allowed range. Returns null, after adding a warning, when the
	 * element is not usable at all.
	 */
	@Nullable
	public abstract T parse(JsonElement element, List<String> warnings);

	public JsonElement defaultJson() {
		return toJson(defaultValue);
	}

	/** Reads the element and sets the field; an unusable element resets the field to its default. */
	public void apply(JsonElement element, List<String> warnings) {
		T value = parse(element, warnings);
		set(value == null ? defaultValue : value);
	}

	public void reset() {
		set(defaultValue);
	}

	public static final class IntOption extends ConfigOption<Integer> {
		public final int min;
		public final int max;

		public IntOption(String section, String key, int def, int min, int max, Supplier<Integer> getter,
			Consumer<Integer> setter) {
			super(section, key, def, getter, setter);
			this.min = min;
			this.max = max;
		}

		@Override
		public JsonElement toJson(Integer value) {
			return new JsonPrimitive(value);
		}

		@Override
		@Nullable
		public Integer parse(JsonElement element, List<String> warnings) {
			BigDecimal number = number(element);
			if (number == null) {
				warnings.add(path() + ": expected a whole number, using the default " + defaultValue);
				return null;
			}
			if (number.stripTrailingZeros().scale() > 0) {
				warnings.add(path() + ": " + number.toPlainString() + " is not a whole number, using the default " + defaultValue);
				return null;
			}
			if (number.compareTo(BigDecimal.valueOf(min)) < 0) {
				warnings.add(path() + ": " + number.toPlainString() + " is below the minimum " + min + ", using " + min);
				return min;
			}
			if (number.compareTo(BigDecimal.valueOf(max)) > 0) {
				warnings.add(path() + ": " + number.toPlainString() + " is above the maximum " + max + ", using " + max);
				return max;
			}
			return number.intValueExact();
		}
	}

	public static final class FloatOption extends ConfigOption<Float> {
		public final float min;
		public final float max;

		public FloatOption(String section, String key, float def, float min, float max, Supplier<Float> getter,
			Consumer<Float> setter) {
			super(section, key, def, getter, setter);
			this.min = min;
			this.max = max;
		}

		@Override
		public JsonElement toJson(Float value) {
			return new JsonPrimitive(value);
		}

		@Override
		@Nullable
		public Float parse(JsonElement element, List<String> warnings) {
			BigDecimal number = number(element);
			if (number == null) {
				warnings.add(path() + ": expected a number, using the default " + defaultValue);
				return null;
			}
			float value = number.floatValue();
			if (value < min) {
				warnings.add(path() + ": " + number.toPlainString() + " is below the minimum " + min + ", using " + min);
				return min;
			}
			if (value > max) {
				warnings.add(path() + ": " + number.toPlainString() + " is above the maximum " + max + ", using " + max);
				return max;
			}
			return value;
		}
	}

	public static final class BoolOption extends ConfigOption<Boolean> {
		public BoolOption(String section, String key, boolean def, Supplier<Boolean> getter, Consumer<Boolean> setter) {
			super(section, key, def, getter, setter);
		}

		@Override
		public JsonElement toJson(Boolean value) {
			return new JsonPrimitive(value);
		}

		@Override
		@Nullable
		public Boolean parse(JsonElement element, List<String> warnings) {
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean())
				return element.getAsBoolean();
			warnings.add(path() + ": expected true or false, using the default " + defaultValue);
			return null;
		}
	}

	public static final class EnumOption<E extends Enum<E>> extends ConfigOption<E> {
		private final Class<E> type;

		public EnumOption(String section, String key, E def, Class<E> type, Supplier<E> getter, Consumer<E> setter) {
			super(section, key, def, getter, setter);
			this.type = type;
		}

		@Override
		public JsonElement toJson(E value) {
			return new JsonPrimitive(value.name());
		}

		@Override
		@Nullable
		public E parse(JsonElement element, List<String> warnings) {
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				String name = element.getAsString();
				for (E constant : type.getEnumConstants())
					if (constant.name().equalsIgnoreCase(name))
						return constant;
			}
			StringBuilder options = new StringBuilder();
			for (E constant : type.getEnumConstants())
				options.append(options.isEmpty() ? "" : ", ").append(constant.name());
			warnings.add(path() + ": expected one of " + options + ", using the default " + defaultValue.name());
			return null;
		}
	}

	@Nullable
	private static BigDecimal number(JsonElement element) {
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
			return element.getAsBigDecimal();
		return null;
	}
}

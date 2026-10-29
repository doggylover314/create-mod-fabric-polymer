package dev.createpolymer.trains.display;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * One stretch of a line on a display board. Create keeps the flip animation on the client; without a client the
 * block entity runs it ({@link #tick}) and the board's model shows the flaps that are spinning.
 */
public class FlapDisplaySection {
	/** The flap cycles of Create's language file (flap_display.cycles.*). */
	private static final Map<String, String[]> LOADED_FLAP_CYCLES = new HashMap<>();
	static {
		LOADED_FLAP_CYCLES.put("alphabet", " ;A;B;C;D;E;F;G;H;I;J;K;L;M;N;O;P;Q;R;S;T;U;V;X;Y;Z".split(";"));
		LOADED_FLAP_CYCLES.put("arrival_time", "   ; min;now;15s;30s;45s".split(";"));
		LOADED_FLAP_CYCLES.put("fluid_units", "mB;B ".split(";"));
		LOADED_FLAP_CYCLES.put("instant", " ; ".split(";"));
		LOADED_FLAP_CYCLES.put("numeric", " ;0;1;2;3;4;5;6;7;8;9".split(";"));
		LOADED_FLAP_CYCLES.put("pixel", "█;▓;▒".split(";"));
		LOADED_FLAP_CYCLES.put("shortened_numbers", " ;K;M".split(";"));
		// Create's value lists use a cycle called "number" that its language file does not have: use the digits
		LOADED_FLAP_CYCLES.put("number", LOADED_FLAP_CYCLES.get("numeric"));
	}

	public static final float MONOSPACE = 7;
	public static final float WIDE_MONOSPACE = 9;

	float size;
	boolean singleFlap;
	boolean hasGap;
	boolean rightAligned;
	boolean wideFlaps;
	String cycle;
	Component component;

	String[] cyclingOptions;
	boolean[] spinning;
	int spinningTicks;
	String text;

	public FlapDisplaySection(float width, String cycle, boolean singleFlap, boolean hasGap) {
		this.size = width;
		this.cycle = cycle;
		this.hasGap = hasGap;
		this.singleFlap = singleFlap;
		this.spinning = new boolean[singleFlap ? 1 : Math.max(0, (int) (width / FlapDisplaySection.MONOSPACE))];
		this.text = StringUtils.repeat(" ", spinning.length);
		this.component = null;
		this.cyclingOptions = getFlapCycle(cycle);
	}

	public FlapDisplaySection rightAligned() {
		rightAligned = true;
		return this;
	}

	public FlapDisplaySection wideFlaps() {
		wideFlaps = true;
		return this;
	}

	public void setText(Component component) {
		this.component = component;
		refresh(true);
	}

	public void refresh(boolean transition) {
		if (component == null)
			return;

		String newText = component.getString();

		if (!singleFlap) {
			if (rightAligned)
				newText = newText.trim();
			newText = newText.toUpperCase(Locale.ROOT);
			newText = newText.substring(0, Math.min(spinning.length, newText.length()));
			String whitespace = StringUtils.repeat(" ", spinning.length - newText.length());
			newText = rightAligned ? whitespace + newText : newText + whitespace;
			if (!text.isEmpty())
				for (int i = 0; i < spinning.length; i++)
					spinning[i] |= transition && text.charAt(i) != newText.charAt(i);
		} else if (!text.isEmpty())
			spinning[0] |= transition && !newText.equals(text);

		text = newText;
		spinningTicks = 0;
	}

	public int tick(boolean instant, RandomSource randomSource) {
		if (cyclingOptions == null)
			return 0;
		int max = Math.max(4, (int) (cyclingOptions.length * 1.75f));
		if (spinningTicks > max)
			return 0;

		spinningTicks++;
		if (spinningTicks <= max && spinningTicks < 2)
			return spinningTicks == 1 ? 0 : spinning.length;

		int spinningFlaps = 0;
		for (int i = 0; i < spinning.length; i++) {
			int increasingChance = Mth.clamp(8 - spinningTicks, 1, 10);
			boolean continueSpin = !instant && randomSource.nextInt(increasingChance * max / 4) != 0;
			continueSpin &= max > 5 || spinningTicks < 2;
			spinning[i] &= continueSpin;

			if (i > 0 && randomSource.nextInt(3) > 0)
				spinning[i - 1] &= continueSpin;
			if (i < spinning.length - 1 && randomSource.nextInt(3) > 0)
				spinning[i + 1] &= continueSpin;
			if (spinningTicks > max)
				spinning[i] = false;

			if (spinning[i])
				spinningFlaps++;
		}

		return spinningFlaps;
	}

	public float getSize() {
		return size;
	}

	public boolean hasGap() {
		return hasGap;
	}

	public boolean isWide() {
		return wideFlaps;
	}

	public boolean isRightAligned() {
		return rightAligned;
	}

	public boolean isSingleFlap() {
		return singleFlap;
	}

	public String getCycle() {
		return cycle;
	}

	/** What the section shows once the flaps stopped, padded to the number of flaps. */
	public String getShownText() {
		return text;
	}

	public boolean isSpinning(int flap) {
		return flap >= 0 && flap < spinning.length && spinning[flap];
	}

	public int getSpinningTicks() {
		return spinningTicks;
	}

	public String[] getCyclingOptions() {
		return cyclingOptions;
	}

	public int flapCount() {
		return spinning.length;
	}

	/** A key for the geometry of the section, to tell when a line has to be laid out again. */
	String shape() {
		return size + cycle + (singleFlap ? "s" : "") + (hasGap ? "g" : "") + (rightAligned ? "r" : "") + (wideFlaps ? "w" : "");
	}

	public void write(ValueOutput output) {
		output.putFloat("Width", size);
		output.putString("Cycle", cycle);
		if (rightAligned)
			output.putBoolean("RightAligned", true);
		if (singleFlap)
			output.putBoolean("SingleFlap", true);
		if (hasGap)
			output.putBoolean("Gap", true);
		if (wideFlaps)
			output.putBoolean("Wide", true);
		if (component != null)
			output.store("Text", ComponentSerialization.CODEC, component);
	}

	public static FlapDisplaySection load(ValueInput input) {
		float width = input.getFloatOr("Width", 0);
		String cycle = input.getStringOr("Cycle", "alphabet");
		boolean singleFlap = input.getBooleanOr("SingleFlap", false);
		boolean hasGap = input.getBooleanOr("Gap", false);

		FlapDisplaySection section = new FlapDisplaySection(width, cycle, singleFlap, hasGap);
		section.rightAligned = input.getBooleanOr("RightAligned", false);
		section.wideFlaps = input.getBooleanOr("Wide", false);

		input.read("Text", ComponentSerialization.CODEC)
			.ifPresent(c -> {
				section.component = c;
				section.refresh(false);
			});
		return section;
	}

	public void update(ValueInput input) {
		input.read("Text", ComponentSerialization.CODEC)
			.ifPresent(c -> component = c);
		refresh(false);
	}

	public Component getText() {
		return component;
	}

	public static String[] getFlapCycle(String key) {
		return LOADED_FLAP_CYCLES.computeIfAbsent(key, k -> new String[] { " " });
	}
}

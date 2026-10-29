package harness;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConfirmScreen;

/**
 * Reads harness_script.txt from the game directory. Lines:
 *   wait N        - wait N ticks
 *   cmd /...      - send a command (player must be op)
 *   shot NAME     - take screenshot screenshots/NAME.png
 *   log           - print player/screen state
 *   quit          - close the game
 */
public final class Harness {
	private static List<String> script;
	private static int line = 0;
	private static int waitTicks = 0;
	private static int inWorldTicks = 0;

	public static void tick(Minecraft mc) {
		if (mc.gui.screen() instanceof ConfirmScreen cs && cs.getClass().getName().contains("PackConfirm")) {
			try {
				Field f = ConfirmScreen.class.getDeclaredField("callback");
				f.setAccessible(true);
				System.out.println("[harness] accepting server resource pack");
				((BooleanConsumer) f.get(cs)).accept(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (mc.player == null || mc.level == null)
			return;
		inWorldTicks++;
		if (script == null) {
			try {
				script = new ArrayList<>(Files.readAllLines(new File(mc.gameDirectory, "harness_script.txt").toPath()));
			} catch (Exception e) {
				script = List.of("wait 200", "shot fallback", "wait 40", "quit");
			}
		}
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		while (line < script.size()) {
			String l = script.get(line++).trim();
			if (l.isEmpty() || l.startsWith("#"))
				continue;
			System.out.println("[harness] " + l);
			if (l.startsWith("wait ")) {
				waitTicks = Integer.parseInt(l.substring(5).trim());
				return;
			} else if (l.startsWith("cmd /")) {
				mc.player.connection.sendCommand(l.substring(5));
			} else if (l.startsWith("shot ")) {
				String name = l.substring(5).trim() + ".png";
				Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, msg -> System.out.println("[harness] " + msg.getString()));
				waitTicks = 10;
				return;
			} else if (l.equals("log")) {
				System.out.println("[harness] pos=" + mc.player.position() + " screen=" + mc.gui.screen());
			} else if (l.equals("quit")) {
				mc.stop();
				return;
			}
		}
	}
}

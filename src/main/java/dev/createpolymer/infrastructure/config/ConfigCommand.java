package dev.createpolymer.infrastructure.config;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /create config reload: reads config/create.json again. Needs operator level 2. */
public final class ConfigCommand {
	private ConfigCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> register(dispatcher));
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("create")
			.then(Commands.literal("config")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reload")
					.executes(context -> reload(context.getSource())))));
	}

	private static int reload(CommandSourceStack source) {
		CreateConfig.Result result = CreateConfig.reload();
		if (!result.loaded()) {
			source.sendFailure(Component.literal("Could not read " + CreateConfig.FILE_NAME + ", keeping the current settings. See the server log."));
			return 0;
		}
		int warnings = result.warnings().size();
		source.sendSuccess(() -> Component.literal("Reloaded " + CreateConfig.FILE_NAME
			+ (warnings == 0 ? "" : " with " + warnings + " warning" + (warnings == 1 ? "" : "s") + ", see the server log")
			+ ". Vault, tank and similar sizes apply to blocks placed from now on."), true);
		return 1;
	}
}

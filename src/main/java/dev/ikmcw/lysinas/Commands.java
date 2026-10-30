package dev.ikmcw.lysinas;

import dev.ikmcw.lysinas.config.ConfigManager;
import dev.ikmcw.lysinas.config.ExtraConfig;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/** Client-side slash commands for listing and switching gradient profiles. */
public final class Commands {
	private Commands() {}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("lysinas")
						.executes(context -> {
							Main.requestOpenSettings();
							return 1;
						})
						.then(ClientCommands.literal("profile")
								.then(ClientCommands.literal("list").executes(context -> list(context.getSource())))
								.then(ClientCommands.literal("current").executes(context -> current(context.getSource())))
								.then(ClientCommands.literal("auto").executes(context -> useAuto(context.getSource())))
								.then(ClientCommands.literal("use")
										.then(ClientCommands.argument("name", StringArgumentType.greedyString())
												.suggests((context, builder) -> {
													for (ExtraConfig.ItemProfile profile : ConfigManager.get().itemProfiles) {
														builder.suggest(profile.name);
													}
													return builder.buildFuture();
												})
											.executes(context -> useProfile(context.getSource(),
													StringArgumentType.getString(context, "name"))))))));
	}

	private static int list(FabricClientCommandSource source) {
		ExtraConfig config = ConfigManager.get();
		if (config.itemProfiles.isEmpty()) {
			source.sendFeedback(Component.literal("Lysinas: no item profiles configured."));
			return 0;
		}
		source.sendFeedback(Component.literal("Lysinas profiles (use /lysinas profile use <name>):"));
		for (ExtraConfig.ItemProfile profile : config.itemProfiles) {
			boolean active = profile.name.equalsIgnoreCase(config.activeProfile);
			source.sendFeedback(Component.literal((active ? "* " : "  ") + profile.name));
		}
		if (config.activeProfile == null || config.activeProfile.isBlank()) {
			source.sendFeedback(Component.literal("Mode: AUTO (item/enchantment selectors choose the profile)."));
		}
		return config.itemProfiles.size();
	}

	private static int current(FabricClientCommandSource source) {
		String active = ConfigManager.get().activeProfile;
		if (active == null || active.isBlank()) {
			source.sendFeedback(Component.literal("Gradient profile mode: AUTO (item/enchantment selectors)."));
		} else {
			source.sendFeedback(Component.literal("Gradient profile forced for all matching enchantment tooltips: " + active));
		}
		return 1;
	}

	private static int useAuto(FabricClientCommandSource source) {
		ExtraConfig updated = ConfigManager.copyCurrent();
		updated.activeProfile = "";
		ConfigManager.save(updated);
		source.sendFeedback(Component.literal("Gradient profile mode set to AUTO."));
		return 1;
	}

	private static int useProfile(FabricClientCommandSource source, String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		ExtraConfig updated = ConfigManager.copyCurrent();
		int index = updated.findProfileIndexByName(name);
		if (index == 0) {
			source.sendError(Component.literal("Unknown profile: " + name + ". Run /lysinas profile list."));
			return 0;
		}
		updated.activeProfile = updated.itemProfiles.get(index - 1).name;
		ConfigManager.save(updated);
		source.sendFeedback(Component.literal("Gradient profile forced to " + updated.activeProfile
				+ ". Use /lysinas profile auto to return to item-based selection."));
		return 1;
	}
}

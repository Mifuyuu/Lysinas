package net.lysinas;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class Lysinas implements ClientModInitializer {
	public static final String MOD_ID = "lysinas";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static KeyMapping openSettingsKey;
	/** Set by /lysinas; opened on the next tick because the chat screen closes after the command runs. */
	private static volatile boolean openRequested;

	@Override
	public void onInitializeClient() {
		ConfigManager.init();
		EnchantmentData.load();
		LysinasCommands.register();
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.lysinas.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) LysinasScreen.resetOpenState();
			if (openRequested) {
				openRequested = false;
				if (!LysinasScreen.isOpen()) client.setScreenAndShow(new LysinasScreen(null));
			}
			while (openSettingsKey.consumeClick()) {
				if (client.player != null && !LysinasScreen.isOpen()) {
					client.setScreenAndShow(new LysinasScreen(null));
				}
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> GradientShaderRenderer.close());
		LOGGER.info("Lysinas initialized.");
	}

	public static void requestOpenSettings() {
		openRequested = true;
	}

	public static boolean isActive() {
		ExtraConfig config = ConfigManager.get();
		if (!config.enabled) return false;
		if (!config.onlyOnHypixel) return true;
		Minecraft client = Minecraft.getInstance();
		if (client.getCurrentServer() == null || client.getCurrentServer().ip == null) return false;
		String address = client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
		return address.contains("hypixel.net") || address.contains("hypixel.io");
	}
}

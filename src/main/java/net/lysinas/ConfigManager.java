package net.lysinas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("lysinas.json");
	/** Config written before the rename from ExtraEnchColour; read once if the new file does not exist yet. */
	private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("extraenchcolour.json");
	private static volatile ExtraConfig config = new ExtraConfig();

	private ConfigManager() {}

	public static void init() {
		reload();
	}

	public static ExtraConfig get() {
		return config;
	}

	/** Applies only the render-mode toggle in memory; the editor persists it on Save or restores it on Cancel. */
	public static synchronized void previewShaderMode(ExtraConfig.ShaderAnimationMode mode) {
		if (mode != null) config.shaderAnimationMode = mode;
	}

	public static synchronized ExtraConfig copyCurrent() {
		return GSON.fromJson(GSON.toJson(config), ExtraConfig.class);
	}

	public static synchronized void save(ExtraConfig updated) {
		if (updated == null) return;
		updated.normalize();
		config = updated;
		write(updated);
	}

	public static synchronized void reload() {
		ExtraConfig loaded = null;
		Path source = Files.isRegularFile(PATH) || !Files.isRegularFile(LEGACY_PATH) ? PATH : LEGACY_PATH;
		if (Files.isRegularFile(source)) {
			try {
				loaded = GSON.fromJson(Files.readString(source), ExtraConfig.class);
				if (source == LEGACY_PATH) Lysinas.LOGGER.info("Migrated settings from {} to {}.", LEGACY_PATH, PATH);
			} catch (Exception exception) {
				Lysinas.LOGGER.error("Could not read {}. Using defaults.", source, exception);
			}
		}
		if (loaded == null) loaded = new ExtraConfig();
		loaded.normalize();
		config = loaded;
		write(loaded);
	}

	private static void write(ExtraConfig value) {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(value));
		} catch (IOException exception) {
			Lysinas.LOGGER.error("Could not write {}.", PATH, exception);
		}
	}
}

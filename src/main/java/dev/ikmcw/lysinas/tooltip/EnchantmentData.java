package dev.ikmcw.lysinas.tooltip;

import dev.ikmcw.lysinas.Main;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class EnchantmentData {
	private static final Map<String, Entry> ENCHANTMENTS = new HashMap<>();

	private EnchantmentData() {}

	public static void load() {
		try (InputStream stream = EnchantmentData.class.getResourceAsStream("/lysinas/enchantments.json")) {
			if (stream == null) throw new IllegalStateException("Bundled enchantment data is missing");
			Map<String, Entry> parsed = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
					new TypeToken<Map<String, Entry>>() {}.getType());
			ENCHANTMENTS.clear();
			if (parsed != null) {
				for (Entry entry : parsed.values()) {
					if (entry != null && entry.id != null && entry.name != null && entry.maxLevel > 0) {
						ENCHANTMENTS.put(entry.id, entry);
						ENCHANTMENTS.putIfAbsent(entry.id.toLowerCase(Locale.ROOT), entry);
					}
				}
			}
			Main.LOGGER.info("Loaded {} enchantment definitions.", parsed == null ? 0 : parsed.size());
		} catch (Exception exception) {
			ENCHANTMENTS.clear();
			Main.LOGGER.error("Could not load bundled enchantment data.", exception);
		}
	}

	public static Entry find(String id) {
		return ENCHANTMENTS.get(id);
	}

	public static Map<String, Entry> all() {
		return Collections.unmodifiableMap(ENCHANTMENTS);
	}

	public static final class Entry {
		private String id;
		private String name;
		private int maxLevel;
		private Integer goodLevel;

		public boolean isMax(int level) {
			return level >= maxLevel;
		}

		public boolean isGood(int level) {
			return goodLevel != null && level >= goodLevel && !isMax(level);
		}

		public String displayName(int level) {
			return name + " " + RomanNumerals.toRoman(level);
		}
	}
}

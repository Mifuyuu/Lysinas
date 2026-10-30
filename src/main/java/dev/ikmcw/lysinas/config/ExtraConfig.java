package dev.ikmcw.lysinas.config;

import dev.ikmcw.lysinas.gradient.Gradient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** User-editable settings stored in config/lysinas.json. */
public final class ExtraConfig {
	public boolean enabled = true;
	public boolean onlyOnHypixel = true;
	public boolean showGoodEnchantments = true;
	public String goodEnchantmentColor = "#FFAA00";
	public ShaderAnimationMode shaderAnimationMode = ShaderAnimationMode.ANIMATED;
	/** Empty means automatically resolve a profile from the held item's selectors. */
	public String activeProfile = "";
	public MaxEnchantments maxEnchantments = new MaxEnchantments();
	public List<ItemProfile> itemProfiles = new ArrayList<>();

	public void normalize() {
		if (goodEnchantmentColor == null || !Gradient.isValidColor(goodEnchantmentColor)) {
			goodEnchantmentColor = "#FFAA00";
		}
		if (maxEnchantments == null) maxEnchantments = new MaxEnchantments();
		maxEnchantments.normalize();
		if (shaderAnimationMode == null) shaderAnimationMode = ShaderAnimationMode.ANIMATED;
		if (itemProfiles == null) itemProfiles = new ArrayList<>();
		List<ItemProfile> validProfiles = new ArrayList<>();
		Set<String> names = new java.util.HashSet<>();
		for (ItemProfile profile : itemProfiles) {
			if (profile == null || validProfiles.size() >= 32) continue;
			profile.normalize();
			String key = profile.name.toLowerCase(Locale.ROOT);
			if (profile.itemIds.isEmpty() && profile.enchantmentIds.isEmpty()) continue;
			if (!names.add(key)) continue;
			validProfiles.add(profile);
		}
		itemProfiles = validProfiles;
		int activeIndex = findProfileIndexByName(activeProfile);
		activeProfile = activeIndex == 0 ? "" : itemProfiles.get(activeIndex - 1).name;
	}

	public ItemProfile findProfile(String registryItemId, String customItemId, Set<String> enchantmentIds) {
		int index = findProfileIndex(registryItemId, customItemId, enchantmentIds);
		return index <= 0 ? null : itemProfiles.get(index - 1);
	}

	/** Returns zero for the default palette and one-based slots for matching profiles. */
	public int findProfileIndex(String registryItemId, String customItemId, Set<String> enchantmentIds) {
		if (itemProfiles == null) return 0;
		for (int i = 0; i < itemProfiles.size(); i++) {
			ItemProfile profile = itemProfiles.get(i);
			if (profile != null && profile.matches(registryItemId, customItemId, enchantmentIds)) return i + 1;
		}
		return 0;
	}

	/** Returns zero when no configured profile has this name. */
	public int findProfileIndexByName(String name) {
		if (name == null || name.isBlank() || itemProfiles == null) return 0;
		for (int i = 0; i < itemProfiles.size(); i++) {
			if (itemProfiles.get(i).name.equalsIgnoreCase(name.trim())) return i + 1;
		}
		return 0;
	}

	public enum ShaderAnimationMode { STATIC, ANIMATED }

	public static final class ItemProfile {
		public String name = "Profile";
		/** Registry IDs (e.g. minecraft:diamond_sword) or Hypixel item IDs (e.g. ASPECT_OF_THE_END). */
		public List<String> itemIds = new ArrayList<>();
		/** Enchantment IDs from the item's custom data enchantments compound. */
		public List<String> enchantmentIds = new ArrayList<>();
		public MaxEnchantments gradient = new MaxEnchantments();

		private void normalize() {
			name = name == null || name.isBlank() ? "Profile" : name.trim();
			itemIds = normalizeIds(itemIds);
			enchantmentIds = normalizeIds(enchantmentIds);
			if (gradient == null) gradient = new MaxEnchantments();
			gradient.normalize();
		}

		private static List<String> normalizeIds(List<String> values) {
			List<String> result = new ArrayList<>();
			if (values == null) return result;
			for (String value : values) {
				if (value == null || value.isBlank()) continue;
				String normalized = value.trim();
				if (normalized.length() <= 128 && result.size() < 32 && result.stream().noneMatch(existing -> existing.equalsIgnoreCase(normalized))) {
					result.add(normalized);
				}
			}
			return result;
		}

		private boolean matches(String registryItemId, String customItemId, Set<String> presentEnchantments) {
			if (itemIds.isEmpty() && enchantmentIds.isEmpty()) return false;
			if (!itemIds.isEmpty()) {
				boolean itemMatches = false;
				for (String id : itemIds) {
					if (id.equalsIgnoreCase(registryItemId) || id.equalsIgnoreCase(customItemId)) {
						itemMatches = true;
						break;
					}
				}
				if (!itemMatches) return false;
			}
			if (!enchantmentIds.isEmpty()) {
				if (presentEnchantments == null) return false;
				boolean enchantmentMatches = false;
				for (String wanted : enchantmentIds) {
					for (String present : presentEnchantments) {
						if (wanted.equalsIgnoreCase(present)) {
							enchantmentMatches = true;
							break;
						}
					}
					if (enchantmentMatches) break;
				}
				if (!enchantmentMatches) return false;
			}
			return true;
		}
	}

	public static final class MaxEnchantments {
		public Animation animation = Animation.FULL_TEXT_CYCLE;
		public long animationPeriodMs = 1800L;
		/** Multiplier applied to the animation phase; 1.0 is the configured base speed. */
		public float animationSpeed = 1.0f;
		/** Spatial sine-wave offset in normalized gradient units, from 0.0 to 0.5. */
		public float sineWaveOffset = 0.0f;
		/** 0 = gradient follows the text; 1–200 = Aaron's Mod style diagonal screen-space chroma, as % of screen width. */
		public float chromaSize = 0.0f;
		/** Saturation used only by {@link Animation#AARON_CHROMA}; Aaron's Mod defaults to 0.75. */
		public float chromaSaturation = 0.75f;
		public Interpolation interpolation = Interpolation.HSV;
		public List<GradientStop> stops = defaultStops();

		private void normalize() {
			if (animation == null) animation = Animation.STATIC;
			if (interpolation == null) interpolation = Interpolation.HSV;
			if (animationPeriodMs < 50L) animationPeriodMs = 50L;
			if (animationPeriodMs > 86_400_000L) animationPeriodMs = 86_400_000L;
			if (!Float.isFinite(animationSpeed)) animationSpeed = 1.0f;
			animationSpeed = Math.max(0.1f, Math.min(5.0f, animationSpeed));
			if (!Float.isFinite(sineWaveOffset)) sineWaveOffset = 0.0f;
			sineWaveOffset = Math.max(0.0f, Math.min(0.5f, sineWaveOffset));
			chromaSize = !Float.isFinite(chromaSize) || chromaSize < 1.0f ? 0.0f : Math.min(200.0f, chromaSize);
			chromaSaturation = !Float.isFinite(chromaSaturation) ? 0.75f : Math.max(0.0f, Math.min(1.0f, chromaSaturation));
			// Upgrade the shipped open-ended RGB preset from v1 to a seamless HSB rainbow.
			// User-defined stop lists are left untouched.
			if (animation == Animation.FULL_TEXT_CYCLE && interpolation == Interpolation.RGB && isLegacyDefaultStops()) {
				interpolation = Interpolation.HSV;
				stops = defaultStops();
			}
			if (stops == null || stops.size() < 2 || stops.size() > 8) {
				stops = defaultStops();
				return;
			}

			List<GradientStop> valid = new ArrayList<>();
			for (GradientStop stop : stops) {
				if (stop == null || Float.isNaN(stop.position) || Float.isInfinite(stop.position)
						|| stop.position < 0.0f || stop.position > 1.0f || !Gradient.isValidColor(stop.color)) {
					stops = defaultStops();
					return;
				}
				valid.add(stop);
			}
			valid.sort((left, right) -> Float.compare(left.position, right.position));
			for (int i = 1; i < valid.size(); i++) {
				if (valid.get(i - 1).position == valid.get(i).position) {
					stops = defaultStops();
					return;
				}
			}
			stops = valid;
		}

		private static List<GradientStop> defaultStops() {
			return new ArrayList<>(List.of(
					new GradientStop(0.0f, "#FF0000"),
					new GradientStop(1.0f / 6.0f, "#FFFF00"),
					new GradientStop(2.0f / 6.0f, "#00FF00"),
					new GradientStop(3.0f / 6.0f, "#00FFFF"),
					new GradientStop(4.0f / 6.0f, "#0000FF"),
					new GradientStop(5.0f / 6.0f, "#FF00FF"),
					new GradientStop(1.0f, "#FF0000")));
		}

		private boolean isLegacyDefaultStops() {
			return stops != null && stops.size() == 3
					&& stops.get(0) != null && stops.get(1) != null && stops.get(2) != null
					&& Float.compare(stops.get(0).position, 0.0f) == 0
					&& Float.compare(stops.get(1).position, 0.5f) == 0
					&& Float.compare(stops.get(2).position, 1.0f) == 0
					&& "#FF5555".equalsIgnoreCase(stops.get(0).color)
					&& "#FFFF55".equalsIgnoreCase(stops.get(1).color)
					&& "#55FFFF".equalsIgnoreCase(stops.get(2).color);
		}
	}

	public static final class GradientStop {
		public float position;
		public String color;

		public GradientStop() {}

		public GradientStop(float position, String color) {
			this.position = position;
			this.color = color;
		}
	}

	public enum Animation {
		STATIC,
		LEFT_TO_RIGHT,
		RIGHT_TO_LEFT,
		BOUNCE,
		FULL_TEXT_CYCLE,
		/** Exact port of Aaron's Mod chroma text: screen-space smooth-HSV hue, ignores the colour stops. */
		AARON_CHROMA
	}

	public enum Interpolation {
		RGB,
		HSL,
		/** HSV/HSB interpolation; produces the saturated, bright rainbow used by Aaron's Mod. */
		HSV,
		/** HSV with Aaron's Mod cubic smoothing on each hue channel; softer transitions between primaries. */
		SMOOTH_HSV
	}
}

package dev.ikmcw.lysinas.gradient;

import dev.ikmcw.lysinas.config.ExtraConfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradientTest {
	@Test
	void parsesRgbColours() {
		assertEquals(0x12ABEF, Gradient.parseColor("#12abef"));
		assertEquals(0x12ABEF, Gradient.parseColor("0x12ABEF"));
		assertEquals(0x12ABEF, Gradient.parseColor("  #12abef  "));
	}

	@Test
	void rejectsAlphaColours() {
		assertThrows(IllegalArgumentException.class, () -> Gradient.parseColor("#12ABEF80"));
	}

	@Test
	void staticGradientReachesBothStops() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		settings.animation = ExtraConfig.Animation.STATIC;
		settings.stops = java.util.List.of(
				new ExtraConfig.GradientStop(0.0f, "#000000"),
				new ExtraConfig.GradientStop(1.0f, "#FFFFFF"));
		Gradient gradient = Gradient.from(settings);
		assertEquals(0x000000, gradient.colorAt(0, 2, 0));
		assertEquals(0xFFFFFF, gradient.colorAt(1, 2, 0));
	}

	@Test
	void hslGradientKeepsExactEndpointColors() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		settings.animation = ExtraConfig.Animation.STATIC;
		settings.interpolation = ExtraConfig.Interpolation.HSL;
		settings.stops = java.util.List.of(
				new ExtraConfig.GradientStop(0.0f, "#FF0000"),
				new ExtraConfig.GradientStop(1.0f, "#0000FF"));
		Gradient gradient = Gradient.from(settings);
		assertEquals(0xFF0000, gradient.colorAt(0, 2, 0));
		assertEquals(0x0000FF, gradient.colorAt(1, 2, 0));
	}

	@Test
	void defaultHsvRainbowIsClosedAndUsesBrightHueTransitions() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		Gradient gradient = Gradient.from(settings);
		assertEquals(0xFF0000, gradient.colorAt(0, 7, 0));
		assertEquals(0xFF0000, gradient.colorAt(6, 7, 0));
		assertEquals(0xFFFF00, gradient.colorAt(1, 7, 0));
	}

	@Test
	void hsvInterpolatesThroughBrightOrangeBetweenRedAndYellow() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		settings.animation = ExtraConfig.Animation.STATIC;
		settings.interpolation = ExtraConfig.Interpolation.HSV;
		settings.stops = java.util.List.of(
				new ExtraConfig.GradientStop(0.0f, "#FF0000"),
				new ExtraConfig.GradientStop(1.0f, "#FFFF00"));
		assertEquals(0xFF8000, Gradient.from(settings).colorAt(1, 3, 0));
	}

	@Test
	void migratesTheOldBuiltInPresetToClosedHsvRainbow() {
		ExtraConfig config = new ExtraConfig();
		config.maxEnchantments.interpolation = ExtraConfig.Interpolation.RGB;
		config.maxEnchantments.stops = java.util.List.of(
				new ExtraConfig.GradientStop(0.0f, "#FF5555"),
				new ExtraConfig.GradientStop(0.5f, "#FFFF55"),
				new ExtraConfig.GradientStop(1.0f, "#55FFFF"));
		config.normalize();
		assertEquals(ExtraConfig.Interpolation.HSV, config.maxEnchantments.interpolation);
		assertEquals("#FF0000", config.maxEnchantments.stops.get(0).color);
		assertEquals(config.maxEnchantments.stops.get(0).color,
				config.maxEnchantments.stops.get(config.maxEnchantments.stops.size() - 1).color);
	}

	@Test
	void keepsUserDefinedGradientStops() {
		ExtraConfig config = new ExtraConfig();
		config.maxEnchantments.interpolation = ExtraConfig.Interpolation.HSL;
		config.maxEnchantments.stops = java.util.List.of(
				new ExtraConfig.GradientStop(0.0f, "#123456"),
				new ExtraConfig.GradientStop(1.0f, "#ABCDEF"));
		config.normalize();
		assertEquals(ExtraConfig.Interpolation.HSL, config.maxEnchantments.interpolation);
		assertEquals("#123456", config.maxEnchantments.stops.get(0).color);
		assertEquals("#ABCDEF", config.maxEnchantments.stops.get(1).color);
	}

	@Test
	void mapsLocalGlyphXWithoutAddingTheGlyphOriginTwice() {
		assertEquals(0.375f, GradientShaderRenderer.progressAtLocalX(106.0f, 100.0f, 112.0f, 0.25f, 0.5f), 0.0001f);
	}

	@Test
	void clampsGlyphInkOverhangToItsLayoutAdvance() {
		assertEquals(0.25f, GradientShaderRenderer.progressAtLocalX(98.0f, 100.0f, 112.0f, 0.25f, 0.5f), 0.0001f);
		assertEquals(0.5f, GradientShaderRenderer.progressAtLocalX(114.0f, 100.0f, 112.0f, 0.25f, 0.5f), 0.0001f);
	}

	@Test
	void appliesSunsetPresetWithExpectedStopsAndModes() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		GradientPreset.SUNSET.apply(settings);
		assertEquals(4, settings.stops.size());
		assertEquals(ExtraConfig.Interpolation.HSL, settings.interpolation);
		assertEquals(ExtraConfig.Animation.FULL_TEXT_CYCLE, settings.animation);
		assertEquals("#35155D", settings.stops.get(0).color);
		assertEquals("#FFD166", settings.stops.get(3).color);
	}

	@Test
	void gradientShareCodeRoundTripsStopsAndModes() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		settings.animation = ExtraConfig.Animation.BOUNCE;
		settings.animationPeriodMs = 3456L;
		settings.animationSpeed = 2.25f;
		settings.sineWaveOffset = 0.18f;
		settings.interpolation = ExtraConfig.Interpolation.HSL;
		settings.stops = java.util.List.of(
				new ExtraConfig.GradientStop(0.0f, "#123456"),
				new ExtraConfig.GradientStop(0.35f, "#ABCDEF"),
				new ExtraConfig.GradientStop(1.0f, "#FEDCBA"));
		String code = GradientShareCode.encode(settings);
		ExtraConfig.MaxEnchantments decoded = GradientShareCode.decode(code);
		assertTrue(code.startsWith("EEC2:"));
		assertTrue(code.length() < 50, "compact share code should remain easy to copy/share");
		assertEquals(settings.animation, decoded.animation);
		assertEquals(settings.animationPeriodMs, decoded.animationPeriodMs);
		assertEquals(settings.animationSpeed, decoded.animationSpeed, 0.00001f);
		assertEquals(settings.sineWaveOffset, decoded.sineWaveOffset, 0.00001f);
		assertEquals(settings.interpolation, decoded.interpolation);
		assertEquals(settings.stops.size(), decoded.stops.size());
		for (int i = 0; i < settings.stops.size(); i++) {
			assertEquals(settings.stops.get(i).position, decoded.stops.get(i).position, 0.00001f);
			assertEquals(settings.stops.get(i).color, decoded.stops.get(i).color);
		}
	}

	@Test
	void gradientShareCodeRejectsMalformedInput() {
		assertThrows(IllegalArgumentException.class, () -> GradientShareCode.decode("EEC1:%%%"));
		assertThrows(IllegalArgumentException.class, () -> GradientShareCode.decode("not-a-gradient"));
	}

	@Test
	void compactGradientCodeDetectsAccidentalEdits() {
		String code = GradientShareCode.encode(new ExtraConfig.MaxEnchantments());
		char changed = code.charAt(5) == 'A' ? 'B' : 'A';
		String corrupted = code.substring(0, 5) + changed + code.substring(6);
		assertThrows(IllegalArgumentException.class, () -> GradientShareCode.decode(corrupted));
	}

	@Test
	void oldEec1JsonCodesRemainImportable() {
		String json = "{\"version\":1,\"interpolation\":\"RGB\",\"animation\":\"STATIC\","
				+ "\"animationPeriodMs\":1200,\"animationSpeed\":1.0,\"sineWaveOffset\":0.0,"
				+ "\"stops\":[{\"position\":0.0,\"color\":\"#123456\"},{\"position\":1.0,\"color\":\"#ABCDEF\"}]}";
		String code = "EEC1:" + java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		ExtraConfig.MaxEnchantments decoded = GradientShareCode.decode(code);
		assertEquals(ExtraConfig.Interpolation.RGB, decoded.interpolation);
		assertEquals("#123456", decoded.stops.get(0).color);
	}

	@Test
	void animationSpeedMultiplierChangesThePhaseRate() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		settings.animation = ExtraConfig.Animation.FULL_TEXT_CYCLE;
		settings.animationPeriodMs = 1000L;
		settings.animationSpeed = 2.0f;
		Gradient gradient = Gradient.from(settings);
		assertEquals(0.5, gradient.progressAt(0, 4, 250L), 0.000001);
	}

	@Test
	void sineWaveOffsetAddsATravelingSpatialDisplacement() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		settings.animation = ExtraConfig.Animation.STATIC;
		settings.animationPeriodMs = 1000L;
		settings.sineWaveOffset = 0.25f;
		Gradient gradient = Gradient.from(settings);
		assertEquals(0.5, gradient.progressAt(1, 5, 0L), 0.000001);
		assertEquals(0.25, gradient.progressAt(1, 5, 250L), 0.000001);
	}

	@Test
	void paletteLutSamplingDoesNotApplySineOffsetTwice() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		settings.animation = ExtraConfig.Animation.STATIC;
		settings.sineWaveOffset = 0.5f;
		settings.stops = java.util.List.of(
				new ExtraConfig.GradientStop(0.0f, "#000000"),
				new ExtraConfig.GradientStop(1.0f, "#FFFFFF"));
		Gradient gradient = Gradient.from(settings);
		assertEquals(0x808080, gradient.paletteColorAt(128, 256));
		assertTrue(gradient.colorAt(128, 256, 0L) != gradient.paletteColorAt(128, 256));
	}

	@Test
	void detectsChangesToAnimationSpeedAndWaveInGradientCache() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		Gradient gradient = Gradient.from(settings);
		assertTrue(gradient.matches(settings));
		settings.animationSpeed = 1.5f;
		assertTrue(!gradient.matches(settings));
		settings.animationSpeed = 1.0f;
		settings.sineWaveOffset = 0.1f;
		assertTrue(!gradient.matches(settings));
	}

	@Test
	void normalizesAnimationSpeedAndWaveToSupportedRanges() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		ExtraConfig config = new ExtraConfig();
		assertEquals(1.0f, config.maxEnchantments.animationSpeed);
		assertEquals(0.0f, config.maxEnchantments.sineWaveOffset);
		config.maxEnchantments.animationSpeed = 99.0f;
		config.maxEnchantments.sineWaveOffset = -1.0f;
		config.normalize();
		assertEquals(5.0f, config.maxEnchantments.animationSpeed);
		assertEquals(0.0f, config.maxEnchantments.sineWaveOffset);
		config.maxEnchantments.animationSpeed = Float.NaN;
		config.maxEnchantments.sineWaveOffset = Float.NaN;
		config.normalize();
		assertEquals(1.0f, config.maxEnchantments.animationSpeed);
		assertEquals(0.0f, config.maxEnchantments.sineWaveOffset);
	}

	@Test
	void itemProfileCanTargetRegistryIdOrHypixelIdAndRequiredEnchantments() {
		ExtraConfig config = new ExtraConfig();
		ExtraConfig.ItemProfile profile = new ExtraConfig.ItemProfile();
		profile.name = "AOTD sharpness";
		profile.itemIds = java.util.List.of("minecraft:diamond_sword", "ASPECT_OF_THE_DRAGON");
		profile.enchantmentIds = java.util.List.of("sharpness");
		config.itemProfiles = java.util.List.of(profile);
		config.normalize();

		assertEquals(profile, config.findProfile("minecraft:diamond_sword", "aspect_of_the_dragon", java.util.Set.of("SHARPNESS")));
		assertEquals(profile, config.findProfile("minecraft:diamond_sword", "OTHER_ITEM", java.util.Set.of("sharpness")));
		assertEquals(null, config.findProfile("minecraft:diamond_sword", "ASPECT_OF_THE_DRAGON", java.util.Set.of("smite")));
		assertEquals(null, config.findProfile("minecraft:bow", "OTHER_ITEM", java.util.Set.of("sharpness")));
	}

	@Test
	void activeProfileNameResolvesCaseInsensitivelyAndInvalidNameFallsBackToAuto() {
		ExtraConfig config = new ExtraConfig();
		ExtraConfig.ItemProfile profile = new ExtraConfig.ItemProfile();
		profile.name = "Sunset Blade";
		profile.enchantmentIds = java.util.List.of("sharpness");
		config.itemProfiles = java.util.List.of(profile);
		config.activeProfile = "sunset blade";
		config.normalize();
		assertEquals("Sunset Blade", config.activeProfile);
		assertEquals(1, config.findProfileIndexByName("SUNSET BLADE"));
		config.activeProfile = "missing profile";
		config.normalize();
		assertEquals("", config.activeProfile);
	}

	@Test
	void staticShaderModeFreezesAnimationAndWaveWithoutChangingPalette() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		settings.animation = ExtraConfig.Animation.FULL_TEXT_CYCLE;
		settings.animationPeriodMs = 1000L;
		settings.sineWaveOffset = 0.2f;
		Gradient animated = Gradient.from(settings, false);
		Gradient stationary = Gradient.from(settings, true);
		assertEquals(0.0, stationary.progressAt(0, 4, 250L), 0.000001);
		assertEquals(0.55, animated.progressAt(0, 4, 250L), 0.000001);
		assertTrue(stationary.matches(settings, true));
		assertTrue(!stationary.matches(settings, false));
	}

	@Test
	void progressMarkerCarriesTheSelectedProfilePaletteSlot() {
		assertEquals(GradientShaderRenderer.MARKER_NAME,
				GradientShaderRenderer.encodeProgress(0.0, 1.0).serialize());
		assertEquals(GradientShaderRenderer.MARKER_NAME + "_3",
				GradientShaderRenderer.encodeProgress(0.0, 1.0, 3).serialize());
	}

	@Test
	void smoothHsvSoftensHueRampLikeAaronsMod() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		settings.animation = ExtraConfig.Animation.STATIC;
		settings.interpolation = ExtraConfig.Interpolation.SMOOTH_HSV;
		settings.stops = java.util.List.of(
				new ExtraConfig.GradientStop(0.0f, "#FF0000"),
				new ExtraConfig.GradientStop(1.0f, "#FFFF00"));
		// Quarter of the way red->yellow: linear HSV gives green 0x40, smoothstep gives 0x28.
		assertEquals(0xFF2800, Gradient.from(settings).colorAt(1, 5, 0));
		settings.interpolation = ExtraConfig.Interpolation.HSV;
		assertEquals(0xFF4000, Gradient.from(settings).colorAt(1, 5, 0));
	}

	@Test
	void compactCodeRoundTripsScreenChromaSizeAndSmoothHsv() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		settings.chromaSize = 42.5f;
		settings.interpolation = ExtraConfig.Interpolation.SMOOTH_HSV;
		ExtraConfig.MaxEnchantments decoded = GradientShareCode.decode(GradientShareCode.encode(settings));
		assertEquals(42.5f, decoded.chromaSize, 0.001f);
		assertEquals(ExtraConfig.Interpolation.SMOOTH_HSV, decoded.interpolation);
		settings.chromaSize = 0.0f;
		assertEquals(0.0f, GradientShareCode.decode(GradientShareCode.encode(settings)).chromaSize);
	}

	@Test
	void fullCodeRoundTripsGoodColourAndProfiles() {
		ExtraConfig config = new ExtraConfig();
		config.showGoodEnchantments = false;
		config.goodEnchantmentColor = "#123456";
		config.maxEnchantments.chromaSize = 100.0f;
		ExtraConfig.ItemProfile profile = new ExtraConfig.ItemProfile();
		profile.name = "Hyperion";
		profile.itemIds = java.util.List.of("HYPERION");
		GradientPreset.SUNSET.apply(profile.gradient);
		config.itemProfiles = java.util.List.of(profile);
		config.normalize();

		String code = GradientShareCode.encodeAll(config);
		assertTrue(GradientShareCode.isFullCode(code));
		ExtraConfig decoded = GradientShareCode.decodeAll(code);
		assertEquals(false, decoded.showGoodEnchantments);
		assertEquals("#123456", decoded.goodEnchantmentColor);
		assertEquals(100.0f, decoded.maxEnchantments.chromaSize);
		assertEquals(1, decoded.itemProfiles.size());
		assertEquals("Hyperion", decoded.itemProfiles.get(0).name);
		assertEquals("#35155D", decoded.itemProfiles.get(0).gradient.stops.get(0).color);
	}

	@Test
	void fullCodeRejectsDecompressionBombs() {
		java.util.zip.Deflater deflater = new java.util.zip.Deflater();
		deflater.setInput(new byte[1_000_000]);
		deflater.finish();
		byte[] buffer = new byte[100_000];
		int length = deflater.deflate(buffer);
		deflater.end();
		String code = "EEC3:" + java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString(java.util.Arrays.copyOf(buffer, length));
		assertThrows(IllegalArgumentException.class, () -> GradientShareCode.decodeAll(code));
	}

	@Test
	void aaronChromaPreviewMatchesAaronsSmoothHsvAtDefaultSaturation() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		GradientPreset.AARON.apply(settings);
		Gradient gradient = Gradient.from(settings);
		// hue 0 at saturation 0.75, value 1: v * mix(1, (1,0,0), 0.75) = (1, 0.25, 0.25).
		assertEquals(0xFF4040, gradient.colorAt(0, 4, 0L));
		// Aaron speed 4 = 4 * 20 ticks / 360 hue per second, so a full cycle takes 4.5 s.
		assertEquals(0.0, gradient.phaseAt(4500L), 0.000001);
		assertEquals(0.5, gradient.phaseAt(2250L), 0.000001);
		assertEquals(100.0f, settings.chromaSize);
	}

	@Test
	void staticShaderModeKeepsAaronChromaButFreezesIt() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		GradientPreset.AARON.apply(settings);
		Gradient frozen = Gradient.from(settings, true);
		assertEquals(ExtraConfig.Animation.AARON_CHROMA, frozen.animation());
		assertEquals(0.0, frozen.phaseAt(1234L), 0.000001);
	}

	@Test
	void compactCodeRoundTripsAaronSaturation() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		GradientPreset.AARON.apply(settings);
		settings.chromaSaturation = 0.5f;
		ExtraConfig.MaxEnchantments decoded = GradientShareCode.decode(GradientShareCode.encode(settings));
		assertEquals(ExtraConfig.Animation.AARON_CHROMA, decoded.animation);
		assertEquals(0.5f, decoded.chromaSaturation, 0.003f);
		assertEquals(100.0f, decoded.chromaSize, 0.001f);
	}

	@Test
	void cyclingGradientWithDifferentEndsLoopsWithoutASeam() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		GradientPreset.SUNSET.apply(settings);
		Gradient gradient = Gradient.from(settings);
		// Mirrored A->B->A: both ends of the cycle are the first colour, the middle is the last colour.
		assertEquals(0x35155D, gradient.paletteColorAt(0, 257));
		assertEquals(0x35155D, gradient.paletteColorAt(256, 257));
		assertEquals(0xFFD166, gradient.paletteColorAt(128, 257));
		// Static shader mode shows the same palette as the animated one.
		assertEquals(gradient.paletteColorAt(64, 257), Gradient.from(settings, true).paletteColorAt(64, 257));
	}

	@Test
	void nonCyclingOrAlreadySeamlessGradientsAreNotMirrored() {
		ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
		GradientPreset.SUNSET.apply(settings);
		settings.animation = ExtraConfig.Animation.LEFT_TO_RIGHT;
		assertEquals(0xFFD166, Gradient.from(settings).paletteColorAt(256, 257));
		GradientPreset.RAINBOW.apply(settings);
		assertEquals(0x00FFFF, Gradient.from(settings).paletteColorAt(128, 257));
	}

	@Test
	void everyPresetHasValidStopsAndAShortLabel() {
		for (GradientPreset preset : GradientPreset.values()) {
			ExtraConfig.MaxEnchantments settings = new ExtraConfig.MaxEnchantments();
			preset.apply(settings);
			GradientShareCode.decode(GradientShareCode.encode(settings));
			assertTrue(preset.displayName().length() <= 8, preset.displayName());
		}
	}
}

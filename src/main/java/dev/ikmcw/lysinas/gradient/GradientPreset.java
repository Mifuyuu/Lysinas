package dev.ikmcw.lysinas.gradient;

import dev.ikmcw.lysinas.config.ExtraConfig;

import java.util.ArrayList;
import java.util.List;

/** Built-in palettes that can be applied from the in-game settings screen. */
public enum GradientPreset {
	/** Aaron's Mod defaults: size 100, saturation 0.75, speed 4 (4 * 20 ticks / 360 = one hue cycle per 4.5 s). */
	AARON(ExtraConfig.Interpolation.SMOOTH_HSV, ExtraConfig.Animation.AARON_CHROMA, 4500L, even("#FF0000", "#FFFF00", "#00FF00", "#00FFFF", "#0000FF", "#FF00FF", "#FF0000")),
	RAINBOW(ExtraConfig.Interpolation.HSV, ExtraConfig.Animation.FULL_TEXT_CYCLE, 1800L, even("#FF0000", "#FFFF00", "#00FF00", "#00FFFF", "#0000FF", "#FF00FF", "#FF0000")),
	SPECTRUM(ExtraConfig.Interpolation.SMOOTH_HSV, ExtraConfig.Animation.FULL_TEXT_CYCLE, 3000L, even("#FF0000", "#FFFF00", "#00FF00", "#00FFFF", "#0000FF", "#FF00FF", "#FF0000")),
	PASTEL(ExtraConfig.Interpolation.SMOOTH_HSV, ExtraConfig.Animation.FULL_TEXT_CYCLE, 3000L,
			even("#FFB3BA", "#FFDFBA", "#FFFFBA", "#BAFFC9", "#BAE1FF", "#E0BAFF", "#FFB3BA")),
	SUNSET(ExtraConfig.Interpolation.HSL, ExtraConfig.Animation.FULL_TEXT_CYCLE, 2400L, List.of(
			new ExtraConfig.GradientStop(0.0f, "#35155D"),
			new ExtraConfig.GradientStop(0.38f, "#B13E78"),
			new ExtraConfig.GradientStop(0.72f, "#FF795E"),
			new ExtraConfig.GradientStop(1.0f, "#FFD166"))),
	OCEAN(ExtraConfig.Interpolation.HSV, ExtraConfig.Animation.FULL_TEXT_CYCLE, 2200L, List.of(
			new ExtraConfig.GradientStop(0.0f, "#062B5B"),
			new ExtraConfig.GradientStop(0.35f, "#087CA7"),
			new ExtraConfig.GradientStop(0.68f, "#20C6B7"),
			new ExtraConfig.GradientStop(1.0f, "#A8F0D2"))),
	FIRE(ExtraConfig.Interpolation.HSL, ExtraConfig.Animation.FULL_TEXT_CYCLE, 1600L,
			even("#8B0000", "#FF3300", "#FF8C00", "#FFD000")),
	AURORA(ExtraConfig.Interpolation.HSV, ExtraConfig.Animation.FULL_TEXT_CYCLE, 2800L,
			even("#00FFA3", "#03E1FF", "#7A5CFF", "#DC1FFF")),
	GALAXY(ExtraConfig.Interpolation.HSL, ExtraConfig.Animation.FULL_TEXT_CYCLE, 3200L,
			even("#3A0CA3", "#7209B7", "#F72585", "#4CC9F0")),
	CANDY(ExtraConfig.Interpolation.RGB, ExtraConfig.Animation.FULL_TEXT_CYCLE, 2400L,
			even("#FF9AE1", "#C4A1FF", "#8FD3FF")),
	GOLD(ExtraConfig.Interpolation.RGB, ExtraConfig.Animation.FULL_TEXT_CYCLE, 2000L,
			even("#B8860B", "#FFAA00", "#FFF2A8")),
	ICE(ExtraConfig.Interpolation.RGB, ExtraConfig.Animation.FULL_TEXT_CYCLE, 2600L,
			even("#3A9BDC", "#8BE9FF", "#F0FDFF")),
	TOXIC(ExtraConfig.Interpolation.HSV, ExtraConfig.Animation.FULL_TEXT_CYCLE, 2000L,
			even("#00FF9C", "#39FF14", "#CCFF00")),
	SAKURA(ExtraConfig.Interpolation.RGB, ExtraConfig.Animation.FULL_TEXT_CYCLE, 2600L,
			even("#FF8FB1", "#FFC4D6", "#FFF0F5")),
	MYTHIC(ExtraConfig.Interpolation.RGB, ExtraConfig.Animation.FULL_TEXT_CYCLE, 2000L,
			even("#AA00AA", "#FF55FF", "#FFAAFF")),
	DIVINE(ExtraConfig.Interpolation.RGB, ExtraConfig.Animation.FULL_TEXT_CYCLE, 2000L,
			even("#00AAAA", "#55FFFF", "#D5FFFF"));

	private final ExtraConfig.Interpolation interpolation;
	private final ExtraConfig.Animation animation;
	private final long periodMs;
	private final List<ExtraConfig.GradientStop> stops;

	GradientPreset(ExtraConfig.Interpolation interpolation, ExtraConfig.Animation animation, long periodMs,
			List<ExtraConfig.GradientStop> stops) {
		this.interpolation = interpolation;
		this.animation = animation;
		this.periodMs = periodMs;
		this.stops = stops;
	}

	/** Button label, e.g. MYTHIC -> "Mythic". */
	public String displayName() {
		StringBuilder label = new StringBuilder();
		for (String word : name().split("_")) {
			if (!label.isEmpty()) label.append(' ');
			label.append(word.charAt(0)).append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
		}
		return label.toString();
	}

	private static List<ExtraConfig.GradientStop> even(String... colors) {
		List<ExtraConfig.GradientStop> stops = new ArrayList<>(colors.length);
		for (int i = 0; i < colors.length; i++) {
			stops.add(new ExtraConfig.GradientStop(i / (float) (colors.length - 1), colors[i]));
		}
		return List.copyOf(stops);
	}

	public void apply(ExtraConfig.MaxEnchantments target) {
		target.interpolation = interpolation;
		target.animation = animation;
		target.animationPeriodMs = periodMs;
		target.animationSpeed = 1.0f;
		target.sineWaveOffset = 0.0f;
		target.chromaSaturation = 0.75f;
		if (animation == ExtraConfig.Animation.AARON_CHROMA) target.chromaSize = 100.0f;
		List<ExtraConfig.GradientStop> copy = new ArrayList<>(stops.size());
		for (ExtraConfig.GradientStop stop : stops) copy.add(new ExtraConfig.GradientStop(stop.position, stop.color));
		target.stops = copy;
	}
}

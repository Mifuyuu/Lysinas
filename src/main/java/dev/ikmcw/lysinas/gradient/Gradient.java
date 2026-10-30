package dev.ikmcw.lysinas.gradient;

import dev.ikmcw.lysinas.config.ExtraConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Gradient {
	private final List<Stop> stops;
	private final ExtraConfig.Interpolation interpolation;
	private final ExtraConfig.Animation animation;
	private final long periodMs;
	private final double speedMultiplier;
	private final double sineWaveOffset;
	private final double chromaSaturation;
	/** Plays the palette A→B→A so a cycling gradient whose first and last colours differ has no seam. */
	private final boolean mirrorLoop;

	private Gradient(List<Stop> stops, ExtraConfig.Interpolation interpolation, ExtraConfig.Animation animation,
			long periodMs, double speedMultiplier, double sineWaveOffset, double chromaSaturation, boolean mirrorLoop) {
		this.stops = stops;
		this.interpolation = interpolation;
		this.animation = animation;
		this.periodMs = periodMs;
		this.speedMultiplier = speedMultiplier;
		this.sineWaveOffset = sineWaveOffset;
		this.chromaSaturation = chromaSaturation;
		this.mirrorLoop = mirrorLoop;
	}

	public static Gradient from(ExtraConfig.MaxEnchantments settings) {
		return from(settings, false);
	}

	public static Gradient from(ExtraConfig.MaxEnchantments settings, boolean forceStatic) {
		List<Stop> parsed = new ArrayList<>();
		if (settings != null && settings.stops != null) {
			for (ExtraConfig.GradientStop stop : settings.stops) {
				if (stop != null && isValidColor(stop.color) && Float.isFinite(stop.position)
						&& stop.position >= 0.0f && stop.position <= 1.0f) {
					parsed.add(new Stop(stop.position, parseColor(stop.color)));
				}
			}
		}
		if (parsed.size() < 2) {
			parsed = new ArrayList<>(List.of(
					new Stop(0.0f, 0xFF0000),
					new Stop(1.0f / 6.0f, 0xFFFF00),
					new Stop(2.0f / 6.0f, 0x00FF00),
					new Stop(3.0f / 6.0f, 0x00FFFF),
					new Stop(4.0f / 6.0f, 0x0000FF),
					new Stop(5.0f / 6.0f, 0xFF00FF),
					new Stop(1.0f, 0xFF0000)));
		}
		parsed.sort(Comparator.comparingDouble(stop -> stop.position));
		ExtraConfig.Interpolation interpolation = settings == null || settings.interpolation == null
				? ExtraConfig.Interpolation.RGB : settings.interpolation;
		ExtraConfig.Animation configured = settings == null || settings.animation == null
				? ExtraConfig.Animation.STATIC : settings.animation;
		ExtraConfig.Animation animation = effectiveAnimation(configured, forceStatic);
		long period = settings == null ? 1800L : Math.max(50L, settings.animationPeriodMs);
		double speed = settings == null || !Float.isFinite(settings.animationSpeed)
				? 1.0 : clamp(settings.animationSpeed, 0.1, 5.0);
		double wave = forceStatic || settings == null || !Float.isFinite(settings.sineWaveOffset)
				? 0.0 : clamp(settings.sineWaveOffset, 0.0, 0.5);
		double saturation = settings == null || !Float.isFinite(settings.chromaSaturation)
				? 0.75 : clamp(settings.chromaSaturation, 0.0, 1.0);
		// A frozen Aaron chroma keeps its colours but stops moving.
		if (forceStatic && animation == ExtraConfig.Animation.AARON_CHROMA) speed = 0.0;
		return new Gradient(List.copyOf(parsed), interpolation, animation, period, speed, wave, saturation,
				shouldMirror(configured, parsed));
	}

	/** Decided from the configured animation (not the frozen one) so STATIC shader mode shows the same palette. */
	private static boolean shouldMirror(ExtraConfig.Animation configured, List<Stop> stops) {
		return configured == ExtraConfig.Animation.FULL_TEXT_CYCLE && stops.get(0).rgb != stops.get(stops.size() - 1).rgb;
	}

	/** STATIC shader mode freezes every animation; Aaron chroma stays Aaron chroma, only its phase stops. */
	static ExtraConfig.Animation effectiveAnimation(ExtraConfig.Animation configured, boolean forceStatic) {
		return forceStatic && configured != ExtraConfig.Animation.AARON_CHROMA ? ExtraConfig.Animation.STATIC : configured;
	}

	/**
	 * Checks whether this parsed gradient still matches the mutable settings object.
	 * This is allocation-free and lets the tooltip mixin reuse the parsed stops.
	 */
	public boolean matches(ExtraConfig.MaxEnchantments settings) {
		return matches(settings, false);
	}

	public boolean matches(ExtraConfig.MaxEnchantments settings, boolean forceStatic) {
		if (settings == null || settings.animation == null || !matchesPalette(settings)) return false;
		ExtraConfig.Animation effectiveAnimation = effectiveAnimation(settings.animation, forceStatic);
		return effectiveAnimation == animation
				&& shouldMirror(settings.animation, stops) == mirrorLoop
				&& Double.compare(clamp(settings.chromaSaturation, 0.0, 1.0), chromaSaturation) == 0
				&& (forceStatic || Math.max(50L, settings.animationPeriodMs) == periodMs)
				&& (forceStatic || Double.compare(clamp(settings.animationSpeed, 0.1, 5.0), speedMultiplier) == 0)
				&& (forceStatic || Double.compare(clamp(settings.sineWaveOffset, 0.0, 0.5), sineWaveOffset) == 0);
	}

	public boolean matchesPalette(ExtraConfig.MaxEnchantments settings) {
		if (settings == null || settings.stops == null || settings.interpolation != interpolation
				|| settings.stops.size() != stops.size()) return false;
		for (int i = 0; i < stops.size(); i++) {
			ExtraConfig.GradientStop configured = settings.stops.get(i);
			Stop parsed = stops.get(i);
			if (configured == null || !Float.isFinite(configured.position)
					|| Double.compare(configured.position, parsed.position) != 0 || !isValidColor(configured.color)) return false;
			if (parseColor(configured.color) != parsed.rgb) return false;
		}
		return true;
	}

	public int colorAt(int index, int length, long clockMs) {
		if (animation == ExtraConfig.Animation.AARON_CHROMA) {
			return smoothHsvToRgb(progressAt(index, length, clockMs), chromaSaturation, 1.0);
		}
		return sample(progressAt(index, length, clockMs));
	}

	/** Samples the stationary color palette; animation is applied to glyph progress, not to the LUT. */
	int paletteColorAt(int index, int length) {
		double position = length <= 1 ? 0.0 : index / (double) (length - 1);
		return sample(position);
	}

	public double progressAt(int index, int length, long clockMs) {
		double base = length <= 1 ? 0.0 : index / (double) (length - 1);
		double phase = phaseAt(clockMs);
		double progress = switch (animation) {
			case STATIC -> clamp(base, 0.0, 1.0);
			case LEFT_TO_RIGHT -> clamp(base - phase, 0.0, 1.0);
			case RIGHT_TO_LEFT -> clamp(base + phase, 0.0, 1.0);
			case FULL_TEXT_CYCLE -> wrap(base - phase);
			case BOUNCE -> reflect(base - bounceOffset(phase));
			// Hue for the preview; on screen the shader uses Aaron's screen-space formula instead.
			case AARON_CHROMA -> wrap(phase - base);
		};
		if (sineWaveOffset > 0.0 && animation != ExtraConfig.Animation.AARON_CHROMA) {
			// One wave spans the text and travels at the same user-controlled rate.
			double wave = Math.sin((base - phase) * Math.PI * 2.0) * sineWaveOffset;
			progress += wave;
			progress = switch (animation) {
				case FULL_TEXT_CYCLE, AARON_CHROMA -> wrap(progress);
				case BOUNCE -> reflect(progress);
				case STATIC, LEFT_TO_RIGHT, RIGHT_TO_LEFT -> clamp(progress, 0.0, 1.0);
			};
		}
		return progress;
	}

	/** Animation phase in [0, 1); tooltip_gradient.fsh mirrors {@link #progressAt} using this value. */
	public double phaseAt(long clockMs) {
		return wrap((positiveModulo(clockMs, periodMs) / (double) periodMs) * speedMultiplier);
	}

	public ExtraConfig.Animation animation() {
		return animation;
	}

	public double sineWaveOffset() {
		return sineWaveOffset;
	}

	private double bounceOffset(double phase) {
		return phase < 0.5 ? phase * 2.0 : 2.0 - phase * 2.0;
	}

	private int sample(double position) {
		if (mirrorLoop) position = position <= 0.5 ? position * 2.0 : 2.0 - position * 2.0;
		if (position <= stops.get(0).position) return stops.get(0).rgb;
		Stop last = stops.get(stops.size() - 1);
		if (position >= last.position) return last.rgb;
		for (int i = 1; i < stops.size(); i++) {
			Stop right = stops.get(i);
			if (position <= right.position) {
				Stop left = stops.get(i - 1);
				double local = (position - left.position) / (right.position - left.position);
				return interpolate(left, right, local);
			}
		}
		return last.rgb;
	}

	private int interpolate(Stop left, Stop right, double amount) {
		amount = clamp(amount, 0.0, 1.0);
		if (amount <= 0.0) return left.rgb;
		if (amount >= 1.0) return right.rgb;
		if (interpolation == ExtraConfig.Interpolation.HSL) {
			double delta = right.hue - left.hue;
			if (delta > 0.5) delta -= 1.0;
			if (delta < -0.5) delta += 1.0;
			return hslToRgb(wrap(left.hue + delta * amount),
					left.saturation + (right.saturation - left.saturation) * amount,
					left.lightness + (right.lightness - left.lightness) * amount);
		}
		if (interpolation == ExtraConfig.Interpolation.HSV || interpolation == ExtraConfig.Interpolation.SMOOTH_HSV) {
			double leftHue = left.hsvSaturation == 0.0 ? right.hsvHue : left.hsvHue;
			double rightHue = right.hsvSaturation == 0.0 ? left.hsvHue : right.hsvHue;
			double delta = rightHue - leftHue;
			if (delta > 0.5) delta -= 1.0;
			if (delta < -0.5) delta += 1.0;
			if (interpolation == ExtraConfig.Interpolation.SMOOTH_HSV) {
				return smoothHsvToRgb(wrap(leftHue + delta * amount),
						left.hsvSaturation + (right.hsvSaturation - left.hsvSaturation) * amount,
						left.value + (right.value - left.value) * amount);
			}
			return hsvToRgb(wrap(leftHue + delta * amount),
					left.hsvSaturation + (right.hsvSaturation - left.hsvSaturation) * amount,
					left.value + (right.value - left.value) * amount);
		}
		int r = (int) Math.round(channel(left.rgb, 16) + (channel(right.rgb, 16) - channel(left.rgb, 16)) * amount);
		int g = (int) Math.round(channel(left.rgb, 8) + (channel(right.rgb, 8) - channel(left.rgb, 8)) * amount);
		int b = (int) Math.round(channel(left.rgb, 0) + (channel(right.rgb, 0) - channel(left.rgb, 0)) * amount);
		return (r << 16) | (g << 8) | b;
	}

	private static int channel(int color, int shift) {
		return (color >>> shift) & 0xFF;
	}

	private static int hslToRgb(double hue, double saturation, double lightness) {
		if (saturation <= 0.0) {
			int value = (int) Math.round(clamp(lightness, 0.0, 1.0) * 255.0);
			return (value << 16) | (value << 8) | value;
		}
		double q = lightness < 0.5 ? lightness * (1.0 + saturation) : lightness + saturation - lightness * saturation;
		double p = 2.0 * lightness - q;
		double r = hueToRgb(p, q, hue + 1.0 / 3.0);
		double g = hueToRgb(p, q, hue);
		double b = hueToRgb(p, q, hue - 1.0 / 3.0);
		return (clampChannel(r) << 16) | (clampChannel(g) << 8) | clampChannel(b);
	}

	private static int clampChannel(double value) {
		return (int) Math.round(clamp(value, 0.0, 1.0) * 255.0);
	}

	public static int hsvToRgb(double hue, double saturation, double value) {
		if (saturation <= 0.0) return clampChannel(value) * 0x010101;
		double sector = wrap(hue) * 6.0;
		int index = (int) Math.floor(sector);
		double fraction = sector - index;
		double p = value * (1.0 - saturation);
		double q = value * (1.0 - saturation * fraction);
		double t = value * (1.0 - saturation * (1.0 - fraction));
		double r;
		double g;
		double b;
		switch (index % 6) {
			case 0 -> { r = value; g = t; b = p; }
			case 1 -> { r = q; g = value; b = p; }
			case 2 -> { r = p; g = value; b = t; }
			case 3 -> { r = p; g = q; b = value; }
			case 4 -> { r = t; g = p; b = value; }
			default -> { r = value; g = p; b = q; }
		}
		return (clampChannel(r) << 16) | (clampChannel(g) << 8) | clampChannel(b);
	}

	/** Port of Aaron's Mod hsv2rgb_smooth: smoothstep each channel's hue ramp before applying S and V. */
	private static int smoothHsvToRgb(double hue, double saturation, double value) {
		int rgb = 0;
		for (double offset : new double[] {0.0, 4.0, 2.0}) {
			double ramp = clamp(Math.abs(positiveModulo(hue * 6.0 + offset, 6.0) - 3.0) - 1.0, 0.0, 1.0);
			ramp = ramp * ramp * (3.0 - 2.0 * ramp);
			rgb = (rgb << 8) | clampChannel(value * (1.0 - saturation + saturation * ramp));
		}
		return rgb;
	}

	private static double hueToRgb(double p, double q, double hue) {
		hue = wrap(hue);
		if (hue < 1.0 / 6.0) return p + (q - p) * 6.0 * hue;
		if (hue < 1.0 / 2.0) return q;
		if (hue < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - hue) * 6.0;
		return p;
	}

	private static double reflect(double value) {
		double wrapped = positiveModulo(value, 2.0);
		return wrapped <= 1.0 ? wrapped : 2.0 - wrapped;
	}

	private static double wrap(double value) {
		return value - Math.floor(value);
	}

	private static long positiveModulo(long value, long modulus) {
		return Math.floorMod(value, modulus);
	}

	private static double positiveModulo(double value, double modulus) {
		return value - Math.floor(value / modulus) * modulus;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public static boolean isValidColor(String value) {
		if (value == null) return false;
		int start = 0;
		int end = value.length();
		while (start < end && value.charAt(start) <= ' ') start++;
		while (end > start && value.charAt(end - 1) <= ' ') end--;
		if (start < end && value.charAt(start) == '#') {
			start++;
		} else if (end - start > 1 && value.charAt(start) == '0' && (value.charAt(start + 1) == 'x' || value.charAt(start + 1) == 'X')) {
			start += 2;
		}
		if (end - start != 6) return false;
		for (int i = start; i < end; i++) {
			if (hexValue(value.charAt(i)) < 0) return false;
		}
		return true;
	}

	public static int parseColor(String value) {
		if (!isValidColor(value)) throw new IllegalArgumentException("Invalid RGB color: " + value);
		int start = 0;
		int end = value.length();
		while (start < end && value.charAt(start) <= ' ') start++;
		while (end > start && value.charAt(end - 1) <= ' ') end--;
		if (value.charAt(start) == '#') start++;
		else if (value.charAt(start) == '0') start += 2;
		int rgb = 0;
		for (int i = start; i < end; i++) rgb = (rgb << 4) | hexValue(value.charAt(i));
		return rgb;
	}

	private static int hexValue(char value) {
		if (value >= '0' && value <= '9') return value - '0';
		if (value >= 'a' && value <= 'f') return value - 'a' + 10;
		if (value >= 'A' && value <= 'F') return value - 'A' + 10;
		return -1;
	}

	private static final class Stop {
		private final double position;
		private final int rgb;
		private final double hue;
		private final double saturation;
		private final double lightness;
		private final double hsvHue;
		private final double hsvSaturation;
		private final double value;

		private Stop(double position, int rgb) {
			this.position = position;
			this.rgb = rgb;
			double r = channel(rgb, 16) / 255.0;
			double g = channel(rgb, 8) / 255.0;
			double b = channel(rgb, 0) / 255.0;
			double max = Math.max(r, Math.max(g, b));
			double min = Math.min(r, Math.min(g, b));
			double delta = max - min;
			this.value = max;
			if (delta == 0.0) {
				this.hsvHue = 0.0;
				this.hsvSaturation = 0.0;
			} else {
				this.hsvSaturation = max == 0.0 ? 0.0 : delta / max;
				double calculatedHue;
				if (max == r) calculatedHue = (g - b) / delta + (g < b ? 6.0 : 0.0);
				else if (max == g) calculatedHue = (b - r) / delta + 2.0;
				else calculatedHue = (r - g) / delta + 4.0;
				this.hsvHue = calculatedHue / 6.0;
			}
			this.lightness = (max + min) / 2.0;
			if (max == min) {
				this.hue = 0.0;
				this.saturation = 0.0;
			} else {
				this.saturation = lightness > 0.5 ? delta / (2.0 - max - min) : delta / (max + min);
				double calculatedHue;
				if (max == r) calculatedHue = (g - b) / delta + (g < b ? 6.0 : 0.0);
				else if (max == g) calculatedHue = (b - r) / delta + 2.0;
				else calculatedHue = (r - g) / delta + 4.0;
				this.hue = calculatedHue / 6.0;
			}
		}
	}
}

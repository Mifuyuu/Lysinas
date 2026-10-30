package dev.ikmcw.lysinas.tooltip;

import dev.ikmcw.lysinas.config.ExtraConfig;
import dev.ikmcw.lysinas.gradient.GradientShaderRenderer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class TooltipColourizer {
	private TooltipColourizer() {}

	public static Component apply(Component line, Collection<String> maxEnchantments, Collection<String> goodEnchantments,
			ExtraConfig config, int goodColour, int profileSlot) {
		if ((maxEnchantments.isEmpty() && (goodEnchantments.isEmpty() || !config.showGoodEnchantments))) return line;
		String plainText = line.getString();
		boolean maybeMax = containsAny(plainText, maxEnchantments);
		boolean maybeGood = config.showGoodEnchantments && containsAny(plainText, goodEnchantments);
		if (!maybeMax && !maybeGood) return line;

		GlyphBuffer glyphs = new GlyphBuffer(plainText.codePointCount(0, plainText.length()));
		line.getVisualOrderText().accept((_, style, codePoint) -> {
			glyphs.add(codePoint, style);
			return true;
		});
		int glyphCount = glyphs.size();
		if (glyphCount == 0) return line;

		boolean[] occupied = new boolean[glyphCount];
		List<Span> maxSpans = maybeMax ? findSpans(glyphs, maxEnchantments, occupied) : List.of();
		List<Span> goodSpans = maybeGood ? findSpans(glyphs, goodEnchantments, occupied) : List.of();
		if (maxSpans.isEmpty() && goodSpans.isEmpty()) return line;

		int totalMaxCharacters = 0;
		for (Span span : maxSpans) totalMaxCharacters += span.end - span.start;
		int[] maxPositions = new int[glyphCount];
		Arrays.fill(maxPositions, -1);
		int position = 0;
		for (Span span : maxSpans) {
			for (int i = span.start; i < span.end; i++) maxPositions[i] = position++;
		}
		boolean[] goodPositions = new boolean[glyphCount];
		for (Span span : goodSpans) Arrays.fill(goodPositions, span.start, span.end, true);

		MutableComponent result = Component.empty();
		StringBuilder run = new StringBuilder();
		Style runStyle = null;
		for (int i = 0; i < glyphCount; i++) {
			Style style = glyphs.styleAt(i);
			if (maxPositions[i] >= 0) {
				// Stationary base progress; the shader animates it every frame so tooltips need no rebuild.
				double progressStart = maxPositions[i] / (double) totalMaxCharacters;
				double progressEnd = (maxPositions[i] + 1) / (double) totalMaxCharacters;
				style = style.withColor(GradientShaderRenderer.encodeProgress(progressStart, progressEnd, profileSlot));
			} else if (goodPositions[i]) {
				style = style.withColor(goodColour);
			}
			if (runStyle != null && !runStyle.equals(style)) {
				result.append(Component.literal(run.toString()).setStyle(runStyle));
				run.setLength(0);
			}
			run.appendCodePoint(glyphs.codePointAt(i));
			runStyle = style;
		}
		if (!run.isEmpty()) result.append(Component.literal(run.toString()).setStyle(runStyle));
		return result;
	}

	private static boolean containsAny(String text, Collection<String> names) {
		for (String name : names) if (name != null && !name.isEmpty() && text.contains(name)) return true;
		return false;
	}

	private static List<Span> findSpans(GlyphBuffer glyphs, Collection<String> names, boolean[] occupied) {
		List<Span> spans = new ArrayList<>();
		for (String name : names) {
			if (name == null || name.isEmpty()) continue;
			int[] target = name.codePoints().toArray();
			for (int start = 0; start <= glyphs.size() - target.length; start++) {
				boolean matches = true;
				for (int offset = 0; offset < target.length; offset++) {
					int at = start + offset;
					Style style = glyphs.styleAt(at);
					TextColor color = style.getColor();
					if (glyphs.codePointAt(at) != target[offset] || occupied[at]
							|| color == null || color.getValue() != TextColor.BLUE.getValue()) {
						matches = false;
						break;
					}
				}
				if (matches) {
					Span span = new Span(start, start + target.length);
					spans.add(span);
					Arrays.fill(occupied, span.start, span.end, true);
					start += target.length - 1;
				}
			}
		}
		spans.sort(Comparator.comparingInt(span -> span.start));
		return spans;
	}

	private static final class GlyphBuffer {
		private int[] codePoints;
		private Style[] styles;
		private int size;

		private GlyphBuffer(int capacity) {
			int initial = Math.max(1, capacity);
			this.codePoints = new int[initial];
			this.styles = new Style[initial];
		}

		private void add(int codePoint, Style style) {
			if (size == codePoints.length) {
				int newCapacity = size * 2;
				codePoints = Arrays.copyOf(codePoints, newCapacity);
				styles = Arrays.copyOf(styles, newCapacity);
			}
			codePoints[size] = codePoint;
			styles[size] = style;
			size++;
		}

		private int size() {
			return size;
		}

		private int codePointAt(int index) {
			return codePoints[index];
		}

		private Style styleAt(int index) {
			return styles[index];
		}
	}

	private record Span(int start, int end) {}
}

package net.lysinas;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Bedrock-style slider: dark inset track with a green fill and a bevelled grey knob, snapping to a fixed step. */
final class BedrockSlider extends AbstractSliderButton {
	private final String label;
	private final double min;
	private final double max;
	private final double step;
	private final String format;
	private Runnable onChange = () -> {};

	BedrockSlider(String label, double min, double max, double step, String format) {
		super(0, 0, 100, 20, Component.empty(), 0.0);
		this.label = label;
		this.min = min;
		this.max = max;
		this.step = step;
		this.format = format;
		updateMessage();
	}

	BedrockSlider onChange(Runnable action) {
		onChange = action;
		return this;
	}

	double get() {
		return Math.round((min + value * (max - min)) / step) * step;
	}

	/** Moves the knob without firing onChange, for loading values into the screen. */
	void set(double actual) {
		value = Math.max(0.0, Math.min(1.0, (actual - min) / (max - min)));
		updateMessage();
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": " + valueText()));
	}

	@Override
	protected void applyValue() {
		onChange.run();
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		int trackTop = y + h / 2 - 3;
		graphics.fill(x, trackTop, x + w, trackTop + 6, isFocused() ? 0xFFFFFFFF : 0xFF000000);
		graphics.fill(x + 1, trackTop + 1, x + w - 1, trackTop + 5, 0xFF1E1E1F);
		// Knob is HANDLE_WIDTH wide so the drag maths in AbstractSliderButton stays exact.
		int knobX = x + (int) (value * (w - HANDLE_WIDTH));
		graphics.fill(x + 1, trackTop + 1, knobX + HANDLE_WIDTH / 2, trackTop + 5, 0xFF3C8527);
		int face = isHoveredOrFocused() ? 0xFFB1B2B5 : 0xFFD0D1D4;
		BedrockButton.bevel(graphics, knobX, y, HANDLE_WIDTH, h, face, 0xFFFFFFFF, 0xFF58585A);
	}

	/** Current value as text, shown in the row label. */
	String valueText() {
		return String.format(Locale.ROOT, format, get());
	}
}

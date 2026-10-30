package net.lysinas;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Bedrock-style text field: dark inset box with a black outline that turns white when focused. */
final class BedrockEditBox extends EditBox {
	private boolean colorSwatch;
	private Runnable onSwatchClick;

	BedrockEditBox(Font font, Component narration) {
		super(font, 0, 0, 100, 20, narration);
	}

	/** Shows a swatch of the typed #RRGGBB colour at the right edge of the box. */
	BedrockEditBox withColorSwatch() {
		colorSwatch = true;
		return this;
	}

	/** Clicking the swatch runs this (opens the colour picker) instead of placing the text cursor. */
	BedrockEditBox onSwatchClick(Runnable action) {
		onSwatchClick = action;
		return this;
	}

	private int swatchX() {
		return getX() + getWidth() - 3 - swatchSize();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (onSwatchClick != null && isVisible() && event.x() >= swatchX() && event.x() < swatchX() + swatchSize()
				&& event.y() >= getY() + 3 && event.y() < getY() + 3 + swatchSize()) {
			onSwatchClick.run();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private int swatchSize() {
		return colorSwatch ? getHeight() - 6 : 0;
	}

	// The private bordered field stays true so vanilla keeps its 4px padding and vertical centring;
	// only the vanilla sprite (drawn when isBordered() is true) is replaced by our own frame.
	@Override
	public boolean isBordered() {
		return false;
	}

	@Override
	public int getInnerWidth() {
		// Keeps typed text from running under the swatch.
		return getWidth() - 8 - (colorSwatch ? swatchSize() + 3 : 0);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (!isVisible()) return;
		int x = getX();
		int y = getY();
		// Unfocused border is darker than the inner top shadow (0xFF141415).
		graphics.fill(x, y, x + getWidth(), y + getHeight(), isFocused() ? 0xFFFFFFFF : 0xFF0B0B0C);
		graphics.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1, 0xFF1E1E1F);
		// 2px inner shadow along the top so the box reads as sunken.
		graphics.fill(x + 1, y + 1, x + getWidth() - 1, y + 3, 0xFF141415);
		super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
		if (colorSwatch) {
			int size = swatchSize();
			int swatchX = swatchX();
			// Grey placeholder while the typed value is not yet a valid colour, so the swatch stays clickable.
			int fill = Gradient.isValidColor(getValue()) ? Gradient.parseColor(getValue()) : 0x5A5B5E;
			graphics.fill(swatchX, y + 3, swatchX + size, y + 3 + size, 0xFF000000);
			graphics.fill(swatchX + 1, y + 4, swatchX + size - 1, y + 2 + size, 0xFF000000 | fill);
		}
	}
}

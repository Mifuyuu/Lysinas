package dev.ikmcw.lysinas.gui;

import dev.ikmcw.lysinas.gradient.Gradient;
import dev.ikmcw.lysinas.gui.widget.BedrockButton;
import dev.ikmcw.lysinas.gui.widget.BedrockEditBox;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

/** Bedrock-styled HSV colour picker: saturation/brightness square, hue bar, hex field and Minecraft colour presets. */
final class ColorPickerScreen extends Screen {
	// Wide enough for the 8x16px palette (128px) right of the 120px square and hue bar, with 10px margins.
	private static final int PANEL_WIDTH = 300;
	private static final int PANEL_HEIGHT = 196;
	private static final int SQUARE = 120;
	private static final int HUE_WIDTH = 14;
	private static final int[] MINECRAFT_COLORS = {
			0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	private enum Drag { NONE, SQUARE, HUE }

	private final Screen parent;
	private final int originalRgb;
	private final Consumer<String> onDone;
	private double hue;
	private double saturation;
	private double value;
	private Drag drag = Drag.NONE;
	private boolean syncingHex;
	private BedrockEditBox hexField;
	private int panelX;
	private int panelY;

	ColorPickerScreen(Screen parent, String initialHex, Consumer<String> onDone) {
		super(Component.literal("Pick colour"));
		this.parent = parent;
		this.originalRgb = Gradient.isValidColor(initialHex) ? Gradient.parseColor(initialHex) : 0xFFFFFF;
		this.onDone = onDone;
		setFromRgb(originalRgb);
	}

	@Override
	protected void init() {
		panelX = (width - PANEL_WIDTH) / 2;
		panelY = (height - PANEL_HEIGHT) / 2;
		int columnX = rightColumnX();
		hexField = new BedrockEditBox(font, Component.literal("Hex colour")).withColorSwatch();
		hexField.setMaxLength(9);
		hexField.setRectangle(PANEL_WIDTH - (columnX - panelX) - 10, 20, columnX, panelY + 70);
		hexField.setValue(hex(currentRgb()));
		hexField.setResponder(text -> {
			if (!syncingHex && Gradient.isValidColor(text)) setFromRgb(Gradient.parseColor(text));
		});
		addRenderableWidget(hexField);

		int footerY = panelY + PANEL_HEIGHT - ConfigScreen.FOOTER + 3;
		addRenderableWidget(new BedrockButton(Component.literal("Cancel"), button -> onClose()))
				.setRectangle(70, 20, panelX + PANEL_WIDTH - 152, footerY);
		addRenderableWidget(new BedrockButton(Component.literal("Done"), BedrockButton.Kind.PRIMARY, button -> {
			onDone.accept(hex(currentRgb()));
			minecraft.setScreenAndShow(parent);
		})).setRectangle(70, 20, panelX + PANEL_WIDTH - 76, footerY);
	}

	private int squareX() {
		return panelX + 10;
	}

	private int squareY() {
		return panelY + 34;
	}

	private int hueX() {
		return squareX() + SQUARE + 6;
	}

	private int rightColumnX() {
		return hueX() + HUE_WIDTH + 10;
	}

	private int paletteY() {
		return panelY + 100;
	}

	private int currentRgb() {
		return Gradient.hsvToRgb(hue, saturation, value);
	}

	private void setFromRgb(int rgb) {
		double r = ((rgb >>> 16) & 0xFF) / 255.0;
		double g = ((rgb >>> 8) & 0xFF) / 255.0;
		double b = (rgb & 0xFF) / 255.0;
		double max = Math.max(r, Math.max(g, b));
		double delta = max - Math.min(r, Math.min(g, b));
		value = max;
		saturation = max == 0.0 ? 0.0 : delta / max;
		// Grey has no hue; keep the current one so the square doesn't jump to red.
		if (delta > 0.0) {
			double h = max == r ? (g - b) / delta + (g < b ? 6.0 : 0.0) : max == g ? (b - r) / delta + 2.0 : (r - g) / delta + 4.0;
			hue = h / 6.0;
		}
	}

	private void syncHexField() {
		syncingHex = true;
		hexField.setValue(hex(currentRgb()));
		syncingHex = false;
	}

	private static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();
		if (inside(x, y, squareX(), squareY(), SQUARE, SQUARE)) {
			drag = Drag.SQUARE;
		} else if (inside(x, y, hueX(), squareY(), HUE_WIDTH, SQUARE)) {
			drag = Drag.HUE;
		} else {
			for (int i = 0; i < MINECRAFT_COLORS.length; i++) {
				if (inside(x, y, paletteCellX(i), paletteCellY(i), 14, 14)) {
					setFromRgb(MINECRAFT_COLORS[i]);
					syncHexField();
					return true;
				}
			}
			return super.mouseClicked(event, doubleClick);
		}
		updateDrag(x, y);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (drag == Drag.NONE) return super.mouseDragged(event, dragX, dragY);
		updateDrag(event.x(), event.y());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		drag = Drag.NONE;
		return super.mouseReleased(event);
	}

	private void updateDrag(double x, double y) {
		double vertical = clamp01((y - squareY()) / (double) SQUARE);
		if (drag == Drag.SQUARE) {
			saturation = clamp01((x - squareX()) / (double) SQUARE);
			value = 1.0 - vertical;
		} else {
			hue = Math.min(vertical, 0.9999);
		}
		syncHexField();
	}

	private static double clamp01(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	private static boolean inside(double x, double y, int left, int top, int w, int h) {
		return x >= left && x < left + w && y >= top && y < top + h;
	}

	private int paletteCellX(int index) {
		return rightColumnX() + (index % 8) * 16;
	}

	private int paletteCellY(int index) {
		return paletteY() + 12 + (index / 8) * 16;
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xB0000000);
		int right = panelX + PANEL_WIDTH;
		int bottom = panelY + PANEL_HEIGHT;
		graphics.fill(panelX - 1, panelY - 1, right + 1, bottom + 1, 0xFF000000);
		graphics.fill(panelX, panelY, right, bottom, 0xFF313233);
		ConfigScreen.headerBar(graphics, panelX, panelY, right, panelY + ConfigScreen.HEADER);
		ConfigScreen.footerBar(graphics, panelX, bottom - ConfigScreen.FOOTER, right, bottom);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.text(font, title.getString(), panelX + 10, panelY + 8, 0xFFFFFFFF, false);

		// Saturation/brightness square: each column fades linearly to black, which is exact for HSV value.
		int sx = squareX();
		int sy = squareY();
		graphics.fill(sx - 1, sy - 1, sx + SQUARE + 1, sy + SQUARE + 1, 0xFF000000);
		for (int column = 0; column < SQUARE; column++) {
			int top = 0xFF000000 | Gradient.hsvToRgb(hue, column / (double) (SQUARE - 1), 1.0);
			graphics.fillGradient(sx + column, sy, sx + column + 1, sy + SQUARE, top, 0xFF000000);
		}
		int markerX = sx + (int) Math.round(saturation * (SQUARE - 1));
		int markerY = sy + (int) Math.round((1.0 - value) * (SQUARE - 1));
		graphics.outline(markerX - 3, markerY - 3, 7, 7, 0xFF000000);
		graphics.outline(markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);

		// Hue bar: six linear segments between the primaries are exact in RGB.
		int hx = hueX();
		graphics.fill(hx - 1, sy - 1, hx + HUE_WIDTH + 1, sy + SQUARE + 1, 0xFF000000);
		int[] primaries = {0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF, 0xFF0000};
		for (int i = 0; i < 6; i++) {
			int top = sy + i * SQUARE / 6;
			int bottom = sy + (i + 1) * SQUARE / 6;
			graphics.fillGradient(hx, top, hx + HUE_WIDTH, bottom, 0xFF000000 | primaries[i], 0xFF000000 | primaries[i + 1]);
		}
		int hueY = sy + (int) Math.round(hue * (SQUARE - 1));
		graphics.fill(hx - 2, hueY - 2, hx + HUE_WIDTH + 2, hueY + 2, 0xFF000000);
		graphics.fill(hx - 1, hueY - 1, hx + HUE_WIDTH + 1, hueY + 1, 0xFFFFFFFF);

		// New colour (top half) against the original (bottom half), like image editors do.
		int cx = rightColumnX();
		int previewWidth = panelX + PANEL_WIDTH - 10 - cx;
		graphics.fill(cx - 1, sy - 1, cx + previewWidth + 1, sy + 31, 0xFF000000);
		graphics.fill(cx, sy, cx + previewWidth, sy + 15, 0xFF000000 | currentRgb());
		graphics.fill(cx, sy + 15, cx + previewWidth, sy + 30, 0xFF000000 | originalRgb);
		graphics.text(font, "New", cx + 3, sy + 4, contrastText(currentRgb()), false);
		graphics.text(font, "Old", cx + 3, sy + 19, contrastText(originalRgb), false);

		graphics.text(font, "Minecraft colours", cx, paletteY(), 0xFFB1B2B5, false);
		for (int i = 0; i < MINECRAFT_COLORS.length; i++) {
			int x = paletteCellX(i);
			int y = paletteCellY(i);
			boolean hovered = inside(mouseX, mouseY, x, y, 14, 14);
			graphics.fill(x, y, x + 14, y + 14, hovered ? 0xFFFFFFFF : 0xFF000000);
			graphics.fill(x + 1, y + 1, x + 13, y + 13, 0xFF000000 | MINECRAFT_COLORS[i]);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private static int contrastText(int rgb) {
		int luminance = (((rgb >>> 16) & 0xFF) * 299 + ((rgb >>> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
		return luminance > 140 ? 0xFF1E1E1F : 0xFFFFFFFF;
	}
}

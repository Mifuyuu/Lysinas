package dev.ikmcw.lysinas.gui;

import dev.ikmcw.lysinas.Main;
import dev.ikmcw.lysinas.config.ConfigManager;
import dev.ikmcw.lysinas.config.ExtraConfig;
import dev.ikmcw.lysinas.gradient.Gradient;
import dev.ikmcw.lysinas.gradient.GradientPreset;
import dev.ikmcw.lysinas.gradient.GradientShareCode;
import dev.ikmcw.lysinas.gui.widget.BedrockButton;
import dev.ikmcw.lysinas.gui.widget.BedrockEditBox;
import dev.ikmcw.lysinas.gui.widget.BedrockSlider;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * In-game editor for the persisted Lysinas configuration, styled after Minecraft Bedrock's Ore UI settings:
 * grey header bar with a search box, category sidebar, and a scrolling list of setting rows.
 */
public final class ConfigScreen extends Screen {
	// 27px bars: 24px lit face plus a 2px lip and a 1px black separator, so 20px controls sit 2px from each edge.
	static final int HEADER = 27;
	static final int FOOTER = 27;
	private static final int SIDEBAR = 100;
	private static final int ROW = 28;
	private static final int SECTION = 16;
	private static final int CONTENT_COLOR = 0xFF313233;
	private static final int SIDEBAR_COLOR = 0xFF262728;
	private static final int BAR_COLOR = 0xFF48494A;
	private static final int BAR_LIGHT = 0xFF5A5B5C;
	private static final int BAR_LIP = 0xFF1E1E1F;
	private static final int DIVIDER_DARK = 0xFF1E1E1F;
	private static final int DIVIDER_LIGHT = 0xFF45464A;
	private static final int ROW_HOVER = 0xFF3A3B3D;
	private static final int GREEN = 0xFF3C8527;
	private static final int LABEL = 0xFFFFFFFF;
	private static final int MUTED = 0xFFB1B2B5;
	private static final int TOGGLE_WIDTH = 36;
	private static boolean open;

	private enum Category {
		GENERAL("General", Items.COMPARATOR),
		GRADIENT("Gradient", Items.AMETHYST_SHARD, "icons/painter.png"),
		ANIMATION("Animation", Items.CLOCK),
		SHARE("Share", Items.PAPER, "icons/world.png"),
		TOOLS("Tools", Items.NAME_TAG, "icons/setting.png"),
		DEVELOPER("Developer", Items.COMMAND_BLOCK, "icons/github.png");

		private final String title;
		private final Item icon;
		/** Optional mod texture (160x160) that replaces the item icon. */
		private final Identifier texture;

		Category(String title, Item icon) {
			this(title, icon, null);
		}

		Category(String title, Item icon, String texture) {
			this.title = title;
			this.icon = icon;
			this.texture = texture == null ? null : Identifier.fromNamespaceAndPath(Main.MOD_ID, texture);
		}
	}

	/** One setting row: a title over a smaller, dimmer description; widgets share the control area to the right. */
	private record Row(Category category, Supplier<String> label, String description, String keywords, boolean preview,
			List<AbstractWidget> widgets) {}

	/** A laid-out row (or a section header when {@code row} is null) at a content-relative y. */
	private record Entry(Row row, Category category, int y) {}

	private final Screen parent;
	private final ExtraConfig draft;
	private final ExtraConfig.ShaderAnimationMode originalShaderMode;
	private final List<Row> rows = new ArrayList<>();
	private final List<Entry> entries = new ArrayList<>();
	private Category category = Category.GENERAL;
	private String query = "";
	private int scroll;
	private int contentHeight;
	private boolean draggingScrollbar;
	private BedrockButton presetButton;
	private boolean presetMenuOpen;
	private int presetScroll;
	/** Name shown on the dropdown; this session only, since the config does not record which preset was applied. */
	private String presetLabel = "Choose preset";
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int selectedStop;
	private String status = "";
	private Gradient previewGradient;
	private boolean loadingStopFields;

	private EditBox searchField;
	private EditBox positionField;
	private EditBox stopColorField;
	private EditBox periodField;
	private BedrockSlider speedField;
	private EditBox sineWaveField;
	private EditBox chromaSizeField;
	private EditBox saturationField;
	private EditBox goodColorField;
	private EditBox shareCodeField;
	private BedrockButton previousStopButton;
	private BedrockButton nextStopButton;
	private BedrockButton interpolationButton;
	private BedrockButton animationButton;

	public ConfigScreen(Screen parent) {
		super(Component.literal("Lysinas"));
		this.parent = parent;
		this.draft = ConfigManager.copyCurrent();
		this.draft.normalize();
		this.originalShaderMode = draft.shaderAnimationMode;
		open = true;
	}

	public static boolean isOpen() {
		return open;
	}

	public static void resetOpenState() {
		open = false;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(500, width - 16);
		panelHeight = Math.min(320, height - 16);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		rows.clear();

		searchField = new BedrockEditBox(font, Component.literal("Search"));
		searchField.setRectangle(130, 20, panelX + panelWidth - 136, panelY + 2);
		searchField.setHint(Component.literal("Search...").withStyle(ChatFormatting.GRAY));
		searchField.setValue(query);
		searchField.setResponder(value -> {
			query = value.trim().toLowerCase(Locale.ROOT);
			scroll = 0;
			layoutRows();
		});
		addRenderableWidget(searchField);

		Category[] categories = Category.values();
		for (int i = 0; i < categories.length; i++) {
			Category tab = categories[i];
			BedrockButton button = new BedrockButton(Component.literal(tab.title), BedrockButton.Kind.TAB,
					new ItemStack(tab.icon), pressed -> {
						category = tab;
						scroll = 0;
						searchField.setValue("");
						layoutRows();
					}).state(() -> query.isEmpty() && category == tab);
			if (tab.texture != null) button.iconTexture(tab.texture, 160);
			button.setRectangle(SIDEBAR - 10, 24, panelX + 5, bodyTop() + 4 + i * 27);
			addRenderableWidget(button);
		}

		buildGeneralRows();
		buildGradientRows();
		buildAnimationRows();
		buildShareRows();
		buildToolRows();
		buildDeveloperRows();

		int footerY = panelY + panelHeight - FOOTER + 3;
		addRenderableWidget(new BedrockButton(Component.literal("Cancel"), button -> closeWithoutSaving()))
				.setRectangle(70, 20, panelX + panelWidth - 152, footerY);
		addRenderableWidget(new BedrockButton(Component.literal("Save"), BedrockButton.Kind.PRIMARY, button -> saveAndClose()))
				.setRectangle(70, 20, panelX + panelWidth - 76, footerY);

		selectedStop = Math.max(0, Math.min(selectedStop, draft.maxEnchantments.stops.size() - 1));
		loadSelectedStopFields();
		positionField.setResponder(value -> updateLiveSelectedStop());
		stopColorField.setResponder(value -> updateLiveSelectedStop());
		periodField.setResponder(value -> updateLiveAnimationFields());
		sineWaveField.setResponder(value -> updateLiveAnimationFields());
		goodColorField.setResponder(value -> {
			if (Gradient.isValidColor(value)) draft.goodEnchantmentColor = canonicalHex(value);
		});
		chromaSizeField.setResponder(value -> {
			Float size = parseFloat(value);
			if (size != null && (size == 0.0f || size >= 1.0f && size <= 200.0f)) draft.maxEnchantments.chromaSize = size;
		});
		saturationField.setResponder(value -> {
			Float saturation = parseFloat(value);
			if (saturation != null && saturation >= 0.0f && saturation <= 1.0f) draft.maxEnchantments.chromaSaturation = saturation;
		});
		layoutRows();
		setInitialFocus(searchField);
	}

	private void buildGeneralRows() {
		row(Category.GENERAL, "Enabled", "Colour max enchantments", "toggle on off mod",
				toggle("Enabled", () -> draft.enabled, () -> draft.enabled = !draft.enabled));
		row(Category.GENERAL, "Hypixel only", "Only run on Hypixel", "server skyblock",
				toggle("Hypixel only", () -> draft.onlyOnHypixel, () -> draft.onlyOnHypixel = !draft.onlyOnHypixel));
		row(Category.GENERAL, "Animate shader", "Off keeps colours still", "static animated freeze shader",
				toggle("Animate shader", () -> draft.shaderAnimationMode == ExtraConfig.ShaderAnimationMode.ANIMATED,
						this::toggleShaderMode));
		row(Category.GENERAL, "Good enchants", "Colour good enchants too", "good enchantment colour color", toggle("Good enchants",
				() -> draft.showGoodEnchantments, () -> draft.showGoodEnchantments = !draft.showGoodEnchantments));
		goodColorField = colorField("Good enchantment color",
				hex -> draft.goodEnchantmentColor = hex, () -> draft.goodEnchantmentColor);
		goodColorField.setValue(canonicalHex(draft.goodEnchantmentColor));
		row(Category.GENERAL, "Good enchant colour", "Click the swatch to pick", "good enchantment color hex", goodColorField);
	}

	private BedrockButton toggle(String narration, java.util.function.BooleanSupplier state, Runnable flip) {
		return new BedrockButton(Component.literal(narration), BedrockButton.Kind.TOGGLE, button -> flip.run()).state(state);
	}

	private void buildGradientRows() {
		previewRow(Category.GRADIENT);
		previousStopButton = new BedrockButton(Component.literal("<"), button -> changeSelectedStop(-1));
		nextStopButton = new BedrockButton(Component.literal(">"), button -> changeSelectedStop(1));
		row(Category.GRADIENT, () -> "Stop " + (selectedStop + 1) + " / " + draft.maxEnchantments.stops.size(),
				"Select a gradient stop", "stop select next previous", previousStopButton, nextStopButton);
		positionField = field("Position", 12);
		row(Category.GRADIENT, "Stop position", "Where the stop sits, 0-1", "stop position", positionField);
		stopColorField = colorField("Stop color", hex -> {
			draft.maxEnchantments.stops.get(selectedStop).color = hex;
			previewGradient = null;
		}, () -> draft.maxEnchantments.stops.get(selectedStop).color);
		row(Category.GRADIENT, "Stop colour", "Click the swatch to pick", "stop color hex chroma", stopColorField);
		row(Category.GRADIENT, "Stops", "Add or remove a stop", "add remove stop",
				new BedrockButton(Component.literal("Add"), button -> addStop()),
				new BedrockButton(Component.literal("Remove"), button -> removeStop()));
		interpolationButton = new BedrockButton(interpolationLabel(), button -> cycleInterpolation());
		row(Category.GRADIENT, "Interpolation", "How the colours blend", "rgb hsl hsv smooth", interpolationButton);
		StringBuilder keywords = new StringBuilder("preset chroma dropdown");
		for (GradientPreset preset : GradientPreset.values()) keywords.append(' ').append(preset.displayName());
		presetButton = new BedrockButton(Component.literal(presetLabel), BedrockButton.Kind.DROPDOWN, button -> {
			presetMenuOpen = !presetMenuOpen;
			presetScroll = 0;
		}).state(() -> presetMenuOpen);
		row(Category.GRADIENT, "Presets", "Ready-made gradients", keywords.toString(), presetButton);
	}

	private void buildAnimationRows() {
		previewRow(Category.ANIMATION);
		animationButton = new BedrockButton(animationLabel(), button -> cycleAnimation());
		row(Category.ANIMATION, "Animation", "How the colours move", "mode cycle bounce left right static", animationButton);
		periodField = field("Animation period", 10);
		periodField.setValue(Long.toString(draft.maxEnchantments.animationPeriodMs));
		row(Category.ANIMATION, "Period (ms)", "Length of one cycle", "animation period duration", periodField);
		speedField = new BedrockSlider("Animation speed", 0.1, 5.0, 0.05, "%.2f").onChange(this::updateLiveAnimationFields);
		speedField.set(draft.maxEnchantments.animationSpeed);
		row(Category.ANIMATION, () -> "Speed " + speedField.valueText() + "x", "Cycle speed, 0.1-5x", "animation speed slider", speedField);
		sineWaveField = field("Sine wave offset", 8);
		sineWaveField.setValue(String.format(Locale.ROOT, "%.2f", draft.maxEnchantments.sineWaveOffset));
		row(Category.ANIMATION, "Sine wave", "Wavy offset, 0-0.5", "wave offset", sineWaveField);
		chromaSizeField = field("Screen chroma size", 8);
		chromaSizeField.setValue(String.format(Locale.ROOT, "%.0f", draft.maxEnchantments.chromaSize));
		row(Category.ANIMATION, "Screen chroma", "Diagonal size 1-200, 0 = off", "chroma size screen aaron diagonal 1-200", chromaSizeField);
		saturationField = field("Aaron chroma saturation", 8);
		saturationField.setValue(String.format(Locale.ROOT, "%.2f", draft.maxEnchantments.chromaSaturation));
		row(Category.ANIMATION, "Aaron saturation", "Colour strength, 0-1", "aaron chroma saturation", saturationField);
	}

	private void buildShareRows() {
		shareCodeField = field("Share code", 32_768);
		shareCodeField.setHint(Component.literal("EEC3: / EEC2: / EEC1:").withStyle(ChatFormatting.GRAY));
		row(Category.SHARE, "Code", "Paste or copy a share code", "share code import export paste", shareCodeField);
		row(Category.SHARE, "Export", "Make a share code", "export share code gradient everything all profiles",
				new BedrockButton(Component.literal("Gradient"), button -> exportCode(false)),
				new BedrockButton(Component.literal("Everything"), button -> exportCode(true)));
		row(Category.SHARE, "Clipboard", "Copy or import the code", "copy import paste",
				new BedrockButton(Component.literal("Copy"), button -> copyCode()),
				new BedrockButton(Component.literal("Import"), BedrockButton.Kind.PRIMARY, button -> importCode()));
	}

	private void buildToolRows() {
		row(Category.TOOLS, "Item profiles", "Gradients per item", "profile item enchantment selector",
				new BedrockButton(Component.literal("Open"), button -> openProfiles()));
		row(Category.TOOLS, "Tooltip benchmark", "Measure render speed", "benchmark performance fps csv",
				new BedrockButton(Component.literal("Open"), button -> openBenchmark()));
	}

	/** Read from fabric.mod.json so the page always matches the jar that is actually loaded. */
	private void buildDeveloperRows() {
		ModMetadata metadata = FabricLoader.getInstance().getModContainer(Main.MOD_ID).orElseThrow().getMetadata();
		String version = metadata.getVersion().getFriendlyString();
		int plus = version.indexOf('+');
		String source = metadata.getContact().get("sources").orElse("");
		if (!source.isEmpty()) {
			row(Category.DEVELOPER, "GitHub", "Source code and issues", "github source code repository link",
					new BedrockButton(Component.literal("Open"), button -> ConfirmLinkScreen.confirmLinkNow(this, source)));
		}
		row(Category.DEVELOPER, "Mod version", plus < 0 ? version : version.substring(0, plus), "version");
		row(Category.DEVELOPER, "Build", plus < 0 ? version : "For Minecraft " + version.substring(plus + 1), "build minecraft");
		row(Category.DEVELOPER, "Author", String.join(", ", metadata.getAuthors().stream().map(Person::getName).toList()),
				"author developer credits");
	}

	private void row(Category rowCategory, String label, String description, String keywords, AbstractWidget... widgets) {
		row(rowCategory, () -> label, description, label + " " + keywords, widgets);
	}

	private void row(Category rowCategory, Supplier<String> label, String description, String keywords, AbstractWidget... widgets) {
		for (AbstractWidget widget : widgets) addRenderableWidget(widget);
		rows.add(new Row(rowCategory, label, description, (description + " " + keywords).toLowerCase(Locale.ROOT), false,
				List.of(widgets)));
	}

	private void previewRow(Category rowCategory) {
		rows.add(new Row(rowCategory, () -> "Live preview", "Updates as you edit", "live preview gradient", true, List.of()));
	}

	private EditBox field(String narration, int maxLength) {
		EditBox box = new BedrockEditBox(font, Component.literal(narration));
		box.setMaxLength(maxLength);
		return box;
	}

	/** Hex field whose swatch opens the colour picker; the picked colour is written straight into the draft. */
	private EditBox colorField(String narration, java.util.function.Consumer<String> apply, Supplier<String> current) {
		BedrockEditBox box = new BedrockEditBox(font, Component.literal(narration)).withColorSwatch();
		box.onSwatchClick(() -> minecraft.setScreenAndShow(new ColorPickerScreen(this, current.get(), hex -> {
			apply.accept(hex);
			status = "Colour picked. Press Save to keep it.";
		})));
		box.setMaxLength(9);
		return box;
	}

	/** Recomputes which rows match the search/category and positions or hides their widgets. */
	private void layoutRows() {
		presetMenuOpen = false;
		entries.clear();
		int y = 0;
		for (Category section : Category.values()) {
			if (query.isEmpty() && section != category) continue;
			boolean headerAdded = false;
			for (Row row : rows) {
				if (row.category() != section || !matches(row)) continue;
				if (!query.isEmpty() && !headerAdded) {
					entries.add(new Entry(null, section, y));
					y += SECTION;
					headerAdded = true;
				}
				entries.add(new Entry(row, section, y));
				y += ROW;
			}
		}
		contentHeight = y;
		scroll = Math.max(0, Math.min(scroll, contentHeight - bodyHeight()));

		for (Row row : rows) {
			for (AbstractWidget widget : row.widgets()) {
				widget.visible = false;
				if (getFocused() == widget) setFocused(null);
			}
		}
		int controlX = controlX();
		int controlWidth = controlRight() - controlX;
		for (Entry entry : entries) {
			if (entry.row() == null || entry.row().widgets().isEmpty()) continue;
			int rowY = bodyTop() + entry.y() - scroll;
			// shortcut: rows partly outside the viewport are hidden rather than clipped; fine at 26px rows.
			if (rowY < bodyTop() || rowY + ROW > bodyTop() + bodyHeight()) continue;
			List<AbstractWidget> widgets = entry.row().widgets();
			if (widgets.size() == 1 && widgets.get(0) instanceof BedrockButton button && button.isToggle()) {
				// Bedrock switches keep their own size and sit at the right edge.
				button.setRectangle(TOGGLE_WIDTH, 18, controlRight() - TOGGLE_WIDTH, rowY + 4);
				button.visible = true;
				continue;
			}
			int gap = 4;
			int each = (controlWidth - gap * (widgets.size() - 1)) / widgets.size();
			for (int i = 0; i < widgets.size(); i++) {
				AbstractWidget widget = widgets.get(i);
				widget.setRectangle(each, 20, controlX + i * (each + gap), rowY + 3);
				widget.visible = true;
			}
		}
	}

	private boolean matches(Row row) {
		if (query.isEmpty()) return true;
		String haystack = row.label().get().toLowerCase(Locale.ROOT) + " " + row.keywords() + " "
				+ row.category().title.toLowerCase(Locale.ROOT);
		for (String token : query.split("\\s+")) {
			if (!haystack.contains(token)) return false;
		}
		return true;
	}

	private int bodyTop() {
		return panelY + HEADER;
	}

	private int bodyHeight() {
		return panelHeight - HEADER - FOOTER;
	}

	private int contentX() {
		return panelX + SIDEBAR;
	}

	private int controlX() {
		return contentX() + 10 + Math.max(110, (panelWidth - SIDEBAR) * 2 / 5);
	}

	private int controlRight() {
		return panelX + panelWidth - 14;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (presetMenuOpen) {
			// Any click closes the dropdown and is swallowed, so it never reaches the widgets underneath.
			presetMenuOpen = false;
			int index = presetIndexAt(event.x(), event.y());
			if (index >= 0) applyPreset(GradientPreset.values()[index]);
			return true;
		}
		if (hasScrollbar() && event.x() >= scrollbarX() && event.x() < scrollbarX() + 6
				&& event.y() >= bodyTop() && event.y() < bodyTop() + bodyHeight()) {
			draggingScrollbar = true;
			scrollToMouse(event.y());
			return true;
		}
		boolean handled = super.mouseClicked(event, doubleClick);
		// Clicking empty space drops focus, so the search bar or a field loses its white border.
		if (!handled) setFocused(null);
		return handled;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingScrollbar) {
			scrollToMouse(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	private boolean hasScrollbar() {
		return contentHeight > bodyHeight();
	}

	private int scrollbarX() {
		return panelX + panelWidth - 8;
	}

	private int thumbHeight() {
		return Math.max(20, bodyHeight() * bodyHeight() / contentHeight);
	}

	/** Centres the scrollbar thumb on the mouse; layoutRows clamps the result. */
	private void scrollToMouse(double mouseY) {
		int travel = bodyHeight() - thumbHeight();
		if (travel <= 0) return;
		scroll = (int) Math.round((mouseY - bodyTop() - thumbHeight() / 2.0) * (contentHeight - bodyHeight()) / travel);
		layoutRows();
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingScrollbar = false;
		boolean handled = super.mouseReleased(event);
		// A slider only needs focus while dragging; keeping it would leave the white border on.
		if (getFocused() instanceof BedrockSlider) setFocused(null);
		return handled;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (presetMenuOpen) {
			if (presetMenuContains(mouseX, mouseY)) {
				int max = Math.max(0, GradientPreset.values().length - MENU_VISIBLE);
				presetScroll = Math.max(0, Math.min(max, presetScroll - (int) Math.signum(scrollY)));
				return true;
			}
			presetMenuOpen = false;
		}
		if (mouseX >= contentX() && mouseX < panelX + panelWidth && mouseY >= bodyTop() && mouseY < bodyTop() + bodyHeight()) {
			scroll -= (int) Math.round(scrollY * ROW);
			layoutRows();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xB0000000);
		int right = panelX + panelWidth;
		int bottom = panelY + panelHeight;
		graphics.fill(panelX - 1, panelY - 1, right + 1, bottom + 1, 0xFF000000);
		graphics.fill(panelX, bodyTop(), right, bottom, CONTENT_COLOR);
		graphics.fill(panelX, bodyTop(), contentX(), bottom - FOOTER, SIDEBAR_COLOR);
		graphics.fill(contentX() - 1, bodyTop(), contentX(), bottom - FOOTER, 0xFF000000);
		headerBar(graphics, panelX, panelY, right, bodyTop());
		footerBar(graphics, panelX, bottom - FOOTER, right, bottom);
	}

	/** Header: 1px highlight ring around the face (like the buttons) on a 2px dark lip, then a black separator. */
	static void headerBar(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom) {
		graphics.fill(left, top, right, bottom - 3, BAR_LIGHT);
		graphics.fill(left + 1, top + 1, right - 1, bottom - 4, BAR_COLOR);
		graphics.fill(left, bottom - 3, right, bottom - 1, BAR_LIP);
		graphics.fill(left, bottom - 1, right, bottom, 0xFF000000);
	}

	/** Footer: black separator on top, then a 1px highlight ring around the face on a 2px dark lip (the header mirrored). */
	static void footerBar(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom) {
		graphics.fill(left, top, right, top + 1, 0xFF000000);
		graphics.fill(left, top + 1, right, bottom - 2, BAR_LIGHT);
		graphics.fill(left + 1, top + 2, right - 1, bottom - 3, BAR_COLOR);
		graphics.fill(left, bottom - 2, right, bottom, BAR_LIP);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		String heading = query.isEmpty() ? category.title : "Search";
		graphics.text(font, title.getString(), panelX + 10, panelY + 8, LABEL, false);
		int headingX = panelX + (panelWidth - font.width(heading)) / 2;
		graphics.text(font, heading, headingX, panelY + 8, LABEL, false);

		int top = bodyTop();
		int bottom = top + bodyHeight();
		int contentX = contentX();
		int right = panelX + panelWidth;
		graphics.enableScissor(contentX, top, right, bottom);
		int labelX = contentX + 10;
		int controlX = controlX();
		for (Entry entry : entries) {
			int y = top + entry.y() - scroll;
			if (y + ROW < top || y > bottom) continue;
			if (entry.row() == null) {
				graphics.text(font, entry.category().title, labelX, y + 5, MUTED, false);
				graphics.fill(labelX, y + SECTION - 2, right - 14, y + SECTION - 1, GREEN);
				continue;
			}
			if (mouseY >= y && mouseY < y + ROW && mouseX >= contentX && mouseX < right) {
				graphics.fill(contentX, y, right - 8, y + ROW, ROW_HOVER);
			}
			// 2px engraved divider (dark over light), spanning the same width as the hover highlight.
			graphics.fill(contentX, y + ROW - 2, right - 8, y + ROW - 1, DIVIDER_DARK);
			graphics.fill(contentX, y + ROW - 1, right - 8, y + ROW, DIVIDER_LIGHT);
			int labelWidth = controlX - labelX - 6;
			String description = entry.row().description();
			String label = font.plainSubstrByWidth(entry.row().label().get(), labelWidth);
			if (description.isEmpty()) {
				graphics.text(font, label, labelX, y + (ROW - 2 - 8) / 2, LABEL, false);
			} else {
				graphics.text(font, label, labelX, y + 4, LABEL, false);
				graphics.text(font, font.plainSubstrByWidth(description, labelWidth), labelX, y + 14, MUTED, false);
			}
			if (entry.row().preview()) extractPreview(graphics, controlX, y + 7, controlRight() - controlX, 12);
		}
		if (entries.isEmpty()) {
			String empty = "No settings match \"" + query + "\"";
			graphics.text(font, empty, contentX + (right - contentX - font.width(empty)) / 2, top + 14, MUTED, false);
		}
		graphics.disableScissor();

		int viewport = bodyHeight();
		if (hasScrollbar()) {
			int trackX = scrollbarX();
			int thumbHeight = thumbHeight();
			int thumbY = top + (viewport - thumbHeight) * scroll / (contentHeight - viewport);
			// Thin 2px rail centred behind the 6px thumb.
			graphics.fill(trackX + 2, top + 2, trackX + 4, bottom - 2, 0xFF1E1E1F);
			BedrockButton.bevel(graphics, trackX, thumbY + 2, 6, Math.max(6, thumbHeight - 4), 0xFFD0D1D4, 0xFFECEDEE, 0xFF58585A);
		}
		if (!status.isEmpty()) {
			graphics.text(font, font.plainSubstrByWidth(status, panelWidth - 172), panelX + 10,
					panelY + panelHeight - FOOTER + 9, 0xFFFFE066, false);
		}
		boolean overMenu = presetMenuOpen && presetMenuContains(mouseX, mouseY);
		// Widgets under the open dropdown must not show hover.
		super.extractRenderState(graphics, overMenu ? -1 : mouseX, overMenu ? -1 : mouseY, partialTick);
		if (presetMenuOpen && presetButton.visible) extractPresetMenu(graphics, mouseX, mouseY);
	}

	private void extractPreview(GuiGraphicsExtractor graphics, int x, int y, int barWidth, int barHeight) {
		graphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xFF000000);
		int segments = Math.max(1, Math.min(barWidth, 96));
		Gradient preview = previewGradient();
		long now = System.currentTimeMillis();
		for (int i = 0; i < segments; i++) {
			int x1 = x + i * barWidth / segments;
			int x2 = x + (i + 1) * barWidth / segments;
			graphics.fill(x1, y, Math.max(x1 + 1, x2), y + barHeight, 0xFF000000 | preview.colorAt(i, segments, now));
		}
	}

	@Override
	public void removed() {
		if (minecraft.player == null) {
			open = false;
			ConfigManager.previewShaderMode(originalShaderMode);
		}
	}

	@Override
	public void onClose() {
		closeWithoutSaving();
	}

	private void changeSelectedStop(int amount) {
		if (!applySelectedStop()) return;
		selectedStop = Math.max(0, Math.min(selectedStop + amount, draft.maxEnchantments.stops.size() - 1));
		loadSelectedStopFields();
	}

	private boolean applySelectedStop() {
		try {
			float position = Float.parseFloat(positionField.getValue().trim());
			if (!Float.isFinite(position) || position < 0.0f || position > 1.0f) {
				status = "Position must be between 0 and 1.";
				return false;
			}
			if (position == 0.0f) position = 0.0f;
			String colorInput = stopColorField.getValue().trim();
			if (!Gradient.isValidColor(colorInput)) {
				status = "Enter a valid RGB color such as #55CCFF.";
				return false;
			}
			for (int i = 0; i < draft.maxEnchantments.stops.size(); i++) {
				if (i != selectedStop && draft.maxEnchantments.stops.get(i).position == position) {
					status = "Each stop must use a unique position.";
					return false;
				}
			}
			ExtraConfig.GradientStop edited = draft.maxEnchantments.stops.get(selectedStop);
			edited.position = position;
			edited.color = canonicalHex(colorInput);
			draft.maxEnchantments.stops.sort(Comparator.comparingDouble(stop -> stop.position));
			selectedStop = draft.maxEnchantments.stops.indexOf(edited);
			loadSelectedStopFields();
			previewGradient = null;
			return true;
		} catch (NumberFormatException exception) {
			status = "Position must be a number between 0 and 1.";
			return false;
		}
	}

	private void addStop() {
		if (!applySelectedStop()) return;
		List<ExtraConfig.GradientStop> stops = draft.maxEnchantments.stops;
		if (stops.size() >= 8) {
			status = "A gradient can have at most 8 stops.";
			return;
		}
		int gapIndex = 0;
		float largestGap = -1.0f;
		for (int i = 0; i < stops.size() - 1; i++) {
			float gap = stops.get(i + 1).position - stops.get(i).position;
			if (gap > largestGap) {
				largestGap = gap;
				gapIndex = i;
			}
		}
		float position = (stops.get(gapIndex).position + stops.get(gapIndex + 1).position) * 0.5f;
		if (Float.compare(position, stops.get(gapIndex).position) == 0
				|| Float.compare(position, stops.get(gapIndex + 1).position) == 0) {
			status = "There is no room for another unique stop.";
			return;
		}
		int sampleIndex = Math.round(position * 255.0f);
		int rgb = previewGradient().colorAt(sampleIndex, 256, System.currentTimeMillis());
		ExtraConfig.GradientStop added = new ExtraConfig.GradientStop(position, String.format(Locale.ROOT, "#%06X", rgb));
		stops.add(added);
		stops.sort(Comparator.comparingDouble(stop -> stop.position));
		selectedStop = stops.indexOf(added);
		loadSelectedStopFields();
		previewGradient = null;
		status = "Stop added.";
	}

	private void removeStop() {
		if (!applySelectedStop()) return;
		if (draft.maxEnchantments.stops.size() <= 2) {
			status = "A gradient needs at least 2 stops.";
			return;
		}
		draft.maxEnchantments.stops.remove(selectedStop);
		selectedStop = Math.min(selectedStop, draft.maxEnchantments.stops.size() - 1);
		loadSelectedStopFields();
		previewGradient = null;
		status = "Stop removed.";
	}

	private void cycleInterpolation() {
		ExtraConfig.Interpolation[] values = ExtraConfig.Interpolation.values();
		draft.maxEnchantments.interpolation = values[(draft.maxEnchantments.interpolation.ordinal() + 1) % values.length];
		interpolationButton.setMessage(interpolationLabel());
		previewGradient = null;
	}

	private void cycleAnimation() {
		ExtraConfig.Animation[] values = ExtraConfig.Animation.values();
		draft.maxEnchantments.animation = values[(draft.maxEnchantments.animation.ordinal() + 1) % values.length];
		animationButton.setMessage(animationLabel());
		previewGradient = null;
	}

	private void toggleShaderMode() {
		draft.shaderAnimationMode = draft.shaderAnimationMode == ExtraConfig.ShaderAnimationMode.ANIMATED
				? ExtraConfig.ShaderAnimationMode.STATIC : ExtraConfig.ShaderAnimationMode.ANIMATED;
		ConfigManager.previewShaderMode(draft.shaderAnimationMode);
		previewGradient = null;
		status = "Shader mode switched live to " + draft.shaderAnimationMode + ". Save to persist.";
	}

	private void openProfiles() {
		if (!applySelectedStop() || !applyOtherFields()) return;
		minecraft.setScreenAndShow(new GradientProfilesScreen(this));
	}

	/** Row stride; neighbouring items share their 1px light line, so each box is MENU_ITEM + 1 tall. */
	private static final int MENU_ITEM = 17;
	private static final int MENU_VISIBLE = 6;

	private int presetMenuRows() {
		return Math.min(MENU_VISIBLE, GradientPreset.values().length);
	}

	private int presetMenuHeight() {
		return presetMenuRows() * MENU_ITEM + 3;
	}

	/** Below the button, or above it when the list would run past the bottom of the body. */
	private int presetMenuY() {
		int below = presetButton.getY() + presetButton.getHeight() + 1;
		return below + presetMenuHeight() <= bodyTop() + bodyHeight() ? below : presetButton.getY() - presetMenuHeight() - 1;
	}

	private boolean presetMenuContains(double x, double y) {
		return x >= presetButton.getX() && x < presetButton.getX() + presetButton.getWidth()
				&& y >= presetMenuY() && y < presetMenuY() + presetMenuHeight();
	}

	private int presetIndexAt(double x, double y) {
		boolean onListScrollbar = GradientPreset.values().length > MENU_VISIBLE && x >= presetButton.getX() + presetButton.getWidth() - 5;
		if (!presetButton.visible || !presetMenuContains(x, y) || onListScrollbar) return -1;
		int row = (int) Math.max(0, (y - presetMenuY() - 2) / MENU_ITEM);
		int index = presetScroll + row;
		return row < presetMenuRows() && index < GradientPreset.values().length ? index : -1;
	}

	/** Bedrock dropdown list: black outline around one column of grey items, each ringed by a light line. */
	private void extractPresetMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.nextStratum();
		int x = presetButton.getX();
		int y = presetMenuY();
		int w = presetButton.getWidth();
		GradientPreset[] presets = GradientPreset.values();
		boolean scrollable = presets.length > MENU_VISIBLE;
		int itemRight = x + w - 1 - (scrollable ? 4 : 0);
		graphics.fill(x, y, x + w, y + presetMenuHeight(), 0xFF000000);
		int hovered = presetIndexAt(mouseX, mouseY);
		graphics.fill(x + 1, y + 1, itemRight, y + presetMenuHeight() - 1, BAR_LIGHT);
		if (hovered >= presetScroll && hovered < presetScroll + presetMenuRows()) {
			// Brighter ring over the shared lines of the hovered item.
			int top = y + 1 + (hovered - presetScroll) * MENU_ITEM;
			graphics.fill(x + 1, top, itemRight, top + MENU_ITEM + 1, 0xFF6E6F71);
		}
		for (int row = 0; row < presetMenuRows(); row++) {
			int index = presetScroll + row;
			int top = y + 1 + row * MENU_ITEM;
			graphics.fill(x + 2, top + 1, itemRight - 1, top + MENU_ITEM, index == hovered ? 0xFF5A5B5C : BAR_COLOR);
			graphics.text(font, font.plainSubstrByWidth(presets[index].displayName(), itemRight - x - 12), x + 6,
					top + 1 + (MENU_ITEM - 1 - 8) / 2, LABEL, false);
		}
		if (scrollable) {
			int trackTop = y + 1;
			int trackHeight = presetMenuHeight() - 2;
			int thumb = Math.max(8, trackHeight * MENU_VISIBLE / presets.length);
			int thumbY = trackTop + (trackHeight - thumb) * presetScroll / (presets.length - MENU_VISIBLE);
			graphics.fill(itemRight, trackTop, x + w - 1, trackTop + trackHeight, 0xFF1E1E1F);
			graphics.fill(itemRight, thumbY, x + w - 1, thumbY + thumb, 0xFFD0D1D4);
		}
	}

	private void applyPreset(GradientPreset preset) {
		if (!applySelectedStop()) return;
		presetLabel = preset.displayName();
		presetButton.setMessage(Component.literal(presetLabel));
		preset.apply(draft.maxEnchantments);
		selectedStop = 0;
		loadSelectedStopFields();
		periodField.setValue(Long.toString(draft.maxEnchantments.animationPeriodMs));
		speedField.set(draft.maxEnchantments.animationSpeed);
		sineWaveField.setValue(String.format(Locale.ROOT, "%.2f", draft.maxEnchantments.sineWaveOffset));
		chromaSizeField.setValue(String.format(Locale.ROOT, "%.0f", draft.maxEnchantments.chromaSize));
		saturationField.setValue(String.format(Locale.ROOT, "%.2f", draft.maxEnchantments.chromaSaturation));
		interpolationButton.setMessage(interpolationLabel());
		animationButton.setMessage(animationLabel());
		previewGradient = null;
		status = preset.displayName() + " preset applied. Press Save to keep it.";
	}

	private void exportCode(boolean everything) {
		if (!applySelectedStop() || !applyOtherFields()) return;
		try {
			shareCodeField.setValue(everything ? GradientShareCode.encodeAll(draft) : GradientShareCode.encode(draft.maxEnchantments));
			status = everything ? "Exported gradient, good colour and profiles. Press Copy." : "Exported gradient. Press Copy.";
		} catch (IllegalArgumentException exception) {
			status = exception.getMessage();
		}
	}

	private void copyCode() {
		String code = shareCodeField.getValue().trim();
		if (code.isEmpty()) {
			status = "There is no code to copy.";
			return;
		}
		minecraft.keyboardHandler.setClipboard(code);
		status = "Copied to clipboard.";
	}

	private void importCode() {
		String code = shareCodeField.getValue();
		try {
			if (GradientShareCode.isFullCode(code)) {
				applyImportedAll(GradientShareCode.decodeAll(code));
				status = "All settings imported. Press Save to persist them.";
			} else {
				applyImportedGradient(GradientShareCode.decode(code));
				status = "Gradient imported. Press Save to persist it.";
			}
		} catch (IllegalArgumentException exception) {
			status = exception.getMessage() == null ? "Invalid share code." : exception.getMessage();
		}
	}

	private void openBenchmark() {
		minecraft.setScreenAndShow(new TooltipBenchmarkScreen(this));
	}

	private void saveAndClose() {
		if (!applySelectedStop() || !applyOtherFields()) return;
		ConfigManager.save(draft);
		open = false;
		minecraft.setScreenAndShow(parent);
	}

	private boolean applyOtherFields() {
		try {
			long period = Long.parseLong(periodField.getValue().trim());
			if (period < 50L || period > 86_400_000L) {
				status = "Animation period must be between 50 and 86400000 ms.";
				return false;
			}
			draft.maxEnchantments.animationPeriodMs = period;
			float speed = (float) speedField.get();
			if (!Float.isFinite(speed) || speed < 0.1f || speed > 5.0f) {
				status = "Animation speed must be between 0.1 and 5.0.";
				return false;
			}
			float wave = Float.parseFloat(sineWaveField.getValue().trim());
			if (!Float.isFinite(wave) || wave < 0.0f || wave > 0.5f) {
				status = "Sine wave offset must be between 0 and 0.5.";
				return false;
			}
			float chromaSize = Float.parseFloat(chromaSizeField.getValue().trim());
			if (!Float.isFinite(chromaSize) || chromaSize != 0.0f && (chromaSize < 1.0f || chromaSize > 200.0f)) {
				status = "Screen chroma size must be 0 (off) or 1-200.";
				return false;
			}
			draft.maxEnchantments.animationSpeed = speed;
			draft.maxEnchantments.sineWaveOffset = wave;
			draft.maxEnchantments.chromaSize = chromaSize;
			float saturation = Float.parseFloat(saturationField.getValue().trim());
			if (!Float.isFinite(saturation) || saturation < 0.0f || saturation > 1.0f) {
				status = "Aaron saturation must be between 0 and 1.";
				return false;
			}
			draft.maxEnchantments.chromaSaturation = saturation;
			String goodColor = goodColorField.getValue().trim();
			if (!Gradient.isValidColor(goodColor)) {
				status = "Enter a valid good-enchantment RGB color.";
				return false;
			}
			draft.goodEnchantmentColor = canonicalHex(goodColor);
			return true;
		} catch (NumberFormatException exception) {
			status = "Period, speed, wave and chroma size must be valid numbers.";
			return false;
		}
	}

	private void closeWithoutSaving() {
		open = false;
		ConfigManager.previewShaderMode(originalShaderMode);
		minecraft.setScreenAndShow(parent);
	}

	public ExtraConfig.MaxEnchantments getDraftGradient() {
		return draft.maxEnchantments;
	}

	public List<ExtraConfig.ItemProfile> getDraftProfiles() {
		return draft.itemProfiles;
	}

	public boolean isShaderStaticPreview() {
		return draft.shaderAnimationMode == ExtraConfig.ShaderAnimationMode.STATIC;
	}

	public void applyProfiles(List<ExtraConfig.ItemProfile> profiles) {
		draft.itemProfiles = profiles;
		status = "Item profiles updated in the draft. Press Save to keep them.";
	}

	private void applyImportedGradient(ExtraConfig.MaxEnchantments settings) {
		draft.maxEnchantments = settings;
		selectedStop = 0;
		previewGradient = null;
		loadSelectedStopFields();
		periodField.setValue(Long.toString(settings.animationPeriodMs));
		speedField.set(settings.animationSpeed);
		sineWaveField.setValue(String.format(Locale.ROOT, "%.2f", settings.sineWaveOffset));
		chromaSizeField.setValue(String.format(Locale.ROOT, "%.0f", settings.chromaSize));
		saturationField.setValue(String.format(Locale.ROOT, "%.2f", settings.chromaSaturation));
		interpolationButton.setMessage(interpolationLabel());
		animationButton.setMessage(animationLabel());
	}

	/** Applies an EEC3 code's shared fields: good-enchantment settings, main gradient and item profiles. */
	private void applyImportedAll(ExtraConfig shared) {
		draft.showGoodEnchantments = shared.showGoodEnchantments;
		draft.goodEnchantmentColor = shared.goodEnchantmentColor;
		draft.itemProfiles = shared.itemProfiles;
		draft.activeProfile = "";
		goodColorField.setValue(canonicalHex(draft.goodEnchantmentColor));
		applyImportedGradient(shared.maxEnchantments);
	}

	private void loadSelectedStopFields() {
		ExtraConfig.GradientStop stop = draft.maxEnchantments.stops.get(selectedStop);
		loadingStopFields = true;
		positionField.setValue(String.format(Locale.ROOT, "%.4f", stop.position));
		stopColorField.setValue(canonicalHex(stop.color));
		loadingStopFields = false;
		previousStopButton.active = selectedStop > 0;
		nextStopButton.active = selectedStop < draft.maxEnchantments.stops.size() - 1;
	}

	private Gradient previewGradient() {
		if (previewGradient == null) previewGradient = Gradient.from(draft.maxEnchantments, isShaderStaticPreview());
		return previewGradient;
	}

	private void updateLiveSelectedStop() {
		if (loadingStopFields || selectedStop >= draft.maxEnchantments.stops.size()) return;
		try {
			float position = Float.parseFloat(positionField.getValue().trim());
			String color = stopColorField.getValue().trim();
			if (!Float.isFinite(position) || position < 0.0f || position > 1.0f || !Gradient.isValidColor(color)) return;
			if (position == 0.0f) position = 0.0f;
			for (int i = 0; i < draft.maxEnchantments.stops.size(); i++) {
				if (i != selectedStop && draft.maxEnchantments.stops.get(i).position == position) return;
			}
			ExtraConfig.GradientStop edited = draft.maxEnchantments.stops.get(selectedStop);
			edited.position = position;
			edited.color = canonicalHex(color);
			draft.maxEnchantments.stops.sort(Comparator.comparingDouble(stop -> stop.position));
			selectedStop = draft.maxEnchantments.stops.indexOf(edited);
			previewGradient = null;
			status = "Live preview updated; Save to keep changes.";
		} catch (NumberFormatException ignored) {
			// Keep the last valid preview while the user is entering a partial number.
		}
	}

	private void updateLiveAnimationFields() {
		if (loadingStopFields) return;
		try {
			long period = Long.parseLong(periodField.getValue().trim());
			float speed = (float) speedField.get();
			float wave = Float.parseFloat(sineWaveField.getValue().trim());
			if (period < 50L || period > 86_400_000L || !Float.isFinite(speed) || speed < 0.1f || speed > 5.0f
					|| !Float.isFinite(wave) || wave < 0.0f || wave > 0.5f) return;
			draft.maxEnchantments.animationPeriodMs = period;
			draft.maxEnchantments.animationSpeed = speed;
			draft.maxEnchantments.sineWaveOffset = wave;
			previewGradient = null;
		} catch (NumberFormatException ignored) {
			// Keep the last valid preview while a numeric value is only partially entered.
		}
	}

	private Component interpolationLabel() {
		return Component.literal(draft.maxEnchantments.interpolation.name().replace('_', ' '));
	}

	private Component animationLabel() {
		return Component.literal(draft.maxEnchantments.animation.name().replace('_', ' '));
	}



	private static Float parseFloat(String value) {
		try {
			float parsed = Float.parseFloat(value.trim());
			return Float.isFinite(parsed) ? parsed : null;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String canonicalHex(String value) {
		return String.format(Locale.ROOT, "#%06X", Gradient.parseColor(value));
	}
}

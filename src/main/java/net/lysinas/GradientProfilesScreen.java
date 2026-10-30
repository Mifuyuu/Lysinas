package net.lysinas;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Editor for ordered item/enchantment-specific gradient rules. */
public final class GradientProfilesScreen extends Screen {
	private static final int PANEL_WIDTH = 480;
	private static final int PANEL_HEIGHT = 460;
	private final LysinasScreen parent;
	private final List<ExtraConfig.ItemProfile> profiles = new ArrayList<>();
	private int selected;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private String status = "";
	private Gradient preview;
	private boolean loading;

	private EditBox nameField;
	private EditBox itemIdsField;
	private EditBox enchantmentIdsField;
	private EditBox stopsField;
	private EditBox periodField;
	private EditBox speedField;
	private EditBox waveField;
	private Button previousButton;
	private Button nextButton;
	private Button interpolationButton;
	private Button animationButton;

	public GradientProfilesScreen(LysinasScreen parent) {
		super(Component.literal("Item Gradient Profiles"));
		this.parent = parent;
		for (ExtraConfig.ItemProfile profile : parent.getDraftProfiles()) profiles.add(copyProfile(profile));
		if (profiles.isEmpty()) {
			ExtraConfig.ItemProfile initial = new ExtraConfig.ItemProfile();
			initial.name = "New profile";
			initial.gradient = copyGradient(parent.getDraftGradient());
			profiles.add(initial);
		}
	}

	@Override
	protected void init() {
		panelWidth = Math.min(PANEL_WIDTH, Math.max(320, width - 16));
		panelX = (width - panelWidth) / 2;
		panelY = Math.max(4, (height - PANEL_HEIGHT) / 2);
		int left = panelX + 12;
		int inner = panelWidth - 24;
		int gap = 6;
		int half = (inner - gap) / 2;
		previousButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> changeSelection(-1))
				.bounds(left, panelY + 29, 42, 20).build());
		nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> changeSelection(1))
				.bounds(panelX + panelWidth - 54, panelY + 29, 42, 20).build());

		nameField = field(left, panelY + 70, inner, "Profile name");
		itemIdsField = field(left, panelY + 112, inner, "Item IDs (registry or Hypixel ID, comma-separated)");
		enchantmentIdsField = field(left, panelY + 154, inner, "Required enchantment IDs (comma-separated)");
		stopsField = field(left, panelY + 196, inner, "Stops: position:#RRGGBB, position:#RRGGBB (2–8)");
		stopsField.setMaxLength(300);

		interpolationButton = addRenderableWidget(Button.builder(Component.literal(""), button -> cycleInterpolation())
				.bounds(left, panelY + 232, half, 20).build());
		animationButton = addRenderableWidget(Button.builder(Component.literal(""), button -> cycleAnimation())
				.bounds(left + half + gap, panelY + 232, half, 20).build());

		periodField = field(left, panelY + 274, half, "Period ms");
		speedField = field(left + half + gap, panelY + 274, half, "Speed multiplier");
		waveField = field(left, panelY + 316, half, "Sine wave offset");
		addRenderableWidget(Button.builder(Component.literal("Copy main gradient"), button -> copyMainGradient())
				.bounds(left + half + gap, panelY + 316, half, 20).build());

		int presetWidth = (inner - gap * 2) / 3;
		addRenderableWidget(Button.builder(Component.literal("Rainbow"), button -> applyPreset(GradientPreset.RAINBOW))
				.bounds(left, panelY + 350, presetWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Sunset"), button -> applyPreset(GradientPreset.SUNSET))
				.bounds(left + presetWidth + gap, panelY + 350, presetWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Ocean"), button -> applyPreset(GradientPreset.OCEAN))
				.bounds(left + 2 * (presetWidth + gap), panelY + 350, presetWidth, 20).build());

		int actionWidth = (inner - gap * 4) / 5;
		addRenderableWidget(Button.builder(Component.literal("Add"), button -> addProfile())
				.bounds(left, panelY + 398, actionWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Remove"), button -> removeProfile())
				.bounds(left + actionWidth + gap, panelY + 398, actionWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Up"), button -> moveProfile(-1))
				.bounds(left + 2 * (actionWidth + gap), panelY + 398, actionWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Down"), button -> moveProfile(1))
				.bounds(left + 3 * (actionWidth + gap), panelY + 398, actionWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> cancel())
				.bounds(left + 4 * (actionWidth + gap), panelY + 398, actionWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Apply to editor"), button -> applyToParent())
				.bounds(panelX + panelWidth / 2 - 86, panelY + 436, 172, 20).build());

		loadSelected();
		stopsField.setResponder(value -> updateLiveStops());
	}

	private EditBox field(int x, int y, int fieldWidth, String hint) {
		EditBox field = addRenderableWidget(new EditBox(font, x, y, fieldWidth, 20, Component.literal(hint)));
		field.setMaxLength(512);
		return field;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xB8101420);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + PANEL_HEIGHT, 0xF0181E2A);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFF58C4D8);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int left = panelX + 12;
		int inner = panelWidth - 24;
		graphics.centeredText(font, title, width / 2, panelY + 9, 0xFFFFFFFF);
		graphics.centeredText(font, "Profile " + (selected + 1) + " / " + profiles.size() + " — first matching profile wins",
				width / 2, panelY + 33, 0xFFB9D9E1);
		graphics.text(font, "Name", left, panelY + 58, 0xFFC7D0D9);
		graphics.text(font, "Match selectors (if both groups are filled, both must match)", left, panelY + 100, 0xFFC7D0D9);
		graphics.text(font, "Enchantment IDs from the item's custom-data enchantments", left, panelY + 142, 0xFFC7D0D9);
		graphics.text(font, "Custom gradient stops", left, panelY + 184, 0xFFC7D0D9);
		graphics.text(font, "Live gradient preview", left, panelY + 374, 0xFFC7D0D9);
		int segments = Math.max(1, Math.min(inner, 160));
		Gradient current = previewGradient();
		long now = System.currentTimeMillis();
		for (int i = 0; i < segments; i++) {
			int x1 = left + i * inner / segments;
			int x2 = left + (i + 1) * inner / segments;
			graphics.fill(x1, panelY + 386, Math.max(x1 + 1, x2), panelY + 394,
					0xFF000000 | current.colorAt(i, segments, now));
		}
		if (!status.isEmpty()) graphics.centeredText(font, status, width / 2, panelY + 421, 0xFFFFC66D);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		cancel();
	}

	private void changeSelection(int direction) {
		if (!applyFields()) return;
		selected = Math.floorMod(selected + direction, profiles.size());
		loadSelected();
	}

	private void addProfile() {
		if (!applyFields()) return;
		if (profiles.size() >= 32) {
			status = "Up to 32 profiles are supported.";
			return;
		}
		ExtraConfig.ItemProfile profile = new ExtraConfig.ItemProfile();
		profile.name = "Profile " + (profiles.size() + 1);
		profile.gradient = copyGradient(parent.getDraftGradient());
		profiles.add(profile);
		selected = profiles.size() - 1;
		loadSelected();
		status = "New profile created; add at least one item or enchantment ID.";
	}

	private void removeProfile() {
		if (profiles.size() <= 1) {
			if (!applyFields()) return;
			current().itemIds.clear();
			current().enchantmentIds.clear();
			loadSelected();
			status = "Last profile cleared; Apply to editor will remove all profiles.";
			return;
		}
		profiles.remove(selected);
		selected = Math.min(selected, profiles.size() - 1);
		loadSelected();
		status = "Profile removed from this draft.";
	}

	private void moveProfile(int direction) {
		if (!applyFields()) return;
		int target = selected + direction;
		if (target < 0 || target >= profiles.size()) return;
		ExtraConfig.ItemProfile current = profiles.get(selected);
		profiles.set(selected, profiles.get(target));
		profiles.set(target, current);
		selected = target;
		loadSelected();
		status = "Profile priority updated; earlier profiles are checked first.";
	}

	private void applyToParent() {
		if (!applyFields()) return;
		List<ExtraConfig.ItemProfile> applicable = new ArrayList<>();
		for (ExtraConfig.ItemProfile profile : profiles) {
			if (!profile.itemIds.isEmpty() || !profile.enchantmentIds.isEmpty()) applicable.add(profile);
		}
		parent.applyProfiles(copyProfiles(applicable));
		minecraft.setScreenAndShow(parent);
	}

	private boolean applyFields() {
		try {
			ExtraConfig.ItemProfile profile = current();
			String name = nameField.getValue().trim();
			if (name.isEmpty()) {
				status = "Profile name cannot be empty.";
				return false;
			}
			for (int i = 0; i < profiles.size(); i++) {
				if (i != selected && profiles.get(i).name.equalsIgnoreCase(name)) {
					status = "Profile names must be unique.";
					return false;
				}
			}
			profile.name = name;
			profile.itemIds = parseIds(itemIdsField.getValue());
			profile.enchantmentIds = parseIds(enchantmentIdsField.getValue());
			if (profile.itemIds.size() > 32 || profile.enchantmentIds.size() > 32) {
				status = "Use at most 32 selectors in each list.";
				return false;
			}
			if (!parseStopsInto(profile.gradient, stopsField.getValue())) {
				status = "Stops use position:#RRGGBB pairs, comma-separated (2–8 unique positions).";
				return false;
			}
			long period = Long.parseLong(periodField.getValue().trim());
			float speed = Float.parseFloat(speedField.getValue().trim());
			float wave = Float.parseFloat(waveField.getValue().trim());
			if (period < 50L || period > 86_400_000L || !Float.isFinite(speed) || speed < 0.1f || speed > 5.0f
					|| !Float.isFinite(wave) || wave < 0.0f || wave > 0.5f) {
				status = "Period, speed, or wave is outside the supported range.";
				return false;
			}
			profile.gradient.animationPeriodMs = period;
			profile.gradient.animationSpeed = speed;
			profile.gradient.sineWaveOffset = wave;
			preview = null;
			return true;
		} catch (NumberFormatException exception) {
			status = "Period, speed, wave and stop positions must be valid numbers.";
			return false;
		}
	}

	private void updateLiveStops() {
		if (loading || stopsField == null) return;
		ExtraConfig.ItemProfile profile = current();
		if (parseStopsInto(profile.gradient, stopsField.getValue())) {
			preview = null;
			status = "Live preview updated; Apply to editor, then Save.";
		}
	}

	private boolean parseStopsInto(ExtraConfig.MaxEnchantments settings, String text) {
		String[] entries = text.split(",");
		if (entries.length < 2 || entries.length > 8) return false;
		List<ExtraConfig.GradientStop> parsed = new ArrayList<>();
		try {
			for (String entry : entries) {
				String[] pair = entry.trim().split(":", 2);
				if (pair.length != 2) return false;
				float position = Float.parseFloat(pair[0].trim());
				if (!Float.isFinite(position) || position < 0.0f || position > 1.0f || !Gradient.isValidColor(pair[1].trim())) return false;
				if (position == 0.0f) position = 0.0f;
				for (ExtraConfig.GradientStop prior : parsed) if (prior.position == position) return false;
				parsed.add(new ExtraConfig.GradientStop(position, canonicalHex(pair[1].trim())));
			}
			parsed.sort(Comparator.comparingDouble(stop -> stop.position));
			settings.stops = parsed;
			return true;
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	private void cycleInterpolation() {
		if (!applyFields()) return;
		ExtraConfig.Interpolation[] values = ExtraConfig.Interpolation.values();
		current().gradient.interpolation = values[(current().gradient.interpolation.ordinal() + 1) % values.length];
		updateLabels();
	}

	private void cycleAnimation() {
		if (!applyFields()) return;
		ExtraConfig.Animation[] values = ExtraConfig.Animation.values();
		current().gradient.animation = values[(current().gradient.animation.ordinal() + 1) % values.length];
		updateLabels();
	}

	private void applyPreset(GradientPreset preset) {
		if (!applyFields()) return;
		preset.apply(current().gradient);
		loadSelected();
		status = preset.name() + " preset applied to this profile.";
	}

	private void copyMainGradient() {
		if (!applyFields()) return;
		current().gradient = copyGradient(parent.getDraftGradient());
		loadSelected();
		status = "Main editor gradient copied to this profile.";
	}

	private void loadSelected() {
		loading = true;
		ExtraConfig.ItemProfile profile = current();
		nameField.setValue(profile.name);
		itemIdsField.setValue(String.join(", ", profile.itemIds));
		enchantmentIdsField.setValue(String.join(", ", profile.enchantmentIds));
		stopsField.setValue(stopsText(profile.gradient));
		periodField.setValue(Long.toString(profile.gradient.animationPeriodMs));
		speedField.setValue(String.format(Locale.ROOT, "%.2f", profile.gradient.animationSpeed));
		waveField.setValue(String.format(Locale.ROOT, "%.2f", profile.gradient.sineWaveOffset));
		loading = false;
		updateLabels();
		previousButton.active = profiles.size() > 1;
		nextButton.active = profiles.size() > 1;
		preview = null;
	}

	private void updateLabels() {
		interpolationButton.setMessage(Component.literal("Interpolation: " + current().gradient.interpolation));
		animationButton.setMessage(Component.literal("Animation: " + current().gradient.animation.name().replace('_', ' ')));
	}

	private ExtraConfig.ItemProfile current() {
		return profiles.get(Math.max(0, Math.min(selected, profiles.size() - 1)));
	}

	private Gradient previewGradient() {
		if (preview == null) preview = Gradient.from(current().gradient, parent.isShaderStaticPreview());
		return preview;
	}

	private void cancel() {
		minecraft.setScreenAndShow(parent);
	}

	private static List<String> parseIds(String value) {
		List<String> result = new ArrayList<>();
		for (String token : value.split(",")) {
			String id = token.trim();
			if (!id.isEmpty() && result.stream().noneMatch(existing -> existing.equalsIgnoreCase(id))) result.add(id);
		}
		return result;
	}

	private static String stopsText(ExtraConfig.MaxEnchantments settings) {
		StringBuilder value = new StringBuilder();
		for (ExtraConfig.GradientStop stop : settings.stops) {
			if (!value.isEmpty()) value.append(", ");
			value.append(String.format(Locale.ROOT, "%.4f", stop.position)).append(':').append(canonicalHex(stop.color));
		}
		return value.toString();
	}

	private static String canonicalHex(String value) {
		return String.format(Locale.ROOT, "#%06X", Gradient.parseColor(value));
	}

	private static ExtraConfig.MaxEnchantments copyGradient(ExtraConfig.MaxEnchantments source) {
		ExtraConfig.MaxEnchantments target = new ExtraConfig.MaxEnchantments();
		target.animation = source.animation;
		target.animationPeriodMs = source.animationPeriodMs;
		target.animationSpeed = source.animationSpeed;
		target.sineWaveOffset = source.sineWaveOffset;
		target.chromaSize = source.chromaSize;
		target.chromaSaturation = source.chromaSaturation;
		target.interpolation = source.interpolation;
		target.stops = new ArrayList<>();
		for (ExtraConfig.GradientStop stop : source.stops) target.stops.add(new ExtraConfig.GradientStop(stop.position, stop.color));
		return target;
	}

	private static ExtraConfig.ItemProfile copyProfile(ExtraConfig.ItemProfile source) {
		ExtraConfig.ItemProfile copy = new ExtraConfig.ItemProfile();
		copy.name = source.name;
		copy.itemIds = new ArrayList<>(source.itemIds);
		copy.enchantmentIds = new ArrayList<>(source.enchantmentIds);
		copy.gradient = copyGradient(source.gradient);
		return copy;
	}

	private static List<ExtraConfig.ItemProfile> copyProfiles(List<ExtraConfig.ItemProfile> source) {
		List<ExtraConfig.ItemProfile> result = new ArrayList<>(source.size());
		for (ExtraConfig.ItemProfile profile : source) result.add(copyProfile(profile));
		return result;
	}
}

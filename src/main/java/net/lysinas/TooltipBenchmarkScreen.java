package net.lysinas;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Renders repeated tooltip-like text through the real GUI glyph pipeline, first with
 * Lysinas markers and then with vanilla text colors, and writes frame samples to CSV.
 */
public final class TooltipBenchmarkScreen extends Screen {
	private static final int WARMUP_FRAMES = 90;
	private static final int MEASURE_FRAMES = 240;
	private static final int DISCARD_SAMPLES = 8;
	private static final int[] COUNTS = {16, 32, 64, 128};
	private static final String[] LINES = {
			"Sharpness V - Critical Damage +8.8%",
			"Looting IV - Bonus Drops +20%",
			"Overload V - Critical Chance +10%"
	};

	private final Screen parent;
	private final List<Component> shaderLines = new ArrayList<>();
	private final List<Component> vanillaLines = new ArrayList<>();
	private final List<Double> shaderSamples = new ArrayList<>();
	private final List<Double> vanillaSamples = new ArrayList<>();
	private int tooltipCount = 64;
	private int frameInStage;
	private int discarded;
	private Stage stage = Stage.IDLE;
	private Button countButton;
	private Button runButton;
	private String report = "";
	private Path csvPath;

	public TooltipBenchmarkScreen(Screen parent) {
		super(Component.literal("Tooltip Shader Benchmark"));
		this.parent = parent;
		this.csvPath = FabricLoader.getInstance().getConfigDir().resolve("lysinas-benchmark.csv");
	}

	@Override
	protected void init() {
		int buttonY = height - 34;
		countButton = addRenderableWidget(Button.builder(countLabel(), button -> cycleCount())
				.bounds(width / 2 - 170, buttonY, 160, 20).build());
		runButton = addRenderableWidget(Button.builder(Component.literal("Start benchmark"), button -> startBenchmark())
				.bounds(width / 2 + 10, buttonY, 160, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), button -> returnToEditor())
				.bounds(width / 2 - 50, height - 60, 100, 20).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE00A0D12);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (isRunning()) drawSyntheticWorkload(graphics);
		graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
		graphics.centeredText(font, "Each synthetic tooltip has 3 lines. Counts are tooltip groups per frame.",
				width / 2, 27, 0xFFB9C3CE);
		graphics.centeredText(font, "Shader samples: " + shaderSamples.size() + "   Vanilla samples: " + vanillaSamples.size(),
				width / 2, 42, 0xFFB9C3CE);
		graphics.centeredText(font, "Phase: " + stage.label, width / 2, 57, 0xFF58C4D8);
		if (!report.isEmpty()) {
			String[] lines = report.split("\\n");
			for (int i = 0; i < lines.length; i++) graphics.centeredText(font, lines[i], width / 2, 82 + i * 13, 0xFFE8EDF2);
		}
		if (!isRunning()) {
			graphics.centeredText(font, "Measures Minecraft frame time (not GPU-only time); keep VSync/FPS cap the same for comparisons.",
					width / 2, height - 78, 0xFF98A6B5);
			graphics.centeredText(font, "CSV: " + csvPath, width / 2, height - 68, 0xFF98A6B5);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		advanceBenchmark();
	}

	@Override
	public void onClose() {
		returnToEditor();
	}

	private void drawSyntheticWorkload(GuiGraphicsExtractor graphics) {
		boolean shader = stage == Stage.WARMUP_SHADER || stage == Stage.MEASURE_SHADER;
		List<Component> lines = shader ? shaderLines : vanillaLines;
		int columns = Math.max(1, width / 190);
		int cellWidth = Math.max(1, width / columns);
		int visibleRows = Math.max(1, (height - 82) / 11);
		for (int i = 0; i < lines.size(); i++) {
			int column = i % columns;
			int row = (i / columns) % visibleRows;
			int x = column * cellWidth + 4;
			int y = 72 + row * 11;
			graphics.text(font, lines.get(i), x, y, 0xFFFFFFFF);
		}
	}

	private void startBenchmark() {
		shaderSamples.clear();
		vanillaSamples.clear();
		report = "";
		frameInStage = 0;
		discarded = 0;
		rebuildWorkload();
		stage = Stage.WARMUP_SHADER;
		countButton.active = false;
		runButton.active = false;
	}

	private void advanceBenchmark() {
		if (!isRunning()) return;
		long frameTimeNs = minecraft.getFrameTimeNs();
		if (stage == Stage.MEASURE_SHADER || stage == Stage.MEASURE_VANILLA) {
			if (frameTimeNs > 0L) {
				if (discarded < DISCARD_SAMPLES) discarded++;
				else if (stage == Stage.MEASURE_SHADER) shaderSamples.add(frameTimeNs / 1_000_000.0);
				else vanillaSamples.add(frameTimeNs / 1_000_000.0);
			}
		}
		frameInStage++;
		if (stage == Stage.WARMUP_SHADER && frameInStage >= WARMUP_FRAMES) switchStage(Stage.MEASURE_SHADER);
		else if (stage == Stage.MEASURE_SHADER && frameInStage >= MEASURE_FRAMES) switchStage(Stage.WARMUP_VANILLA);
		else if (stage == Stage.WARMUP_VANILLA && frameInStage >= WARMUP_FRAMES) switchStage(Stage.MEASURE_VANILLA);
		else if (stage == Stage.MEASURE_VANILLA && frameInStage >= MEASURE_FRAMES) finishBenchmark();
	}

	private void switchStage(Stage next) {
		stage = next;
		frameInStage = 0;
		discarded = 0;
	}

	private void finishBenchmark() {
		stage = Stage.DONE;
		countButton.active = true;
		runButton.active = true;
		Stats shader = Stats.of(shaderSamples);
		Stats vanilla = Stats.of(vanillaSamples);
		double overhead = vanilla.meanMs == 0.0 ? 0.0 : (shader.meanMs / vanilla.meanMs - 1.0) * 100.0;
		report = String.format(Locale.ROOT,
				"Shader: mean %.3f ms | p95 %.3f ms | %.1f FPS average (%d samples)\n"
						+ "Vanilla: mean %.3f ms | p95 %.3f ms | %.1f FPS average (%d samples)\n"
						+ "Mean frame-time overhead: %+.1f%%",
				shader.meanMs, shader.p95Ms, shader.fps, shaderSamples.size(),
				vanilla.meanMs, vanilla.p95Ms, vanilla.fps, vanillaSamples.size(), overhead);
		writeCsv();
		Lysinas.LOGGER.info("Tooltip benchmark complete: {} tooltips/frame; {}", tooltipCount, report.replace('\n', ';'));
	}

	private void rebuildWorkload() {
		shaderLines.clear();
		vanillaLines.clear();
		for (int tooltip = 0; tooltip < tooltipCount; tooltip++) {
			for (int line = 0; line < LINES.length; line++) {
				String text = LINES[line] + "   Sample " + (tooltip + 1);
				shaderLines.add(markedLine(text));
				vanillaLines.add(Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xE6EAF0))));
			}
		}
	}

	private static Component markedLine(String text) {
		int[] codePoints = text.codePoints().toArray();
		MutableComponent line = Component.empty();
		int count = Math.max(1, codePoints.length);
		for (int i = 0; i < codePoints.length; i++) {
			String glyph = new String(Character.toChars(codePoints[i]));
			double start = i / (double) count;
			double end = (i + 1) / (double) count;
			line.append(Component.literal(glyph).setStyle(Style.EMPTY.withColor(GradientShaderRenderer.encodeProgress(start, end))));
		}
		return line;
	}

	private void writeCsv() {
		StringBuilder csv = new StringBuilder("phase,tooltips,frame_ms\n");
		appendSamples(csv, "shader", shaderSamples);
		appendSamples(csv, "vanilla", vanillaSamples);
		try {
			Files.createDirectories(csvPath.getParent());
			Files.writeString(csvPath, csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		} catch (IOException exception) {
			Lysinas.LOGGER.error("Could not write tooltip benchmark CSV to {}.", csvPath, exception);
			statusError();
		}
	}

	private void statusError() {
		report += "\nCSV write failed; see latest log.";
	}

	private void appendSamples(StringBuilder csv, String phaseName, List<Double> samples) {
		for (double ms : samples) csv.append(phaseName).append(',').append(tooltipCount).append(',')
				.append(String.format(Locale.ROOT, "%.6f", ms)).append('\n');
	}

	private void cycleCount() {
		int current = 0;
		for (int i = 0; i < COUNTS.length; i++) if (COUNTS[i] == tooltipCount) current = i;
		tooltipCount = COUNTS[(current + 1) % COUNTS.length];
		countButton.setMessage(countLabel());
	}

	private Component countLabel() {
		return Component.literal("Workload: " + tooltipCount + " tooltips");
	}

	private boolean isRunning() {
		return stage != Stage.IDLE && stage != Stage.DONE;
	}

	private void returnToEditor() {
		minecraft.setScreenAndShow(parent);
	}

	private enum Stage {
		IDLE("Ready"),
		WARMUP_SHADER("Shader warm-up"),
		MEASURE_SHADER("Measuring shader path"),
		WARMUP_VANILLA("Vanilla warm-up"),
		MEASURE_VANILLA("Measuring vanilla path"),
		DONE("Complete");

		private final String label;
		Stage(String label) { this.label = label; }
	}

	private record Stats(double meanMs, double p95Ms, double fps) {
		private static Stats of(List<Double> source) {
			if (source.isEmpty()) return new Stats(0.0, 0.0, 0.0);
			List<Double> sorted = new ArrayList<>(source);
			Collections.sort(sorted);
			double mean = source.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
			double p95 = sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
			return new Stats(mean, p95, mean <= 0.0 ? 0.0 : 1000.0 / mean);
		}
	}
}

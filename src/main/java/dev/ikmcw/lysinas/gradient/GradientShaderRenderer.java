package dev.ikmcw.lysinas.gradient;

import dev.ikmcw.lysinas.Main;
import dev.ikmcw.lysinas.config.ConfigManager;
import dev.ikmcw.lysinas.config.ExtraConfig;
import dev.ikmcw.lysinas.mixin.GlyphInstanceAccessor;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Shader selection, marker encoding, and gradient uniform management for tooltip glyphs. */
public final class GradientShaderRenderer {
	public static final String MARKER_NAME = "lysinas_gradient";
	public static final String UNIFORM_NAME = "LysinasGradientUniforms";
	private static final int LUT_SIZE = 256;
	private static final int COLOR_BITS = 12;
	private static final int COLOR_MAX = (1 << COLOR_BITS) - 1;
	private static final int MAX_PROFILE_SLOTS = 32;
	private static final String[] MARKER_NAMES = createMarkerNames();
	private static final Map<String, Integer> MARKER_SLOTS = createMarkerSlots();
	private static final int UNIFORM_SIZE = uniformSize();

	private static final Gradient[] cachedGradients = new Gradient[MAX_PROFILE_SLOTS + 1];
	private static final LutUniform[] cachedUniforms = new LutUniform[MAX_PROFILE_SLOTS + 1];
	private static final Map<Integer, RenderPipeline> pipelines = new ConcurrentHashMap<>();
	private static final Map<RenderPipeline, Integer> pipelineSlots = new ConcurrentHashMap<>();
	private static DynamicUniformStorage<LutUniform> uniformStorage;
	/** One clock per frame so every draw in the frame shares a phase and reuses the same uniform slice. */
	private static long frameClockMs = -1L;

	private GradientShaderRenderer() {}

	public static TextColor encodeProgress(double start, double end) {
		return encodeProgress(start, end, 0);
	}

	public static TextColor encodeProgress(double start, double end, int profileSlot) {
		int slot = Math.max(0, Math.min(MAX_PROFILE_SLOTS, profileSlot));
		int startValue = quantize(start);
		int endValue = quantize(end);
		return new TextColor((startValue << COLOR_BITS) | endValue, MARKER_NAMES[slot]);
	}

	public static boolean isMarked(GlyphRenderState state) {
		return paletteIndex(state) >= 0;
	}

	public static int paletteIndex(GlyphRenderState state) {
		if (!(state.renderable() instanceof GlyphInstanceAccessor accessor)) return -1;
		Style style = accessor.lysinas$getStyle();
		TextColor color = style.getColor();
		return color == null ? -1 : parsePaletteIndex(color.serialize());
	}

	public static RenderPipeline pipeline() {
		return pipeline(0);
	}

	public static RenderPipeline pipeline(int profileSlot) {
		int slot = Math.max(0, Math.min(MAX_PROFILE_SLOTS, profileSlot));
		return pipelines.computeIfAbsent(slot, value -> {
			RenderPipeline pipeline = PipelineHolder.create(value);
			pipelineSlots.put(pipeline, value);
			return pipeline;
		});
	}

	public static int paletteIndexForPipeline(RenderPipeline pipeline) {
		Integer slot = pipelineSlots.get(pipeline);
		return slot == null ? -1 : slot;
	}

	public static VertexConsumer wrapVertices(GlyphRenderState state, VertexConsumer delegate) {
		if (!(state.renderable() instanceof GlyphInstanceAccessor accessor)) return delegate;
		TextColor color = accessor.lysinas$getStyle().getColor();
		if (color == null || parsePaletteIndex(color.serialize()) < 0) return delegate;
		int payload = color.getValue();
		float start = ((payload >>> COLOR_BITS) & COLOR_MAX) / (float) COLOR_MAX;
		float end = (payload & COLOR_MAX) / (float) COLOR_MAX;
		// GlyphRenderState.bounds() is intentionally null in Minecraft 26.2. Use the
		// glyph layout interval [x, activeRight] and capture pre-transform vertex X.
		// Both values are local coordinates and already include the glyph's x once.
		float glyphStartX = accessor.lysinas$getX();
		float glyphEndX = accessor.lysinas$getActiveRight();
		if (!Float.isFinite(glyphStartX) || !Float.isFinite(glyphEndX) || glyphEndX <= glyphStartX) {
			glyphStartX = state.renderable().left();
			glyphEndX = state.renderable().right();
		}
		return new GradientProgressVertexConsumer(delegate, glyphStartX, glyphEndX,
				start, end, payload);
	}

	public static GpuBufferSlice uniformSlice() {
		return uniformSlice(0);
	}

	public static GpuBufferSlice uniformSlice(int profileSlot) {
		ExtraConfig config = ConfigManager.get();
		int slot = Math.max(0, Math.min(MAX_PROFILE_SLOTS, profileSlot));
		ExtraConfig.MaxEnchantments settings = slot > 0 && config.itemProfiles != null && slot <= config.itemProfiles.size()
				? config.itemProfiles.get(slot - 1).gradient : config.maxEnchantments;
		LutUniform lut = getUniform(slot, settings);
		Gradient gradient = cachedGradients[slot];
		if (frameClockMs < 0L) frameClockMs = System.nanoTime() / 1_000_000L;
		// Aaron's Mod spans one hue cycle over chromaSize% of the screen divided by 1.75.
		float screenSpan = settings.chromaSize <= 0.0f ? 0.0f
				: settings.chromaSize / 100.0f * Minecraft.getInstance().getWindow().getWidth() / 1.75f;
		boolean frozen = config.shaderAnimationMode == ExtraConfig.ShaderAnimationMode.STATIC;
		ExtraConfig.Animation animation = Gradient.effectiveAnimation(gradient.animation(), frozen);
		// Aaron's ChromaSize scales width and height separately; 0 here means his default of 100.
		float aaronSize = (settings.chromaSize <= 0.0f ? 100.0f : settings.chromaSize) / 100.0f;
		var window = Minecraft.getInstance().getWindow();
		LutUniform uniform = new LutUniform(lut.colors, frozen ? 0.0f : (float) gradient.phaseAt(frameClockMs),
				frozen ? 0.0f : (float) gradient.sineWaveOffset(), animation.ordinal(), screenSpan,
				aaronSize * window.getWidth(), aaronSize * window.getHeight(), settings.chromaSaturation);
		return getStorage().writeUniform(uniform);
	}

	public static void endFrame() {
		frameClockMs = -1L;
		DynamicUniformStorage<LutUniform> storage = uniformStorage;
		if (storage != null) storage.endFrame();
	}

	public static synchronized void close() {
		DynamicUniformStorage<LutUniform> storage = uniformStorage;
		uniformStorage = null;
		Arrays.fill(cachedGradients, null);
		Arrays.fill(cachedUniforms, null);
		if (storage != null) storage.close();
	}

	private static synchronized LutUniform getUniform(int profileSlot, ExtraConfig.MaxEnchantments settings) {
		Gradient current = cachedGradients[profileSlot];
		LutUniform uniform = cachedUniforms[profileSlot];
		if (current == null || uniform == null || !current.matches(settings)) {
			current = Gradient.from(settings);
			int[] colors = new int[LUT_SIZE];
			for (int i = 0; i < LUT_SIZE; i++) colors[i] = current.paletteColorAt(i, LUT_SIZE);
			uniform = new LutUniform(colors, 0.0f, 0.0f, 0, 0.0f, 1.0f, 1.0f, 0.0f);
			cachedGradients[profileSlot] = current;
			cachedUniforms[profileSlot] = uniform;
		}
		return uniform;
	}

	private static int parsePaletteIndex(String marker) {
		if (marker == null) return -1;
		return MARKER_SLOTS.getOrDefault(marker, -1);
	}

	private static String[] createMarkerNames() {
		String[] names = new String[MAX_PROFILE_SLOTS + 1];
		names[0] = MARKER_NAME;
		for (int i = 1; i < names.length; i++) names[i] = MARKER_NAME + "_" + i;
		return names;
	}

	private static Map<String, Integer> createMarkerSlots() {
		Map<String, Integer> slots = new HashMap<>();
		for (int i = 0; i < MARKER_NAMES.length; i++) slots.put(MARKER_NAMES[i], i);
		return Map.copyOf(slots);
	}

	private static DynamicUniformStorage<LutUniform> getStorage() {
		DynamicUniformStorage<LutUniform> storage = uniformStorage;
		if (storage != null) return storage;
		synchronized (GradientShaderRenderer.class) {
			storage = uniformStorage;
			if (storage == null) {
				storage = new DynamicUniformStorage<>("Lysinas gradient LUT", UNIFORM_SIZE, 4);
				uniformStorage = storage;
			}
			return storage;
		}
	}

	private static int uniformSize() {
		Std140SizeCalculator calculator = new Std140SizeCalculator().putVec4().putVec4();
		for (int i = 0; i < LUT_SIZE; i++) calculator.putVec4();
		return calculator.get();
	}

	private static int quantize(double value) {
		return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * COLOR_MAX);
	}

	static float progressAtLocalX(float vertexX, float glyphStartX, float glyphEndX, float start, float end) {
		float width = glyphEndX - glyphStartX;
		float local = width <= 0.0f ? 0.0f : Math.max(0.0f, Math.min(1.0f, (vertexX - glyphStartX) / width));
		return start + (end - start) * local;
	}

	private static final class PipelineHolder {
		private static final RenderPipeline.Snippet MATRICES_PROJECTION = RenderPipeline.builder()
				.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
				.buildSnippet();

		private static RenderPipeline create(int profileSlot) {
			String name = profileSlot == 0 ? "tooltip_gradient" : "tooltip_gradient_profile_" + profileSlot;
			return RenderPipelines.register(RenderPipeline.builder(MATRICES_PROJECTION)
						.withLocation(Identifier.fromNamespaceAndPath(Main.MOD_ID, "pipeline/" + name))
						.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
						.withPrimitiveTopology(PrimitiveTopology.QUADS)
						.withVertexShader(Identifier.fromNamespaceAndPath(Main.MOD_ID, "tooltip_gradient"))
						.withFragmentShader(Identifier.fromNamespaceAndPath(Main.MOD_ID, "tooltip_gradient"))
						.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
						.withDepthStencilState(Optional.empty())
						.withBindGroupLayout(BindGroupLayout.builder()
								.withSampler("Sampler0")
								.withUniform(UNIFORM_NAME, UniformType.UNIFORM_BUFFER)
								.build())
						.build());
		}
	}

	private static final class LutUniform implements DynamicUniformStorage.DynamicUniform {
		private final int[] colors;
		private final float phase;
		private final float wave;
		private final int animation;
		private final float screenSpan;
		private final float aaronWidth;
		private final float aaronHeight;
		private final float saturation;

		private LutUniform(int[] colors, float phase, float wave, int animation, float screenSpan,
				float aaronWidth, float aaronHeight, float saturation) {
			this.colors = colors;
			this.phase = phase;
			this.wave = wave;
			this.animation = animation;
			this.screenSpan = screenSpan;
			this.aaronWidth = aaronWidth;
			this.aaronHeight = aaronHeight;
			this.saturation = saturation;
		}

		// DynamicUniformStorage skips the upload when equal to the previous write.
		@Override
		public boolean equals(Object other) {
			return other instanceof LutUniform that && colors == that.colors && phase == that.phase
					&& wave == that.wave && animation == that.animation && screenSpan == that.screenSpan
					&& aaronWidth == that.aaronWidth && aaronHeight == that.aaronHeight && saturation == that.saturation;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(colors) * 31 + Float.hashCode(phase);
		}

		@Override
		public void write(ByteBuffer buffer) {
			Std140Builder builder = Std140Builder.intoBuffer(buffer);
			builder.putVec4(phase, wave, animation, screenSpan);
			builder.putVec4(aaronWidth, aaronHeight, saturation, 0.0f);
			for (int rgb : colors) {
				builder.putVec4(((rgb >>> 16) & 0xFF) / 255.0f,
						((rgb >>> 8) & 0xFF) / 255.0f,
						(rgb & 0xFF) / 255.0f,
						1.0f);
			}
		}
	}

	private static final class GradientProgressVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final float left;
		private final float right;
		private final float start;
		private final float end;
		private final int markerRgb;
		private float vertexX;

		private GradientProgressVertexConsumer(VertexConsumer delegate, float left, float right,
				float start, float end, int markerRgb) {
			this.delegate = delegate;
			this.left = left;
			this.right = right;
			this.start = start;
			this.end = end;
			this.markerRgb = markerRgb;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			vertexX = x;
			delegate.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer addVertex(Matrix4fc pose, float x, float y, float z) {
			vertexX = x;
			delegate.addVertex(pose, x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int color) {
			int alpha = color >>> 24 & 0xFF;
			if (alpha == 0) alpha = 0xFF;
			return writeColor(alpha, (color & 0xFFFFFF) == markerRgb);
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			boolean foreground = ((red & 0xFF) << 16 | (green & 0xFF) << 8 | (blue & 0xFF)) == markerRgb;
			return writeColor(alpha, foreground);
		}

		private VertexConsumer writeColor(int alpha, boolean foreground) {
			float progress = progressAtLocalX(vertexX, left, right, start, end);
			int fixed = (int) Math.round(Math.max(0.0f, Math.min(1.0f, progress)) * 65535.0f);
			int high = (fixed >>> 8) & 0xFF;
			int low = fixed & 0xFF;
			delegate.setColor(high, low, foreground ? 0xFF : 0x00, alpha & 0xFF);
			return this;
		}

		@Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
		@Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
		@Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
		@Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
		@Override public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width); return this; }
	}
}

#version 330

in vec4 vertexColor;
in vec2 texCoord0;

layout(std140) uniform LysinasGradientUniforms {
    // x = phase [0, 1), y = sine-wave offset, z = ExtraConfig.Animation ordinal,
    // w = screen-space span in pixels (0 = follow the text).
    vec4 gradientParams;
    // Aaron chroma: x, y = ChromaSize-scaled screen size in pixels, z = saturation.
    vec4 aaronParams;
    vec4 gradientColors[256];
};

uniform sampler2D Sampler0;
out vec4 fragColor;

vec3 sampleGradient(float progress) {
    float scaled = clamp(progress, 0.0, 1.0) * 255.0;
    int index = min(int(floor(scaled)), 254);
    float fraction = scaled - float(index);
    return mix(gradientColors[index].rgb, gradientColors[index + 1].rgb, fraction);
}

float reflect01(float value) {
    float wrapped = mod(value, 2.0);
    return wrapped <= 1.0 ? wrapped : 2.0 - wrapped;
}

// Mirrors Gradient.progressAt; ordinals are STATIC, LEFT_TO_RIGHT, RIGHT_TO_LEFT, BOUNCE, FULL_TEXT_CYCLE, AARON_CHROMA (5, handled in main).
float animate(float base) {
    float phase = gradientParams.x;
    int mode = int(gradientParams.z + 0.5);
    float progress = base;
    if (mode == 1 || mode == 4) progress = base - phase;
    else if (mode == 2) progress = base + phase;
    else if (mode == 3) progress = base - (phase < 0.5 ? phase * 2.0 : 2.0 - phase * 2.0);
    progress += sin((base - phase) * 6.28318530718) * gradientParams.y;
    if (mode == 4) return fract(progress);
    if (mode == 3) return reflect01(progress);
    return clamp(progress, 0.0, 1.0);
}

// Aaron's Mod hsv2rgb_smooth.
vec3 hsvSmooth(float hue, float saturation, float value) {
    vec3 rgb = smoothstep(0.0, 1.0, abs(mod(hue * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0);
    return value * mix(vec3(1.0), rgb, saturation);
}

void main() {
    vec4 glyph = texture(Sampler0, texCoord0);
    float alpha = glyph.a * vertexColor.a;
    if (alpha < 0.0039) {
        discard;
    }

    // Red and green carry a 16-bit stationary progress value interpolated across each glyph quad.
    if (int(gradientParams.z + 0.5) == 5) {
        // AARON_CHROMA: identical to Aaron's Mod chroma.glsl; the drop shadow is the same hue at 1/4 brightness.
        vec2 uv = gl_FragCoord.xy / aaronParams.xy;
        float hue = fract(gradientParams.x + (uv.y - uv.x) * 1.75);
        fragColor = vec4(hsvSmooth(hue, aaronParams.z, vertexColor.b > 0.5 ? 1.0 : 0.25), alpha);
        return;
    }

    float progress = (vertexColor.r * 256.0 + vertexColor.g) / 257.0;
    // Screen-space chroma (Aaron's Mod style): diagonal from top-left to bottom-right, continuous across lines.
    // x - y (FragCoord y points up) grows rightwards and downwards, so "progress - phase" flows left to right
    // like the per-text mode and Aaron's chroma.
    if (gradientParams.w > 0.0) progress = (gl_FragCoord.x - gl_FragCoord.y) / gradientParams.w;
    // Blue is 1 for the glyph face and 0 for its vanilla dark drop-shadow pass.
    // Animating per fragment keeps the cycle seam sharp instead of smearing the whole palette across one glyph.
    vec3 color = sampleGradient(animate(progress)) * vertexColor.b;
    fragColor = vec4(color, alpha);
}

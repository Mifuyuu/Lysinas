package net.lysinas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Versioned compact text format for sharing gradient settings. */
public final class GradientShareCode {
	private static final String COMPACT_PREFIX = "EEC2:";
	private static final String LEGACY_PREFIX = "EEC1:";
	private static final String FULL_PREFIX = "EEC3:";
	private static final int MAX_CODE_LENGTH = 32_768;
	private static final int MAX_FULL_JSON_BYTES = 65_536;
	private static final int SCREEN_SPACE_FLAG = 0x20;
	private static final int SATURATION_FLAG = 0x40;
	private static final int MAX_COMPACT_PAYLOAD = 64;
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private GradientShareCode() {}

	/** Encodes 2–8 stops into a compact binary payload (normally about 30–75 chars). */
	public static String encode(ExtraConfig.MaxEnchantments settings) {
		validateSettings(settings);
		ByteArrayOutputStream payload = new ByteArrayOutputStream(48);
		int header = (settings.interpolation.ordinal() & 0x03) | ((settings.animation.ordinal() & 0x07) << 2)
				| (settings.chromaSize > 0.0f ? SCREEN_SPACE_FLAG : 0)
				| (settings.animation == ExtraConfig.Animation.AARON_CHROMA ? SATURATION_FLAG : 0);
		payload.write(header);
		writeVarInt(payload, (int) settings.animationPeriodMs);
		writeUnsignedShort(payload, Math.round(settings.animationSpeed * 1000.0f));
		writeUnsignedShort(payload, Math.round(settings.sineWaveOffset * 10_000.0f));
		if (settings.chromaSize > 0.0f) writeUnsignedShort(payload, Math.round(settings.chromaSize * 100.0f));
		if (settings.animation == ExtraConfig.Animation.AARON_CHROMA) payload.write(Math.round(settings.chromaSaturation * 255.0f));
		payload.write(settings.stops.size());

		List<ExtraConfig.GradientStop> stops = new ArrayList<>(settings.stops);
		stops.sort(Comparator.comparingDouble(stop -> stop.position));
		int previousPosition = -1;
		for (ExtraConfig.GradientStop stop : stops) {
			int position = Math.round(stop.position * 65_535.0f);
			if (position == previousPosition) {
				throw new IllegalArgumentException("Two stop positions are too close to encode distinctly.");
			}
			previousPosition = position;
			writeUnsignedShort(payload, position);
			int rgb = Gradient.parseColor(stop.color);
			payload.write((rgb >>> 16) & 0xFF);
			payload.write((rgb >>> 8) & 0xFF);
			payload.write(rgb & 0xFF);
		}

		byte[] raw = payload.toByteArray();
		int checksum = crc16(raw, 0, raw.length);
		payload.write((checksum >>> 8) & 0xFF);
		payload.write(checksum & 0xFF);
		String code = COMPACT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray());
		if (code.length() > 128) throw new IllegalArgumentException("Compact share code exceeded its size limit.");
		return code;
	}

	/** Encodes the good-enchantment colour, main gradient and every item profile as a deflated JSON code. */
	public static String encodeAll(ExtraConfig config) {
		validateSettings(config.maxEnchantments);
		for (ExtraConfig.ItemProfile profile : config.itemProfiles) validateSettings(profile.gradient);
		FullPayload payload = new FullPayload();
		payload.version = 3;
		payload.showGoodEnchantments = config.showGoodEnchantments;
		payload.goodEnchantmentColor = config.goodEnchantmentColor;
		payload.maxEnchantments = config.maxEnchantments;
		payload.itemProfiles = config.itemProfiles;
		byte[] json = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		deflater.setInput(json);
		deflater.finish();
		ByteArrayOutputStream output = new ByteArrayOutputStream(json.length / 2 + 16);
		byte[] chunk = new byte[1024];
		while (!deflater.finished()) output.write(chunk, 0, deflater.deflate(chunk));
		deflater.end();
		String code = FULL_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
		if (code.length() > MAX_CODE_LENGTH) throw new IllegalArgumentException("Settings are too large to share.");
		return code;
	}

	public static boolean isFullCode(String code) {
		return code != null && code.trim().startsWith(FULL_PREFIX);
	}

	/**
	 * Decodes an EEC3 code into a fresh config holding only the shared fields
	 * (good-enchantment toggle/colour, main gradient, item profiles); everything else keeps defaults.
	 */
	public static ExtraConfig decodeAll(String code) {
		if (!isFullCode(code)) throw new IllegalArgumentException("Not a full-settings code (expected EEC3:).");
		String normalized = code.trim();
		if (normalized.length() > MAX_CODE_LENGTH) throw new IllegalArgumentException("Share code is too long.");
		try {
			byte[] compressed = Base64.getUrlDecoder().decode(normalized.substring(FULL_PREFIX.length()));
			Inflater inflater = new Inflater();
			inflater.setInput(compressed);
			ByteArrayOutputStream json = new ByteArrayOutputStream(compressed.length * 4);
			byte[] chunk = new byte[1024];
			try {
				while (!inflater.finished()) {
					int read = inflater.inflate(chunk);
					if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
						throw new IllegalArgumentException("Share code is truncated.");
					}
					json.write(chunk, 0, read);
					// Guards against decompression bombs pasted from untrusted sources.
					if (json.size() > MAX_FULL_JSON_BYTES) throw new IllegalArgumentException("Share code payload is too large.");
				}
			} finally {
				inflater.end();
			}
			FullPayload payload = GSON.fromJson(json.toString(StandardCharsets.UTF_8), FullPayload.class);
			if (payload == null || payload.version != 3) throw new IllegalArgumentException("Unsupported full-settings code version.");
			if (!Gradient.isValidColor(payload.goodEnchantmentColor)) {
				throw new IllegalArgumentException("Good-enchantment colour is invalid.");
			}
			validateSettings(payload.maxEnchantments);
			if (payload.itemProfiles == null) payload.itemProfiles = new ArrayList<>();
			if (payload.itemProfiles.size() > 32) throw new IllegalArgumentException("A code may contain at most 32 profiles.");
			for (ExtraConfig.ItemProfile profile : payload.itemProfiles) {
				if (profile == null) throw new IllegalArgumentException("Share code contains an empty profile.");
				validateSettings(profile.gradient);
			}
			ExtraConfig result = new ExtraConfig();
			result.showGoodEnchantments = payload.showGoodEnchantments;
			result.goodEnchantmentColor = canonicalHex(payload.goodEnchantmentColor);
			result.maxEnchantments = payload.maxEnchantments;
			result.itemProfiles = payload.itemProfiles;
			result.normalize();
			return result;
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (RuntimeException | DataFormatException exception) {
			throw new IllegalArgumentException("Could not decode full-settings code.", exception);
		}
	}

	/** Accepts new compact EEC2 codes and existing EEC1 JSON codes. */
	public static ExtraConfig.MaxEnchantments decode(String code) {
		if (code == null) throw new IllegalArgumentException("Share code is empty.");
		String normalized = code.trim();
		if (normalized.length() > MAX_CODE_LENGTH) throw new IllegalArgumentException("Share code is too long.");
		if (normalized.startsWith(COMPACT_PREFIX)) return decodeCompact(normalized.substring(COMPACT_PREFIX.length()));
		if (normalized.startsWith(LEGACY_PREFIX)) return decodeLegacy(normalized.substring(LEGACY_PREFIX.length()));
		throw new IllegalArgumentException("Not an Lysinas share code (expected EEC2: or legacy EEC1:).");
	}

	private static ExtraConfig.MaxEnchantments decodeCompact(String encoded) {
		try {
			byte[] bytes = Base64.getUrlDecoder().decode(encoded);
			if (bytes.length < 19 || bytes.length > MAX_COMPACT_PAYLOAD) {
				throw new IllegalArgumentException("Compact gradient payload has an invalid size.");
			}
			int checksumOffset = bytes.length - 2;
			int expectedCrc = ((bytes[checksumOffset] & 0xFF) << 8) | (bytes[checksumOffset + 1] & 0xFF);
			if (crc16(bytes, 0, checksumOffset) != expectedCrc) {
				throw new IllegalArgumentException("Share code checksum failed; check for a typo.");
			}

			ByteBuffer input = ByteBuffer.wrap(bytes, 0, checksumOffset).order(ByteOrder.BIG_ENDIAN);
			int header = input.get() & 0xFF;
			if ((header & 0x80) != 0) throw new IllegalArgumentException("Unsupported compact gradient flags.");
			int interpolationIndex = header & 0x03;
			int animationIndex = (header >>> 2) & 0x07;
			if (interpolationIndex >= ExtraConfig.Interpolation.values().length
					|| animationIndex >= ExtraConfig.Animation.values().length) {
				throw new IllegalArgumentException("Unknown interpolation or animation in share code.");
			}

			long period = readVarInt(input);
			validatePeriod(period);
			int speedRaw = input.getShort() & 0xFFFF;
			int waveRaw = input.getShort() & 0xFFFF;
			float speed = speedRaw / 1000.0f;
			float wave = waveRaw / 10_000.0f;
			if (speed < 0.1f || speed > 5.0f || wave > 0.5f) {
				throw new IllegalArgumentException("Animation settings are outside the supported range.");
			}
			float chromaSize = 0.0f;
			if ((header & SCREEN_SPACE_FLAG) != 0) {
				chromaSize = (input.getShort() & 0xFFFF) / 100.0f;
				if (chromaSize < 1.0f || chromaSize > 200.0f) throw new IllegalArgumentException("Chroma size is outside 1-200.");
			}
			float saturation = (header & SATURATION_FLAG) != 0 ? (input.get() & 0xFF) / 255.0f : 0.75f;
			int count = input.get() & 0xFF;
			if (count < 2 || count > 8 || input.remaining() != count * 5) {
				throw new IllegalArgumentException("Gradient code must contain 2 to 8 complete stops.");
			}

			ExtraConfig.MaxEnchantments result = new ExtraConfig.MaxEnchantments();
			result.interpolation = ExtraConfig.Interpolation.values()[interpolationIndex];
			result.animation = ExtraConfig.Animation.values()[animationIndex];
			result.animationPeriodMs = period;
			result.animationSpeed = speed;
			result.sineWaveOffset = wave;
			result.chromaSize = chromaSize;
			result.chromaSaturation = saturation;
			List<ExtraConfig.GradientStop> stops = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				float position = (input.getShort() & 0xFFFF) / 65_535.0f;
				int red = input.get() & 0xFF;
				int green = input.get() & 0xFF;
					int blue = input.get() & 0xFF;
					String color = String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
					stops.add(new ExtraConfig.GradientStop(position, color));
				}
				stops.sort(Comparator.comparingDouble(stop -> stop.position));
				ensureUniquePositions(stops);
				result.stops = stops;
				return result;
			} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Could not decode compact gradient code.", exception);
		}
	}

	private static ExtraConfig.MaxEnchantments decodeLegacy(String encoded) {
		try {
			byte[] jsonBytes = Base64.getUrlDecoder().decode(encoded);
			if (jsonBytes.length > 3072) throw new IllegalArgumentException("Legacy share-code payload is too large.");
			LegacyPayload payload = GSON.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), LegacyPayload.class);
			if (payload == null || payload.version != 1) throw new IllegalArgumentException("Unsupported legacy share-code version.");
			if (payload.stops == null || payload.stops.size() < 2 || payload.stops.size() > 8) {
				throw new IllegalArgumentException("Gradient code must contain 2 to 8 stops.");
			}
			ExtraConfig.MaxEnchantments result = new ExtraConfig.MaxEnchantments();
			result.interpolation = ExtraConfig.Interpolation.valueOf(payload.interpolation);
			result.animation = ExtraConfig.Animation.valueOf(payload.animation);
			validatePeriod(payload.animationPeriodMs);
			result.animationPeriodMs = payload.animationPeriodMs;
			result.animationSpeed = payload.animationSpeed == null ? 1.0f : payload.animationSpeed;
			result.sineWaveOffset = payload.sineWaveOffset == null ? 0.0f : payload.sineWaveOffset;
			if (!Float.isFinite(result.animationSpeed) || result.animationSpeed < 0.1f || result.animationSpeed > 5.0f
					|| !Float.isFinite(result.sineWaveOffset) || result.sineWaveOffset < 0.0f || result.sineWaveOffset > 0.5f) {
				throw new IllegalArgumentException("Gradient animation values are outside the supported range.");
			}
			List<ExtraConfig.GradientStop> stops = new ArrayList<>(payload.stops.size());
			for (LegacyStop stop : payload.stops) {
				if (stop == null || !Float.isFinite(stop.position) || stop.position < 0.0f || stop.position > 1.0f
						|| !Gradient.isValidColor(stop.color)) {
					throw new IllegalArgumentException("Legacy gradient contains an invalid stop.");
				}
					stops.add(new ExtraConfig.GradientStop(stop.position, canonicalHex(stop.color)));
				}
			stops.sort(Comparator.comparingDouble(stop -> stop.position));
			ensureUniquePositions(stops);
			result.stops = stops;
			return result;
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Could not decode legacy gradient code.", exception);
		}
	}

	private static void validateSettings(ExtraConfig.MaxEnchantments settings) {
		if (settings == null || settings.stops == null || settings.stops.size() < 2 || settings.stops.size() > 8) {
			throw new IllegalArgumentException("Gradient must contain between 2 and 8 stops.");
		}
		validatePeriod(settings.animationPeriodMs);
		if (settings.interpolation == null || settings.animation == null) {
			throw new IllegalArgumentException("Gradient mode is missing.");
		}
		if (!Float.isFinite(settings.animationSpeed) || settings.animationSpeed < 0.1f || settings.animationSpeed > 5.0f
				|| !Float.isFinite(settings.sineWaveOffset) || settings.sineWaveOffset < 0.0f || settings.sineWaveOffset > 0.5f) {
			throw new IllegalArgumentException("Gradient animation values are outside the supported range.");
		}
		if (!Float.isFinite(settings.chromaSize) || settings.chromaSize != 0.0f
				&& (settings.chromaSize < 1.0f || settings.chromaSize > 200.0f)) {
			throw new IllegalArgumentException("Chroma size must be 0 (off) or between 1 and 200.");
		}
		if (!Float.isFinite(settings.chromaSaturation) || settings.chromaSaturation < 0.0f || settings.chromaSaturation > 1.0f) {
			throw new IllegalArgumentException("Chroma saturation must be between 0 and 1.");
		}
		for (ExtraConfig.GradientStop stop : settings.stops) {
			if (stop == null || !Float.isFinite(stop.position) || stop.position < 0.0f || stop.position > 1.0f
					|| !Gradient.isValidColor(stop.color)) {
				throw new IllegalArgumentException("Gradient contains an invalid stop.");
			}
		}
	}

	private static void writeVarInt(ByteArrayOutputStream output, int value) {
		while ((value & 0xFFFFFF80) != 0) {
			output.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		output.write(value & 0x7F);
	}

	private static long readVarInt(ByteBuffer input) {
		long result = 0L;
		for (int shift = 0; shift < 35; shift += 7) {
			if (!input.hasRemaining()) throw new IllegalArgumentException("Truncated period value.");
			int value = input.get() & 0xFF;
			result |= (long) (value & 0x7F) << shift;
			if ((value & 0x80) == 0) return result;
		}
		throw new IllegalArgumentException("Period varint is too long.");
	}

	private static void writeUnsignedShort(ByteArrayOutputStream output, int value) {
		output.write((value >>> 8) & 0xFF);
		output.write(value & 0xFF);
	}

	private static int crc16(byte[] data, int offset, int length) {
		int crc = 0xFFFF;
		for (int i = offset; i < offset + length; i++) {
			crc ^= (data[i] & 0xFF) << 8;
			for (int bit = 0; bit < 8; bit++) crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
		}
		return crc;
	}

	private static void validatePeriod(long period) {
		if (period < 50L || period > 86_400_000L) {
			throw new IllegalArgumentException("Animation period must be between 50 and 86400000 ms.");
		}
	}

	private static void ensureUniquePositions(List<ExtraConfig.GradientStop> stops) {
		for (int i = 1; i < stops.size(); i++) {
			if (stops.get(i - 1).position == stops.get(i).position) {
				throw new IllegalArgumentException("Gradient stop positions must be unique.");
			}
		}
	}

	private static String canonicalHex(String color) {
		return String.format(Locale.ROOT, "#%06X", Gradient.parseColor(color));
	}

	private static final class FullPayload {
		int version;
		boolean showGoodEnchantments;
		String goodEnchantmentColor;
		ExtraConfig.MaxEnchantments maxEnchantments;
		List<ExtraConfig.ItemProfile> itemProfiles;
	}

	private static final class LegacyPayload {
		int version;
		String interpolation;
		String animation;
		long animationPeriodMs;
		Float animationSpeed;
		Float sineWaveOffset;
		List<LegacyStop> stops;
	}

	private static final class LegacyStop {
		float position;
		String color;
	}
}

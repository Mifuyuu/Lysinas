package net.lysinas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/** Minecraft Bedrock (Ore UI) style button: black outline, flat face, darker 2px bottom lip, no vanilla sprite. */
final class BedrockButton extends Button {
	enum Kind { SECONDARY, PRIMARY, TAB, TOGGLE, DROPDOWN }

	private final Kind kind;
	private final ItemStack icon;
	/** Square PNG icon drawn at 16x16 instead of an item; null when the button uses an item or no icon. */
	private Identifier iconTexture;
	private int iconTextureSize;
	/** Selected tab, or the on-state of a toggle. */
	private BooleanSupplier state = () -> false;

	BedrockButton(Component message, OnPress onPress) {
		this(message, Kind.SECONDARY, ItemStack.EMPTY, onPress);
	}

	BedrockButton(Component message, Kind kind, OnPress onPress) {
		this(message, kind, ItemStack.EMPTY, onPress);
	}

	BedrockButton(Component message, Kind kind, ItemStack icon, OnPress onPress) {
		super(0, 0, 20, 20, message, onPress, DEFAULT_NARRATION);
		this.kind = kind;
		this.icon = icon;
	}

	BedrockButton iconTexture(Identifier texture, int size) {
		iconTexture = texture;
		iconTextureSize = size;
		return this;
	}

	BedrockButton state(BooleanSupplier value) {
		state = value;
		return this;
	}

	boolean isToggle() {
		return kind == Kind.TOGGLE;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		if (kind == Kind.TOGGLE) {
			extractToggle(graphics, x, y, w, h);
			return;
		}
		boolean primary = kind == Kind.PRIMARY || kind == Kind.TAB && state.getAsBoolean();
		boolean dark = kind == Kind.TAB && !primary;
		int face;
		int light;
		int lip;
		int text;
		if (!active) {
			face = 0xFF5A5B5E; light = 0xFF6A6B6E; lip = 0xFF3A3B3D; text = 0xFF8C8D90;
		} else if (primary) {
			face = isHovered() ? 0xFF2A641C : 0xFF3C8527; light = 0xFF4FA236; lip = 0xFF1D4D13; text = 0xFFFFFFFF;
		} else if (dark) {
			face = isHovered() ? 0xFF5A5B5C : 0xFF48494A; light = 0xFF6D6E70; lip = 0xFF2B2B2C; text = 0xFFFFFFFF;
		} else {
			face = isHovered() ? 0xFFB1B2B5 : 0xFFD0D1D4; light = 0xFFECEDEE; lip = 0xFF58585A; text = 0xFF1E1E1F;
		}
		bevel(graphics, x, y, w, h, face, light, lip);

		Font font = Minecraft.getInstance().font;
		int contentY = y + (h - 2 - 8) / 2;
		if (iconTexture != null || !icon.isEmpty()) {
			int iconY = y + (h - 2 - 16) / 2;
			if (iconTexture != null) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, iconTexture, x + 3, iconY, 0.0f, 0.0f, 16, 16,
						iconTextureSize, iconTextureSize, iconTextureSize, iconTextureSize);
			} else {
				graphics.fakeItem(icon, x + 3, iconY);
			}
			graphics.text(font, getMessage(), x + 23, contentY, text, false);
			return;
		}
		if (kind == Kind.DROPDOWN) {
			// Left-aligned value with a pixel chevron at the right edge, like Bedrock's dropdowns.
			graphics.text(font, font.plainSubstrByWidth(getMessage().getString(), w - 22), x + 6, contentY, text, false);
			// Chevron points down when closed and up while the list is open.
			boolean open = state.getAsBoolean();
			int chevronX = x + w - 13;
			int chevronY = y + (h - 2 - 4) / 2;
			for (int i = 0; i < 4; i++) {
				int rowY = chevronY + (open ? 3 - i : i);
				graphics.fill(chevronX + i, rowY, chevronX + i + 1, rowY + 1, text);
				graphics.fill(chevronX + 6 - i, rowY, chevronX + 7 - i, rowY + 1, text);
			}
			return;
		}
		graphics.text(font, getMessage(), x + (w - font.width(getMessage())) / 2, contentY, text, false);
	}

	/** Bedrock switch: green track with an "I" mark when on, grey with an "O" mark when off, knob slides across. */
	private void extractToggle(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		boolean on = state.getAsBoolean();
		int track = on ? (isHovered() ? 0xFF2A641C : 0xFF3C8527) : (isHovered() ? 0xFF6D6E70 : 0xFF5A5B5E);
		int light = on ? 0xFF4FA236 : 0xFF7C7D80;
		int knob = h;
		// Track is inset 2px at the top and at the end away from the knob, so the knob stands proud of it.
		// It runs 2px under the knob, so the knob's black edge hides the highlight on that side (no light next to the knob).
		int top = y + 2;
		int left = on ? x + 2 : x + knob - 2;
		int right = on ? x + w - knob + 2 : x + w - 2;
		graphics.fill(left, top, right, y + h, 0xFF000000);
		graphics.fill(left + 1, top + 1, right - 1, y + h - 1, light);
		graphics.fill(left + 2, top + 2, right - 2, y + h - 2, track);
		// The I sits 1px off-centre, away from the knob; the O stays centred.
		int centerX = (left + right) / 2 + (on ? -1 : 0);
		int centerY = top + (y + h - top) / 2;
		if (on) {
			graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 3, 0xFFFFFFFF);
		} else {
			// 6x6 ring with the corner pixels left out so the O reads round.
			int markX = centerX - 3;
			int markY = centerY - 3;
			int mark = 0xFFD0D1D4;
			graphics.fill(markX + 1, markY, markX + 5, markY + 1, mark);
			graphics.fill(markX + 1, markY + 5, markX + 5, markY + 6, mark);
			graphics.fill(markX, markY + 1, markX + 1, markY + 5, mark);
			graphics.fill(markX + 5, markY + 1, markX + 6, markY + 5, mark);
		}
		bevel(graphics, on ? x + w - knob : x, y, knob, h, 0xFFD0D1D4, 0xFFFFFFFF, 0xFF58585A);
	}

	static void bevel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int face, int light, int lip) {
		graphics.fill(x, y, x + w, y + h, 0xFF000000);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, lip);
		// 1px highlight ring around the whole face, its bottom edge sitting directly on the darker lip.
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 3, light);
		graphics.fill(x + 2, y + 2, x + w - 2, y + h - 4, face);
	}
}

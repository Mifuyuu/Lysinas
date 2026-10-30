package dev.ikmcw.lysinas.mixin;

import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph$GlyphInstance")
public interface GlyphInstanceAccessor {
	@Accessor("style")
	Style lysinas$getStyle();

	@Invoker("x")
	float lysinas$getX();

	@Invoker("activeRight")
	float lysinas$getActiveRight();
}

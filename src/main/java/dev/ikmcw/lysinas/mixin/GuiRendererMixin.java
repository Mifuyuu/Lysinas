package dev.ikmcw.lysinas.mixin;

import dev.ikmcw.lysinas.gradient.GradientShaderRenderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
	@Redirect(
			method = "addElementToMesh",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;pipeline()Lcom/mojang/blaze3d/pipeline/RenderPipeline;")
	)
	private RenderPipeline lysinas$selectGlyphPipeline(GuiElementRenderState state) {
		if (state instanceof GlyphRenderState glyph) {
			int profileSlot = GradientShaderRenderer.paletteIndex(glyph);
			if (profileSlot >= 0) return GradientShaderRenderer.pipeline(profileSlot);
		}
		return state.pipeline();
	}

	@Redirect(
			method = "addElementToMesh",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;buildVertices(Lcom/mojang/blaze3d/vertex/VertexConsumer;)V")
	)
	private void lysinas$writeProgressVertices(GuiElementRenderState state, VertexConsumer vertices) {
		if (state instanceof GlyphRenderState glyph && GradientShaderRenderer.isMarked(glyph)) {
			state.buildVertices(GradientShaderRenderer.wrapVertices(glyph, vertices));
		} else {
			state.buildVertices(vertices);
		}
	}

	@Redirect(
			method = "executeDraw",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V")
	)
	private void lysinas$bindGradientUniform(RenderPass renderPass, RenderPipeline pipeline) {
		renderPass.setPipeline(pipeline);
		int profileSlot = GradientShaderRenderer.paletteIndexForPipeline(pipeline);
		if (profileSlot >= 0) {
			renderPass.setUniform(GradientShaderRenderer.UNIFORM_NAME, GradientShaderRenderer.uniformSlice(profileSlot));
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void lysinas$endGradientUniformFrame(CallbackInfo ci) {
		GradientShaderRenderer.endFrame();
	}
}

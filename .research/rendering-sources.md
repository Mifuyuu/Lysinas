# Rendering research references

- Fabric 26.2 rendering concepts: https://docs.fabricmc.net/develop/rendering/basic-concepts — use the Blaze3D abstraction, not raw OpenGL; describes GUI extraction/render phases.
- Fabric 26.2 custom pipelines: https://docs.fabricmc.net/develop/rendering/world — custom pipeline setup and render pass.
- Minecraft 26.2 text preparation: https://docs.neoforged.net/primer/docs/26.2/ — Font now prepares text/glyph renderables; GUI rendering consumes glyph states.
- SkyHanni beta renderer hook: https://github.com/SkyHanniStudios/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/mixins/hooks/GuiRendererHook.kt — marker style inspection and pipeline replacement for GlyphRenderState.
- SkyHanni beta GUI renderer Mixin: https://github.com/SkyHanniStudios/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/mixins/transformers/MixinGuiRenderer.java — hooks addElementToMesh and binds chroma UBO during GUI draws.
- SkyHanni beta custom text pipeline: https://github.com/SkyHanniStudios/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/utils/render/SkyHanniRenderPipeline.kt — Position/UV/Color format, glyph texture sampler, uniform bind group.
- SkyHanni beta chroma fragment shader: https://github.com/SkyHanniStudios/SkyHanni/blob/beta/src/main/resources/assets/skyhanni/shaders/textured_chroma.fsh — fragment-space chroma while preserving glyph texture alpha.
- SkyHanni beta chroma vertex shader: https://github.com/SkyHanniStudios/SkyHanni/blob/beta/src/main/resources/assets/skyhanni/shaders/textured_chroma.vsh — passes glyph UV/color through pipeline.

# Lysinas

`Lysinas` is a client-side Fabric mod that recolours max-level Hypixel SkyBlock enchantments in tooltips. It works independently of Aaron's Mod and adds customisable multi-stop gradients with animation, following the same idea as [Birdflop Animated TAB](https://www.birdflop.com/resources/animtab/).

## Features

- Recolour max-level enchantments with a multi-stop gradient
- Set the colour and position of each stop yourself, 2–8 stops
- Shader-based glyph gradient that interpolates colour inside each character, not just per glyph
- In-game ClickGUI for editing colour stops, interpolation, animation and the main toggles
- Gradient profiles per item registry/Hypixel ID and enchantment ID, with priority ordering
- Switch the shader preview/runtime between `STATIC` and `ANIMATED` instantly from the ClickGUI, no restart needed
- Live gradient preview that updates while you type positions/colours and speed/wave values
- Ready-made gradient presets: Rainbow, Sunset and Ocean
- Share/import gradients with short `EEC2:` codes with a checksum; older `EEC1:` codes still import
- Client-side slash commands to list, switch and reset profile mode without opening the ClickGUI
- In-game benchmark comparing the shader glyph path with the vanilla text path, exporting frame times as CSV
- `RGB`, `HSL`, `HSV` or `SMOOTH_HSV` interpolation (HSV with cubic smoothing like Aaron's Mod)
- Animation is computed in the shader every frame, so it stays smooth even when the tooltip is not rebuilt
- Screen chroma (`chromaSize` 1–200, 0 = off): a diagonal gradient based on screen position like Aaron's Mod, continuous across lines (works best with `FULL_TEXT_CYCLE`)
- `AARON_CHROMA` mode (the **Aaron** preset): the exact same formula as Aaron's Mod chroma text (size 100, saturation 0.75, speed 4 = 4.5 seconds per cycle), with adjustable size and saturation
- Share everything with an `EEC3:` code (good enchant colour + main gradient + all item profiles)
- `STATIC`, `LEFT_TO_RIGHT`, `RIGHT_TO_LEFT`, `BOUNCE` and `FULL_TEXT_CYCLE` animations
- Adjust animation speed `0.1×–5.0×` and sine-wave offset `0–0.5` in the ClickGUI
- Recolour good-level enchantments with a single colour
- Enchantment thresholds are read from a file bundled with the mod, so Aaron's Mod's API is never called at runtime
- Restrict the mod to Hypixel with `onlyOnHypixel`

## Installing and building

This project targets Minecraft `26.2`, Fabric Loader `0.19.3`, Fabric API `0.157.0+26.2` and Java 25, matching the latest upstream sources checked.

```bash
./gradlew build
```

The mod file will be at `build/libs/lysinas-1.5.0+26.2.jar`. Put it in your `mods` folder together with Fabric API.

## Configuration

Launch the game once so the mod creates `config/lysinas.json`, then edit it like this:

```json
{
  "enabled": true,
  "onlyOnHypixel": true,
  "showGoodEnchantments": true,
  "goodEnchantmentColor": "#FFAA00",
  "shaderAnimationMode": "ANIMATED",
  "activeProfile": "",
  "itemProfiles": [],
  "maxEnchantments": {
    "animation": "FULL_TEXT_CYCLE",
    "animationPeriodMs": 1800,
    "animationSpeed": 1.0,
    "sineWaveOffset": 0.0,
    "interpolation": "HSV",
    "stops": [
      { "position": 0.0, "color": "#FF0000" },
      { "position": 0.1667, "color": "#FFFF00" },
      { "position": 0.3333, "color": "#00FF00" },
      { "position": 0.5, "color": "#00FFFF" },
      { "position": 0.6667, "color": "#0000FF" },
      { "position": 0.8333, "color": "#FF00FF" },
      { "position": 1.0, "color": "#FF0000" }
    ]
  }
}
```

You can edit `stops` in `config/lysinas.json` directly: add/remove stops within 2–8, set `position` from `0.0` to `1.0`, and write colours as `#RRGGBB` or `0xRRGGBB`. `HSV` walks the hue wheel like Aaron's Mod's HSB; `HSL` and `RGB` are still available.

Open the in-game settings with the `O` key (rebind it under Options → Controls → Lysinas). The screen has a live preview that follows stop/period/speed/wave changes as you type, lets you add/remove/select stops, choose interpolation/animation and set the good enchantment colour. The `Shader: STATIC/ANIMATED` button toggles the motion of the displayed gradient instantly without changing the pipeline or restarting; press Save to keep the mode or Cancel to revert it. `animationSpeed` ranges from `0.1` to `5.0`, where `1.0` is normal speed. `sineWaveOffset` ranges from `0` to `0.5` and is the amplitude as a fraction of the gradient; `0` disables the wave and higher values make the colours swing more. One wave cycle spans the text length and moves with the animation speed. Save writes the values to `config/lysinas.json`; Cancel discards unsaved changes. Click a preset to replace the gradient in the draft, then press Save to keep it.

The **Item Profiles** button opens a manager for up to 32 profiles. Enter `Item IDs` as item registry IDs such as `minecraft:diamond_sword` or Hypixel SkyBlock custom IDs from `CUSTOM_DATA.id`/`CUSTOM_DATA.ExtraAttributes.id` such as `ASPECT_OF_THE_DRAGON`; enter `Enchantment IDs` from the keys in `CUSTOM_DATA.enchantments` such as `sharpness`. IDs within a group match with OR; if both item IDs and enchantment IDs are filled in, both groups must match (AND), which is useful for colouring a specific enchantment on a specific item. The topmost matching profile wins, and the Up/Down buttons set priority. Each profile edits stops in the form `0:#123456, 1:#ABCDEF`, adjusts interpolation/animation/period/speed/wave, and can use a preset or copy the main gradient. Press **Apply to editor**, then **Save** on the main screen to save everything; Cancel discards the profile screen's draft.

There are 3 ways to open the ClickGUI: press `O`, type `/lysinas`, or press Configure in Mod Menu (if installed).

Client-side commands work from chat without opening the ClickGUI: `/lysinas profile list` lists profiles, `/lysinas profile use <name>` forces that profile on all enchantment tooltips, `/lysinas profile current` shows the current mode, and `/lysinas profile auto` goes back to choosing by item/enchantment selectors. Profile names with spaces can be typed in full after `use`, e.g. `/lysinas profile use My Ocean`. The chosen mode is saved to the config and takes effect immediately; AUTO is the default.

The **Share / Import Code** button exports the gradient as a short `EEC2:` code in binary + Base64URL (typically about 30–75 characters depending on the number of stops) with a CRC-16 to catch typos; stop positions use 16-bit precision and RGB is stored in full 24-bit. Press Export then Copy to share, or paste a code and press Import. JSON-based `EEC1:` codes from earlier versions still import. A code contains only stops, interpolation, animation, period, speed and sine-wave offset, not server toggles, profile selectors or the good enchantment colour. Importing changes the settings screen temporarily until you press Save.

The **Benchmark** button in the ClickGUI runs a simulated tooltip text workload through the real GUI glyph renderer: it measures shader-marked text against vanilla text, warms up before sampling, and writes results to `config/lysinas-benchmark.csv`. Workloads of 16/32/64/128 tooltip groups per frame are available; each group has 3 lines. Summarise the results from the project root with:

```bash
python3 tools/summarize_tooltip_benchmark.py /path/to/config/lysinas-benchmark.csv
```

The report includes mean, median, P95, P99, average FPS and overhead relative to vanilla. These are Minecraft frame times including CPU scheduling and any GPU sync that occurs, not direct GPU timers; test in the same environment with the same VSync/FPS cap to compare.

The default is a closed rainbow loop whose first and last stops are both red, so `FULL_TEXT_CYCLE` does not jump from the end colour back to the start. If you set your own colours and use `FULL_TEXT_CYCLE`, make the first and last stop colours the same for a seamless loop; if you want two different end colours, use `STATIC` or `BOUNCE`. Changing `shaderAnimationMode` directly in the JSON file still requires a restart; changing it through the ClickGUI takes effect immediately.

Only max-level enchantments use the custom GUI render pipeline and shader: progress is passed through the vertex colour and a colour-stop LUT is sampled in the fragment shader, so the gradient flows continuously within glyphs and across character boundaries. Pipelines/LUTs are cached per profile actually in use (up to 33 palette slots including the default) so profiles never mix colours. The X position uses glyph coordinates before the matrix transform, relative to the layout range `[x, activeRight]`, which already contains the glyph start position exactly once; `GlyphRenderState.bounds()` is not used because Minecraft 26.2 returns `null` for glyphs. Good-level enchantments still use the single `goodEnchantmentColor`. Minecraft 26.2 offers a Vulkan render backend; this implementation uses Blaze3D `RenderPipeline` instead of raw OpenGL, but it should still be tested with different GPU drivers and mods that change GUI rendering.

`animationPeriodMs` is the base cycle time of the animation; lower is faster. `animationSpeed` multiplies this rate without changing the period. Gradients/LUTs are cached per palette slot, bounded at 33 slots; `DynamicUniformStorage` uses a ring buffer that resets every frame and keeps old buffers from resizes temporarily before closing them in `endFrame`; a callback closes the storage on client shutdown, so there is no path for unbounded GPU buffer growth from continuous animation. There is still a transient per-glyph allocation in the vertex wrapper and per-tooltip text building, which can create GC pressure with very large workloads, but it is not a retained, ever-growing leak. To test on other servers, set `onlyOnHypixel` to `false`; items must carry Hypixel SkyBlock-style custom data.

If Aaron's Mod is installed at the same time, disable its Rainbow Max Enchants feature so tooltips are not coloured twice.

## Data scope and attribution

This mod keeps only the SkyBlock enchantment behaviour of the checked version of Aaron's Mod, reimplemented in the `dev.ikmcw.lysinas` namespace, and bundles a snapshot of the enchantment thresholds in `src/main/resources/lysinas/enchantments.json`. No classes or other dependencies of Aaron's Mod are bundled.

Birdflop is used only as a reference for the idea of colour stops and animation; no Birdflop source code was copied.

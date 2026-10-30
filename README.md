# Lysinas

`Lysinas` เป็น Fabric client-side mod สำหรับเปลี่ยนสี enchantment ระดับ max ของ Hypixel SkyBlock ใน tooltip โดยแยกการทำงานออกจาก Aaron's Mod และเพิ่ม gradient ที่กำหนดเองได้หลายจุด พร้อม animation แบบเดียวกับแนวคิดใน [Birdflop Animated TAB](https://www.birdflop.com/resources/animtab/)

## ความสามารถ

- เปลี่ยนสี enchantment ระดับ max เป็น gradient หลาย color stops
- กำหนดสีและตำแหน่งของแต่ละ stop ได้เอง 2–8 จุด
- shader-based glyph gradient ที่ interpolate สีภายในตัวอักษร ไม่ได้เปลี่ยนสีแค่ต่อ glyph
- clickgui ในเกมสำหรับแก้ color stops, interpolation, animation และ toggle หลัก
- โปรไฟล์ Gradient แยกตาม item registry/Hypixel IDs และ enchantment IDs พร้อมจัดลำดับความสำคัญ
- สลับ shader preview/runtime ระหว่าง `STATIC` และ `ANIMATED` ได้ทันทีจาก ClickGUI โดยไม่ restart
- live gradient preview อัปเดตระหว่างพิมพ์ตำแหน่ง/สีและค่าความเร็ว/คลื่น
- preset gradient สำเร็จรูป Rainbow, Sunset และ Ocean
- แชร์/นำเข้า Gradient ด้วยรหัส `EEC2:` แบบสั้นพร้อม checksum และยัง import `EEC1:` รุ่นก่อนหน้าได้
- Slash commands client-side สำหรับดูรายการและสลับ/คืนโหมดโปรไฟล์โดยไม่เปิด ClickGUI
- benchmark ในเกมเปรียบเทียบ shader glyph path กับ vanilla text path และส่งออก frame-time เป็น CSV
- interpolation แบบ `RGB`, `HSL`, `HSV` หรือ `SMOOTH_HSV` (HSV ที่ทำ cubic smoothing แบบ Aaron's Mod)
- animation คำนวณใน shader ทุกเฟรม จึงลื่นแม้ tooltip ไม่ถูกสร้างใหม่
- Screen chroma (`chromaSize` 1–200, 0 = ปิด): gradient แนวทแยงตามตำแหน่งบนจอแบบ Aaron's Mod ต่อเนื่องข้ามบรรทัด (เหมาะกับ `FULL_TEXT_CYCLE`)
- โหมด `AARON_CHROMA` (ปุ่ม preset **Aaron**): สูตรเดียวกับ chroma text ของ Aaron's Mod ทุกจุด (size 100, saturation 0.75, speed 4 = 4.5 วินาที/รอบ) ปรับ size และ saturation ได้
- แชร์ทุกอย่างด้วยรหัส `EEC3:` (สี good enchant + gradient หลัก + item profiles ทั้งหมด)
- animation แบบ `STATIC`, `LEFT_TO_RIGHT`, `RIGHT_TO_LEFT`, `BOUNCE` และ `FULL_TEXT_CYCLE`
- ปรับ animation speed `0.1×–5.0×` และ sine-wave offset `0–0.5` ใน ClickGUI
- เปลี่ยนสี enchantment ระดับ good เป็นสีเดี่ยวได้
- อ่าน threshold ข้อมูล enchantment จากไฟล์ที่ bundle มากับ mod จึงไม่เรียก API ของ Aaron's Mod ตอน runtime
- จำกัดการทำงานไว้ที่ Hypixel ได้ด้วย `onlyOnHypixel`

## ติดตั้งและ build

โปรเจกต์นี้ target Minecraft `26.2`, Fabric Loader `0.19.3`, Fabric API `0.157.0+26.2` และ Java 25 ตาม source รุ่นล่าสุดที่ตรวจสอบจากต้นทาง

```bash
./gradlew build
```

ไฟล์ mod จะอยู่ที่ `build/libs/lysinas-1.4.0+26.2.jar` จากนั้นนำไปใส่ในโฟลเดอร์ `mods` พร้อม Fabric API

## ตั้งค่า

เปิดเกมครั้งแรกเพื่อให้ mod สร้างไฟล์ `config/lysinas.json` แล้วแก้ไฟล์ดังนี้:

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

ผู้ใช้แก้ `stops` ใน `config/lysinas.json` ได้โดยตรง: เพิ่ม/ลบ stop ภายในช่วง 2–8 จุด, ตั้ง `position` จาก `0.0` ถึง `1.0`, และใส่สีเป็น `#RRGGBB` หรือ `0xRRGGBB`. `HSV` เดิน hue ตามวงสีแบบ HSB ของ Aaron's Mod; `HSL` และ `RGB` ยังเลือกใช้ได้

เปิดหน้าตั้งค่าในเกมได้ด้วยปุ่ม `O` (เปลี่ยนปุ่มได้ที่ Options → Controls → Lysinas). หน้าจอมี live preview ที่เปลี่ยนตาม stop/period/speed/wave ระหว่างพิมพ์, เพิ่ม/ลบ/เลือก stop, เลือก interpolation/animation และตั้งสี good enchantments. ปุ่ม `Shader: STATIC/ANIMATED` สลับการเคลื่อนไหวของ gradient ที่กำลังแสดงทันที โดยไม่เปลี่ยน pipeline และไม่ต้อง restart; กด Save เพื่อบันทึกโหมดหรือ Cancel เพื่อคืนโหมดเดิม. `animationSpeed` ใช้ช่วง `0.1` ถึง `5.0` โดย `1.0` คือความเร็วปกติ. `sineWaveOffset` ใช้ช่วง `0` ถึง `0.5` เป็น amplitude ในสัดส่วนของ gradient; `0` ปิดคลื่น, ค่าสูงขึ้นเพิ่มการแกว่งของสี. คลื่นหนึ่งรอบพาดตามความยาวข้อความและเคลื่อนด้วย animation speed. ปุ่ม Save เขียนค่าใน `config/lysinas.json`; Cancel ทิ้งการแก้ไขที่ยังไม่บันทึก. คลิก preset เพื่อแทน gradient ใน draft แล้วกด Save เพื่อเก็บ

ปุ่ม **Item Profiles** เปิดตัวจัดการโปรไฟล์ได้สูงสุด 32 รายการ. ใส่ `Item IDs` เป็น item registry ID เช่น `minecraft:diamond_sword` หรือ Hypixel SkyBlock custom ID จาก `CUSTOM_DATA.id`/`CUSTOM_DATA.ExtraAttributes.id` เช่น `ASPECT_OF_THE_DRAGON`; ใส่ `Enchantment IDs` จาก key ใน `CUSTOM_DATA.enchantments` เช่น `sharpness`. ภายในกลุ่ม ID เดียวกันจับคู่แบบ OR; ถ้ากรอกทั้ง item IDs และ enchantment IDs ต้องตรงทั้งสองกลุ่ม (AND) จึงเหมาะกับการกำหนดสีให้ enchantment เฉพาะบน item เฉพาะ. รายการบนสุดที่ match จะชนะ และปุ่ม Up/Down ใช้จัด priority. แต่ละโปรไฟล์แก้ stops ด้วยรูปแบบ `0:#123456, 1:#ABCDEF`, ปรับ interpolation/animation/period/speed/wave, ใช้ preset หรือคัดลอก gradient หลักได้. กด **Apply to editor** แล้วกด **Save** ในหน้าหลักเพื่อบันทึกทั้งหมด; Cancel จะทิ้ง draft ของหน้าโปรไฟล์

เปิด ClickGUI ได้ 3 ทาง: กดปุ่ม `O`, พิมพ์ `/lysinas` หรือกดปุ่ม Configure ใน Mod Menu (ถ้าติดตั้งไว้)

คำสั่ง client-side ใช้ได้จาก chat โดยไม่ต้องเปิด ClickGUI: `/lysinas profile list` แสดงโปรไฟล์, `/lysinas profile use <ชื่อ>` บังคับใช้โปรไฟล์นั้นกับ tooltip enchantment ทั้งหมด, `/lysinas profile current` ดูโหมดปัจจุบัน และ `/lysinas profile auto` กลับไปเลือกตาม item/enchantment selectors. ชื่อโปรไฟล์ที่มีเว้นวรรคใส่ได้ทั้งชื่อหลัง `use` เช่น `/lysinas profile use My Ocean`. โหมดที่เลือกบันทึกลง config และมีผลทันที; โหมด AUTO คือค่าปกติ

ปุ่ม **Share / Import Code** ส่งออก gradient เป็น `EEC2:` รหัสสั้นแบบ binary + Base64URL (โดยทั่วไปประมาณ 30–75 ตัวอักษรตามจำนวน stops) พร้อม CRC-16 ช่วยตรวจจับการพิมพ์ผิด; ตำแหน่ง stop ใช้ความละเอียด 16-bit และ RGB เก็บครบ 24-bit. กด Export แล้ว Copy เพื่อแชร์ หรือวางรหัสแล้วกด Import. ยังนำเข้ารหัส `EEC1:` แบบ JSON จากเวอร์ชันก่อนหน้าได้. Code ประกอบด้วย stops, interpolation, animation, period, speed และ sine-wave offset เท่านั้น ไม่รวม server toggles, profile selectors หรือสี good enchantment. การ import เปลี่ยนค่าในหน้าตั้งค่าชั่วคราวจนกว่าจะกด Save

ปุ่ม **Benchmark** ใน ClickGUI รัน workload ข้อความ tooltip จำลองผ่าน GUI glyph renderer จริง: วัด shader-marked text เทียบกับ vanilla text, warm-up ก่อนเก็บตัวอย่าง และเขียนผลที่ `config/lysinas-benchmark.csv`. เลือก workload ได้ 16/32/64/128 tooltip groups ต่อเฟรม; หนึ่งกลุ่มมี 3 บรรทัด. จาก root โปรเจกต์สรุปผลด้วย:

```bash
python3 tools/summarize_tooltip_benchmark.py /path/to/config/lysinas-benchmark.csv
```

รายงานมี mean, median, P95, P99, FPS เฉลี่ย และ overhead เทียบ vanilla. ค่าเป็น frame-time ของ Minecraft รวมผลจาก CPU scheduling และการ sync กับ GPU ที่เกิดขึ้น ไม่ใช่ GPU timer โดยตรง; ให้ทดสอบในสภาพแวดล้อมเดียวกันและใช้ VSync/FPS cap แบบเดิมเพื่อเปรียบเทียบ

ค่าเริ่มต้นเป็น rainbow วงปิด โดย stop แรกและสุดท้ายเป็นสีแดงเหมือนกัน เพื่อไม่ให้ `FULL_TEXT_CYCLE` กระโดดจากสีปลายกลับไปสีต้น หากปรับสีเองและใช้ `FULL_TEXT_CYCLE` ให้ตั้งสี stop แรก/สุดท้ายให้เหมือนกันเพื่อให้ loop ต่อเนื่อง; หากต้องการปลายสองสีต่างกันให้ใช้ `STATIC` หรือ `BOUNCE`. การแก้ `shaderAnimationMode` จากไฟล์ JSON โดยตรงยังต้อง restart; เปลี่ยนผ่าน ClickGUI ได้ทันที

เฉพาะ max-level enchantments ใช้ custom GUI render pipeline และ shader โดยส่ง progress ผ่าน vertex color แล้ว sample color-stop LUT ใน fragment shader จึงไล่สีต่อเนื่องภายใน glyph และข้ามรอยต่อระหว่างตัวอักษรได้. มี pipeline/LUT cache แยกตามโปรไฟล์ที่ใช้จริง (สูงสุด 33 palette slots รวม default) เพื่อให้โปรไฟล์ไม่ปะปนสี. ตำแหน่ง X ใช้พิกัด glyph ก่อน matrix transform เทียบกับช่วง layout `[x, activeRight]` ซึ่งมีตำแหน่งต้น glyph อยู่แล้วเพียงครั้งเดียว; ไม่ใช้ `GlyphRenderState.bounds()` เพราะ Minecraft 26.2 คืน `null` สำหรับ glyph. ส่วน enchantment ระดับ good ยังคงใช้สีเดี่ยวตาม `goodEnchantmentColor`. Minecraft 26.2 มี render backend Vulkan ให้เลือก; implementation นี้ใช้ Blaze3D `RenderPipeline` แทน raw OpenGL แต่ยังควรทดสอบกับ GPU drivers และม็อดที่ปรับ GUI rendering

`animationPeriodMs` คือเวลาครบรอบพื้นฐานของ animation ยิ่งน้อยยิ่งเร็ว; `animationSpeed` คูณอัตรานี้โดยไม่ต้องเปลี่ยน period. gradient/LUT ถูก cache แยกตาม palette slot ที่มีขอบเขต 33 ช่อง; `DynamicUniformStorage` ใช้ ring buffer ซึ่ง reset ทุกเฟรมและเก็บ buffer เก่าจากการ resize ไว้ชั่วคราวก่อนปิดใน `endFrame`; เพิ่ม callback ปิด storage ตอน client shutdown แล้ว จึงไม่พบเส้นทางสะสม GPU buffer ไม่สิ้นสุดจาก animation ต่อเนื่อง. ยังมี transient allocation ต่อ glyph ใน vertex wrapper และงานสร้าง text ตาม tooltip ซึ่งอาจสร้าง GC pressure เมื่อ workload ใหญ่มาก แต่ไม่ใช่ retained leak แบบโตไปเรื่อย ๆ. หากทดสอบบนเซิร์ฟเวอร์อื่นให้ตั้ง `onlyOnHypixel` เป็น `false` และต้องมี custom data ของไอเท็มในรูปแบบ Hypixel SkyBlock

หากติดตั้ง Aaron's Mod พร้อมกัน ให้ปิดฟีเจอร์ Rainbow Max Enchants ของ Aaron's Mod เพื่อไม่ให้ tooltip ถูกแต่งสีซ้ำสองครั้ง

## ขอบเขตข้อมูลและที่มา

ระบบนี้คงพฤติกรรมเฉพาะส่วน SkyBlock enchantment ของ Aaron's Mod รุ่นที่ตรวจสอบ โดยเขียน implementation ใหม่ใน namespace `net.lysinas` และ bundle snapshot ของ threshold ที่ใช้กับ enchantment ไว้ใน `src/main/resources/lysinas/enchantments.json` ไม่ได้ bundle คลาสหรือ dependency อื่นของ Aaron's Mod

Birdflop ใช้เป็น reference สำหรับแนวคิดการกำหนด color stops และ animation เท่านั้น ไม่ได้คัดลอก source code ของ Birdflop

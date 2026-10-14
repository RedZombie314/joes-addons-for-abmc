# -*- coding: utf-8 -*-
"""
生成幸运维度的太阳贴图 assets/joes_addons_for_abmc/textures/environment/lucky_sun.png

这里**直接使用幸运方块的贴图本体**（16x16 原样、不缩放、不改色、不加光晕），
所以太阳看上去就是"天上挂着一个幸运方块"。

关键点：为了让贴图的原色能原样显示出来，LuckySunMixin 已经把**太阳这一次绘制**
从原版的加色混合（blendFuncSeparate(SRC_ALPHA, ONE, ...)，dst = src + dst）
改成了普通的 alpha 混合（RenderSystem.defaultBlendFunc()，dst = src*a + dst*(1-a)）。
加色混合下贴图颜色是"加到天空上"的，白天天空很亮，任何颜色加上去都会溢出成白色，
所以加色混合**在数学上不可能**原样显示贴图 —— 只有 alpha 混合可以。

尺寸：太阳四边形固定 60x60 世界单位、UV 贴满整张画布，所以
      方块在画面上的大小 = BLOCK / CANVAS × 60。
      CANVAS=48 -> 20 单位（原版太阳的亮核是 8/32×60 = 15 单位）。
      想让太阳更大/更小，只改下面的 CANVAS 即可，不用动 Java 代码。

用法：python tools/gen_lucky_sun.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_PNG = os.path.join(
    ROOT, "src", "main", "resources", "assets", "joes_addons_for_abmc",
    "textures", "block", "lucky_block.png")
OUT = os.path.join(
    ROOT, "src", "main", "resources", "assets", "joes_addons_for_abmc",
    "textures", "environment", "lucky_sun.png")

CANVAS = 48      # 画布边长（决定太阳大小：方块边长 / CANVAS = 四边形宽度的占比）
BLOCK = 16       # 幸运方块贴图边长（原生 16，1:1 贴上去，保证像素清晰）


def main() -> None:
    block = Image.open(BLOCK_PNG).convert("RGBA")
    assert block.size == (BLOCK, BLOCK), block.size

    # 边距用方块描边色 + alpha=0：alpha 为 0 时颜色不参与混合，
    # 但线性过滤在方块边缘取值时会把颜色和边距平均，用描边色就能避免出现黑边。
    border = block.getpixel((0, 0))
    out = Image.new("RGBA", (CANVAS, CANVAS), (border[0], border[1], border[2], 0))

    off = (CANVAS - BLOCK) // 2
    out.paste(block, (off, off), block)     # 用自身 alpha 贴上去，原样呈现

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    print("已写出:", OUT, out.size, "方块位置:", (off, off, off + BLOCK, off + BLOCK))
    print("方块占四边形宽度: %.0f%%   -> %.1f 世界单位"
          % (100.0 * BLOCK / CANVAS, 60.0 * BLOCK / CANVAS))
    print("中心像素:", out.getpixel((CANVAS // 2, CANVAS // 2)))
    print("边距像素(alpha 应为 0):", out.getpixel((0, 0)))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Generate a thematic preview image for the Medieval Sim mod.

Produces: build/preview.png

This script is self-contained and uses Pillow to render a medieval-themed
shield with crossed swords, a parchment background and the mod name.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from pathlib import Path
import math
import sys

OUT_PATH = Path(__file__).resolve().parents[1] / "build" / "preview.png"


def ensure_dir(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)


def draw_parchment(draw: ImageDraw.Draw, w: int, h: int):
    # Base parchment color with subtle noise-ish gradient
    for y in range(h):
        t = y / h
        r = int(235 - 20 * t)
        g = int(220 - 30 * t)
        b = int(185 - 10 * t)
        draw.line([(0, y), (w, y)], fill=(r, g, b))


def draw_shield(draw: ImageDraw.Draw, cx: int, cy: int, size: int):
    # Draw a simple kite shield shape with bevel
    w = size
    h = int(size * 1.2)
    left = cx - w // 2
    right = cx + w // 2
    top = cy - h // 2
    bottom = cy + h // 2

    # Outer border
    pts = [
        (cx, top),
        (right, cy - h*0.15),
        (right - w*0.15, bottom),
        (left + w*0.15, bottom),
        (left, cy - h*0.15),
    ]
    pts = [(int(x), int(y)) for x, y in pts]
    draw.polygon(pts, fill=(120, 35, 20))

    # Inner face
    inset = int(w * 0.08)
    inner = [(x + (inset if x < cx else -inset), y + inset) if y < cy else (x, y - inset) for x, y in pts]
    draw.polygon(inner, fill=(200, 170, 80))


def draw_swords(draw: ImageDraw.Draw, cx: int, cy: int, size: int):
    # Draw two crossed swords behind the shield
    blade_w = int(size * 0.06)
    blade_h = int(size * 1.0)

    def draw_sword(angle_deg, offset_x):
        angle = math.radians(angle_deg)
        # center of sword shaft
        sx = cx + offset_x
        sy = cy
        # blade rectangle
        dx = math.cos(angle)
        dy = math.sin(angle)
        # four points of a thin rectangle rotated
        hw = blade_w // 2
        points = [
            (sx - dy * hw, sy + dx * hw),
            (sx + dy * hw, sy - dx * hw),
            (sx + dx * blade_h + dy * hw, sy + dy * blade_h - dx * hw),
            (sx + dx * blade_h - dy * hw, sy + dy * blade_h + dx * hw),
        ]
        draw.polygon([(int(x), int(y)) for x, y in points], fill=(192, 192, 192))
        # simple hilt
        hx = sx + dx * (blade_h * 0.12)
        hy = sy + dy * (blade_h * 0.12)
        draw.rectangle([int(hx - blade_w), int(hy - blade_w // 2), int(hx + blade_w), int(hy + blade_w // 2)], fill=(90, 60, 30))

    draw_sword(-30, -size * 0.06)
    draw_sword(30, size * 0.06)


def draw_title(draw: ImageDraw.Draw, w: int, h: int, text: str):
    # Try to load a local-ish font; fallback to default
    try:
        font = ImageFont.truetype("arial.ttf", size=int(h * 0.08))
    except Exception:
        font = ImageFont.load_default()
    text_w, text_h = draw.textsize(text, font=font)
    x = (w - text_w) // 2
    y = int(h * 0.08)
    # shadow
    draw.text((x + 2, y + 2), text, font=font, fill=(40, 20, 10))
    draw.text((x, y), text, font=font, fill=(70, 30, 10))


def main():
    # Canvas size
    w, h = 1200, 675
    bg = Image.new("RGB", (w, h), (240, 220, 180))
    draw = ImageDraw.Draw(bg)

    draw_parchment(draw, w, h)

    # Slight vignette
    vign = Image.new("L", (w, h), 0)
    vd = ImageDraw.Draw(vign)
    for i in range(200):
        vd.ellipse([-i, -i, w + i, h + i], fill=int(255 - (i * 255 / 200)))
    bg = Image.composite(bg, bg.filter(ImageFilter.GaussianBlur(6)), vign)

    # Draw swords and shield
    cx, cy = w // 2, int(h * 0.55)
    draw_swords(draw, cx, cy, 260)
    draw_shield(draw, cx, cy, 260)

    # Decorative corner icons
    draw.rectangle([40, 40, 140, 140], outline=(120, 60, 20), width=4)
    draw.rectangle([w - 140, 40, w - 40, 140], outline=(120, 60, 20), width=4)

    # Title
    draw_title(draw, w, h, "Medieval Sim")

    ensure_dir(OUT_PATH)
    bg.save(OUT_PATH, format="PNG")
    print(f"Wrote preview image: {OUT_PATH}")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("Error while generating preview:", e, file=sys.stderr)
        sys.exit(2)

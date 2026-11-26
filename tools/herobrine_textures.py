"""Draws the textures of the Herobrine part from scratch (needs Pillow).

Run from the repository root:  python tools/herobrine_textures.py
Output: textures/entity/herobrine.png, textures/entity/herobrine_eyes.png, textures/items/herobrine_eye.png
"""
import os
import random

from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets",
                      "shatteredcitadels", "textures")

SKIN = (176, 128, 100)
HAIR = (52, 36, 22)
SHIRT = (0, 150, 150)
PANTS = (58, 52, 140)
SHOE = (86, 86, 86)
EYE = (255, 255, 255)


def shade(color, rng, spread=10):
    d = rng.randint(-spread, spread)
    return tuple(max(0, min(255, c + d)) for c in color) + (255,)


def fill(img, rng, box, color):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), shade(color, rng))


def limb(img, rng, u, v, top_color, side_color, low_color, low_rows, sleeve_rows=0, sleeve=None):
    """A 4x12x4 limb whose texture block starts at (u, v)."""
    fill(img, rng, (u + 4, v, u + 8, v + 4), top_color)
    fill(img, rng, (u + 8, v, u + 12, v + 4), low_color)
    fill(img, rng, (u, v + 4, u + 16, v + 16), side_color)
    if sleeve_rows:
        fill(img, rng, (u, v + 4, u + 16, v + 4 + sleeve_rows), sleeve)
    fill(img, rng, (u, v + 16 - low_rows, u + 16, v + 16), low_color)


def skin():
    rng = random.Random(1206)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # Head: top and the upper rows of every side are hair, the rest is skin.
    fill(img, rng, (8, 0, 16, 8), HAIR)
    fill(img, rng, (16, 0, 24, 8), SKIN)
    fill(img, rng, (0, 8, 32, 16), SKIN)
    fill(img, rng, (0, 8, 32, 10), HAIR)
    fill(img, rng, (0, 10, 8, 12), HAIR)
    fill(img, rng, (16, 10, 32, 12), HAIR)
    fill(img, rng, (24, 12, 32, 15), HAIR)
    # Face: blank white eyes, a nose and a beard line.
    for x in (9, 10, 13, 14):
        img.putpixel((x, 12), EYE + (255,))
    fill(img, rng, (11, 13, 13, 14), (140, 96, 74))
    fill(img, rng, (10, 14, 14, 15), HAIR)
    # Body.
    fill(img, rng, (20, 16, 28, 20), SHIRT)
    fill(img, rng, (28, 16, 36, 20), PANTS)
    fill(img, rng, (16, 20, 40, 32), SHIRT)
    fill(img, rng, (16, 30, 40, 32), PANTS)
    fill(img, rng, (22, 20, 26, 22), SKIN)
    # Arms (right at 40,16; left at 32,48) and legs (right at 0,16; left at 16,48).
    for u, v in ((40, 16), (32, 48)):
        limb(img, rng, u, v, SHIRT, SKIN, SKIN, 0, 4, SHIRT)
    for u, v in ((0, 16), (16, 48)):
        limb(img, rng, u, v, PANTS, PANTS, SHOE, 3)
    return img


def eyes():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
    for x in (9, 10, 13, 14):
        img.putpixel((x, 12), EYE + (255,))
    return img


def relic():
    rng = random.Random(41)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 7.0, (y - 7.5) / 4.6
            d = dx * dx + dy * dy
            if d <= 1.0:
                img.putpixel((x, y), shade((40, 30, 24) if d > 0.62 else (150, 108, 84), rng, 6))
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if r <= 2.6:
                glow = int(255 - r * 22)
                img.putpixel((x, y), (glow, glow, 255, 255))
    return img


def main():
    skin().save(os.path.join(ASSETS, "entity", "herobrine.png"))
    eyes().save(os.path.join(ASSETS, "entity", "herobrine_eyes.png"))
    relic().save(os.path.join(ASSETS, "items", "herobrine_eye.png"))


if __name__ == "__main__":
    main()

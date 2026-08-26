"""
Generates a vanilla pumpkinblur.png override for an iron-sights ADS vignette.

Vanilla's pumpkin vision-restriction overlay is a multiplicative mask: white areas show the
scene unchanged, black areas block it. Confirmed against Aechronis/aechronis's own resource pack
override (same file, same mechanism, but drawn as a circular scope reticle -- appropriate for
their scoped sniper rifles). This version is an original, independently-drawn shape: a tall
narrow window rather than a round scope, plus a front-sight-post silhouette and a rear-sight
notch, since the musket has iron sights, not a telescopic scope.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
cx, cy = W // 2, H // 2

# Large-radius soft mask, oversampled then blurred for a smooth falloff edge.
SS = 4
big = Image.new("L", (W * SS, H * SS), 0)
d = ImageDraw.Draw(big)

win_w, win_h = 460 * SS, 700 * SS
d.ellipse(
    [cx * SS - win_w // 2, cy * SS - win_h // 2, cx * SS + win_w // 2, cy * SS + win_h // 2],
    fill=255,
)
mask = big.resize((W, H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(10))

canvas = Image.new("RGBA", (W, H), (0, 0, 0, 255))
white = Image.new("RGBA", (W, H), (255, 255, 255, 255))
canvas.paste(white, (0, 0), mask)

draw = ImageDraw.Draw(canvas)

# Front sight post: a tapered dark blade rising from the bottom of the window.
post_w_base = 34
post_w_tip = 10
post_top = cy + 40
post_bottom = cy + int(win_h / SS / 2) + 40
draw.polygon(
    [
        (cx - post_w_base // 2, post_bottom),
        (cx + post_w_base // 2, post_bottom),
        (cx + post_w_tip // 2, post_top),
        (cx - post_w_tip // 2, post_top),
    ],
    fill=(10, 10, 10, 255),
)

# Rear sight notch: a small dark U-shaped notch near the top of the window.
notch_y = cy - int(win_h / SS / 2) + 60
notch_w = 90
notch_h = 46
draw.rectangle(
    [cx - notch_w // 2, notch_y, cx - notch_w // 2 + 18, notch_y + notch_h],
    fill=(10, 10, 10, 255),
)
draw.rectangle(
    [cx + notch_w // 2 - 18, notch_y, cx + notch_w // 2, notch_y + notch_h],
    fill=(10, 10, 10, 255),
)

out_path = (
    Path(__file__).resolve().parent
    / "assets"
    / "minecraft"
    / "textures"
    / "misc"
    / "pumpkinblur.png"
)
canvas.save(out_path)
print("saved", out_path, canvas.size)

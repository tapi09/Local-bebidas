#!/usr/bin/env python3
"""Generate the generic Software de Bebidas app icon.

Renders a rounded blue tile with a simple white bottle glyph drawn with Pillow
primitives (no source image needed). Each frame is drawn at 8x and downsampled with
LANCZOS so small sizes stay smooth. Outputs a multi-resolution Windows .ico and a
256px window icon PNG.

Requires Pillow. Run from anywhere:
    python src/main/installer/generate_icon.py

Outputs:
    src/main/installer/softwaredebebidas.ico   (sizes 16-256, used by jpackage --icon)
    src/main/resources/icons/softwaredebebidas.png (256px, classpath window icon)
"""

import os

from PIL import Image, ImageDraw

SIZES = (16, 24, 32, 48, 64, 128, 256)
SUPERSAMPLE = 8
BACKGROUND = (37, 99, 235, 255)
FOREGROUND = (255, 255, 255, 255)
CORNER_RADIUS_RATIO = 0.22

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ICO_PATH = os.path.join(SCRIPT_DIR, "softwaredebebidas.ico")
PNG_PATH = os.path.normpath(
    os.path.join(SCRIPT_DIR, "..", "resources", "icons", "softwaredebebidas.png")
)


def _draw_bottle(draw, size):
    """Draw a centered bottle silhouette (cap, neck, shoulders, body) in white."""
    cx = size / 2
    # Proportions relative to the tile size.
    body_w = size * 0.34
    body_top = size * 0.42
    body_bottom = size * 0.86
    neck_w = size * 0.14
    neck_top = size * 0.16
    cap_h = size * 0.06
    radius = size * 0.07

    # Body with rounded bottom corners.
    draw.rounded_rectangle(
        (cx - body_w / 2, body_top, cx + body_w / 2, body_bottom),
        radius=radius,
        fill=FOREGROUND,
    )
    # Shoulders: a trapezoid joining the body to the neck.
    draw.polygon(
        [
            (cx - body_w / 2, body_top + radius),
            (cx - neck_w / 2, neck_top + cap_h),
            (cx + neck_w / 2, neck_top + cap_h),
            (cx + body_w / 2, body_top + radius),
        ],
        fill=FOREGROUND,
    )
    # Neck.
    draw.rectangle(
        (cx - neck_w / 2, neck_top + cap_h, cx + neck_w / 2, body_top + radius),
        fill=FOREGROUND,
    )
    # Cap, slightly wider than the neck.
    draw.rounded_rectangle(
        (cx - neck_w * 0.65, neck_top, cx + neck_w * 0.65, neck_top + cap_h * 1.4),
        radius=size * 0.02,
        fill=FOREGROUND,
    )
    # Label band cut out of the body so the glyph reads as a bottle.
    draw.rectangle(
        (cx - body_w / 2 + size * 0.03, size * 0.58, cx + body_w / 2 - size * 0.03, size * 0.72),
        fill=BACKGROUND,
    )


def render_icon(size):
    big = size * SUPERSAMPLE
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    radius = max(1, int(big * CORNER_RADIUS_RATIO))
    draw.rounded_rectangle((0, 0, big - 1, big - 1), radius=radius, fill=BACKGROUND)
    _draw_bottle(draw, big)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def main():
    frames = [render_icon(size) for size in SIZES]
    os.makedirs(os.path.dirname(PNG_PATH), exist_ok=True)
    frames[-1].save(PNG_PATH)
    frames[-1].save(
        ICO_PATH,
        format="ICO",
        sizes=[(size, size) for size in SIZES],
        append_images=frames[:-1],
    )
    print("Wrote {0}".format(ICO_PATH))
    print("Wrote {0}".format(PNG_PATH))


if __name__ == "__main__":
    main()

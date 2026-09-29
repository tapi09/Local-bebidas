#!/usr/bin/env python3
"""Generate the Cocolatan app icon from the real brand logo.

When the real logo (data/logo/logo.png) exists it is letterboxed inside a
square RGBA canvas with ~6% padding, preserving the aspect ratio (LANCZOS,
no cropping). When the logo is missing, a blue rounded-square tile with a
white letter "C" is rendered as fallback. Outputs a multi-resolution
Windows .ico and a 256px window icon PNG.

Requires Pillow. Run from anywhere:
    python src/main/installer/generate_icon.py

Outputs:
    src/main/installer/cocolatan.ico   (sizes 16-256, used by jpackage --icon)
    src/main/resources/icons/cocolatan.png (256px, classpath window icon)
"""

import os

from PIL import Image, ImageDraw, ImageFont

SIZES = (16, 24, 32, 48, 64, 128, 256)
PADDING_RATIO = 0.06
BACKGROUND = (37, 99, 235, 255)
FOREGROUND = (255, 255, 255, 255)
CORNER_RADIUS_RATIO = 0.22
LETTER = "C"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "..", ".."))
LOGO_PATH = os.path.join(REPO_ROOT, "src", "main", "resources", "icons", "logo_secundario_gpt.png")
ICO_PATH = os.path.join(SCRIPT_DIR, "cocolatan.ico")
PNG_PATH = os.path.normpath(
    os.path.join(SCRIPT_DIR, "..", "resources", "icons", "cocolatan.png")
)


def _bold_font(size):
    candidates = (
        "C:/Windows/Fonts/segoeuib.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
        "C:/Windows/Fonts/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    )
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size=size)
            except OSError:
                continue
    return ImageFont.load_default()


def _render_fallback_tile(size):
    radius = max(1, int(size * CORNER_RADIUS_RATIO))
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=BACKGROUND)
    font = _bold_font(int(size * 0.62))
    bbox = draw.textbbox((0, 0), LETTER, font=font)
    text_width = bbox[2] - bbox[0]
    text_height = bbox[3] - bbox[1]
    x = (size - text_width) / 2 - bbox[0]
    y = (size - text_height) / 2 - bbox[1]
    draw.text((x, y), LETTER, font=font, fill=FOREGROUND)
    return image


def _contain_logo(logo, size):
    """Letterbox the logo inside a square RGBA canvas with ~6% padding.

    The logo keeps its aspect ratio (no crop) and is centered on a
    transparent background.
    """
    pad = max(1, int(size * PADDING_RATIO))
    inner = size - 2 * pad
    ratio = min(inner / logo.width, inner / logo.height)
    new_width = max(1, int(logo.width * ratio))
    new_height = max(1, int(logo.height * ratio))
    resized = logo.resize((new_width, new_height), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    offset_x = (size - new_width) // 2
    offset_y = (size - new_height) // 2
    canvas.paste(resized, (offset_x, offset_y), resized)
    return canvas


def render_icon(size):
    if os.path.exists(LOGO_PATH):
        with Image.open(LOGO_PATH) as logo:
            logo_rgba = logo.convert("RGBA")
            return _contain_logo(logo_rgba, size)
    return _render_fallback_tile(size)


def main():
    frames = [render_icon(size) for size in SIZES]
    os.makedirs(os.path.dirname(PNG_PATH), exist_ok=True)
    frames[-1].save(PNG_PATH)
    frames[-1].save(ICO_PATH, format="ICO", append_images=frames[:-1])
    print("Wrote {0}".format(ICO_PATH))
    print("Wrote {0}".format(PNG_PATH))


if __name__ == "__main__":
    main()

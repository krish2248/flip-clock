"""Renders widget preview PNGs and Play Store graphics with the app's real palette."""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

RES = sys.argv[1]
STORE = sys.argv[2]
os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
os.makedirs(STORE, exist_ok=True)

SCREEN = (11, 11, 13)
TOP = (30, 30, 35)
BOTTOM = (24, 24, 28)
DIGIT = (244, 244, 246)
SECONDARY = (180, 180, 190)
ACCENT = (232, 163, 61)

FONT_CANDIDATES = [
    "C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]
FONT_REG = ["C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf"]


def font(size, bold=True):
    for p in (FONT_CANDIDATES if bold else FONT_REG):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def card(img, box, text, radius, fsize, s=4):
    """Draws one split-flap card (supersampled by caller)."""
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    mid = (y0 + y1) / 2
    d.rounded_rectangle(box, radius=radius, fill=BOTTOM)
    d.rounded_rectangle((x0, y0, x1, mid + radius), radius=radius, fill=TOP)
    d.rectangle((x0, mid, x1, mid + radius), fill=BOTTOM)
    f = font(fsize)
    bb = d.textbbox((0, 0), text, font=f)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    d.text(((x0 + x1) / 2 - tw / 2 - bb[0], mid - th / 2 - bb[1]), text, font=f, fill=DIGIT)
    seam = max(3, int(s * 2.4))
    d.rectangle((x0, mid - seam / 2, x1, mid + seam / 2), fill=SCREEN)
    nw, nh = max(2, int((x1 - x0) * 0.03)), (y1 - y0) * 0.07
    d.rectangle((x0, mid - nh / 2, x0 + nw, mid + nh / 2), fill=SCREEN)
    d.rectangle((x1 - nw, mid - nh / 2, x1, mid + nh / 2), fill=SCREEN)


def plate(w, h, s, radius=22):
    img = Image.new("RGBA", (w * s, h * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, w * s - 1, h * s - 1), radius=radius * s, fill=SCREEN + (255,))
    return img


def save(img, w, h, path):
    img.resize((w, h), Image.LANCZOS).save(path, optimize=True)


S = 4
# --- Flip Clock (4x2)
w, h = 520, 240
img = plate(w, h, S)
pad, gap = 18 * S, 10 * S
cw = (w * S - pad * 2 - gap) / 2
card(img, (pad, pad, pad + cw, h * S - pad), "10", 18 * S, int(150 * S))
card(img, (pad + cw + gap, pad, pad + 2 * cw + gap, h * S - pad), "09", 18 * S, int(150 * S))
ImageDraw.Draw(img).text((pad + 14 * S, pad + 10 * S), "AM", font=font(15 * S), fill=ACCENT)
save(img, w, h, os.path.join(RES, "drawable-nodpi/widget_preview_clock.png"))

# --- Flip Clock & Date (4x3)
w, h = 520, 330
img = plate(w, h, S)
info_h = 46 * S
cw = (w * S - pad * 2 - gap) / 2
card(img, (pad, pad, pad + cw, h * S - pad - info_h), "10", 18 * S, int(150 * S))
card(img, (pad + cw + gap, pad, pad + 2 * cw + gap, h * S - pad - info_h), "09", 18 * S, int(150 * S))
d = ImageDraw.Draw(img)
f = font(19 * S)
t = "Wed, 18 November   \u23f0 7:00"
bb = d.textbbox((0, 0), t, font=f)
d.text((w * S / 2 - (bb[2] - bb[0]) / 2, h * S - pad - info_h + 12 * S), "Wed, 18 November", font=f, fill=SECONDARY)
save(img, w, h, os.path.join(RES, "drawable-nodpi/widget_preview_clock_date.png"))

# --- Flip Calendar (2x2)
w, h = 260, 260
img = plate(w, h, S)
d = ImageDraw.Draw(img)
f = font(15 * S)
for text, y, col in (("NOVEMBER", 16 * S, ACCENT),):
    bb = d.textbbox((0, 0), text, font=f)
    d.text((w * S / 2 - (bb[2] - bb[0]) / 2, y), text, font=f, fill=col)
card(img, (40 * S, 44 * S, w * S - 40 * S, h * S - 44 * S), "18", 16 * S, int(118 * S))
t = "Wednesday"
bb = d.textbbox((0, 0), t, font=font(15 * S, bold=False))
d.text((w * S / 2 - (bb[2] - bb[0]) / 2, h * S - 34 * S), t, font=font(15 * S, bold=False), fill=SECONDARY)
save(img, w, h, os.path.join(RES, "drawable-nodpi/widget_preview_calendar.png"))

# --- Flip Mini (3x1)
w, h = 390, 130
img = plate(w, h, S)
card(img, (14 * S, 14 * S, w * S - 14 * S, h * S - 14 * S), "10:09", 14 * S, int(84 * S))
save(img, w, h, os.path.join(RES, "drawable-nodpi/widget_preview_mini.png"))

# --- Play Store hi-res icon (512x512, full-bleed square; Play applies its own mask)
s = 4
W = 512 * s
icon = Image.new("RGB", (W, W), SCREEN)
u = W / 74.0
def P(x, y): return ((x - 17) * u, (y - 17) * u)
d = ImageDraw.Draw(icon)
shadow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
ImageDraw.Draw(shadow).rounded_rectangle((*P(30, 31), *P(78, 82)), radius=6 * u, fill=(0, 0, 0, 120))
icon.paste(shadow.filter(ImageFilter.GaussianBlur(3 * u)), (0, 0), shadow.filter(ImageFilter.GaussianBlur(3 * u)))
d = ImageDraw.Draw(icon)
d.rounded_rectangle((*P(30, 28), *P(78, 80)), radius=6 * u, fill=(31, 31, 37))
d.rounded_rectangle((*P(30, 28), *P(78, 54)), radius=6 * u, fill=(42, 42, 49))
d.rectangle((*P(30, 48), *P(78, 54)), fill=(42, 42, 49))
seven = [P(42.5, 35), P(66, 35), P(66, 40.6), P(53.6, 73), P(46.8, 73), P(58.6, 40.6), P(42.5, 40.6)]
d.polygon(seven, fill=DIGIT)
d.rectangle((*P(30, 52.6), *P(78, 55.4)), fill=SCREEN)
d.rectangle((*P(30, 50.5), *P(31.8, 57.5)), fill=SCREEN)
d.rectangle((*P(76.2, 50.5), *P(78, 57.5)), fill=SCREEN)
d.ellipse((*P(34.8, 34.8), *P(39.2, 39.2)), fill=ACCENT)
icon.resize((512, 512), Image.LANCZOS).save(os.path.join(STORE, "icon-512.png"), optimize=True)

# --- Feature graphic (1024x500)
W, H = 1024 * S, 500 * S
fg = Image.new("RGB", (W, H), SCREEN)
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse((W * 0.15, H * 0.05, W * 0.85, H * 1.2), fill=(232, 163, 61, 38))
glow = glow.filter(ImageFilter.GaussianBlur(120 * S / 4))
fg.paste(glow, (0, 0), glow)
cw_, ch_ = 150 * S, 220 * S
y0 = (H - ch_) / 2 - 20 * S
xs = [W / 2 - 2 * cw_ - 60 * S, W / 2 - cw_ - 50 * S, W / 2 + 50 * S, W / 2 + cw_ + 60 * S]
for x, t in zip(xs, "1009"):
    card(fg, (x, y0, x + cw_, y0 + ch_), t, 18 * S, int(170 * S), s=S)
d = ImageDraw.Draw(fg)
for cy in (y0 + ch_ * 0.36, y0 + ch_ * 0.64):
    r = 11 * S
    d.ellipse((W / 2 - r, cy - r, W / 2 + r, cy + r), fill=DIGIT)
f = font(34 * S, bold=False)
t = "Flip  \u2014  a beautiful split-flap clock"
bb = d.textbbox((0, 0), t, font=f)
d.text((W / 2 - (bb[2] - bb[0]) / 2, y0 + ch_ + 34 * S), t, font=f, fill=SECONDARY)
fg.resize((1024, 500), Image.LANCZOS).save(os.path.join(STORE, "feature-graphic-1024x500.png"), optimize=True)
print("images ok")

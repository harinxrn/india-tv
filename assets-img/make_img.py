# Kerala Tv logo art - v2.5 redesign.
# Old art drew text at fixed offsets without measuring it, so the banner
# wordmark ran off the right edge. This version measures every string
# with PIL and auto-fits / centers it - nothing can overflow.
# Design: deep-green premium gradient, white play mark in a green tile,
# red LIVE dot, Montserrat wordmark with tracked-out caps subtitle.
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.expanduser("~/workspace/india-tv/app/src/main/res")
FB = os.path.join(HERE, "fonts", "Montserrat-Bold.ttf")
FS = os.path.join(HERE, "fonts", "Montserrat-SemiBold.ttf")
# The Montserrat TTFs are binary and are not synced to the GitHub repo;
# fall back to DejaVu so a fresh clone can still generate the art.
_DEJAVU = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
if not os.path.exists(FB):
    FB = _DEJAVU
if not os.path.exists(FS):
    FS = FB

WHITE = (245, 247, 246)
SAGE = (157, 184, 173)
RED = (255, 59, 48)


def vgrad(w, h, top, bot):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    return img


def tile(size, radius, top=(20, 184, 106), bot=(9, 122, 72)):
    """Rounded-corner green gradient tile with transparent corners."""
    g = vgrad(size, size, top, bot)
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(g, (0, 0), mask)
    return out


def play_pts(cx, cy, w, h):
    # Optically centered play triangle (nudged right - equal margins lie).
    x0 = cx - w / 2 + w * 0.08
    return [(x0, cy - h / 2), (x0, cy + h / 2), (x0 + w, cy)]


def live_dot(img, cx, cy, r):
    d = ImageDraw.Draw(img)
    d.ellipse([cx - r - 5, cy - r - 5, cx + r + 5, cy + r + 5], fill=(255, 255, 255))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=RED)


def tracked_width(draw, text, font, tracking):
    if not text:
        return 0
    return sum(draw.textlength(ch, font=font) for ch in text) + tracking * (len(text) - 1)


def draw_tracked(draw, x, y, text, font, fill, tracking):
    cx = x
    for ch in text:
        draw.text((cx, y), ch, font=font, fill=fill)
        cx += draw.textlength(ch, font=font) + tracking
    return cx - tracking


def fit_font(draw, text, path, start_size, max_width, tracking):
    size = start_size
    while size > 10:
        f = ImageFont.truetype(path, size)
        if tracked_width(draw, text, f, tracking) <= max_width:
            return f
        size -= 2
    return ImageFont.truetype(path, size)


# ================= Banner: render 1280x720, save 640x360 =================
W, H = 1280, 720
b = vgrad(W, H, (8, 34, 25), (3, 15, 10)).convert("RGBA")
# soft glow behind the mark
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse([40, 130, 560, 650], fill=(20, 160, 95, 90))
b = Image.alpha_composite(b, glow.filter(ImageFilter.GaussianBlur(90)))
d = ImageDraw.Draw(b)

TILE = 300
TX, TY = 84, (H - TILE) // 2
b.alpha_composite(tile(TILE, 64), (TX, TY))
d = ImageDraw.Draw(b)
cx, cy = TX + TILE // 2, TY + TILE // 2
d.polygon(play_pts(cx, cy, 118, 142), fill=WHITE)
live_dot(b, TX + TILE - 54, TY + 54, 17)
d = ImageDraw.Draw(b)

# Text block - everything measured, auto-fit to the space right of the tile.
X0 = TX + TILE + 68
AVAIL = W - X0 - 84
word = "KERALA TV"
wf = fit_font(d, word, FB, 118, AVAIL, 4)
wb = d.textbbox((0, 0), word, font=wf)
word_h = wb[3] - wb[1]
word_y = 268 - wb[1]
draw_tracked(d, X0, word_y, word, wf, WHITE, 4)

sub = "MALAYALAM LIVE NEWS & TV"
sf = fit_font(d, sub, FS, 39, AVAIL - 96, 7)
row_y = word_y + word_h + 44
d.rounded_rectangle([X0, row_y + 8, X0 + 62, row_y + 20], radius=6, fill=RED)
draw_tracked(d, X0 + 88, row_y, sub, sf, SAGE, 7)

b.convert("RGB").resize((640, 360), Image.LANCZOS).save(os.path.join(OUT, "drawable", "banner.png"))

# ================= Icon: render 512x512, save 192x192 =================
# Same tile as the banner, inset with a margin so it reads as an app
# tile (like the banner mark) instead of a full-bleed green square.
S = 512
M = 26
TS = S - 2 * M
icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
icon.alpha_composite(tile(TS, 104), (M, M))
d = ImageDraw.Draw(icon)
d.polygon(play_pts(S // 2, S // 2, 150, 182), fill=WHITE)
live_dot(icon, M + TS - 80, M + 80, 24)
icon.resize((192, 192), Image.LANCZOS).save(os.path.join(OUT, "mipmap-xxxhdpi", "ic_launcher.png"))
print("new logo art done")

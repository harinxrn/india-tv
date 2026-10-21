from PIL import Image, ImageDraw, ImageFont
import math, os

OUT = os.path.expanduser("~/workspace/india-tv/app/src/main/res")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

def grad(w, h, top=(14,14,18), bot=(26,22,20)):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    return img

def play(draw, cx, cy, r, color=(255, 138, 26)):
    pts = [(cx - r * 0.55, cy - r), (cx - r * 0.55, cy + r), (cx + r * 0.85, cy)]
    draw.polygon(pts, fill=color)

# ---- Banner 320x180 (Android TV home row) ----
b = grad(320, 180)
d = ImageDraw.Draw(b)
play(d, 52, 78, 34)
try:
    f1 = ImageFont.truetype(FONT, 44)
    f2 = ImageFont.truetype(FONT, 17)
except Exception:
    f1 = ImageFont.load_default(); f2 = f1
d.text((100, 48), "INDIA TV", font=f1, fill=(245, 245, 247))
d.text((102, 104), "Live • Malayalam • Sports • Movies", font=f2, fill=(168, 168, 175))
d.line([(0, 177), (320, 177)], fill=(255, 138, 26), width=3)
b.save(os.path.join(OUT, "drawable", "banner.png"))

# ---- Launcher icon 192x192 ----
i = grad(192, 192)
d = ImageDraw.Draw(i)
d.rounded_rectangle([6, 6, 186, 186], radius=36, outline=(58, 58, 66), width=3)
play(d, 100, 84, 46)
try:
    f3 = ImageFont.truetype(FONT, 30)
except Exception:
    f3 = ImageFont.load_default()
d.text((96, 148), "TV", font=f3, fill=(245, 245, 247), anchor="mm")
i.save(os.path.join(OUT, "mipmap-xxxhdpi", "ic_launcher.png"))
print("images done")

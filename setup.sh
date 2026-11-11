#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "==> 0) organize files"
mkdir -p src native/java audio
[ -f app.js ] && mv app.js src/app.js
for f in *.java; do [ -f "$f" ] && mv "$f" native/java/; done
for f in manifest-*.xml patch_manifest.py; do [ -f "$f" ] && mv "$f" native/; done
for f in adhan*.mp3 adhan*.ogg adhan*.wav; do [ -f "$f" ] && mv "$f" audio/; done

echo "==> 1) npm install"
npm install

echo "==> 2) site files"
if [ ! -f www/index.html ]; then
  rm -rf _site
  git clone --depth 1 https://github.com/shefraprogrammer-sudo/Al-Sirat-Al-Mustaqeem-.git _site
  mkdir -p www
  cp -r _site/. www/ && rm -rf www/.git _site
fi
[ -f www/index.html ] || { echo "ERROR: www/index.html missing"; exit 1; }

echo "==> 3) bundle adhan code"
npm run bundle
grep -q 'adhan-app.js' www/index.html || \
  sed -i 's#</body>#<script src="adhan-app.js"></script></body>#' www/index.html

echo "==> 4) android project"
[ -d android ] || npx cap add android
PKG_DIR=android/app/src/main/java/com/shefra/alsirat
mkdir -p "$PKG_DIR"
cp native/java/*.java "$PKG_DIR/"
python3 native/patch_manifest.py

echo "==> 5) audio"
mkdir -p android/app/src/main/res/raw
for f in adhan adhan_fajr; do
  for ext in mp3 ogg wav; do
    [ -f "audio/$f.$ext" ] && cp "audio/$f.$ext" android/app/src/main/res/raw/
  done
done

echo "==> 5b) app icon"
python3 -c "import PIL" 2>/dev/null || pip install --quiet pillow || pip install --quiet --break-system-packages pillow
python3 - android/app/src/main/res <<'PYEOF'
import math, os, sys
from PIL import Image, ImageDraw

RES = sys.argv[1] if len(sys.argv) > 1 else "res"
GOLD = (224, 184, 92, 255)
GOLD_L = (248, 222, 140, 255)
TEAL = (14, 50, 62, 255)
GLOW = (40, 110, 124, 110)
BG1, BG2 = (6, 18, 28), (16, 58, 72)
SS = 4

def gradient(S):
    g = Image.new("RGBA", (S, S))
    px = g.load()
    for y in range(S):
        t = y / (S - 1)
        col = tuple(int(BG1[i] + (BG2[i] - BG1[i]) * t) for i in range(3)) + (255,)
        for x in range(S):
            px[x, y] = col
    return g

def crescent(d, cx, cy, r, col, bg):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col)
    d.ellipse([cx - r * 0.35, cy - r * 1.0 - r * 0.05, cx - r * 0.35 + r * 1.7 * 0.62, cy + r * 0.92], fill=bg)

def star4(d, cx, cy, r, col):
    pts = []
    for i in range(8):
        rr = r if i % 2 == 0 else r * 0.28
        a = -math.pi / 2 + i * math.pi / 4
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=col)

def art(img, S, u, sky_stars):
    d = ImageDraw.Draw(img, "RGBA")
    c = S / 2
    T = lambda x, y: (c + x * u, c + y * u)
    def rect(x0, y0, x1, y1, col):
        a, b = T(x0, y0), T(x1, y1)
        d.rectangle([a[0], a[1], b[0], b[1]], fill=col)
    def ell(x0, y0, x1, y1, col):
        a, b = T(x0, y0), T(x1, y1)
        d.ellipse([a[0], a[1], b[0], b[1]], fill=col)
    def dome(cx, base, r, col):
        a, b = T(cx - r, base - r), T(cx + r, base + r)
        d.pieslice([a[0], a[1], b[0], b[1]], 180, 360, fill=col)
    def arch(cx, top, w, bot, col):
        ell(cx - w, top, cx + w, top + 2 * w, col)
        rect(cx - w, top + w, cx + w, bot, col)

    # soft glow behind the mosque
    ell(-0.92, -0.95, 0.92, 0.89, GLOW)
    if sky_stars:
        for (x, y, r) in [(-0.72, -0.72, 0.07), (0.62, -0.62, 0.05), (-0.45, -0.38, 0.035), (0.9, -0.2, 0.035), (-0.95, 0.0, 0.03)]:
            sx, sy = T(x, y)
            star4(d, sx, sy, r * u, GOLD_L)
    g = 0.78  # ground y
    # side wings
    rect(-0.78, 0.50, -0.45, g, GOLD)
    rect(0.45, 0.50, 0.78, g, GOLD)
    dome(-0.62, 0.50, 0.15, GOLD)
    dome(0.62, 0.50, 0.15, GOLD)
    # main hall + big dome
    rect(-0.50, 0.30, 0.50, g, GOLD)
    dome(0, 0.30, 0.50, GOLD)
    # finial + crescent
    rect(-0.012, -0.34, 0.012, -0.2, GOLD_L)
    # crescent (true cut-out so no dark disc shows)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    a, b = T(-0.10, -0.55), T(0.10, -0.35)
    ld.ellipse([a[0], a[1], b[0], b[1]], fill=GOLD_L)
    a, b = T(-0.03, -0.58), T(0.14, -0.40)
    ld.ellipse([a[0], a[1], b[0], b[1]], fill=(0, 0, 0, 0))
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img, "RGBA")
    # minarets
    for sgn in (-1, 1):
        mx = 0.86 * sgn
        rect(mx - 0.065, -0.05, mx + 0.065, g, GOLD)
        rect(mx - 0.10, 0.12, mx + 0.10, 0.17, GOLD_L)
        rect(mx - 0.10, -0.08, mx + 0.10, -0.03, GOLD_L)
        a, b, cc = T(mx - 0.085, -0.08), T(mx + 0.085, -0.08), T(mx, -0.40)
        d.polygon([a, b, cc], fill=GOLD)
        rect(mx - 0.008, -0.50, mx + 0.008, -0.38, GOLD_L)
        ell(mx - 0.035, -0.54, mx + 0.035, -0.47, GOLD_L)
        arch(mx, 0.26, 0.026, 0.38, TEAL)
    # door and windows
    arch(0, 0.46, 0.13, g, TEAL)
    for x in (-0.34, 0.34):
        arch(x, 0.40, 0.055, 0.60, TEAL)
    for x in (-0.62, 0.62):
        arch(x, 0.58, 0.04, 0.70, TEAL)
    # ground line
    rect(-1.0, g, 1.0, g + 0.045, GOLD_L)

def render(size, mode):
    S = size * SS
    if mode == "fg":
        img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        art(img, S, S * 0.295, False)
    else:
        img = gradient(S)
        art(img, S, S * 0.40, True)
        mask = Image.new("L", (S, S), 0)
        m = ImageDraw.Draw(mask)
        if mode == "round":
            m.ellipse([0, 0, S - 1, S - 1], fill=255)
        else:
            m.rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
        img.putalpha(mask)
    return img.resize((size, size), Image.LANCZOS)

legacy = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
fg = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for dens in legacy:
    d = os.path.join(RES, "mipmap-" + dens)
    os.makedirs(d, exist_ok=True)
    render(legacy[dens], "square").save(os.path.join(d, "ic_launcher.png"))
    render(legacy[dens], "round").save(os.path.join(d, "ic_launcher_round.png"))
    render(fg[dens], "fg").save(os.path.join(d, "ic_launcher_foreground.png"))

v26 = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(v26, exist_ok=True)
xml = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
       '    <background android:drawable="@color/ic_launcher_background"/>\n'
       '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n</adaptive-icon>\n')
for n in ("ic_launcher.xml", "ic_launcher_round.xml"):
    open(os.path.join(v26, n), "w").write(xml)
vals = os.path.join(RES, "values")
os.makedirs(vals, exist_ok=True)
open(os.path.join(vals, "ic_launcher_background.xml"), "w").write(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#0B2A38</color>\n</resources>\n')
print("icons generated in", RES)
PYEOF

echo "==> 6) sync and build"
npx cap sync android
( cd android && ./gradlew assembleDebug )
echo "DONE: android/app/build/outputs/apk/debug/app-debug.apk"

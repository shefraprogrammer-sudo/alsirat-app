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

echo "==> 6) sync and build"
npx cap sync android
( cd android && ./gradlew assembleDebug )
echo "DONE: android/app/build/outputs/apk/debug/app-debug.apk"

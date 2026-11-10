#!/usr/bin/env bash
# يبني APK "الصراط المستقيم" مع الأذان في الخلفية.
# المتطلبات: Node 18+ ، JDK 17 ، Android SDK (أو Android Studio) ، git ، python3
set -euo pipefail
cd "$(dirname "$0")"

echo "==> 1) تثبيت الحزم"
npm install

echo "==> 2) ملفات الموقع"
if [ ! -f www/index.html ]; then
  rm -rf _site
  git clone --depth 1 https://github.com/shefraprogrammer-sudo/Al-Sirat-Al-Mustaqeem-.git _site
  mkdir -p www
  cp -r _site/. www/ && rm -rf www/.git _site
fi
[ -f www/index.html ] || { echo "ERROR: ضع ملفات الموقع في فولدر www (index.html)"; exit 1; }

echo "==> 3) تجميع كود الأذان"
npm run bundle
grep -q 'adhan-app.js' www/index.html || \
  sed -i 's#</body>#<script src="adhan-app.js"></script></body>#' www/index.html

echo "==> 4) مشروع أندرويد"
[ -d android ] || npx cap add android
PKG_DIR=android/app/src/main/java/com/shefra/alsirat
mkdir -p "$PKG_DIR"
cp native/java/*.java "$PKG_DIR/"
python3 native/patch_manifest.py

echo "==> 5) ملفات الصوت (audio/adhan.mp3 و اختياري audio/adhan_fajr.mp3)"
mkdir -p android/app/src/main/res/raw
for f in adhan adhan_fajr; do
  for ext in mp3 ogg wav; do
    [ -f "audio/$f.$ext" ] && cp "audio/$f.$ext" android/app/src/main/res/raw/
  done
done
ls android/app/src/main/res/raw/adhan.* >/dev/null 2>&1 || \
  echo "تنبيه: لا يوجد audio/adhan.mp3 — سيُستخدم صوت المنبه الافتراضي مؤقتاً."

echo "==> 6) مزامنة وبناء"
npx cap sync android
( cd android && ./gradlew assembleDebug )
echo
echo "تم ✅  ملف الـ APK:"
echo "  android/app/build/outputs/apk/debug/app-debug.apk"

#!/data/data/com.termux/files/usr/bin/bash
set -e
ROOT="${1:-.}"
cd "$ROOT"
EXPECTED="2950831cac2dc4417c23d2a6e2c054173bc5675d"
ACTUAL="$(git rev-parse HEAD)"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "ERROR: This overlay expects GHI commit $EXPECTED"
  echo "Current commit: $ACTUAL"
  exit 1
fi
cp -a android/app/src/main/java/io/ciphertun/ghi/app/GhiNavHost.kt android/app/src/main/java/io/ciphertun/ghi/app/GhiNavHost.kt.bak
cp -a android/app/src/main/java/io/ciphertun/ghi/app/GhiSession.kt android/app/src/main/java/io/ciphertun/ghi/app/GhiSession.kt.bak
cp -a android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/DiscoverScreen.kt android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/DiscoverScreen.kt.bak
cp -a android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/PayloadGeneratorScreen.kt android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/PayloadGeneratorScreen.kt.bak
cp -a android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/NetworkToolsScreens.kt android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/NetworkToolsScreens.kt.bak
cp -a backend/mobile/carrier.go backend/mobile/carrier.go.bak
cp -a "$(dirname "$0")/android/" android/
cp -a "$(dirname "$0")/backend/" backend/
echo "Overlay applied to exact current-main commit."

#!/data/data/com.termux/files/usr/bin/bash
set -e
PATCH_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="/storage/emulated/0/Download/GlobalHostIntelligence-push"
test -d "$REPO/.git"
cp -a "$PATCH_DIR/android/." "$REPO/android/"
cp -f "$PATCH_DIR/android/gradle/libs.versions.toml" "$REPO/android/gradle/libs.versions.toml"
cp -f "$PATCH_DIR/android/core/ui/build.gradle.kts" "$REPO/android/core/ui/build.gradle.kts"
cp -a "$PATCH_DIR/backend/." "$REPO/backend/"
cd "$REPO"
git diff --check
git add -A
echo "=== GHI 3.1.0 FULL REPAIR/UPGRADE ==="
git status --short
git diff --cached --stat

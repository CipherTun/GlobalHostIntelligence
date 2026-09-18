#!/system/bin/sh
set -eu

ROOT="${1:-/storage/emulated/0/Download/GlobalHostIntelligence-push}"
PATCH_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if [ ! -d "$ROOT/.git" ]; then
  echo "Repository not found: $ROOT"
  echo "Pass the repository path as the first argument."
  exit 1
fi

copy_file() {
  src="$PATCH_DIR/$1"
  dst="$ROOT/$1"
  mkdir -p "$(dirname "$dst")"
  cp -f "$src" "$dst"
  echo "updated: $1"
}

copy_file "android/core/ui/src/main/java/io/ciphertun/ghi/core/ui/navigation/GhiRoute.kt"
copy_file "android/core/crawlercore/src/main/java/io/ciphertun/ghi/core/crawlercore/GhiMobileBridge.kt"
copy_file "android/app/src/main/java/io/ciphertun/ghi/app/GhiNavHost.kt"
copy_file "android/app/src/main/java/io/ciphertun/ghi/app/GhiAppChrome.kt"
copy_file "android/app/src/main/java/io/ciphertun/ghi/app/GhiSessionTools.kt"
copy_file "android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/SecurityToolsScreens.kt"
copy_file "backend/mobile/tls_dns_tools.go"
copy_file "backend/mobile/tls_dns_tools_test.go"

cd "$ROOT"
git diff --check
echo "Overlay applied and git diff --check passed."

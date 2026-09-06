#!/data/data/com.termux/files/usr/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$ROOT/GlobalHostIntelligence-push"
if [ ! -d "$REPO/.git" ]; then
  echo "ERROR: expected checkout at $REPO"
  exit 1
fi
cp -f "$ROOT/ghi_upgrade_patch/backend/mobile/discovery_engine.go" "$REPO/backend/mobile/discovery_engine.go"
cp -f "$ROOT/ghi_upgrade_patch/backend/mobile/cdn_evidence.go" "$REPO/backend/mobile/cdn_evidence.go"
cp -f "$ROOT/ghi_upgrade_patch/android/app/src/main/java/io/ciphertun/ghi/app/GhiSession.kt" "$REPO/android/app/src/main/java/io/ciphertun/ghi/app/GhiSession.kt"
cp -f "$ROOT/ghi_upgrade_patch/android/app/src/main/java/io/ciphertun/ghi/app/GhiNavHost.kt" "$REPO/android/app/src/main/java/io/ciphertun/ghi/app/GhiNavHost.kt"
cp -f "$ROOT/ghi_upgrade_patch/android/app/src/main/java/io/ciphertun/ghi/app/GhiAppChrome.kt" "$REPO/android/app/src/main/java/io/ciphertun/ghi/app/GhiAppChrome.kt"
cp -f "$ROOT/ghi_upgrade_patch/android/core/crawlercore/src/main/java/io/ciphertun/ghi/core/crawlercore/GhiMobileBridge.kt" "$REPO/android/core/crawlercore/src/main/java/io/ciphertun/ghi/core/crawlercore/GhiMobileBridge.kt"
cp -f "$ROOT/ghi_upgrade_patch/android/core/ui/src/main/java/io/ciphertun/ghi/core/ui/navigation/GhiRoute.kt" "$REPO/android/core/ui/src/main/java/io/ciphertun/ghi/core/ui/navigation/GhiRoute.kt"
cp -f "$ROOT/ghi_upgrade_patch/android/core/ui/src/main/java/io/ciphertun/ghi/core/ui/components/GhiComponents.kt" "$REPO/android/core/ui/src/main/java/io/ciphertun/ghi/core/ui/components/GhiComponents.kt"
cp -f "$ROOT/ghi_upgrade_patch/android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/SubdomainsScreen.kt" "$REPO/android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/SubdomainsScreen.kt"
cp -f "$ROOT/ghi_upgrade_patch/android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/PayloadGeneratorScreen.kt" "$REPO/android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/PayloadGeneratorScreen.kt"

# Replace the weak CDN-only label with evidence-based detection.
F="$REPO/backend/mobile/response_checker.go"
if [ -f "$F" ]; then
  sed -i 's/r\.CDN = detectCDN(resp\.Header)/r.CDN = detectCDNEvidence(resp.Header, r.Server, r.Addresses, u.Hostname())/' "$F"
fi
F="$REPO/backend/mobile/mobile.go"
if [ -f "$F" ]; then
  sed -i 's/CDN: detectCDN(resp\.Header)/CDN: detectCDNEvidence(resp.Header, resp.Header.Get("Server"), ips, host)/g' "$F" 2>/dev/null || true
  sed -i 's/GlobalHostIntelligence\/2\.3/GlobalHostIntelligence\/3.0/g' "$F" 2>/dev/null || true
fi
F="$REPO/android/feature/discover/src/main/java/io/ciphertun/ghi/feature/discover/DiscoverScreen.kt"
if [ -f "$F" ]; then
  sed -i 's/status == "COMPLETED") StatusTone.OK else StatusTone.NEUTRAL/status == "COMPLETED") StatusTone.OK else if (status == "PARTIAL") StatusTone.WARN else StatusTone.NEUTRAL/' "$F"
  sed -i 's/Parallel passive discovery + concurrent HTTP\/HTTPS validation\. Only live 2xx–3xx hosts are promoted\./Live multi-source discovery with independent source failures, one-pass validation, and a 500-host target./' "$F"
fi
F="$REPO/android/app/build.gradle.kts"
if [ -f "$F" ]; then
  sed -i 's/versionCode = 240/versionCode = 310/' "$F"
  sed -i 's/versionName = "2\.4\.0"/versionName = "3.1.0"/' "$F"
fi

# Keep the new source in the source selector's default set without exposing the
# internal fast country adapter as a separate provider.
F="$REPO/android/app/src/main/java/io/ciphertun/ghi/app/GhiSession.kt"
sed -i 's/"country")/"country")/' "$F" 2>/dev/null || true

cd "$REPO"
git diff --check
git status --short
printf '\nUpgrade staged in working tree. Review above, then commit/push normally.\n'

#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
git config --global --add safe.directory "$(pwd)"
git remote get-url origin >/dev/null 2>&1 || git remote add origin https://github.com/CipherTun/GlobalHostIntelligence.git
git add -A
git commit -m "Upgrade GHI premium navy UI and embedded discovery engine" || true
git push origin main

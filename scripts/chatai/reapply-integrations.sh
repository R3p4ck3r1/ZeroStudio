#!/usr/bin/env bash
set -euo pipefail

# Reapply local integration adjustments after syncing upstream RikkaHub into core/chatai.

# 1) chatai app module must stay library (no standalone applicationId setup here)
# 2) avoid manifest application-level conflicts with host core/app
MANIFEST="core/chatai/app/src/main/AndroidManifest.xml"
if [[ -f "$MANIFEST" ]]; then
  python3 - <<'PY'
from pathlib import Path
p = Path("core/chatai/app/src/main/AndroidManifest.xml")
s = p.read_text(encoding="utf-8")
# Strip common application-level attrs that conflict when merged into host app
for attr in [
    'android:name=".RikkaHubApp"',
    'android:icon="@mipmap/ic_launcher"',
    'android:label="@string/app_name"',
    'android:theme="@style/Theme.Rikkahub"',
    'android:allowBackup="true"',
    'android:largeHeap="true"',
    'android:supportsRtl="true"',
    'android:usesCleartextTraffic="true"',
    'android:appCategory="productivity"',
    'android:dataExtractionRules="@xml/data_extraction_rules"',
    'android:fullBackupContent="@xml/backup_rules"',
    'android:enableOnBackInvokedCallback="true"',
]:
    s = s.replace("\n    " + attr, "")
if 'tools:node="merge"' not in s:
    s = s.replace('<application', '<application\n    tools:node="merge"', 1)
p.write_text(s, encoding="utf-8")
PY
fi

# 3) ensure web build uses pnpm (current project workflow installs it)
WEB_KTS="core/chatai/web/build.gradle.kts"
if [[ -f "$WEB_KTS" ]]; then
  grep -q 'commandLine("pnpm", "run", "build")' "$WEB_KTS" || {
    echo "WARN: expected pnpm build command not found in $WEB_KTS"
  }
fi

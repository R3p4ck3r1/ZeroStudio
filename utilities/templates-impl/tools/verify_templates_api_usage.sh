#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_DIR="$ROOT_DIR/templates-impl"

legacy_refs=$(rg -n "com\\.android\\.tools\\.idea\\.wizard\\.template" "$TARGET_DIR" --glob '!tools/*' || true)
if [[ -n "$legacy_refs" ]]; then
  echo "[FAIL] Found legacy wizard template imports:"
  echo "$legacy_refs"
  exit 1
fi

echo "[OK] No legacy wizard template imports found in utilities/templates-impl source files."

api_refs=$(rg -n "^import com\\.itsaky\\.androidide\\.templates" "$TARGET_DIR" --glob '*.kt' | wc -l)
echo "[OK] Found $api_refs templates-api import references in utilities/templates-impl Kotlin sources."

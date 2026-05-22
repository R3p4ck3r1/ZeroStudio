#!/usr/bin/env bash
set -euo pipefail

# Sync bazelbuild/remote-apis into tooling/.tooling/reapi/remote-apis
# Requires network access to GitHub.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP_DIR="${TMPDIR:-/tmp}/remote-apis-sync-$$"
TARGET_DIR="$ROOT_DIR/remote-apis"

mkdir -p "$TMP_DIR"
trap 'rm -rf "$TMP_DIR"' EXIT

git clone https://github.com/bazelbuild/remote-apis "$TMP_DIR/remote-apis"

rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR"
cp -R "$TMP_DIR/remote-apis"/* "$TARGET_DIR/"

# Required by current Iteration2 integrated stack seam (proto + generated-java inputs)
mkdir -p "$ROOT_DIR/required-slices"
cat > "$ROOT_DIR/required-slices/paths.txt" <<'PATHS'
build/bazel/remote/execution/v2
build/bazel/semver
build/bazel/remote/asset/v1
google/bytestream
google/longrunning
google/rpc
PATHS

echo "remote-apis synced to $TARGET_DIR"

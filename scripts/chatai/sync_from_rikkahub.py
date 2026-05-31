#!/usr/bin/env python3
"""Merge an upstream RikkaHub checkout into core/chatai.

The sync is intentionally conservative: it updates files from upstream, but it
preserves local ZeroStudio edits when a base checkout is available. Kotlin Gradle
script files are protected by default because core/chatai uses local module wiring
that should not be overwritten by upstream RikkaHub build scripts.
"""

from __future__ import annotations

import argparse
import fnmatch
import filecmp
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

DEFAULT_EXCLUDES = (
    ".git/**",
    ".gradle/**",
    "build/**",
    "local.properties",
    "**/build/**",
    "**/.gradle/**",
    "**/node_modules/**",
)

DEFAULT_PROTECTED = (
    "*.kts",
    "**/*.kts",
)


def parse_patterns(values: list[str], defaults: tuple[str, ...]) -> list[str]:
    patterns: list[str] = list(defaults)
    for value in values:
        patterns.extend(part.strip() for part in value.split(",") if part.strip())
    return patterns


def matches(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(path, pattern) for pattern in patterns)


def files_under(root: Path, excludes: list[str]) -> dict[str, Path]:
    result: dict[str, Path] = {}
    if not root.exists():
        return result
    for path in root.rglob("*"):
        if not path.is_file() and not path.is_symlink():
            continue
        rel = path.relative_to(root).as_posix()
        if matches(rel, excludes):
            continue
        result[rel] = path
    return result


def same_file(left: Path, right: Path) -> bool:
    if not left.exists() or not right.exists():
        return False
    if left.is_symlink() or right.is_symlink():
        return left.is_symlink() and right.is_symlink() and os.readlink(left) == os.readlink(right)
    return filecmp.cmp(left, right, shallow=False)


def copy_path(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if source.is_symlink():
        if target.exists() or target.is_symlink():
            target.unlink()
        os.symlink(os.readlink(source), target)
        return
    shutil.copy2(source, target)


def remove_empty_dirs(root: Path) -> None:
    for path in sorted((p for p in root.rglob("*") if p.is_dir()), reverse=True):
        try:
            path.rmdir()
        except OSError:
            pass


def merge_text_file(target: Path, base: Path, source: Path) -> bool:
    with tempfile.NamedTemporaryFile(prefix="chatai-merge-", delete=False) as out:
        out_path = Path(out.name)
    try:
        result = subprocess.run(
            ["git", "merge-file", "-p", str(target), str(base), str(source)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        target.write_text(result.stdout, encoding="utf-8")
        if result.returncode not in (0, 1):
            sys.stderr.write(result.stderr)
            return False
        return result.returncode == 0
    finally:
        out_path.unlink(missing_ok=True)


def sync(args: argparse.Namespace) -> int:
    source_root = args.source.resolve()
    target_root = args.target.resolve()
    base_root = args.base.resolve() if args.base else None
    excludes = parse_patterns(args.exclude, DEFAULT_EXCLUDES)
    protected = parse_patterns(args.protect, DEFAULT_PROTECTED)

    source_files = files_under(source_root, excludes)
    target_files = files_under(target_root, excludes)
    base_files = files_under(base_root, excludes) if base_root else {}

    changed = conflicts = preserved = deleted = 0
    all_paths = sorted(set(source_files) | set(target_files))

    for rel in all_paths:
        source = source_files.get(rel)
        target = target_files.get(rel)
        base = base_files.get(rel)
        protected_path = matches(rel, protected)

        if source is None:
            if target is None or protected_path:
                preserved += int(target is not None and protected_path)
                continue
            if base is not None and same_file(target, base):
                target.unlink()
                deleted += 1
            else:
                preserved += 1
            continue

        target_path = target_root / rel
        if target is None:
            copy_path(source, target_path)
            changed += 1
            continue

        if protected_path:
            # Keep existing local build/script wiring. New protected files were handled
            # above, but existing ones are never overwritten by the upstream checkout.
            preserved += 1
            continue

        if same_file(target, source):
            continue

        if base is None:
            copy_path(source, target_path)
            changed += 1
            continue

        if same_file(source, base):
            preserved += 1
            continue

        if same_file(target, base):
            copy_path(source, target_path)
            changed += 1
            continue

        if target.is_symlink() or source.is_symlink() or base.is_symlink():
            preserved += 1
            conflicts += 1
            print(f"CONFLICT symlink/manual merge required: {rel}", file=sys.stderr)
            continue

        clean = merge_text_file(target, base, source)
        changed += 1
        if not clean:
            conflicts += 1
            print(f"CONFLICT markers written: {rel}", file=sys.stderr)

    remove_empty_dirs(target_root)
    print(
        f"RikkaHub sync complete: changed={changed}, deleted={deleted}, "
        f"preserved={preserved}, conflicts={conflicts}"
    )
    return 1 if conflicts else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path, help="Upstream RikkaHub checkout")
    parser.add_argument("--target", required=True, type=Path, help="core/chatai target directory")
    parser.add_argument("--base", type=Path, help="Optional previous upstream checkout for 3-way merge")
    parser.add_argument("--exclude", action="append", default=[], help="Comma-separated exclude glob(s)")
    parser.add_argument("--protect", action="append", default=[], help="Comma-separated protected glob(s)")
    return sync(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())

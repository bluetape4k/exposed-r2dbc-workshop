#!/usr/bin/env python3
"""Select Gradle test/report tasks for the changed R2DBC modules.

The CI workflow keeps the full DB matrix, but a change-triggered run only needs
the module tasks affected by its source paths. Root, shared-module, and workflow
changes use the existing full-project tasks so shared build contracts remain covered.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MODULE_DIRS = {
    "00-shared",
    "01-spring-boot",
    "03-exposed-r2dbc-basic",
    "04-exposed-r2dbc-ddl",
    "05-exposed-r2dbc-dml",
    "06-advanced",
    "07-jpa-convert",
    "08-r2dbc-coroutines",
    "09-spring",
    "10-multi-tenant",
    "11-high-performance",
    "12-production-integration",
    "13-ecosystem-integrations",
}
SHARED_MODULE_PREFIXES = {"00-shared"}


def parse_files(value: str) -> list[Path]:
    try:
        files = json.loads(value or "[]")
    except json.JSONDecodeError as error:
        raise SystemExit(f"changed module file list is not valid JSON: {error}") from error
    if not isinstance(files, list) or not all(isinstance(item, str) for item in files):
        raise SystemExit("changed module file list must be a JSON array of strings")
    return [Path(item) for item in files]


def module_name(path: Path) -> str | None:
    if not path.parts or path.parts[0] not in MODULE_DIRS:
        return None
    for length in range(len(path.parts), 1, -1):
        candidate = REPO_ROOT.joinpath(*path.parts[:length])
        if (candidate / "build.gradle.kts").is_file():
            return candidate.name
    return None


def selected_modules(files: list[Path], full: bool) -> list[str] | None:
    if full:
        return None
    if any(path.parts and path.parts[0] in SHARED_MODULE_PREFIXES for path in files):
        return None
    modules = {name for path in files if (name := module_name(path))}
    # An unknown module path is safer as a full run than as an empty check.
    unknown_module_paths = any(
        path.parts and path.parts[0] in MODULE_DIRS and module_name(path) is None
        for path in files
    )
    if not modules or unknown_module_paths:
        return None
    return sorted(modules)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--files-json", required=True)
    parser.add_argument("--full", action="store_true")
    parser.add_argument("--task", choices=("test", "koverXmlReport"), required=True)
    args = parser.parse_args()

    modules = selected_modules(parse_files(args.files_json), args.full)
    if modules is None:
        print(args.task)
    else:
        for module in modules:
            print(f":{module}:{args.task}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

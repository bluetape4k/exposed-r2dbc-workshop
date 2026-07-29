#!/usr/bin/env python3
"""Audit Korean localization scope for single-language docs and Kotlin comments."""

from __future__ import annotations

import argparse
import re
from collections import defaultdict
from pathlib import Path


DOC_SUFFIXES = {".md", ".mdx", ".adoc"}
KOTLIN_SUFFIXES = {".kt", ".kts"}
EXCLUDED_DIRS = {".git", ".gradle", ".idea", ".kotlin", ".worktrees", "build"}
OPERATING_DOC_NAMES = {"AGENTS.md", "CLAUDE.md"}
README_NAMES = {"README.md", "README.ko.md"}
COMMENT_RE = re.compile(r"(^\s*//|/\*\*|\*\s|@param|@property|@return|@throws|TODO|FIXME)")
KDOC_TAG_RE = re.compile(r"@(property|param|return|throws)\b")


def iter_files(root: Path):
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(root)
        if any(part in EXCLUDED_DIRS for part in rel.parts):
            continue
        yield rel


def is_doc(path: Path) -> bool:
    return path.suffix in DOC_SUFFIXES


def is_kotlin(path: Path) -> bool:
    return path.suffix in KOTLIN_SUFFIXES


def first_bucket(path: Path) -> str:
    if len(path.parts) == 1:
        return "."
    return path.parts[0]


def is_operating_doc(path: Path) -> bool:
    return (
        path.name in OPERATING_DOC_NAMES
        or path.parts[0] == ".github"
        or path.parts[0] == ".omc"
        or path.parts[:2] == ("docs", "korean-rewrite")
    )


def is_readme_pair_member(path: Path) -> bool:
    return path.name in README_NAMES


def read_text(root: Path, rel: Path) -> str:
    return (root / rel).read_text(encoding="utf-8", errors="replace")


def count_matching_lines(text: str, pattern: re.Pattern[str]) -> int:
    return sum(1 for line in text.splitlines() if pattern.search(line))


def collect(root: Path) -> dict[str, object]:
    files = list(iter_files(root))
    docs = [p for p in files if is_doc(p)]
    kotlin = [p for p in files if is_kotlin(p)]

    primary_docs = [
        p for p in docs
        if not is_readme_pair_member(p) and not is_operating_doc(p)
    ]
    readme_pairs = [p for p in docs if is_readme_pair_member(p)]
    operating_docs = [p for p in docs if is_operating_doc(p)]

    doc_buckets: dict[str, list[Path]] = defaultdict(list)
    for path in primary_docs:
        if path.parts[:2] == ("docs", "lessons"):
            doc_buckets["docs/lessons"].append(path)
        elif path.parts[:2] == ("docs", "review"):
            doc_buckets["docs/review"].append(path)
        elif path.parts[:3] == ("docs", "superpowers", "plans"):
            doc_buckets["docs/superpowers/plans"].append(path)
        elif path.parts[:3] == ("docs", "superpowers", "specs"):
            doc_buckets["docs/superpowers/specs"].append(path)
        elif len(path.parts) == 1:
            doc_buckets["root"].append(path)
        else:
            doc_buckets[first_bucket(path)].append(path)

    kotlin_buckets: dict[str, dict[str, int]] = defaultdict(lambda: {"files": 0, "comment_lines": 0, "kdoc_tags": 0})
    total_comment_lines = 0
    total_kdoc_tags = 0
    for path in kotlin:
        text = read_text(root, path)
        comment_lines = count_matching_lines(text, COMMENT_RE)
        kdoc_tags = len(KDOC_TAG_RE.findall(text))
        bucket = first_bucket(path)
        kotlin_buckets[bucket]["files"] += 1
        kotlin_buckets[bucket]["comment_lines"] += comment_lines
        kotlin_buckets[bucket]["kdoc_tags"] += kdoc_tags
        total_comment_lines += comment_lines
        total_kdoc_tags += kdoc_tags

    return {
        "primary_docs": primary_docs,
        "readme_pairs": readme_pairs,
        "operating_docs": operating_docs,
        "doc_buckets": dict(sorted(doc_buckets.items())),
        "kotlin_files": kotlin,
        "kotlin_buckets": dict(sorted(kotlin_buckets.items())),
        "comment_lines": total_comment_lines,
        "kdoc_tags": total_kdoc_tags,
    }


def print_markdown(root: Path, data: dict[str, object]) -> None:
    primary_docs = data["primary_docs"]
    readme_pairs = data["readme_pairs"]
    operating_docs = data["operating_docs"]
    kotlin_files = data["kotlin_files"]

    print("# Korean Localization Scope Audit")
    print()
    print("## Summary")
    print()
    print(f"- Repository root: `{root}`")
    print(f"- Primary single-language documentation files: {len(primary_docs)}")
    print(f"- Bilingual README pair files excluded from primary rewrite: {len(readme_pairs)}")
    print(f"- Operating/LLM-facing documentation files excluded: {len(operating_docs)}")
    print(f"- Kotlin/KTS files: {len(kotlin_files)}")
    print(f"- Kotlin/KTS comment or KDoc candidate lines: {data['comment_lines']}")
    print(f"- KDoc detail tag candidates: {data['kdoc_tags']}")
    print()
    print("## Primary Documentation Buckets")
    print()
    print("| Bucket | Files |")
    print("|---|---:|")
    for bucket, paths in data["doc_buckets"].items():
        print(f"| `{bucket}` | {len(paths)} |")
    print()
    print("## Kotlin Comment Buckets")
    print()
    print("| Bucket | Kotlin/KTS files | Comment/KDoc candidate lines | KDoc detail tags |")
    print("|---|---:|---:|---:|")
    for bucket, stats in data["kotlin_buckets"].items():
        print(f"| `{bucket}` | {stats['files']} | {stats['comment_lines']} | {stats['kdoc_tags']} |")
    print()
    print("## Primary Single-Language Documents")
    print()
    for path in primary_docs:
        print(f"- `{path}`")
    print()
    print("## Excluded README Pair Files")
    print()
    for path in readme_pairs:
        print(f"- `{path}`")
    print()
    print("## Excluded Operating Docs")
    print()
    for path in operating_docs:
        print(f"- `{path}`")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    root = args.root.resolve()
    data = collect(root)
    print_markdown(root, data)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

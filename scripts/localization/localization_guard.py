#!/usr/bin/env python3
"""Guard Korean localization boundaries and optional stale-English candidates."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from audit_scope import collect


ENGLISH_WORD_RE = re.compile(r"\b[A-Za-z][A-Za-z'-]{2,}\b")
HANGUL_RE = re.compile(r"[가-힣]")
INLINE_CODE_RE = re.compile(r"`[^`]*`")
COMMENT_LINE_RE = re.compile(r"^\s*(//|/\*\*|\*|/\*)")


def strip_inline_code(line: str) -> str:
    return INLINE_CODE_RE.sub("", line)


def has_stale_english_candidate(line: str) -> bool:
    text = strip_inline_code(line)
    if not ENGLISH_WORD_RE.search(text):
        return False
    return not HANGUL_RE.search(text)


def read_text(root: Path, rel: Path) -> str:
    return (root / rel).read_text(encoding="utf-8", errors="replace")


def validate_readme_pairs(readme_paths: list[Path]) -> list[str]:
    names = {path for path in readme_paths}
    failures: list[str] = []
    for path in sorted(readme_paths):
        if path.name == "README.md":
            expected = path.with_name("README.ko.md")
            if expected not in names:
                failures.append(f"`{path}` is missing sibling `{expected}`")
        elif path.name == "README.ko.md":
            expected = path.with_name("README.md")
            if expected not in names:
                failures.append(f"`{path}` is missing sibling `{expected}`")
    return failures


def validate_operating_exclusion(primary_docs: list[Path]) -> list[str]:
    failures: list[str] = []
    for path in primary_docs:
        if path.name in {"AGENTS.md", "CLAUDE.md"}:
            failures.append(f"operating doc leaked into primary scope: `{path}`")
        if path.parts and path.parts[0] in {".github", ".omc"}:
            failures.append(f"LLM-facing path leaked into primary scope: `{path}`")
        if path.parts[:2] == ("docs", "korean-rewrite"):
            failures.append(f"scope-control artifact leaked into primary scope: `{path}`")
        if path.name in {"README.md", "README.ko.md"}:
            failures.append(f"bilingual README leaked into primary scope: `{path}`")
    return failures


def scan_doc_english(root: Path, docs: list[Path], limit: int) -> tuple[int, list[str]]:
    count = 0
    examples: list[str] = []
    for path in docs:
        in_fence = False
        for lineno, line in enumerate(read_text(root, path).splitlines(), start=1):
            if line.strip().startswith("```"):
                in_fence = not in_fence
                continue
            if in_fence or not has_stale_english_candidate(line):
                continue
            count += 1
            if len(examples) < limit:
                examples.append(f"{path}:{lineno}: {line.strip()}")
    return count, examples


def scan_comment_english(root: Path, kotlin_files: list[Path], limit: int) -> tuple[int, list[str]]:
    count = 0
    examples: list[str] = []
    in_block = False
    for path in kotlin_files:
        for lineno, line in enumerate(read_text(root, path).splitlines(), start=1):
            stripped = line.strip()
            if stripped.startswith("/*"):
                in_block = True
            is_comment = in_block or COMMENT_LINE_RE.match(line)
            if not is_comment:
                continue
            if has_stale_english_candidate(line):
                count += 1
                if len(examples) < limit:
                    examples.append(f"{path}:{lineno}: {stripped}")
            if "*/" in stripped:
                in_block = False
    return count, examples


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--strict-korean", action="store_true")
    parser.add_argument("--max-doc-english-lines", type=int, default=0)
    parser.add_argument("--max-comment-english-lines", type=int, default=0)
    parser.add_argument("--example-limit", type=int, default=20)
    args = parser.parse_args()

    root = args.root.resolve()
    data = collect(root)
    primary_docs = data["primary_docs"]
    readme_pairs = data["readme_pairs"]
    kotlin_files = data["kotlin_files"]

    failures = []
    failures.extend(validate_readme_pairs(readme_pairs))
    failures.extend(validate_operating_exclusion(primary_docs))

    doc_english, doc_examples = scan_doc_english(root, primary_docs, args.example_limit)
    comment_english, comment_examples = scan_comment_english(root, kotlin_files, args.example_limit)

    print("Korean localization guard")
    print(f"- primary docs: {len(primary_docs)}")
    print(f"- README pair files: {len(readme_pairs)}")
    print(f"- Kotlin/KTS files: {len(kotlin_files)}")
    print(f"- stale-English doc candidates: {doc_english}")
    print(f"- stale-English comment candidates: {comment_english}")

    if args.strict_korean:
        if doc_english > args.max_doc_english_lines:
            failures.append(
                f"doc stale-English candidates {doc_english} exceed limit {args.max_doc_english_lines}"
            )
        if comment_english > args.max_comment_english_lines:
            failures.append(
                f"comment stale-English candidates {comment_english} exceed limit {args.max_comment_english_lines}"
            )

    if doc_examples:
        print()
        print("Doc candidate examples:")
        for example in doc_examples:
            print(f"- {example}")
    if comment_examples:
        print()
        print("Comment candidate examples:")
        for example in comment_examples:
            print(f"- {example}")

    if failures:
        print()
        print("Failures:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

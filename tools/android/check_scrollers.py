#!/usr/bin/env python3
"""Fails when a horizontal scroller can crash on an infinite max width.

Rules (docs/M3X_DESIGN_SYSTEM.md 13.4):
  - bare `horizontalScroll(` only inside ui/components/SafeScroll.kt (use nebHorizontalScroll)
  - every LazyRow( / HorizontalPager( call carries `boundedWidth()` in its first lines
"""
import pathlib, re, sys

root = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/java"
bad = []
for f in root.rglob("*.kt"):
    text = f.read_text()
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if line.lstrip().startswith(("import ", "//", "*", "/*")):
            continue
        if re.search(r"(?<!neb)(?<![A-Za-z])horizontalScroll\(", line) and f.name != "SafeScroll.kt":
            bad.append(f"{f}:{i+1}: bare horizontalScroll, use nebHorizontalScroll")
        if re.search(r"\b(LazyRow|HorizontalPager)\(", line) and "fun " not in line:
            if "boundedWidth()" not in "\n".join(lines[i:i + 9]):
                bad.append(f"{f}:{i+1}: {line.strip()[:40]} without Modifier.boundedWidth()")
print("\n".join(bad) or "scrollers ok")
sys.exit(1 if bad else 0)

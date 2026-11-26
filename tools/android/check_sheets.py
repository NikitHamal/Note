#!/usr/bin/env python3
"""Fails when a bottom sheet can ship without the shared padding / status bar rules.

Rules (docs/M3X_DESIGN_SYSTEM.md 13.7):
  - New sheets use NebModalSheet (ui/components/NebSheet.kt). A raw ModalBottomSheet( is only allowed in the
    legacy list below; do not add to it, migrate instead.
  - NebModalSheet pads its content and stops below the status bar by itself. `fullBleed = true` is only for
    content that draws edge to edge rows or pads itself, and the call must say so with a `// bleed:` comment
    within its first 14 lines.
"""
import pathlib, re, sys

root = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/java"
LEGACY_RAW = {"NebSheet.kt", "LiquidGlassProfileSheet.kt", "NebPickerSheet.kt", "ReadingSheet.kt",
              "AuthLegalSheet.kt"}
bad = []
for f in root.rglob("*.kt"):
    lines = f.read_text().splitlines()
    for i, line in enumerate(lines):
        t = line.lstrip()
        if t.startswith(("import ", "//", "*", "/*")) or "fun " in line:
            continue
        if re.search(r"\bModalBottomSheet\(", line) and f.name not in LEGACY_RAW:
            bad.append(f"{f}:{i+1}: raw ModalBottomSheet, use NebModalSheet")
        if "NebModalSheet(" in line:
            block = "\n".join(lines[i:i + 14])
            if "fullBleed = true" in block and "// bleed:" not in block:
                bad.append(f"{f}:{i+1}: fullBleed = true needs a `// bleed: why` comment")
print("\n".join(bad) or "sheets ok")
sys.exit(1 if bad else 0)

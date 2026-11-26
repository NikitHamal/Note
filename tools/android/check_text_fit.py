#!/usr/bin/env python3
"""Ratchet for text that can wrap badly, overflow or lap onto its neighbour (docs/M3X_DESIGN_SYSTEM.md 13.8).

A `Text(` that sits straight inside a `Row(` (a Row opens within the 10 lines above it), has no
`maxLines`, no `weight(` / `fillMaxWidth` and no `overflow`, is a risk: a long name, title or label
pushes its neighbour (badge, time, count, chevron) out of the row or wraps one word per line.

The existing count per file is frozen in tools/android/text_fit_baseline.json. The check fails only
when a file gets MORE risky texts than its baseline, so old code is migrated gradually and new code
is clean. Run `python3 tools/android/check_text_fit.py --update` after you FIX some (never to hide new ones).
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java"
BASE = Path(__file__).with_name("text_fit_baseline.json")
SKIP = ("/ui/sim/", "/screens/notes/", "/screens/reader/")
TEXT = re.compile(r"(?<![A-Za-z0-9_.])Text\(")


def args_of(src: str, start: int) -> str:
    depth, i = 0, start
    while i < len(src):
        c = src[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return src[start:i]
        i += 1
    return ""


def risky(path: Path) -> list[int]:
    src = path.read_text(encoding="utf-8", errors="ignore")
    lines = src.splitlines()
    out = []
    for m in TEXT.finditer(src):
        a = args_of(src, m.end() - 1)
        if any(k in a for k in ("maxLines", "weight(", "fillMaxWidth", "overflow", "softWrap")):
            continue
        ln = src.count("\n", 0, m.start())
        if "fit-ok" in lines[ln] or (ln and "fit-ok" in lines[ln - 1]):
            continue
        # a plain string literal of a few characters cannot overflow
        lit = re.match(r'\s*"([^"$\\]{0,18})"\s*[,)]?', a)
        if lit and a.strip().startswith('"' + lit.group(1) + '"') and "$" not in a.split(",")[0]:
            continue
        window = "\n".join(lines[max(0, ln - 10):ln])
        if re.search(r"\bRow\(", window) and "Column(" not in window.split("Row(")[-1]:
            out.append(ln + 1)
    return out


def main() -> int:
    found = {}
    for p in sorted(SRC.rglob("*.kt")):
        rel = "/" + p.relative_to(SRC).as_posix()
        if any(s in rel for s in SKIP):
            continue
        hits = risky(p)
        if hits:
            found[rel] = hits
    counts = {k: len(v) for k, v in found.items()}
    if "--update" in sys.argv or not BASE.exists():
        BASE.write_text(json.dumps(counts, indent=1, sort_keys=True) + "\n")
        print(f"baseline written: {sum(counts.values())} in {len(counts)} files")
        return 0
    base = json.loads(BASE.read_text())
    bad = [(k, v, base.get(k, 0)) for k, v in counts.items() if v > base.get(k, 0)]
    for k, v, b in bad:
        print(f"FAIL {k}: {v} Text(...) in a Row without maxLines/weight (baseline {b}) at lines {found[k]}")
    if bad:
        print("Give the Text maxLines + overflow = Ellipsis, or Modifier.weight(1f, false); // fit-ok: reason to opt out.")
        return 1
    print("text fit ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Turn the print theme (notes/pipeline/theme.css) into the reading stylesheet used by the Android reader.

The print theme is the design of every component (cards, choices, fact sheets, derivations, tables).
Rather than restyle them by hand and drift, the reader takes the same rules and changes only the units:
mm and pt become rem, relative to the 10.7 pt body, so the whole page scales with the reader's text size.
Page rules, running heads, break rules and the cover / contents / about furniture are dropped.
Run: python3 tools/android/make_reader_css.py  ->  app/src/main/assets/reader/book.css
"""
import re, sys, os
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
src = open(os.path.join(ROOT, "notes/pipeline/theme.css"), encoding="utf-8").read()
src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)

def blocks(css):
    """Yield (selector, body) for flat rules; at-rules are skipped whole."""
    i, n = 0, len(css)
    while i < n:
        j = css.find("{", i)
        if j < 0:
            break
        sel = css[i:j].strip()
        depth, k = 1, j + 1
        while k < n and depth:
            depth += {"{": 1, "}": -1}.get(css[k], 0)
            k += 1
        yield sel, css[j + 1:k - 1]
        i = k

DROP_SEL = re.compile(r"^@|^\.cover|^\.frontmatter|^\.fm-|^\.about|^\.toc|^\.tiles?\b|^\.tile\b|^\.credits|^\.fm|^\.card\b|^html\s*\{?$|^html$|^body$|^:root$")
DROP_PROP = re.compile(r"^\s*(break-[a-z]+|page-break-[a-z-]+|page|string-set|orphans|widows|box-decoration-break|"
                       r"hyphens|bookmark-[a-z]+|text-justify)\s*:", re.I)
K_MM, K_PT = 2.8346 / 10.7, 1 / 10.7

def conv(v):
    v = re.sub(r"(-?[\d.]+)mm", lambda m: f"{float(m.group(1)) * K_MM:.3f}rem", v)
    v = re.sub(r"(-?[\d.]+)pt", lambda m: f"{float(m.group(1)) * K_PT:.3f}rem", v)
    return v

out = []
for sel, body in blocks(src):
    if sel.startswith("@") or DROP_SEL.match(sel):
        # keep the root variables (colour roles) but not the page rules
        if sel == ":root":
            out.append(f":root{{{body}}}")
        continue
    props = [p for p in body.split(";") if p.strip() and not DROP_PROP.match(p)]
    props = [p for p in props if "counter(" not in p and "var(--page" not in p]
    if not props:
        continue
    out.append(f"{sel}{{{conv(';'.join(props))}}}")
dest = os.path.join(ROOT, "app/src/main/assets/reader/book.css")
open(dest, "w", encoding="utf-8").write("\n".join(out) + "\n")
print(len(out), "rules ->", dest, os.path.getsize(dest))

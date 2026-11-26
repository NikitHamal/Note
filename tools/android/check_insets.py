#!/usr/bin/env python3
"""Fail the build when a screen can draw under the status bar, or a ghost action row is hand-aligned.

The app is edge to edge, so every full screen has to put its first row below the status bar. The one way to do
that is `NebTopBar(...)` (it applies `statusBarsPadding()` itself). A screen that draws its own back arrow row
must use one of the markers below, or carry `// inset-ok: <reason>` in the file (embedded in a host that
already pads, a dialog, a reader with its own chrome).

Rule 2: a ghost action row (like, reply, comments) under a post or comment uses `Modifier.nebGhostRowStart()`
so its first icon sits under the left edge of the text, never 12dp inside it.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/neb/ians/ui"
OK = re.compile(r"NebTopBar|statusBarsPadding|Scaffold|TopAppBar|WindowInsets\.statusBars|safeDrawing|systemBars|NebScreenHeader|inset-ok:")
BACK = r"Icons\.(AutoMirrored\.)?(Outlined|Filled|Default|Rounded|Sharp)\.ArrowBack"
bad = []
for f in list(UI.glob("screens/**/*.kt")) + list(UI.glob("sim/**/*.kt")):
    t = f.read_text()
    if "ArrowBack" in t and re.search(BACK, re.sub(r"^import .*$", "", t, flags=re.M)) and not OK.search(t):
        bad.append(f"{f.relative_to(ROOT)}: back arrow row without NebTopBar/statusBarsPadding (clashes with the status bar)")
# Rule 1b: a Scaffold with a back arrow needs a real topBar (an empty Scaffold content starts under the bar).
for f in list(UI.glob("screens/**/*.kt")):
    t = re.sub(r"^import .*$", "", f.read_text(), flags=re.M)
    if "Icons.AutoMirrored.Outlined.ArrowBack" in t and "Scaffold(" in t and "topBar" not in t and not re.search(r"NebTopBar|statusBarsPadding|inset-ok:", t):
        bad.append(f"{f.relative_to(ROOT)}: Scaffold with a back arrow but no topBar")
# Rule 2 applies to every file that draws a like pill, not a fixed list. Opt out with `// align-ok: reason`.
for f in UI.rglob("*.kt"):
    t = f.read_text()
    if "LikePill(" in t and "fun LikePill" not in t and "nebGhostRowStart" not in t and "align-ok:" not in t:
        bad.append(f"{f.relative_to(ROOT)}: LikePill row without nebGhostRowStart()")
# Rule 3: a screen never draws its own helper sentence under the list controls ("... Complete profiles are listed first.").
for f in UI.glob("screens/**/*.kt"):
    for n, line in enumerate(f.read_text().splitlines(), 1):
        if re.search(r"Text\(\"[^\"]*(are listed first|Fill what you like|whenever you like)", line):
            bad.append(f"{f.relative_to(ROOT)}:{n}: tagline or helper sentence (see section 14, no taglines)")
# Rule 4: dark mode comes from the app theme (MaterialTheme.colorScheme.surface.luminance() < 0.5f), never from the
# system flag, otherwise a user who forces Dark or Light in settings gets half a screen in the other mode.
for f in UI.rglob("*.kt"):
    if f.parent.name == "theme":
        continue
    t = re.sub(r"^import .*$", "", f.read_text(), flags=re.M)
    if "isSystemInDarkTheme(" in t and "theme-ok:" not in t:
        bad.append(f"{f.relative_to(ROOT)}: isSystemInDarkTheme() outside ui/theme (use the theme's surface luminance)")
if bad:
    print("\n".join(bad))
    sys.exit(1)
print("insets ok")

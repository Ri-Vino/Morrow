"""Trims Fraunces to the one axis Morrow varies, so the app ships a third of the file.

Morrow only sets Fraunces at weight 600 with the soft axis fully on and the default wonky glyphs, changing
just the optical size. Pinning the other three axes leaves outlines within a unit (of 2000) of the full font
and keeps every glyph. Run from the repo root with the full variable font from Google Fonts:

    python tools/trim_fraunces.py "Fraunces[SOFT,WONK,opsz,wght].ttf"

Needs fonttools. Writes shared/src/commonMain/composeResources/font/fraunces.ttf.
"""

import sys
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

OUT = Path(__file__).resolve().parent.parent / "shared" / "src" / "commonMain" / "composeResources" / "font" / "fraunces.ttf"

font = TTFont(sys.argv[1])
trimmed = instancer.instantiateVariableFont(font, {"wght": 600, "SOFT": 100, "WONK": 1}, updateFontNames=False)
trimmed.save(OUT)
print(f"Wrote {OUT} ({OUT.stat().st_size // 1024} KB)")

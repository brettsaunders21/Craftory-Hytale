"""Flat UI icons for the Squint Goggles energy HUD (Common/UI/Custom/Craftory/Hud/Energy/*@2x.png).

Drawn at 8x and downsampled so edges match the soft vanilla UI icons; sizes are @2x pixels.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

SS = 8
OUTLINE = (24, 22, 28, 255)
GOLD = (232, 169, 59, 255)
GOLD_D = (150, 98, 30, 255)
FIZZ = (128, 226, 86, 255)
FIZZ_D = (52, 140, 46, 255)
AMBER = (242, 179, 61, 255)
CYAN = (122, 214, 255, 255)
VIOLET = (170, 130, 255, 255)
RED = (226, 82, 72, 255)
GREY = (150, 158, 172, 255)
GREY_D = (84, 90, 104, 255)
WATER = (86, 160, 236, 255)
HI = (255, 255, 255, 200)


def canvas(w, h=None):
    h = h or w
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def S(*v):
    return [x * SS for x in v]


def done(img, w, h=None):
    return img.resize((w, h or w), Image.LANCZOS)


def circle(d, cx, cy, r, fill, outline=OUTLINE, width=2):
    d.ellipse(S(cx - r, cy - r, cx + r, cy + r), fill=fill, outline=outline, width=width * SS)


def grade_fizz():
    img, d = canvas(28)
    circle(d, 13, 15, 10, FIZZ)
    d.ellipse(S(7, 9, 12, 14), fill=HI)
    circle(d, 22, 6, 3.5, FIZZ, width=1)
    circle(d, 5, 24, 2.5, FIZZ_D, width=1)
    return done(img, 28)


def grade_pressurised():
    img, d = canvas(28)
    circle(d, 14, 14, 11, AMBER)
    circle(d, 14, 14, 7, (60, 44, 24, 255), outline=GOLD_D, width=1)
    a = math.radians(-50)
    d.line(S(14, 14, 14 + 6 * math.cos(a), 14 + 6 * math.sin(a)), fill=FIZZ, width=2 * SS)
    circle(d, 14, 14, 1.6, GOLD, width=0)
    return done(img, 28)


def grade_glimmer():
    img, d = canvas(28)
    pts = [(14, 2), (24, 12), (14, 26), (4, 12)]
    d.polygon([(x * SS, y * SS) for x, y in pts], fill=VIOLET, outline=OUTLINE, width=2 * SS)
    d.polygon([(x * SS, y * SS) for x, y in [(14, 5), (21, 12), (14, 12)]], fill=CYAN)
    d.polygon([(x * SS, y * SS) for x, y in [(14, 5), (7, 12), (14, 12)]], fill=(220, 240, 255, 255))
    return done(img, 28)


def stored():
    img, d = canvas(28)
    d.rounded_rectangle(S(6, 4, 22, 26), radius=3 * SS, fill=(48, 52, 62, 255), outline=OUTLINE, width=2 * SS)
    d.rectangle(S(9, 14, 19, 23), fill=FIZZ)
    d.rectangle(S(9, 14, 19, 15.5), fill=(200, 255, 170, 255))
    d.rectangle(S(10, 1.5, 18, 5), fill=GOLD, outline=OUTLINE, width=SS)
    return done(img, 28)


def arrow(col, into: bool):
    img, d = canvas(28)
    bar_x = 21 if into else 5
    d.rounded_rectangle(S(bar_x - 2, 4, bar_x + 2, 24), radius=SS, fill=GREY, outline=OUTLINE, width=SS)
    x0, x1 = (3, 18) if into else (9, 25)
    pts = [(x0, 11), (x1 - 7, 11), (x1 - 7, 6), (x1, 14), (x1 - 7, 22), (x1 - 7, 17), (x0, 17)]
    d.polygon([(x * SS, y * SS) for x, y in pts], fill=col, outline=OUTLINE, width=2 * SS)
    return done(img, 28)


def cog(fill=GREY, crack=True):
    img, d = canvas(28)
    for i in range(8):
        a = i * math.pi / 4
        cx, cy = 14 + 10 * math.cos(a), 14 + 10 * math.sin(a)
        d.rectangle(S(cx - 2.6, cy - 2.6, cx + 2.6, cy + 2.6), fill=fill, outline=OUTLINE, width=SS)
    circle(d, 14, 14, 9, fill)
    circle(d, 14, 14, 3.5, (40, 40, 48, 255), width=1)
    if crack:
        d.line(S(18, 5, 15.5, 10, 19, 13, 16, 18), fill=RED, width=2 * SS, joint="curve")
    return done(img, 28)


def chip(fill, edge):
    """9-slice source for `Border: 6` (12px @2x corners around a 12px stretchable middle)."""
    img, d = canvas(36)
    d.rounded_rectangle(S(0.5, 0.5, 35.5, 35.5), radius=8 * SS, fill=fill, outline=edge, width=2 * SS)
    return done(img, 36)


def st_running():
    img, d = canvas(24)
    d.polygon([(x * SS, y * SS) for x, y in [(7, 4), (20, 12), (7, 20)]], fill=FIZZ, outline=OUTLINE, width=SS)
    return done(img, 24)


def st_idle():
    img, d = canvas(24)
    for x in (6, 14):
        d.rounded_rectangle(S(x, 4, x + 5, 20), radius=SS, fill=GREY, outline=OUTLINE, width=SS)
    return done(img, 24)


def st_nopower():
    img, d = canvas(24)
    pts = [(14, 1), (5, 14), (11, 14), (9, 23), (19, 9), (13, 9)]
    d.polygon([(x * SS, y * SS) for x, y in pts], fill=RED, outline=OUTLINE, width=SS)
    return done(img, 24)


def st_warn(col):
    img, d = canvas(24)
    d.polygon([(x * SS, y * SS) for x, y in [(12, 2), (23, 21), (1, 21)]], fill=col, outline=OUTLINE, width=SS)
    d.rectangle(S(11, 8, 13, 15), fill=OUTLINE)
    d.rectangle(S(11, 17, 13, 19), fill=OUTLINE)
    return done(img, 24)


def bar(c0, c1):
    w, h = 284, 8
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for x in range(w):
        t = x / (w - 1)
        base = [round(a + (b - a) * t) for a, b in zip(c0[:3], c1[:3])]
        for y in range(h):
            k = 1.25 if y < 2 else (0.75 if y >= h - 2 else 1.0)
            px[x, y] = (*[min(255, round(v * k)) for v in base], 255)
    return img


def water():
    img, d = canvas(28)
    d.polygon([(x * SS, y * SS) for x, y in [(14, 2), (21, 15), (7, 15)]], fill=WATER)
    circle(d, 14, 17, 7.5, WATER, width=0)
    d.arc(S(6.5, 9.5, 21.5, 24.5), 0, 180, fill=OUTLINE, width=2 * SS)
    d.line(S(14, 2, 6.8, 15), fill=OUTLINE, width=2 * SS)
    d.line(S(14, 2, 21.2, 15), fill=OUTLINE, width=2 * SS)
    d.ellipse(S(9.5, 14, 12.5, 18), fill=HI)
    return done(img, 28)


def wind():
    img, d = canvas(28)
    for y, w, r in ((8, 18, 4), (15, 22, 4), (22, 14, 3)):
        d.line(S(3, y, w, y), fill=OUTLINE, width=4 * SS)
        d.line(S(3, y, w, y), fill=(220, 228, 240, 255), width=2 * SS)
    return done(img, 28)


def shroom():
    img, d = canvas(28)
    d.rounded_rectangle(S(11, 14, 17, 26), radius=2 * SS, fill=(236, 222, 196, 255), outline=OUTLINE, width=2 * SS)
    d.chord(S(3, 3, 25, 25), 180, 360, fill=RED, outline=OUTLINE, width=2 * SS)
    d.line(S(3, 14, 25, 14), fill=OUTLINE, width=2 * SS)
    for cx, cy in ((9, 9), (16, 7), (20, 11)):
        d.ellipse(S(cx - 1.6, cy - 1.6, cx + 1.6, cy + 1.6), fill=(255, 240, 230, 255))
    return done(img, 28)


def moon():
    img, d = canvas(28)
    circle(d, 14, 14, 10, (210, 214, 236, 255))
    d.ellipse(S(12, 1, 30, 19), fill=(0, 0, 0, 0))
    return done(img, 28)


def jar():
    img, d = canvas(28)
    d.rounded_rectangle(S(5, 7, 23, 26), radius=4 * SS, fill=(150, 200, 220, 140), outline=OUTLINE, width=2 * SS)
    d.rounded_rectangle(S(8, 15, 20, 23), radius=2 * SS, fill=VIOLET)
    d.rectangle(S(8, 2, 20, 7), fill=(120, 80, 50, 255), outline=OUTLINE, width=SS)
    return done(img, 28)


ICONS = {
    "GradeFizz": grade_fizz,
    "GradePressurised": grade_pressurised,
    "GradeGlimmer": grade_glimmer,
    "Stored": stored,
    "FlowIn": lambda: arrow(FIZZ, True),
    "FlowOut": lambda: arrow(AMBER, False),
    "Jank": cog,
    "ChipRunning": lambda: chip((28, 56, 34, 235), (95, 191, 90, 255)),
    "ChipIdle": lambda: chip((36, 41, 52, 235), (122, 132, 148, 255)),
    "ChipNoPower": lambda: chip((62, 26, 30, 235), (217, 83, 79, 255)),
    "ChipWarn": lambda: chip((60, 48, 24, 235), (232, 169, 59, 255)),
    "StatusRunning": st_running,
    "StatusIdle": st_idle,
    "StatusNoPower": st_nopower,
    "StatusStalled": lambda: st_warn(AMBER),
    "StatusStolen": lambda: st_warn(RED),
    "BarFizz": lambda: bar((70, 170, 60), (140, 235, 120)),
    "BarJank": lambda: bar((232, 169, 59), (226, 70, 60)),
    "BarJar": lambda: bar((120, 90, 220), (150, 210, 255)),
    "Water": water,
    "Wind": wind,
    "Shroom": shroom,
    "Moon": moon,
    "Jar": jar,
}


def export(out_dir: Path) -> list[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    paths = []
    for name, fn in ICONS.items():
        p = out_dir / f"{name}@2x.png"
        fn().save(p, optimize=True)
        paths.append(p)
    return paths

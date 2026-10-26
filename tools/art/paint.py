"""Procedural goblin junk-tech materials: rusty tin, corrugated sheet, brass, soot, rope, plank, Fizz glow."""

from __future__ import annotations

import colorsys
import random

import numpy as np

# DESIGN §14 palette: rust, brass, soot, toxic Fizz green (+ Glimmer cyan for Tier 2 accents)
RUST = (0.62, 0.30, 0.15)
RUST_DARK = (0.38, 0.17, 0.09)
TIN = (0.55, 0.57, 0.58)
TIN_DARK = (0.36, 0.38, 0.40)
BRASS = (0.80, 0.62, 0.24)
BRASS_DARK = (0.52, 0.36, 0.12)
SOOT = (0.16, 0.14, 0.13)
SOOT_LIGHT = (0.27, 0.24, 0.22)
WOOD = (0.47, 0.32, 0.19)
WOOD_DARK = (0.31, 0.20, 0.11)
ROPE = (0.71, 0.58, 0.37)
LEATHER = (0.40, 0.24, 0.14)
FIZZ = (0.55, 1.00, 0.22)
FIZZ_DEEP = (0.18, 0.62, 0.10)
GLIMMER = (0.35, 0.95, 1.00)
EMBER = (1.00, 0.55, 0.15)
FLAME = (1.00, 0.82, 0.35)
GLASS = (0.62, 0.80, 0.72)
CLOTH = (0.45, 0.36, 0.25)
SKIN_GOBLIN = (0.46, 0.60, 0.29)


def jitter(c, amount):
    h, l, s = colorsys.rgb_to_hls(*c)
    l = min(1, max(0, l + random.uniform(-amount, amount)))
    return colorsys.hls_to_rgb(h, l, s)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def _region(cv, rect):
    x, y, w, h = rect
    return cv.a[y:y + h, x:x + w]


def _fill(cv, rect, col, noise=0.05, alpha=1.0):
    r = _region(cv, rect)
    h, w = r.shape[:2]
    n = (np.random.rand(h, w, 1) - 0.5) * 2 * noise
    r[..., :3] = np.clip(np.array(col)[None, None, :] + n, 0, 1)
    r[..., 3] = alpha


def _shade_sides(cv, rect, face, top_light=0.06, bottom_dark=0.10):
    if face in ("top", "bottom"):
        return
    r = _region(cv, rect)
    h = r.shape[0]
    if h < 2:
        return
    g = np.linspace(top_light, -bottom_dark, h)[:, None, None]
    r[..., :3] = np.clip(r[..., :3] + g, 0, 1)


def _edge(cv, rect, amount=0.25, top_highlight=0.10):
    r = _region(cv, rect)
    h, w = r.shape[:2]
    if w < 3 or h < 3:
        return
    r[0, :, :3] = np.clip(r[0, :, :3] + top_highlight, 0, 1)
    r[-1, :, :3] *= 1 - amount
    r[:, 0, :3] *= 1 - amount * 0.6
    r[:, -1, :3] *= 1 - amount * 0.8


def _blotch(cv, rect, col, count, rmax=2.5):
    r = _region(cv, rect)
    h, w = r.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    for _ in range(count):
        cx, cy = random.uniform(0, w), random.uniform(0, h)
        rad = random.uniform(0.8, rmax)
        m = (xx - cx) ** 2 + ((yy - cy) * 1.3) ** 2 < rad * rad
        c = jitter(col, 0.05)
        r[m, :3] = r[m, :3] * 0.35 + np.array(c) * 0.65


def _streaks(cv, rect, col, count):
    """Rust runs dripping down from rivets/top edges."""
    r = _region(cv, rect)
    h, w = r.shape[:2]
    for _ in range(count):
        x = random.randrange(w)
        y0 = random.randrange(max(1, h // 3))
        length = random.randint(2, max(2, h // 2))
        for y in range(y0, min(h, y0 + length)):
            t = 0.55 * (1 - (y - y0) / length)
            r[y, x, :3] = r[y, x, :3] * (1 - t) + np.array(col) * t


def _rivets(cv, rect, inset=1, col=BRASS):
    r = _region(cv, rect)
    h, w = r.shape[:2]
    if w < 6 or h < 6:
        return
    xs = [inset, w - 1 - inset]
    ys = [inset, h - 1 - inset]
    if w >= 14:
        xs.append(w // 2 + random.choice((-1, 0, 1)))
    for x in xs:
        for y in ys:
            x2 = min(w - 1, max(0, x + random.choice((0, 0, 1, -1))))  # mismatched, slightly off-grid
            c = jitter(random.choice((col, TIN, BRASS_DARK)), 0.05)
            r[y, x2, :3] = c
            if y + 1 < h:
                r[y + 1, x2, :3] = np.array(c) * 0.55


def metal(base=TIN, rust=0.35, rivets=True, corrugated=False, edge=True, streaks=True, bolt=BRASS):
    def p(cv, rect, face, box):
        _fill(cv, rect, jitter(base, 0.03), noise=0.035)
        r = _region(cv, rect)
        h, w = r.shape[:2]
        if corrugated and w >= 3:
            along_u = face not in ("left", "right") or w >= h
            n = w if along_u else h
            wave = np.array([0.10, 0.05, -0.06, -0.10][i % 4] for i in range(n)) if False else \
                np.array([(0.10, 0.04, -0.07, -0.11)[i % 4] for i in range(n)])
            if along_u:
                r[..., :3] = np.clip(r[..., :3] + wave[None, :, None], 0, 1)
            else:
                r[..., :3] = np.clip(r[..., :3] + wave[:, None, None], 0, 1)
        area = rect[2] * rect[3]
        if rust > 0:
            _blotch(cv, rect, RUST, int(area * rust / 14) + (1 if area > 20 else 0))
            _blotch(cv, rect, RUST_DARK, int(area * rust / 40))
            if streaks and face not in ("top", "bottom"):
                _streaks(cv, rect, RUST, int(rect[2] * rust / 3))
        _shade_sides(cv, rect, face)
        if edge:
            _edge(cv, rect)
        if rivets:
            _rivets(cv, rect, col=bolt)
    return p


def brass(rivets=False):
    return metal(BRASS, rust=0.08, rivets=rivets, streaks=False, bolt=TIN)


def soot(rust=0.0):
    def p(cv, rect, face, box):
        _fill(cv, rect, SOOT, noise=0.04)
        _blotch(cv, rect, SOOT_LIGHT, rect[2] * rect[3] // 25)
        if rust:
            _blotch(cv, rect, RUST_DARK, int(rect[2] * rect[3] * rust / 20))
        _shade_sides(cv, rect, face, 0.04, 0.04)
        _edge(cv, rect, 0.3, 0.05)
    return p


def plank(base=WOOD, horizontal=True):
    def p(cv, rect, face, box):
        _fill(cv, rect, base, noise=0.03)
        r = _region(cv, rect)
        h, w = r.shape[:2]
        along = horizontal if face not in ("top", "bottom") else True
        n = h if along else w
        step = 4
        for i in range(0, n, step):
            if along:
                r[i, :, :3] *= 0.72
            else:
                r[:, i, :3] *= 0.72
        # grain
        for _ in range(max(1, w * h // 12)):
            x, y = random.randrange(w), random.randrange(h)
            r[y, x, :3] = np.array(WOOD_DARK)
        _shade_sides(cv, rect, face, 0.04, 0.06)
        _edge(cv, rect, 0.25, 0.04)
    return p


def rope():
    def p(cv, rect, face, box):
        _fill(cv, rect, ROPE, noise=0.03)
        r = _region(cv, rect)
        h, w = r.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        twist = ((xx + yy) % 3) == 0
        r[twist, :3] *= 0.70
        _edge(cv, rect, 0.2, 0.0)
    return p


def leather(base=LEATHER):
    def p(cv, rect, face, box):
        _fill(cv, rect, base, noise=0.04)
        _blotch(cv, rect, mix(base, SOOT, 0.4), rect[2] * rect[3] // 20)
        _shade_sides(cv, rect, face)
        _edge(cv, rect, 0.3, 0.05)
        r = _region(cv, rect)
        h, w = r.shape[:2]
        if w >= 6 and h >= 6:  # stitching
            for x in range(1, w - 1, 2):
                r[1, x, :3] = np.array(ROPE) * 0.9
    return p


def solid(col, noise=0.03, edge=True):
    def p(cv, rect, face, box):
        _fill(cv, rect, col, noise=noise)
        _shade_sides(cv, rect, face, 0.03, 0.05)
        if edge:
            _edge(cv, rect, 0.2, 0.05)
    return p


def glow(col=FIZZ, deep=FIZZ_DEEP, bubbles=True):
    """Emissive Fizz: bright core, deeper rim, a few bubbles. Pair with shading='fullbright'."""
    def p(cv, rect, face, box):
        _fill(cv, rect, col, noise=0.05)
        r = _region(cv, rect)
        h, w = r.shape[:2]
        if w >= 3 and h >= 3:
            r[0, :, :3] = r[-1, :, :3] = np.array(deep)
            r[:, 0, :3] = r[:, -1, :3] = np.array(deep)
        if bubbles:
            for _ in range(max(1, w * h // 10)):
                x, y = random.randrange(w), random.randrange(h)
                r[y, x, :3] = np.array(mix(col, (1, 1, 1), 0.6))
    return p


def flame():
    def p(cv, rect, face, box):
        r = _region(cv, rect)
        h, w = r.shape[:2]
        r[..., 3] = 0
        for y in range(h):
            t = y / max(1, h - 1)  # 0 at top
            half = (w / 2) * (0.35 + 0.65 * t ** 0.6)
            for x in range(w):
                d = abs(x + 0.5 - w / 2)
                if d <= half + random.uniform(-0.6, 0.4):
                    core = d < half * 0.45 and t > 0.3
                    c = FLAME if core else mix(EMBER, FIZZ, 0.0 if t > 0.25 else 0.15)
                    r[y, x, :3] = jitter(c, 0.04)
                    r[y, x, 3] = 1
    return p


def glass(liquid=FIZZ, fill=0.6):
    """Side faces show a liquid level; top shows the surface."""
    def p(cv, rect, face, box):
        _fill(cv, rect, mix(GLASS, SOOT, 0.25), noise=0.02)
        r = _region(cv, rect)
        h, w = r.shape[:2]
        if face in ("top", "bottom"):
            if fill > 0:
                r[..., :3] = np.array(mix(liquid, (1, 1, 1), 0.15))
        else:
            level = int(round(h * (1 - fill)))
            if fill > 0:
                r[level:, :, :3] = np.array(liquid) * (0.9 + 0.1 * np.random.rand(h - level, w, 1))
                if level < h:
                    r[level, :, :3] = np.array(mix(liquid, (1, 1, 1), 0.5))
            if w >= 3:
                r[:, 1, :3] = np.clip(r[:, 1, :3] + 0.18, 0, 1)  # glint
        _edge(cv, rect, 0.2, 0.0)
    return p

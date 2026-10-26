"""Craftory particle sprites (32x32): Fizz spark, Jank smoke puffs (white/grey/black), Glimmer mote."""

from __future__ import annotations

import math
import random

import numpy as np
from PIL import Image

SIZE = 32


def _img(a: np.ndarray) -> Image.Image:
    return Image.fromarray(np.clip(a * 255 + 0.5, 0, 255).astype(np.uint8), "RGBA")


def _quantise(alpha: np.ndarray, steps=4) -> np.ndarray:
    return np.round(alpha * steps) / steps


def spark(core=(0.85, 1.0, 0.55), rim=(0.35, 0.95, 0.15), rays=4, seed=1) -> Image.Image:
    random.seed(seed)
    a = np.zeros((SIZE, SIZE, 4), np.float32)
    yy, xx = np.mgrid[0:SIZE, 0:SIZE] + 0.5
    cx = cy = SIZE / 2
    dx, dy = xx - cx, yy - cy
    r = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    star = np.maximum(0, np.cos(ang * rays / 2 * 2 + 0.3)) ** 6 * np.clip(1 - r / 15, 0, 1)
    blob = np.clip(1 - r / 6, 0, 1)
    alpha = np.clip(star * 1.2 + blob * 1.5, 0, 1)
    alpha = _quantise(alpha)
    t = np.clip(blob * 1.6, 0, 1)[..., None]
    a[..., :3] = np.array(rim) * (1 - t) + np.array(core) * t
    a[..., 3] = alpha
    # a few stray pixels: jittery electric fizz
    for _ in range(5):
        th, d = random.uniform(0, 2 * math.pi), random.uniform(8, 13)
        x, y = int(cx + math.cos(th) * d), int(cy + math.sin(th) * d)
        a[y, x] = (*rim, 1)
    return _img(a)


def smoke(col, seed=2) -> Image.Image:
    rng = np.random.default_rng(seed)
    a = np.zeros((SIZE, SIZE, 4), np.float32)
    yy, xx = np.mgrid[0:SIZE, 0:SIZE] + 0.5
    alpha = np.zeros((SIZE, SIZE), np.float32)
    shade = np.zeros((SIZE, SIZE), np.float32)
    for _ in range(6):  # lumpy cartoon puff from overlapping circles
        cx, cy = rng.uniform(10, 22), rng.uniform(11, 21)
        rad = rng.uniform(5, 8)
        d = np.hypot(xx - cx, yy - cy) / rad
        alpha = np.maximum(alpha, np.clip(1.6 - d * 1.6, 0, 1))
        shade = np.maximum(shade, np.clip(1 - np.hypot(xx - cx + 2, yy - cy + 2) / rad, 0, 1))
    alpha = _quantise(np.clip(alpha, 0, 1) * 0.9, 3)
    base = np.array(col)
    hi = np.clip(base + 0.18, 0, 1)
    lo = base * 0.7
    t = _quantise(shade, 2)[..., None]
    a[..., :3] = lo * (1 - t) + hi * t
    a[..., 3] = alpha
    return _img(a)


def mote(seed=3) -> Image.Image:
    a = np.zeros((SIZE, SIZE, 4), np.float32)
    yy, xx = np.mgrid[0:SIZE, 0:SIZE] + 0.5
    d = np.abs(xx - 16) + np.abs(yy - 16)  # diamond, crystal-like
    alpha = _quantise(np.clip(1.4 - d / 9, 0, 1))
    glow = np.clip(1 - d / 5, 0, 1)[..., None]
    a[..., :3] = np.array((0.15, 0.70, 0.90)) * (1 - glow) + np.array((0.85, 1.0, 1.0)) * glow
    a[..., 3] = alpha
    return _img(a)


def tether_beam() -> Image.Image:
    yy, xx = np.mgrid[0:32, 0:32] + 0.5
    distance = np.abs(xx - 16) / 16
    glow = np.clip(1 - distance, 0, 1) ** 1.8
    pulse = 0.88 + 0.12 * np.cos((yy - 16) * math.pi / 8)
    alpha = np.clip(glow * pulse, 0, 1)
    image = np.zeros((32, 32, 4), np.float32)
    image[..., 0] = 0.12 * glow
    image[..., 1] = 0.72 + 0.23 * glow
    image[..., 2] = 0.82 + 0.18 * glow
    image[..., 3] = alpha
    return _img(image)


def textures() -> dict[str, Image.Image]:
    return {
        "Fizz_Spark": spark(),
        "Smoke_White": smoke((0.88, 0.88, 0.86), 2),
        "Smoke_Grey": smoke((0.50, 0.50, 0.50), 4),
        "Smoke_Black": smoke((0.12, 0.11, 0.11), 6),
        "Glimmer_Mote": mote(),
    }


def trails() -> dict[str, Image.Image]:
    def ribbon(color: tuple[float, float, float], seed: int) -> Image.Image:
        rng = np.random.default_rng(seed)
        yy, xx = np.mgrid[0:SIZE, 0:SIZE] + 0.5
        center = SIZE / 2 + np.sin(yy / 4 + seed) * 2
        width = np.maximum(0, 1 - np.abs(xx - center) / 8)
        alpha = _quantise(np.clip(width, 0, 1) * 0.8, 4)
        noise = rng.uniform(0.85, 1.0, (SIZE, SIZE, 1))
        image = np.zeros((SIZE, SIZE, 4), np.float32)
        image[..., :3] = np.array(color) * noise
        image[..., 3] = alpha
        return _img(image)

    return {
        "Craftory_Riftmoth_Feed": ribbon((0.58, 0.20, 0.78), 31),
        "Craftory_Slurp_Siphon": ribbon((0.22, 0.82, 0.34), 47),
    }

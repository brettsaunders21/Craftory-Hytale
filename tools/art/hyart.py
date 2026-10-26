"""Tiny toolkit for authoring Hytale .blockymodel/.blockyanim files and painting their textures.

Models are trees of boxes. Every face gets its own rectangle in a shelf-packed atlas, so a texture
is painted once per face at 1 texel per model unit (32 per block for props, 64 for character
attachments). The renderer is a small numpy rasterizer used only for previews.
"""

from __future__ import annotations

import json
import math
import random
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

import numpy as np
from PIL import Image, ImageDraw

FACES = ("front", "back", "left", "right", "top", "bottom")


# ---------------------------------------------------------------- math


def quat_from_euler(x: float = 0, y: float = 0, z: float = 0) -> tuple[float, float, float, float]:
    """Degrees, applied X then Y then Z (q = qz * qy * qx)."""
    def axis(ax, deg):
        h = math.radians(deg) / 2
        s = math.sin(h)
        return (s * ax[0], s * ax[1], s * ax[2], math.cos(h))

    q = (0.0, 0.0, 0.0, 1.0)
    for ax, deg in (((1, 0, 0), x), ((0, 1, 0), y), ((0, 0, 1), z)):
        q = quat_mul(axis(ax, deg), q)
    return q


def quat_mul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (
        aw * bx + ax * bw + ay * bz - az * by,
        aw * by - ax * bz + ay * bw + az * bx,
        aw * bz + ax * by - ay * bx + az * bw,
        aw * bw - ax * bx - ay * by - az * bz,
    )


def quat_matrix(q) -> np.ndarray:
    x, y, z, w = q
    n = math.sqrt(x * x + y * y + z * z + w * w) or 1.0
    x, y, z, w = x / n, y / n, z / n, w / n
    return np.array(
        [
            [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
        ]
    )


def r5(v: float) -> float:
    v = round(float(v), 5)
    return 0.0 if v == 0 else v


# ---------------------------------------------------------------- model


Painter = Callable[["Canvas", tuple[int, int, int, int], str, "Box"], None]


@dataclass
class Box:
    name: str
    size: tuple[float, float, float] = (0, 0, 0)  # (0,0,0) = transform-only node
    pos: tuple[float, float, float] = (0, 0, 0)  # relative to parent mesh centre
    offset: tuple[float, float, float] = (0, 0, 0)  # mesh centre relative to this node's pivot
    rot: tuple[float, float, float] = (0, 0, 0)  # euler degrees
    paint: Painter | None = None
    shading: str = "standard"
    stretch: tuple[float, float, float] = (1, 1, 1)
    faces: tuple[str, ...] = FACES
    quad: str | None = None  # normal for quads, e.g. "+Z"; size z ignored
    double_sided: bool = False
    visible: bool = True
    children: list["Box"] = field(default_factory=list)
    uv: dict[str, tuple[int, int, int, int]] = field(default_factory=dict)

    def add(self, *kids: "Box") -> "Box":
        self.children.extend(kids)
        return self

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()

    @property
    def has_shape(self) -> bool:
        return any(self.size)

    def face_size(self, face: str) -> tuple[int, int]:
        sx, sy, sz = (int(round(v)) for v in self.size)
        if self.quad:
            return sx, sy
        return {
            "front": (sx, sy), "back": (sx, sy),
            "left": (sz, sy), "right": (sz, sy),
            "top": (sx, sz), "bottom": (sx, sz),
        }[face]

    def shape_faces(self) -> tuple[str, ...]:
        return ("front",) if self.quad else self.faces


@dataclass
class Model:
    roots: list[Box]
    fmt: str = "prop"
    atlas: tuple[int, int] = (0, 0)
    image: Image.Image | None = None

    def boxes(self):
        for r in self.roots:
            yield from r.walk()

    def node_count(self) -> int:
        return sum(1 for _ in self.boxes())

    def find(self, name: str) -> Box:
        return next(b for b in self.boxes() if b.name == name)


def pack(model: Model, width: int | None = None, pad: int = 0) -> None:
    rects = []
    for b in model.boxes():
        if not b.has_shape:
            continue
        for f in b.shape_faces():
            w, h = b.face_size(f)
            if w > 0 and h > 0:
                rects.append((h, w, b, f))
    rects.sort(key=lambda r: (-r[0], -r[1]))
    area = sum((w + pad) * (h + pad) for h, w, _, _ in rects)
    widest = max((w for _, w, _, _ in rects), default=8)
    if width is None:
        width = 16
        while width < widest or width * width < area * 1.25:
            width *= 2
    x = y = shelf = 0
    for h, w, b, f in rects:
        if x + w > width:
            x, y, shelf = 0, y + shelf + pad, 0
        b.uv[f] = (x, y, w, h)
        x += w + pad
        shelf = max(shelf, h)
    height = 16
    while height < y + shelf:
        height *= 2
    model.atlas = (width, height)


class Canvas:
    def __init__(self, w: int, h: int):
        self.a = np.zeros((h, w, 4), dtype=np.float32)

    def image(self) -> Image.Image:
        return Image.fromarray(np.clip(self.a * 255 + 0.5, 0, 255).astype(np.uint8), "RGBA")


def paint(model: Model, seed: int = 7) -> Image.Image:
    random.seed(seed)
    np.random.seed(seed)
    cv = Canvas(*model.atlas)
    for b in model.boxes():
        if b.paint is None:
            continue
        for f, rect in b.uv.items():
            b.paint(cv, rect, f, b)
    model.image = cv.image()
    return model.image


# ---------------------------------------------------------------- export


def _vec(v):
    return {"x": r5(v[0]), "y": r5(v[1]), "z": r5(v[2])}


def _node(b: Box, ids: list[int]) -> dict:
    ids[0] += 1
    q = quat_from_euler(*b.rot)
    shape: dict = {
        "type": "none",
        "offset": _vec(b.offset),
        "stretch": _vec(b.stretch),
        "settings": {"isPiece": False},
        "textureLayout": {},
        "unwrapMode": "custom",
        "visible": b.visible,
        "doubleSided": b.double_sided,
        "shadingMode": b.shading,
    }
    if b.has_shape:
        if b.quad:
            shape["type"] = "quad"
            shape["settings"] = {"size": {"x": r5(b.size[0]), "y": r5(b.size[1])}, "normal": b.quad, "isPiece": False}
        else:
            shape["type"] = "box"
            shape["settings"] = {"size": _vec(b.size), "isPiece": False}
        for f, (x, y, _, _) in b.uv.items():
            shape["textureLayout"][f] = {"offset": {"x": x, "y": y}, "mirror": {"x": False, "y": False}, "angle": 0}
    node = {
        "id": str(ids[0]),
        "name": b.name,
        "position": _vec(b.pos),
        "orientation": {"x": r5(q[0]), "y": r5(q[1]), "z": r5(q[2]), "w": r5(q[3])},
        "shape": shape,
    }
    if b.children:
        node["children"] = [_node(c, ids) for c in b.children]
    return node


def model_json(model: Model) -> dict:
    ids = [0]
    return {"nodes": [_node(r, ids) for r in model.roots], "format": model.fmt, "lod": "auto"}


def write_model(model: Model, path: Path, texture_path: Path | None = None) -> None:
    if model.node_count() > 255:
        raise ValueError(f"{path.name}: {model.node_count()} nodes > 255")
    names = [b.name for b in model.boxes()]
    dupes = {n for n in names if names.count(n) > 1}
    if dupes:
        raise ValueError(f"{path.name}: duplicate node names {sorted(dupes)}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(model_json(model), indent=2) + "\n")
    if texture_path is not None and model.image is not None:
        texture_path.parent.mkdir(parents=True, exist_ok=True)
        model.image.save(texture_path, optimize=True)


def anim_json(duration: int, tracks: dict[str, dict[str, list]], hold: bool = False) -> dict:
    """tracks: node -> {"orientation": [(t, (ex,ey,ez)), ...], "position": [(t, (x,y,z))], ...}."""
    out = {}
    for node, tr in tracks.items():
        entry = {k: [] for k in ("position", "orientation", "shapeStretch", "shapeVisible", "shapeUvOffset")}
        for t, e, *interp in tr.get("orientation", []):
            q = quat_from_euler(*e)
            entry["orientation"].append({
                "time": t, "delta": {"x": r5(q[0]), "y": r5(q[1]), "z": r5(q[2]), "w": r5(q[3])},
                "interpolationType": interp[0] if interp else "smooth",
            })
        for key in ("position", "shapeStretch"):
            for t, v, *interp in tr.get(key, []):
                entry[key].append({"time": t, "delta": _vec(v), "interpolationType": interp[0] if interp else "smooth"})
        for t, v in tr.get("shapeVisible", []):
            entry["shapeVisible"].append({"time": t, "delta": bool(v)})
        for t, v in tr.get("shapeUvOffset", []):
            entry["shapeUvOffset"].append({"time": t, "delta": {"x": v[0], "y": v[1]}})
        out[node] = entry
    return {"formatVersion": 1, "duration": duration, "holdLastKeyframe": hold, "nodeAnimations": out}


def write_json(obj: dict, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")

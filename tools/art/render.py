"""Offline preview renderer for .blockymodel files (orthographic numpy rasterizer).

Reads the exported JSON plus its texture, so it renders vanilla and Craftory models the same way.
Previews only: Hytale's client lighting, LOD and shaders are not reproduced.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

from hyart import quat_matrix, quat_mul

# corner order TL, TR, BR, BL in (sx, sy, sz) sign space for each face, viewed from outside
FACE_CORNERS = {
    "front": [(-1, 1, 1), (1, 1, 1), (1, -1, 1), (-1, -1, 1)],
    "back": [(1, 1, -1), (-1, 1, -1), (-1, -1, -1), (1, -1, -1)],
    "right": [(1, 1, 1), (1, 1, -1), (1, -1, -1), (1, -1, 1)],
    "left": [(-1, 1, -1), (-1, 1, 1), (-1, -1, 1), (-1, -1, -1)],
    "top": [(-1, 1, -1), (1, 1, -1), (1, 1, 1), (-1, 1, 1)],
    "bottom": [(-1, -1, 1), (1, -1, 1), (1, -1, -1), (-1, -1, -1)],
}
QUAD_FACE = {"+Z": "front", "-Z": "back", "+X": "right", "-X": "left", "+Y": "top", "-Y": "bottom"}


def _v(d, default=0.0):
    return np.array([d.get("x", default), d.get("y", default), d.get("z", default)], dtype=np.float64)


def _face_dims(face, sx, sy, sz):
    return {"front": (sx, sy), "back": (sx, sy), "left": (sz, sy), "right": (sz, sy),
            "top": (sx, sz), "bottom": (sx, sz)}[face]


def _sample_anim(track, frame):
    if not track:
        return None
    keys = sorted(track, key=lambda k: k["time"])
    if frame <= keys[0]["time"]:
        return keys[0]["delta"]
    for a, b in zip(keys, keys[1:]):
        if a["time"] <= frame <= b["time"]:
            t = (frame - a["time"]) / max(1e-9, b["time"] - a["time"])
            if a.get("interpolationType") == "smooth":
                t = t * t * (3 - 2 * t)
            if isinstance(a["delta"], bool):
                return a["delta"]
            da, db = a["delta"], b["delta"]
            if "w" in da:
                if sum(da[k] * db[k] for k in "xyzw") < 0:
                    db = {k: -db[k] for k in "xyzw"}
                q = {k: da[k] + (db[k] - da[k]) * t for k in "xyzw"}
                n = math.sqrt(sum(v * v for v in q.values())) or 1
                return {k: v / n for k, v in q.items()}
            return {k: da[k] + (db[k] - da[k]) * t for k in da}
    return keys[-1]["delta"]


def collect_faces(model: dict, anim: dict | None = None, frame: float = 0):
    """Returns list of (corners[4x3], uv[4x2], shading, double_sided)."""
    out = []
    tracks = (anim or {}).get("nodeAnimations", {})

    def visit(node, R, t, parent_offset):
        o = node.get("orientation", {"x": 0, "y": 0, "z": 0, "w": 1})
        q = (o["x"], o["y"], o["z"], o["w"])
        pos = _v(node.get("position", {}))
        tr = tracks.get(node.get("name"), {})
        dq = _sample_anim(tr.get("orientation"), frame)
        if dq:
            q = quat_mul(q, (dq["x"], dq["y"], dq["z"], dq["w"]))
        dp = _sample_anim(tr.get("position"), frame)
        if dp:
            pos = pos + _v(dp)
        Rw = R @ quat_matrix(q)
        tw = t + R @ (parent_offset + pos)
        shape = node.get("shape", {})
        offset = _v(shape.get("offset", {}))
        visible = shape.get("visible", True)
        dv = _sample_anim(tr.get("shapeVisible"), frame)
        if dv is not None:
            visible = dv
        stype = shape.get("type", "none")
        if visible and stype in ("box", "quad"):
            st = _v(shape.get("stretch", {}), 1.0)
            ss = _sample_anim(tr.get("shapeStretch"), frame)
            if ss:
                st = st * _v(ss, 1.0)
            size = shape["settings"]["size"]
            sx, sy = size["x"], size["y"]
            sz = size.get("z", 0) if stype == "box" else 0
            half = np.array([sx, sy, sz]) * st / 2
            centre = tw + Rw @ offset
            if stype == "quad":
                faces = {QUAD_FACE.get(shape["settings"].get("normal", "+Z"), "front"): None}
            else:
                faces = {f: None for f in FACE_CORNERS}
            for face in faces:
                lay = shape.get("textureLayout", {}).get(face)
                if lay is None:
                    continue
                if stype == "quad":
                    fw, fh = sx, sy
                    signs = np.array(FACE_CORNERS[face], dtype=np.float64)
                    # quads lie in the plane through the centre; map the 2D size onto the face axes
                    h3 = {"front": (sx, sy, 0), "back": (sx, sy, 0), "right": (0, sy, sx), "left": (0, sy, sx),
                          "top": (sx, 0, sy), "bottom": (sx, 0, sy)}[face]
                    corners = [centre + Rw @ (s * np.array(h3) * st / 2) for s in signs]
                else:
                    fw, fh = _face_dims(face, sx, sy, sz)
                    corners = [centre + Rw @ (np.array(s) * half) for s in FACE_CORNERS[face]]
                uv = face_uv(lay, fw, fh)
                out.append((np.array(corners), np.array(uv, dtype=np.float64),
                            shape.get("shadingMode", "standard"), shape.get("doubleSided", False)))
        for c in node.get("children", []):
            visit(c, Rw, tw, offset)

    for n in model["nodes"]:
        visit(n, np.eye(3), np.zeros(3), np.zeros(3))
    return out


def face_uv(lay: dict, fw: float, fh: float) -> list[tuple[float, float]]:
    """Texture coords of the face corners TL, TR, BR, BL.

    Hytale anchors the face's (0,0) at `offset`; mirror flips an axis and `angle` rotates clockwise around that
    anchor, so mirrored/rotated faces extend left/up from the offset (matches vanilla layouts).
    """
    x0, y0 = lay["offset"]["x"], lay["offset"]["y"]
    mx = -1 if lay.get("mirror", {}).get("x") else 1
    my = -1 if lay.get("mirror", {}).get("y") else 1
    ang = int(lay.get("angle", 0)) % 360
    out = []
    for s_, t_ in ((0, 0), (fw, 0), (fw, fh), (0, fh)):
        s_, t_ = s_ * mx, t_ * my
        if ang == 90:
            s_, t_ = -t_, s_
        elif ang == 180:
            s_, t_ = -s_, -t_
        elif ang == 270:
            s_, t_ = t_, -s_
        out.append((x0 + s_, y0 + t_))
    return out


def view_matrix(yaw: float, pitch: float) -> np.ndarray:
    y, p = math.radians(yaw), math.radians(pitch)
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rx = np.array([[1, 0, 0], [0, math.cos(p), -math.sin(p)], [0, math.sin(p), math.cos(p)]])
    return rx @ ry


LIGHT = np.array([-0.45, 0.8, 0.55])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def render(model: dict, tex: Image.Image, yaw=35.0, pitch=25.0, size=256, anim=None, frame=0.0,
           bg=(0, 0, 0, 0), fit=None, ss=2) -> Image.Image:
    """`model`/`tex` may also be lists to composite several models (e.g. a rig plus attachments)."""
    models = model if isinstance(model, list) else [model]
    texes = tex if isinstance(tex, list) else [tex]
    faces = []
    for mdl, tx in zip(models, texes):
        ta = np.asarray(tx.convert("RGBA"), dtype=np.float32) / 255
        faces += [(*f, ta) for f in collect_faces(mdl, anim, frame)]
    V = view_matrix(yaw, pitch)
    if not faces:
        return Image.new("RGBA", (size, size), bg)
    pts = np.concatenate([f[0] for f in faces]) @ V.T
    if fit is None:
        lo, hi = pts.min(0), pts.max(0)
        centre = (lo + hi) / 2
        extent = max(hi[0] - lo[0], hi[1] - lo[1]) * 1.08 or 1
    else:
        centre, extent = fit
    S = size * ss
    scale = S / extent
    col = np.zeros((S, S, 4), np.float32)
    col[:] = np.array(bg, np.float32) / 255
    zbuf = np.full((S, S), -np.inf, np.float32)
    for corners, uv, shading, dbl, texa in faces:
        th, tw_ = texa.shape[:2]
        p = (corners @ V.T - centre) * scale
        p[:, 0] += S / 2
        p[:, 1] = S / 2 - p[:, 1]
        n_world = np.cross(corners[3] - corners[0], corners[1] - corners[0])
        nn = np.linalg.norm(n_world)
        if nn < 1e-9:
            continue
        n_world /= nn
        n_view = V @ n_world
        facing = n_view[2] > 1e-6
        if not facing and not dbl:
            continue
        if shading in ("fullbright",):
            light = 1.0
        elif shading == "flat":
            light = 0.85
        else:
            nl = n_world if facing else -n_world
            light = 0.55 + 0.45 * max(0.0, float(nl @ LIGHT))
            light += 0.1 * max(0.0, nl[1])
        for tri in ((0, 1, 2), (0, 2, 3)):
            P, U = p[list(tri)], uv[list(tri)]
            x0, y0 = np.floor(P[:, :2].min(0)).astype(int)
            x1, y1 = np.ceil(P[:, :2].max(0)).astype(int)
            x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, S - 1), min(y1, S - 1)
            if x1 < x0 or y1 < y0:
                continue
            ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
            px, py = xs + 0.5, ys + 0.5
            (ax, ay, az), (bx, by, bz), (cx, cy, cz) = P
            d = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if abs(d) < 1e-9:
                continue
            w0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / d
            w1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / d
            w2 = 1 - w0 - w1
            m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not m.any():
                continue
            z = w0 * az + w1 * bz + w2 * cz
            u = w0 * U[0, 0] + w1 * U[1, 0] + w2 * U[2, 0]
            v = w0 * U[0, 1] + w1 * U[1, 1] + w2 * U[2, 1]
            ui = np.clip(np.floor(u).astype(int), 0, tw_ - 1)
            vi = np.clip(np.floor(v).astype(int), 0, th - 1)
            texel = texa[vi, ui]
            m &= texel[..., 3] > 0.5
            m &= z > zbuf[ys, xs]
            if not m.any():
                continue
            yy, xx = ys[m], xs[m]
            zbuf[yy, xx] = z[m]
            rgb = texel[m][:, :3] * light
            col[yy, xx, :3] = np.clip(rgb, 0, 1)
            col[yy, xx, 3] = 1
    img = Image.fromarray((col * 255 + 0.5).astype(np.uint8), "RGBA")
    return img.resize((size, size), Image.LANCZOS) if ss > 1 else img


def load(model_path: Path, texture_path: Path | None = None):
    model = json.loads(Path(model_path).read_text())
    if texture_path is None:
        texture_path = Path(str(model_path).replace(".blockymodel", ".png"))
    return model, Image.open(texture_path)


ANGLES = (("front 3/4", 35, 25), ("back 3/4", 215, 25), ("top", 20, 70))


def three_views(model, tex, size=192, anim=None, frame=0.0):
    return [render(model, tex, yaw, pitch, size, anim=anim, frame=frame) for _, yaw, pitch in ANGLES]

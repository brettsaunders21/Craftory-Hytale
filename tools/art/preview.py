"""Renders every Craftory model (3 angles), animation frames and icons (1x and 4x) into a contact sheet.

Usage: python3 tools/art/preview.py OUT_DIR [ASSETS_ZIP]
Writes OUT_DIR/renders/*.png and OUT_DIR/art_preview.png. Nothing here is committed.
"""

from __future__ import annotations

import io
import json
import os
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from render import ANGLES, render  # noqa: E402

COMMON = HERE.parents[1] / "craftory-tech" / "src" / "main" / "resources" / "Common"
CELL = 168
BG = (52, 54, 60, 255)
INK = (235, 235, 235, 255)


def textures_for(model_path: Path) -> list[Path]:
    stem = model_path.stem
    pngs = sorted(model_path.parent.glob("*.png"))
    siblings = [m.stem for m in model_path.parent.glob("*.blockymodel") if m.stem != stem and m.stem.startswith(stem)]
    own = [p for p in pngs if p.stem.startswith(stem) and not any(p.stem.startswith(s) for s in siblings)]
    return own or pngs


def model_rows(slice5b_only=False):
    rows = []
    for mp in sorted(COMMON.rglob("*.blockymodel")):
        if slice5b_only and not any(
            marker in mp.stem
            for marker in ("Wellhead_Off", "Wellhead_Pumping", "Glimmerwell_Anchor", "Slurp_Walker")
        ):
            continue
        model = json.loads(mp.read_text())
        anims = sorted(mp.parent.glob("*.blockyanim"))
        for tp in textures_for(mp):
            if slice5b_only and "Glimmerwell_Anchor" in mp.stem:
                state = "corrupted" if "Corrupted" in tp.stem else "normal"
                label = f"Glimmerwell anchor - {state}"
            elif slice5b_only and "Wellhead_Pumping" in mp.stem:
                label = "Wellhead - pumping"
            elif slice5b_only and "Wellhead_Off" in mp.stem:
                label = "Wellhead - off"
            else:
                label = f"{mp.relative_to(COMMON).as_posix()}\n  tex {tp.name}"
            views = [render(model, Image.open(tp), yaw, pitch, CELL) for _, yaw, pitch in ANGLES]
            rows.append((label, views, mp.stem + "__" + tp.stem))
        for ap in anims:
            if not ap.stem.startswith(mp.stem.split("_On")[0].split("_Off")[0]):
                continue
            anim = json.loads(ap.read_text())
            dur = anim["duration"]
            tp = textures_for(mp)[-1]
            try:
                views = [render(model, Image.open(tp), 35, 25, CELL, anim=anim, frame=dur * k / 3) for k in range(3)]
            except Exception:  # noqa: BLE001 - anim targets nodes of a sibling model variant
                continue
            if any(n in json.dumps(model) for n in anim["nodeAnimations"]):
                rows.append((f"{ap.name} on {mp.name}\n  frames 0, 1/3, 2/3", views, ap.stem))
    return rows


def grizzlecog_row(assets_zip: Path | None):
    if not assets_zip or not assets_zip.exists():
        return None
    zf = zipfile.ZipFile(assets_zip)
    rig = json.loads(zf.read("Common/NPC/Intelligent/Klops/Models/Model.blockymodel"))
    rig_tex = Image.open(io.BytesIO(zf.read("Common/NPC/Intelligent/Klops/Models/Model_default.png")))
    appearance = json.loads((COMMON.parent / "Server/Models/Intelligent/Craftory/Craftory_Grizzlecog.json").read_text())
    models, texes = [], []
    bones = {}

    def index(node):
        bones[node["name"]] = node
        for c in node.get("children", []):
            index(c)

    for n in rig["nodes"]:
        index(n)
    for att in appearance["DefaultAttachments"]:
        mp, tp = att["Model"], att["Texture"]
        local = COMMON / mp
        data = json.loads(local.read_text()) if local.exists() else json.loads(zf.read("Common/" + mp))
        tex = Image.open(COMMON / tp) if (COMMON / tp).exists() else Image.open(io.BytesIO(zf.read("Common/" + tp)))
        # graft attachment roots onto the matching bone so they follow the rig transform
        clone = json.loads(json.dumps(rig))
        cb = {}

        def index2(node):
            cb[node["name"]] = node
            for c in node.get("children", []):
                index2(c)

        for n in clone["nodes"]:
            index2(n)
        for n in clone["nodes"]:
            pass
        for root in data["nodes"]:
            bone = cb.get(root["name"])
            if bone is None:
                continue
            for node in cb.values():
                node["shape"] = {**node["shape"], "visible": False}
            bone.setdefault("children", []).extend(root.get("children", []))
        models.append(clone)
        texes.append(tex)
    models.insert(0, rig)
    texes.insert(0, rig_tex)
    views = [render(models, texes, yaw, pitch, CELL) for yaw, pitch in ((25, 10), (200, 10), (-60, 15))]
    return ("Grizzlecog: Klops rig + Craftory hat, goggles, backpack\n  (preview only, vanilla rig)", views,
            "Grizzlecog")


def icon_rows(slice5b_only=False):
    out = []
    for ip in sorted((COMMON / "Icons" / "ItemsGenerated" / "Craftory").glob("*.png")) + sorted(
        (COMMON / "Particles" / "Craftory").glob("*.png")
    ) + sorted((COMMON / "Beams").glob("*.png")):
        if slice5b_only and ip.stem not in {"Craftory_Glimmerwell_Anchor", "Craftory_Wellhead"}:
            continue
        im = Image.open(ip).convert("RGBA")
        out.append((ip.name, im))
    if not slice5b_only:
        for temperament, color in (
            ("SPARK", "#5ff3ff"),
            ("EMBER", "#ff8a2b"),
            ("TIDE", "#3f8cff"),
            ("GROWTH", "#5fd35a"),
            ("STONE", "#a58c6a"),
            ("GALE", "#d6f2ff"),
            ("CLANK", "#8bff3a"),
            ("HOLLOW", "#6a2a9a"),
        ):
            swatch = Image.new("RGBA", (64, 64), color)
            out.append((f"Well tint {temperament} {color}", swatch))
    return out


def mob_preview(out: Path, vanilla_root: Path) -> None:
    mapping = (
        ("Glimmer Wisp", "Flying_Critter/Bat", "Glimmer_Wisp"),
        ("Sparkjaw", "Swimming_Beast/Crocodile", "Sparkjaw"),
        ("Riftmoth", "Void/Spawn_Void", "Riftmoth"),
        ("Goblin Poacher", "Intelligent/Goblin/Goblin_Lobber", "Goblin_Poacher"),
        ("Vessa", "Intelligent/Klops/Klops_Merchant", "Vessa"),
    )
    row_height = CELL + 34
    sheet = Image.new("RGBA", (CELL * 6, row_height * len(mapping)), BG)
    draw = ImageDraw.Draw(sheet)
    for row, (label, model_id, craftory_name) in enumerate(mapping):
        spec = json.loads((vanilla_root / "Server/Models" / f"{model_id}.json").read_text())
        model = json.loads((vanilla_root / "Common" / spec["Model"]).read_text())
        vanilla = Image.open(vanilla_root / "Common" / spec["Texture"]).convert("RGBA")
        craftory = Image.open(COMMON / "NPC/Craftory" / craftory_name / "Texture.png").convert("RGBA")
        views = [render(model, vanilla, yaw, pitch, CELL) for _, yaw, pitch in ANGLES]
        views.extend(render(model, craftory, yaw, pitch, CELL) for _, yaw, pitch in ANGLES)
        y = row * row_height
        draw.text((5, y + 3), f"{label} / vanilla", fill=INK)
        draw.text((CELL * 3 + 5, y + 3), f"{label} / Craftory", fill=INK)
        for col, view in enumerate(views):
            sheet.alpha_composite(view, (col * CELL, y + 22))
    sheet.save(out / "mobs.png")


def main():
    out = Path(sys.argv[1])
    args = [arg for arg in sys.argv[2:] if arg != "--slice5b"]
    slice5b_only = "--slice5b" in sys.argv[2:]
    zip_path = Path(args[0]) if args else None
    (out / "renders").mkdir(parents=True, exist_ok=True)
    rows = model_rows(slice5b_only)
    g = None if slice5b_only else grizzlecog_row(zip_path)
    if g:
        rows.append(g)
    for label, views, key in rows:
        strip = Image.new("RGBA", (CELL * 3, CELL), BG)
        for i, v in enumerate(views):
            strip.alpha_composite(v, (i * CELL, 0))
        strip.save(out / "renders" / f"{key}.png")
    icons = icon_rows(slice5b_only)
    label_w = 300
    cols = 2
    row_h = CELL
    model_h = ((len(rows) + cols - 1) // cols) * row_h
    icon_cell = (64 * 4 + 64 + 24, 64 * 4 + 28)
    icon_cols = 6
    icon_h = ((len(icons) + icon_cols - 1) // icon_cols) * icon_cell[1]
    width = max(cols * (label_w + CELL * 3), icon_cols * icon_cell[0])
    sheet = Image.new("RGBA", (width, 60 + model_h + 40 + icon_h), BG)
    d = ImageDraw.Draw(sheet)
    title = (
        "Craftory Slice 5b preview. Offline renders, not the Hytale client."
        if slice5b_only
        else "Craftory art pass 2: Goblin workshop and Spark-era assets. Offline numpy renders."
    )
    d.text((10, 10), title, fill=INK)
    d.text((10, 30), "Models: front 3/4, back 3/4, top. Anim rows: 3 frames. Icons: 4x and 1:1 (64px).", fill=INK)
    for i, (label, views, _) in enumerate(rows):
        x = (i % cols) * (label_w + CELL * 3)
        y = 60 + (i // cols) * row_h
        d.text((x + 8, y + 8), label, fill=INK)
        for j, v in enumerate(views):
            sheet.alpha_composite(v, (x + label_w + j * CELL, y))
    y0 = 60 + model_h + 40
    for i, (name, im) in enumerate(icons):
        x = (i % icon_cols) * icon_cell[0]
        y = y0 + (i // icon_cols) * icon_cell[1]
        big = im.resize((im.width * 4, im.height * 4), Image.NEAREST)
        sheet.alpha_composite(big, (x + 8, y + 20))
        sheet.alpha_composite(im, (x + 16 + big.width, y + 20))
        d.text((x + 8, y + 4), name, fill=INK)
        big.save(out / "renders" / f"icon4x_{name}.png", format="PNG")
        im.save(out / "renders" / f"icon1x_{name}.png", format="PNG")
    vanilla_ref = os.environ.get("CRAFTORY_VANILLA_REF")
    if vanilla_ref and not slice5b_only:
        mob_preview(out, Path(vanilla_ref).expanduser())
    sheet.save(out / "art_preview.png", optimize=True)
    print(f"{len(rows)} model rows, {len(icons)} icons -> {out / 'art_preview.png'} {sheet.size}")


if __name__ == "__main__":
    main()

"""Held-item models and textures, exported from the existing goblin item art."""

from __future__ import annotations

import io
import json
import os
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

from hyart import Box, Model, model_json, write_model
import goblin_items as gi
from paint import BRASS, FIZZ, GLIMMER, RUST, TIN_DARK, brass, glass, leather, metal, solid

BOOK_MODEL = "Items/Weapons/Spellbook/Book.blockymodel"
GRIT = {
    "Adamantite": (0.55, 0.58, 0.70),
    "Cobalt": (0.26, 0.40, 0.76),
    "Copper": (0.75, 0.38, 0.20),
    "Gold": (0.88, 0.68, 0.20),
    "Iron": (0.58, 0.60, 0.62),
    "Mithril": (0.48, 0.70, 0.82),
    "Onyxium": (0.25, 0.23, 0.33),
    "Prisma": (0.55, 0.30, 0.78),
    "Silver": (0.78, 0.80, 0.84),
    "Thorium": (0.45, 0.62, 0.42),
}
DAUBS = {
    "Blue": (0.19, 0.47, 0.92),
    "Green": (0.22, 0.72, 0.28),
    "Red": (0.88, 0.18, 0.16),
    "Yellow": (0.96, 0.72, 0.12),
}


def _receipt() -> Model:
    paper = Box("Receipt", (15, 23, 2), pos=(0, 11, 0), rot=(0, 0, -5),
                paint=solid((0.78, 0.68, 0.48), noise=0.08))
    paper.add(
        Box("TopRoll", (17, 3, 3), pos=(0, 11, 0), paint=leather((0.35, 0.24, 0.15))),
        Box("BottomFold", (16, 2, 2), pos=(0, -11, 0), rot=(0, 0, 5), paint=leather((0.45, 0.31, 0.18))),
        Box("InkLineA", (8, 1, 1), pos=(-1, 5, 1.3), paint=solid((0.25, 0.22, 0.17))),
        Box("InkLineB", (10, 1, 1), pos=(0, 2, 1.3), paint=solid((0.25, 0.22, 0.17))),
        Box("InkLineC", (7, 1, 1), pos=(-2, -1, 1.3), paint=solid((0.25, 0.22, 0.17))),
        Box("CogStamp", (5, 5, 1), pos=(3, -6, 1.5), rot=(0, 0, 12), paint=brass(rivets=False)),
        Box("Clip", (3, 4, 2), pos=(-6, 8, 0), paint=metal(TIN_DARK, rust=0.3)),
    )
    return Model([paper], fmt="character")


def _gob_jar(filled: bool) -> Model:
    tint = GLIMMER if filled else (0.66, 0.78, 0.72)
    fill = 0.44 if filled else 0.0
    jar = Box("Jar", (16, 20, 14), pos=(0, 10, 0), paint=glass(tint, fill))
    jar.add(
        Box("JarShoulder", (14, 4, 12), pos=(0, 10, 0), paint=glass(tint, fill)),
        Box("Neck", (9, 4, 9), pos=(0, 12, 0), paint=glass(tint, fill)),
        Box("Rim", (12, 2, 12), pos=(0, 15, 0), paint=brass(rivets=True)),
        Box("Lid", (10, 3, 10), pos=(0, 17, 0), paint=metal(RUST, rust=0.2)),
        Box("Handle", (2, 12, 2), pos=(-9, 8, 0), paint=brass(rivets=False)),
        Box("SealRivet", (2, 2, 2), pos=(0, 13, 6), paint=solid(BRASS)),
    )
    return Model([jar], fmt="character")


def _book_texture(kind: str, cache: Path) -> Image.Image:
    tex_name = "Grimoire_Brown_Texture.png" if kind == "notebook" else "Grimoire_Purple_Texture.png"
    with zipfile.ZipFile(cache) as archive:
        image = Image.open(io.BytesIO(archive.read(f"Common/Items/Weapons/Spellbook/{tex_name}"))).convert("RGBA")
    draw = ImageDraw.Draw(image)
    panels = ((1, 29, 20, 54), (41, 29, 60, 54))
    for x0, y0, x1, y1 in panels:
        if kind == "notebook":
            draw.rectangle((x0, y0, x1, y1), fill=(91, 54, 30, 255))
            draw.rectangle((x0 + 2, y0 + 2, x1 - 2, y1 - 2), fill=(126, 78, 42, 255))
            for y in (y0 + 4, y0 + 8, y1 - 6):
                draw.line((x0 + 3, y, x1 - 3, y + 1), fill=(103, 61, 32, 255))
            for x, y in ((x0 + 4, y0 + 5), (x1 - 5, y0 + 10), (x0 + 6, y1 - 5)):
                draw.rectangle((x, y, x + 1, y + 1), fill=(65, 39, 25, 255))
            cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
            draw.rectangle((cx - 4, cy - 4, cx + 4, cy + 4), fill=(75, 47, 26, 255))
            draw.rectangle((cx - 3, cy - 3, cx + 3, cy + 3), fill=(184, 128, 48, 255))
            draw.rectangle((cx - 1, cy - 1, cx + 1, cy + 1), fill=(80, 195, 198, 255))
            draw.rectangle((x1 - 3, y0 + 3, x1 - 1, y0 + 6), fill=(203, 145, 57, 255))
        else:
            draw.rectangle((x0, y0, x1, y1), fill=(22, 37, 73, 255))
            draw.rectangle((x0 + 2, y0 + 2, x1 - 2, y1 - 2), fill=(38, 59, 105, 255))
            cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
            draw.polygon(((cx, cy - 8), (cx + 5, cy), (cx, cy + 8), (cx - 5, cy)), fill=(75, 213, 235, 255))
            draw.polygon(((cx, cy - 5), (cx + 2, cy), (cx, cy + 5), (cx - 2, cy)), fill=(210, 250, 250, 255))
            draw.rectangle((x0 + 3, y0 + 3, x1 - 3, y0 + 4), fill=(111, 77, 184, 255))
            for y in (y0 + 7, y1 - 7):
                draw.line((x0 + 3, y, x1 - 3, y), fill=(86, 145, 206, 255))
    return image


def _write_variant(common, manifest, written, build, name, factory, folder=None):
    folder = folder or name
    directory = common / "Items" / "Craftory" / folder
    model = build(factory(), name)
    model_path = directory / f"{name}.blockymodel"
    texture_path = directory / f"{name}_Texture.png"
    write_model(model, written(manifest, model_path))
    model.image.save(written(manifest, texture_path), optimize=True)
    return f"Items/Craftory/{folder}/{model_path.name}", f"Items/Craftory/{folder}/{texture_path.name}"


def export(common: Path, resources: Path, manifest: list[str], build, written) -> None:
    item_assets = {}
    special = {
        "Boom_Valve", "Fizz_Charge", "Glimmer_Dust", "Glimmer_Lens", "Grizzlecog_Receipt",
        "Gutter_Gear", "Muffler", "Riftmoth_Wing", "Scrap_Plating_Bodge", "Spark_Shard", "Squawk_Box",
    }
    for name in sorted(special - {"Grizzlecog_Receipt"}):
        item_assets[name] = _write_variant(common, manifest, written, build, name, gi.ITEMS[name])
    item_assets["Grizzlecog_Receipt"] = _write_variant(common, manifest, written, build, "Grizzlecog_Receipt", _receipt)

    for category, variants, factory in (("Grit", GRIT, gi.grit), ("Paint_Daub", DAUBS, gi.paint_daub)):
        directory = common / "Items" / "Craftory" / category
        shared = None
        for name, color in variants.items():
            model = build(factory(color), f"{category}_{name}")
            data = model_json(model)
            if shared is None:
                shared = data
                write_model(model, written(manifest, directory / f"{category}_Pile.blockymodel" if category == "Grit" else directory / f"{category}.blockymodel"))
            elif json.dumps(data, sort_keys=True) != json.dumps(shared, sort_keys=True):
                raise ValueError(f"{category} variants do not share one model")
            texture_name = f"Grit_{name}_Texture.png" if category == "Grit" else f"Paint_Daub_{name}_Texture.png"
            model.image.save(written(manifest, directory / texture_name), optimize=True)
            path = f"Items/Craftory/{category}/"
            model_name = f"{category}_Pile.blockymodel" if category == "Grit" else f"{category}.blockymodel"
            item_assets[f"{category}_{name}"] = (path + model_name, path + texture_name)

    directory = common / "Items" / "Craftory" / "Gob_Jar"
    shared = None
    for name, filled in (("Gob_Jar", False), ("Gob_Jar_Filled", True)):
        model = build(_gob_jar(filled), name)
        data = model_json(model)
        if shared is None:
            shared = data
            write_model(model, written(manifest, directory / "Gob_Jar.blockymodel"))
        elif json.dumps(data, sort_keys=True) != json.dumps(shared, sort_keys=True):
            raise ValueError("Gob-Jar variants do not share one model")
        texture_name = f"Gob_Jar_{'Filled' if filled else 'Empty'}_Texture.png"
        model.image.save(written(manifest, directory / texture_name), optimize=True)
        item_assets[name] = ("Items/Craftory/Gob_Jar/Gob_Jar.blockymodel", f"Items/Craftory/Gob_Jar/{texture_name}")

    cache = Path(
        os.environ.get(
            "HYTALE_ASSETS_ZIP",
            str(Path.home() / ".gradle/caches/hytale-assets/release-0.7.4-Assets.zip"),
        ),
    ).expanduser()
    for short, kind in (("Tinkers_Notebook", "notebook"), ("Codex", "codex")):
        folder = short
        texture_path = common / "Items" / "Craftory" / folder / f"{short}_Texture.png"
        texture_path.parent.mkdir(parents=True, exist_ok=True)
        if cache.is_file():
            _book_texture(kind, cache).save(written(manifest, texture_path), optimize=True)
        else:
            print(f"Warning: Hytale assets ZIP not found at {cache}; keeping {texture_path}")
            written(manifest, texture_path)
        item_assets[short] = (BOOK_MODEL, f"Items/Craftory/{folder}/{texture_path.name}")

    for short, (model_path, texture_path) in item_assets.items():
        item_path = resources / "Server/Item/Items/Craftory" / f"Craftory_{short}.json"
        item = json.loads(item_path.read_text())
        item.setdefault("PlayerAnimationsId", "Item")
        item["Model"] = model_path
        item["Texture"] = texture_path
        item_path.write_text(json.dumps(item, indent=2) + "\n")

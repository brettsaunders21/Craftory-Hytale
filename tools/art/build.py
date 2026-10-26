"""Regenerates every Goblin-era Craftory asset under craftory-tech/src/main/resources/Common.

Usage: python3 tools/art/build.py            (writes assets)
       python3 tools/art/preview.py OUT_DIR  (renders previews + contact sheet, never committed)
"""

from __future__ import annotations

import json
import os
import sys
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from hyart import Model, model_json, pack, paint, write_json, write_model  # noqa: E402
import goblin_blocks as gb  # noqa: E402
import goblin_items as gi  # noqa: E402
import grizzlecog  # noqa: E402
import particles  # noqa: E402
from render import render  # noqa: E402

COMMON = HERE.parents[1] / "craftory-tech" / "src" / "main" / "resources" / "Common"
ICON_DIR = COMMON / "Icons" / "ItemsGenerated" / "Craftory"
ICON_SIZE = 64

CANISTER_FILLS = {"Empty": 0.0, "Quarter": 0.25, "Half": 0.5, "ThreeQuarter": 0.75, "Full": 1.0}


def seed(name: str) -> int:
    return zlib.crc32(name.encode())


def build(model: Model, name: str) -> Model:
    pack(model)
    paint(model, seed(name))
    return model


def strip(model_dict: dict) -> str:
    return json.dumps(model_dict, sort_keys=True)


def icon(model: Model, path: Path, yaw=45.0, pitch=28.0) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img = render(model_json(model), model.image, yaw=yaw, pitch=pitch, size=ICON_SIZE, ss=4)
    img.save(path, optimize=True)


def written(manifest: list[str], p: Path) -> Path:
    manifest.append(p.relative_to(COMMON.parent).as_posix())
    return p


def export_blocks(manifest: list[str]) -> None:
    for name, (fn, variants) in gb.BLOCKS.items():
        d = COMMON / "Blocks" / "Craftory" / name
        if name in ("Fizz_Canister", "Sloshtank"):
            variants = {f"_{k}": {"fill": v} for k, v in CANISTER_FILLS.items()}
        built = {suffix: (build(m, name), anims) for suffix, kw in variants.items() for m, anims in [fn(**kw)]}
        shared = len({strip(model_json(m)) for m, _ in built.values()}) == 1
        first_model = None
        texture_written = False
        for suffix, (m, anims) in built.items():
            model_name = name if shared else f"{name}{suffix}"
            if first_model is None or not shared:
                write_model(m, written(manifest, d / f"{model_name}.blockymodel"))
                first_model = m
            tex_name = f"{name}_Texture.png" if name == "Gutterpipe" or len(built) == 1 else f"{name}{suffix}_Texture.png"
            if name != "Gutterpipe" or not texture_written:
                m.image.save(written(manifest, d / tex_name), optimize=True)
                texture_written = True
            for anim_name, anim in anims.items():
                write_json(anim, written(manifest, d / f"{name}_{anim_name}.blockyanim"))
        if name == "Gutterpipe":
            for stale_texture in d.glob("Gutterpipe_C??_Texture.png"):
                stale_texture.unlink()
        if name == "Gasbag":
            for stale in (d / "Gasbag.blockymodel", d / "Gasbag_Texture.png"):
                stale.unlink(missing_ok=True)
        icon_variant = {"Boomshroom_Burner": "_On", "Scorch_Oven": "_On", "Wakebell_Anchor": "_On",
                        "Junk_Heart_Firebox": "_On", "Fizz_Canister": "_Full", "Junk_Heart_Chimney": "_Cap"}
        m, _ = built[icon_variant.get(name, next(iter(built)))]
        icon(m, written(manifest, ICON_DIR / f"Craftory_{name}.png"))


def export_items(manifest: list[str]) -> None:
    for name, fn in gi.ITEMS.items():
        d = COMMON / "Items" / "Craftory" / name
        m = build(fn(), name)
        if name in ("Tinkers_Wrench", "Tinkers_Hammer"):
            write_model(m, written(manifest, d / f"{name}.blockymodel"))
            m.image.save(written(manifest, d / f"{name}_Texture.png"), optimize=True)
        icon(m, written(manifest, ICON_DIR / f"Craftory_{name}.png"), yaw=30, pitch=20)
    d = COMMON / "Items" / "Craftory" / "Squint_Goggles"
    worn = build(gi.squint_goggles(True), "Squint_Goggles")
    write_model(worn, written(manifest, d / "Squint_Goggles_Head.blockymodel"))
    worn.image.save(written(manifest, d / "Squint_Goggles_Head_Texture.png"), optimize=True)
    loose = build(gi.squint_goggles(False), "Squint_Goggles_Icon")
    icon(loose, written(manifest, ICON_DIR / "Craftory_Squint_Goggles.png"), yaw=20, pitch=20)


def export_particles(manifest: list[str]) -> None:
    d = COMMON / "Particles" / "Craftory"
    for name, img in particles.textures().items():
        d.mkdir(parents=True, exist_ok=True)
        img.save(written(manifest, d / f"{name}.png"), optimize=True)


def export_beams(manifest: list[str]) -> None:
    d = COMMON / "Beams"
    d.mkdir(parents=True, exist_ok=True)
    particles.tether_beam().save(written(manifest, d / "Craftory_Tether_Cyan.png"), optimize=True)


def main() -> None:
    manifest: list[str] = []
    export_blocks(manifest)
    export_items(manifest)
    export_particles(manifest)
    export_beams(manifest)
    grizzlecog.export(manifest, COMMON, build)
    vanilla_ref = os.environ.get("CRAFTORY_VANILLA_REF")
    if vanilla_ref:
        import retint

        manifest.extend(
            path.relative_to(COMMON.parent).as_posix()
            for path in retint.retint(Path(vanilla_ref).expanduser(), COMMON)
        )
    out = HERE / "manifest.txt"
    out.write_text("\n".join(sorted(set(manifest))) + "\n")
    print(f"wrote {len(set(manifest))} files; list in {out.relative_to(HERE.parents[1])}")


if __name__ == "__main__":
    main()

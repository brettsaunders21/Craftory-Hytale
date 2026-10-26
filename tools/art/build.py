"""Regenerates every Goblin-era Craftory asset under craftory-tech/src/main/resources/Common.

Usage: python3 tools/art/build.py            (writes assets)
       python3 tools/art/preview.py OUT_DIR  (renders previews + contact sheet, never committed)
"""

from __future__ import annotations

import json
import math
import os
import re
import sys
import zlib
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from hyart import Box, Model, model_json, pack, paint, quat_from_euler, quat_matrix, write_json, write_model  # noqa: E402
import goblin_blocks as gb  # noqa: E402
import goblin_items as gi  # noqa: E402
import grizzlecog  # noqa: E402
import hud_icons  # noqa: E402
import hand_items  # noqa: E402
import particles  # noqa: E402
from render import FACE_CORNERS, QUAD_FACE, render  # noqa: E402

COMMON = HERE.parents[1] / "craftory-tech" / "src" / "main" / "resources" / "Common"
RESOURCES = COMMON.parent
ITEM_DIR = RESOURCES / "Server" / "Item" / "Items" / "Craftory"
HITBOX_DIR = RESOURCES / "Server" / "Item" / "Block" / "Hitboxes" / "Craftory"
CORE_RESOURCES = HERE.parents[1] / "craftory-core" / "src" / "main" / "resources"
CORE_ITEM_DIR = CORE_RESOURCES / "Server" / "Item" / "Items" / "Craftory"
CORE_HITBOX_DIR = CORE_RESOURCES / "Server" / "Item" / "Block" / "Hitboxes" / "Craftory"
ICON_DIR = COMMON / "Icons" / "ItemsGenerated" / "Craftory"
ICON_SIZE = 64

CANISTER_FILLS = {"Empty": 0.0, "Quarter": 0.25, "Half": 0.5, "ThreeQuarter": 0.75, "Full": 1.0}


def seed(name: str) -> int:
    return zlib.crc32(name.encode())


def build(model: Model, name: str) -> Model:
    pack(model)
    paint(model, seed(name))
    return model


def model_bounds(model: Model) -> tuple[np.ndarray, np.ndarray]:
    points = []

    def visit(node, rotation, translation, parent_offset):
        local_rotation = quat_matrix(quat_from_euler(*node.rot))
        world_rotation = rotation @ local_rotation
        world_translation = translation + rotation @ (parent_offset + np.asarray(node.pos))
        if node.visible and node.has_shape:
            offset = np.asarray(node.offset)
            stretch = np.asarray(node.stretch)
            center = world_translation + world_rotation @ offset
            if node.quad:
                face = QUAD_FACE.get(node.quad, "front")
                sx, sy, _ = node.size
                h3 = {
                    "front": (sx, sy, 0),
                    "back": (sx, sy, 0),
                    "right": (0, sy, sx),
                    "left": (0, sy, sx),
                    "top": (sx, 0, sy),
                    "bottom": (sx, 0, sy),
                }[face]
                for sign in FACE_CORNERS[face]:
                    points.append(center + world_rotation @ (np.asarray(sign) * np.asarray(h3) * stretch / 2))
            else:
                half = np.asarray(node.size) * stretch / 2
                for sx in (-1, 1):
                    for sy in (-1, 1):
                        for sz in (-1, 1):
                            points.append(center + world_rotation @ (np.asarray((sx, sy, sz)) * half))
        for child in node.children:
            visit(child, world_rotation, world_translation, np.asarray(node.offset))

    for root in model.roots:
        visit(root, np.eye(3), np.zeros(3), np.zeros(3))
    vertices = np.asarray(points, dtype=np.float64)
    return vertices.min(axis=0), vertices.max(axis=0)


def validate_block_models(name: str, models: dict[str, tuple[Model, dict]]) -> None:
    for suffix, (model, _) in models.items():
        lo, hi = model_bounds(model)
        label = f"{name}{suffix}"
        if lo[1] < -0.01:
            raise ValueError(f"{label} geometry extends below y=0: {lo[1]:.3f}")
        if name != "Gutterpipe" and lo[1] > 0.01:
            raise ValueError(f"{label} has no ground-level base at y=0: {lo[1]:.3f}")
        if lo[0] < -17 or hi[0] > 17 or lo[2] < -17 or hi[2] > 17:
            raise ValueError(f"{label} geometry exceeds the block footprint: {lo.tolist()}..{hi.tolist()}")
        for root in model.roots:
            if any(abs(angle) > 0.01 for angle in root.rot):
                raise ValueError(f"{label} root {root.name} has a non-axis-aligned base rotation: {root.rot}")

        if name == "Gutterpipe":
            continue

        def validate_grounded_nodes(node, rotation, translation, parent_offset):
            local_rotation = quat_matrix(quat_from_euler(*node.rot))
            world_rotation = rotation @ local_rotation
            world_translation = translation + rotation @ (parent_offset + np.asarray(node.pos))
            if node.visible and node.has_shape and not node.quad:
                offset = np.asarray(node.offset)
                stretch = np.asarray(node.stretch)
                center = world_translation + world_rotation @ offset
                half = np.asarray(node.size) * stretch / 2
                points = [
                    center + world_rotation @ (np.asarray((sx, sy, sz)) * half)
                    for sx in (-1, 1)
                    for sy in (-1, 1)
                    for sz in (-1, 1)
                ]
                bottom = min(point[1] for point in points)
                if bottom <= 0.01:
                    if abs(bottom) > 0.01:
                        raise ValueError(f"{label} base {node.name} does not sit at y=0: {bottom:.3f}")
                    if not np.allclose(world_rotation, np.eye(3), atol=1e-5):
                        raise ValueError(f"{label} base {node.name} is tilted")
            for child in node.children:
                validate_grounded_nodes(child, world_rotation, world_translation, np.asarray(node.offset))

        for root in model.roots:
            validate_grounded_nodes(root, np.eye(3), np.zeros(3), np.zeros(3))


def hitbox_for_models(models: list[Model]) -> dict:
    bounds = [model_bounds(model) for model in models]
    lo = np.min([item[0] for item in bounds], axis=0)
    hi = np.max([item[1] for item in bounds], axis=0)

    def outward(value: float, lower: bool) -> float:
        rounded = math.floor(value * 32) / 32 if lower else math.ceil(value * 32) / 32
        return round(rounded, 6)

    minimum = {
        "X": outward(0.5 + lo[0] / 32, True),
        "Y": outward(lo[1] / 32, True),
        "Z": outward(0.5 + lo[2] / 32, True),
    }
    maximum = {
        "X": outward(0.5 + hi[0] / 32, False),
        "Y": outward(hi[1] / 32, False),
        "Z": outward(0.5 + hi[2] / 32, False),
    }
    return {"Boxes": [{"Min": minimum, "Max": maximum}]}


def set_item_hitbox(item_path: Path, name: str, hitbox_type: str, states: dict[str, str] | None = None) -> None:
    data = json.loads(item_path.read_text())
    block_type = data.get("BlockType", {})
    if block_type.get("DrawType") != "Model":
        return

    text = item_path.read_text()
    root_match = re.search(r'(?m)^  "BlockType"\s*:\s*\{\s*$', text)
    if not root_match:
        raise ValueError(f"missing BlockType object in {item_path}")
    block_start = root_match.end()
    block_end = text.find("\n  },", block_start)
    if block_end < 0:
        block_end = text.rfind("\n  }")
    block_text = text[block_start:block_end]
    root_pattern = re.compile(r'(?m)^    "HitboxType"\s*:\s*"[^"]+"\s*,?$')
    if root_pattern.search(block_text):
        block_text = root_pattern.sub(f'    "HitboxType": "{hitbox_type}",', block_text, count=1)
        text = text[:block_start] + block_text + text[block_end:]
    else:
        text = text[:block_start] + f'\n    "HitboxType": "{hitbox_type}",' + text[block_start:]

    if states:
        for state, state_hitbox in states.items():
            state_pattern = re.compile(rf'(?m)^(?P<indent>\s*)"{re.escape(state)}"\s*:\s*\{{')
            match = state_pattern.search(text)
            if not match:
                raise ValueError(f"missing state {state} in {item_path}")
            state_start = match.end()
            state_end = text.find("\n" + match.group("indent") + "  },", state_start)
            if state_end < 0:
                state_end = text.find("\n" + match.group("indent") + "}", state_start)
            state_text = text[state_start:state_end]
            existing = re.compile(r'(?m)^(?P<indent>\s*)"HitboxType"\s*:\s*"[^"]+"\s*,?$')
            if existing.search(state_text):
                state_text = existing.sub(
                    lambda found: f'{found.group("indent")}"HitboxType": "{state_hitbox}",',
                    state_text,
                    count=1,
                )
                text = text[:state_start] + state_text + text[state_end:]
            else:
                child_indent = match.group("indent") + "  "
                text = text[:state_start] + f'\n{child_indent}"HitboxType": "{state_hitbox}",' + text[state_start:]
    json.loads(text)
    item_path.write_text(text)


def set_item_particle_target(item_path: Path, system_id: str, target_node: str) -> None:
    text = item_path.read_text()
    pattern = re.compile(
        rf'(?m)^(?P<indent>[ \t]*)"SystemId"\s*:\s*"{re.escape(system_id)}"(?P<system_comma>,?)\s*\n'
        rf'[ \t]*"TargetNodeName"\s*:\s*"[^"]+"(?P<target_comma>,?)'
    )
    replacement = (
        lambda match: f'{match.group("indent")}"SystemId": "{system_id}"{match.group("system_comma")}\n'
        f'{match.group("indent")}"TargetNodeName": "{target_node}"{match.group("target_comma")}'
    )
    text, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise ValueError(f"missing particle target for {system_id} in {item_path}")
    json.loads(text)
    item_path.write_text(text)


def normalize_item_hitbox_ids() -> None:
    for item_path in ITEM_DIR.glob("*.json"):
        item = json.loads(item_path.read_text())
        block_type = item.get("BlockType", {})
        if block_type.get("DrawType") != "Model":
            continue
        base_hitbox = block_type.get("HitboxType")
        state_hitboxes = {}
        definitions = block_type.get("State", {}).get("Definitions", {})
        for state, definition in definitions.items():
            hitbox = definition.get("HitboxType")
            if isinstance(hitbox, str) and "/" in hitbox:
                state_hitboxes[state] = hitbox.rsplit("/", 1)[-1]
        if isinstance(base_hitbox, str) and "/" in base_hitbox:
            base_hitbox = base_hitbox.rsplit("/", 1)[-1]
            set_item_hitbox(item_path, item_path.stem, base_hitbox, state_hitboxes)
        elif state_hitboxes:
            raise ValueError(f"state hitboxes have no base hitbox in {item_path}")


def export_hitboxes(name: str, models: dict[str, tuple[Model, dict]], manifest: list[str]) -> None:
    for item_dir, hitbox_dir, tracked in (
        (ITEM_DIR, HITBOX_DIR, True),
        (CORE_ITEM_DIR, CORE_HITBOX_DIR, False),
    ):
        item_path = item_dir / f"Craftory_{name}.json"
        if not item_path.exists():
            continue
        item = json.loads(item_path.read_text())
        if item.get("BlockType", {}).get("DrawType") != "Model":
            continue

        if name == "Gutterpipe":
            state_hitboxes = {}
            for suffix, (model, _) in models.items():
                state = suffix.removeprefix("_")
                hitbox_type = f"Craftory_Gutterpipe_{state}"
                state_hitboxes[state] = hitbox_type
                path = hitbox_dir / "Gutterpipe" / f"Craftory_Gutterpipe_{state}.json"
                output_path = written(manifest, path) if tracked else path
                write_json(hitbox_for_models([model]), output_path)
            base_hitbox = state_hitboxes["C00"]
            set_item_hitbox(item_path, name, base_hitbox, state_hitboxes)
            continue

        hitbox_type = f"Craftory_{name}"
        path = hitbox_dir / f"Craftory_{name}.json"
        output_path = written(manifest, path) if tracked else path
        write_json(hitbox_for_models([model for model, _ in models.values()]), output_path)
        set_item_hitbox(item_path, name, hitbox_type)


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
        raw = {suffix: fn(**kw) for suffix, kw in variants.items()}
        validate_block_models(name, raw)
        if name == "Gutterpipe":
            master = build(gb.gutterpipe_master(), name)
            master_uv = {node.name: node.uv for node in master.boxes()}
            built = {}
            for suffix, (model, animations) in raw.items():
                for node in model.boxes():
                    if node.has_shape:
                        node.uv = dict(master_uv[node.name])
                model.image = master.image
                built[suffix] = (model, animations)
        else:
            built = {suffix: (build(model, name), animations) for suffix, (model, animations) in raw.items()}
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
            for stale in (d / "Gutterpipe.blockymodel", *d.glob("Gutterpipe_C??_Texture.png")):
                stale.unlink(missing_ok=True)
        if name == "Gasbag":
            for stale in (d / "Gasbag.blockymodel", d / "Gasbag_Texture.png"):
                stale.unlink(missing_ok=True)
        export_hitboxes(name, built, manifest)
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


def export_trails(manifest: list[str]) -> None:
    d = COMMON / "Trails"
    for name, img in particles.trails().items():
        d.mkdir(parents=True, exist_ok=True)
        img.save(written(manifest, d / f"{name}.png"), optimize=True)


def main() -> None:
    manifest: list[str] = []
    export_blocks(manifest)
    normalize_item_hitbox_ids()
    for item_name, system_id, target_node in (
        ("Craftory_Fizz_Canister", "Goblin_Decorative_Broken", "Smoke-Emitter"),
        ("Craftory_Boomshroom_Burner", "Campfire_New_Cartoon", "Flame-Emitter"),
        ("Craftory_Gutter_Boiler", "Goblin_Decorative_Steam", "Steam-Emitter"),
    ):
        set_item_particle_target(ITEM_DIR / f"{item_name}.json", system_id, target_node)
    export_items(manifest)
    hand_items.export(COMMON, COMMON.parent, manifest, build, written)
    export_particles(manifest)
    export_beams(manifest)
    export_trails(manifest)
    grizzlecog.export(manifest, COMMON, build)
    hud_dir = COMMON / "UI" / "Custom" / "Craftory" / "Hud" / "Energy"
    manifest.extend(p.relative_to(COMMON.parent).as_posix() for p in hud_icons.export(hud_dir))
    vanilla_ref = os.environ.get("CRAFTORY_VANILLA_REF")
    if vanilla_ref:
        import retint

        manifest.extend(
            path.relative_to(COMMON.parent).as_posix()
            for path in retint.retint(Path(vanilla_ref).expanduser(), COMMON)
        )
    out = HERE / "manifest.txt"
    preserved = set()
    if out.exists():
        preserved = {
            entry
            for entry in out.read_text().splitlines()
            if entry and (RESOURCES / entry).is_file()
        }
    out.write_text("\n".join(sorted(set(manifest) | preserved)) + "\n")
    print(f"wrote {len(set(manifest))} files; list in {out.relative_to(HERE.parents[1])}")


if __name__ == "__main__":
    main()

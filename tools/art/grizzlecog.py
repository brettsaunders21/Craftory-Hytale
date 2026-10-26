"""Grizzlecog: vanilla Klops rig re-dressed with Craftory attachments (goggles, junk backpack, cog hat).

Attachments use the character density (64 units per block). Each root node is named after the rig bone it
binds to (Head, Chest); children are positioned relative to that bone's mesh centre, like vanilla attachments.
Klops head mesh: 30 x 28 x 28. Chest mesh: 35 x 18 x 26.
"""

from __future__ import annotations

import json

from hyart import Box, Model, write_model
from paint import BRASS, FIZZ, GLIMMER, LEATHER, RUST, TIN, TIN_DARK, brass, glass, glow, leather, metal, rope, soot
from goblin_blocks import BRASS_P, CORRUGATED, CORRUGATED_RUST, DARK_TIN, FIZZ_GLOW, ROPE_P, RUSTY

KLOPS = "NPC/Intelligent/Klops/Models"
MODEL_ID = "Craftory_Grizzlecog"


def cog_hat():
    crown = Box("CogCrown", (24, 10, 24), pos=(0, 17, -1), rot=(-4, 8, 5), paint=metal(BRASS, rust=0.3))
    crown.add(
        Box("CogBrim", (32, 4, 32), pos=(0, -4, 0), paint=metal(RUST, rust=0.3, rivets=True, bolt=TIN)),
        Box("CogHubTop", (10, 3, 10), pos=(0, 6, 0), paint=DARK_TIN),
        Box("CogAxle", (4, 5, 4), pos=(0, 9, 0), rot=(0, 0, 10), paint=brass()),
        Box("Feather", (2, 12, 6), pos=(9, 8, -4), rot=(0, 0, -18), paint=glow(FIZZ), shading="fullbright"),
    )
    for i in range(8):  # cog teeth around the brim
        crown.add(Box(f"CogTooth{i}", (6, 4, 4), pos=(0, -4, 0), offset=(0, 0, 17.5), rot=(0, i * 45, 0),
                      paint=metal(BRASS, rust=0.35, rivets=False)))
    return Model([Box("Head", piece=True).add(crown)], fmt="character")


def goggles():
    band = Box("GoggleStrap", (32, 5, 30), pos=(0, 7, 1), rot=(-6, 0, 0), paint=leather(LEATHER),
               faces=("front", "back", "left", "right"))
    lens_l = Box("GoggleLensL", (9, 9, 4), pos=(-7, 0, 16), rot=(0, 0, 5), paint=metal(BRASS, rust=0.2, rivets=False))
    lens_l.add(Box("GoggleGlassL", (7, 7, 1), pos=(0, 0, 2.2), paint=glow(FIZZ), shading="fullbright"))
    lens_r = Box("GoggleLensR", (10, 10, 5), pos=(7, 0.5, 16.5), rot=(0, 0, -7), paint=metal(TIN_DARK, rust=0.4))
    lens_r.add(Box("GoggleGlassR", (8, 8, 1), pos=(0, 0, 2.7), paint=glow(GLIMMER, (0.1, 0.45, 0.6)),
                   shading="fullbright"),
               Box("GoggleLoupe", (5, 5, 5), pos=(0, 0, 5), paint=brass()))
    band.add(lens_l, lens_r, Box("GoggleBridge", (5, 3, 3), pos=(0, 0.5, 16), paint=BRASS_P))
    return Model([Box("Head", piece=True).add(band)], fmt="character")


def backpack():
    pack = Box("JunkPack", (30, 32, 16), pos=(0, 4, -22), rot=(0, 0, -3), paint=CORRUGATED_RUST)
    pack.add(
        Box("PackLid", (32, 4, 18), pos=(0, 17, 0), rot=(0, 0, 4), paint=RUSTY),
        Box("FizzTank", (10, 20, 10), pos=(-17, -2, 0), paint=glass(FIZZ, 0.7)).add(
            Box("TankCap", (12, 3, 12), pos=(0, 11, 0), paint=BRASS_P),
        ),
        Box("Smokestack", (6, 18, 6), pos=(10, 24, -2), rot=(0, 0, -8), paint=soot()).add(
            Box("StackHat", (9, 2, 9), pos=(0, 9.5, 0), rot=(0, 20, 6), paint=DARK_TIN),
        ),
        Box("PackCog", (2, 16, 16), pos=(16, -4, 0), rot=(25, 0, 0), paint=metal(BRASS, rust=0.3)),
        Box("Wrench", (4, 26, 4), pos=(-6, 6, -9), rot=(0, 0, 25), paint=metal(TIN_DARK, rust=0.3)),
        Box("Bedroll", (34, 9, 9), pos=(0, -19, 2), paint=leather((0.45, 0.36, 0.25))),
        Box("StrapL", (5, 30, 2), pos=(-9, 0, 9.5), paint=ROPE_P),
        Box("StrapR", (5, 30, 2), pos=(9, 0, 9.5), paint=ROPE_P),
        Box("Pot", (8, 7, 8), pos=(0, -4, -11), paint=CORRUGATED),
    )
    return Model([Box("Chest", piece=True).add(pack)], fmt="character")


ATTACHMENTS = {"Grizzlecog_Hat": cog_hat, "Grizzlecog_Goggles": goggles, "Grizzlecog_Backpack": backpack}


def appearance() -> dict:
    vanilla = f"{KLOPS}/Attachments"
    craftory = [{"Model": f"NPC/Craftory/Grizzlecog/{n}.blockymodel",
                 "Texture": f"NPC/Craftory/Grizzlecog/{n}_Texture.png"} for n in ATTACHMENTS]
    return {
        "Parent": "Klops",
        "Model": f"{KLOPS}/Model.blockymodel",
        "Texture": f"{KLOPS}/Model_default.png",
        "DefaultAttachments": [
            {"Model": f"{vanilla}/Cosmetics/Tail/Tail.blockymodel", "Texture": f"{vanilla}/Cosmetics/Tail/Tail_Default.png"},
            {"Model": f"{vanilla}/Merchant/Shoes.blockymodel", "Texture": f"{vanilla}/Merchant/Shoes_Texture.png"},
            {"Model": f"{vanilla}/Merchant/Tunic.blockymodel", "Texture": f"{vanilla}/Merchant/Tunic_Brown.png"},
            {"Model": f"{vanilla}/Cosmetics/Head/Eyebrows/Eyebrows.blockymodel",
             "Texture": f"{vanilla}/Cosmetics/Head/Eyebrows/Eyebrows_White.png"},
            *craftory,
        ],
    }


def export(manifest, common, build):
    d = common / "NPC" / "Craftory" / "Grizzlecog"
    for name, fn in ATTACHMENTS.items():
        m = build(fn(), name)
        p = d / f"{name}.blockymodel"
        write_model(m, p)
        m.image.save(d / f"{name}_Texture.png", optimize=True)
        manifest += [p.relative_to(common.parent).as_posix(), (d / f"{name}_Texture.png").relative_to(common.parent).as_posix()]
    server = common.parent / "Server" / "Models" / "Intelligent" / "Craftory" / f"{MODEL_ID}.json"
    server.parent.mkdir(parents=True, exist_ok=True)
    server.write_text(json.dumps(appearance(), indent=2) + "\n")
    manifest.append(server.relative_to(common.parent).as_posix())

"""Goblin-era Craftory items: held tools, worn goggles, small parts (models used for icons too).

Held/worn items use the character density (64 units per block) like vanilla attachments.
"""

from __future__ import annotations

from hyart import Box, Model
from paint import (
    BRASS, FIZZ, GLIMMER, LEATHER, RUST, SOOT, TIN, TIN_DARK, WOOD, brass, glass, glow, leather, metal,
    plank, rope, soot, solid,
)
from goblin_blocks import (
    BRASS_P, CORRUGATED, CORRUGATED_RUST, CYAN_GLOW, DARK_TIN, FIZZ_GLOW, ROPE_P, RUSTY, TIN_SHEET,
)

GRIP = leather((0.36, 0.22, 0.13))
HANDLE = plank(WOOD, horizontal=False)


def tinkers_wrench():
    handle = Box("Handle", (4, 26, 4), pos=(0, 13, 0), rot=(0, 0, 3), paint=HANDLE)
    handle.add(
        Box("GripWrap", (5, 9, 5), pos=(0, -7, 0), paint=GRIP),
        Box("Pommel", (6, 3, 6), pos=(0, -13, 0), rot=(0, 20, 0), paint=BRASS_P),
        Box("Collar", (6, 3, 6), pos=(0, 10, 0), paint=brass(rivets=False)),
        Box("Head", (14, 6, 5), pos=(1, 15, 0), rot=(0, 0, -6), paint=metal(TIN_DARK, rust=0.35)).add(
            Box("JawFixed", (4, 10, 5), pos=(-5, 6, 0), paint=metal(TIN, rust=0.3)),
            Box("JawMoving", (4, 8, 5), pos=(4, 5, 0), rot=(0, 0, 8), paint=metal(RUST, rust=0.2)),
            Box("AdjustScrew", (8, 2, 2), pos=(0, -2, 3), paint=brass(rivets=False)),
            Box("FizzGauge", (3, 3, 2), pos=(-1, 0, 3.2), paint=FIZZ_GLOW, shading="fullbright"),
        ),
        Box("Lashing", (5, 2, 5), pos=(0, 4, 0), rot=(0, 0, 10), paint=ROPE_P),
    )
    return Model([handle], fmt="character")


def tinkers_hammer():
    handle = Box("Handle", (4, 30, 4), pos=(0, 15, 0), rot=(0, 0, -2), paint=HANDLE)
    handle.add(
        Box("GripWrap", (5, 10, 5), pos=(0, -8, 0), paint=GRIP),
        Box("Head", (22, 9, 9), pos=(2, 15, 0), rot=(0, 0, 4), paint=metal(TIN_DARK, rust=0.45)).add(
            Box("Face", (4, 11, 11), pos=(-12, 0, 0), paint=metal(TIN, rust=0.25)),
            Box("Claw", (6, 6, 6), pos=(12, 1, 0), rot=(0, 0, -25), paint=metal(RUST, rust=0.25)),
            Box("Strap", (6, 10, 10), pos=(2, 0, 0), paint=ROPE_P),
            Box("BoltTop", (3, 2, 3), pos=(-4, 5, 0), paint=BRASS_P),
        ),
    )
    return Model([handle], fmt="character")


def squint_goggles(worn: bool):
    """Worn on the player's Head bone: a `Head` piece with the rig's head offset, so children sit in head-centre
    coordinates of the vanilla 30x28x28 head (front face z=14, eyes at x=+-7.5, y=-1.8)."""
    strap = Box("Strap", (32, 5, 30), pos=(0, -1.5, 0), paint=leather(LEATHER),
                faces=("back", "left", "right") if worn else ("front", "back", "left", "right", "top", "bottom"))
    lens_l = Box("LensL", (10, 10, 4), pos=(7.5, -1.8, 16), rot=(0, 0, 4), paint=metal(BRASS, rust=0.2, rivets=False))
    lens_l.add(Box("GlassL", (7, 7, 1), pos=(0, 0, 2.1), paint=glow(FIZZ), shading="fullbright"))
    lens_r = Box("LensR", (11, 11, 5), pos=(-7.5, -1.3, 16.5), rot=(0, 0, -6),
                 paint=metal(TIN_DARK, rust=0.4, rivets=False))
    lens_r.add(
        Box("GlassR", (8, 8, 1), pos=(0, 0, 2.6), paint=glow(GLIMMER, (0.1, 0.45, 0.6)), shading="fullbright"),
        Box("Loupe", (5, 5, 6), pos=(0, 0, 5.5), paint=brass(rivets=False)),
    )
    parts = [strap, lens_l, lens_r, Box("Bridge", (5, 3, 3), pos=(0, -1, 16), paint=BRASS_P),
             Box("Buckle", (6, 6, 2), pos=(0, -1.5, -15.5), paint=BRASS_P)]
    if worn:
        return Model([Box("Head", pos=(0, 8, -1), offset=(0, 20, 2), piece=True).add(*parts)], fmt="character")
    return Model([Box("Goggles").add(*parts)], fmt="character")


def notebook():
    book = Box("Cover", (18, 22, 5), pos=(0, 11, 0), rot=(0, 0, 3), paint=leather((0.42, 0.26, 0.15)))
    book.add(
        Box("Pages", (16, 20, 4), pos=(1, 0, 0), paint=solid((0.86, 0.80, 0.64), noise=0.02)),
        Box("Spine", (3, 23, 6), pos=(-8.5, 0, 0), paint=leather((0.30, 0.18, 0.10))),
        Box("CogBadge", (7, 7, 1), pos=(1, 3, 2.8), rot=(0, 0, 12), paint=BRASS_P),
        Box("CogHub", (3, 3, 1), pos=(1, 3, 3.4), paint=FIZZ_GLOW, shading="fullbright"),
        Box("Strap", (2, 22, 6), pos=(6, 0, 0), paint=rope()),
        Box("Bookmark", (2, 6, 1), pos=(4, -13, 1), paint=solid((0.55, 0.85, 0.25))),
        Box("CornerPlate", (4, 4, 6), pos=(7, 9, 0), paint=metal(RUST, rust=0.2, rivets=False)),
    )
    return Model([book], fmt="character")


def fizz_charge():
    cell = Box("Cell", (10, 16, 10), pos=(0, 8, 0), rot=(0, 10, 4), paint=glass(FIZZ, 0.85))
    cell.add(
        Box("CapTop", (12, 3, 12), pos=(0, 8, 0), paint=BRASS_P),
        Box("CapBottom", (12, 3, 12), pos=(0, -8, 0), paint=metal(TIN_DARK, rust=0.3, rivets=False)),
        Box("Terminal", (4, 3, 4), pos=(0, 11, 0), paint=metal(TIN, rust=0.1, rivets=False)),
        Box("Wire", (2, 12, 2), pos=(6, 0, 0), rot=(0, 0, 8), paint=rope()),
    )
    return Model([cell], fmt="character")


def boom_valve():
    body = Box("Body", (10, 10, 10), pos=(0, 5, 0), paint=metal(BRASS, rust=0.25))
    body.add(
        Box("PipeIn", (16, 5, 5), pos=(0, -1, 0), paint=DARK_TIN),
        Box("Stem", (3, 6, 3), pos=(0, 7, 0), paint=DARK_TIN),
        Box("Wheel", (12, 2, 12), pos=(0, 10, 0), rot=(0, 15, 6), paint=metal(RUST, rust=0.3, rivets=True)),
        Box("WarnTag", (4, 5, 1), pos=(6, 2, 4), rot=(0, 0, 15), paint=solid((0.92, 0.36, 0.12))),
    )
    return Model([body], fmt="character")


def gutter_gear():
    hub = Box("Hub", (6, 6, 4), pos=(0, 12, 0), paint=BRASS_P)
    rim = Box("Rim", (18, 18, 3), pos=(0, 0, 0), rot=(0, 0, 8), paint=metal(TIN_DARK, rust=0.4, rivets=False))
    for i in range(8):
        tooth = Box(f"Tooth{i}", (4, 4, 3), pos=(0, 0, 0), offset=(0, 10, 0), rot=(0, 0, i * 45 + (3 if i % 3 else -4)),
                    paint=metal(RUST, rust=0.3, rivets=False))
        rim.add(tooth)
    rim.add(Box("Crack", (1, 7, 4), pos=(4, -3, 0), rot=(0, 0, 30), paint=soot()))
    hub.add(rim)
    return Model([hub], fmt="character")


def scrap_plating_bodge():
    plate = Box("Plate", (18, 16, 2), pos=(0, 8, 0), rot=(0, 0, -6), paint=CORRUGATED)
    plate.add(
        Box("Patch", (9, 8, 1), pos=(4, 3, 1.4), rot=(0, 0, 14), paint=metal(RUST, rust=0.2)),
        Box("BoltA", (2, 2, 2), pos=(-7, 6, 1.2), paint=BRASS_P),
        Box("BoltB", (2, 2, 2), pos=(7, -6, 1.2), paint=metal(TIN, rust=0.1, rivets=False)),
        Box("Strap", (20, 3, 3), pos=(0, -3, 0), rot=(0, 0, 4), paint=ROPE_P),
    )
    return Model([plate], fmt="character")


def squawk_box():
    box = Box("Box", (14, 12, 10), pos=(0, 6, 0), rot=(0, 6, 0), paint=metal(TIN, rust=0.35))
    box.add(
        Box("Horn", (6, 6, 8), pos=(3, 4, 7), rot=(-15, 10, 0), paint=brass(rivets=False)).add(
            Box("Bell", (10, 10, 2), pos=(0, 0, 4.5), paint=metal(BRASS, rust=0.15, rivets=False)),
        ),
        Box("Grille", (5, 5, 1), pos=(-4, -1, 5.2), paint=soot()),
        Box("Antenna", (1, 10, 1), pos=(-5, 11, -2), rot=(0, 0, -12), paint=DARK_TIN),
        Box("Bulb", (3, 3, 3), pos=(-6, 16, -2), paint=FIZZ_GLOW, shading="fullbright"),
    )
    return Model([box], fmt="character")


def muffler():
    can = Box("Can", (18, 9, 9), pos=(0, 5, 0), rot=(0, 0, 4), paint=CORRUGATED_RUST)
    can.add(
        Box("EndA", (2, 11, 11), pos=(-9.5, 0, 0), paint=DARK_TIN),
        Box("EndB", (2, 11, 11), pos=(9.5, 0, 0), paint=DARK_TIN),
        Box("Outlet", (6, 4, 4), pos=(12, 1, 0), rot=(0, 0, 10), paint=soot()),
        Box("Wad", (6, 10, 10), pos=(-1, 0, 0), paint=solid((0.55, 0.50, 0.42), noise=0.08)),
        Box("Tie", (2, 10, 10), pos=(4, 0, 0), paint=ROPE_P),
    )
    return Model([can], fmt="character")


def glimmer_lens():
    frame = Box("Monocle", (18, 18, 3), pos=(0, 0, 0), paint=brass(rivets=True))
    frame.add(
        Box("Lens", (12, 12, 1), pos=(0, 0, 2), paint=glass(GLIMMER, 0.8)),
        Box("RimTop", (18, 3, 3), pos=(0, 7.5, 0), paint=BRASS_P),
        Box("RimBottom", (18, 3, 3), pos=(0, -7.5, 0), paint=BRASS_P),
        Box("RimLeft", (3, 12, 3), pos=(-7.5, 0, 0), paint=BRASS_P),
        Box("RimRight", (3, 12, 3), pos=(7.5, 0, 0), paint=BRASS_P),
        Box("Hinge", (4, 4, 4), pos=(10, 3, 0), paint=RUSTY),
        Box("EarArm", (3, 3, 20), pos=(16, 0, 0), rot=(0, 0, 12), paint=CORRUGATED),
        Box("Chain", (2, 2, 12), pos=(-11, -8, 0), rot=(0, 0, -35), paint=brass(rivets=False)),
    )
    return Model([frame], fmt="character")


def gob_jar(filled: bool):
    jar = Box("Jar", (16, 20, 14), pos=(0, 10, 0), paint=glass(GLIMMER if filled else (0.66, 0.78, 0.72), 0.42))
    jar.add(
        Box("JarShoulder", (14, 4, 12), pos=(0, 10, 0), paint=glass(GLIMMER if filled else (0.66, 0.78, 0.72), 0.4)),
        Box("Neck", (9, 4, 9), pos=(0, 12, 0), paint=glass(GLIMMER if filled else (0.66, 0.78, 0.72), 0.4)),
        Box("Rim", (12, 2, 12), pos=(0, 15, 0), paint=brass(rivets=True)),
        Box("Lid", (10, 3, 10), pos=(0, 17, 0), paint=RUSTY),
        Box("Handle", (2, 12, 2), pos=(-9, 8, 0), paint=brass(rivets=False)),
        Box("SealRivet", (2, 2, 2), pos=(0, 13, 6), paint=BRASS_P),
    )
    if filled:
        jar.add(
            Box("CapturedGlimmer", (7, 12, 6), pos=(0, 6, 0), rot=(10, 15, -8), paint=CYAN_GLOW, shading="fullbright"),
            Box("Swirl", (1, 11, 1), pos=(3, 6, 4), rot=(0, 0, 24), paint=CYAN_GLOW, shading="fullbright"),
        )
    return Model([jar], fmt="character")


def codex():
    book = Box("Codex", (20, 24, 5), pos=(0, 12, 0), rot=(0, 0, 4), paint=leather((0.23, 0.31, 0.36)))
    book.add(
        Box("Pages", (17, 21, 3), pos=(1, 0, 0), paint=solid((0.85, 0.81, 0.68), noise=0.03)),
        Box("Spine", (3, 25, 6), pos=(-9, 0, 0), paint=metal(TIN_DARK, rust=0.25)),
        Box("GlimmerSigil", (8, 8, 1), pos=(1, 2, 3), paint=CYAN_GLOW, shading="fullbright"),
        Box("CornerA", (4, 4, 2), pos=(-7, 9, 3), paint=BRASS_P),
        Box("CornerB", (4, 4, 2), pos=(8, -9, 3), paint=BRASS_P),
        Box("Clasp", (2, 22, 6), pos=(8, 0, 0), paint=brass(rivets=False)),
    )
    return Model([book], fmt="character")


def paint_daub(color):
    jar = Box("Daub", (12, 15, 10), pos=(0, 8, 0), rot=(0, 5, -3), paint=solid(color))
    jar.add(
        Box("Cap", (14, 3, 12), pos=(0, 8, 0), paint=BRASS_P),
        Box("DaubMark", (8, 7, 1), pos=(0, -1, 5.4), paint=solid(color)),
        Box("Rim", (13, 2, 11), pos=(0, -7, 0), paint=RUSTY),
    )
    return Model([jar], fmt="character")


def glimmer_dust():
    pile = Box("Dust", (15, 7, 12), pos=(0, 4, 0), rot=(0, 12, 0), paint=CYAN_GLOW, shading="fullbright")
    pile.add(
        Box("FlakeA", (5, 4, 4), pos=(-5, 4, 2), rot=(20, 0, 15), paint=glass(GLIMMER, 0.7)),
        Box("FlakeB", (4, 5, 4), pos=(4, 3, -3), rot=(0, 15, -20), paint=glass(GLIMMER, 0.7)),
        Box("FlakeC", (3, 3, 3), pos=(0, 5, 5), paint=CYAN_GLOW, shading="fullbright"),
    )
    return Model([pile], fmt="character")


def spark_shard():
    shard = Box("SparkShard", (8, 18, 7), pos=(0, 9, 0), rot=(8, 18, -12), paint=CYAN_GLOW, shading="fullbright")
    shard.add(
        Box("ShardFacetA", (3, 10, 3), pos=(-4, 3, 1), rot=(0, 0, 25), paint=glass(GLIMMER, 0.7)),
        Box("ShardFacetB", (3, 8, 3), pos=(4, -3, -1), rot=(0, 0, -20), paint=glass((0.16, 0.65, 0.88), 0.8)),
    )
    return Model([shard], fmt="character")


def riftmoth_wing():
    wing = Box("Wing", (22, 18, 1), pos=(0, 9, 0), rot=(0, 0, -8), paint=glass((0.44, 0.62, 0.72), 0.75))
    wing.add(
        Box("VeinA", (1, 16, 1), pos=(-7, 0, 1), rot=(0, 0, -22), paint=metal(TIN, rust=0.2)),
        Box("VeinB", (1, 16, 1), pos=(0, 0, 1), paint=metal(TIN, rust=0.2)),
        Box("VeinC", (1, 16, 1), pos=(7, 0, 1), rot=(0, 0, 22), paint=metal(TIN, rust=0.2)),
        Box("WingTip", (7, 4, 1), pos=(5, 8, 1), rot=(0, 0, -20), paint=CYAN_GLOW, shading="fullbright"),
    )
    return Model([wing], fmt="character")


def grit(color):
    pile = Box("GritPile", (13, 5, 11), pos=(0, 3, 0), rot=(0, 8, 0), paint=solid(color, noise=0.12))
    pile.add(
        Box("GritA", (6, 4, 5), pos=(-5, 3, 2), rot=(0, 10, 0), paint=metal(color, rust=0.18, rivets=False)),
        Box("GritB", (5, 3, 6), pos=(4, 3, -2), rot=(0, -12, 0), paint=solid(color, noise=0.1)),
    )
    return Model([pile], fmt="character")


ITEMS = {
    "Tinkers_Wrench": tinkers_wrench,
    "Tinkers_Hammer": tinkers_hammer,
    "Tinkers_Notebook": notebook,
    "Fizz_Charge": fizz_charge,
    "Glimmer_Lens": glimmer_lens,
    "Gob_Jar": lambda: gob_jar(False),
    "Gob_Jar_Filled": lambda: gob_jar(True),
    "Codex": codex,
    "Paint_Daub_Blue": lambda: paint_daub((0.19, 0.47, 0.92)),
    "Paint_Daub_Green": lambda: paint_daub((0.22, 0.72, 0.28)),
    "Paint_Daub_Red": lambda: paint_daub((0.88, 0.18, 0.16)),
    "Paint_Daub_Yellow": lambda: paint_daub((0.96, 0.72, 0.12)),
    "Glimmer_Dust": glimmer_dust,
    "Spark_Shard": spark_shard,
    "Riftmoth_Wing": riftmoth_wing,
    "Grit_Adamantite": lambda: grit((0.55, 0.58, 0.70)),
    "Grit_Cobalt": lambda: grit((0.26, 0.40, 0.76)),
    "Grit_Copper": lambda: grit((0.75, 0.38, 0.20)),
    "Grit_Gold": lambda: grit((0.88, 0.68, 0.20)),
    "Grit_Iron": lambda: grit((0.58, 0.60, 0.62)),
    "Grit_Mithril": lambda: grit((0.48, 0.70, 0.82)),
    "Grit_Onyxium": lambda: grit((0.25, 0.23, 0.33)),
    "Grit_Prisma": lambda: grit((0.55, 0.30, 0.78)),
    "Grit_Silver": lambda: grit((0.78, 0.80, 0.84)),
    "Grit_Thorium": lambda: grit((0.45, 0.62, 0.42)),
    "Boom_Valve": boom_valve,
    "Gutter_Gear": gutter_gear,
    "Scrap_Plating_Bodge": scrap_plating_bodge,
    "Scrap_Plating": scrap_plating_bodge,
    "Squawk_Box": squawk_box,
    "Muffler": muffler,
}

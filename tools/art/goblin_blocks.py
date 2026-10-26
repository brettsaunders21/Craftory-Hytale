"""Goblin-era (Tier 1-2) Craftory block models. Units: 32 per block, origin at the bottom centre."""

from __future__ import annotations

import random

from hyart import Box, Model, anim_json
from paint import (
    BRASS, EMBER, FIZZ, GLIMMER, RUST, SOOT, TIN, TIN_DARK, WOOD, brass, flame, glass, glow, leather,
    metal, plank, rope, soot, solid,
)

TIN_SHEET = metal(TIN, rust=0.30)
RUSTY = metal((0.50, 0.40, 0.33), rust=0.6)
CORRUGATED = metal(TIN, rust=0.35, corrugated=True)
CORRUGATED_RUST = metal(RUST, rust=0.25, corrugated=True, bolt=TIN)
DARK_TIN = metal(TIN_DARK, rust=0.25)
BRASS_P = brass()
BRASS_RIV = brass(rivets=True)
SOOT_P = soot()
SOOT_RUST = soot(rust=0.4)
ROPE_P = rope()
WOOD_P = plank()
FIZZ_GLOW = glow(FIZZ)
CYAN_GLOW = glow(GLIMMER, (0.10, 0.45, 0.60))
EMBER_GLOW = glow(EMBER, (0.55, 0.18, 0.05))
DEAD_GLASS = solid((0.20, 0.24, 0.22))
DEAD_EMBER = solid((0.22, 0.17, 0.14))
FLAME_P = flame()


def chicken_leg(prefix: str, pos, splay: float = 0.0, length: float = 9.0, crook: float = 0.0) -> Box:
    """Scrawny two-segment metal leg with a three-toed foot; pos is the hip, below the body."""
    hip = Box(f"{prefix}Hip", pos=pos, rot=(crook, 0, splay))
    thigh = Box(f"{prefix}Thigh", (2, length * 0.55, 2), offset=(0, -length * 0.275, 0), rot=(0, 0, 0), paint=DARK_TIN)
    knee = Box(f"{prefix}Knee", (3, 3, 3), pos=(0, -length * 0.275, 0), paint=BRASS_P)
    shin = Box(f"{prefix}Shin", (2, length * 0.5, 2), pos=(0, -length * 0.25 - 1.5, 0), rot=(0, 0, -splay * 1.6),
               paint=TIN_SHEET)
    foot = Box(f"{prefix}Foot", (2, 1, 2), pos=(0, -length * 0.25, 0), paint=DARK_TIN)
    for i, yaw in enumerate((-35, 0, 35)):
        foot.add(Box(f"{prefix}Toe{i}", (1, 1, 4), pos=(0, 0, 0), offset=(0, 0, 2), rot=(0, yaw, 0), paint=DARK_TIN))
    foot.add(Box(f"{prefix}Spur", (1, 1, 2), offset=(0, 0, -1.5), paint=DARK_TIN))
    shin.add(foot)
    knee.add(shin)
    thigh.add(knee)
    hip.add(thigh)
    return hip


def lashing(name: str, size, pos=(0, 0, 0), rot=(0, 0, 0)) -> Box:
    return Box(name, size, pos=pos, rot=rot, paint=ROPE_P)


def bolt(name: str, pos, s=2, paint=BRASS_P, rot=(0, 0, 0)) -> Box:
    return Box(name, (s, s, 1), pos=pos, rot=rot, paint=paint)


# ------------------------------------------------------------------ Crank-a-Fizz


def crank_a_fizz():
    body = Box("Barrel", (16, 14, 14), pos=(0, 17, 0), rot=(0, 3, -2), paint=CORRUGATED)
    body.add(
        Box("BarrelLid", (17, 2, 15), pos=(0, 8, 0), rot=(0, 0, 2), paint=RUSTY),
        Box("BarrelFloor", (17, 2, 15), pos=(0, -7.5, 0), paint=DARK_TIN),
        lashing("RopeBandA", (17, 1, 15), pos=(-3, 1, 0), rot=(0, 0, 4)),
        lashing("RopeBandB", (17, 1, 15), pos=(4, -3, 0), rot=(0, 0, -3)),
        Box("FizzWindow", (6, 5, 1), pos=(-2, 1, 7.2), paint=FIZZ_GLOW, shading="fullbright"),
        Box("WindowFrame", (8, 1, 1), pos=(-2, 4, 7.4), rot=(0, 0, -6), paint=BRASS_P),
        Box("PatchPlate", (5, 4, 1), pos=(5, -3, 7.3), rot=(0, 0, 9), paint=metal(RUST, rust=0.2)),
        Box("Valve", (3, 4, 3), pos=(4, 10, -2), paint=BRASS_P).add(
            Box("ValveWheel", (5, 1, 5), pos=(0, 2.5, 0), rot=(0, 20, 0), paint=metal(RUST, rust=0.3, rivets=False)),
        ),
        Box("SpoutPipe", (3, 3, 6), pos=(-5, 9, -3), rot=(-25, 0, 0), paint=DARK_TIN),
        Box("GearHub", (2, 8, 8), pos=(8.5, 0, 0), paint=BRASS_RIV),
    )
    pivot = Box("CrankPivot", pos=(10, 0, 0))  # animated: spins around X
    pivot.add(
        Box("CrankAxle", (4, 2, 2), offset=(1, 0, 0), paint=DARK_TIN),
        Box("CrankArm", (1, 2, 9), pos=(3, 0, 0), offset=(0, 0, 3.5), paint=RUSTY).add(
            Box("CrankHandle", (5, 2, 2), pos=(2, 0, 4), paint=plank()),
        ),
    )
    body.add(pivot)
    body.add(chicken_leg("LegL", (-5, -8, 0), splay=8), chicken_leg("LegR", (5, -8, 1), splay=-6, crook=4))
    anim = anim_json(60, {"CrankPivot": {"orientation": [
        (0, (0, 0, 0), "linear"), (15, (90, 0, 0), "linear"), (30, (180, 0, 0), "linear"),
        (45, (270, 0, 0), "linear"), (60, (360, 0, 0), "linear")]}})
    return Model([body]), {"Crank": anim}


# ------------------------------------------------------------------ Boomshroom Burner


def boomshroom_burner(lit: bool):
    box = Box("Firebox", (16, 12, 16), pos=(0, 13, 0), rot=(0, -3, 1), paint=SOOT_RUST)
    box.add(
        Box("Grate", (10, 6, 1), pos=(0, -1, 8.2), paint=EMBER_GLOW if lit else DEAD_EMBER,
            shading="fullbright" if lit else "standard"),
        Box("GrateBarA", (1, 7, 1), pos=(-2.5, -1, 8.8), paint=DARK_TIN),
        Box("GrateBarB", (1, 7, 1), pos=(2.5, -1, 8.8), paint=DARK_TIN),
        Box("GrateLip", (12, 1, 2), pos=(0, -4.5, 8.5), rot=(0, 0, -3), paint=RUSTY),
        Box("SidePlate", (1, 8, 10), pos=(-8.4, 1, 0), rot=(4, 0, 0), paint=CORRUGATED_RUST),
        lashing("Rope", (17, 2, 17), pos=(0, 4, 0), rot=(0, 0, -3)),
        Box("Rim", (18, 2, 18), pos=(0, 7, 0), rot=(0, 2, 0), paint=RUSTY),
    )
    cap = Box("ShroomCap", (20, 5, 20), pos=(0, 16.5, 0), rot=(3, 6, -2),
              paint=metal((0.55, 0.20, 0.14), rust=0.2, rivets=True, bolt=TIN))
    cap.add(
        Box("ShroomCapTop", (13, 3, 13), pos=(0, 4, 0), paint=metal((0.60, 0.24, 0.16), rust=0.15, rivets=False)),
        Box("SpotA", (4, 1, 4), pos=(-4, 5.6, 2), paint=solid((0.86, 0.80, 0.65))),
        Box("SpotB", (3, 1, 3), pos=(4, 5.6, -3), paint=solid((0.86, 0.80, 0.65))),
        Box("SpotC", (3, 3, 1), pos=(5, 1, 10.1), paint=solid((0.86, 0.80, 0.65))),
        Box("FlueStem", (4, 6, 4), pos=(2, 7, 1), rot=(0, 0, 8), paint=SOOT_P),
    )
    # stem posts between firebox rim and cap leave a gap where the flame shows
    for i, (x, z) in enumerate(((-7, -7), (7, -7), (-7, 7), (7, 7))):
        box.add(Box(f"Post{i}", (2, 10, 2), pos=(x, 12, z), rot=(0, 0, (-1) ** i * 3), paint=DARK_TIN))
    box.add(cap)
    if lit:
        fl = Box("Flame", pos=(0, 6.5, 0))  # animated flicker
        fl.add(
            Box("FlameA", (11, 12, 0), offset=(0, 6, 0), quad="+Z", paint=FLAME_P, shading="fullbright",
                double_sided=True),
            Box("FlameB", (11, 12, 0), offset=(0, 6, 0), rot=(0, 90, 0), quad="+Z", paint=FLAME_P,
                shading="fullbright", double_sided=True),
        )
        box.add(fl, Box("Flame-Emitter", pos=(0, 12, 0)))
    for i, (x, z, s) in enumerate(((-6, -5, 10), (6, -5, -10), (0, 6, 0))):
        box.add(chicken_leg(f"Leg{i}", (x, -6, z), splay=s, length=8))
    anims = {}
    if lit:
        anims["Flicker"] = anim_json(36, {
            "Flame": {
                "shapeStretch": [],
                "position": [(0, (0, 0, 0)), (9, (0.4, 0.3, 0)), (18, (-0.3, -0.2, 0.2)), (27, (0.2, 0.4, -0.3)),
                             (36, (0, 0, 0))],
                "orientation": [(0, (0, 0, 0)), (12, (0, 25, 3)), (24, (0, -20, -4)), (36, (0, 0, 0))],
            },
            "FlameA": {"shapeStretch": [(0, (1, 1, 1)), (9, (0.9, 1.2, 1)), (18, (1.05, 0.85, 1)),
                                        (27, (0.95, 1.15, 1)), (36, (1, 1, 1))]},
            "FlameB": {"shapeStretch": [(0, (1, 0.95, 1)), (12, (1.1, 1.15, 1)), (24, (0.9, 0.9, 1)),
                                        (36, (1, 0.95, 1))]},
        })
    return Model([box]), anims


# ------------------------------------------------------------------ Fizz Canister


def fizz_canister(fill: float):
    can = Box("Can", (12, 20, 12), pos=(0, 12, 0), rot=(0, 4, 1.5), paint=metal(TIN, rust=0.25, corrugated=True))
    can.add(
        Box("Gauge", (6, 14, 1), pos=(0, 0, 6.2), paint=glass(FIZZ, fill)),
        Box("GaugeFrameL", (1, 16, 2), pos=(-3.5, 0, 6.2), paint=BRASS_P),
        Box("GaugeFrameR", (1, 16, 2), pos=(3.5, 0, 6.2), rot=(0, 0, 2), paint=BRASS_P),
        Box("Lid", (14, 3, 14), pos=(0, 11, 0), rot=(0, -6, 0), paint=RUSTY).add(
            Box("Cork", (4, 3, 4), pos=(2, 3, -1), rot=(0, 0, 6), paint=solid((0.62, 0.45, 0.28))),
            Box("TerminalA", (2, 3, 2), pos=(-4, 2.5, 3), paint=BRASS_P),
            Box("TerminalB", (2, 2, 2), pos=(4, 2, 3), paint=metal(RUST, rust=0.2, rivets=False)),
        ),
        Box("Base", (14, 3, 14), pos=(0, -10.5, 0), paint=DARK_TIN),
        lashing("RopeHandle", (1, 6, 10), pos=(-6.8, 4, 0), rot=(0, 0, 8)),
        lashing("RopeBand", (13, 2, 13), pos=(0, -5, 0), rot=(0, 0, -4)),
        Box("WarningPlate", (5, 4, 1), pos=(3, -7, -6.3), rot=(0, 0, 7), paint=metal((0.80, 0.66, 0.22), rust=0.3)),
    )
    if fill > 0:
        can.add(Box("FizzDrip", (1, 3, 1), pos=(-3, 9, 6.5), paint=FIZZ_GLOW, shading="fullbright"))
    else:
        can.add(Box("FizzDrip", (1, 3, 1), pos=(-3, 9, 6.5), paint=DEAD_GLASS))
    return Model([can]), {}


# ------------------------------------------------------------------ Gutterpipe


def gutterpipe(end_north=True, end_south=True):
    pipe = Box("Pipe", (10, 10, 32), pos=(0, 16, 0), paint=metal(TIN_DARK, rust=0.45, corrugated=True))
    pipe.add(
        Box("Seam", (11, 2, 32), pos=(0, 4.6, 0), rot=(0, 0, 1.5), paint=RUSTY),
        lashing("Lashing", (11, 11, 3), pos=(0, 0, 4), rot=(0, 0, 6)),
        Box("Patch", (1, 6, 7), pos=(5.2, -1, -6), rot=(4, 0, 0), paint=metal(RUST, rust=0.2)),
        bolt("BoltA", (2, 3, -8), paint=BRASS_P),
    )
    if end_north:
        pipe.add(Box("FlangeN", (14, 14, 3), pos=(0, 0, -14.5), rot=(0, 0, 4), paint=metal(RUST, rust=0.3)))
    if end_south:
        pipe.add(Box("FlangeS", (14, 14, 3), pos=(0, 0, 14.5), rot=(0, 0, -3), paint=metal(BRASS, rust=0.3)))
    return Model([pipe]), {}


# ------------------------------------------------------------------ Gnasher Pit


def teeth(prefix, count, flip):
    out = []
    for i in range(count):
        out.append(Box(f"{prefix}Tooth{i}", (2, 3, 2), pos=(-6 + i * 4 + random.choice((0, 0.5)), -2 * flip, 0),
                       rot=(0, 0, random.choice((-8, 0, 6))), paint=metal((0.78, 0.78, 0.72), rust=0.15, rivets=False)))
    return out


def gnasher_pit():
    random.seed(3)
    crate = Box("Crate", (24, 14, 22), pos=(0, 7, 0), rot=(0, 2, 0), paint=CORRUGATED_RUST)
    crate.add(
        Box("CrateRim", (26, 2, 24), pos=(0, 7, 0), rot=(0, 0, -1.5), paint=RUSTY),
        Box("Plank", (26, 3, 1), pos=(0, -2, 11.3), rot=(0, 0, 3), paint=WOOD_P),
        Box("Chute", (6, 4, 6), pos=(6, -3, 13), rot=(30, 0, 0), paint=DARK_TIN),
        Box("GritHeap", (5, 2, 4), pos=(6, -6, 16), paint=solid((0.45, 0.42, 0.38), noise=0.08)),
        Box("FizzLamp", (3, 3, 3), pos=(-10, 9, -9), paint=FIZZ_GLOW, shading="fullbright"),
        Box("LampPost", (1, 4, 1), pos=(-10, 6.5, -9), paint=DARK_TIN),
        lashing("RopeCorner", (2, 14, 2), pos=(-12, 0, 11), rot=(0, 0, 5)),
        Box("Hopper", (20, 8, 6), pos=(0, 11, -9), rot=(-18, 0, 2), paint=metal(TIN, rust=0.4)),
    )
    jaw_l = Box("JawL", pos=(0, 8.5, -4))  # animated: hinge at the back, chomps around X
    jaw_l.add(Box("JawLPlate", (20, 3, 11), offset=(0, 0, 5.5), paint=SOOT_RUST).add(*teeth("JawL", 5, 1)))
    jaw_r = Box("JawR", pos=(0, 8.5, 6))
    jaw_r.add(Box("JawRPlate", (20, 3, 4), offset=(0, 0, 1), paint=DARK_TIN))
    crate.add(jaw_l, jaw_r)
    crate.add(
        Box("GearL", (2, 10, 10), pos=(-12.5, 2, 0), rot=(30, 0, 0), paint=BRASS_RIV),
        Box("GearR", (2, 8, 8), pos=(12.5, 1, -2), rot=(10, 0, 0), paint=metal(RUST, rust=0.2)),
    )
    anim = anim_json(40, {
        "JawL": {"orientation": [(0, (0, 0, 0)), (8, (-38, 0, 0)), (16, (0, 0, 0), "linear"), (22, (-20, 0, 0)),
                                 (30, (0, 0, 0), "linear"), (40, (0, 0, 0))]},
        "GearL": {"orientation": [(0, (0, 0, 0), "linear"), (20, (180, 0, 0), "linear"), (40, (360, 0, 0), "linear")]},
        "GearR": {"orientation": [(0, (0, 0, 0), "linear"), (20, (-180, 0, 0), "linear"),
                                  (40, (-360, 0, 0), "linear")]},
    })
    return Model([crate]), {"Chomp": anim}


# ------------------------------------------------------------------ Scorch Oven


def scorch_oven(lit: bool):
    oven = Box("Oven", (24, 22, 22), pos=(0, 11, 0), rot=(0, -2, 0), paint=SOOT_RUST)
    oven.add(
        Box("Door", (12, 10, 2), pos=(-2, -1, 11.2), rot=(0, 0, -3), paint=BRASS_RIV).add(
            Box("DoorSlot", (8, 2, 1), pos=(0, 2, 1), paint=EMBER_GLOW if lit else DEAD_EMBER,
                shading="fullbright" if lit else "standard"),
            Box("DoorLatch", (2, 3, 2), pos=(5, -1, 1.2), paint=DARK_TIN),
        ),
        Box("Ashpan", (14, 3, 3), pos=(-2, -9, 11), paint=DARK_TIN),
        Box("RoofSheet", (26, 2, 24), pos=(0, 11.5, 0), rot=(0, 0, 3), paint=CORRUGATED_RUST),
        Box("SidePatchL", (1, 10, 12), pos=(-12.4, 2, 1), rot=(5, 0, 0), paint=CORRUGATED),
        Box("SidePatchR", (1, 8, 8), pos=(12.4, -4, -3), rot=(-4, 0, 0), paint=metal(RUST, rust=0.3)),
        Box("Thermo", (3, 6, 1), pos=(8, 4, 11.3), paint=glass(EMBER if lit else (0.3, 0.3, 0.3), 0.7 if lit else 0.2)),
        lashing("RopeBand", (25, 2, 23), pos=(0, 6, 0), rot=(0, 0, -2)),
        Box("Chimney", (6, 14, 6), pos=(7, 18, -6), rot=(0, 0, -7), paint=SOOT_P).add(
            Box("ChimneyCap", (9, 2, 9), pos=(0, 7.5, 0), rot=(0, 15, 6), paint=RUSTY),
        ),
        Box("Foot0", (4, 2, 4), pos=(-9, -11.5, -8), paint=DARK_TIN),
        Box("Foot1", (4, 3, 4), pos=(9, -11.0, -8), paint=DARK_TIN),
        Box("Foot2", (4, 2, 4), pos=(-9, -11.5, 8), paint=DARK_TIN),
        Box("Foot3", (4, 2, 4), pos=(9, -11.5, 8), paint=DARK_TIN),
    )
    return Model([oven]), {}


# ------------------------------------------------------------------ Wakebell Anchor


def wakebell_anchor(lit: bool):
    base = Box("Base", (20, 6, 20), pos=(0, 3, 0), rot=(0, 5, 0), paint=CORRUGATED_RUST)
    post = Box("Post", (4, 24, 4), pos=(-5, 15, -3), rot=(0, 0, -3), paint=plank(WOOD, horizontal=False))
    arm = Box("Arm", (14, 3, 3), pos=(5, 10, 0), rot=(0, 0, -5), paint=DARK_TIN)
    swing = Box("BellPivot", pos=(4, -1.5, 0))  # animated: sways around Z/X
    bell = Box("Bell", (8, 8, 8), pos=(0, -6, 0), paint=metal(BRASS, rust=0.15, rivets=True, bolt=TIN))
    bell.add(
        Box("BellLip", (10, 2, 10), pos=(0, -4.5, 0), paint=brass()),
        Box("BellCrown", (4, 2, 4), pos=(0, 5, 0), paint=BRASS_P),
        Box("Clapper", (2, 3, 2), pos=(0, -6, 0), paint=CYAN_GLOW if lit else DARK_TIN,
            shading="fullbright" if lit else "standard"),
    )
    swing.add(lashing("BellRope", (1, 3, 1), pos=(0, -1, 0)), bell)
    arm.add(swing, lashing("ArmLash", (4, 4, 4), pos=(-6, 0, 0), rot=(0, 0, 10)))
    post.add(arm, Box("Lamp", (4, 4, 4), pos=(0, 13, 0), paint=CYAN_GLOW if lit else DEAD_GLASS,
                      shading="fullbright" if lit else "standard"),
             Box("LampHat", (6, 2, 6), pos=(0, 15.5, 0), rot=(0, 30, 4), paint=RUSTY),
             Box("Brace", (2, 10, 2), pos=(4, -7, 0), rot=(0, 0, -35), paint=DARK_TIN))
    base.add(post,
             Box("Anchor", (10, 3, 4), pos=(4, 4, 5), rot=(0, -20, 0), paint=metal(TIN_DARK, rust=0.5)),
             Box("AnchorFluke", (3, 5, 3), pos=(8, 5, 6), rot=(0, -20, 25), paint=metal(TIN_DARK, rust=0.5)),
             lashing("BaseRope", (21, 2, 21), pos=(0, 1, 0), rot=(0, 0, 2)),
             Box("GlimmerShard", (3, 5, 3), pos=(-5, 5, 6), rot=(10, 30, 15), paint=CYAN_GLOW if lit else DEAD_GLASS,
                 shading="fullbright" if lit else "standard"))
    anims = {"Sway": anim_json(90, {"BellPivot": {"orientation": [
        (0, (0, 0, 0)), (22, (6, 0, 12)), (45, (0, 0, 0)), (67, (-6, 0, -12)), (90, (0, 0, 0))]}})}
    return Model([base]), anims


# ------------------------------------------------------------------ Junk Heart pieces (PR #9)


def junk_heart_firebox(lit: bool):
    fb = Box("Firebox", (30, 26, 30), pos=(0, 13, 0), rot=(0, 1.5, 0), paint=SOOT_RUST)
    fb.add(
        Box("Mouth", (16, 10, 1), pos=(0, -3, 15.2), paint=EMBER_GLOW if lit else DEAD_EMBER,
            shading="fullbright" if lit else "standard"),
        Box("MouthHood", (20, 3, 4), pos=(0, 3.5, 16), rot=(-10, 0, -2), paint=RUSTY),
        Box("GrateBar0", (1, 11, 1), pos=(-5, -3, 16), paint=DARK_TIN),
        Box("GrateBar1", (1, 11, 1), pos=(0, -3, 16), paint=DARK_TIN),
        Box("GrateBar2", (1, 11, 1), pos=(5, -3, 16), paint=DARK_TIN),
        Box("FizzCoil", (4, 12, 4), pos=(-12, 4, 13), paint=FIZZ_GLOW, shading="fullbright"),
        Box("CoilCage", (6, 2, 6), pos=(-12, 11, 13), paint=BRASS_P),
        Box("PlateL", (1, 18, 20), pos=(-15.3, 1, -2), rot=(3, 0, 0), paint=CORRUGATED),
        Box("PlateR", (1, 14, 14), pos=(15.3, -3, 4), rot=(-2, 0, 0), paint=metal(RUST, rust=0.3)),
        Box("Top", (32, 2, 32), pos=(0, 13, 0), rot=(0, 0, 1), paint=RUSTY),
        Box("Collar", (12, 3, 12), pos=(0, 15, 0), paint=DARK_TIN),
        lashing("RopeBand", (31, 2, 31), pos=(0, 7, 0), rot=(0, 0, -1.5)),
        Box("Gauge", (5, 5, 1), pos=(10, 6, 15.3), paint=brass(rivets=False)),
    )
    return Model([fb]), {}


def rattlewheel(on: bool):
    base = Box("WheelBase", (22, 4, 22), pos=(0, 2, 0), rot=(0, 2, 0), paint=CORRUGATED_RUST)
    base.add(
        Box("BearingPost", (4, 14, 4), pos=(0, 10, 0), paint=DARK_TIN),
        Box("BraceL", (3, 12, 3), pos=(-7, 8, 0), rot=(0, 0, -18), paint=plank()),
        Box("BraceR", (3, 12, 3), pos=(7, 8, 0), rot=(0, 0, 18), paint=plank(WOOD, horizontal=False)),
        bolt("BearingBoltL", (-3, 15, 2), s=2),
        bolt("BearingBoltR", (3, 15, 2), s=2, paint=RUSTY),
    )
    wheel = Box("WheelPivot", pos=(0, 17, 0))
    wheel.add(
        Box("Axle", (4, 4, 12), paint=DARK_TIN),
        Box("RimA", (28, 3, 3), pos=(0, 0, 0), rot=(0, 0, 45), paint=metal(TIN, rust=0.25)),
        Box("RimB", (28, 3, 3), pos=(0, 0, 0), rot=(0, 0, -45), paint=CORRUGATED),
        Box("PaddleA", (7, 2, 11), pos=(0, 10, 0), rot=(0, 0, 3), paint=plank()),
        Box("PaddleB", (7, 2, 11), pos=(10, 0, 0), rot=(0, 0, 93), paint=CORRUGATED_RUST),
        Box("PaddleC", (7, 2, 11), pos=(0, -10, 0), rot=(0, 0, 183), paint=plank(WOOD, horizontal=False)),
        Box("PaddleD", (7, 2, 11), pos=(-10, 0, 0), rot=(0, 0, 273), paint=metal(RUST, rust=0.3)),
        Box("Hub", (8, 8, 5), paint=FIZZ_GLOW if on else BRASS_P,
            shading="fullbright" if on else "standard"),
        bolt("HubCap", (0, 0, 3), s=4, paint=RUSTY if on else DARK_TIN),
    )
    return Model([base, wheel]), {}


def shroom_bed():
    mud = Box("MudPlot", (28, 4, 24), pos=(0, 2, 0), rot=(0, -2, 0), paint=metal(RUST, rust=0.4))
    mud.add(
        Box("Soil", (23, 2, 19), pos=(0, 3, 0), paint=SOOT_RUST),
        Box("CanopyPostA", (2, 24, 2), pos=(-11, 14, -9), rot=(0, 0, 2), paint=plank()),
        Box("CanopyPostB", (2, 24, 2), pos=(11, 14, -9), rot=(0, 0, -3), paint=plank(WOOD, horizontal=False)),
        Box("CanopyPostC", (2, 24, 2), pos=(-11, 14, 9), paint=plank(WOOD, horizontal=False)),
        Box("CanopyPostD", (2, 24, 2), pos=(11, 14, 9), rot=(0, 0, 2), paint=plank()),
        Box("Canopy", (31, 3, 26), pos=(0, 26, 0), rot=(0, 0, 2), paint=CORRUGATED),
        Box("CanopyPatch", (12, 1, 10), pos=(-5, 28, 2), rot=(0, 3, -2), paint=RUSTY),
        lashing("FrontLash", (31, 1, 1), pos=(0, 24, 12), rot=(0, 0, -1)),
        Box("StemA", (3, 7, 3), pos=(-6, 8, -3), paint=DARK_TIN),
        Box("CapA", (9, 3, 8), pos=(-6, 13, -3), rot=(0, 0, 2), paint=EMBER_GLOW),
        Box("StemB", (3, 5, 3), pos=(5, 7, 4), paint=DARK_TIN),
        Box("CapB", (8, 3, 7), pos=(5, 11, 4), rot=(0, 0, -3), paint=FIZZ_GLOW),
        Box("StemC", (2, 4, 2), pos=(0, 6, -8), paint=DARK_TIN),
        Box("CapC", (6, 2, 6), pos=(0, 9, -8), paint=BRASS_P),
    )
    return Model([mud]), {}


def scrap_plating():
    core = Box("Core", (30, 30, 30), pos=(0, 16, 0), paint=DARK_TIN)
    core.add(
        Box("SheetN", (31, 31, 1), pos=(0.5, 0, -15.6), rot=(0, 0, 1), paint=CORRUGATED),
        Box("SheetS", (31, 31, 1), pos=(-0.5, 0, 15.6), rot=(0, 0, -1), paint=CORRUGATED_RUST),
        Box("SheetW", (1, 31, 31), pos=(-15.6, 0, 0), paint=metal(TIN, rust=0.5)),
        Box("SheetE", (1, 31, 31), pos=(15.6, 0, 0.5), paint=metal(RUST, rust=0.2, corrugated=True)),
        Box("SheetTop", (32, 1, 32), pos=(0, 15.6, 0), rot=(0, 1.5, 0), paint=metal(TIN_DARK, rust=0.5)),
        Box("SheetBottom", (32, 1, 32), pos=(0, -15.6, 0), paint=DARK_TIN),
        Box("PatchS", (12, 9, 1), pos=(6, 6, 16.3), rot=(0, 0, 8), paint=brass(rivets=True)),
        Box("PatchE", (1, 10, 8), pos=(16.3, -7, -7), rot=(6, 0, 0), paint=metal(TIN, rust=0.3)),
        Box("StrapS", (32, 3, 1), pos=(0, -6, 16.4), rot=(0, 0, -4), paint=ROPE_P),
    )
    return Model([core]), {}


def chimney(cap: bool):
    pipe = Box("Flue", (14, 32, 14), pos=(0, 16, 0), rot=(0, 4, 1.5), paint=SOOT_RUST)
    pipe.add(
        Box("BandLow", (16, 3, 16), pos=(0, -10, 0), rot=(0, 0, -2), paint=RUSTY),
        Box("BandHigh", (16, 3, 16), pos=(0, 8, 0), rot=(0, 0, 3), paint=BRASS_RIV),
        Box("PatchW", (1, 8, 6), pos=(-7.3, -1, 2), rot=(6, 0, 0), paint=CORRUGATED_RUST),
        lashing("Wire", (15, 1, 15), pos=(0, 1, 0), rot=(0, 0, 4)),
    )
    if cap:
        pipe.add(Box("Hat", (20, 3, 20), pos=(0, 13, 0), rot=(4, 10, -5), paint=metal(TIN_DARK, rust=0.5)).add(
            Box("HatPeak", (10, 3, 10), pos=(0, 3, 0), paint=SOOT_P),
            Box("HatStrutA", (2, 6, 2), pos=(-6, -3.5, 0), paint=DARK_TIN),
            Box("HatStrutB", (2, 6, 2), pos=(6, -3.5, 0), paint=DARK_TIN),
        ), Box("Smoke-Emitter", pos=(0, 17, 0)))
    return Model([pipe]), {}


def storm_vane(on: bool):
    base = Box("Foot", (10, 3, 10), pos=(0, 1.5, 0), paint=DARK_TIN)
    mast = Box("Mast", (3, 27, 3), pos=(0, 16, 0), paint=RUSTY)
    mast.add(
        Box("Crossbar", (22, 3, 3), pos=(0, 7, 0), rot=(0, 0, -5), paint=BRASS_P),
        Box("Vane", (3, 12, 1), pos=(0, 7, 2.2), rot=(0, 0, -12), paint=FIZZ_GLOW if on else CORRUGATED),
        Box("CopperWire", (1, 17, 1), pos=(2, -4, 2), rot=(0, 0, 8), paint=BRASS_P),
        Box("Spark", (4, 4, 2), pos=(0, 11, 3), paint=CYAN_GLOW if on else DEAD_GLASS,
            shading="fullbright" if on else "standard"),
    )
    base.add(mast)
    return Model([base]), {}


def fizz_damper(on: bool):
    body = Box("Canister", (18, 17, 14), pos=(0, 9, 0), rot=(0, -4, 2), paint=CORRUGATED_RUST)
    body.add(
        Box("Rim", (20, 2, 16), pos=(0, 8, 0), paint=BRASS_RIV),
        Box("Window", (8, 8, 1), pos=(0, 0, 7.5), paint=FIZZ_GLOW if on else DEAD_GLASS,
            shading="fullbright" if on else "standard"),
        Box("Nozzle", (6, 4, 6), pos=(0, 13, 0), paint=DARK_TIN),
        Box("Foam", (10, 5, 10), pos=(0, 17, 0), paint=FIZZ_GLOW if on else DEAD_GLASS,
            shading="fullbright" if on else "standard"),
        lashing("Strap", (20, 2, 2), pos=(0, 2, 8)),
        bolt("BoltA", (-7, 0, 8), 2),
        bolt("BoltB", (7, 0, 8), 2),
    )
    return Model([body]), {}


def tether_stake():
    post = Box("Post", (5, 28, 5), pos=(0, 14, 0), rot=(0, 0, 5), paint=RUSTY)
    post.add(
        Box("Spike", (3, 8, 3), pos=(0, -18, 0), rot=(0, 0, -5), paint=DARK_TIN),
        Box("Clamp", (9, 4, 9), pos=(0, 4, 0), paint=BRASS_RIV),
        Box("Coil", (7, 3, 7), pos=(0, 10, 0), rot=(0, 0, 8), paint=CYAN_GLOW, shading="fullbright"),
        Box("Wire", (2, 16, 2), pos=(4, -1, 0), rot=(0, 0, -8), paint=BRASS_P),
        bolt("Bolt", (0, 4, 3), 2),
    )
    return Model([post]), {}


BLOCKS = {
    # name: (builder, kwargs-variants)
    "Crank_A_Fizz": (crank_a_fizz, {"": {}}),
    "Rattlewheel": (rattlewheel, {"_Off": {"on": False}, "_On": {"on": True}}),
    "Shroom_Bed": (shroom_bed, {"": {}}),
    "Boomshroom_Burner": (boomshroom_burner, {"_Off": {"lit": False}, "_On": {"lit": True}}),
    "Fizz_Canister": (fizz_canister, {}),
    "Gutterpipe": (gutterpipe, {"": {}}),
    "Gnasher_Pit": (gnasher_pit, {"": {}}),
    "Scorch_Oven": (scorch_oven, {"_Off": {"lit": False}, "_On": {"lit": True}}),
    "Wakebell_Anchor": (wakebell_anchor, {"_Off": {"lit": False}, "_On": {"lit": True}}),
    "Junk_Heart_Firebox": (junk_heart_firebox, {"_Off": {"lit": False}, "_On": {"lit": True}}),
    "Scrap_Plating": (scrap_plating, {"": {}}),
    "Junk_Heart_Chimney": (chimney, {"": {"cap": False}, "_Cap": {"cap": True}}),
    "Storm_Vane": (storm_vane, {"_Off": {"on": False}, "_On": {"on": True}}),
    "Fizz_Damper": (fizz_damper, {"_Off": {"on": False}, "_On": {"on": True}}),
    "Tether_Stake": (tether_stake, {"": {}}),
}

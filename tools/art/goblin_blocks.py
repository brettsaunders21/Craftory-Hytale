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


def lashing(name: str, size, pos=(0, 0, 0), rot=(0, 0, 0)) -> Box:
    return Box(name, size, pos=pos, rot=rot, paint=ROPE_P)


def bolt(name: str, pos, s=2, paint=BRASS_P, rot=(0, 0, 0)) -> Box:
    return Box(name, (s, s, 1), pos=pos, rot=rot, paint=paint)


# ------------------------------------------------------------------ Crank-a-Fizz


def crank_a_fizz():
    body = Box("Barrel", (18, 14, 26), pos=(0, 10, 0), paint=CORRUGATED)
    body.add(
        Box("Skid", (26, 4, 28), pos=(0, -8, 0), paint=CORRUGATED_RUST),
        Box("BarrelLid", (19, 2, 27), pos=(0, 8, 0), paint=RUSTY),
        Box("BarrelFloor", (19, 2, 27), pos=(0, -7.5, 0), paint=DARK_TIN),
        lashing("RopeBandA", (19, 1, 27), pos=(-3, 1, 0)),
        lashing("RopeBandB", (19, 1, 27), pos=(4, -3, 0)),
        Box("FizzWindow", (6, 5, 1), pos=(-2, 1, 13.2), paint=FIZZ_GLOW, shading="fullbright"),
        Box("WindowFrame", (8, 1, 1), pos=(-2, 4, 13.4), rot=(0, 0, -6), paint=BRASS_P),
        Box("PatchPlate", (5, 4, 1), pos=(5, -3, 13.3), rot=(0, 0, 9), paint=metal(RUST, rust=0.2)),
        Box("Valve", (3, 4, 3), pos=(4, 10, -2), paint=BRASS_P).add(
            Box("ValveWheel", (5, 1, 5), pos=(0, 2.5, 0), rot=(0, 20, 0), paint=metal(RUST, rust=0.3, rivets=False)),
        ),
        Box("SpoutPipe", (3, 3, 6), pos=(-5, 9, -3), rot=(-25, 0, 0), paint=DARK_TIN),
        Box("GearHub", (2, 8, 8), pos=(8.5, 0, 0), paint=BRASS_RIV),
        Box("Steam-Emitter", pos=(0, 13, 0)),
    )
    pivot = Box("CrankPivot", pos=(9.5, 0, 0))  # animated: spins around X
    pivot.add(
        Box("CrankAxle", (4, 2.2, 2), offset=(1, 0, 0), paint=DARK_TIN),
        Box("CrankArm", (1, 2, 9), pos=(3, 0, 0), offset=(0, 0, 3.6), paint=RUSTY).add(
            Box("CrankHandle", (5, 2, 2), pos=(0.8, 0.2, 4), paint=plank()),
        ),
    )
    body.add(pivot)
    anim = anim_json(60, {"CrankPivot": {"orientation": [
        (0, (0, 0, 0), "linear"), (15, (90, 0, 0), "linear"), (30, (180, 0, 0), "linear"),
        (45, (270, 0, 0), "linear"), (60, (360, 0, 0), "linear")]}})
    return Model([body]), {"Crank": anim}


# ------------------------------------------------------------------ Boomshroom Burner


def boomshroom_burner(lit: bool):
    box = Box("Firebox", (22, 14, 22), pos=(0, 8, 0), paint=SOOT_RUST)
    box.add(
        Box("Skid", (28, 4, 28), pos=(0, -6, 0), paint=CORRUGATED_RUST),
        Box("Grate", (10, 6, 1), pos=(0, -1, 8.2), paint=EMBER_GLOW if lit else DEAD_EMBER,
            shading="fullbright" if lit else "standard"),
        Box("GrateBarA", (1, 7, 1), pos=(-2.5, -1, 8.8), paint=DARK_TIN),
        Box("GrateBarB", (1, 7, 1), pos=(2.5, -1, 8.8), paint=DARK_TIN),
        Box("GrateLip", (12, 1, 2), pos=(0, -4.5, 8.5), rot=(0, 0, -3), paint=RUSTY),
        Box("SidePlate", (1, 8, 10), pos=(-8.4, 1, 0), rot=(4, 0, 0), paint=CORRUGATED_RUST),
        lashing("Rope", (17, 2, 17), pos=(0, 4, 0), rot=(0, 0, -3)),
        Box("Rim", (18, 2, 18), pos=(0, 7, 0), rot=(0, 2, 0), paint=RUSTY),
    )
    cap = Box("ShroomCap", (26, 5, 26), pos=(0, 16, 0),
              paint=metal((0.55, 0.20, 0.14), rust=0.2, rivets=True, bolt=TIN))
    cap.add(
        Box("ShroomCapTop", (20, 3, 20), pos=(0, 4, 0), paint=metal((0.60, 0.24, 0.16), rust=0.15, rivets=False)),
        Box("SpotA", (4, 1, 4), pos=(-4, 5.6, 2), paint=solid((0.86, 0.80, 0.65))),
        Box("SpotB", (3, 1, 3), pos=(4, 5.6, -3), paint=solid((0.86, 0.80, 0.65))),
        Box("SpotC", (3, 3, 1), pos=(5, 2, 10.1), paint=solid((0.86, 0.80, 0.65))),
        Box("FlueStem", (4, 6, 4), pos=(2, 7, 1), rot=(0, 0, 8), paint=SOOT_P),
        Box("Smoke-Emitter", pos=(2, 11, 1)),
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
        box.add(Box("Flame-Emitter", pos=(0, 6.5, 0)), fl)
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
    can = Box("Can", (24, 20, 24), pos=(0, 12, 0), paint=metal(TIN, rust=0.25, corrugated=True))
    can.add(
        Box("Gauge", (8, 14, 1), pos=(0, 0, 12.2), paint=glass(FIZZ, fill)),
        Box("GaugeFrameL", (1, 16, 2), pos=(-4.5, 0, 12.2), paint=BRASS_P),
        Box("GaugeFrameR", (1, 16, 2), pos=(4.5, 0, 12.2), paint=BRASS_P),
        Box("Lid", (26, 3, 26), pos=(0, 11, 0), paint=RUSTY).add(
            Box("Cork", (4, 3, 4), pos=(2, 3, -1), rot=(0, 0, 6), paint=solid((0.62, 0.45, 0.28))),
            Box("TerminalA", (2, 3, 2), pos=(-4, 2.5, 3), paint=BRASS_P),
            Box("TerminalB", (2, 2, 2), pos=(4, 2, 3), paint=metal(RUST, rust=0.2, rivets=False)),
        ),
        Box("Base", (26, 3, 26), pos=(0, -10.5, 0), paint=DARK_TIN),
        lashing("RopeHandle", (1, 6, 10), pos=(-12.8, 4, 0), rot=(0, 0, 8)),
        lashing("RopeBand", (25, 2, 25), pos=(0, -5, 0)),
        Box("WarningPlate", (5, 4, 1), pos=(3, -7, -12.3), rot=(0, 0, 7), paint=metal((0.80, 0.66, 0.22), rust=0.3)),
        Box("Smoke-Emitter", pos=(0, 16, 0)),
    )
    if fill > 0:
        can.add(Box("FizzDrip", (1, 3, 1), pos=(-3, 9, 6.5), paint=FIZZ_GLOW, shading="fullbright"))
    else:
        can.add(Box("FizzDrip", (1, 3, 1), pos=(-3, 9, 6.5), paint=DEAD_GLASS))
    return Model([can]), {}


def sloshtank(fill: float):
    tank = Box("Tank", (24, 26, 24), pos=(0, 13.5, 0), paint=metal(TIN, rust=0.3, corrugated=True))
    tank.add(
        Box("BasePlate", (28, 3, 28), pos=(0, -12, 0), paint=DARK_TIN),
        Box("Top", (26, 3, 26), pos=(0, 13, 0), paint=RUSTY),
        Box("Bottom", (26, 3, 26), pos=(0, -11, 0), paint=DARK_TIN),
        Box("WaterGlass", (8, 19, 2), pos=(-3, -1, 10.4), paint=glass(FIZZ, fill)),
        Box("GlassFrameL", (1, 22, 2), pos=(-7.5, -1, 10.7), paint=BRASS_P),
        Box("GlassFrameR", (1, 22, 2), pos=(1.5, -1, 10.7), paint=BRASS_P),
        Box("GlassFrameTop", (9, 1, 1.8), pos=(-3, 10, 10.7), paint=RUSTY),
        Box("GlassFrameBottom", (9, 1, 1.8), pos=(-3, -10.8, 10.7), paint=RUSTY),
        Box("GaugeCase", (10, 10, 2), pos=(6.2, 7.7, 10.9), paint=BRASS_RIV),
        Box("GaugeFace", (7, 7, 1), pos=(6, 8, 11.8), paint=DEAD_GLASS),
        Box("GaugeNeedle", (1, 6, 1), pos=(6, 8, 12.7), rot=(0, 0, (0.5 - fill) * 90), paint=metal(RUST, rust=0.2)),
        Box("GaugeHub", (2, 2, 1), pos=(6, 8, 13.2), paint=BRASS_P),
        Box("Float", (3, 2, 1), pos=(-3, -9 + fill * 16, 11.6), paint=BRASS_P),
        Box("RopeHandle", (2, 8, 2), pos=(-11.5, 7, 0), paint=ROPE_P),
        lashing("RopeBand", (21, 2, 21), pos=(0, -6, 0), rot=(0, 0, -3)),
        bolt("RivetA", (-6.8, 10, 10), paint=BRASS_P),
        bolt("RivetB", (7, -10, 10), paint=BRASS_P),
        Box("Outlet", (5, 4, 5), pos=(0, -8, 12), rot=(25, 0, 0), paint=BRASS_RIV),
    )
    return Model([tank]), {}


def gutter_boiler(lit: bool, offline: bool = False):
    glow_paint = DEAD_GLASS if offline or not lit else FIZZ_GLOW
    body = Box("Boiler", (26, 21, 22), pos=(0, 11.5, 0), paint=metal(TIN_DARK, rust=0.45, corrugated=True))
    body.add(
        Box(
            "TopPlate",
            (28, 3, 24),
            pos=(0, 12 if not offline else 13, 0),
            rot=(0, 0 if not offline else -12, 0 if not offline else 15),
            paint=BRASS_RIV if not offline else SOOT_RUST,
        ),
        Box("BasePlate", (29, 3, 25), pos=(0, -10, 0), paint=SOOT_RUST),
        Box("PressureGlass", (9, 11, 2), pos=(0, 1, 11.4), paint=glass(FIZZ, 0.9 if lit else 0.2)),
        Box("GaugeFrame", (13, 14, 2), pos=(0, 1, 11.8), paint=BRASS_RIV),
        Box("GaugeNeedle", (1, 8, 1), pos=(0, 1, 13), rot=(0, 0, -25 if lit else -65), paint=glow_paint),
        Box("GaugeHub", (3, 3, 2), pos=(0, 1, 13.6), paint=BRASS_P),
        Box("ValveStem", (4, 6, 4), pos=(9, 7.4, -5), paint=BRASS_P).add(
            Box("ValveWheel", (10, 2, 10), pos=(0, 4, 0), rot=(0, 10, 0), paint=RUSTY),
        ),
        Box("Flue", (8, 6, 8), pos=(0, 15, 0), paint=SOOT_RUST),
        Box("FlueLip", (12, 2, 12), pos=(0, 18, 0), paint=BRASS_RIV),
        lashing("BoilerStrap", (2, 21, 21.5), pos=(11, -0.5, 0)),
    )
    if offline:
        body.add(
            Box("BlownLidHinge", (5, 2, 3), pos=(-8, 14, 8), rot=(0, 0, -24), paint=RUSTY),
            Box("SprungRivet", (3, 2, 1), pos=(11, 9, 11.8), rot=(0, 0, 24), paint=BRASS_P),
            Box("MissingRivetSocket", (3, 3, 1), pos=(-11, 10, 11.5), paint=SOOT_P),
            Box("SootBurst", (15, 8, 1), pos=(0, 10, 12), rot=(0, 0, -8), paint=SOOT_RUST),
            Box("BlackenedSeam", (20, 2, 1), pos=(0, 5, 12.2), rot=(0, 0, 4), paint=SOOT_P),
        )
    body.add(Box("Steam-Emitter", pos=(0, 20, 0)))
    return Model([body]), {}


def gasbag(fill: float):
    scale = 0.68 + fill * 0.38
    bag = Box("Gasbag")
    envelope = Box(
        "Envelope",
        stretch=(scale, 0.62 + fill * 0.52, scale),
    )
    bag.pos = (0, 10, 0)
    envelope.add(
        Box("GasbagBelly", (19, 16, 17), pos=(0, 14, 0), paint=leather((0.55, 0.43, 0.29)))
    )
    envelope.add(
        Box("BagMiddle", (23, 13, 20), pos=(0, 0, 0), paint=leather((0.51, 0.39, 0.26))),
        Box("Shoulder", (17, 6, 16), pos=(0, 8, 0), paint=leather((0.51, 0.39, 0.26))),
        Box("Crown", (10, 5, 11), pos=(0, 8, 0), paint=leather((0.58, 0.46, 0.32))),
        Box("TaperedBase", (14, 4, 14), pos=(0, -6, 0), paint=leather((0.52, 0.40, 0.28))),
        Box("CanvasPatch", (9, 7, 2), pos=(7, -1, 10.5), rot=(0, 0, 8), paint=solid((0.63, 0.48, 0.31))),
        Box("TinPatch", (6, 6, 1), pos=(-6, 5, 11), rot=(0, 0, -8), paint=metal(RUST, rust=0.2)),
        Box("FizzWindow", (6, 11, 1), pos=(-2, -1, 10.7), paint=glass(FIZZ, 0.72)),
        Box("WindowRim", (8, 13, 1), pos=(-2, -1, 11.2), paint=BRASS_RIV),
        Box("Valve", (5, 7, 5), pos=(0, -6, 0), paint=BRASS_RIV).add(
            Box("ValveWheel", (8, 2, 8), pos=(0, -3, 0), paint=RUSTY),
        ),
        Box("GaugeCase", (9, 9, 2), pos=(8, 7, 10.7), paint=BRASS_RIV),
        Box("GaugeFace", (6, 6, 1), pos=(8, 7, 11.8), paint=DEAD_GLASS),
        Box("GaugeNeedle", (1, 5, 1), pos=(8, 7, 12.6), rot=(0, 0, -15), paint=metal(RUST, rust=0.2)),
        Box("GaugeHub", (2, 2, 1), pos=(8, 7, 13), paint=BRASS_P),
        Box("BrassFrame", (20, 2, 18), pos=(0, 1, 0), paint=BRASS_RIV),
        Box("FrameLegL", (2, 8, 2), pos=(-8, -4, -7), paint=BRASS_P),
        Box("FrameLegR", (2, 8, 2), pos=(8, -4, -7), paint=BRASS_P),
        Box("FrameCrossbar", (17, 1.8, 1.8), pos=(0, -7, -7), paint=BRASS_P),
        lashing("NetBelt", (24, 2, 21), pos=(0, -4, 0), rot=(0, 0, 3)),
        lashing("NetRopeA", (2, 17, 2), pos=(-7, 0, 10), rot=(0, 0, 30)),
        lashing("NetRopeB", (2, 17, 2), pos=(7, 0, 10), rot=(0, 0, -30)),
        lashing("NetRopeC", (2, 17, 2), pos=(-7, 0, -10), rot=(0, 0, -30)),
        lashing("NetRopeD", (2, 17, 2), pos=(7, 0, -10), rot=(0, 0, 30)),
        bolt("PatchRivetA", (4, 3, 11.3), paint=BRASS_P),
        bolt("PatchRivetB", (10, -3, 11.3), paint=BRASS_P),
    )
    bag.add(envelope)
    anim = anim_json(
        40,
        {
            "GaugeNeedle": {
                "orientation": [
                    (0, (0, 0, -15)),
                    (10, (0, 0, -21)),
                    (20, (0, 0, -12)),
                    (30, (0, 0, -19)),
                    (40, (0, 0, -15)),
                ],
            },
        },
    )
    return Model([bag]), {"Twitch": anim}


def hurl_tube():
    tube = Box("Tube", pos=(0, 16, 0))
    tube.add(
        Box("Mount", (26, 4, 26), pos=(0, -14, 0), paint=CORRUGATED_RUST),
        Box("Barrel", (24, 12, 24), paint=CORRUGATED_RUST),
        Box("Muzzle", (26, 15, 3), pos=(0, 0, 13), paint=BRASS_RIV),
        Box("Breech", (26, 15, 3), pos=(0, 0, -13), paint=RUSTY),
        Box("Sight", (3, 4, 9), pos=(0, 8, 0), paint=BRASS_P),
        lashing("BreechRope", (16, 2, 16), pos=(0, -4, -9)),
        bolt("MuzzleRivetA", (-5, 5, 15.2), paint=BRASS_P),
        bolt("MuzzleRivetB", (5, -5, 15.2), paint=BRASS_P),
    )
    return Model([tube]), {}


def hurl_station():
    stand = Box("Station", pos=(0, 0, 0))
    stand.add(
        Box("Foot", (24, 4, 24), pos=(0, 2, 0), paint=CORRUGATED_RUST),
        Box("Column", (8, 18, 8), pos=(0, 12, 0), paint=DARK_TIN),
        Box("Cradle", (20, 5, 18), pos=(0, 22, 0), rot=(-12, 0, 0), paint=BRASS_RIV),
        Box("TubeSeat", (14, 8, 16), pos=(0, 27, 0), rot=(-12, 0, 0), paint=CORRUGATED),
        Box("LoadingHatch", (11, 8, 2), pos=(0, 27, 10), rot=(-12, 0, 0), paint=RUSTY),
        Box("Handle", (3, 13, 3), pos=(12, 15, 0), rot=(0, 0, -20), paint=BRASS_P),
        bolt("FootRivetA", (-8, 4, 8)),
        bolt("FootRivetB", (8, 4, -8)),
    )
    return Model([stand]), {}


def hurl_cannon():
    cannon = Box("Cannon", pos=(0, 0, 0))
    cannon.add(
        Box("Carriage", (26, 8, 22), pos=(0, 4, 0), paint=CORRUGATED_RUST),
        Box("Barrel", (18, 16, 24), pos=(0, 18, 1), rot=(-8, 0, 0), paint=metal(TIN_DARK, rust=0.35, corrugated=True)),
        Box("Muzzle", (22, 20, 4), pos=(0, 17, 13.5), rot=(-8, 0, 0), paint=BRASS_RIV),
        Box("Breech", (21, 18, 4), pos=(0, 20, -12), paint=RUSTY),
        Box("ChargePiston", (4, 8, 4), pos=(0, 29, -1), paint=BRASS_P),
        Box("Gauge", (7, 7, 2), pos=(13, 24, 9), paint=glass(FIZZ, 0.8)),
        bolt("CarriageBoltL", (-10, 6, 5)),
        bolt("CarriageBoltR", (10, 6, 5)),
    )
    anim = anim_json(
        36,
        {
            "ChargePiston": {
                "position": [(0, (0, 0, 0)), (18, (0, 3, 0)), (36, (0, 0, 0))],
            },
            "Gauge": {
                "orientation": [(0, (0, 0, -20)), (18, (0, 0, 35)), (36, (0, 0, -20))],
            },
        },
    )
    return Model([cannon]), {"Charge": anim}


def catch_net():
    frame = Box("NetFrame", pos=(0, 12, 0))
    frame.add(
        Box("FrameTop", (28, 3, 24), pos=(0, 12, 0), paint=BRASS_RIV),
        Box("FrameLegL", (3, 24, 3), pos=(-12, 0, 0), paint=RUSTY),
        Box("FrameLegR", (3, 24, 3), pos=(12, 0, 0), paint=RUSTY),
        Box("NetMesh", (22, 1, 18), pos=(0, 7, 0), paint=rope()),
        lashing("NetRopeL", (2, 18, 2), pos=(-8, 0, 0), rot=(0, 0, -15)),
        lashing("NetRopeR", (2, 18, 2), pos=(8, 0, 0), rot=(0, 0, 15)),
        bolt("FrameRivetL", (-12, 10, 2)),
        bolt("FrameRivetR", (12, 10, 2)),
    )
    anim = anim_json(
        24,
        {
            "NetMesh": {
                "shapeStretch": [(0, (1, 1, 1)), (12, (1.1, 0.8, 1.1)), (24, (1, 1, 1))],
            },
        },
    )
    return Model([frame]), {"Land": anim}


def well_cradle(state: str):
    cradle = Box("Cradle", pos=(0, 0, 0))
    cradle.add(
        Box("Base", (26, 5, 26), pos=(0, 2.5, 0), paint=CORRUGATED_RUST),
        Box("Rim", (22, 4, 22), pos=(0, 7, 0), paint=BRASS_RIV),
        Box("SupportA", (3, 15, 3), pos=(-9, 14, -9), paint=RUSTY),
        Box("SupportB", (3, 15, 3), pos=(9, 14, -9), paint=RUSTY),
        Box("SupportC", (3, 15, 3), pos=(-9, 14, 9), paint=RUSTY),
        Box("SupportD", (3, 15, 3), pos=(9, 14, 9), paint=RUSTY),
        Box("Seat", (16, 3, 16), pos=(0, 14, 0), paint=BRASS_P),
    )
    if state in ("Seated", "Harmony"):
        cradle.add(
            Box("GlimmerCrystal", (7, 11, 7), pos=(0, 23, 0), rot=(10, 20, -8), paint=CYAN_GLOW, shading="fullbright"),
        )
    if state == "Harmony":
        cradle.add(
            Box("HarmonyRing", (23, 1, 23), pos=(0, 18, 0), paint=CYAN_GLOW, shading="fullbright"),
        )
    return Model([cradle]), {}


def crackle_coil(active: bool):
    tower = Box("CoilTower", pos=(0, 0, 0))
    tower.add(
        Box("Foot", (24, 4, 24), pos=(0, 2, 0), paint=CORRUGATED_RUST),
        Box("Mast", (6, 22, 6), pos=(0, 15, 0), paint=metal(TIN_DARK, rust=0.35)),
        Box("CopperCoil", (15, 4, 15), pos=(0, 10, 0), paint=BRASS_RIV),
        Box("CopperCoilTop", (15, 4, 15), pos=(0, 20, 0), paint=BRASS_RIV),
        Box("Cage", (18, 2, 18), pos=(0, 27, 0), paint=RUSTY),
    )
    arc = Box("Arc", (1, 21, 1), pos=(0, 22, 0), paint=CYAN_GLOW, shading="fullbright", visible=active)
    arc.add(
        Box("ArcBranch", (1, 13, 1), pos=(4, 0, 0.2), rot=(0, 0, 38), paint=CYAN_GLOW, shading="fullbright"),
        Box("ArcTip", (1, 8, 1), pos=(-3, -5, -0.2), rot=(0, 0, -42), paint=CYAN_GLOW, shading="fullbright"),
    )
    tower.add(arc)
    if active:
        animation = anim_json(
            24,
            {
                "Arc": {
                    "orientation": [(0, (0, 0, -8)), (12, (0, 0, 8)), (24, (0, 0, -8))],
                },
            },
        )
        return Model([tower]), {"Arc": animation}
    return Model([tower]), {}


def resonator():
    frame = Box("Resonator", pos=(0, 0, 0))
    frame.add(
        Box("Base", (24, 4, 24), pos=(0, 2, 0), paint=CORRUGATED_RUST),
        Box("FrameLeft", (4, 24, 4), pos=(-9, 15, 0), paint=DARK_TIN),
        Box("FrameRight", (4, 24, 4), pos=(9, 15, 0), paint=DARK_TIN),
        Box("TopBar", (24, 4, 5), pos=(0, 27, 0), paint=BRASS_RIV),
        Box("BottomBar", (21.8, 3, 5), pos=(0, 7, 0), paint=RUSTY),
        Box("Crystal", (9, 14, 7), pos=(0, 17, 0), rot=(5, 15, -8), paint=CYAN_GLOW, shading="fullbright"),
        bolt("FrameRivetL", (-9, 25, 3)),
        bolt("FrameRivetR", (9, 25, 3)),
    )
    return Model([frame]), {}


# ------------------------------------------------------------------ Gutterpipe


def _gutterpipe_axis_box(name, size, pos, axis, paint):
    if axis == 0:
        size, pos = (size[2], size[1], size[0]), (pos[2], pos[1], pos[0])
    elif axis == 1:
        size, pos = (size[0], size[2], size[1]), (pos[0], pos[2], pos[1])
    return Box(name, size, pos=pos, paint=paint)


_GUTTERPIPE_METAL = metal(TIN_DARK, rust=0.45, corrugated=True)
_GUTTERPIPE_CAP = metal(RUST, rust=0.3, rivets=True)


def _gutterpipe_tube(axis, ends, name):
    start, end = ends
    size = (10, 10, end - start)
    position = (0, 0, 16 + (start + end) / 2) if axis == 1 else (0, 16, (start + end) / 2)
    return _gutterpipe_axis_box(name, size, position, axis, _GUTTERPIPE_METAL)


def _gutterpipe_cap(axis, sign):
    direction = ("X", "Y", "Z")[axis]
    name = f"Cap_{'Pos' if sign > 0 else 'Neg'}{direction}"
    position = (0, 0, 16 + sign * 13) if axis == 1 else (0, 16, sign * 13)
    return _gutterpipe_axis_box(name, (12, 12, 2), position, axis, _GUTTERPIPE_CAP)


def _gutterpipe_arm(bit):
    axis = bit // 2
    sign = 1 if bit % 2 == 0 else -1
    direction = ("X", "Y", "Z")[axis]
    return _gutterpipe_axis_box(
        f"Arm_{'Pos' if sign > 0 else 'Neg'}{direction}",
        (10, 10, 10),
        (0, 0, 16 + sign * 11) if axis == 1 else (0, 16, sign * 11),
        axis,
        _GUTTERPIPE_METAL,
    )


def _gutterpipe_straight_parts(axis, ends, kind):
    direction = ("X", "Y", "Z")[axis]
    tube = _gutterpipe_tube(axis, ends, f"Tube_{direction}_{kind}")
    caps = []
    for sign, end in ((-1, ends[0]), (1, ends[1])):
        if end == sign * 12:
            caps.append(_gutterpipe_cap(axis, sign))
    return [tube, *caps]


def gutterpipe(mask: int):
    """Build a Gutterpipe for the world mask bits +X,-X,+Y,-Y,+Z,-Z."""
    if not 0 <= mask < 64:
        raise ValueError("Gutterpipe mask must be six bits")
    arms = [bit for bit in range(6) if mask & (1 << bit)]
    straight_axis = None
    if not arms:
        straight_axis = 0
    elif len(arms) == 1:
        straight_axis = arms[0] // 2
    elif len(arms) == 2 and arms[0] // 2 == arms[1] // 2:
        straight_axis = arms[0] // 2

    root = Box("Gutterpipe")
    if straight_axis is not None:
        axis_arms = [bit for bit in arms if bit // 2 == straight_axis]
        if len(axis_arms) == 2:
            root.add(*_gutterpipe_straight_parts(straight_axis, (-16, 16), "Through"))
        elif len(axis_arms) == 1:
            bit = axis_arms[0]
            ends = (-12, 16) if bit % 2 == 0 else (-16, 12)
            root.add(*_gutterpipe_straight_parts(straight_axis, ends, "OnePositive" if bit % 2 == 0 else "OneNegative"))
        else:
            root.add(*_gutterpipe_straight_parts(straight_axis, (-12, 12), "ClosedBoth"))
    else:
        root.add(Box("Hub", (12, 12, 12), pos=(0, 16, 0), paint=_GUTTERPIPE_METAL))
        root.add(*(_gutterpipe_arm(bit) for bit in arms))
    return Model([root]), {}


def gutterpipe_master():
    root = Box("GutterpipeMaster")
    for axis in range(3):
        root.add(*_gutterpipe_straight_parts(axis, (-12, 12), "ClosedBoth"))
        root.add(*_gutterpipe_straight_parts(axis, (-12, 16), "OnePositive"))
        root.add(*_gutterpipe_straight_parts(axis, (-16, 12), "OneNegative"))
        root.add(*_gutterpipe_straight_parts(axis, (-16, 16), "Through"))
    root.add(Box("Hub", (12, 12, 12), pos=(0, 16, 0), paint=_GUTTERPIPE_METAL))
    root.add(*(_gutterpipe_arm(bit) for bit in range(6)))
    return Model([root])


def snatcher_claw():
    housing = Box("Housing", (22, 18, 18), pos=(0, 9, -2), paint=metal(BRASS, rust=0.25, rivets=True))
    housing.add(
        Box("TopPlate", (18, 2, 15), pos=(0, 10, 0), paint=BRASS_RIV),
        Box("SideRailL", (2, 14, 16), pos=(-12, 0, -1), paint=RUSTY),
        Box("SideRailR", (2, 14, 16), pos=(12, 0, -1), paint=BRASS_P),
        Box("GlimmerEye", (5, 5, 2), pos=(0, 1, 10), paint=CYAN_GLOW, shading="fullbright"),
        bolt("HousingRivetL", (-8, 5, 7), s=2, paint=BRASS_P),
        bolt("HousingRivetR", (8, 5, 7), s=2, paint=BRASS_P),
    )
    palm = Box("Palm", pos=(0, 8, 10))
    palm.add(
        Box("PalmPlate", (12, 8, 4), pos=(0, 0, 0), paint=metal(TIN_DARK, rust=0.3, rivets=True)),
        Box("Knuckle", (14, 4, 5), pos=(0, 5, 1), paint=BRASS_RIV),
    )
    jaw_l = Box("GrabLeft", pos=(-5, 10, 11))
    jaw_l.add(
        Box("LeftFinger", (4, 11, 4), pos=(-1, -4, 3), rot=(-10, 0, -10), paint=metal(TIN_DARK, rust=0.4)),
        Box("LeftHook", (4, 5, 4), pos=(1, -10, 5), rot=(0, 0, 35), paint=RUSTY),
    )
    jaw_r = Box("GrabRight", pos=(5, 10, 11))
    jaw_r.add(
        Box("RightFinger", (4, 11, 4), pos=(1, -4, 3), rot=(-10, 0, 10), paint=metal(TIN_DARK, rust=0.4)),
        Box("RightHook", (4, 5, 4), pos=(-1, -10, 5), rot=(0, 0, -35), paint=BRASS_RIV),
    )
    housing.add(palm, jaw_l, jaw_r)
    animation = anim_json(
        16,
        {
            "GrabLeft": {
                "orientation": [(0, (0, 0, -14)), (6, (0, 0, 9)), (10, (0, 0, 9)), (16, (0, 0, -14))],
            },
            "GrabRight": {
                "orientation": [(0, (0, 0, 14)), (6, (0, 0, -9)), (10, (0, 0, -9)), (16, (0, 0, 14))],
            },
        },
    )
    return Model([housing]), {"Grab": animation}


# ------------------------------------------------------------------ Gnasher Pit


def teeth(prefix, count, flip):
    out = []
    for i in range(count):
        out.append(Box(f"{prefix}Tooth{i}", (2, 3, 2), pos=(-6 + i * 4 + random.choice((0, 0.5)), -2 * flip, 0),
                       rot=(0, 0, random.choice((-8, 0, 6))), paint=metal((0.78, 0.78, 0.72), rust=0.15, rivets=False)))
    return out


def gnasher_pit():
    random.seed(3)
    crate = Box("Crate", (24, 14, 22), pos=(0, 7, 0), paint=CORRUGATED_RUST)
    crate.add(
        Box("CrateRim", (26, 2, 24), pos=(0, 7, 0), rot=(0, 0, -1.5), paint=RUSTY),
        Box("Plank", (26, 3, 1), pos=(0, -2, 11.3), rot=(0, 0, 3), paint=WOOD_P),
        Box("Chute", (6, 4, 6), pos=(6, -3, 13), rot=(30, 0, 0), paint=DARK_TIN),
        Box("GritHeap", (5, 2, 4), pos=(6, -6, 13), paint=solid((0.45, 0.42, 0.38), noise=0.08)),
        Box("FizzLamp", (3, 3, 3), pos=(-10, 9, -9), paint=FIZZ_GLOW, shading="fullbright"),
        Box("LampPost", (1, 4, 1), pos=(-10, 6.5, -9), paint=DARK_TIN),
        lashing("RopeCorner", (2, 14, 2), pos=(-12, 1, 12), rot=(0, 0, 5)),
        Box("Hopper", (20, 8, 6), pos=(0, 11, -9), rot=(-18, 0, 2), paint=metal(TIN, rust=0.4)),
    )
    jaw_l = Box("JawL", pos=(0, 8.5, -4))  # animated: hinge at the back, chomps around X
    jaw_l.add(Box("JawLPlate", (19, 2.8, 11), offset=(-0.5, 0, 5.5), paint=SOOT_RUST).add(*teeth("JawL", 5, 1)))
    jaw_r = Box("JawR", pos=(0, 8.5, 6))
    jaw_r.add(Box("JawRPlate", (19, 3, 4), pos=(0.5, 0, 0), offset=(0, 0, 1), paint=DARK_TIN))
    crate.add(jaw_l, jaw_r)
    crate.add(
        Box("GearL", (2, 10, 10), pos=(-12.5, 2, 0), rot=(30, 0, 0), paint=BRASS_RIV),
        Box("GearR", (2, 8, 8), pos=(12.5, 1, -2), rot=(10, 0, 0), paint=metal(RUST, rust=0.2)),
        Box("Spark-Emitter", pos=(0, 16, 0)),
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
    oven = Box("Oven", (24, 22, 22), pos=(0, 11, 0), paint=SOOT_RUST)
    oven.add(
        Box("Door", (12, 10, 2), pos=(-2, -1, 11.2), rot=(0, 0, -3), paint=BRASS_RIV).add(
            Box("DoorSlot", (8, 2, 1), pos=(0, 2, 1), paint=EMBER_GLOW if lit else DEAD_EMBER,
                shading="fullbright" if lit else "standard"),
            Box("DoorLatch", (2, 3, 2), pos=(5.2, -1, 1.2), paint=DARK_TIN),
        ),
        Box("Ashpan", (14, 3, 3), pos=(-2, -8.8, 11), paint=DARK_TIN),
        Box("RoofSheet", (26, 2, 24), pos=(0, 11.5, 0), rot=(0, 0, 3), paint=CORRUGATED_RUST),
        Box("SidePatchL", (1, 10, 12), pos=(-12.4, 2, 1), rot=(5, 0, 0), paint=CORRUGATED),
        Box("SidePatchR", (1, 8, 8), pos=(12.4, -4, -3), rot=(-4, 0, 0), paint=metal(RUST, rust=0.3)),
        Box("Thermo", (3, 6, 1), pos=(8, 4, 11.3), paint=glass(EMBER if lit else (0.3, 0.3, 0.3), 0.7 if lit else 0.2)),
        lashing("RopeBand", (25, 2, 23), pos=(0, 6, 0), rot=(0, 0, -2)),
        Box("Chimney", (6, 14, 6), pos=(0, 18, 0), paint=SOOT_P).add(
            Box("ChimneyCap", (9, 2, 9), pos=(0, 7.5, 0), rot=(0, 15, 6), paint=RUSTY),
        ),
        Box("Smoke-Emitter", pos=(0, 27, 0)),
        Box("Foot0", (4, 2, 4), pos=(-9, -9.5, -8), paint=DARK_TIN),
        Box("Foot1", (4, 3, 4), pos=(9, -8.5, -8), paint=DARK_TIN),
        Box("Foot2", (4, 2, 4), pos=(-9, -9.5, 8), paint=DARK_TIN),
        Box("Foot3", (4, 2, 4), pos=(9, -9.5, 8), paint=DARK_TIN),
    )
    return Model([oven]), {}


# ------------------------------------------------------------------ Wakebell Anchor


def wakebell_anchor(lit: bool):
    base = Box("Base", (28, 6, 28), pos=(0, 3, 0), paint=CORRUGATED_RUST)
    post = Box("Post", (4, 24, 4), pos=(-5, 15, -3), rot=(0, 0, -3), paint=plank(WOOD, horizontal=False))
    arm = Box("Arm", (14, 3, 3), pos=(5, 10, 0), rot=(0, 0, -5), paint=DARK_TIN)
    swing = Box("BellPivot", pos=(4, -1.5, 0))  # animated: sways around Z/X
    bell = Box("Bell", (8, 8, 8), pos=(0, -6, 0), paint=metal(BRASS, rust=0.15, rivets=True, bolt=TIN))
    bell.add(
        Box("BellLip", (10, 2, 10), pos=(0, -4.5, 0), paint=brass()),
        Box("BellCrown", (4, 2, 4), pos=(0, 5, 0), paint=BRASS_P),
        Box("Clapper", (2, 3, 1.8), pos=(0, -6, 0), paint=CYAN_GLOW if lit else DARK_TIN,
            shading="fullbright" if lit else "standard"),
    )
    swing.add(lashing("BellRope", (1, 3, 1), pos=(0, -1, 0)), bell)
    arm.add(swing, lashing("ArmLash", (4, 4, 3.6), pos=(-6, 0, 0), rot=(0, 0, 10)))
    post.add(arm, Box("Lamp", (3.8, 4, 3.8), pos=(0, 13, 0), paint=CYAN_GLOW if lit else DEAD_GLASS,
                      shading="fullbright" if lit else "standard"),
             Box("LampHat", (6, 2, 6), pos=(0, 15.5, 0), rot=(0, 30, 4), paint=RUSTY),
             Box("Brace", (2, 10, 2), pos=(4, -7, 0), rot=(0, 0, -35), paint=DARK_TIN))
    base.add(post,
             Box("Anchor", (10, 3, 4), pos=(4, 4, 5), rot=(0, -20, 0), paint=metal(TIN_DARK, rust=0.5)),
             Box("AnchorFluke", (3, 5, 3), pos=(8, 5, 6), rot=(0, -20, 25), paint=metal(TIN_DARK, rust=0.5)),
             lashing("BaseRope", (27, 2, 27), pos=(0, 1, 0)),
             Box("GlimmerShard", (3, 5, 3), pos=(-5, 5, 6), rot=(10, 30, 15), paint=CYAN_GLOW if lit else DEAD_GLASS,
                 shading="fullbright" if lit else "standard"))
    anims = {"Sway": anim_json(90, {"BellPivot": {"orientation": [
        (0, (0, 0, 0)), (22, (6, 0, 12)), (45, (0, 0, 0)), (67, (-6, 0, -12)), (90, (0, 0, 0))]}})}
    return Model([base]), anims


def wellhead(pumping: bool):
    tank = Box(
        "PressureTank",
        (18, 15 if pumping else 19, 18),
        pos=(0, 14, 0),
        paint=CORRUGATED_RUST,
    )
    tank.add(
        Box("TankTop", (20, 2, 20), pos=(0, 9.8, 0), paint=BRASS_RIV),
        Box("TankBase", (20, 2, 20), pos=(0, -9, 0), paint=DARK_TIN),
        Box("Gauge", (7, 8, 2), pos=(0, 1, 10), paint=glass(FIZZ, 0.9 if pumping else 0.2)),
        Box("GaugeFrame", (9, 10, 2), pos=(0, 1, 10.8), paint=BRASS_P),
        Box("GaugeNeedle", (1, 5, 1), pos=(0, 1, 12), rot=(0, 0, -24 if pumping else -70),
            paint=FIZZ_GLOW if pumping else DEAD_GLASS),
        Box("GaugeHub", (2, 2, 1), pos=(0, 1, 12.5), paint=BRASS_P),
        Box("FeedPipe", (4, 12, 4), pos=(6.8, 12, -5), paint=DARK_TIN),
        Box("FeedElbow", (3.8, 4, 7.8), pos=(7, 18, -7), paint=BRASS_P),
        Box("OutputPipe", (4, 9, 4), pos=(-6.8, 12, 4), paint=DARK_TIN),
        Box("OutputCap", (6, 2, 6), pos=(-7, 17, 4), paint=BRASS_RIV),
        Box(
            "OutputPortGlow",
            (4, 2, 4),
            pos=(-7, 18.5, 4),
            paint=FIZZ_GLOW if pumping else DEAD_GLASS,
            shading="fullbright" if pumping else "standard",
        ),
        Box("WellSocket", (12, 3, 12), pos=(0, -11, 0), paint=BRASS_P),
        Box("SocketGlow", (8, 1, 8), pos=(0, -13.5, 0), paint=CYAN_GLOW if pumping else DEAD_GLASS,
            shading="fullbright" if pumping else "standard"),
        lashing("Strap", (2, 17, 19.8), pos=(9.2, -0.2, 0)),
        bolt("BoltA", (-7, 8, 9), paint=BRASS_P),
        bolt("BoltB", (7, -7, 9), paint=BRASS_P),
    )
    bellows = Box(
        "Bellows",
        (10, 7 if pumping else 10, 10),
        pos=(7, 5 if pumping else 6.5, -4.8),
        paint=CORRUGATED_RUST,
    )
    bellows.add(
        Box("BellowsRidgeA", (12, 1, 12), pos=(0, -2, 0), paint=RUSTY),
        Box("BellowsRidgeB", (12, 1, 12), pos=(0, 2.2, 0), paint=RUSTY),
    )
    tank.add(bellows)
    wheel = Box("PumpWheel", pos=(8.8, 4.2, -5))
    wheel.add(
        Box("WheelHub", (4, 4, 4), pos=(0, 0, 0.2), paint=BRASS_P),
        Box("WheelSpokeA", (2, 14, 2), paint=RUSTY),
        Box("WheelSpokeB", (13.8, 2, 1.8), paint=RUSTY),
        Box("WheelRim", (14, 1, 14), paint=BRASS_RIV),
    )
    tank.add(wheel)
    return Model([tank]), {}


def glimmerwell_anchor(corrupted: bool):
    glow_paint = solid((0.55, 0.18, 0.72)) if corrupted else CYAN_GLOW
    base_paint = metal((0.30, 0.28, 0.31) if corrupted else TIN_DARK, rust=0.55)
    base = Box("AnchorBase", (26, 4, 26), pos=(0, 2, 0), paint=base_paint)
    base.add(
        Box("RuneRing", (13, 2, 13), pos=(0, 5, 0), paint=BRASS_P),
        Box("WellCrystal", (5, 12, 5), pos=(0, 11, 0), rot=(8, 18, -8), paint=glow_paint,
            shading="fullbright"),
        Box("ShardA", (3, 7, 3), pos=(-6, 7, 2), rot=(8, 20, 18), paint=glow_paint,
            shading="fullbright"),
        Box("ShardB", (3, 7, 3), pos=(6, 7, -2), rot=(-10, -18, -15), paint=glow_paint,
            shading="fullbright"),
        bolt("AnchorBoltA", (-6, 4, 6.2), paint=BRASS_P),
        bolt("AnchorBoltB", (6, 4, -6.2), paint=BRASS_P),
    )
    return Model([base]), {}


# ------------------------------------------------------------------ Junk Heart pieces (PR #9)


def junk_heart_firebox(lit: bool):
    fb = Box("Firebox", (30, 26, 30), pos=(0, 13, 0), paint=SOOT_RUST)
    fb.add(
        Box("Mouth", (16, 10, 1), pos=(0, -3, 15.2), paint=EMBER_GLOW if lit else DEAD_EMBER,
            shading="fullbright" if lit else "standard"),
        Box("MouthHood", (20, 3, 4), pos=(0, 3.5, 14.7), rot=(-10, 0, -2), paint=RUSTY),
        Box("GrateBar0", (1, 11, 1), pos=(-5, -3, 16), paint=DARK_TIN),
        Box("GrateBar1", (1, 11, 1), pos=(0, -3, 16), paint=DARK_TIN),
        Box("GrateBar2", (1, 11, 1), pos=(5, -3, 16), paint=DARK_TIN),
        Box("FizzCoil", (4, 12, 4), pos=(-11, 4, 12.5), paint=FIZZ_GLOW, shading="fullbright"),
        Box("CoilCage", (6, 2, 6), pos=(-11, 11, 12.5), paint=BRASS_P),
        Box("PlateL", (1, 18, 20), pos=(-15.3, 1, -2), rot=(3, 0, 0), paint=CORRUGATED),
        Box("PlateR", (1, 14, 14), pos=(15.3, -3, 4), rot=(-2, 0, 0), paint=metal(RUST, rust=0.3)),
        Box("Top", (32, 2, 32), pos=(0, 13, 0), rot=(0, 0, 1), paint=RUSTY),
        Box("Collar", (12, 3, 12), pos=(0, 15, 0), paint=DARK_TIN),
        lashing("RopeBand", (31, 2, 31), pos=(0, 7, 0), rot=(0, 0, -1.5)),
        Box("Gauge", (5, 5, 1), pos=(10, 6, 15.3), paint=brass(rivets=False)),
        Box("Smoke-Emitter", pos=(0, 15, 0)),
    )
    return Model([fb]), {}


def rattlewheel(on: bool):
    base = Box("WheelBase", (26, 4, 26), pos=(0, 2, 0), paint=CORRUGATED_RUST)
    base.add(
        Box("BearingPost", (3.8, 13.8, 3.8), pos=(0, 10, 0), paint=DARK_TIN),
        Box("BraceL", (3, 12, 2.8), pos=(-7, 8, 0), rot=(0, 0, -18), paint=plank()),
        Box("BraceR", (3, 12, 2.6), pos=(7, 8, 0), rot=(0, 0, 18), paint=plank(WOOD, horizontal=False)),
        bolt("BearingBoltL", (-3.2, 15, 2.2), s=2),
        bolt("BearingBoltR", (3.2, 15, 2.2), s=2, paint=RUSTY),
    )
    wheel = Box("WheelPivot", pos=(0, 17, 0))
    wheel.add(
        Box("Axle", (4, 4, 12), paint=DARK_TIN),
        Box("RimA", (28, 3, 3), pos=(0, 0, 0), rot=(0, 0, 45), paint=metal(TIN, rust=0.25)),
        Box("RimB", (28, 3, 2.8), pos=(0, 0, 0), rot=(0, 0, -45), paint=CORRUGATED),
        Box("PaddleA", (7, 2, 11), pos=(0, 10, 0), rot=(0, 0, 3), paint=plank()),
        Box("PaddleB", (7, 2, 11), pos=(10, 0, 0), rot=(0, 0, 93), paint=CORRUGATED_RUST),
        Box("PaddleC", (7, 2, 11), pos=(0, -10, 0), rot=(0, 0, 183), paint=plank(WOOD, horizontal=False)),
        Box("PaddleD", (7, 2, 11), pos=(-10, 0, 0), rot=(0, 0, 273), paint=metal(RUST, rust=0.3)),
        Box("Hub", (8, 8, 5), paint=FIZZ_GLOW if on else BRASS_P,
            shading="fullbright" if on else "standard"),
        bolt("HubCap", (0, 0, 3), s=3.8, paint=RUSTY if on else DARK_TIN),
        Box("Spark-Emitter", pos=(0, 12, 0)),
    )
    return Model([base, wheel]), {}


def shroom_bed():
    mud = Box("MudPlot", (28, 4, 24), pos=(0, 2, 0), paint=metal(RUST, rust=0.4))
    mud.add(
        Box("Soil", (23, 2, 19), pos=(0, 3, 0), paint=SOOT_RUST),
        Box("CanopyPostA", (2, 24, 2), pos=(-11, 14, -9), rot=(0, 0, 2), paint=plank()),
        Box("CanopyPostB", (2, 24, 2), pos=(11, 14, -9), rot=(0, 0, -3), paint=plank(WOOD, horizontal=False)),
        Box("CanopyPostC", (2, 23, 2), pos=(-11, 14, 9), paint=plank(WOOD, horizontal=False)),
        Box("CanopyPostD", (2, 24, 2), pos=(11, 14, 9), rot=(0, 0, 2), paint=plank()),
        Box("Canopy", (28, 3, 26), pos=(0, 26, 0), paint=CORRUGATED),
        Box("CanopyPatch", (12, 1, 10), pos=(-5, 28, 2), rot=(0, 3, -2), paint=RUSTY),
        lashing("FrontLash", (31, 1, 1), pos=(0, 24, 12), rot=(0, 0, -1)),
        Box("StemA", (3, 7, 3), pos=(-6, 8, -3), paint=DARK_TIN),
        Box("CapA", (9, 3, 8), pos=(-6, 13, -3), rot=(0, 0, 2), paint=EMBER_GLOW),
        Box("StemB", (3, 5, 3), pos=(5, 7, 4), paint=DARK_TIN),
        Box("CapB", (8, 3, 7), pos=(5, 11, 4), rot=(0, 0, -3), paint=FIZZ_GLOW),
        Box("StemC", (2, 4, 2), pos=(0, 6, -8), paint=DARK_TIN),
        Box("CapC", (6, 2, 6), pos=(0, 9, -8), paint=BRASS_P),
        Box("Spore-Emitter", pos=(0, 28, 0)),
    )
    return Model([mud]), {}


def scrap_plating():
    core = Box("Core", (30, 30, 30), pos=(0, 16, 0), paint=DARK_TIN)
    core.add(
        Box("SheetN", (31, 31, 1), pos=(0.5, 0, -15.6), rot=(0, 0, 1), paint=CORRUGATED),
        Box("SheetS", (31, 31, 1), pos=(-0.5, 0, 15.6), rot=(0, 0, -1), paint=CORRUGATED_RUST),
        Box("SheetW", (1, 31, 31), pos=(-15.6, 0, 0), paint=metal(TIN, rust=0.5)),
        Box("SheetE", (1, 31, 30.5), pos=(15.6, 0, 0.5), paint=metal(RUST, rust=0.2, corrugated=True)),
        Box("SheetTop", (32, 1, 32), pos=(0, 15.6, 0), rot=(0, 1.5, 0), paint=metal(TIN_DARK, rust=0.5)),
        Box("SheetBottom", (32, 1, 32), pos=(0, -15.5, 0), paint=DARK_TIN),
        Box("PatchS", (12, 9, 1), pos=(6, 6, 16.3), rot=(0, 0, 8), paint=brass(rivets=True)),
        Box("PatchE", (1, 10, 8), pos=(16.3, -7, -7), rot=(6, 0, 0), paint=metal(TIN, rust=0.3)),
        Box("StrapS", (32, 3, 1), pos=(0, -6, 16.4), rot=(0, 0, -4), paint=ROPE_P),
    )
    return Model([core]), {}


def chimney(cap: bool):
    pipe = Box("Flue", (14, 32, 14), pos=(0, 16, 0), paint=SOOT_RUST)
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
    base = Box("Foot", (24, 3, 24), pos=(0, 1.5, 0), paint=DARK_TIN)
    mast = Box("Mast", (3, 27, 3), pos=(0, 16, 0), paint=RUSTY)
    mast.add(
        Box("Crossbar", (22, 3, 2.8), pos=(0, 7, 0), rot=(0, 0, -5), paint=BRASS_P),
        Box("Vane", (3, 12, 1), pos=(0, 7, 2.2), rot=(0, 0, -12), paint=FIZZ_GLOW if on else CORRUGATED),
        Box("CopperWire", (1, 17, 1), pos=(2, -4, 2), rot=(0, 0, 8), paint=BRASS_P),
        Box("Spark", (4, 4, 2), pos=(0, 11, 3), paint=CYAN_GLOW if on else DEAD_GLASS,
            shading="fullbright" if on else "standard"),
        Box("Spark-Emitter", pos=(0, 14, 0)),
    )
    base.add(mast)
    return Model([base]), {}


def fizz_damper(on: bool):
    body = Box("Canister", (24, 17, 22), pos=(0, 9, 0), paint=CORRUGATED_RUST)
    body.add(
        Box("Footplate", (26, 4, 26), pos=(0, -7, 0), paint=DARK_TIN),
        Box("Rim", (20, 2, 16), pos=(0, 8, 0), paint=BRASS_RIV),
        Box("Window", (8, 8, 1), pos=(0, 0, 7.5), paint=FIZZ_GLOW if on else DEAD_GLASS,
            shading="fullbright" if on else "standard"),
        Box("Nozzle", (6, 4, 6), pos=(0, 13, 0), paint=DARK_TIN),
        Box("Foam", (10, 5, 10), pos=(0, 17, 0), paint=FIZZ_GLOW if on else DEAD_GLASS,
            shading="fullbright" if on else "standard"),
        lashing("Strap", (20, 2, 2), pos=(0, 2, 9.5)),
        bolt("BoltA", (-7, 0, 8), 2),
        bolt("BoltB", (7, 0, 8), 2),
        Box("Steam-Emitter", pos=(0, 20, 0)),
    )
    return Model([body]), {}


def tether_stake():
    post = Box("Post", (5, 25, 5), pos=(0, 15.5, 0), paint=RUSTY)
    post.add(
        Box("Spike", (3, 6, 3), pos=(0, -12.5, 0), paint=DARK_TIN),
        Box("Clamp", (10, 4, 10), pos=(0, 4, 0), paint=BRASS_RIV),
        Box("Coil", (7, 3, 7), pos=(0, 10, 0), rot=(0, 0, 8), paint=CYAN_GLOW, shading="fullbright"),
        Box("Wire", (2, 16, 2), pos=(4, -1, 0), rot=(0, 0, -8), paint=BRASS_P),
        bolt("Bolt", (0, 4, 3), 2),
    )
    return Model([post]), {}


def boiler_tank():
    tank = Box("Tank", (24, 25, 22), pos=(0, 13, 0), paint=CORRUGATED_RUST)
    tank.add(
        Box("Top", (26, 2, 24), pos=(0, 13, 0), paint=BRASS_RIV),
        Box("Bottom", (25, 2, 23), pos=(0, -12, 0), paint=DARK_TIN),
        Box("GaugeFrame", (10, 10, 2), pos=(0, 1, 12), paint=BRASS_RIV),
        Box("GaugeGlass", (7, 7, 1), pos=(0, 1, 13.5), paint=CYAN_GLOW, shading="fullbright"),
        Box("GaugeNeedle", (1, 5, 1), pos=(0, 1, 14.5), rot=(0, 0, -20), paint=BRASS_P),
        Box("SideValve", (4, 4, 4), pos=(13, 3, 0), paint=BRASS_RIV),
        bolt("RivetA", (-8, 4, 11), 2),
        bolt("RivetB", (8, 4, 11), 2),
        bolt("RivetC", (-8, -7, 11), 2),
        bolt("RivetD", (8, -7, 11), 2),
    )
    return Model([tank]), {}


def boiler_whistle_stack():
    stack = Box("Base", (26, 5, 26), pos=(0, 2.5, 0), paint=BRASS_RIV)
    stack.add(
        Box("Pipe", (10, 18, 10), pos=(0, 14, 0), paint=BRASS_P),
        Box("Rim", (16, 3, 16), pos=(0, 23, 0), paint=BRASS_RIV),
        Box("Whistle", (16, 7, 9), pos=(0, 29, 2), paint=BRASS_RIV),
        Box("Mouth", (8, 2, 1), pos=(0, 29, 7), paint=SOOT_P),
        Box("Steam-Emitter", pos=(0, 29, 7)),
        Box("PressureCap", (8, 3, 8), pos=(0, 24, -9), paint=BRASS_P),
        bolt("RivetA", (-8, 2, 11), 2),
        bolt("RivetB", (8, 2, 11), 2),
    )
    return Model([stack]), {}


def gasbag_frame():
    frame = Box("Base", (28, 3, 28), pos=(0, 1.5, 0), paint=CORRUGATED_RUST)
    for x in (-12, 12):
        for z in (-12, 12):
            frame.add(Box(f"Post{x}_{z}", (3, 27, 3), pos=(x, 14, z), paint=BRASS_RIV))
            frame.add(bolt(f"Foot{x}_{z}", (x, 2, z), 2))
    frame.add(
        Box("CrossbarX", (26, 3, 3), pos=(0, 25, 0), paint=BRASS_P),
        Box("CrossbarZ", (3, 2.8, 26), pos=(0, 25.2, 0), paint=BRASS_P),
    )
    return Model([frame]), {}


def gasbag_envelope():
    envelope = Box("Canvas", (27, 28, 27), pos=(0, 14, 0), paint=leather((0.54, 0.43, 0.30)))
    envelope.add(
        Box("PatchA", (10, 8, 1), pos=(-7, 3, 14), rot=(0, 0, -8), paint=plank((0.67, 0.49, 0.32))),
        Box("PatchB", (7, 9, 1), pos=(7, -5, 14), rot=(0, 0, 12), paint=leather((0.36, 0.30, 0.24))),
        Box("Seam", (2, 25, 1), pos=(0, 0, 14.5), paint=ROPE_P),
        bolt("GrommetA", (-11, 0, 14.2), 2, BRASS_P),
        bolt("GrommetB", (11, 0, 14.2), 2, BRASS_P),
    )
    return Model([envelope]), {}


BLOCKS = {
    # name: (builder, kwargs-variants)
    "Crank_A_Fizz": (crank_a_fizz, {"": {}}),
    "Rattlewheel": (rattlewheel, {"_Off": {"on": False}, "_On": {"on": True}}),
    "Shroom_Bed": (shroom_bed, {"": {}}),
    "Boomshroom_Burner": (boomshroom_burner, {"_Off": {"lit": False}, "_On": {"lit": True}}),
    "Fizz_Canister": (fizz_canister, {}),
    "Sloshtank": (sloshtank, {}),
    "Gutter_Boiler": (gutter_boiler, {"_On": {"lit": True}, "_Off": {"lit": False}, "_Offline": {"lit": False, "offline": True}}),
    "Gasbag": (gasbag, {
        "_Empty": {"fill": 0.0},
        "_Quarter": {"fill": 0.25},
        "_Half": {"fill": 0.5},
        "_ThreeQuarter": {"fill": 0.75},
        "_Full": {"fill": 1.0},
    }),
    "Gutterpipe": (gutterpipe, {f"_C{mask:02d}": {"mask": mask} for mask in range(64)}),
    "Snatcher_Claw": (snatcher_claw, {"": {}}),
    "Hurl_Tube": (hurl_tube, {"": {}}),
    "Hurl_Station": (hurl_station, {"": {}}),
    "Hurl_Cannon": (hurl_cannon, {"": {}}),
    "Catch_Net": (catch_net, {"": {}}),
    "Well_Cradle": (well_cradle, {
        "_Empty": {"state": "Empty"},
        "_Seated": {"state": "Seated"},
        "_Harmony": {"state": "Harmony"},
    }),
    "Crackle_Coil": (crackle_coil, {"_Off": {"active": False}, "_Active": {"active": True}}),
    "Resonator": (resonator, {"": {}}),
    "Gnasher_Pit": (gnasher_pit, {"": {}}),
    "Scorch_Oven": (scorch_oven, {"_Off": {"lit": False}, "_On": {"lit": True}}),
    "Wakebell_Anchor": (wakebell_anchor, {"_Off": {"lit": False}, "_On": {"lit": True}}),
    "Wellhead": (wellhead, {"_Off": {"pumping": False}, "_Pumping": {"pumping": True}}),
    "Glimmerwell_Anchor": (glimmerwell_anchor, {"_Normal": {"corrupted": False}, "_Corrupted": {"corrupted": True}}),
    "Junk_Heart_Firebox": (junk_heart_firebox, {"_Off": {"lit": False}, "_On": {"lit": True}}),
    "Scrap_Plating": (scrap_plating, {"": {}}),
    "Junk_Heart_Chimney": (chimney, {"": {"cap": False}, "_Cap": {"cap": True}}),
    "Storm_Vane": (storm_vane, {"_Off": {"on": False}, "_On": {"on": True}}),
    "Fizz_Damper": (fizz_damper, {"_Off": {"on": False}, "_On": {"on": True}}),
    "Tether_Stake": (tether_stake, {"": {}}),
    "Boiler_Tank": (boiler_tank, {"": {}}),
    "Boiler_Whistle_Stack": (boiler_whistle_stack, {"": {}}),
    "Gasbag_Frame": (gasbag_frame, {"": {}}),
    "Gasbag_Envelope": (gasbag_envelope, {"": {}}),
}

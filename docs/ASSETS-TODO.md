# Asset art status

Regenerate generated assets with `python3 tools/art/build.py`. Preview models with
`python3 tools/art/preview.py <out_dir>` and keep preview output outside the repository.

## Completed

- **Art Pass 2 (`de86ddb`):** Sloshtank fill states and icon; Gutter Boiler Off/On/Offline visuals,
  firebox glow and steam emitter; Gasbag five fill visuals; Hurl Tube, Station and Cannon models,
  Cannon Charge animation, Catch Net model and Land animation; Well Cradle Empty/Seated/Harmony;
  Crackle Coil Off/Active and Arc animation; Resonator frame and seated cyan crystal.
- Generated icons for Glimmer Lens, Gob-Jar empty/filled, Codex, all four Paint Daubs, Fizz Charge,
  Tether Stake, Glimmer Dust, Spark Shard, Riftmoth Wing, all grit piles, and the listed Craftory
  wares. The filled Gob-Jar uses one cyan swirl, without an item-ID tint.
- **A5:** Boiler Tank, Boiler Whistle Stack, Gasbag Frame and Gasbag Envelope parts, with generated
  models, textures and icons. The Boiler Tank has a cyan gauge; the Gasbag Envelope is patched canvas.
- Gutterpipe C00–C63 world-space shape models share one texture. Snatcher Claw has its own model,
  texture, icon and Grab animation. Junk Heart Firebox has Off/On art; Scorch Oven has an On state.
- Scrap Plating, Boom Valve, Gutter Gear, Scrap Plating Bodge, Squawk Box and Muffler use generated
  icons. Tinker's Hammer and Tinker's Wrench have generated hand models, textures and icons.
- Glimmerwell pulses use distinct sparse Flicker and dense Steady particle patterns (Roaring and
  Primal reuse Steady), tinted at spawn from all eight public Core temperament tint values.
- Tether beams use a custom cyan Beam asset and texture.
- Snatcher Claw `Grab`, Hurl Cannon `Charge` and Catch Net `Land` animations are event-triggered.
  Claw and Net hold for 10 ticks; Cannon uses a 5-tick (0.25-second) pre-launch charge.

## Remaining

- Mob re-tints for Wisp, Sparkjaw, Riftmoth and Poacher, plus Vessa, are blocked on vanilla rig
  assets; Hytale asset authentication/download is pending.
- Boiler sound IDs are blocked pending the vanilla sound files.
- All appearance remains client-unverified; offline previews are not a substitute for a live-client
  check.
- Storm Hunt part 2-owned items remain excluded from this work.

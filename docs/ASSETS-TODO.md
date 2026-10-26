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

## Remaining

- Glimmerwell Flicker/Steady appearance and temperament tints; Tethered beam colour.
- Mob re-tints/models for Wisp, Sparkjaw, Riftmoth and Poacher, plus Vessa. These need vanilla rig
  assets; Hytale asset authentication/download is pending.
- Hurl Cannon `Charge`, Catch Net `Land`, and Snatcher Claw `Grab` animations are assets referenced
  by block JSON but are not event-triggered.
- The Glimmerwell discovery anchor still needs its final appearance and effects. Gnasher Pit's
  planned 3×3×2 visual and the Hurl capsule model/mount animations also remain.
- The Junk Heart chimney still uses the vanilla grey developer pipe; Scrap Plating still needs a
  dedicated riveted block texture. Grizzlecog still uses the vanilla Klops body texture and tunic.
- Craftory smoke/Fizz/Glimmer particle-system JSONs and authored Jank fizzle/rattle effects are
  still outstanding; current machine effects reuse vanilla particles and sounds.
- All appearance remains client-unverified; offline previews are not a substitute for a live-client
  check.
- Storm Hunt part 2-owned items are excluded from this work.

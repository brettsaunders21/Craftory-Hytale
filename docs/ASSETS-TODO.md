# Asset art status

## Completed

The generated Goblin-era art pass includes Craftory block models and textures for the Boomshroom Burner, Crank-A-Fizz, Fizz Canister, Gnasher Pit, Gutterpipe, Junk Heart Chimney and Firebox, Scorch Oven, Scrap Plating, and Wakebell Anchor; item models and textures for the Squint Goggles, Tinkers Hammer, and Tinkers Wrench; generated Craftory item icons; Grizzlecog model attachments; and Craftory particle sprite textures.

The item wiring and Grizzlecog appearance update are in this branch. Art wiring still needed by PR #9 and PR #10 is listed in [ASSETS-WIRING.md](ASSETS-WIRING.md).

Regenerate the generated assets with:

```sh
python3 tools/art/build.py
```

Preview generated models with the offline renderer:

```sh
python3 tools/art/preview.py <out_dir> ~/.gradle/caches/hytale-assets/release-0.7.4-Assets.zip
```

## Remaining

- **Gutterpipe connected shapes:** patterned rule sets such as vanilla `MetalSimplePipe` map each shape to a separate block ID. Gutterpipe is also an energy block entity, so connected shapes need per-shape block IDs and network support. Only the straight, end-capped form ships for now.
- **Fizz Canister fill-state switching:** the item defines Empty, Quarter, Half, ThreeQuarter, and Full model states, but there is no canister ticking system to switch among them.
- **Scorch Oven On state:** only the Off model is wired; the On art is unused.
- **Gnasher Pit multiblock model:** the current model is one block; the planned model is 3×3×2.
- **Particle systems:** Craftory smoke sprites, `Fizz_Spark.png`, and `Glimmer_Mote.png` still need particle-system JSON assets.
- **PR #9/#10 wiring:** see [ASSETS-WIRING.md](ASSETS-WIRING.md).
- **Grizzlecog body:** the model still uses the vanilla Klops body texture and tunic.
- **In-client visual verification:** appearance remains client-unverified because the previews come from an offline renderer.

# Asset art TODO

Replace these vanilla placeholders with Craftory Blockbench models and textures:

- `Craftory_Crank_A_Fizz`: Goblin lab tinkering bench model, texture, and idle animation.
- `Craftory_Boomshroom_Burner`: Goblin brazier model, off/on textures, fire animation, particles, and light.
- `Craftory_Fizz_Canister`: Goblin boomshroom generator model and texture.
- `Craftory_Gutterpipe`: grey developer tube model and texture. It is a straight-pipe placeholder; connected-block visuals are not configured.
- `Craftory_Hurl_Tube`: vanilla developer tube placeholder; replace with glass-and-scrap, player-sized tubes and connected bend/junction visuals.
- `Craftory_Hurl_Station`: vanilla developer tube placeholder; replace with a glass-and-scrap boarding platform and station sign.
- Hurl capsule: non-serialized vanilla Minecart model (debug-model fallback); replace with a player-sized capsule and mount animations. Camera smoothness remains unverified.
- `Craftory_Hurl_Cannon`: vanilla developer tube placeholder; replace with a scrap cannon, charging animation, smoke and firing effects.
- `Craftory_Catch_Net`: vanilla hay cube placeholder; replace with a rope-and-scrap net and landing animation.
- `Craftory_Grizzlecog`: Klops merchant appearance and shopkeeper animation.
- `Craftory_Gnasher_Pit`: replace the single-block placeholder with the planned 3×3×2 multiblock model and textures.
- `Craftory_Tinkers_Hammer`: vanilla crude hammer model, texture and icon (`Items/Tools/Hammer/Crude.blockymodel`, `Icons/ItemsGenerated/Tool_Hammer_Crude.png`).
- `Craftory_Junk_Heart_Firebox`: goblin brazier model and texture (same as the burner off state) with no fire state yet. Needs a firebox model, a lit state and a door animation.
- `Craftory_Scrap_Plating`: grey developer cube textures from `Build_GreyDark_Cube` (`BlockTextures/Dev_Grey_Dark_Side.png`, `Dev_Grey_Dark_Top.png`). Needs riveted scrap textures.
- Junk Heart chimney: the template uses the vanilla `Build_GreyDark_Pipe` block (grey developer tube, `VariantRotation: Pipe`) for (0,1,0) and (0,2,0). A goblin brick or pipe chimney block would fit better once one exists.
- Ghost frame: a documented `PrefabPreview` entity lists each missing part's placed block (preview mode), plus `Dust_Sparkles_Fine` particles at each missing part (particles mode). The Core config key `GhostMode` (`preview`, `particles` or `both`) picks one; the default is `both`. **Preview visuals are not yet verified in a real client** (translucency, tint and whether the offsets line up), so keep `both` until they are.
- Junk Heart smoke: `Dungeon_Goblin_Smoke_Pipe_Small` particles at the chimney top.
- Hammer clank: `SFX_Metal_Hit`. A goblin anvil clank would be better.
- Boom Valve, Gutter Gear, Scrap Plating Bodge, Squawk Box and Muffler icons: `GoblinLab_Tinkering_Bench.png`.
- Jank fizzle puff: `Cosmetic/Goblin/Cosmetic_Stink`.
- Jank rattle steam: `Decorative/Goblin/Goblin_Decorative_Steam`.
- High-Jank grey smoke: `Deployables/Healing_Totem/Totem_Heal_Smoke`.
- Jank rattle/fizzle sound: `SFX_GoblinLab_Broken_Device`.
- `Craftory_Glimmerwell_Anchor`: replace the invisible, non-solid discovery placeholder with a Glimmerwell model, temperament-tinted glow, and pulse/mote effects.
- Glimmerwell discovery effects currently use the vanilla `Impact_Spark` particle and `SFX_Interact` sound as placeholders; replace with authored pulse and resonance assets.

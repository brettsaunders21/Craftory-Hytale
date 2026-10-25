# Craftory for Hytale: Gameplay & Art Direction

Status: draft for discussion. Names are placeholders.

## 1. The pitch

Craftory starts as **goblin junk-tech** — loud, smoky, held together with rope and Boomshroom
gas — that you can only get by trading with goblins. You earn their know-how, then build and bodge
your own machines. Along the way you find crystals that react to your machines, and Craftory turns
into a **crystal-and-scrap hybrid**: thrown-together brass, glowing crystal and arcing
sparks, in the style of the TV series *Arcane*. Research pushes further until the tech falls away
and you're running **pure arcane magic**, with flashing infusion rituals and tiny helper golems
inspired by Thaumcraft.

Every Craftory Tech system survives (energy storage, generators, ore doubling, smelting, foundry,
magnetising, block placing and breaking, fluids, tools, defence). Each one is re-themed and
reworked for every tier.

### Grounding in vanilla Hytale (Update 7, "Chapter I: Out of the Breach")

We build on what the game already has instead of fighting it:

- **Scrap** is the goblin material players already earn from Goblin Breaches.
- **The Goblin Lab** (Forgotten Temple) has a Boomshroom-powered Generator that drives the Goblin
  Tinkerer's Workbench, plus Boiler/Engine/Compressor recipe-set machines. That's exactly the "a
  power source drives benches" fantasy, so our Tier 1 is presented as the goblins' *other* gear.
- **Goblin trader NPCs** already exist (e.g. Klops Scrap Trader, who takes Scraps and the Gobliterator
  Core). Barter shops are data-driven JSON (`Server/BarterShops/*.json`), so we can add our own.
- **The Void, Voidhearts, the Deepworks and the Gobliterator** give us late-goblin-tier goals and a
  natural source of arcane instability.
- **Runestones and mana** show that Hytale already has a magic layer. Our arcane tiers should
  feel like the industrial big brother of runestones and could interoperate with them later.
- The **Scrap Arcanist** cosmetic set's name is a perfect fit for the mid tiers.

IP note: *Arcane* and Thaumcraft are style references only. We don't use their names or terms
(no "Hextech", "Shimmer", "Piltover", "Zaun", "Vis", "Aspects").

## 2. Tier ladder

Seven tiers in three eras. The engine treats tiers as data, so we can add or split tiers later.

| # | Era | Tier name | Energy | How you get there |
|---|-----|-----------|--------|-------------------|
| 1 | Goblin | **Scrapjank** | Fizz | Trade Scrap with Grizzlecog the goblin peddler for *sealed* machines. You can't craft them yet. |
| 2 | Goblin | **Tinkered** | Fizz (high pressure) | Fill your Tinker's Notebook by taking apart traded machines and doing Grizzlecog's jobs. Then you build your own. |
| 3 | Hybrid | **Sparkbound** | Fizz + Dim Glimmer | Your first crystals, found near breaches or Void scars, cracked and wired into goblin machines. Unlocked by your first research discovery. |
| 4 | Hybrid | **Glimmerforged** | Dim → Bright Glimmer | Crystal-cored machines and the first Resonators that convert Fizz to Glimmer. Unlocked after defeating the Gobliterator and studying its Core. |
| 5 | Hybrid | **Resonant** | Bright Glimmer | Mostly crystal with tech scaffolding. Infusion begins, and you get Tinkling golems. |
| 6 | Arcane | **Arcanum** | Radiant Glimmer | The tech falls away into pure magic: floating machines, flowing energy and big infusion rituals. |
| 7 | Arcane | **Ascendant** | Prismatic Glimmer | Endgame: reality-bending devices and Void-taming, with room to add more later. |

### Visual language per era

- **Goblin (1–2):** riveted scrap and corrugated tin, mismatched bolts and rope lashings. Sputtering
  Boomshroom tanks, steam puffs and smoke rings, and lots of wobble and shake when running. Warm
  palette: rust, brass, toxic green Fizz.
- **Hybrid (3–5):** the same scrap now cradles cracked glowing crystals, with taped-on copper coils,
  arcing sparks, and crystal light bleeding through gaps. Palette moves to brass plus cyan/magenta
  glow. The crystals get cleaner and the scrap gets tidier as you climb.
- **Arcane (6–7):** almost no metal. Floating crystal lattices, rune rings, orbiting motes, and
  flashing, pulsing rituals.

Every machine has a Blockbench model *per era*, not just a recolour. A Tier 1 Gnasher and a Tier 5
Gnasher should look like different machines that do the same job.

## 3. Energy

One energy network model with a **grade** on every unit of energy. That's the "devices need certain
tiers" rule.

| Grade | Name | Produced by | Feel |
|-------|------|-------------|------|
| 0 | **Fizz** | Goblin generators | Bubbly green gas-electric. Leaky. |
| 1 | **Dim Glimmer** | Resonators (from Fizz) and crystal generators | Faint cyan sparks. |
| 2 | **Bright Glimmer** | Tier 4–5 crystal generators | Strong arcs and hum. |
| 3 | **Radiant Glimmer** | Arcane sources | Flowing light ribbons. |
| 4 | **Prismatic Glimmer** | Endgame | Shifting colours. |

Rules:

- Each device has a `minGrade` (to run) and a `maxGrade` (that it can handle). Pushing a higher grade
  into a low-tier device **overloads** it: sparks, smoke, jank build-up, and eventually a breakdown.
  It's dramatic but recoverable, never griefing-level destruction.
- Pipes and conduits also have a max grade. Mixed-grade setups use **Resonators** to step energy up
  (lossy) and **Dampeners** to step it down.
- Energy storage: Fizz Canister → Boomshroom Battery (goblin), then Glimmer Capacitors by grade.
  Capacities follow Craftory Tech's energy cells, scaled per tier.

## 4. Progression path (what the player actually does)

1. **First contact.** Do a vanilla Goblin Breach and get Scrap. Grizzlecog, a goblin peddler, shows up
   at the Goblin Lab (or in breaches) and sells *sealed* Scrapjank machines. A sealed machine works,
   but slowly builds up jank and has to be patched with Scrap. You can't open or copy it.
2. **Earn the know-how.** Two paths feed the **Tinker's Notebook**, and you can mix them:
   - **Reverse-engineer:** take a sealed machine apart with the Tinker's Wrench. It's destroyed, and
     you get a notebook page.
   - **Goblin jobs:** a short questline from Grizzlecog (bring Boomshroom, defend the Lab from a Void
     surge, recover a blueprint from the Deepworks).
3. **Build your own (Tier 1, then Tier 2).** Use Ramshackle Assembly (§5). Then upgrade by *bodging*
   (§6) to reach Tinkered gear.
4. **The first spark.** A Tinkered machine running near a crystal or Void scar makes the crystal
   react. That reaction is your first research discovery and opens **Sparkbound**.
5. **Research era.** World exploration with visible effects (§7) unlocks Glimmerforged and Resonant.
   The Gobliterator's Core is a key Tier 4 research item.
6. **Leaving tech behind.** Late research replaces machine parts with crystal and rune equivalents:
   Arcanum, then Ascendant.

The player follows a **Craftory Codex**: a node map in a custom UI page that lights up and links
as you progress, so the path is always visible.

## 5. Ramshackle Assembly (interactive building)

Machines aren't just crafted at a bench:

1. Place a **frame** (a ghost outline of the machine).
2. Hit parts in with a **Tinker's Hammer** in the world, one by one. Each part snaps into place on
   the model with a clank and smoke puff, and the frame shows the next part needed.
3. **Kick-start:** the machine sputters, and a short timing minigame decides its *build quality*
   (Wobbly / Sturdy / Masterwork), which adds small stat modifiers.

In the hybrid tiers the final step becomes **crystal seating**: align a crystal's resonance by
rotating it until it hums. In the arcane tiers, assembly becomes an infusion ritual (§8).

## 6. Bodging and Jank (the "improve your tech" mechanic)

- Machines have **upgrade slots** for bodge parts: Boom-Valves (speed), Gutter Gears (efficiency),
  Scrap Plating (durability), Squawk Boxes (status alerts).
- Pushing a machine harder builds **Jank**. Jank shows as rattling, sparks and smoke. High Jank risks
  a comedic **fizzle** (the machine stalls and spits its contents). It's not lethal, just annoying.
- Upgrading a machine's tier means bodging it up: install the right set of parts and run the
  machine "hot" until it **settles** into the next tier. It's a risk-and-reward loop rather than a
  plain crafting recipe.
- Later, Tinkling golems (§9) can keep Jank down.

## 7. Research: interactive and visible while you explore

- **Glimmer Lens** (the goblin Spyglass, upgraded): look at things in the world (crystals, Void scars,
  creatures, ruins, weather such as storms, breaches, your own running machines). Each scan triggers
  a burst of motes that fly into you and adds **Insight** of a theme (Spark, Ember, Tide, Growth,
  Void, Stone...).
- **Field experiments:** some research needs something set up in the world, e.g. "run a Resonator
  next to an open breach during a thunderstorm." Success shows a big visual moment (light pillar,
  sound sting) and unlocks a Codex entry.
- **Research Bench:** spend Insight on a hex-node puzzle in a custom UI page, linking themes from
  start to goal. Solving it plays an animation and the Codex node lights up.
- **Research is per player** (and shareable on co-op servers through a "Shared Notes" option).
- Late tiers move research from the bench into the world: ritual circles, pylons, starlit
  alignments.

## 8. Arcane systems

- **Infusion Altar:** a multi-block with a centre pedestal and ring pedestals. Items orbit, beams
  flash between pedestals, and instability can leak (Void wisps spawn and must be handled). This is
  the main Tier 5+ crafting method.
- **Rune Rings:** arcane-era replacements for machine parts that float and spin around devices.
- **Wards and Spires:** base defence. Goblin Bangstick turrets give way to Sparkbound coil towers, then
  Glimmer Spires.
- **Runestone tie-in (later):** arcane devices could charge or craft vanilla runestones or affect mana.

## 9. Tinklings (tiny golems)

Small scrap-and-crystal helpers, introduced in Tier 5 and refined in the arcane tiers:

- Built from a body (scrap at first, crystal later), a core (sets the tier) and a **Job Sigil**.
- **Job Sigils** are placed in the world to set the task area: Courier (move items between
  containers), Harvester, Miner, Tender (keeps Jank down and refuels machines), Guard.
- Upgrades: carry capacity, speed, range, and filter sigils.
- They show their state through animations (hauling, sleeping, confused sparks when blocked).
- Implementation should use Hytale's NPC/role system. This needs a prototype first.

## 10. Pipes and transport

Pipes are a showcase. Everything moving through them is **visible**.

| Tier | Pipe | Carries | Look |
|------|------|---------|------|
| 1–2 | **Gutterpipe** | Items, fluids, Fizz | Rickety brass and tin with glass portholes. Items rattle past, fluid sloshes, leaks drip. |
| 3–4 | **Sparkline** | + Dim/Bright Glimmer, faster items | Copper pipe wrapped round crystal shards. Items glow as they pass. |
| 5 | **Glimmer Conduit** | + filtered/sorted routing | Crystal tube with a visible light stream. |
| 6–7 | **Ley Channel** | Everything, very fast | No pipe, just a floating light ribbon between rune nodes. |

- **Hurl Tubes (Tier 2+):** person-sized pneumatic tubes like Satisfactory hypertubes. Jump in at a
  goblin launcher (big puff of smoke), ride through, and pop out at the end. They go from wobbly goblin
  tubes to crystal-lit rides and later ley streams. They work well alongside vanilla gadgets (Scrap
  Glider, Hookshot).
- Items moving through pipes are rendered with level-of-detail limits, so visuals stay smooth
  (see the architecture doc).

## 11. Craftory Tech feature map

| Craftory Tech | Goblin era | Hybrid era | Arcane era |
|---------------|------------|------------|------------|
| Energy cells | Fizz Canister, Boomshroom Battery | Glimmer Capacitor (Dim/Bright) | Radiant/Prismatic Reservoir |
| Solid fuel generator | Boomshroom Burner | Spark Furnace | n/a |
| Geothermal generator | Gutter Boiler (lava/magma steam) | Magma Resonator | Core Tap |
| Rotary (wind/water) | Rattlewheel | Storm Vane (bonus in thunderstorms) | Sky Loom |
| Solar | n/a | Sunglass Array (crystal lenses) | Daystar / Moonwell (by time of day) |
| Electric furnace | Scorch Oven | Glimmer Kiln | Ember Altar |
| Macerator (ore doubling) | Gnasher (chomping jaws) | Crystal Grinder (tripling) | Unmaking Circle |
| Foundry | Bodge Foundry | Spark Foundry | Infusion |
| Magnetiser / table | Sparkcoil | Resonance Imprinter | Rune Ring imprinting |
| Block placer / breaker | Plonker / Thumper | Spark Arm | Tinkling Miner/Builder sigils |
| Power connectors | Fizz Spigot + Gutterpipe | Sparkline | Ley Node |
| Fluids | Gutterpipe and Sloshtank | Glimmer Tank | Ley Channel |
| Powered tools | Fizz Drill and saw (alongside vanilla Scrap Drill) | Spark tools | Arcane focus tools |
| Turrets | Bangstick Turret | Coil Tower | Glimmer Spire / Ward |
| (new) Wrench | Tinker's Wrench | same, upgraded | Attuning Rod |
| (new) Goggles | Squint Goggles (energy/jank HUD) | Glimmer Lens (adds research scanning) | Truesight Circlet |

## 12. Open questions

- Grizzlecog: our own goblin NPC (needs us to add one), or extend existing goblin traders where the
  API allows?
- Should sealed machines eventually break, or just stay worse than built ones?
- PvP/griefing limits for overload effects on servers. Default: cosmetic plus stall only.
- How deep should runestone/mana integration go?

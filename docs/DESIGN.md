# Craftory for Hytale: Game Design

Status: merged design plan (replaces the earlier draft). Names are working names. Numbers are placeholders for tuning.

Tags used in this document:

- **(needs prototype)**: depends on a Hytale modding API we haven't tested on server 0.7.x yet.
- **(proposal)**: changes or adds to something the owner has already approved. Needs a yes or no.
- **(cut)** in §13: dropped on purpose.

Target: Hytale Early Access, Update 7 "Chapter I: Out of the Breach", server 0.7.x, Kotlin. Engine and module layout are in [ARCHITECTURE.md](ARCHITECTURE.md).

IP rule: *Arcane* (the TV series) and Thaumcraft are style references only. We don't use their names or terms (no Hextech, Shimmer, Piltover, Zaun, Vis, Aspects, Taint, Thaumometer).

---

## 1. Pitch and pillars

**Pitch.** You start by buying leaky goblin machines with Scrap from a breach. You learn to bodge your own. Then one of your machines makes a breach crystal *sing*, and a half-blind crystal archivist shows up to explain what you just did. From there, scrap and crystal merge into sparking Arcane-style hybrid tech. Eventually the metal falls away and you're running floating rune machinery fed by **Glimmerwells**: knots of raw magic that are born from storms, breaches, Blood Moons and boss deaths. You hunt Wells, fight what guards them, haul them home through hostile country, and at the very end you tame a piece of the Void into a realm of your own.

**Pillars**

1. **Power comes from places and events.** Every important energy source is tied to something in the world: Boomshroom caves, storms, lava, auroras, breaches, Wells, the Void. Where you build and when you go out matter more than what you craft.
2. **Assembled and operated by hand, automated later.** Machines are put together in the world and worked with cranks, kicks, valves, tuning and rituals. Automation is the reward, and you always feel what you automated away.
3. **The machine shows its state.** Speed, Jank, grade, fill and upgrades show on the model (gauges, smoke colour, crystal glow, ring speed). UI is optional.
4. **Research happens out in the world.** Scanning, field experiments and Well hunts are the main way you progress. Menus are a shortcut, not the path.
5. **Goblin tech and crystal magic merge and never fully separate.** Each era has its own verb, look and teacher, but the scrap never fully disappears and goblins stay relevant to the end.
6. **Risk makes bases feel alive and never griefs anyone.** Faults stall, pop and spill. They don't destroy blocks by default and never cross into another player's claim.
7. **Cheap on the server, expandable by addons.** Everything heavy is event-driven, timer-based or budgeted (§15). Tiers, Wells, mobs, modules and realm islands are data that addons can extend.

---

## 2. Eras and tier ladder

Seven tiers in three eras (unchanged, owner-approved). Tiers are data, so we can add or split them later.

| # | Era | Tier | Energy grade | Teacher | Core verb | Signature fantasy |
|---|---|---|---|---|---|---|
| 1 | Goblin | **Scrapjank** | Fizz (0) | Grizzlecog | crank, kick | Sealed goblin machines you buy and keep patching |
| 2 | Goblin | **Tinkered** | Fizz, Pressurised Fizz (0+) | Grizzlecog, Snikka | bash, vent, bodge | Your own ramshackle factory and first Hurl Tube |
| 3 | Hybrid | **Sparkbound** | Dim Glimmer (1) | Vessa | scan, tune | Cracked crystals wired into goblin junk; your first Well |
| 4 | Hybrid | **Glimmerforged** | Bright Glimmer (2) | Vessa | aim, calibrate | The Gobliterator Core turned into a power plant; weather power |
| 5 | Hybrid | **Resonant** | Bright Glimmer (2), tinted | Vessa, Snikka | hammer rhythm | Infusion begins; Tinklings walk your base |
| 6 | Arcane | **Arcanum** | Radiant Glimmer (3) | Vessa (last lesson) | conduct, perform | Floating machines, ley streams, Primal Wells |
| 7 | Arcane | **Ascendant** | Prismatic Glimmer (4) | none | weave | Tame the Void; build the Hollowmere realm |

**Energy grades**

| Grade | Name | Look in pipes and machines | Main sources |
|---|---|---|---|
| 0 | **Fizz** | Green bubbles in glass portholes, burping valves | Boomshroom fuel, cranks, water and wind |
| 0+ | **Pressurised Fizz** (proposal) | Same but fast and hissing, with twitching gauge needles | Gutter Boiler, Fizz Compressor |
| 1 | **Dim Glimmer** | Faint cyan sparks | Crackle Coils, Resonators, Flicker Wells |
| 2 | **Bright Glimmer** | Strong arcs and an audible hum | Coreheart Engine, weather generators, Steady Wells |
| 3 | **Radiant Glimmer** | Flowing ribbons of light | Arcane generators, Roaring and Primal Wells |
| 4 | **Prismatic Glimmer** | Colour-shifting ribbons | Only woven: Prism Confluence and Veil Engine |

Pressurised Fizz is grade 0 with a `pressurised` flag in the engine. Tinkered machines run slowly on plain Fizz and at full speed on Pressurised. It gives Tier 2 its own power goal without adding a sixth grade.

---

## 3. Progression spine

### 3.1 How a tier is unlocked: Keystones and Hearts

The **Craftory Codex** (custom UI page) shows the seven tiers as bands. Each band has one **Keystone** that gates the next tier. Everything else in the band is optional power or comfort. A Keystone has three steps:

1. **Learn**: a research requirement (Notebook pages, a scan set or a field experiment).
2. **Prove**: a world goal (an event, a boss or a capture) that drops the tier's **gating item**.
3. **Build**: assemble the tier's **Heart** from the gating item.

The **Heart** (proposal) is the base centrepiece of that tier and is also a useful machine. It doesn't hard-gate machines. Instead, machines inside its radius build Jank 25% slower and get a small speed bonus. It shows the base's tier from across a valley, and on team servers it's the shared progression object (§11). Each new Heart is an upgrade of the last one, assembled in place on the same footprint (Sprawl, §6.1), so the base grows around one landmark.

### 3.2 Per-tier checklist

| # | Tier | Learn | Prove (gating item) | Build: Heart | Key machines unlocked | Signature event / mob | Hours (cum.) |
|---|---|---|---|---|---|---|---|
| 1 | Scrapjank | Finish a vanilla Goblin Breach, bring Grizzlecog 20 Scrap | Survive **Scrap Rush** (a rival goblin crew tries to steal your sealed machine) → *Cracked Tinker's Wrench* | **Junk Heart**: a junk furnace and smokestack | Sealed Burner, Gnasher Pit, Scorch Oven, Fizz Canister, Gutterpipe, Crank-a-Fizz, Rattlewheel, Shroom Bed | Scrap Rush; first Flicker sightings | 2–3 (~3) |
| 2 | Tinkered | 8 Tinker's Notebook pages (take sealed machines apart, goblin jobs) | **Deepworks Blueprint run**: recover the Pressure Valve schematic → *Pressure Valve* | **Boomheart Boiler**: the Junk Heart rebuilt around a Gutter Boiler with a Gasbag on top | Ramshackle Assembly, bodging, Bellows Foundry, Gutter Boiler, Tar Still, Gasbag, Snatcher Claw, Chuck Chute, Hurl Cannon and Tubes, Bangstick Turret, Junkyard Crane | Goblin Poachers appear; Breach Leech runs | 4–6 (~9) |
| 3 | Sparkbound | Field experiment **First Spark**: a running Tinkered machine within 8 blocks of a breach or Void crystal | Catch your first **Flicker** in a Gob-Jar and seat it → *Dim Resonant Shard* | **Spark Heart**: crystal shards wired into the Boiler cage | Glimmer Lens, Tuning Fork, Crackle Coil, Resonator, Storm Vane, Void Scar Tap, Sparkline, Coil Tower, Glimmer Gauntlet, Zap Focus | Vessa arrives; Stormcall; Sparkjaw, Glimmer Wisp | 4–6 (~15) |
| 4 | Glimmerforged | Experiment **Heart of the Machine**: the Gobliterator Core on an Imprinter, fed Bright Glimmer | Tether and seat your first **Steady** Well → *Storm Shard*, plus the studied Core | **Coreheart Engine** (replaces the Spark Heart): the Gobliterator Core spinning in a goblin clamp cage | Prism Grinder (tripling), Arc Foundry, Imprinter, Sunglass Array, Moonwell, Magma Resonator, Voidheart Furnace, Spark Pylon, Sorting Prism, Punch Cards, Booster Rings, Skyline, Ward Lantern | Scar Bloom, Coffer Echo; Cinderkin, Riftmoth | 6–8 (~23) |
| 5 | Resonant | Seat 3 Wells of different Temperaments; experiment **Three-Point Resonance** | Survive **Choir Tide** (3-wave defence during your first Resonance Forge craft) → *Harmonic Chord* | **Resonant Heart**: clamps gone, the Core floats in turning crystal rings | Resonance Forge, Tinklings, Glimmer Conduit, Aurora Harp, Refractor Pane, Void Lure, Shaper and Excavation Foci | Mossback Warden, Shard Golem, Shardling; Snikka moves in | 8–10 (~33) |
| 6 | Arcanum | Vessa's last lesson: rebuild the Lantern Archive's **Great Lens** | Beat the **Prism Warden** at a Primal Well, or win a **Convergence** → *Starlit Keystone* | **Arcane Heart**: a floating prism with rune rings | Infusion Circle, Rune Rings, Unmaking Circle, Ley Channels and Streams, Ley Tap, Sky Loom, Blood Moon Chalice, Glimmer Spire, Ward Spire, Ley Lantern, Skyward Disc | Blood Tide; Hollowmaw; Prism Warden | 8–12 (~45) |
| 7 | Ascendant | Experiment **Hollow Silence**: hold a Void-touched Well stable while holding a lit Ward Lantern | Tame a Void-touched Well into a **Veil Anchor**, then beat **The Unwoven** there → *Unwoven Heartstring* | **Prismatic Heart**: a slowly turning prism that splits light across the base | Prism Confluence, Veil Engine, Rift Gates, Orrery, Riftloom, Prismatic Reservoir, Blink Focus | The Unwoven; Fraying events | 10–15 (~60) |

The hours are for one player playing normal vanilla content alongside. A group of 3–4 should be about 40% faster. Server config scales research costs.

### 3.3 Story beats (effects and NPC dialogue, no cutscene system)

| Tier | Beat |
|---|---|
| 1 | Grizzlecog's sealed crate opens with a puff and the machine coughs to life. |
| 2 | Snikka, Grizzlecog's nervous apprentice (proposal), sneaks you the Pressure Valve job. |
| 3 | Your machine whines, a nearby crystal answers with a light pillar, and **Vessa Lanternwright** steps out of the glow: "*You made it sing. With… a kettle?*" Goblins back off: "Void-glass! Bad luck! …Sells though." |
| 4 | The Gobliterator Core hums in its cage while goblins gather to watch, terrified and impressed. |
| 5 | Snikka holds a crystal barehanded for the first time and starts running your Tinkling workshop. |
| 6 | The Great Lens opens, Vessa sees clearly for the first time, and she hands you her folio. |
| 7 | The Unwoven falls, the scar goes still, and the Riftloom opens the Hollowmere. Grizzlecog arrives with a cart: "*New market. Prices went up.*" |

**Cast**

- **Grizzlecog**: goblin scrap peddler (custom NPC role plus a `Server/BarterShops` JSON).
- **Snikka** (proposal): reuses the goblin rig and carries the merge story.
- **Vessa Lanternwright**: a human crystal archivist who lives in the **Lantern Archive**, a ruin prefab placed near a Void scar.
- **The Wellborn**: creatures that form around Wells. They aren't evil; Vessa calls them "the world trying to sing".
- **The Unwoven**: a Prism Warden swallowed by the Void, and the Tier 7 antagonist.

We sit next to Hytale's lore ("people who learn from the breaches") and don't rewrite canon. Any lore reference should be checked with the owner.

---

## 4. Glimmerwells, world events and mobs

### 4.1 What a Glimmerwell is

A **Glimmerwell** (just "Well" in the UI) is a knot of raw magic that forms where something big happened: lightning hit a ridge, a breach tore open, a boss died, a Blood Moon rose over a lake. The drafts used node, Ley Well and Wellspring for this idea. They are now one system. A Well has three properties:

- **Temperament**: its type. It sets the Insight theme, the Glimmer **Tint**, the guardians and what it likes.
- **Strength**: its size. It sets the highest Glimmer grade it can supply.
- **State**: what's happening to it right now.

Tiny Wells, called **Flickers**, can be caught by hand. They're the Tier 3 tutorial.

### 4.2 Temperaments (8)

| Temperament | Born from | Looks like | Tapped for | Taste (likes / hates) |
|---|---|---|---|---|
| **Spark** | Lightning strikes in thunderstorms; Storm Vanes running in storms | Crackling cyan knot, arcs jump to nearby metal | Spiky, fast Glimmer | Likes copper and scrap; hates water |
| **Ember** | Lava zones; an exploding Boomshroom Generator; fire deaths | Slow orange coal-sphere dripping sparks | Steady heat; boosts Kilns and Foundries | Likes lava and furnaces; hates water and rain |
| **Tide** | Rain on open water, waterfalls, Blood Moon over a lake | Sphere of floating droplets, mirror-like | Fluid-linked Glimmer; purifies Void Tar | Needs open water within 4 blocks; hates Ember |
| **Growth** | Old forest clearings; a Mossback Warden's death; big farms | Pulsing green bulb, flowers bloom around it | Low Glimmer, big crop and Tinkling regen buffs | Likes crops; hates Gnashers and Racket |
| **Stone** | Deep caves, Deepworks ruins, golem kills | Floating geode that spins and sheds pebbles | Very steady base-load | Needs 3+ blocks of rock overhead |
| **Gale** | High peaks, auroras (`Zone3_Northern_Lights`), Scrap Glider flights near cliffs | Shimmering swirl that tugs loose items in | Powers Hurl Tubes, Skylines and Sky Looms directly | Needs open sky above |
| **Clank** | Goblin camps, breaches, and *your own base* when Racket stays high | A whirl of gears and scrap in green Fizz | Repairs Jank; boosts goblin-era machines | Likes running machines; hates Wards |
| **Hollow** | Breach scars, Void creature kills, Voidhearts | Purple-black inverted sphere; light bends into it | Huge Glimmer, but adds Void Bleed (§10) | Hungry: must be fed |

Clank is the goblin-flavoured Temperament that keeps scrap relevant in the magic tiers. Gale also covers frost and sky (the energy draft's Frost tint is folded into it).

**Secondary Temperament.** A Well born during a second condition (a Spark Well during a Blood Moon) can come out dual, e.g. *Spark/Hollow*. Dual Wells count as two Tints for the Prism Confluence and are required by some Arcanum recipes (Sky Loom needs Gale/Spark). Players will hunt for specific pairs.

### 4.3 Strength (4)

| Strength | Size | Max grade supplied | Typical birth | Guarded by |
|---|---|---|---|---|
| **Flicker** | Fist-sized mote | Dim (1) | Rainstorms, small kills, any small event | Nothing; catch it by hand |
| **Steady** | Barrel-sized | Bright (2) | Storm strikes, small breach scars, Coffer wave 3+ | 1–3 Wellborn |
| **Roaring** | 3×3 orb with orbiting shards | Radiant (3) | Full breach closures, elite kills, Blood Moon peaks | A named guardian encounter with a boss bar |
| **Primal** | 5×5+, bends terrain, lights the night sky | Radiant (3), and counts as 2 Tints | Gobliterator or Voidtaken Grizzly death sites; stacked events | The Prism Warden; server-wide map marker |

No Well supplies Prismatic directly. Prismatic is *woven* from many Tints (§5.5). That keeps exploration ahead of raw size at the top.

Strength also changes with the world: Spark Wells surge in storms, Hollow Wells surge at night and after breaches, Growth Wells surge at dawn. The same spot is worth visiting at a different time, so players learn the weather.

### 4.4 States

```
 event spawns it ──► WILD ◄──── Blood Moon / storm flare
                      │ calm (beat guardians, or smother with Fizz Dampers)
                      ▼
                   DORMANT ──tether──► TETHERED ──► TAPPED (Wellhead, in place)
                      │                         └─► JARRED ──► SEATED (Cradle at home)
     Void feeders     ▼                                          │ overdrawn / neglected
                  CORRUPTED ──left too long──► VOID-TOUCHED      ▼
                                                               SPENT (shrinks to a Flicker)
```

- **Wild**: pulses, sheds Flickers and harmless **Glimmer squalls** (knockback and light), and spawns guardians. Can't be tethered.
- **Dormant**: calm and humming. Can be scanned fully and tethered.
- **Corrupted**: Riftmoths have been feeding on it. Its anchor desaturates, output halves, and it spawns Void larvae on a timer. Cleanse it after every tracked Riftmoth is confirmed dead or with a seated/tapped Tide or Growth Well within 16 blocks. A Cleansing Censer remains deferred.
- **Void-touched**: corrupted for too long. It becomes a small Void anomaly (Void Larvae, a shielded Hollowmaw). It's dangerous, and it's also the Ascendant target.
- **Spent**: overdrawn. It shrinks back to a Flicker. It's a recoverable mistake, not a permanent loss.

### 4.5 Birth events

All built on Hytale's **World Event** system (`Server/WorldEvent`, stage graphs with conditions and actions) and the **Encounter Manager** (`Server/EncounterManager`: spawn markers, player roster, boss bars, weather overrides, `SignalWorldEvent`). Both are documented for 0.7.2. We haven't tested them ourselves.

| Event | Trigger (cheap) | What the player sees | Result |
|---|---|---|---|
| **Stormcall** | Thunderstorm weather; one roll every few minutes per loaded player region | Lightning hits a ridge nearby, the strike point keeps crackling, a map marker appears | Wild Spark Well (Flicker–Steady). Storm Vanes within 64 blocks raise the strength roll |
| **Scar Bloom** | Vanilla Goblin Breach ends (needs prototype) | An hour later a purple bulb rises from the cooling crater | Hollow or Clank Well; strength scales with how long the breach stayed open and how many goblins died |
| **Coffer Echo** | Vanilla Veilbound Coffer reaches wave 3+ | The lid blasts open and a Well spins out | Temperament from the biome, strength from the wave reached |
| **Blood Tide** | Vanilla `Blood_Moon` | Every Dormant Well in loaded chunks flares to Wild; Tide and Hollow Wells form on water | A night of chaos: more Wells, but every guardian respawns |
| **Fallen Giant** | Death of a boss or hunt target (Gobliterator, Voidtaken Grizzly, Mossback Warden) | The corpse dissolves into a spiral of light | Roaring or Primal Well at the death spot; Temperament from the creature |
| **Wellwake** | Player field experiment (e.g. a Resonator next to a Flicker in the rain) | A light pillar, a sound sting, a guardian rises from the ground | Summon a Well of a chosen Temperament, at a cost |
| **Convergence** (T6) | Two Wild Wells' auras overlap | The sky changes colour above both | A timed event: capture the merged Well, split it, or let it burst |

The fun decision: players **farm events on purpose**. They plant Storm Vanes on a ridge before a storm, leave a breach open longer for a stronger Scar Bloom (while goblins pour out), or pick which lake to defend on a Blood Moon.

**Budget.** At most 1 Steady-or-stronger Wild Well per 512×512 region, 3 Roaring per world and 1 Primal per world at a time (server config). Captured or burst Primal Wells re-roll elsewhere after some real-time hours, so one player can't hoard the map.

### 4.6 The capture loop

1. **Scan** (Glimmer Lens, T3+). Hold the Lens on a Well. A **resonance dial** HUD (`CustomUIHud`) shows three rotating rings: Temperament, Strength and State. Lock each when it pulses. A perfect read also shows the Well's **Taste** (needed for a good seat) and gives bonus Insight. A sloppy read still works but hides the Taste. Your first scan of each Temperament is a Codex discovery with a big mote burst. (needs prototype: HUD timing latency)
2. **Calm.** Either beat the guardian encounter (full value), or smother the Well the goblin way: ring it with 3+ **Fizz Dampers** (rattling canisters spraying green foam). Smothering is safe and fast but drops the Well one strength band.
3. **Tether.** Place **Tether Stakes** (scrap posts and copper wire; from T5, floating **Tether Pylons**) around it: 3 for Flicker/Steady, 4 for Roaring, 6 for Primal. Beams link each stake to the Well. Uneven placement makes a stake rattle and lean, and you hammer it back. Once locked, the Well shrinks and spins faster. On servers, a Tethered Well is claimed by its tetherer.
4. **Choose: tap or jar.**
   - **Tap in place**: place a claimed **Wellhead** directly above a Tethered Well. Its input is horizontal and its output points up. Run a Sparkline or Spark Pylons home. A tapped Well also allows **catalysis** (§5.4). It's cheap and safe, but you're defending a remote outpost and long links lose output.
   - **Jar it**: bring it home for full output, feeding, merging and Tinkling cores. This is the risky option.
5. **Haul (the escort mission).** Jars by tier: **Gob-Jar** (T3: a cracked pickle jar in a scrap cage, holds Flicker/Steady), **Bell Jar** (T5, Roaring), **Ley Lantern** (T6, Primal). Pull the Well in with a few timing checks; miss one and it lashes out and resets that stake. While you carry it:
   - You walk slower, and you can't glide, Hookshot or ride Hurl Tubes until the Ley Lantern. (needs prototype: movement limits on a held item)
   - The Well calls its own kind and the Void. Riftmoths and Wellborn track you, and **Goblin Poachers** ambush jar carriers.
   - A **Stability** meter drains from hits, falls, weather the Well dislikes and time. At zero the jar bursts and the Well reappears Wild where you stand, with its guardians. That's not a total loss: you might have dragged it closer to home.
   - In co-op, one player carries and the others escort.
6. **Seat.** Build a **Well Cradle** at home (goblin: a scrap ring on stilts with a rope net; hybrid: a crystal cup with four pylons; arcane: a floating rune ring). Uncork the jar. The Well inspects its new home and scores the surroundings against its Taste, giving a **Harmony** rating from 1 to 5. Harmony sets its regen and whether it can mature. This is the base-layout puzzle: the Ember Well wants to sit by your Foundry, but the Tide Well next door hates that.

**Growing Wells.** **Feed** a seated Well its Tribute (Ember: coal and Boomshroom; Tide: fish; Growth: compost; Hollow: Essence of the Void and Voidhearts; Clank: Scrap) and it matures one band over time, if Harmony is 4+. Or **merge** two jarred Wells of the same Temperament in a Cradle: beams flash, the Wells orbit faster, waves of their Wellborn attack, and you keep the pylons charged. Succeed and you get one Well a band stronger. Fail and you get two Spent Flickers. This is the main road to player-made Primal Wells.

**Well Seeds.** Seated Wells slowly drop **Well Seeds** (a Temperament essence item). Seeds are Infusion catalysts (§8.5), Tinkling cores (§9) and a currency Grizzlecog buys. You don't have to capture a Well to get value from it: a Wild Well you leave alone keeps paying out Insight and Seeds every time it surges.

### 4.7 Pressure events (threats to Wells you already have)

- **The Hungry Dark.** A Hollow or Corrupted Well within ~96 blocks of a seated Well slowly draws Riftmoths to it. Riftmoths ignore players and go for the Well. It's a small base-defence wave run as an encounter, and it's why Coil Towers and Spires exist.
- **Poacher Raid.** A loud base (high Racket or Glimmer draw, §10.1) can attract a Goblin Poacher crew with a **Slurp Walker** that siphons your Well. Beat them for their jarred Flickers and Scrap. Lose and your Well drops a band. Only happens while the owner is online.
- **Temper backlash.** Overdraw a Well and it goes from Calm to Restless to Hungry and starts its own guardian mini-event. Tribute calms it.

### 4.8 Mobs

All mobs are NPC roles (`Server/NPC/Roles`), built as variants of vanilla templates where possible, with Blockbench models. Goblin and Void mobs reuse vanilla rigs and animations with new parts. Void creatures already fight goblins in vanilla, so we get three-way fights for free. Goblins flee from Hollow Wells, which is a nice tell.

**MVP** marks the 8 we build first.

| Mob | Faction / tier | Look and animation | Behaviour | Ties to Wells |
|---|---|---|---|---|
| **Glimmer Wisp** (MVP) | Ambient, T3+ | Palm-sized crystal with two ribbon tails, leaves light trails | Passive; flocks around Wild Wells, follows jar carriers | A wisp swarm on the horizon marks a Wild Well. Scanning one hints at the nearest Well's Temperament |
| **Sparkjaw** (MVP) | Wellborn (Spark), T3–4 | Lean lizard with a crystal crest that charges brighter | Stalks, charges, chain-lightning lunge that arcs to metal armour | Kill it while charged for a **Charged Crest** (Storm Vane upgrade) |
| **Cinderkin** (MVP) | Wellborn (Ember), T3–4 | Waddling coal-lump with a molten belly; burps flame | Swarms in threes, rolls into you, pops on death | Goblin shrines appear by Ember Wells: goblins worship them |
| **Brinewisp** | Wellborn (Tide), T4 | Translucent jellyfish shape | Pulls players toward the Well | Breaks your jar's Stability fastest |
| **Mossback Warden** | Wellborn (Growth), T5 | Huge shelled tortoise-golem with a meadow on its back | Tanky, roots players, heals allies; neutral unless you carry a Gnasher crate | Its death is a Fallen Giant event that births a Growth Well |
| **Shard Golem** (MVP) | Wellborn (Stone), T4–5 | Slow crystal-and-rock brute | Shields other Wellborn; hit its glowing joints for bonus damage | Guards Stone Wells |
| **Gale Shrike** | Wellborn (Gale), T5 | Kite-like crystal bird | Dives out of the sun, knocks players off ledges | Guards Gale Wells on peaks |
| **Junk Elemental** (MVP) | Wellborn (Clank), T2+ | Walking heap of scrap and pipes with a broken Boomshroom tank for a heart | Smashes machines; goblins nearby fight it | Spawns at Clank Wells, including the one your loud base grew. Drops bodge parts |
| **Goblin Poacher + Slurp Walker** (MVP) | Goblin, T2–5 | Goggled goblin with a backpack jar and siphon hose; the Walker is a wobbly two-legged scrap rig with a giant glass tank | Steals jars, siphons Wells (a visible beam fills the tank); break the tank to stop it | Main source of loose Flickers. Camps hold Notebook pages |
| **Riftmoth** (MVP) | Void, T3+ | Moth with torn ink wings and a glowing proboscis, flies in figure-eights | Ignores players unless hit; drains Wells with a purple thread | Main cause of Corruption |
| **Void Mite** | Void, T3+ | Little purple ticks | Latch onto *machines* and drain stored energy | The Void Bleed pest (§10.3) |
| **Hollowmaw** | Void, T6 | Floating mouth-orb with orbiting teeth (Void Eye rig); a starfield inside its jaw | Pulls players in, spits Void Larvae; shielded while Riftmoths live | Kill it to stop a Corrupted Well turning Void-touched |
| **Shardling** | Wild construct, T5 | Knee-high crystal-and-scrap golem that glows like a lantern | Neutral; piles loose items near Wells | Tame one with a Flicker for the Tinkling body blueprint |
| **Prism Warden** | Boss, T6 | 6-block crystal knight of shards held by light; takes its Well's colour | Phase fight using its Well's Temperament; reforms between phases | Guards Primal Wells; drops the Ley Lantern core |
| **The Unwoven** | Boss, T7 | A Prism Warden turned inside-out, with a torn starfield body | Multi-phase fight at your Veil Anchor; copies attacks in the last phase | Tier 7 Prove step; returns in Fraying events (§12) |

The Poacher drops an empty Gob-Jar (`Craftory_Gob_Jar`) that can catch Flickers and Wells. The filled Gob-Jar remains the metadata-bearing capture item, not a generic Poacher drop.

Cut from the drafts: Spark Wisp (merged into Glimmer Wisp and Sparkjaw), Glimmer Moth (merged into Glimmer Wisp), Hollow Echo (its attack-copying moves to The Unwoven), Cinder Choirling, Thornchord and Arc Skirmisher (replaced by the roster above).

---

## 5. Energy and generators

Principles: power comes from places; climbing tiers means *better* energy, not just more; early generators need a crank, a kick or a vent; faults are funny, not griefing.

### 5.1 Goblin era: Fizz (T1–2)

Fizz is fermented Boomshroom gas squeezed through scrap dynamos. It matches vanilla: the Goblin Lab's Boomshroom Generator powers the Tinkerer's Workbench.

**Fuel loop.** **Shroom Bed** (T1, a 3×3 mud plot under a scrap canopy) grows Boomshrooms in the dark. Light or footsteps make them twitchy and they can chain-pop (knockback only), so you choose between sparse safe beds and dense risky ones. **Mash Vat** turns Boomshroom and water into *Boom Mash*, a better fuel. Hold interact to stir it, or it sours to half value. The **Fizz Still** (§6.2) makes Boom Powder as a byproduct.

| Generator | Tier | How you work it | Look |
|---|---|---|---|
| **Crank-a-Fizz** | 1 | Hold interact to crank. Makes very little and can't be automated. Kick-starts cold machines and charges your first Canister | Hand crank with a squeaky spring and bouncing gauge |
| **Boomshroom Burner** | 1 | Burns shrooms, Mash, Boom Powder or charcoal. It coughs and stalls every few minutes; kick it to restart. A well-timed kick gives +10% for a minute | Tin drum on a squat scrap plinth; green smoke ring on each fuel pop |
| **Rattlewheel** | 1 | 2×2 wheel in flowing water, or sails on high ground. Output from height, flow and wind/storm weather | Mismatched wood-and-tin wheel; planks fly off in gales (cosmetic) |
| **Sealed Grizzlecog Generator** | 1 (traded) | Works out of the box but builds Jank; patch it with Scrap. Take it apart for the Burner page | Padlocks and "NO TOUCHY" signs |
| **Gutter Boiler** | 2 | 2×2×2 multi-block. Build the tank and whistle stack around the firebox, then kick-start it. Fuel the firebox, top up water, and watch the gauge. Above the red line, pull the vent chain (whistle and steam). Ignore it and it pops (knockback, water dumps, offline until re-riveted). Running near the red line gives bonus output. A firebox on lava needs no fuel | Firebox, riveted tank, brass whistle stack, cyan gauge |
| **Tar Still** | 2 | Pump in Tar, Poison or Slime. Poison gives the most Fizz but leaves Gunk to dig out | Copper pot still with dripping coils |
| **Breach Leech** | 2 | Place it during a vanilla Goblin Breach to siphon rift energy into Canisters. Fast Pressurised Fizz, but goblins target it (needs prototype) | Portable scrap leech with a hose into the portal |

### 5.2 Hybrid era: making Glimmer (T3–5)

There are three routes to Glimmer, and each one is a different decision:

1. **Refine Fizz (brute force).** A **Crackle Coil** (T3) bolts onto a Burner or Boiler and skims about 5% of the passing Fizz off as Dim Glimmer: your first Glimmer. A **Resonator** (T3) is a scrap frame with a seated crystal (vanilla Cyan or Void Crystal Shards). Tuning it sets the conversion rate (25–40%), and crystals cloud over and need replacing. A **Resonator Cascade** (T4) stacks up to three stages (Fizz to Dim to Bright) at about 60% loss per stage. It needs a huge Fizz base.
2. **Tap the environment.** Generators tied to weather, zones and time (table below). Each gives Glimmer with a Tint.
3. **Tap Wells.** A tapped or seated Well (§4.6) supplies up to its strength's grade, with its Temperament as Tint.

| Generator | Tier | World hook | Interaction / decision |
|---|---|---|---|
| **Storm Vane** | 3 | Storm weather (`Zone1_Storm`, `Zone2_Thunder_Storm`, …) | Spark Tint. A trickle in clear weather, huge in storms. Lightning overcharges its buffer, so pull the grounding lever or it locks out. Build it high and away from the base (needs prototype: strikes at a block; fallback is fake strikes) |
| **Void Scar Tap** | 3–4 | Void Tar or a Void scar | Hollow Tint. Good output, but now and then it stirs the Void (a small Void Larva event) |
| **Sunglass Array** | 4 | Daylight; `Zone2_Blazing_Light` bonus | Ember Tint. One master lens plus 4–12 satellites. You turn the master to follow the sun and the satellites copy it. It drifts a notch each in-game hour until Tinklings take over |
| **Moonwell** | 4 | Night, clear sky | Tide Tint. The night twin of the Sunglass Array: together they give 24-hour power |
| **Magma Resonator** | 4 | Lava pool under the footprint (`Zone4_Lava_Fields`) | Ember Tint. Steady and high, but needs a forward base or a long line home |
| **Voidheart Furnace** | 4 | Burns Voidhearts | A huge short burst of Bright Glimmer that also raises Void Bleed, so you might run it far from home on purpose |
| **Coreheart Engine** (T4 Heart) | 4–5 | The Gobliterator Core | 7×7×6. Assembly: scrap base, four coil masts, Core lifted in (Junkyard Crane), then a calibration ritual. Fizz in, Bright Glimmer out at base scale. Every in-game day it needs a **Harmonic Sync** timing check; skip it and output drops while Racket spikes. At T5 the clamps come off and it becomes the Resonant Heart |
| **Aurora Harp** | 5 | `Zone3_Northern_Lights` (Borea) | Gale Tint. A frame of crystal strings. Pluck them to tune chords during auroras for big boosts. It's an expedition build |

### 5.3 Tint (proposal)

Glimmer from a Well or environmental generator carries its Temperament as a **Tint**. Matching machines get a small bonus (an Ember-tinted Kiln smelts 15% faster). Mixed Tints on one network become **Muddled**: they still work, with no bonus. This is the reason to build several separate grids, and Prismatic depends on it. It costs one byte per network, recomputed only when the topology or a source changes.

### 5.4 Catalysis: the mid-game choice

The Wellhead demands 40 units per solve and emits at the dim-Glimmer rank, scaled by the Well's harmony and by catalysis (`delivered - ceil(delivered × lossPercent / 100)`, default loss 15%). Corruption halves its output. Output is zero if the Well is missing or no longer Tapped. Look after good Wells and upgrading is cheap. Skip them and you pay the Cascade tax. This is the key reason to go exploring and to hold remote Wellheads.

### 5.5 Arcane era: Radiant and Prismatic (T6–7)

| Generator | Tier | Hook | Interaction |
|---|---|---|---|
| **Ley Tap** | 6 | Depth | A floating bore of rune rings that sinks a light pillar; output rises with depth. Replaces the Magma Resonator: works anywhere, but costs a lot |
| **Sky Loom** | 6 | Current weather; needs a Gale/Spark Well | Weaves weather into Radiant Glimmer of the matching Tint (storm → Spark, snow → Gale, sun → Ember) |
| **Blood Moon Chalice** | 6 | `Blood_Moon` | Idle until a Blood Moon, then fills to bursting while mobs pour in. You defend it all night for the best burst in the game |
| **Well Spire** | 6–7 | A seated or tapped Well | Upgrades the Cradle or Wellhead: full grade, no temper drift while it's fed |
| **Prism Confluence** | 7 | 3+ Radiant inputs of different Tints | The main source of **Prismatic**. Ribbons of three to seven Tints spiral into a prism, and more distinct Tints means more output. It rewards the explorer, not the biggest single grid |
| **Veil Engine** | 7 | The tamed Veil Anchor (§12) | Enormous Prismatic output from a contained rift. It must be **re-sealed** on a timer by running a Void-surge event. Skip a seal and it shuts down safely and leaves a Void scar. The ultimate "power with a price" |

### 5.6 Storage, links and converters

| Store | Tier | Max grade | Look / mechanic |
|---|---|---|---|
| **Fizz Canister** | 1 | 0+ | Carried sloshing tank; powers Fizz tools; leaks 1%/min unless corked |
| **Boomshroom Battery** | 1–2 | 0+ | Stacked jars of glowing mash; a cracked jar leaks |
| **Gasbag** | 2 | 0+ | 2×2×3 multi-block: scrap frame at the base, patched-canvas envelope above. Build and kick-start it before it charges. Fire or arrows start a leak you patch. A goblin-base landmark |
| **Glimmer Capacitor** | 3–5 | 1–2 | Crystal in a brass cage; brightens as it fills, arcs when full |
| **Radiant Reservoir** | 6 | 3 | Floating crystal with rune rings that spin faster as it charges |
| **Prismatic Reservoir** | 7 | 4 | Rotating prism that splits light across nearby blocks |

A store holds one grade at a time and takes on the grade of whatever charges it until empty. That keeps the solver simple and the grade visible.

Transmission links are in §7.1. Converters are boundary nodes between sub-networks (ARCHITECTURE §7): Resonator, Cascade and catalysis step *up*. A **Dampener** steps *down* at about 5% loss (shedding sparks and warming an adjacent Kiln), so old Fizz machines can run off an arcane grid. A **Fizz Compressor** turns Fizz into Pressurised Fizz at 30% loss, so a Tinkered base doesn't need a Boiler.

**Faults** (`faults.blockDamage=false`, `faults.knockback=true` by default):

| Fault | Trigger | Effect | Recovery |
|---|---|---|---|
| Over-grade +1 | Grade above a device's `maxGrade` | Jank ×3, blue sparks, hum | Add a Dampener |
| Over-grade +2 | Badly wired grid | **Fizzle**: stall, spit contents, scorched block state | Hammer plus 2 Scrap Plating |
| Brownout | Demand > supply | Proportional slowdown; **Priority Valves** (T2) pick who starves | More generators or storage |
| Overpressure | Boiler above the red line | Whistle, then pop | Re-rivet |
| Overcharge | Ungrounded Storm Vane is struck | Arcs to metal, then locks out | Pull the grounding lever |

### 5.7 Unattended machine use

Craftory machines pause with vanilla `NonTicking` sections so unattended farms do not run indefinitely.
A fuelled Wakebell Anchor covers its chunk and eight neighbors, keeps those areas loaded and ticking, and
drains while no player tracks a covered section. Its charge is capped by `anchorMaxChargeMinutes` (240 by
default; 0 disables Anchors). Vanilla processing benches and item entities also tick when Hytale honors
the Anchor's section-ticking request.

### 5.8 Crews and the Tinker's Notebook

Crew membership is explicit and opt-in; otherwise each player has a solo crew. Crew knowledge is shared.
Joining merges the player's solo pages into the destination, while leaving or being kicked copies the
departing crew's knowledge into a fresh solo crew. The eight first-slice pages cover six machines and
two Grizzlecog jobs. A Cracked Tinker's Wrench trades an undiscovered machine for Scraps and its Notebook
page; after that page is known, the wrench packs the machine intact but loses stored energy. The delivery
receipt records its job page. Finding all eight pages advances the crew once to tier 2, Tinkered; tiers
do not gate other features yet.

---

## 6. Inventions and machines

### 6.1 The shared multi-block framework

- **Controller and shell.** One controller block holds the components (state machine, inventory, energy). Shell blocks have a tiny `ShellOf(controllerPos)` component, never tick, and forward interactions to the controller.
- **Ramshackle Assembly.** Place a frame item and it shows a **ghost frame** of missing parts (needs prototype: ghost rendering; cap of one preview per player). Hammer each part in with the **Tinker's Hammer**: clank, smoke puff, the next part highlights. Finish with a **kick-start** timing check that sets build quality (Wobbly, Sturdy or Masterwork, which adds small stat modifiers). Hybrid machines finish with **crystal seating** (tune it until it hums). Arcane machines are **conducted** as a ritual (§8.5).
- **Formation is event-driven.** A place, break or hammer hit inside a registered footprint triggers one check against that one template. Casings use Hytale's `Patterned` connected-block rules (as vanilla's goblin iron pipes do), so they join up with no custom code.
- **Breaking a shell** puts the structure into `Damaged`. It keeps inventory and upgrades but stalls until repaired.
- **Sprawl modules.** Machines have sockets where you bolt on modules (an extra jaw, a hopper, a second boiler, a muffler, a cooling fin). Machines grow outward instead of being replaced, so a maxed Gnasher looks like a junk-pile factory. Modules are data, so addons can add their own.
- **Bodging and Jank.** Upgrade slots take bodge parts: **Boom-Valve** (speed), **Gutter Gear** (efficiency), **Scrap Plating** (durability), **Squawk Box** (alerts), **Muffler** (less Racket). Running hard builds Jank (rattles, sparks, smoke), and high Jank risks a comic fizzle. To go up a tier, install the right parts and run the machine hot until it **settles** into the next tier. It's a risk-and-reward ritual, not a recipe.
- **Crate & Carry.** Charge the Tinker's Wrench on a controller to pack the whole multi-block into a **Bodge Crate** that keeps quality, modules and contents. Moving and rebuilding in place is painless.
- **Punch Cards** (T4). Goblin-style logic punched at a **Card Punch** and slotted into machines and claws: "stop when output full", "only run in storms", "pulse every 10 s", "run when Racket is low". At T6 they become floating **Rune Glyphs** with the same logic.
- **Footprint cap:** 7×7 in plan, except the Orrery and Riftloom (open decision).

**Implemented in Slice 2b.** The Junk Heart is the first multi-block. Controller `Craftory_Junk_Heart_Firebox`, eight `Craftory_Scrap_Plating` blocks around it at y=0, and a two-block `Build_GreyDark_Pipe` chimney at (0,1,0) and (0,2,0). Shells have no `ShellOf` component (the derived `MultiblockIndex` maps them to the controller instead), so vanilla blocks can be parts. The ghost frame is a `PrefabPreview` entity listing the missing parts plus a `Dust_Sparkles_Fine` particle at each missing part every 20 ticks (Core config `GhostMode` = `preview`, `particles` or `both`, default `both`). Both appear only while a player is within 16 blocks, and each player only drives their nearest frame. The preview entity is visible to everyone nearby and is not yet verified in a real client. The Tinker's Hammer (Secondary interaction) debits the next part's item and places it, lowest y first; with no item it says "Needs: <part>". A complete frame is `READY_TO_START` and shows a chat gauge with a 3 s cycle whose sweet spot is the 1.5 s mark; a kick within 0.15 s is Masterwork (×1.15 output), within 0.5 s Sturdy (×1.0), otherwise Wobbly (×0.9). The formed Heart burns burner fuel from its own slot for 4× burner Fizz times quality, and the placer's crew learns `craftory:heart/junk`, which tier 2 now needs alongside all eight pages. Machine pages unlock workbench recipes for the six single-block machines (crafting is cancelled through `CraftRecipeEvent.Pre` for crews without the page).

**Interaction verbs and their Hytale basis**

| Verb | Era | Feel | Basis |
|---|---|---|---|
| Crank | Goblin | Hold Use; the handle spins and the gauge climbs | `Charging` hold interaction (documented); server clamps charge time |
| Kick / bash | Goblin | Hit at the right moment | Hit interaction plus a server timing window (needs prototype on custom blocks) |
| Lever / valve | All | Flip modes | Block-state `ChangeState` |
| Tune | Hybrid | Rotate a crystal until the hum locks | Use steps on a rotation state; data-driven pitch and glow |
| Aim | Hybrid | Point an emitter | One raycast on use, saved |
| Conduct | Arcane | Hit runes as they flare | Custom UI page; in-world rune entities (needs prototype) |

**Crystal drift** (hybrid): tuned crystals drift out of tune, faster in storms and near breaches. Detuned machines arc and waste energy. A Tender Tinkling re-tunes them. Drift is a stored timestamp plus a rate, worked out only when someone looks.

### 6.2 Machine roster by era

| Job (old Craftory) | Goblin (T1–2) | Hybrid (T3–5) | Arcane (T6–7) |
|---|---|---|---|
| Ore doubling | **Gnasher Pit**: 3×3×2 pit with a jaw. Drop ore in and crank; overfill and it jams, so kick it. Modules: Fizz Motor (automates the crank), extra jaws (faster, louder), Sieve (gravel to Scrap or Void shards) | **Prism Grinder** (tripling): ore floats in a glass drum and shatters in light. Tune Low (more dust), Mid (byproduct gems) or High (crystal shards for research) | **Unmaking Circle**: 5×5 floor rune. Walk the rim clockwise (or have a Tinkling do it) and items dissolve into motes and reform. Doing it yourself is faster and better quality |
| Smelting | **Scorch Oven** | **Glimmer Kiln** (Ember Tint bonus) | **Ember Altar** |
| Alloys / foundry | **Bellows Foundry**: pump the bellows to keep the heat needle green. Too hot makes Slag, too cold stalls. A Boom-Valve automates it but drifts. T2 adds a second crucible | **Arc Foundry**: aim two electrode arms over a crucible. A closer gap is hotter but jankier; storms add heat | Infusion Circle (§8.5) |
| Fuel | **Fizz Still**: turn the strength valve. Low gives fast, weak Fizz; high gives rich Fizz plus Boom Powder | — | — |
| Magnetiser / imprint | **Sparkcoil** | **Imprinter**: a beam between two tuned crystals imprints a grade onto a part (input for Resonators, Coil Towers and Tinkling cores) | Rune Ring imprinting |
| Place / break | **Plonker / Thumper** | **Spark Arm** | Tinkling Miner/Builder sigils |
| Crafting | Tinker's Workbench (vanilla) + Ramshackle Assembly | **Resonance Forge** (§8.5) | **Infusion Circle** |

**How the Gnasher changes across eras** (same ~3×3 footprint; you upgrade in place):

| Tier | Look | Interaction |
|---|---|---|
| T1 | Tin pit, one rusty jaw, hand crank | Crank; kick the jams |
| T2 | Extra jaws, Fizz Motor, smokestack, daub-tagged outputs | Speed valve; jams less often |
| T3–4 | Jaws replaced by a glass drum, crystal on a Tuning Mast | Tune for the output mix |
| T5 | Crystal dominant, scrap thinned to scaffolding | A Tender Tinkling keeps it tuned |
| T6–7 | No machine, just a floor rune (Unmaking Circle) | Walk it or conduct it; Rune Rings add speed |

### 6.3 Defence

| Goblin | Hybrid | Arcane |
|---|---|---|
| **Bangstick Turret** (T1): a scrap tripod lobbing vanilla Bangsticks, with a seat so you can man it | **Coil Tower** (T3): stack up to 6 coils for range and chain jumps; rings light up as it charges; storm bonus | **Glimmer Spire** (T6): floating crystal, focused beams, threat-based targeting |
| **Spring Snapper** (T2): a floor plate that flings attackers; reset by cranking | **Refractor Pane** (T5): crystal panels that bounce projectiles back; repair cracks with shards | **Ward Spire** dome (§8.6) (needs prototype: projectile interception) |
| | **Ward Lantern** (T4): small radius, drains Instability, keeps Void Mites out | |
| | **Void Lure** (T5): an opt-in bait totem that starts a Void surge at your base for drops and Insight | |

### 6.4 Showpieces (one per era)

- **Junkyard Crane** (goblin, T2): a 5×5 base with a rotating lattice boom up to 12 tall, built in four stages (base, mast, boom, cab). Two cab levers (swing and hoist) lift Bodge Crates, tanks and the Gobliterator Core. A magnet head pulls dropped Scrap into a hopper.
- **Coreheart Engine** (hybrid, T4–5): see §5.2. It's also the Tier 4 Heart.
- **The Orrery** (arcane, T7): a five-storey tower; each storey is a rune ring you conduct into place. It's a turning model of the region, with active world events, breaches and Wells shown as motes. With a long cooldown it can **nudge the local weather** (call a storm for the Vanes, clear skies for the Arrays). It reads the event registry on use. (needs prototype: weather override; fallback is a local cosmetic effect plus a forecast nudge)

---

## 7. Logistics and transport

All transport is visible. Items are data moving along segments, and only display entities near players are rendered, with a cap per chunk (ARCHITECTURE §7).

### 7.1 Pipes, links and item handling

| Tier | Link | Carries | Max grade / loss | Look and twist |
|---|---|---|---|---|
| 1–2 | **Gutterpipe** | Items, fluids, Fizz | 0+, 2% per joint | Tin and brass with glass portholes; items rattle past, fluid sloshes. Leaky joints drip (puddles sprout Boomshrooms). Scrap Plating seals a joint, so long straight runs pay off |
| 2 | **Snatcher Claw** | Insert/extract | — | A spring grabber arm that visibly swings each item. Set its filter by holding the item up to it |
| 2 | **Chuck Chute** | Item stacks | — | A spring catapult that throws stacks up to 24 blocks to a **Catch Basket**, over ravines and walls. Strong wind can make it miss and scatter the stack: cheap, but unreliable |
| 2 | **Daub Sorting** | Routing | — | No filter GUI. Slap a paint daub on a junction, and items stamped that colour by a Claw take that branch |
| 2 | **Sloshtank** | Fluids | — | Stackable rings that merge into one tall tank with a float needle |
| 3–4 | **Sparkline** | + Glimmer, faster items | 2, 0.5%/segment | Copper wrapped around crystal; sparks run along it under load; items glow as they pass |
| 4 | **Spark Pylon** | Energy beam | 2, flat 5% | 32-block line-of-sight relay (Craftory Tech's power beams return). LOS is checked on link and on block changes in the path |
| 4 | **Sorting Prism** | Routing | — | Items refract out of different faces along coloured beams, by tag (5 outputs) |
| 5 | **Glimmer Conduit** | Everything, filtered | 2, ~0 | Crystal tube with a visible light stream |
| 6 | **Mote Couriers** | Short-range items | — | Swarms of light motes carry items between **Mote Wells** |
| 6–7 | **Ley Channel / Ley Node** | Everything | 4, ~0 | A floating light ribbon between rune nodes; long-range power lines |

### 7.2 Moving players

| Tier | System | Ride |
|---|---|---|
| 2 | **Hurl Cannon + Hurl Tube + Catch Net** | Load a Boom Powder charge, climb in, *boom*. Smoke puffs from the joints as you rocket through. A janky tube can pop an ejector hatch and drop you out mid-ride; patch it with Scrap |
| 2 | **Sky Hurl** | An open-ended cannon that fires you into the sky and opens your vanilla Scrap Glider |
| 2 | **Anchor Mortar** | Fires vanilla Hook Anchors into far cliffs for your Hookshot |
| 2 | **Gobbler** | A rideable 3×3 tunnel-borer with Scrap Drill heads that dumps spoil into a towed **Scrap Barrow**. Burns Fizz and Jank fast (needs prototype: rideable block-breaker) |
| 3–5 | **Sparkline Tubes** | Faster and crystal-lit. **Booster Rings**: press Jump as you pass for a burst. **Switch Junctions**: look left or right to pick the branch |
| 4 | **Skyline** | Pylons with a crystal cable; hook on with the Hookshot and zip one way |
| 4 | **Updraft Vent** | A grate blowing a glowing updraft; chain them to glide across the base |
| 6 | **Ley Streams** | No tube: step into a ribbon of light and glide, steering gently inside a corridor. The Ley Lantern lets you carry a jarred Well on one |
| 6 | **Skyward Disc** | A floating disc that hovers 1–3 blocks above terrain on a carried Reservoir (needs prototype: flying mount) |
| 7 | **Rift Gates** | Paired standing rings that open as a portal for a few seconds, paid in Prismatic per use. Tubes stay cheaper for the daily commute |

Implementation: each rider is mounted (`MountedComponent`, minecart-style controller) on one invisible capsule entity that follows a spline. The spline is baked only when the tube network changes. (needs prototype: fast mount movement on custom paths, camera smoothness)

---

## 8. Research and magic

### 8.1 Research modes by era

| Tier | How you learn | Codex look |
|---|---|---|
| 1–2 | **Tinker's Notebook**: take sealed machines apart with the Wrench (destroys them, gives a page) and do Grizzlecog's jobs (bring Boomshroom, defend the Lab from a Void surge, recover a Deepworks blueprint) | A grease-stained goblin notebook on blueprint paper |
| 3–5 | **Glimmer Lens** scans, **Field Experiments**, Well hunts | Vessa's annotated folio; cyan crystal stains spread across the paper |
| 6–7 | Convergences, Primal Wells, Void taming | The paper burns away into constellations |

### 8.2 Glimmer Lens and Insight

The **Squint Goggles** (T1: energy and Jank HUD) become the **Glimmer Lens** at T3: a brass spyglass with a cracked crystal and taped wires. Hold it on a target: a ring fills around the reticle, then motes burst out and spiral into your chest with a chime. Your first scan of each target type gives full Insight; repeats give a little.

- **Scannable things:** crystals, ores, Wells, mobs (vanilla and ours), the sky during storms, breaches, Void scars, ruins and Runestone sites, and **your own machines while they run** (a high-Jank Tinkered machine gives Clank Insight).
- **Insight** comes in the 8 Temperament themes, shown as coloured orbs on a small HUD meter.
- **Lens modes:** *Trace* (a faint line toward the nearest known Well of a chosen Temperament), *Read* (machine grade and Jank overlays), and at T6 the **Truesight Circlet** (shows ward lines and hidden Void Mites).

### 8.3 Field Experiments (the main gate)

A Codex card with a world goal, checked by an event that happens anyway (machine start, lightning, item placed, encounter signal). We never poll for "is it set up yet". Success plays a sound sting and a light pillar, motes rush into you, and the Codex page draws itself in ink.

| Experiment | Setup | Showy result | Unlocks |
|---|---|---|---|
| **First Spark** | A running Tinkered machine within 8 blocks of a breach or Void crystal | The crystal flickers and arcs to the machine, and Vessa appears | Sparkbound Keystone |
| **Bottled Lightning** | A Storm Vane on a peak gets struck | Bolt, and the vane glows for 10 s | Glimmer Capacitor, Sparkline |
| **Breach Echo** | Run a Resonator within 20 blocks of an open breach | The portal flickers cyan and Void Mites leak out | Hollow Insight, Dampener |
| **Heart of the Machine** | The Gobliterator Core on an Imprinter, fed Bright Glimmer | The Core reassembles as a hologram, then cracks | Glimmerforged Keystone, Coreheart Engine |
| **Three-Point Resonance** | Three different Wells seated in a triangle; stand in the middle at noon | A light pillar seen from far away | Resonance Forge (Resonant Keystone) |
| **Wellwake** | A Resonator next to a Flicker in the rain (or other Temperament recipes) | A guardian rises and a Well is born | Summoning Wells |
| **Hollow Silence** | Hold a Void-touched Well stable with a lit Ward Lantern | The Well goes quiet and the sky clears | Ascendant Keystone |

**Tinker's Puzzle Board** (proposal: optional skip). A goblin corkboard with string and pins (a floating rune slate later). Spend Insight on a short link-the-themes puzzle to skip an experiment, at about 3× the Insight the field version would give. It's for players who'd rather not fight a Roaring Well. It replaces the hex puzzle as the main gate.

### 8.4 The Codex map

`InteractiveCustomUIPage`. A map drawn on blueprint paper with the seven Keystone bands. Nodes are machines, tools and tiers. Locked nodes are smudged. Experiment cards are pinned with hints ("Find somewhere the sky is angry"). The **Well log** pins every Well you've scanned at its real map position, with its Temperament icon and the last strength seen. The era shift (crystal stains, then constellations) is one animation that tells you the mod has changed. Progress is a player component, with an optional **Shared Notes** team setting.

### 8.5 Crafting magic: Forge to Circle

- **Resonance Forge** (T5): a central Anvil Crucible plus up to 6 Prong Pedestals. Arcs jump from each pedestal to the crucible. A world-space resonance dial drifts, and you keep it green by **striking the anvil in rhythm** with the Tinker's Hammer. Doing well is faster, with a chance of a Masterwork result. Doing badly raises Instability (§10.2). A Tender Tinkling can strike for you, at Sturdy quality only.
- **Infusion Circle** (T6–7): the Forge loses its metal. The anvil becomes a floating crystal and the pedestals become rune stones; items lift and orbit, and the sky dims. You **conduct**: aim your Gauntlet at the pedestal that's pulsing. A miss spills Instability. **Catalysts:** a Well Seed in the ring changes the result (Spark: faster tool; Hollow: lifesteal, but more Instability), so the Wells you captured matter.
- **Rune Rings** (T6): instead of bodge parts, throw a ring rune at a machine and it joins its orbit. Ring count shows tier; ring speed shows load (idle rings drift, overloaded ones wobble and shed sparks).
- **The merge, shown:** every infusion upgrade shows the old scrap parts flying off the model and being replaced with crystal or rune parts.

### 8.6 Gauntlet, foci and wards

The **Tinker's Wrench** gets a crystal socket at T3 and becomes the **Glimmer Gauntlet**. The wrench shrinks each tier until, at T6, it's a glowing glove with a floating rune ring. **Foci** are crystal lenses that clip into the socket and run on Glimmer from a carried Capacitor (not mana).

| Focus | Tier | Use |
|---|---|---|
| **Zap** | 3 | Short arc, chains through wet targets |
| **Tug** | 4 | Pulls items and mobs (item magnet) |
| **Shaper** | 5 | Swaps a 3×3 of blocks for blocks from your inventory |
| **Excavation** | 5 | Beam-mines a line of blocks (replaces the Fizz Drill) |
| **Warding** | 6 | Temporary hex ward walls |
| **Blink** | 7 | Short teleport along a ley line or to a seated Well |

Also: **Glimmer Pick / Spark Saw** (hybrid tools whose crystal blades glow by grade) and the **Tinker's Satchel** (T5, linked to Courier Tinklings).

**Runestone tie-in** (proposal, needs prototype): a T6 **Runebinder Adapter** lets vanilla Modifier Runestones (e.g. Quickening, Thrift) modify a focus. How deep this goes depends on how much of the Runebinder Lattice is exposed to plugins.

**Wards**: Bangstick → Coil Tower → **Ward Spire** (T6), a rune pillar that projects a hex dome. Its modes: repel hostile mobs, no weather inside, suppress rifts, and **Hush** (mutes your machines and cuts Racket). Under attack the dome cracks hex by hex; re-conduct its ritual to repair it. **Ward Lines** are glowing fences drawn between Spires with the Gauntlet. Each ward is a radius and a rule stored in a small spatial index. Mobs check it only when they pick a target or spawn. (needs prototype: target and spawn hooks)

---

## 9. Tinklings

Half-block golems of scrap and crystal, and the Resonant-tier payoff. Built at the Resonance Forge (later the Circle) from **Body + Core + Sigil**. You unlock the first body blueprint by taming a **Shardling** with a Flicker. Snikka runs the Tinkling workshop.

- **Body:** Scrap (T5: clunky, clanks, sometimes trips), Crystal (T6: smooth, hovers a little), Prismatic (T7: shifts colour). The body sets speed, carry and range.
- **Core:** a Well Seed. Its Temperament gives a personality perk: Spark is fast, Stone carries more, Growth repairs itself, Clank fixes Jank, Tide is a better pump tender, Ember never needs a charge in the heat, Gale can ride Hurl Tubes with you, Hollow can enter Void rifts (needed for some T7 research).
- **Job Sigils** are carved tiles placed in the world. A bound Tinkling works inside the sigil's glowing area.

| Sigil | Job | Fun bit |
|---|---|---|
| **Courier** | Move items between linked containers | Carries a comically large crate on its head |
| **Harvester** | Harvest and replant | Sings little chimes |
| **Miner** | Dig a marked volume into a chest | Tiny helmet with a crystal lamp |
| **Tender** | Strike forge anvils, re-tune crystals, clear Jank, refuel generators, aim Sunglass Arrays | Carries a wrench bigger than itself |
| **Guard** | Attack Void Mites and wisps in its area | Zaps with a tiny Zap Focus |
| **Scout** (T6) | Walk to the nearest known Well and report its strength | Comes back sooty and excited |

Filter and Link sigils attach to a job sigil like bodge parts. Animations show state: working, sleeping (curled up with Zzz motes), blocked (confused sparks), and low Glimmer (dimming, trudging to a **Charging Plinth**).

Limits: soft cap of 12 per player (server config). Built on NPC roles with simple states. A Courier only reads its linked containers, every 20+ ticks. With no players nearby, Tinklings go **abstract**: the sigil resolves the job as a data transfer with no pathing, and the entity reappears when someone comes back. (needs prototype: custom NPC actions for container transfer; AI bound to our sigil blocks)

---

## 10. Risk systems

Four meters, each tracked as a single number where the engine already has one. None of them destroys blocks by default.

### 10.1 Racket (per base region)

Goblin machines are loud. Every machine has a Racket value: high with speed bodges, low with Mufflers. Glimmer draw from seated Wells adds to it. A base's total Racket raises the odds that world events target it: goblin scavenger raids and **Poacher Raids** at medium Racket, Void surges at high Racket. A base that stays loud for days grows its own **Clank Well**. **The decision:** run loud and fast and get raided more (raids also drop Scrap), or invest in Mufflers, Dampeners and later the Ward Spire's Hush. Racket is a per-region running total, updated when a machine changes state.

### 10.2 Jank and Instability (per machine / per crafting site)

**Jank** (§6.1) is the goblin-era risk: rattles, then fizzles. **Instability** is the magic-era risk on each Forge or Circle, from 0 to 100:

| Level | Effect |
|---|---|
| 0–30 | Pretty sparks |
| 30–60 | Flux pockets: visual glitches; items in nearby pipes slow down |
| 60–85 | Wisp leak: 1–3 Void Mites spawn and target the *machine* |
| 85+ | Backlash: the craft fails and reagents scatter on the ground (never deleted); Instability resets to 40 |

Instability drains over time, faster with Ward Lanterns or a Tender Tinkling.

### 10.3 Void Bleed (per energy network)

Hollow Tint, Voidheart Furnaces, Hollow catalysts and high-grade energy through cheap links add **Void Bleed** to a network. It shows as purple cracks on conduits and a desaturated look nearby. High Bleed has a chance (checked every few minutes) to open a **Void Rift**: a small Hollow event at your base with Void Mites and Riftmoths. Clearing it gives Hollow Insight, so a base that runs dirty becomes its own research site. Rifts spawn **only inside the owner's claim**, and their mobs only stall and drain the owner's machines.

### 10.4 Well risks

Corruption, Void-touched Wells, burst jars, Spent Wells and temper backlash (§4). Every one of them is recoverable.

**Server-safety rules:** `risk.blockDamage=false` (default); `risk.maxConcurrentRifts`; per-player rift cooldowns; risk mobs despawn when no member of the owner team is in range; no effect crosses into another claim; overloads and backlash can never be used as a PvP weapon.

---

## 11. Multiplayer

- **Wells are shared world objects.** Anyone can find, scan and fight a Wild Well. Once Tethered, it belongs to the tetherer (or their team). Primal Wells are server landmarks with a map marker and a re-roll timer.
- **Research is per player**, with optional **Shared Notes** for teams. Field experiment progress counts for every team member who is present.
- **Team Heart.** A team registers one Heart. All members get its aura and share its Codex band, but each Keystone's **Prove** step needs at least two members present, so the key moments are group moments. Every duo requirement has a **solo fallback** with a Tinkling standing in (slower and weaker).
- **Co-op roles** (Codex branches, never forced):

| Role | Focus | Perk |
|---|---|---|
| **Tinker** | Building, bodging, Jank | Masterwork chance on kick-start; can repair other players' fizzled machines |
| **Lensreader** | Scanning, research, capture | Double scan range; wider timing windows on the resonance dial and jar pulls |
| **Warden** | Defence, events, bosses | Coil Tower and Spire buffs; taunts Wellborn off the jar carrier |
| **Courier** | Pipes, tubes, Tinklings, trade | Bigger Tinkling squads; better Grizzlecog prices; slower Stability drain when carrying jars |

- **Group content:** jar escorts, the merge ritual (one conducts, the others defend), Blood Moon Chalice nights, Prism Warden and Unwoven fights, and duo experiments (two players scanning one Well from opposite pylons).
- **Fairness:** Poacher Raids and Racket raids only happen while the owner is online. Bursts and rift scars only land in unclaimed land or the owner's own claim (needs prototype: claim system hook).

---

## 12. Endgame and the new-dimension hook

### 12.1 Taming the Void (the Ascendant Keystone)

1. **Learn:** *Hollow Silence*. Hold a Void-touched Well stable with a lit Ward Lantern.
2. **Prove:** turn that Well into a **Veil Anchor** with 4 Ward Spires and the Arcane Heart, put it in a Ley Lantern Cradle, and survive **The Unwoven**: a multi-phase boss that uses each Temperament you've seated against you, then copies your attacks.
3. **Build:** the **Prismatic Heart** from the *Unwoven Heartstring*.

The tamed Anchor can then power a **Veil Engine** (§5.5) and host the **Riftloom**.

### 12.2 The Hollowmere

The **Riftloom** (5×5×7, floating rune rings around the Veil Anchor) weaves the torn edge into a stable pocket plane: **the Hollowmere**. It's a separate world per team (needs prototype: per-team world creation and persistent portals. The Goblin Breach's instanced fragments suggest it's possible). It starts as one floating island of crystal and scrap in a starlit void.

- **Grow it with Wells.** Each Well you've captured can be **planted** in the Hollowmere, which grows an island of its Temperament (Ember isle, Tide isle, Clank junkyard isle…). Dual Wells grow mixed islands. Your realm is a record of which Wells you chose to capture rather than let burst.
- **Why live there:** Prismatic flows naturally, so endgame machines run best there. Ley Streams link the islands. Grizzlecog opens a "New market" with rare trades for Well Seeds.
- **Repeatable threat:** **Fraying** events. The Unwoven return, the realm's edges unravel, and you defend Riftloom anchors to reweave them. Each won Fraying adds a little realm space.
- **Expansion surface:** addons can add island types, Wellborn variants, Fraying bosses and new Temperaments without touching the core ladder. This is the "whole new dimension" the mod can keep growing into.
- **Fallback** if per-team worlds aren't possible: the Hollowmere becomes a bounded sky region above the Veil Anchor in the main world, with the same island-planting rules.

Other endgame goals: player-made Primal Wells through merging, a Prism Confluence with all 8 Tints, the Orrery, and Rift Gate networks.

---

## 13. Old Craftory feature map

| Craftory Tech feature | Status | Goblin | Hybrid | Arcane |
|---|---|---|---|---|
| Energy cells | Kept, re-themed | Fizz Canister, Boomshroom Battery, Gasbag | Glimmer Capacitor | Radiant / Prismatic Reservoir |
| Solid fuel generator | Kept, now hands-on | Boomshroom Burner (kick it) | Voidheart Furnace | — (cut in arcane) |
| Geothermal | Kept | Gutter Boiler on lava | Magma Resonator | Ley Tap |
| Rotary (wind/water) | Kept, weather-linked | Rattlewheel | Storm Vane | Sky Loom |
| Solar | Kept, now aimed | — | Sunglass Array and Moonwell | Well Spire / Confluence |
| Electric furnace | Kept | Scorch Oven | Glimmer Kiln | Ember Altar |
| Macerator (doubling) | Changed: you choose outputs | Gnasher Pit | Prism Grinder (tripling + choice) | Unmaking Circle |
| Foundry | Changed: hands-on | Bellows Foundry (renamed from Bodge Foundry) | Arc Foundry | Infusion Circle |
| Magnetiser / table | Changed into grade imprinting | Sparkcoil | Imprinter | Rune Ring imprinting |
| Block placer / breaker | Kept | Plonker / Thumper | Spark Arm | Tinkling Miner/Builder |
| Power beam connectors | Kept | Gutterpipe | Spark Pylon | Ley Node |
| Fluids | Kept, visible | Gutterpipe, Sloshtank | Glimmer Conduit | Ley Channel |
| Item pipes | Kept, visible | Gutterpipe, Snatcher Claw, Daub Sorting, Chuck Chute | Sorting Prism, Glimmer Conduit | Mote Couriers, Ley Channel |
| Powered tools | Changed | Fizz Drill/Saw (next to vanilla Scrap Drill) | Glimmer Pick, Spark Saw | Gauntlet foci |
| Turrets | Kept, grows into wards | Bangstick Turret, Spring Snapper | Coil Tower, Refractor Pane | Glimmer Spire, Ward Spire |
| Wrench / goggles | Kept, grows | Tinker's Wrench, Squint Goggles | Glimmer Gauntlet, Glimmer Lens | Gauntlet + Truesight Circlet |
| Crafted-at-bench machines | Changed | Ramshackle Assembly | Crystal seating | Conducted rituals |
| Flat upgrade tiers | Changed | Bodging and settling | Tuning | Rune Rings |
| **New:** Wells, Tint, catalysis, Hurl Tubes, Racket, Sprawl, Crate & Carry, Punch Cards, Tinklings, Hollowmere | New | | | |
| Hex research puzzle (old draft) | Cut as the main gate | Kept only as the optional Puzzle Board | | |
| Thaumcraft-style spreading taint | Cut | Replaced by local, creature-driven Corruption and claim-bound rifts | | |
| Wellspring Cuttings (energy draft) | Cut | Replaced by jarring and seating | | |
| Resonance Engine (machines draft) | Merged | Into the Coreheart Engine | | |

---

## 14. Art and audio direction

Style targets: Hytale's chunky, readable voxel look; Update 7's scrappy goblin tech; and the TV series *Arcane*'s painterly, high-contrast glow. Every machine has a Blockbench model **per era**, not a recolour. Animations are block states and model animations; effects are data on the effect bus.

| | Goblin (T1–2) | Hybrid (T3–5) | Arcane (T6–7) |
|---|---|---|---|
| **Shapes** | Riveted tin, corrugated sheets, rope lashings, mismatched bolts, chicken-leg stands; everything slightly crooked | The same scrap cradling cracked crystals; taped copper coils; tidier and more symmetrical each tier | Almost no metal: floating lattices, orbiting rune rings and motes, rings instead of casings |
| **Palette** | Rust, brass, soot, toxic Fizz green | Brass plus cyan and magenta glow; Tint colours show on lines | Deep night blues with Tint ribbons; Prismatic colour shifts |
| **Motion** | Wobble, shake, puffing smoke rings, bouncing needles | Steady hum, arcs that jump, crystals pulsing with load, light bleeding through gaps | Slow orbits, breathing light, ribbons that ripple like cloth |
| **Audio** | Clanks, burps, squeaky springs, steam whistles, goblin muttering | Rising hum, electric crackle, the "lock" chime when tuning, glassy resonance | Choir-like pads, chimes in key with the Well's Temperament, deep Void drones |
| **Music cues** | Upbeat junk-percussion during Scrap Rush and Poacher fights | Strings plus electric pulses for Well captures | Choral swells for rituals; dissonant drones for the Unwoven |
| **Mobs** | Vanilla goblin rigs with new gear | Crystal-and-creature hybrids (Sparkjaw, Cinderkin) | Light-and-shard constructs (Prism Warden) |

**Readability rules:** a machine's grade reads from 20 blocks away by its glow colour; Jank reads by smoke colour (white, grey, black); a Well's Temperament reads by colour and motion before any scan. Hearts are the tallest thing in a base on purpose.

---

## 15. Performance notes for heavy ideas

| Heavy idea | How it stays cheap |
|---|---|
| Wells everywhere | Wells are created by events and stored in a per-world `WellRegistry` (a spatial hash by region). Nothing searches the world. Dozens per world, not thousands. Per-region and per-world budgets (§4.5) |
| Birth events | One roll per loaded player region every few minutes, plus hooks on vanilla event endings (breaches are already rare). No per-block checks |
| Guardians and bosses | Encounter Manager spawn markers trigger only when the Player sensor fires in range. No players, no AI. Guardians despawn with the chunk |
| Well state (corruption, maturing, regen) | Counters in a slow bucket (~5 s, staggered), loaded Wells only. Unloaded Wells catch up on load with elapsed time × rate |
| Taste and Harmony | Computed when seated, and again only on block changes inside the Cradle's small radius |
| Weather and time generators | Zone and biome read once at placement. Weather cached and refreshed on the in-game hour (when vanilla rolls the forecast). Clock read once per 20-tick bucket |
| Multi-block formation and environment checks | Event-driven against one template; shells never tick; lava, water and height checked only on assembly and on block changes inside the footprint |
| Tint, leak totals, max grade, Void Bleed | Cached per network and recomputed on topology change only. Per-tick solve stays the linear array pass (ARCHITECTURE §5) |
| Visible items, Chuck Chute arcs | Items are data along segments; only display entities near players render, capped per chunk. One display entity per throw, landing resolved on a timer, wind read at launch |
| Hurl Tubes and Ley Streams | One mounted capsule entity per rider on a pre-baked spline, rebuilt only when the network changes |
| Racket and raid targeting | A per-region running total updated when a machine changes state; read only when the event director runs |
| Jar carrying | One player component in a 20-tick bucket (Stability drain and ambush roll) |
| Turrets and Spires | Range query only when charged, on the tower's bucket (e.g. every 10 ticks), with a target cap |
| Wards | Radius-and-rule entries in a spatial index; checked when a mob picks a target or spawns, never per tick |
| Tinklings | Soft cap per player; simple role states; sigil-linked containers only; abstract data transfers when no players are near |
| Crystal drift | Timestamp plus rate, evaluated lazily |
| Rituals and orbiting items | Client-side model and particle effects on one entity; only the controller ticks, and only while crafting |
| Orrery | Reads the event registry and WellRegistry on use; never scans terrain |
| Hollowmere | Islands are prefab-pasted once when planted; the realm unloads when no team member is inside; Fraying runs only while players are present |
| Effects in general | Throttled per player on the effect-event bus |

Every heavy system gets a counter on `/craftory stress` and a tick-time metric before it ships.

---

## 16. Suggested build order (vertical slices)

Each slice is playable end to end and builds on ARCHITECTURE §8 milestones 0–2 (repo, engine, core integration).

| # | Slice | Contents | Proves |
|---|---|---|---|
| 1 | **Junk power** | Crank-a-Fizz, Boomshroom Burner (kick), Fizz Canister, Gutterpipe energy, Gnasher Pit (crank, jam), Scorch Oven, Squint Goggles, Grizzlecog + barter shop with sealed machines | Graded network, hold and hit interactions on custom blocks, the barter JSON |
| 2 | **Bodge it yourself** | Tinker's Notebook, Wrench take-apart, Ramshackle Assembly with ghost frame and kick-start, bodge slots and Jank, Junk Heart, Scrap Rush | Multi-block framework, ghost rendering, encounters at a base |
| 3 | **Visible goblin logistics** | Item/fluid Gutterpipe with portholes, Snatcher Claw, Daub Sorting, Sloshtank, Gutter Boiler + Pressurised Fizz, Gasbag, Hurl Cannon and Tubes | Visible item transit and display caps, mount-on-spline |
| 4 | **First Well** | Glimmer Lens + resonance dial, Insight, Codex shell, First Spark, Flicker catch with Gob-Jar, simple Cradle, Crackle Coil, Resonator, Glimmer Wisp, Vessa intro | World Event + WellRegistry, HUD timing, the scan-to-seat loop |
| 5 | **Storm hunt** | Stormcall, Steady Wells, Sparkjaw, calm/smother, Tether Stakes, Wellhead and catalysis, jar escort with Stability, Riftmoth, Goblin Poacher + Slurp Walker, Storm Vane | Encounters, lightning hooks, movement limits, the full capture loop with co-op |
| 6 | **Glimmerforged** | Heart of the Machine, Coreheart Engine, Prism Grinder, Arc Foundry, Sparkline and Spark Pylon, Tint, Sunglass/Moonwell, Racket, Scar Bloom/Coffer Echo | Vanilla event hooks, Tint caching, Racket director |
| 7 | **Resonant** | Resonance Forge, Instability, Taste/Harmony, feeding, Tinklings (Courier, Tender), Shardling, Void Bleed + rifts, Ward Lantern | NPC jobs, abstract simulation, claim-safe risk |
| 8 | **Arcanum** | Infusion Circle, Rune Rings, Ley Channels and Streams, Roaring/Primal Wells, merge ritual, Prism Warden, Blood Moon Chalice, Ward Spire | Big encounters, conduct UI, ribbon transport |
| 9 | **Ascendant** | Prism Confluence, Veil Anchor, The Unwoven, Veil Engine, Rift Gates, Orrery | Endgame balance, seal events |
| 10 | **Hollowmere** | Riftloom, per-team realm, island planting, Fraying | World creation API, or the sky-region fallback |

**Prototype spikes to run early** (before slice 4): custom World Event conditions and signals; hooking vanilla breach/coffer/Blood Moon endings; server-side lightning at a block; `CustomUIHud` timing latency; hit/hold interactions on custom blocks; mounts on a custom spline; held-item movement limits; NPC-to-block beam visuals; reusing Void Eye and goblin rigs; per-team world creation and portals; loot table additions to the Deepworks and Gobliterator; local weather override; projectile interception; flying mounts; Runebinder Lattice access; claim system hooks.

---

## 17. Owner decisions (2026-10-25)

| # | Topic | Decision |
|---|---|---|
| 1 | Node name | **Glimmerwell** approved |
| 2 | Wells shared or per-player | **Shared.** A Tethered Well is claimed by its tetherer's crew (§11) |
| 3 | Jarring | **Yes.** Jar-escort as a co-op mission, plus tap-in-place |
| 4 | Pressurised Fizz | **Keep** the half-grade; revisit only if it proves awkward in development |
| 5 | Tint | **Bonus-only.** No recipe requires a Tint; Prismatic still needs distinct Tints as inputs |
| 6 | Tier Heart aura | **Soft bonus** |
| 7 | Characters | Vessa Lanternwright and Snikka **approved**, lore framing OK |
| 8 | Puzzle Board | **Keep the optional puzzle** for the full build-out; may be removed after playtesting |
| 9 | Temperaments | **8**, Clank in, Frost folded into Gale |
| 10 | Racket on PvP | **Event targeting only**; not visible to other players |
| 11 | Raids when offline | **Online-only** for Poacher and Racket raids |
| 12 | Void scars | **Config** `risk.voidScarFadeDays` (0 = permanent) |
| 13 | Block damage | **Off by default**, config `faults.blockDamage` as hardcore opt-in |
| 14 | Veil Engine seal skip | **Config** `endgame.allowSealSkip`, default on for PvE |
| 15 | Runestones | **Cosmetic tie-in first**; Runebinder Adapter for foci later (needs prototype) |
| 16 | Sky Hurl / Skyline | Accept the vanilla Scrap Glider and Hookshot **or** Craftory equivalents |
| 17 | Footprint | **7×7 soft cap**; larger where it makes sense (Orrery, Riftloom, Coreheart) |
| 18 | Hollowmere | Sky-region fallback **OK** |
| 19 | Boss gating items | **Add to vanilla loot tables** (simplest to maintain); add our own follow-up encounter only if a slice shows it adds depth |
| 20 | Shared Codex | **Opt-in crews.** Hytale has no team concept we can rely on, so players form a Craftory crew (create, invite, leave). Crew members share Codex progress, tethered Wells and machine access. Co-op for two or more players is a primary focus |
| 21 | Rattlewheel footprint | Slice 2d uses a single block; the 2×2 footprint is deferred |
| 22 | Rattlewheel water sampling | Use `FluidSection` fluid ids and count the `Water` fluid asset in the four horizontal cells and the cell below |
| 23 | Rattlewheel sky check | Skip the sky check because the available local height-map API is not exposed; wind mode uses altitude and lack of water |
| 24 | Shroom Bed darkness | Use cached `BlockSection` light first and queue official section lighting when unavailable; if still unavailable, treat scaled night or a non-air, non-fluid block within 8 cells above as dark |
| 25 | Shroom Bed pop | Apply a small additive player velocity impulse through the official `Velocity` component; no damage or block changes |
| 26 | Jar weather stability | Drain 1 stability per 10 seconds only in disliked weather: Spark dislikes rain but not storms; Ember dislikes rain and storms; other temperaments have no weather penalty for now |
| 27 | Slice 5a lightning | Use a fake lightning particle and thunder sound because native server lightning is unavailable; sample weather by polling |
| 28 | Slice 5a capture visuals | Use queued block-state changes for Storm Vane and Damper visuals; tether beams refresh on a low-rate schedule |
| 29 | Capture pulse alignment | Press timing is interval-based; whether capture should align to Well pulses is a client playtest question |
| 30 | Slice 5a Storm Vane | A passive, non-energy block; storm activation is sampled by the existing weather polling path |
| 31 | Slice 5a Fizz Damper | Powered at 2 Fizz/s per unit; three or more active dampers within four blocks smother a Wild Well |
| 32 | Slice 5a stake spacing | Stake angular gaps are measured against equal spacing; a deviation greater than 40 degrees marks a stake leaning |
| 33 | Slice 5a storm budget | At most one Steady-or-stronger Wild Well per 512×512 region; budget is configurable |
| 34 | Slice 5b Core boundary | Engine owns pure catalysis math; Tech accesses Well state only through Core's public Well API |
| 35 | Slice 5a carrier readout | The carrier gets a throttled notification; nearby crew read stability through Squint Goggles |
| 36 | Wellhead ownership | Placement is allowed only above a crew-claimed Tethered Well; machine use and break honor the placer crew or the existing override permission |
| 37 | Wellhead output | Demand 40; output uses current Well strength, placement harmony, catalysis loss, and a 50% corruption penalty; demand/output are inactive outside active areas |
| 38 | Steady seating | Absent or positive jar stability is accepted; zero/burst stability is rejected; successful Steady-or-stronger seating grants `craftory:well/steady_seated`, not the Tier 4 unlock |
| 39 | Riftmoth pressure | A claimed active Tapped/Seated Well rolls its configured 300-second/20% schedule; 60 seconds of feeding corrupts it. Per-Well defaults are configurable |
| 40 | Affliction cleanup | Tracked confirmed-dead Riftmoths or a nearby active Tide/Growth Well cleanse corruption; `Larva_Void` is the Void-spawn stand-in, capped at three |
| 41 | Poacher raid | A claimed active Well rolls the 1200-second/10% raid schedule; two Poachers and a Walker arrive. A 120-second siphon lowers one strength band, never below Flicker |
| 42 | Walker appearance | Use the vanilla Coffer Goblin Guardian appearance as the bulky Walker body; no custom attachment or tint is required |

Naming: a new mod name to replace "Craftory" is being chosen; see the README once decided.

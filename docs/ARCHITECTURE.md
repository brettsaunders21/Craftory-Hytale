# Craftory for Hytale: Architecture & Design

This document covers how Craftory Tech is being rebuilt for Hytale. It keeps the features and the two
core ideas of the original plugin, **expandability** and **high performance**, and rebuilds them on
Hytale's ECS, asset and UI systems.

References:
- [CraftoryStudios/Craftory-Tech](https://github.com/CraftoryStudios/Craftory-Tech): the original Spigot plugin, v0.9.x, Java.
- [CraftoryStudios/Craftory](https://github.com/CraftoryStudios/Craftory): the unfinished "Craftory Core" rewrite, a shared core for all Craftory addons.

## 1. What we keep from Craftory Tech

| Area | Craftory Tech (Spigot) | Hytale direction |
|---|---|---|
| Power storage | Iron / Gold / Diamond / Emerald cells | Capacitor banks. Tiers become data, and the core glows and fills to show charge |
| Generators | Solid fuel, geothermal, rotary (wind/water), solar (basic, compacted, array) | The same set. Output depends on Hytale biome/zone, weather, time of day and nearby fluids |
| Machines | Electric furnace, macerator, foundry and electric foundry (4 tiers each) | Powered smelter, grinder (ore doubling) and multi-block alloy foundry |
| Magnetism | Magnetiser, magnetising table | Magnetised components, plus an item-magnet accessory |
| Automation | Block placer / breaker | Placer, breaker, and item extractors/inserters |
| Transmission | Power connectors with beams, early wires/pipes | Visible conduits that connect through block states, plus long-range crystal relays |
| Tools | Wrench, configurator, powered tools (chainsaw...) | Engineer's wrench, configurator, powered tools, and goggles that show a machine-status HUD |
| UI | Chest-menu GUIs: battery bar, indicators, side output config, tanks | Custom UI pages that share one widget kit (energy bar, progress, IO sides, tanks, upgrades) |
| Fluids | Early fluid storage | Tanks and pipes, built on Hytale's fluid system |
| Localisation | 34 languages through Crowdin | Hytale `.lang` files, keeping the Crowdin workflow |

Craftory Tech's own roadmap also included multi-block generators, a full tech tree and magic as a
companion system. Hytale's prefabs, zones and progression make all three a better fit than they were on Spigot.

## 2. Lessons from the old architecture

Craftory Tech:
- One `PowerGrid` per connected network. Each tick it sums demand, storage space and production, then distributes energy. That per-network solver model was sound and we keep it.
- The hot path was slow. Every tick looked each member up by `Location` (`getPoweredBlock(location)` over `HashSet<Location>`), used `instanceof` dispatch, and each grid ran as its own `BukkitRunnable`. Grid state lived outside the world and needed a separate `PowerGridSaver`.
- `@Ticking(ticks = N)` lowered the tick rate for each block type.

Craftory Core (rewrite):
- A clean split into an API module and an example addon, the right shape for expandability.
- `@Tickable(ticks, async)` with tick groups, a sync manager, and a 4-thread async worker pool. Methods were called through reflection.
- Capabilities as interfaces (`EnergyInput`, `EnergyOutput`, `EnergyStorage`, fluid equivalents), with limits declared in annotations.
- Persistent data and volatile data held separately for each block.

Carried forward: per-network solving, capabilities instead of class hierarchies, and the core/addon split.
Dropped: location hash lookups on the hot path, reflection in tick loops, our own thread pools that
mutate world state, and side-car save files.

## 3. Platform facts we build on (Hytale)

- Server plugins are JVM jars (`JavaPlugin`, `manifest.json`) and can bundle an asset pack. We build with
  Kotlin on Java 25.
- Data is ECS: block data is a `Component<ChunkStore>` and logic runs in systems such as
  `EntityTickingSystem`. Chunk sections carry a `NonTicking` marker when no player is tracking them;
  `WorldChunk.addKeepLoaded()` keeps a chunk loaded but does not force it to tick.
- Each `World` is its own `TickingThread`. The ECS supports parallel work through `CommandBuffer.fork()` /
  `mergeParallel`.
- Components are persisted through codecs (`BuilderCodec`), so machine and network state saves with the chunk.
- UI is server-driven: `InteractiveCustomUIPage` with `.ui` markup, plus `CustomUIHud`. Hytale plans to move
  this to NoesisGUI.
- Models and animations are Blockbench `.blockymodel` / `.blockyanim`. Block states handle visual changes such as on/off and fill level.
- Benches (`Crafting` / `Processing`, with tiers) and JSON recipes with `BenchRequirement`.

## 4. Module layout

```
craftory-engine/   Pure Kotlin, no Hytale dependency. Generic energy-network solver and math,
                   unit tested and benchmarked in isolation.
craftory-core/     Hytale plugin "CraftoryCore". Bridges the engine to Hytale ECS: block
                   components, systems, persistence codecs, UI kit, debug tooling and the public
                   addon API.
craftory-tech/     Hytale plugin "CraftoryTech". Content: machines, generators, items, recipes
                   and assets. Uses only the public core API, so it doubles as the reference addon.
```

Third-party addons depend on `CraftoryCore` the same way `CraftoryTech` does.

Core also owns opt-in crews and shared research knowledge. Players without an explicit crew receive a
lazy solo crew. Accepting an invite merges solo knowledge into the destination; leaving or being kicked
copies the crew's knowledge into a new solo crew. Core persists this state in `crews.json` with debounced
atomic replacement. Tech uses only the public Core API to render the Tinker's Notebook and advance a
crew to Tinkered when all eight first-slice pages are discovered.

## 5. Performance design

Block-entity components are the source of truth for gameplay state. The Core energy graph is a derived
solver/index cache rebuilt from loaded components; solver output is written back to components for
machines, commands, UI and debugging to read.

1. **Data-oriented networks.** Each energy network keeps its members in dense index-addressed arrays
   (`LongArray` stored/capacity, `IntArray` max in/out, a role bitmask). The per-tick solve is a few linear
   passes with no allocation, hashing or virtual dispatch. Persisted gameplay state lives in block-entity
   components; `EnergyGraph` is a derived cache rebuilt as members load.
2. **Incremental topology.** Placing a block merges networks (union-find). Only breaking a block can split a
   network, and only then do we flood-fill that one network. Placing or breaking never triggers a global rebuild.
3. **Sleeping networks.** A network with no production and no demand is marked idle and skipped until a
   member changes. A large idle base then costs nearly nothing.
4. **Query-driven systems.** Machine systems query their state, energy member and block-state components.
   The Core energy clock gates the solver cadence; there are no per-machine maps or custom schedulers.
   Machines in `NonTicking` sections are inactive unless covered by a charged Wakebell Anchor. The Core
   keeps that coverage in a per-world chunk index, and the energy solve treats inactive members as having
   no production or demand without changing their configured values or stored energy. Anchors refresh
   chunk and section active timers and request `SET_TICKING`; loaded references alone do not provide
   ticking.
5. **World-thread ownership.** All mutation happens on the world's own thread through the ECS. Solver
   write-back and machine state updates remain serial because they mutate shared graph and component state.
6. **No reflection on hot paths.** Tick intervals, capabilities and limits are resolved once at registration.
7. **Measured, not assumed.**
   - JMH benchmarks for the engine solver.
   - A `/craftory stress` command that places N machines.
   - Tick-time metrics for each world.

   Budget targets get set once we have the first benchmark numbers.

## 6. Expandability design

- **Capabilities, not inheritance.** `EnergyStorage`, `EnergyProducer`, `EnergyConsumer`, `ItemIO`,
  `FluidTank` and `Upgradable` are separate components. A machine is a combination of them, and other
  mods can attach the same components to their own blocks and join Craftory networks.
- **Data-driven definitions.** Tiers, capacities, transfer rates, process times and recipes live in JSON
  assets, so a new tier or a simple machine needs no new code.
- **Registries with namespaced keys.** Addons register machine types, recipe types, network types (energy
  now, then fluids and items) and UI widgets.
- **Events.** For example network formed/split, machine started/finished, and energy produced, so addons can react.
- **Stable public API surface.** Kept in `studio.craftory.core.api` and versioned with the plugin.

## 7. Gameplay direction

The gameplay and art direction lives in [DESIGN.md](DESIGN.md): a goblin junk-tech start, then
crystal-and-scrap hybrid tiers, then pure arcane magic. It builds on Hytale Update 7's goblin content
(Scrap, the Goblin Lab, goblin traders, the Void). Architectural consequences:

- **Tiers are data.** Tier definitions (grade, max throughput, visuals, unlock key) are loaded from
  asset JSON, so we can add or split tiers without code changes.
- **Graded energy.** Each network packet carries a grade (Fizz, then Dim/Bright/Radiant/Prismatic
  Glimmer). The solver respects each device's `minGrade`/`maxGrade` and each link's max grade.
  Over-grade flow becomes an overload event, not an error. Resonators and Dampeners are converter
  nodes between sub-networks.
- **One transport graph, several payloads.** Pipes carry energy, items, fluids and (via Hurl Tubes)
  players. The engine models a shared graph with per-payload channels. Item transit is
  simulated as data (position along a segment), and only the client-visible part is rendered:
  capped display entities per chunk, with LOD/batching for distant pipes. Visuals never drive
  simulation.
- **Per-player progression.** Knowledge, the Tinker's Notebook, Insight and Codex state are stored
  as player components with the save hook, and can be optionally shared per team or server.
- **Machine state machine.** Machines move through Frame → Assembling → Running/Stalled/Overloaded
  → Fizzled. Assembly progress, build quality, Jank and upgrade slots are machine components that
  the UI and block-state animations read.
- **Effects are events.** Research discoveries, overloads, infusions and fizzles go through an
  effect-event bus. Content defines the particles, sounds and animations in data, and the core
  throttles them per player.
- **Golems on Hytale NPCs.** Tinklings use Hytale's NPC/role system with Craftory job sigils as
   world markers. Job logic runs on the world thread through query-based systems.

## 8. Milestones

### Slice 2c implementation notes

Powered machines persist `CraftoryJank` and two `CraftoryBodgeSlots` on their block entities.
The slots are a dedicated component rather than `BenchBlock.upgradeItems`, since only the Scorch
Oven is a bench. Jank evaluation runs after energy solving and pauses in inactive areas. Bodge parts
use their item `Secondary` interaction; Scrap repairs Jank through the machine block interaction so
vanilla Scrap assets are not overridden. The Core energy-condition facade exposes under-grade and
overload events to Tech without exposing Core internals.
Boom Valve speed currently scales generators and the Gnasher; vanilla ProcessingBench timing
does not expose a safe per-machine speed multiplier, so Scorch Oven processing speed is unchanged.
Scrap Rush raids persist their goblin UUIDs and theft timer on the target machine. A game-time
probability roll selects one powered owned machine per eligible crew; vanilla
`Coffer_Goblin_Scrapper` NPCs spawn through the column-probe API and follow a transient path to it,
stall it after stealing, and grant the crew's Scrap Rush notebook page when defeated.
`/craftory scraprush start` is the operator-only, schedule-bypassing entry point.

0. **Repository & dev environment**
   - Gradle multi-project build, local dev server, debugging and hot swap, CI, lint and tests.
1. **Engine**
   - Generic graded energy network solver.
   - Transport graph with item/fluid channels.
   - Unit tests and benchmarks.
2. **Core integration**
   - Block components, network building from placed blocks, persistence and debug commands.
   - UI kit (machine page, Codex page shell).
3. **Goblin era vertical slice (Tier 1)**
   - Boomshroom Burner, Fizz Canister, Gnasher, Scorch Oven and Gutterpipe energy.
   - Tinker's Wrench, Squint Goggles, and Grizzlecog selling sealed machines.
4. **Know-how and assembly**
   - Tinker's Notebook, goblin job questline and Ramshackle Assembly.
5. **Visible transport**
   - Item/fluid Gutterpipes, Sloshtank and Hurl Tubes.
6. **Tinkered (Tier 2)**
   - Bodging, Jank and upgrade slots, plus the rest of the goblin-era machines and turrets.
7. **Research foundation**
   - Glimmer Lens scanning, Insight, the Research Bench puzzle, Codex map and field experiments.
8. **Hybrid tiers (3–5)**
   - Crystals, Resonators/Dampeners, Sparkline/Glimmer Conduits, Infusion Altar and Tinklings.
9. **Arcane tiers (6–7)**
   - Ley Channels, Rune Rings, Wards, and endgame devices.

An art and UI pass runs alongside milestone 3 onward, with each era's look locked in
before its content milestone starts.

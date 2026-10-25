# API spikes (DESIGN §16)

Throwaway proofs for later slices. All code lives in `craftory-tech/src/main/kotlin/studio/craftory/tech/spike/`
(+ `Server/WorldEvent/{Event,Stage}/Craftory_Spike_*.json`, `Common/UI/Custom/Craftory/Hud/SpikeHud.ui`, the
`/craftory spike` wrapper in `CraftoryCorePlugin`, and the spike checks in `CraftorySelfTest`). Delete those to remove it.
Probe components are registered without codecs, so removing them can't break saves.

Run as an operator: `/craftory spike <name> [args]` (permission `craftory.spike`). Server: Hytale 0.7.4.
Javadoc base: `https://docs.hytale.com/api/` + package path (e.g. [WeatherResource](https://docs.hytale.com/api/com/hypixel/hytale/builtin/weather/resources/WeatherResource)).

Verdicts: **verified** = proven on the live dev server · **unverified** = API found and proof written, but needs a real
client to observe · **impossible** = no server API in 0.7.4.

| # | Spike | Command | Verdict |
|---|-------|---------|---------|
| 1 | Lightning strike at block / detect natural strikes | `lightning` | **impossible** (fallback proof unverified) |
| 2 | Weather read / local override / change events | `weather [local <id>\|clear\|watch on\|off\|list]` | read **verified**; override **unverified**; change event **impossible** (polling works) |
| 3 | Vanilla World Event endings + custom events | `events [start <id>\|signal <uuid> <signal>]` | **verified** (assets + custom asset load); end hook **unverified** in play |
| 4 | Per-crew world + portal + unload when empty | `realm create\|leave\|list\|status` | **verified** (create/remove round-trip in selftest); teleport **unverified** |
| 5 | Held-item slow / movement modifier | `slow on\|off\|held <itemId>` | **unverified** |
| 6 | NPC-to-block beam | `beam [seconds\|list]` | assets **verified**; visual **unverified** |
| 7 | Projectile interception | `projectile on\|off` | **unverified** |
| 8 | Claim hooks | `claims [guard on\|off]` | **impossible** (no claim API); fallback unverified |
| 9 | Flying mount / player-controlled flier | `mount` | **unverified** (client flight control unknown) |
| 10 | `CustomUIHud` latency | `hud start\|stop` | **unverified** (client RTT needs a client) |

Live evidence (`./gradlew runAllMods`, console): `Universe ready!`, then `craftory selftest` ->
`selftest: 50 passed, 0 failed` / `selftest: cleanup (liveGrit=0, sectionHolders=0, chunkHolders=0)`, including
`PASS spike beam assets (count=4)`, `PASS spike weather assets and resource (assets=107, resource=true)`,
`PASS spike custom world event asset`, `PASS spike realm round-trip`. Console output of each spike is quoted below.

---

## 1. Lightning — impossible (fallback: particle + sound + damage)

- No lightning/thunder class, member, packet or string in `Server-0.7.4.jar`, and none in the Javadoc index.
  Weather assets (`Server/Weathers/Zone2/Zone2_Thunder_Storm.json`) carry only sky/fog/cloud colours, a `Particle`
  system and `Tags` (`"Zone2": ["Thunderstorm"], "Rain": ["Storm"]`). Lightning flashes are client-side weather
  visuals; the server never learns about a "strike", so there is nothing to detect or spawn.
- Fallback proof (`SpikeCommands.lightning`): vanilla particle `Server/Particles/Spell/Lightning/Lightning.particlesystem`
  via `ParticleUtil.spawnParticleEffect`, sound `SFX_Global_Weather_Thunder` via `SoundUtil.playSoundEvent3d`, at the
  targeted block. Stormcall/Storm Vane damage would be our own `DamageSystems` call in that radius.
- Natural-strike detection fallback: treat "storm active" as the signal. Poll the player's
  [WeatherTracker](https://docs.hytale.com/api/com/hypixel/hytale/builtin/weather/components/WeatherTracker)
  weather and check the weather asset's `Tags` (`Rain: Storm` / `Thunderstorm`), then roll our own strikes on a
  bounded timer while the Storm Vane is in storm weather.

```kotlin
ParticleUtil.spawnParticleEffect("Lightning", pos, store)
SoundUtil.playSoundEvent3d(SoundEvent.getAssetMap().getIndex("SFX_Global_Weather_Thunder"), SoundCategory.SFX, pos, store)
```

## 2. Weather — read verified, local override unverified, change event impossible (poll)

- [WeatherResource](https://docs.hytale.com/api/com/hypixel/hytale/builtin/weather/resources/WeatherResource)
  (`Resource<EntityStore>`): `getEnvironmentWeather()` (env -> weather index), `getWeatherIndexForEnvironment(int)`,
  `getForcedWeatherIndex()`, `getEffectiveWeatherIndex(int)`, `setForcedWeather(String)` (whole world).
- [WeatherTracker](https://docs.hytale.com/api/com/hypixel/hytale/builtin/weather/components/WeatherTracker)
  (per-player component): `getWeatherIndex()`, `getEnvironmentId()`, `tryClaimOverride(Ref, int)` /
  `releaseOverride(Ref)`, `setOverrideWeatherIndex(int)` / `clearOverrideWeatherIndex()`. This is the per-player
  "local" override that vanilla encounters use (`EncounterWeatherState`, `ActionSetEncounterWeather`).
- Weather assets: `Weather.getAssetMap()` (107 loaded, including `Blood_Moon`).
- No weather-change event exists. `SpikeWeatherWatchSystem` polls `getEffectiveWeatherIndex(env)` (includes forced weather) for every environment every 20 ticks per world
  (state in a world `Resource`) and logs changes. Weather rerolls at most hourly (`compareAndSwapHour`), so a
  20-tick poll is cheap and plenty.
- Console: `[spike weather] assets=107 [Blood_Moon, Cave_Deep, ...]`.

```kotlin
val tracker = store.getComponent(ref, WeatherTracker.getComponentType())!!
tracker.tryClaimOverride(ref, Weather.getAssetMap().getIndex("Zone2_Thunder_Storm")) // local, per player
tracker.releaseOverride(ref)
```

Fallback for zone-wide local weather: claim the override for every player inside the zone (enter/leave check on the
same 20-tick bucket).

## 3. World Events — verified (definitions, conditions, signals); end hook unverified in play

- Package `com.hypixel.hytale.builtin.adventure.worldevents`:
  [WorldEventAsset](https://docs.hytale.com/api/com/hypixel/hytale/builtin/adventure/worldevents/event/WorldEventAsset)
  (`Server/WorldEvent/Event/*.json`, stages in `Server/WorldEvent/Stage/`),
  [EventEndEcsEvent](https://docs.hytale.com/api/com/hypixel/hytale/builtin/adventure/worldevents/component/EventEndEcsEvent)
  (`getWorldEvent()`, `getReason()` = `SUCCESS|FAIL|ITERATION`),
  [WorldEventSignal](https://docs.hytale.com/api/com/hypixel/hytale/builtin/adventure/worldevents/component/WorldEventSignal)
  (`eventId()`, `signalId()`), conditions `SignalCondition`, `EventEndCondition`, `IntervalCondition`,
  `LocationCondition`, `ProximityCondition`, `TimeCondition`, `WorldClosedCondition`, and
  [GlobalWorldEventManager](https://docs.hytale.com/api/com/hypixel/hytale/builtin/adventure/worldevents/GlobalWorldEventManager)
  (`startEvent`, `stopEvent`, `activeIds`).
- Both ECS events are fired per entity on the **EntityStore** (`WorldEventTickSystem` -> `buffer.invoke(ref, EventEndEcsEvent(...))`;
  NPC action `ActionSignalWorldEvent` -> `store.invoke(ref, WorldEventSignal(null, signalId))`), so an
  `EntityEventSystem<EntityStore, EventEndEcsEvent>` receives them (`SpikeWorldEventEndLogSystem`).
- Vanilla ids (console): `Coffer_Goblin`, `Goblin_Breach_First_Event`, `Goblin_Breach_Perpetual_Event`,
  `Goblin_Breach_Prelude_Bootstrap`, `Goblin_Breach_Prelude_Event`, `Hunt_Breach`. The Coffer outcome is already a
  signal: `coffer_goblin_complete` (cleared) or `coffer_goblin_abort` (everyone left), plus a 30-minute
  `IntervalCondition` timeout. **Blood Moon is not a World Event**: it is the weather asset `Blood_Moon`. Detect it by
  polling the weather index as in spike 2. `MoonPhaseChangeEvent` (a `WorldEventSystem`) reports moon-phase changes.
- Custom definition: `Craftory_Spike_Event` (root stage forks on `SignalCondition "Craftory_Spike_Signal"`, `Mode: Complete`)
  loads (selftest `PASS spike custom world event asset`). The first attempt with `"Mode": "COMPLETE"` failed to decode:
  enum values are PascalCase.

```kotlin
class BreachEnded : EntityEventSystem<EntityStore, EventEndEcsEvent>(EventEndEcsEvent::class.java) {
    override fun getQuery() = Query.any<EntityStore>()
    override fun handle(i: Int, c: ArchetypeChunk<EntityStore>, s: Store<EntityStore>, b: CommandBuffer<EntityStore>, e: EventEndEcsEvent) {
        if (e.reason == EventEndEcsEvent.Reason.SUCCESS && e.worldEvent.asset.startsWith("Goblin_Breach")) { /* reward */ }
    }
}
```

Fallback: if the end event doesn't fire for an asset, listen for that asset's own `WorldEventSignal` ids (as with the Coffer).

## 4. Hollowmere per-crew world — verified (create + remove-on-empty); teleport unverified

- [InstancesPlugin](https://docs.hytale.com/api/com/hypixel/hytale/builtin/instances/InstancesPlugin):
  `spawnInstance(asset, worldName, forWorld, returnTransform, Consumer<WorldConfig>)` returns `CompletableFuture<World>`;
  `teleportPlayerToLoadingInstance(ref, accessor, future, transform)`, `exitInstance(ref, accessor)`,
  `safeRemoveInstance(World)`, `getInstanceAssets()` (vanilla `Basic` used).
  Removal: `InstanceWorldConfig.ensureAndGet(config).setRemovalConditions(WorldEmptyCondition(seconds))`
  ([WorldEmptyCondition](https://docs.hytale.com/api/com/hypixel/hytale/builtin/instances/removal/WorldEmptyCondition)),
  also `IdleTimeoutCondition`, `TimeoutCondition`. Portals: `builtin.portals` (`PortalDevice`, `EnterPortalEvent`).
- Selftest round-trip: spawn `craftory_selftest_<id>` -> it appears in `Universe` -> `safeRemoveInstance` -> it's gone.
  `realm create` names the world `craftory_spike_<crewId8>` from `CraftoryCore.crewService().crewOf(uuid)` and teleports.
- Open: whether instance chunks persist across removal (needed so planted islands survive). Removal likely discards
  the instance, so the island layout must be saved in crew data and re-pasted, or the world kept with `Universe.makeWorld`
  and unloaded instead. Decide before slice 4.

```kotlin
val future = InstancesPlugin.get().spawnInstance("Basic", "craftory_hollow_$crew", world, returnTransform,
    Consumer { InstanceWorldConfig.ensureAndGet(it).setRemovalConditions(WorldEmptyCondition(30.0)) })
InstancesPlugin.teleportPlayerToLoadingInstance(ref, store, future, null)
```

Fallback (owner-approved): bounded sky region above the Veil Anchor in the main world.

## 5. Held-item movement limits — unverified (needs client to feel the speed)

- [MovementManager](https://docs.hytale.com/api/com/hypixel/hytale/server/core/entity/entities/player/movement/MovementManager)
  (player component): `getSettings()` returns the protocol `MovementSettings` (`baseSpeed`, `forward*SpeedMultiplier`, ...),
  `update(PacketHandler)` pushes the settings to the client, and `resetDefaultsAndUpdate(ref, accessor)` restores them.
  Movement is client-predicted, so settings must be sent. There is no stacking "modifier" API for speed; `StatModifiersManager`
  covers entity stats, not movement.
- `SpikeHeldSlowSystem`: every 20 ticks compares `InventoryComponent.getItemInHand(...)` with the configured id and
  halves `baseSpeed` / resets. Transient components hold the state.

```kotlin
val mm = accessor.getComponent(ref, MovementManager.getComponentType())!!
mm.settings.baseSpeed *= 0.5f; mm.update(playerRef.packetHandler)  // apply
mm.resetDefaultsAndUpdate(ref, accessor)                              // remove
```

Fallback: if another system (mounts use `MountPlugin.resetOriginalPlayerMovementSettings`) resets settings, re-apply
on the 20-tick check. A slowness `EntityEffect` is the alternative if a vanilla one exists.

## 6. NPC-to-block beam — assets verified, visual unverified

- [BeamComponent](https://docs.hytale.com/api/com/hypixel/hytale/builtin/beam/BeamComponent)`.spawn(store, pos, lifetimeSeconds, AttachedBeam...)`,
  [AttachedBeam](https://docs.hytale.com/api/com/hypixel/hytale/builtin/beam/AttachedBeam)`.toBlock(beamIndex, scale, sourceNode, x, y, z)` /
  `toEntity(..., ref, node)` / `toPosition(...)`; `add`/`remove`/`clear` on an existing component.
  Assets `Beam.getAssetMap()`: `Basic`, `Chain`, `Fire`, `Rope` (console `[spike beam] assets=4 Basic, Chain, Fire, Rope`).
- A tether from an NPC = add `BeamComponent` to the NPC ref with `AttachedBeam.toBlock(...)`. No particle-line or
  stretched-model workaround is needed.

```kotlin
BeamComponent.spawn(store, eye, 5f, AttachedBeam.toBlock(Beam.getAssetMap().getIndex("Chain"), 1f, null, x, y, z))
```

Fallback: particle trail sampled along the segment every N ticks.

## 7. Projectile interception — unverified (needs a shooter)

- [ProjectileLaunchEvent](https://docs.hytale.com/api/com/hypixel/hytale/server/core/modules/projectile/event/ProjectileLaunchEvent)
  (`getProjectileRef()`, `getInteractionType()`, `isLaunchedByWeapon()`), components
  [Projectile](https://docs.hytale.com/api/com/hypixel/hytale/server/core/modules/projectile/component/Projectile)
  and `PredictedProjectile`, plus `ProjectileModule.spawnProjectile(...)` for turrets.
- Ward proof: `EntityTickingSystem` over `Projectile + TransformComponent` removes projectiles inside a sphere
  (`commandBuffer.removeEntity`) and counts them. A launch logger shows whether the event reaches `EntityStore` systems.
- Open: `PredictedProjectile` arrows are client-predicted, so the client may still render an arrow that the server removed.

Fallback: cancel the damage instead (`Damage` event filter by source projectile inside the ward).

## 8. Claims — impossible (no API)

- No claim, protection or region classes in 0.7.4 (jar + Javadoc). Console: `[spike claims] matching plugins=[none]; no claim API in 0.7.4`.
- Fallback proof: `SpikeClaimGuardSystem` cancels `DamageBlockEvent` (cancellable ECS event) for non-crew players
  within 8 blocks of a guard point. The real version would store claims in crew data and also guard `BreakBlockEvent`/`PlaceBlockEvent` and block use interactions. Solo players can't own a guard and count as outsiders.

## 9. Flying mounts — unverified

- `builtin.mounts`: `MountPlugin` (`getMountComponentType`, `getMountedComponentType`), `NPCMountComponent`
  (owner `PlayerRef`, anchor, `originalRoleIndex`), `MountedComponent(Ref, Vector3f, MountController)`, NPC action
  `ActionMount`, NPC motion `MotionControllerFly`, protocol enum `MountController`.
- Proof spawns the first vanilla NPC role whose name suggests flight and sets `NPCMountComponent`. Whether the client
  steers a flying controller is client-side and unproven.

Fallback: server-steered flight. The player mounts a `MotionControllerFly` NPC that follows look direction, or the
rail/spline mount idea from DESIGN.

## 10. `CustomUIHud` latency — unverified (client RTT not measurable headless)

- [CustomUIHud](https://docs.hytale.com/api/com/hypixel/hytale/server/core/entity/entities/player/hud/CustomUIHud)
  `(PlayerRef, key, zOrder)`, `build(UICommandBuilder)`, `update(clear, UICommandBuilder)`; `HudManager.addCustomHud/removeCustomHud`.
- Proof: `SpikeHudSystem` updates one label every tick and accumulates the server-side nanoseconds spent in `update()`.
  `hud stop` prints updates/s and average cost. Each `update(false, …)` sends one packet with the changed properties.
  The send rate is therefore whatever we call it at, at most once per world tick.
- Needs a client: start `hud start` with a stopwatch overlay or a client-side timestamp echo.

Recommendation regardless: update timer HUDs at most once per second (20-tick bucket), and only on value change.

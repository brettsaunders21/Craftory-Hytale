# Asset wiring for PRs #9 and #10

The generated files live under `craftory-tech/src/main/resources/Common/`. Item JSON asset paths below are relative to that directory.

## PR #9: multiblock

### `Craftory_Junk_Heart_Firebox.json`

The current PR #9 file has no `BlockType.State.Definitions`, so wire the base block to the Off art:

- `Icon` → `Icons/ItemsGenerated/Craftory/Craftory_Junk_Heart_Firebox.png`
- `BlockType.CustomModel` → `Blocks/Craftory/Junk_Heart_Firebox/Junk_Heart_Firebox_Off.blockymodel`
- `BlockType.CustomModelTexture` → `[{"Texture":"Blocks/Craftory/Junk_Heart_Firebox/Junk_Heart_Firebox_Off_Texture.png","Weight":1}]`

If On/Off states are added, set the Off state to the Off model and texture above and the On state to:

- `State.Definitions.On.CustomModel` → `Blocks/Craftory/Junk_Heart_Firebox/Junk_Heart_Firebox_On.blockymodel`
- `State.Definitions.On.CustomModelTexture` → `[{"Texture":"Blocks/Craftory/Junk_Heart_Firebox/Junk_Heart_Firebox_On_Texture.png","Weight":1}]`

### `Craftory_Scrap_Plating.json`

- `Icon` → `Icons/ItemsGenerated/Craftory/Craftory_Scrap_Plating.png`
- `BlockType.DrawType` → `Model`
- `BlockType.CustomModel` → `Blocks/Craftory/Scrap_Plating/Scrap_Plating.blockymodel`
- `BlockType.CustomModelTexture` → `[{"Texture":"Blocks/Craftory/Scrap_Plating/Scrap_Plating_Texture.png","Weight":1}]`

### `Craftory_Tinkers_Hammer.json`

- `Icon` → `Icons/ItemsGenerated/Craftory/Craftory_Tinkers_Hammer.png`
- `Model` → `Items/Craftory/Tinkers_Hammer/Tinkers_Hammer.blockymodel`
- `Texture` → `Items/Craftory/Tinkers_Hammer/Tinkers_Hammer_Texture.png`

### New chimney blocks

Add these item JSONs using the minimal model-block fields shown in PR #9's Scrap Plating definition. Both use the generated chimney icon, which is the only chimney icon in the art manifest.

`Craftory_Junk_Heart_Chimney.json`:

```json
{
  "TranslationProperties": {
    "Name": "server.items.Craftory_Junk_Heart_Chimney.name",
    "Description": "server.items.Craftory_Junk_Heart_Chimney.description"
  },
  "ItemLevel": 10,
  "MaxStack": 100,
  "Icon": "Icons/ItemsGenerated/Craftory/Craftory_Junk_Heart_Chimney.png",
  "PlayerAnimationsId": "Block",
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Model",
    "HitboxType": "Full",
    "Flags": {},
    "Gathering": {
      "Breaking": {
        "GatherType": "GoblinMetal"
      }
    },
    "BlockParticleSetId": "Metal",
    "CustomModel": "Blocks/Craftory/Junk_Heart_Chimney/Junk_Heart_Chimney.blockymodel",
    "CustomModelTexture": [
      {
        "Texture": "Blocks/Craftory/Junk_Heart_Chimney/Junk_Heart_Chimney_Texture.png",
        "Weight": 1
      }
    ]
  }
}
```

`Craftory_Junk_Heart_Chimney_Cap.json`:

```json
{
  "TranslationProperties": {
    "Name": "server.items.Craftory_Junk_Heart_Chimney_Cap.name",
    "Description": "server.items.Craftory_Junk_Heart_Chimney_Cap.description"
  },
  "ItemLevel": 10,
  "MaxStack": 100,
  "Icon": "Icons/ItemsGenerated/Craftory/Craftory_Junk_Heart_Chimney.png",
  "PlayerAnimationsId": "Block",
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Model",
    "HitboxType": "Full",
    "Flags": {},
    "Gathering": {
      "Breaking": {
        "GatherType": "GoblinMetal"
      }
    },
    "BlockParticleSetId": "Metal",
    "CustomModel": "Blocks/Craftory/Junk_Heart_Chimney/Junk_Heart_Chimney_Cap.blockymodel",
    "CustomModelTexture": [
      {
        "Texture": "Blocks/Craftory/Junk_Heart_Chimney/Junk_Heart_Chimney_Cap_Texture.png",
        "Weight": 1
      }
    ]
  }
}
```

The cap model has a `Smoke-Emitter` node at its top. PR #9's `CraftoryIds` chimney/smoke constants currently use vanilla `Build_GreyDark_Pipe`; point those constants at the new Craftory chimney and cap block IDs.

## PR #10: jank/bodge/scraprush

Update each `Icon` field:

- `Craftory_Boom_Valve.json` → `Icons/ItemsGenerated/Craftory/Craftory_Boom_Valve.png`
- `Craftory_Gutter_Gear.json` → `Icons/ItemsGenerated/Craftory/Craftory_Gutter_Gear.png`
- `Craftory_Scrap_Plating_Bodge.json` → `Icons/ItemsGenerated/Craftory/Craftory_Scrap_Plating_Bodge.png`
- `Craftory_Squawk_Box.json` → `Icons/ItemsGenerated/Craftory/Craftory_Squawk_Box.png`
- `Craftory_Muffler.json` → `Icons/ItemsGenerated/Craftory/Craftory_Muffler.png`

The Craftory smoke sprites `Particles/Craftory/Smoke_White.png`, `Smoke_Grey.png`, and `Smoke_Black.png`, plus `Fizz_Spark.png` and `Glimmer_Mote.png`, still need particle-system JSON assets before they can be wired to particle emitters.

# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.5.1 — hotfix: hatch block items showed raw translation keys instead of names.**

## What 0.5.1 fixes

0.5.0 collapsed the hatch blocks from one-per-tier to one-per-family, but kept the tier in the block **item**
id (`item_input_hatch_tiny`, …). That broke every hatch name in game.

The cause is that `BlockItem.getDescriptionId()` does not use the item's own registry name — it delegates to
the block:

```
BlockItem.getDescriptionId():
  0: aload_0
  1: invokevirtual getBlock()
  4: invokevirtual Block.getDescriptionId()
  7: areturn
```

So all 46 tier items resolved to their **family** key, `block.modular_machinery_reborn.item_input_hatch`,
which 0.5.0 had deleted — the game therefore fell back to printing the raw key. In 0.4.x this worked by
accident, because each tier was its own block whose id happened to equal the item id.

`TieredHatchItem` now overrides `getDescriptionId()` to report its own id, which is what the 46 per-tier
language entries key on. The six family keys were also restored so the family blocks' own description ids
resolve.

Verified end to end by bytecode, not by assumption:

- `Item.getName(ItemStack)` → `getDescriptionId(ItemStack)`
- `Item.getDescriptionId(ItemStack)` → `getDescriptionId()`
- `TieredHatchItem.getDescriptionId()` → returns the stored per-tier id

No other behaviour changed in this release.

## Language key coverage

Every registered item and block now resolves to a key present in both language files:

| | Items | Blocks | UI | Orphans |
|---|---|---|---|---|
| `zh_cn.json` (65 keys) | 53 resolved, 0 missing | 7 resolved, 0 missing | 0 missing | 0 |
| `en_us.json` (65 keys) | 53 resolved, 0 missing | 7 resolved, 0 missing | 0 missing | 0 |

The 53 item keys are 1 `machine_controller` + 46 tiered hatch items + 6 standalone items. The 7 block keys
are `machine_controller` plus the six hatch families. The 6 remaining keys are the creative tab and five UI
strings.

## Runtime contents

- `modular_machinery_reborn:machine_controller`, a controller block that consumes one held item and applies a matching machine recipe.
- `modular_machinery_reborn:machine`, a recipe type with `machine`, `input`, `output`, `duration`, and `energy` fields.
- Optional KubeJS 6 schema: `event.recipes.modular_machinery_reborn.machine({...})`.
- The controller forms when the eight blocks around it are `minecraft:iron_block` or any hatch block.
- Hatch tiers follow the original model: one block per family with a `size` property, one block item per tier (7×2 item, 8×2 fluid, 8×2 energy).
- The block entity exposes Forge item, FE, and fluid capabilities and processes recipes over `duration` ticks.
- Standalone 1.12.2 items are registered with their legacy textures: blueprint, modularium, construction tool, machine projector, redstone signal, and wrench.

## Known limitations

- Tiered hatches are decorative: no block entity, no capability routing, and the tier capacity values in
  `HatchTier` are not consumed yet.
- The machine definition layer has not been started; the structure is still a hardcoded 8-block ring.
- The requirement/component recipe engine, structure preview and blueprint tooling, upgrade / parallel /
  factory systems, mod integrations, and the original GUI are all still missing.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

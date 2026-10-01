# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.5.0 — milestone M0: hatch tier model reverted to the original, plus a resource-duplication fix.**

## What 0.5.0 changes

### One block per hatch family, tiers carried by a `size` property

0.4.x registered a separate block for every tier (52 hatch blocks). The original mod did the opposite: it
registered **one block per family** (`blockinputbus`, `blockenergyoutputhatch`, …) and exposed the tiers as
item variants of that one block through a `size` blockstate property, keeping the tier in item metadata.

0.5.0 returns to that model, since 1.20.1 has no item metadata:

- **6 hatch blocks** replacing 52 — `item_input_hatch`, `item_output_hatch`, `fluid_input_hatch`,
  `fluid_output_hatch`, `energy_input_hatch`, `energy_output_hatch`, each with a `size` property.
- **46 tiered block items** — 7×2 item, 8×2 fluid, 8×2 energy. The item registration ids are unchanged
  from 0.4.x.
- The invented `item_input_hatch_ultimate` / `item_output_hatch_ultimate` tiers are **removed**: the
  original item bus has only seven tiers and no ultimate art exists in the source. This also removes the
  reason 0.4.3 had to substitute the ludicrous texture.
- The four untiered hatch blocks are gone as well; the original had no such concept.

Tier ladders match the original enums exactly, including their numeric values:

| Family | Original enum | Tiers |
|---|---|---|
| Item bus | `ItemBusSize` | 7 — tiny(1) small(4) normal(6) reinforced(9) big(12) huge(16) ludicrous(32) |
| Fluid hatch | `FluidHatchSize` | 8 — the item ladder plus vacuum(32000 mB) |
| Energy hatch | `EnergyHatchData` | 8 — the item ladder plus ultimate(2097152 FE) |

### ⚠️ Breaking change

The 52 hatch **block** ids are gone; only the 46 tiered **item** ids and `machine_controller` survive.
Hatch blocks placed by 0.4.x will be unknown blocks in 0.5.0 — re-place them using the same item.

### Structures now accept every hatch family and tier

`MachineStructure` previously whitelisted four untiered hatches, which meant **no tiered hatch could ever be
built into a machine** and the output families were missing entirely. Any `MachineHatchBlock` now counts as
a port, so all six families and every tier work in the ring.

### Fixed: resources were destroyed when the output slot was full

`MachineControllerBlockEntity` consumed one input item and one batch of FE, then tried to insert the result,
and on failure parked `progress` at `maxProgress`. The next tick advanced past `maxProgress` and consumed
again — so while the output slot stayed full, the machine destroyed one input and one batch of FE **every
tick** with no output. The insertion is now simulated before anything is consumed, and a blocked machine
holds its progress without burning materials.

### Localization

The four untiered hatch keys and the two item-`ultimate` keys were dropped, so both language files now hold
59 keys (46 hatch items + 6 standalone items + `machine_controller` + 6 UI keys). Names remain as aligned in
0.4.4.

### Housekeeping

The startup log no longer prints a hardcoded `0.1.0`; it reads the real version from the mod container.

## Runtime contents

- `modular_machinery_reborn:machine_controller`, a controller block that consumes one held item and applies a matching machine recipe.
- `modular_machinery_reborn:machine`, a recipe type with `machine`, `input`, `output`, `duration`, and `energy` fields.
- Optional KubeJS 6 schema: `event.recipes.modular_machinery_reborn.machine({...})`.
- The controller forms when the eight blocks around it are `minecraft:iron_block` or any hatch block.
- The block entity exposes Forge item, FE, and fluid capabilities and processes recipes over `duration` ticks.
- The client menu shows input/output slots, structure state, progress, and stored FE; JEI lists all loaded machine recipes.
- Standalone 1.12.2 items are registered with their legacy textures: blueprint, modularium, construction tool, machine projector, redstone signal, and wrench.

## Known limitations

- Tiered hatches are still decorative: they have no block entity, no capacity values in play, and no
  capability routing. The tier numbers exist in `HatchTier` but are not yet consumed.
- The machine definition layer is not started. The structure is still a hardcoded 8-block ring on the
  controller's Y level.
- The requirement/component recipe engine, the structure preview and blueprint tooling, the upgrade /
  parallel / factory systems, all mod integrations, and the original GUI are not implemented.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

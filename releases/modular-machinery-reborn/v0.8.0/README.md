# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.8.0 — milestone M2: the requirement-based recipe engine.**

## What 0.8.0 changes

### Recipes are requirement lists again

0.4–0.7 used a flat shortcut (`input`, `output`, `duration`, `energy`) that could express exactly one item in
and one item out. The original used a typed requirement list, and that is what machines are built around:

```json
{
  "machine": "alloy_furnace",
  "registryName": "alloy_furnace_diamond",
  "recipeTime": 5,
  "requirements": [
    { "type": "modularmachinery:energy", "io-type": "input",  "energyPerTick": 100 },
    { "type": "modularmachinery:item",   "io-type": "input",  "item": "minecraft:coal", "amount": 32 },
    { "type": "modularmachinery:item",   "io-type": "output", "item": "minecraft:diamond", "amount": 1 }
  ]
}
```

Requirement `type` is accepted with either namespace, so the original files load with their `type` and
`io-type` fields untouched: `modularmachinery:item` and `modular_machinery_reborn:item` are the same thing.
Three kinds exist in M2 — `item`, `fluid`, `energy`.

### When things are consumed follows the original lifecycle

The original drove crafting through three hooks, and the timing is not cosmetic:

- **Inputs are paid when the craft begins** (`startCrafting`), not when it finishes.
- **Energy and per-tick fluids are drawn every tick** (`doIOTick`); `energyPerTick` is a rate, so
  `alloy_smelter_diamond` costs 100 FE/t for 5 ticks = 500 FE.
- **Outputs are produced at the end**, and **each output requirement rolls its own `chance`**. This is why
  `centrifuge_centrifuge_wool` lists four separate string outputs — two guaranteed, one at 75%, one at 40%,
  one at 20% — instead of one output with an amount range.

The finish is checked for space using the **guaranteed** amount, so a craft cannot start ending when its
possible result would not fit. Unlike the original, a blocked output no longer drains that tick's energy: the
space check happens before the per-tick draw.

### Ore dictionary moved to item tags

The ore dictionary has no automatic successor and guessing would silently match the wrong items, so
`ore:legacyName` is mapped only for names whose Forge 1.20.1 tag is confirmed to exist, and anything else is a
load error naming what to write instead:

| Legacy | 1.20.1 |
|---|---|
| `ore:ingotIron` | `#forge:ingots/iron` |
| `ore:ingotGold` | `#forge:ingots/gold` |
| `ore:ingotCopper` | `#forge:ingots/copper` |
| `ore:dustRedstone` | `#forge:dusts/redstone` |
| `ore:dustGlowstone` | `#forge:dusts/glowstone` |
| `ore:dustPrismarine` | `#forge:dusts/prismarine` |
| `ore:gemDiamond` / `gemEmerald` / `gemLapis` / `gemQuartz` / `gemPrismarine` | `#forge:gems/...` |

`#namespace:path` also works directly, which is the form to use for anything outside that table. The 1.12.2
`@meta` syntax is rejected with an explanatory error, as in the machine definitions.

### Nine built-in recipes ported

All nine non-adapter recipes from the original `default_recipes` are ported, with only the item ids that
changed between 1.12 and 1.20.1 translated (`minecraft:grass` → `grass_block`, `minecraft:dye@3` →
`light_blue_dye`, `minecraft:wool` → `white_wool`, `modularmachinery:itemmodularium` →
`modular_machinery_reborn:itemmodularium`).

| Recipe | Machine | Time | Energy | Outputs |
|---|---|---|---|---|
| `alloy_smelter_diamond` | alloy_furnace | 5 | 100 FE/t | 1x diamond |
| `alloy_smelter_modularium` | alloy_furnace | 120 | 20 FE/t | 4x modularium |
| `centrifuge_centrifuge_blaze_powder` | iron_centrifuge | 60 | 16 FE/t | gunpowder 95%, glowstone dust 40% |
| `centrifuge_centrifuge_grass` | iron_centrifuge | 120 | 16 FE/t | dirt, + 7 chances (70/5/10/10/40/30/30/1%) |
| `centrifuge_centrifuge_magma_cream` | iron_centrifuge | 60 | 16 FE/t | slime ball 95%, blaze powder 95% |
| `centrifuge_centrifuge_wool` | iron_centrifuge | 60 | 16 FE/t | 2x string, 2x string 75%, string 40%, string 20% |
| `centrifuge_wash_glowstone` | iron_centrifuge | 120 | 16 FE/t | gunpowder 60% |
| `centrifuge_wash_redstone` | iron_centrifuge | 180 | 16 FE/t | gunpowder 60% |
| `power_transformer_energy_transform` | transformer | 1 | 128 FE/t in / 128 FE/t out | — |

`alloy_smelter_furnaces.adapter.json` is **not** ported: recipe adapters (converting a vanilla furnace into a
machine recipe) are a separate feature.

### ⚠️ Breaking change

The flat `input`/`output`/`duration`/`energy` schema is gone. A recipe using it is now a load error naming the
missing `requirements` array. `kubejs-examples/server_scripts/machines.js` shows the new shape.

### JEI

The category lays item inputs and outputs out as grids and summarises everything that is not an item slot —
energy rates, fluids, probabilities, amount ranges — in a text block underneath. Previously it drew one input
slot, one output slot and one line of text.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.8.0.jar`.
- **All 9 ported recipes validated statically** against the same rules the loader applies (schema shape,
  `type`/`io-type` values, required fields, machine existence): **0 problems**. Every item reference is either
  a curated confirmed 1.20.1 id or one of the four mapped ore dictionary names.
- **Chance semantics cross-checked with the original**: `ResultChance.canWork(float)` returns true for
  `chance >= 1`, false for `chance <= 0`, otherwise `chance > rand.nextFloat()`. The port rolls
  `random.nextFloat() < chance`, which is the same predicate.
- Language keys unchanged at 75 per file.

### Statistical evidence is pending an in-game run

The plan's M2 acceptance asks for statistical evidence of the probabilistic outputs, and that **cannot be
produced by static analysis**. What is here is the exact rate table above; verifying it means running
`centrifuge_centrifuge_blaze_powder` (95% / 40%) or `centrifuge_wash_redstone` (60%) a few hundred times and
comparing the observed rates. The roll logic itself is verified against the original, as noted above.

## Known limitations

- Not ported requirement kinds: `gas`, `gas_pertick`, `fluid_pertick`, `item_durability`,
  `ingredient_array_input`, `interface_number_input`, `catalyst`. An unknown kind is a load error, not a
  silently skipped requirement.
- Recipe adapters (`adapter` + `modifiers`) are not implemented.
- `minAmount`/`maxAmount` are honoured on outputs; an input using a range is a load error rather than a guess.
- `failure-action` is still parsed but not consumed: a craft interrupted by a broken structure loses the
  inputs it already paid, which is the original's `reset` behaviour, but the field does not yet select
  anything.
- The structure preview/blueprint tooling, the upgrade/parallel/factory systems, all mod integrations and the
  original GUI remain unimplemented.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

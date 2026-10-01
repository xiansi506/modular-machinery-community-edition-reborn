# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.6.0 — milestone M1: data-driven machine definitions replace the hardcoded structure.**

## What 0.6.0 changes

### The structure is no longer hardcoded

0.4.x/0.5.x formed a machine when the eight blocks around the controller on its own Y level were iron block or
a hatch. The original mod instead read a machine definition from JSON and matched an arbitrary, multi-layer
pattern of blocks. 0.6.0 implements that layer.

- **`MachineDefinition`** — a loaded machine: registry name, display name, pattern, `failure-action`,
  `requires-blueprint`.
- **`MachinePattern`** — relative positions (the controller sits at the origin and is excluded) mapped to the
  list of accepted blocks at each position. Any shape, any number of Y levels; the bounding box is derived
  from the coordinates.
- **`BlockMatcher`** — one accepted block. The original matched on *block + metadata*; 1.20.1 has no metadata,
  so the equivalent is *block + blockstate properties*: `ns:path` accepts any state of the block (the
  original's "no `@meta`" case, which is how `casings_all` accepts every casing), and
  `ns:path[key=value,…]` adds property constraints.
- **`MachineRegistry`** — replaces the hardcoded `"basic"` check. Patterns are pre-rotated for all four
  horizontal facings and the controller's `FACING` selects which one is tested, mirroring the original, which
  rotated the pattern counter-clockwise until it matched the controller.
- **`MachineLoader`** — a data pack reload listener reading `data/<namespace>/machinery/`.
- **`MachineStructure`** (the hardcoded ring) is deleted.

### Definitions live in the data pack

```json
{
  "registryname": "transformer",
  "localizedname": "Power Transformer",
  "failure-action": "reset",
  "requires-blueprint": false,
  "parts": [
    { "x": 1, "y": -1, "z": 0, "elements": "minecraft:stone_bricks" },
    { "x": -1, "y": 0, "z": 1, "elements": "modular_machinery_reborn:energy_output_hatch" },
    { "x": 0, "y": 0, "z": 2, "elements": "casings_decorative" }
  ]
}
```

`x`/`y`/`z` accept a number or an array of numbers; an array expands to the Cartesian product of positions,
as in the original `buildPermutations`. An omitted coordinate is `0`. `elements` accepts a string or an array.

### Variables

`data/<namespace>/machinery/<name>.var.json` maps a name to a list of block descriptors, the counterpart of the
original `default_variables/casings.var.json`. `casings_all`, `casings_decorative`, `casings_fluid`,
`casings_energy` and `casings_item` are shipped, so definitions can say `"elements": "casings_all"` instead of
repeating every casing and hatch.

### Casing

`blockcasing` with its six variants (`plain`, `vent`, `firebox`, `gearbox`, `reinforced`, `circuitry`) is
migrated. It was unavoidable for M1: the built-in definitions use it directly *and* through the variable sets,
so the machines could not be ported without it. Textures are pre-composited from the original background and
overlay sheets, following the 0.2.6 decision that avoided translucent co-planar overlays.

### 1.12.2 metadata is rejected, not guessed

There is no general mapping from 1.12.2 metadata to 1.20.1 blockstate properties, so `ns:block@N` is a parse
error with an explanatory message rather than a silent mis-match. The ported definitions spell variants out:
`modularmachinery:blockcasing@4` becomes `modular_machinery_reborn:blockcasing[casing=reinforced]` (the
original's `getMetaFromState` returned the enum ordinal, so 4 is `REINFORCED`).

### Recipes bind to a real machine

The `machine` field of a recipe now resolves to a registry name and is compared against the machine actually
formed at the controller, instead of the hardcoded `"basic"` / `"machine_controller"` strings.

## Ported built-in machines

| Definition | Parts | Structure | Source |
|---|---|---|---|
| `power_transformer.json` (`transformer`) | 32 | **3×5×3, Y −1..3 (five layers)** | `power_transformer.json` |
| `iron_centrifuge.json` | 26 | 3×3×3, Y −1..1 | `iron_centrifuge.json` |
| `alloy_furnace.json` | 26 | 3×3×3, Y −1..1 | `alloy_furnace.json` |

`assembly_line` is **not** ported: its identity is a `dynamic-patterns` line of variable length (5–15 blocks),
which M1 does not implement.

## Not implemented yet (accepted but not evaluated)

These original fields are still parsed or recognised but not acted on; the loader logs a warning naming them so
they are not silently dropped: `modifiers`, `dynamic-patterns`, `color`, `prefix`, `has-factory`,
`factory-only`, `hide-components-when-formed`, `controller-bounding-box`, plus the per-part `nbt`,
`preview-nbt` and `selector-tag`. `failure-action` is parsed and carried but the machine still always resets
progress, and `requires-blueprint` is not enforced yet.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.6.0.jar`.
- Every block descriptor in every definition was resolved against the registered blocks and their real
  blockstate property names and values; every variable reference resolves: **0 problems**.
- Derived structure sizes confirm the multi-layer case: `power_transformer` spans Y −1..3, i.e. five layers.
- Language keys: 72 keys in each of `zh_cn.json` and `en_us.json`, covering 59 registered items, 8 blocks and
  6 UI strings — **0 missing, 0 orphans**.

## Known limitations

- Tiered hatches are still decorative: they have no block entity, no capability routing, and the tier capacity
  values in `HatchTier` are not consumed.
- The controller GUI is still a programmatic 1.20.1 panel, not a reproduction of the original
  `guicontroller_large.png` layout, and it does not yet show the formed machine's name.
- The requirement/component recipe engine (fluid, gas, per-tick consumption, chance outputs, min/max amounts,
  catalysts, fuel), the structure preview and blueprint tooling, the upgrade/parallel/factory systems, all mod
  integrations and the original GUI remain unimplemented.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

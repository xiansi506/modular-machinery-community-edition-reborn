# Modular Machinery: Community Edition Reborn

**Release 0.18.0 — M6a: the upgrade data layer, plus the M6 break-down.**

## What this release is

"M6 — upgrades and parallelism" turned out not to be one milestone but five independent subsystems totalling
roughly 3600–4800 new lines. This release is the reconnaissance, the split, and the first slice: the **upgrade data
layer**, which is the only slice with no runtime behaviour and the prerequisite the other four share.

See **[M6-升级与并行-分册.md](../../../modular-machinery-reborn/docs/专项/M6-升级与并行-分册.md)** for the full survey,
dependency-ordered split, data-model design and risks.

## Three premises overturned during reconnaissance

1. **The original's `UpgradeType` is not a hardcoded enum.** It is a plain class instantiated from CraftTweaker
   scripts via `RegistryUpgrade.registerUpgrade` (`MachineUpgradeBuilder.java:327`). What *is* hardcoded is the
   parallel/bus **tier** enums (`ParallelControllerData.java:9-14`, `UpgradeBusData.java:8-14`).
2. **The original's "parallelism" is not a thread pool — it is a numeric reduction.**
   `RecipeCraftingContext.getMaxParallelism` (`:434-455`) starts from the active recipe's value and takes the
   minimum across parallelizable requirements. `FactoryRecipeThread`'s own class comment admits it is "not threads in
   the real sense". Parallelism (settling N copies of one recipe) and the factory (N different recipes at once) are
   **two different features** and must ship as separate slices.
3. **`maxParallelism` / `internalParallelism` / `maxThreads` are not in the original machine JSON schema** — they came
   from Config and CraftTweaker only.

## The split

| Slice | Content | Depends on |
|---|---|---|
| **M6a** | Upgrade data layer — **this release** | — |
| M6b | Upgrade bus (capability + block + GUI, texture `guiupgradebus.png`) | M6a |
| M6c | Recipe modifiers + parallel-limit computation | — (this is what unblocks the three recorded items) |
| M6d | Parallel controller (5 tiers, `guismartinterface.png`) + smart interface | M6c |
| M6e | Factory controller (`guifactory.png` 280×213 + `guifactoryelements.png`) | M6d; the only slice touching registration |

M6c was placed before M6d deliberately: the parallel limit is a pure number, so it can be verified by setting it
above 1 on a machine field without needing any new block — otherwise acceptance reduces to "a number appeared in the
GUI", which the plan explicitly forbids as a weak standard.

## M6a data format

`data/<ns>/upgrade/<name>.json` or `config/modular_machinery_reborn/upgrade/<name>.json`; `<name>.item.json` maps an
upgrade to its item.

```json
{ "name": "modular_machinery_reborn:example_speed", "localizedname": "Example Speed Upgrade",
  "level": 1.5, "max-stack": 4, "dynamic": false,
  "descriptions": ["An example upgrade declared by a data pack."] }
```

Writing both a `compatible-machines` whitelist and an `incompatible-machines` blacklist is a **load error** naming
what to delete; unknown `upgrade` or `item` references **error** with the value to write instead; unknown machine
names **warn** (cross-pack ordering cannot be guaranteed); unknown field names warn (typo guard). Compatibility is
stored as registry names rather than machine objects because `/reload` replaces every `MachineDefinition` instance —
holding objects would pin the previous generation. The config directory wins over the data pack, matching the D9 rule
for machine definitions.

## Verification

Independently re-checked after the build: `mods.toml` 0.18.0; **zero** files under `data/`; language files
**127 keys each, identical sets** (no keys added); 12 GUI textures; the renderer invariants intact
(`viewport.m11` positive, `Lighting.setupLevel` retained, `cameraView` present, no negative Y scale). Build
`BUILD SUCCESSFUL in 18s`. Jar SHA-256
`F387D918159EC1DD314B1EACF69ECD371231B80D2BAC0B176D5808DF07D27897`, deployed with a matching hash.

**M6a is compile-verified only.** The loader logic (two-pass parsing, declaration merging, every warning branch) has
no offline test, because the project has no test infrastructure at all — `src/test` is empty and `build.gradle` does
not even configure JUnit. That is this release's largest gap.
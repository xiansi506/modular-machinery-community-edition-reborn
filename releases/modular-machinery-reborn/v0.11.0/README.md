# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.11.0 — milestone M10: the mod ships no content.**

## What 0.11.0 changes

### Machines and recipes left the jar

The mod no longer contains any machine definition or recipe. `data/` is gone from the jar entirely.

Everything that used to be bundled now lives in `example-datapack/`, a normal data pack:

| Moved | Was | Now |
|---|---|---|
| Machines | `data/modular_machinery_reborn/machinery/*.json` | `example-datapack/data/.../machinery/` |
| Variable set | `casings.var.json` | same, in the data pack |
| Recipes | `data/modular_machinery_reborn/recipes/**` | `example-datapack/data/.../recipes/**` |

A fresh install is therefore a framework: no machines, no recipes, and no controllers beyond the generic
`machine_controller`. Install the data pack to get the three example machines and their nine recipes.

This also removed the three bundled controllers (`alloy_furnace_controller` and friends) — with their machines
gone they could never form. The jar dropped from 11 blockstates to 8 and from 62 items to 59.

### Controller claims

A data pack cannot create blocks: registration happens while the mod is being constructed, and data packs are
read afterwards. So a data-pack machine is stuck with the generic controller — unless it is *claimed*.

A claim is a file in `config/modular_machinery_reborn/machinery/` that names a machine **without defining it**:

```json
{ "registryname": "alloy_furnace" }
```

There is no flag to remember: a file with `parts` is a definition, a file without is a claim. Claims are
registered at startup as `<registryname>_controller`, bound to that machine; the definition itself can stay in
the data pack and keep being edited with `/reload`. That is how the bundled example machines get dedicated
controllers — `example-datapack/claims/` holds the three claims, copy them into the config directory and
restart.

A claim whose machine never loads is reported at startup, because it would otherwise be an invisible dead block.
And without a claim, the loader names the machines that fell back to the generic controller.

### Recipes from the config directory

`config/modular_machinery_reborn/recipes/**` is now a recipe source, the other half of the original's
config-directory story (it read `config/modularmachinery/recipes/`).

1.20.1 has no hook for injecting recipes: they only ever come from a `RecipeManager` reload. So rather than
adding a second loading path, the directory is exposed **as** a data pack. A file at
`recipes/alloy_smelter/diamond.json` becomes the recipe id `modular_machinery_reborn:alloy_smelter/diamond`, and
editing it is picked up by `/reload`. The pack is contributed only when the directory holds something, and only
as `SERVER_DATA`.

If a data pack defines the same recipe id as a config-directory file, the two compete under normal data pack
priority; distinct ids avoid the question.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.11.0.jar`.
- **The jar contains zero files under `data/`.** Blockstates 8, item models 59.
- Language keys: 82 in each language file, **0 missing and 0 orphans** checked against resolved description ids.
  Six of them are deliberately reserved — three controller fallback names and three machine display names — for
  use when a data pack machine is claimed. Data packs cannot ship language files, so the Chinese names have to
  come from here.
- **The data pack is self-consistent**: all three machine definitions resolve every `elements` entry to a
  namespaced block id or a variable defined in `casings.var.json`, and all nine recipes reference a machine
  defined inside the same pack. That check matters now that the mod provides no machines at all.
- The three claim files are recognised as claims (they carry `registryname` and no `parts`).

### Runtime verification is still outstanding

The config directory is read through `FMLPaths.CONFIGDIR`, which only exists inside a running game, so neither
the claim mechanism nor the recipe pack can be exercised offline. Verifying them means installing
`example-datapack`, copying `claims/` into the config directory, restarting, and checking that the three
controllers appear and form only around their own structures.

## Known limitations

- No per-machine factory controllers, no MOC compatibility namespace, no per-machine JEI categories, no `color`
  tinting.
- On a dedicated server a claimed controller's item name falls back to its language key, because machine
  definitions are not synced to clients yet.
- The structure preview/blueprint tooling, the upgrade/parallel/factory systems, mod integrations and the
  original GUI remain unimplemented.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.10.0 — milestone M9: pack authors get controllers of their own.**

## What 0.10.0 changes

### The machine directory is back

The original kept its machine definitions in `config/modularmachinery/machinery/` and discovered them in
`MachineRegistry.preloadMachines()` **before** blocks were registered. That ordering is the entire reason a pack
author could add a machine and receive a controller block of its own — and it is what 0.9.0 was missing, since it
could only give dedicated controllers to machines compiled into the mod.

0.10.0 restores it:

```
config/modular_machinery_reborn/machinery/
├── my_machine.json            <- registryname "my_machine" -> my_machine_controller
└── variables/
    └── casings.var.json       <- variable sets, same role as the data pack's
```

Each file's `registryname` decides the block: `my_machine` becomes `my_machine_controller`, bound to
`modular_machinery_reborn:my_machine` and only ever validating that machine's structure. The file is read twice
for two different reasons — once during mod construction, where only `registryname` is needed to register the
block, and once per reload, where the full definition is loaded.

So both goals hold at once: every machine can have its own controller, and pack authors can add machines.

### Two sources, one schema

| Source | Path | Dedicated controller |
|---|---|---|
| Config directory (**the one to use**) | `config/modular_machinery_reborn/machinery/` | yes |
| Data pack | `data/<namespace>/machinery/` | no — generic controller only |

Both use the same JSON. When the same `registryname` appears in both, **the config directory wins**, since it is
the instance-local override. The data pack route is unchanged from 0.7.0, so nothing regresses for data pack or
KubeJS users; those machines simply keep using the generic `machine_controller`.

### Restart, not reload

Blocks can only be registered at construction, so a machine dropped into the directory **after** launch is
loaded and usable but has to wait for the next start to gain its controller. The loader says so rather than
leaving it a mystery:

```
[...] 1 machine(s) have no controller block of their own and use the generic machine_controller:
modular_machinery_reborn:my_machine. Put a definition in <config>/modular_machinery_reborn/machinery
to give one its own controller (it takes effect on the next launch).
```

A registered controller whose machine never loads is reported too, because that is a dead block.

### Generated models

A controller block with no blockstate file renders as the missing model, and the jar can only ship files for the
machines it knows about. So the mod contributes a synthetic client resource pack containing a blockstate and an
item model for each pack-author controller. This is the same trick the original used — `RegistryBlocks`
`writeControllerModel` wrote a copy of `blockstates/block_machine_controller.json` per registered machine.

All controllers share one model, so the generated files differ only by name. The pack is only contributed when
there is something in it, so a normal install's pack list is untouched.

### Example

`examples/pack-author/` holds a complete, copyable example: a 3×3 casing plate machine plus a recipe, and a
README explaining the layout and why a restart is needed.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.10.0.jar`.
- **The generated blockstate is character-for-character identical to the shipped
  `blockstates/machine_controller.json`**, and all four facing variants plus the item model's parent match.
  Checked by reconstructing the Java string constant and comparing it to the jar's copy.
- The directory scan rules were re-implemented independently and run against `examples/pack-author/`: the
  example resolves to one machine, `example_machine`, giving `example_machine_controller` bound to
  `modular_machinery_reborn:example_machine`; `.var.json` files and anything under `variables/` are correctly
  excluded; the example's `elements` resolves to the registered `blockcasing` block; the example recipe's
  `machine` field matches the definition's `registryname`.
- Language keys unchanged; the built-in three controllers still ship their own blockstates.

### Runtime verification is still outstanding

The directory scan depends on `FMLPaths.CONFIGDIR`, which only exists inside a running game, so it cannot be
exercised offline. Confirming it means copying `examples/pack-author/config` into an instance, restarting, and
checking that `example_machine_controller` appears in the creative tab, forms only around its own structure, and
renders with the controller texture rather than the missing model.

## Known limitations

- **Recipes still come from the data pack only.** The original also loaded them from
  `config/modularmachinery/recipes/`; that half of the config-directory story is not ported, so a pack-author
  machine needs a data pack for its recipe. See `移植方案-v2.md`.
- On a dedicated server a pack-author controller's item name falls back to its language key, because machine
  definitions are not synced to clients yet.
- No per-machine factory controllers, no MOC compatibility namespace, no per-machine JEI categories, and no
  `color` tinting — unchanged from 0.9.0.
- Everything else from 0.8.0/0.9.0 stands: the structure preview/blueprint tooling, the upgrade/parallel/factory
  systems, mod integrations and the original GUI remain unimplemented.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.9.0 — milestone M8: per-machine controllers (the original's default mode).**

## What 0.9.0 changes

### Every built-in machine now has a controller of its own

The original registered a controller block per machine and did so **by default**
(`Config.onlyOneMachineController` defaults to `false`; its comment reads "When enabled, Modules no longer
register a separate controller for each machine"). Only the generic `blockcontroller` was registered
unconditionally. 0.9.0 restores that default side:

| Block | Bound to | Item name |
|---|---|---|
| `machine_controller` | nothing — finds its machine from the structure | Machine Controller |
| `alloy_furnace_controller` | `modular_machinery_reborn:alloy_furnace` | Alloy Smelter Controller |
| `iron_centrifuge_controller` | `modular_machinery_reborn:iron_centrifuge` | Iron Reinforced Centrifuge Controller |
| `transformer_controller` | `modular_machinery_reborn:transformer` | Power Transformer Controller |

A bound controller only ever checks **its own** machine's pattern, so it cannot become a different machine.
That is the original's behaviour, where `TileMultiblockMachineController.checkStructure` validated the
controller's own `DynamicMachine`. The generic controller still searches every definition, which is what a
data-pack-added machine needs.

Controllers are named after their machine through the original's own translation key shape,
`%s Controller` / `%s控制器`, using the machine's data pack display name. Machine display names are now
translatable too — the original shipped no such keys, so its built-in machines showed their English
`localizedname` in every language; this release uses the override key the original already looked up
(`<namespace>.<path>`).

### Why the bound set is a constant in code

This is the one place where the port could not follow the original. Blocks may only be registered while the
mod is being constructed; machine definitions are loaded from the data pack afterwards, and a data pack can be
reloaded at runtime. The original got around this by loading its machines *before* registering blocks.

So the bound set lives in `ModBlocks.BOUND_CONTROLLER_MACHINES`. The loader now warns when a name there has no
matching definition, which would otherwise leave a block that can never form:

```
[modular_machinery_reborn] alloy_furnace_controller exists as a block but no machine definition
'alloy_furnace' was loaded, so it can never form.
```

Machines added by a data pack still work through the generic `machine_controller`; they just do not get a
block of their own. That is the same limitation the original had for machines introduced after startup.

### JEI

Every controller is registered as a recipe catalyst, so recipe pages offer the generic controller and all
three bound ones. JEI has no per-recipe catalyst binding, so narrowing a page to one machine's controller
would need per-machine JEI categories — the original's "per-machine dynamic category" — which is not in this
release.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.9.0.jar`.
- Registry: 62 controller/casing/hatch/standalone items and 11 blocks; 11 blockstates, each resolving to an
  existing model.
- Language keys: 82 keys in each of `zh_cn.json` and `en_us.json` — **0 missing, 0 orphans**, checked against
  the real resolved description ids.

## Known limitations

- **No per-machine factory controllers.** The original added `<machine>_factory_controller` when
  `has-factory` was set. The factory system itself is not implemented yet (it is milestone M6), so there is
  nothing for such a block to do. `has-factory` and `factory-only` are still parsed but not evaluated.
- The MOC compatibility namespace (`modularcontroller:<machine>_controller`, which existed to keep old saves
  loading) is not reproduced; it has no meaning for a new 1.20.1 mod.
- Machine definitions are not synced to clients yet, so on a dedicated server a bound controller item falls
  back to its shipped language key instead of the data pack's display name. Single player and the integrated
  server resolve the definition normally.
- Everything else from 0.8.0 stands: the structure preview/blueprint tooling, the upgrade/parallel/factory
  systems, mod integrations and the original GUI remain unimplemented.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

This first migration slice provides:

- `modular_machinery_reborn:machine_controller`, a controller block that consumes one held item and applies a matching machine recipe.
- `modular_machinery_reborn:machine`, a data-driven recipe type with `machine`, `input`, `output`, `duration`, and `energy` fields.
- Optional KubeJS 6 schema: `event.recipes.modular_machinery_reborn.machine({...})`.

The original archive is retained at `../ModularMachinery-Community-Edition-master.zip`. The old project targets Forge 1.12.2/Cleanroom APIs, so its multiblock, GUI, capability, and integration code must be migrated in separate slices rather than copied into Forge 1.20.1 wholesale. The controller currently provides a deliberately small vertical slice for validating recipes and scripting.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

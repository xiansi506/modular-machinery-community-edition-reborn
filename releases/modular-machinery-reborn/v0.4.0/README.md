# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

This first migration slice provides:

- `modular_machinery_reborn:machine_controller`, a controller block that consumes one held item and applies a matching machine recipe.
- `modular_machinery_reborn:machine`, a data-driven recipe type with `machine`, `input`, `output`, `duration`, and `energy` fields.
- Optional KubeJS 6 schema: `event.recipes.modular_machinery_reborn.machine({...})`.
- The controller forms when the eight blocks around it are `minecraft:iron_block`.
- The ring also accepts `item_input_hatch`, `item_output_hatch`, `energy_input_hatch`, and `fluid_input_hatch` as port blocks.
- The block entity exposes Forge item, FE, and fluid capabilities and processes recipes over `duration` ticks.
- The client menu shows input/output slots, structure state, progress, and stored FE; JEI lists all loaded machine recipes.
- The standalone 1.12.2 items are registered with their legacy textures: blueprint, modularium, construction tool, machine projector, redstone signal, and wrench.
- Input, output, energy, and fluid hatches are available in the legacy tiers from tiny through ludicrous; fluid hatches also include vacuum.

The original archive is retained at `../ModularMachinery-Community-Edition-master.zip`. The old project targets Forge 1.12.2/Cleanroom APIs, so its much larger collection of dynamic shapes, ports, specialized components, and legacy integrations cannot be copied into Forge 1.20.1 wholesale. This 0.2.0 slice provides the reusable 1.20.1 runtime contracts; additional component types can build on the same capability and recipe interfaces.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

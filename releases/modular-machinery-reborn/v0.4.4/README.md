# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.4.4 — localization alignment.**

## What 0.4.4 changes

This release changes display names only. No Java source, behaviour, recipe format or asset file was modified.

**Chinese (`zh_cn.json`)** — all names realigned to the original Modular Machinery: Community Edition official translation:

- `itemmodularium` was mistranslated as 模块铀 ("uranium"); corrected to **模块化合金锭**.
- Tiered hatches now use the original `<tier><type>` order (e.g. 微型物品输入仓) instead of the suffix form.
- `normal` is **中型** (was 普通) and `ludicrous` is **超级** (was 荒谬级), matching the original.
- `machine_controller` → 机械控制器, `itemblueprint` → 机械蓝图, `itemconstructtool` → 建造选择工具, `machine_projector` → 机器结构投影仪.
- The creative tab is localized: **模块化机械 - 社区版：重生** (previously English-only).

**English (`en_us.json`)** — realigned to the original official English:

- All 48 tiered hatch keys use the original prefix form, e.g. `Tiny Item Input`, `Tiny Fluid Input Hatch`, `Ludicrous Energy Output Hatch`.
- `itemconstructtool` restored to `Construct Selection Tool` (was `Construction Tool`).
- Creative tab is `Modular Machinery Community Edition: Reborn`.

> Note: the original mod did not write "Hatch" in the **item** bus names (`Tiny Item Input`) while fluid and energy hatches do (`Tiny Fluid Input Hatch`). This release mirrors the original exactly.

## Runtime contents

- `modular_machinery_reborn:machine_controller`, a controller block that consumes one held item and applies a matching machine recipe.
- `modular_machinery_reborn:machine`, a recipe type with `machine`, `input`, `output`, `duration`, and `energy` fields.
- Optional KubeJS 6 schema: `event.recipes.modular_machinery_reborn.machine({...})`.
- The controller forms when the eight blocks around it are `minecraft:iron_block`; the ring also accepts `item_input_hatch`, `item_output_hatch`, `energy_input_hatch`, and `fluid_input_hatch` as port blocks.
- The block entity exposes Forge item, FE, and fluid capabilities and processes recipes over `duration` ticks.
- The client menu shows input/output slots, structure state, progress, and stored FE; JEI lists all loaded machine recipes.
- Standalone 1.12.2 items are registered with their legacy textures: blueprint, modularium, construction tool, machine projector, redstone signal, and wrench.
- Input, output, energy, and fluid hatches are available in the legacy tiers from tiny through ludicrous; fluid hatches also include vacuum.

## Known limitations

The port is still a foundation slice, not a feature-complete migration. The data-driven machine definition layer, the requirement/component recipe engine, the structure preview and blueprint tooling, the upgrade/parallel/factory systems, and all mod integrations are not implemented yet. Tiered hatches are currently decorative placeholders without block entities or capability routing, and `MachineStructure` does not accept tiered hatches. See `移植方案-v2.md` in the project tree for the revised plan.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

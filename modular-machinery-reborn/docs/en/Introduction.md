# MMCER introduction

**Modular Machinery: Community Edition Reborn · 0.123.1 · Minecraft 1.20.1 / Forge / Java 17 · protocol 13**

Turn your own structures into processing machines. Define machines and recipes with JSON or KubeJS, then add resource ports, parallel processing, factory threads and upgrades. The mod does not bundle machine definitions or processing recipes; optional examples are provided separately.

## Main features

- **Structure authoring:** block states, NBT, alternative materials, component tags, dynamic sections and dedicated machine/factory controllers. The structure tool exports complete JSON/KubeJS definitions. Selection gestures remain unchanged; export settings are simpler.
- **Standalone Recipe Editor item:** right-click to open, or use on a controller to select its machine. The simple canvas supports input/output slots, localized-name search, JEI drops, amount scrolling, seconds and FE/t. Advanced forms, NBT, parallel limits, source editing and KubeJS exports remain available. Version 0.123.1 reserves room for the right-side JEI list.
- **Resources and processing:** items, fluids, long energy, Mekanism gases, Botania mana, Nature’s Aura, Blood Magic 3 LP/Will, PneumaticCraft air and Embers Rekindled Ember; probabilities, output ranges, catalysts and modifiers.
- **Automation and scripting:** unified ME buses, the **ME Machinery Pattern Provider** and **ME Machinery Pattern Mirror Image**, factory/core threads, upgrades, input groups, lifecycle callbacks, persistent script state and client GUI/model/animation hooks. ME gas requires AE2, Mekanism and Applied Mekanistics.
- **Energy and visuals:** eight tiers of wireless input/output hatches join Flux Networks directly, using its native network selection and permissions. Draconic Evolution OP/long interfaces, Jade energy views, JEI multiblock previews, blueprints, world ghosts and GeckoLib material effects are supported.

Install integrations and their own dependencies as needed; see the [project integration list](../../../README.md). Other recipe adapters, energy backends and magic systems were not added in this update. New wireless textures remain a design only.

## Usage and validation

Creator tools require creative mode and permission level 2. They export server files; install them manually and reload. New registered controllers require a restart. Update both client and server, back up worlds and finish in-flight crafts before upgrading.

Production builds and scoped offline regressions passed. Live GUI rendering, JEI interaction, full optional-mod combinations, world reloads and multiplayer still require in-game acceptance. Old CraftTweaker/ZenScript APIs do not run directly. See [usage notes and limitations](../已知限制.md) for supported contracts and migration boundaries.

[Structure tool](Construct-tool.md) · [Recipe Editor](Visual-recipe-editor.md) · [Energy](Energy-and-special-rendering.md) · [Documentation](README.md) · [中文](../简介.md).

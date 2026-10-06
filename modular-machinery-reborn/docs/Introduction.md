# Modular Machinery: Community Edition Reborn — Introduction

> ## ⚠️ WORK IN PROGRESS — **not finished**
>
> Current version **0.30.1**. Everything under "Done" below has been **verified in game**. Everything under "Not done" is **not usable yet**. Please **keep this status banner** wherever you repost this text.

---

## What this is

A reimplementation of **Modular Machinery: Community Edition** (Minecraft 1.12.2) for **Minecraft 1.20.1 / Forge**.

**It is a framework**: the mod ships **no machines and no recipes of its own**. You supply the multiblocks and the recipes, by any of three routes:

- **Data pack** JSON (recommended; takes effect on `/reload`)
- **Config directory** `config/modular_machinery_reborn/machinery/` (after a **restart**, the machine gets a **dedicated controller block**)
- **KubeJS** scripts in the server-script directory (takes effect on `/reload`)

See `docs/使用说明.md` (Chinese) and `docs/KJS-配方指南.md` for the authoring guides.

---

## ✅ Done — verified in game

**Machine definitions**
- Three sources (data pack / config directory / KubeJS); multi-layer and arbitrary shapes; matching rotated to the controller's facing
- Blueprint binding and `requires-blueprint`
- Controller-claim mechanism, so data-pack machines can have a dedicated controller too
- **KubeJS can write a complete definition** (0.31.0): every one of `modifiers` / `smart-interfaces` /
  `has-factory` / `factory-only` / `max-threads` / `core-threads` / the three parallelism fields /
  `failure-action` / `requires-blueprint` has a method, nested entries take an object literal or a JSON string,
  and validation goes through the **same schema** a data pack uses — the sentences are identical. See
  [`KJS-配方指南.md`](KJS-配方指南.md) §六 for the table
- **The two activation paths are kept apart**: a data change (structure, recipe, upgrade, script) takes effect on **`/reload`**;
  **adding a block** (a dedicated controller, a factory controller) needs a **restart** — the rule and the full table are in [`使用说明.md`](使用说明.md) §六

**Recipe engine**
- Requirement-list schema; types `item` / `fluid` / `energy` / `interface_number_input` / `ingredient_array_input`
- Fluid can be consumed or produced **per tick**: add `"perTick": true` to a `fluid` requirement (the original's separate
  `fluid_pertick` kind returns `null` from `createRequirement` — a dead type with no JSON entry point)
- Inputs consumed at start, energy drained per tick, outputs rolled independently per completion
- The original's ore-dictionary syntax (`ore:`) mapped to tags; the original's namespace (`modularmachinery:`) accepted unchanged

**Hatches and port capabilities**
- Item / fluid / energy hatches hold their storage and expose capabilities; tier textures; fluid rendered by colour

**User interface (reproduced from the original)**
- Machine controller screen (176×213), hatch screens, factory controller screen (280×213)
- **3D structure preview** — right-click a blueprint; the preview panel's **button row is complete** (reset view / layer toggle / per-layer 2D view / layer scrollbar); a JEI "machine structure" category; machine info including the parallelism/thread rows
- Recipe JEI pages (input quantities, and the blocks a machine is built from)
- **One JEI category per machine** — every machine gets its own category and catalysts (that machine's controller plus a blueprint bound to it), rather than all controllers sharing one

**Upgrades / parallelism / factory**
- Upgrade bus in 5 tiers (3/6/9/12/18 slots); upgrade effects declared as data
- Parallel controller in 5 tiers (4 / 16 / 64 / 256 / 512), with N copies genuinely settled
- Factory controller: several different recipes running at once, core threads, a thread ceiling
- Recipe modifiers (duration / output / input / chance / energy)

**Smart data interface**
- Merged into the controller (no separate block); consumed by the `interface_number_input` requirement

**Structure construct tool**
- WorldEdit-wand-style selection, white outline, rotation to the controller's facing, and **export of a machine-definition fragment** into the config directory

**Compatibility**
- The old ModularController's `modularcontroller` namespace, so controllers in older saves survive — with a migration prompt

---

## ❌ Not done — not usable yet

**Structures**
- **Variable-size structures (`dynamic-patterns`) are not implemented**, so the built-in **assembly line (`assembly_line`) is missing** — 3 of the original's 4 built-in machines are ported

**Recipes**
- Gas requirements: `gas` / `gas_pertick` (need the Mekanism gas API)
- Recipe adapters (mechanically routing another mod's recipes into one of these machines)

**Interface and presentation**
- `color` tinting
- The full `craftcheck.failure.*` failure text (a machine waiting for resources still just shows `idle`)

**Tools**
- The **projector `machine_projector`** (world-space ghost preview) — deliberately not in this release
- A **real "select a region and place the blocks" builder** — the original **has no such feature**. This mod's construct tool is *selection + definition export*, not a placer. **The owner decided not to build one.**
- `ItemDebugStruct` — **deliberately not ported** (owner's decision, 2026-10-04): the original never registered it and its implementation is broken; see the implementation order's §6.1.

**Scripting**
- AE2 / GregTech / TConstruct and similar compatibility modules (**each optional and standalone**; confirmed to be **in scope**, one module at a time — see the implementation order's wave 5)
- **What the original's CraftTweaker scripts could write is covered here by KubeJS entry points** — the CraftTweaker bridge **itself is not ported**. The machine-definition half is closed by wave 1's eleven extended fields; the recipe and upgrade-declaration half is wave 5.1.

---

## Known limitations

The full user-facing list — including which machine source can have a dedicated controller, the difference between `/reload` and a restart, and the things deliberately not done with the reason why — is in [已知限制.md](已知限制.md) (Chinese).
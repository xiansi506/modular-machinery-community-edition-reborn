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

**Recipe engine**
- Requirement-list schema; types `item` / `fluid` / `energy` / `interface_number_input` / `ingredient_array_input`
- Inputs consumed at start, energy drained per tick, outputs rolled independently per completion
- The original's ore-dictionary syntax (`ore:`) mapped to tags; the original's namespace (`modularmachinery:`) accepted unchanged

**Hatches and port capabilities**
- Item / fluid / energy hatches hold their storage and expose capabilities; tier textures; fluid rendered by colour

**User interface (reproduced from the original)**
- Machine controller screen (176×213), hatch screens, factory controller screen (280×213)
- **3D structure preview** — right-click a blueprint; a JEI "machine structure" category; machine info including the parallelism/thread rows
- Recipe JEI pages (input quantities, and the blocks a machine is built from)

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

**KubeJS machine-definition extended fields**
- `modifiers`, `smart-interfaces`, `has-factory` / `factory-only` / `max-threads` / `core-threads`, the three parallelism fields, `failure-action`, `requires-blueprint`
- KubeJS can currently define only the **core fields** (pattern/parts, display name); everything above still has to be written in a data pack or the config directory

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
- `ItemDebugStruct`
- A **real "select a region and place the blocks" builder** — the original **has no such feature**. This mod's construct tool is *selection + definition export*, not a placer.

**Scripting**
- A CraftTweaker-equivalent API
- AE2 / GregTech / TConstruct and similar compatibility modules (optional, standalone, and excluded from the main-line completion count)

---

## ✅ Once listed as "not done", but **actually delivered long ago**

These four sat in the list above for a long time. The code and the archived release notes both show they
were delivered, so they have been removed from "not done":

- **Per-machine JEI categories** — **done in 0.17.0**: every machine gets its own JEI category and catalysts
  (that machine's own controller plus a blueprint bound to it). 0.16.2 had concluded that JEI could not bind a
  catalyst per recipe; **0.17.0 disproved that**.
- **The preview panel's button row** (reset view / layer toggle / per-layer 2D view / layer scrollbar) —
  **done in 0.16.0**.
- **`fluid_pertick`** — **not a gap**: the original's JSON entry point for that type
  (`RequirementTypeFluidPerTick#createRequirement`) **returns `null`** (the same dead-type shape as
  `item_durability`) and only CraftTweaker could build it; per-tick fluid is expressed here as
  **`fluid` + `"perTick": true`**, which **is implemented**.
- **The `/reload` versus restart difference** — documented in full in [`使用说明.md`](使用说明.md).

---

## Known limitations

The full user-facing list — including which machine source can have a dedicated controller, the difference between `/reload` and a restart, and the things deliberately not done with the reason why — is in [已知限制.md](已知限制.md) (Chinese).
> **0.123.1:** the JEI screen handler now reserves room on the right. The canvas, advanced forms, title and ghost targets share the adjusted bounds; resizing and draft text are preserved. JEI's global overlay toggle remains under your control. Offline layout checks passed; in-game rendering is pending.

> **0.123.0 / protocol 13:** obtain the standalone **Recipe Editor** item from the MMCER creative tab (`/give @s modular_machinery_reborn:recipe_editor`). Right-click to open; use on a controller to select its machine. The default canvas has input/output slots: click to search by localized name or ID, or drag items/fluids/gases from JEI. Scroll changes amounts, Shift-scroll changes ten, right-click removes, left-click opens details. Use More for other resource types. Enter seconds and FE/t, check, review and save. IDs are generated automatically. Advanced mode retains IDs, parallelism, options, NBT and source editing. Saving does not install/reload recipes; use the reviewed recipe ID for the installation path described below. Closing still ends the draft session.

# Visual recipe editor

Version **0.121.62**, Minecraft 1.20.1 / Forge / Java 17, protocol **13**. Update both peers. The first implementation is complete; real input, drag/drop, GUI and multiplayer acceptance are still pending.

Open `/mm-recipe_editor` as a creative player with permission level 2, or `/mm-recipe_editor "namespace:machine"`. The console cannot open a player screen; holding the structure tool is unnecessary.

1. Enter a complete recipe ID, choose a loaded machine, duration in ticks (20 ticks = 1 second), and an optional parallel ceiling. Blank parallelism uses the machine limit. An array of machine ID strings is accepted in the machine field. The server list contains at most 512 machines; additional IDs can be entered directly.
2. Add input/output requirements. Previous/Next selects the requirement; Options switches between resource/count, common options and item NBT. Choosing a different type replaces that requirement with defaults.
3. Choose JSON or KubeJS, validate and review the preview, then confirm export. Any draft edit requires validation again.

JEI allows item, fluid and Mekanism gas drops onto the corresponding ID field. Item drops and the main-hand button retain typed NBT; they do not copy stack counts. Fluid/gas drops choose the resource, leaving the quantity under form control. Without JEI, use complete IDs or the item/tag/fluid/aura/will search list. Gas also accepts direct IDs. Input tags use `#forge:...`; select concrete outputs.

The forms cover item, fluid, gas, energy, Mana, Aura, LP, Will, compressed air and Ember. Energy is always FE/t and supports nonnegative long values; fluid/gas use integer mB, air uses integer mL plus a separate bar condition, Will/Ember accept finite decimals, and other magic resources use integer raw units. Tick mode selects `gas_pertick` and `compressed_air_pertick` where required. Dependencies and runtime resource rules remain those in the [recipe guide](../KJS-配方指南.md), [magic guide](Magic-resources.md), and [PneumaticCraft/Embers guide](PneumaticCraft-and-Embers.md). An asterisk marks an absent dependency; server validation rejects it.

Chance is 0–1 for items/fluids/gas. Output item ranges use minAmount/maxAmount; inputs use fixed quantities. Selector tags and fixed parallel costs retain their existing semantics. Item NBT accepts legacy JSON match templates or a lossless `["mmr:literal_nbt_v1", "<SNBT compound>"]` envelope, including for display NBT. Display NBT needs a match template; use `{}` when no extra condition is needed.

Source is editable JSON. Form patches preserve unrepresented commands, scripting, triggers, modifiers and other advanced fields. Unsupported form types remain editable in Source. Preserving unknown fields does not mean the engine recognizes them. Invalid text stays available for correction. Draft limits are 64 requirements, 16000 Java characters / 24000 UTF-8 bytes, nesting depth 64, and 65536 characters in formatted output.

The preview lists directions, resources, units, tick/craft quantities, chance, selector and pressure, with one visible row of item icons. Base totals apply to **one unmodified craft**. Scripts, modifiers, probability, triggers, output ranges and actual parallelism can change the result. Port hints only inspect the base structure and cannot prove formation, dynamic/custom ports or actual supply.

Exports are written on the **server** to `config/modular_machinery_reborn/exports/recipes/`, using timestamp names and numbered collision suffixes. Existing files are preserved. The client cannot choose arbitrary paths. A reload removing a machine invalidates saving that old preview; disk errors retain the reviewed draft. Closing, dimension changes, respawn, logout and server stop end the session. Copy Source before closing; persistent draft libraries and editing undo are outside this first version.

- JSON: install under `data/<namespace>/recipes/<path>.json` in a data pack, with `pack.mcmeta` using format 15. The installed path determines the recipe ID, so rename the timestamp file appropriately. The export folder itself is not a data pack. The configuration recipe folder can only represent IDs in the `modular_machinery_reborn` namespace.
- KubeJS: install the generated `.js` in `kubejs/server_scripts/`, keeping `.id(...)`. Java Gson parses the JSON string so long energy values beyond 2^53 retain their precision. KubeJS and its dependencies are required. The native custom-recipe schema initialization was also corrected for this path.

Install only one equivalent definition. End ongoing crafts before reloading, run `/mm-syntax`, then test a real craft and relevant tick/parallel behavior. No recipe is automatically activated by the editor.

See the [Chinese guide](../可视化配方编辑器.md) and [0.121.0 record](../归档/版本记录/更新记录-0.121.0.md) for validation boundaries.

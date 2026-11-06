> **0.123.0 / protocol 13:** selection gestures, default single-block toggle, box logic, operations, air filtering and undo/redo are unchanged. Only export UI is simplified. Right-click the controller after selecting, review the suggested ID/name and full-JSON format, then preview and save. Advanced export fields and source preview remain available. Saving still requires installing the exported definition as documented below.

# Structure authoring tool

For **0.121.62 / Minecraft 1.20.1 / Forge**, protocol **13**. Update both peers. Creative mode and permission level 2 are required.

## Workflow

1. Hold the construct tool and sneak-right-click air or a block to open settings.
2. Choose single-block or two-corner box selection, toggle/add/remove, and air filtering. Return to the world and click blocks. Boxes include both corners and create one undo step; a green outline marks the pending first corner.
3. Right-click a machine controller to bind the origin and open export settings. Fill in the machine ID and name, choose field filters and format, then validate and preview.
4. Scroll the read-only source preview, switch pages, and confirm export. Copy page copies only the current page.

Clicking a controller now opens confirmation instead of immediately writing a fragment. Unauthorized players retain ordinary block interaction.

## Selection and substitutions

Selections and box volumes are limited to **32768 blocks**. Unloaded chunks or oversized edits reject the entire operation and keep the existing selection and corner. Undo/redo retain up to 32 operations and a bounded total coordinate count. Undo with a pending corner cancels only that corner. Clear is undoable; new edits discard the redo branch.

The HUD shows count, bounds, mode and corner. White selection outlines remain limited to 32 blocks. Material substitutions use source block IDs and validated target descriptors, up to 16 mappings. They affect exported data only and do not inherit unrelated source block entity NBT. Settings reset pending corners. Dimension changes, respawns and disconnects clear the temporary session; preferences do not persist across restarts.

## Field filters

Block-state fields accept `*`, empty, or a comma-separated list such as `facing,half`. Native states and positions normalize together to a north-facing controller.

Matching and preview NBT have separate filters, both empty by default. Use `*` for all fields, `Energy,Mode` for top-level fields, or `/nested/tier,/ForgeCaps/example:cap` for nested compound paths. JSON Pointer escapes `~0` and `~1` are supported; list indices are not. Missing fields are skipped per block. Check the source preview for typing errors and intended constraints.

Snapshots preserve literal strings, numeric types, typed arrays and empty containers. Inventory, energy and progress become structure conditions if included in matching NBT. Keep only stable fields. Existing hand-written regex/range templates retain their behavior.

## Formats and saving

All files go to the server instance's `config/modular_machinery_reborn/exports/`. Lowercase names and numbered suffixes prevent replacement of old exports. Dedicated-server files must be retrieved from that server.

- parts fragment: merge the parts into a complete definition.
- Full JSON: copy into a data pack's `data/<namespace>/machinery/` or the configuration machinery directory, then reload.
- KubeJS: copy the generated `.js` into `kubejs/server_scripts/`, install KubeJS and reload. Generating the file does not require KubeJS. Install either the JSON or equivalent script to avoid duplicate IDs.

The complete definition is checked with the production parser; generated scripts use the existing `.field("parts", ...)` API. New dedicated controller registration still requires a restart. Processing recipes remain separate.

Saving checks the current session and world again. A stale preview, unreadable chunk, controller beyond 64 blocks or disk failure preserves the selection. Successful writes clear it; undo can restore it. Previews are paged at 8000 UTF-16 code units and complete exports are limited to approximately 4M code units.

The new 33-case offline suite drives production selection/filter/export code and the real Rhino/builder path using lightweight world fixtures. Actual GUI rendering, clicks, outlines, multiplayer and portal transitions still need in-game acceptance. See the [update record](../归档/版本记录/更新记录-0.120.0.md).

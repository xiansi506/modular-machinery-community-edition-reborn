# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.16.0 — the structure preview panel is reproduced from the original, with its features.**

## What 0.16.0 delivers

The 3D preview now replicates the original's panel rather than approximating it. Every position and size comes
from the original's own widget layout, so it can be checked against the source line by line:

| Element | Position | Size | Original source |
|---|---|---|---|
| Title | (5, 5) | 174 × 18 | `title.setAbsXY(5, 5)`; `StructurePreviewTitle` = 36-wide prefix + 136-wide name |
| 3D viewport | (6, 26) | 172 × 150 | `.setWidthHeight(WORLD_RENDERER_WIDTH, HEIGHT).setAbsXY(6, 26)` |
| Ingredient list | (5, 179) | **174 × 36** | `ingredientList.setAbsXY(5, 179)`; `IngredientList` is 174×36 with `MAX_STACK_PER_ROW = 9` |
| Button strip | right-aligned, y=161 | 13 × 13 each | `bottomMenu.setAbsXY(PANEL_WIDTH - w - 6, 161)`, overlaid on the viewport |
| Layer stepper | right-aligned, y=44 | 9 wide | the original's `rightMenu` band |

**The ingredient list was the visible defect**: it had been a single 18-pixel row, 172 wide, with the machine name
printed at y=205 on top of where the original's second row belongs. It is now the original's two rows of nine
18-pixel slots, and the title moved to the top where the original had it.

### Features

1. **Machine info** — size (长/高/宽), the controller's Y position within the structure, and whether the machine
   requires a blueprint.
2. **Reset center** — restores pan and zoom.
3. **Cycle replaceable blocks** — every 1.5 s, the alternate block accepted at each position.
4. **3D ↔ layer preview** — render one horizontal layer at a time, stepped by wheel, arrows or the scrollbar.
5. **JEI title** is now the original's `jei.category.preview` = **结构预览** / "Structure Preview".

**Deliberately not included**, because the data does not exist yet: the parallelism and thread counts (those
fields belong to the unimplemented M6 milestone), the dynamic-pattern line, and the original's real scrolling
ingredient list — an `+N` overflow marker stands in for the scrollbar.

### The rendering fixes that came before

0.16.0 sits on top of three independent fixes, each of which produced the same "lit from inside" family of
symptoms. They are preserved exactly and must not be undone:

| Version | Cause | Symptom |
|---|---|---|
| 0.15.2 | 180° X rotation in the **model** transform | Rotated the vertex normals, lighting the tops as bottoms |
| 0.15.3 | `Lighting.setupFor3DItems()` instead of `setupLevel(...)` | Light directions rotated by a fixed GUI-item orientation; everything darker than the world |
| 0.15.4 | Negative Y scale in the **projection** | Image flipped, and the negative determinant reversed culling so blocks showed their interiors |

## Verification

- **Independently re-checked after the work landed**, not taken on report: jar and instance hashes match
  (`2076A05A…`); `mods.toml` = 0.16.0; **zero files under `data/`**; language files 115 keys each with **0
  orphans in either direction**; `jei.category.preview` present in both.
- **Renderer invariants re-verified by reading the file**: `viewport.m11(boxPixels / guiHeight)` positive, no
  negative Y scale anywhere, `Lighting.setupLevel(...)` retained, and no reversion to `setupFor3DItems()`.
- Build: `BUILD SUCCESSFUL in 18s`, jar 311024 bytes, deployed to the Rainbow Garden instance.

### Not verified

- **Nothing has been seen rendered.** All layout and sprite claims are static analysis of source, the shipped
  PNG and API signatures.
- **JEI input routing is the least certain part.** The API signatures were confirmed with `javap`, but whether
  JEI's simulate→execute press/release mapping and `handleMouseScrolled` reach the panel as the code assumes is
  untested. That is the most likely place for a surprise.

## A build-environment note worth keeping

Building with `& gradle ... *> $log` inside a background PowerShell **hangs**: PowerShell buffers the redirect
until process exit, and the single-use daemon then sits at 0% CPU. Redirect on the cmd side instead:

```powershell
cmd /c "set JAVA_HOME=... && set GRADLE_USER_HOME=... && gradle.bat -p ... clean build --offline --no-daemon --console=plain > _build.log 2>&1"
```

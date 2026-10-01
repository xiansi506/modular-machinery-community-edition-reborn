# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.15.1 — fix: the JEI structure preview was blank, and the component blocks were missing.**

## Bug 1 — the preview area was pitch black

`GuiGraphics.enableScissor` takes **absolute screen coordinates**; it does not consult the pose. JEI calls a
category's `draw` with the pose already translated to the recipe's origin, so passing the panel's own
coordinates put the clip rectangle somewhere else entirely — over the top-left corner of the window. The
structure was clipped away in full, and the only thing left in the recipe page was the panel texture's dark
background, which reads as a black void.

Confirmed by disassembling `GuiGraphics.enableScissor`: it builds a `ScreenRectangle` and calls
`applyScissor`, touching no pose state.

**Fix:** the JEI category no longer clips. Clipping bought nothing — the renderer scales a structure so its
rotated extent is about **0.91 of the box** on its longest axis, so it fits with margin by construction. Trading
a belt-and-braces clip for a preview that cannot silently vanish is the right way round when the clip itself is
what failed.

The blueprint **screen** was never affected: there the pose is untranslated, so its absolute coordinates were
already correct. It now goes through the same `StructurePreviewRenderer.clip` helper, which reads the pose's
translation back out and is therefore correct wherever it is called.

## Bug 2 — the machine's blocks were not shown

The original's preview panel listed the blocks a machine needs: `MachineStructurePreviewPanel` line 185 is
`ingredientList.setAbsXY(5, 179)` — an ingredient list along the bottom of the panel. 0.15.0 registered those
blocks with JEI as **hidden** ingredients (which is what the original's JEI category did, so that searching an
item finds the structures using it) but never drew them, leaving the bottom of the panel empty.

**Fix:** the components are now both registered for searching *and* drawn, in the row the original used. The
item row shows whole slots that fit; anything beyond shows as a `+N` marker rather than being silently
dropped.

## Layout

The panel geometry is now shared by the screen and the JEI category in `PreviewLayout`, and every number is the
original's:

| Element | Position | Source |
|---|---|---|
| Panel | 184 × 220 | `GuiScreenBlueprint.X_SIZE/Y_SIZE` |
| 3D viewport | 172 × 150 at (6, 20) | `MachineStructurePreviewPanel.WORLD_RENDERER_WIDTH/HEIGHT` |
| Ingredient row | at (5, 179), 172 wide | `ingredientList.setAbsXY(5, 179)` |
| Machine name | centred at y=205 | this mod |

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.15.1.jar`, deployed to the Rainbow Garden instance.
- The scissor diagnosis is grounded in the disassembled method, not inferred from the symptom.
- **Still not seen rendering.** The clip is gone from JEI, so if the preview is still blank the cause is in the
  renderer itself rather than in clipping — and the in-world blueprint screen (right-click a bound blueprint)
  is the faster way to tell the two apart, since it was never clipped by this bug.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

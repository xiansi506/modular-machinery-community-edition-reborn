# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.15.0 — milestone M5b: the 3D structure preview.**

## What 0.15.0 changes

### Machines can be looked at before they are built

Right-clicking a bound blueprint opens its machine's structure in 3D. The panel is the original's
`guiblueprint_new.png` blitted whole at **184×220**, with the structure drawn into the dark area the texture
already has. Dragging turns it; scrolling zooms.

JEI gains a matching category — **one entry per machine**, called "Machine Structure" — showing the same 3D
view. Each entry registers the machine's component blocks as hidden ingredients the way the original did, so
searching an item finds the structures that use it.

### The renderer is written, not ported

The original drove an 847-line `WorldSceneRenderer`: a fake world, its own camera with mouse look, a compile
cache thread, and the 1.12.2 `GlStateManager`/`Tessellator` stack. None of that transfers, and a structure
preview is a few dozen blocks drawn once per frame, so this is a fresh ~100-line renderer:

- It keeps the GUI's **orthographic** projection and rotates the pose, giving the isometric look structure
  previews are usually shown in. The original used a perspective camera; that is a viewing choice, not a
  correctness one.
- It turns the world into GUI space with a **180° rotation about X**, not by scaling Y negative. A negative
  scale inverts triangle winding and block render types enable back-face culling, so the visible faces would be
  exactly the wrong ones.
- It pushes the structure forward in Z so it sits in front of the panel, the way vanilla puts container slot
  items at z=100, and scissor-clips it to the texture's dark area.
- It brackets the draw with `Lighting.setupFor3DItems()` / `setupForFlatItems()`, which is what vanilla does
  around 3D item rendering in a GUI.

### What it draws

`StructurePreview` turns a pattern into concrete blocks, and it does two things a naive reading would miss:

- **It draws the controller at the origin.** The pattern never includes the controller's own position, but the
  controller is exactly the block a player has to place to know where the machine goes.
- **It picks a representative state per position** (`BlockMatcher.representativeState()`), applying the required
  blockstate properties. Without that a machine built from `blockcasing[casing=reinforced]` and
  `blockcasing[casing=firebox]` would be drawn as the default casing everywhere and the structure would be a
  uniform slab.

A machine with its own controller draws that one; everything else draws the generic `machine_controller`.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.15.0.jar`.
- **Preview model check, 0 problems**, over all six loaded machine definitions: no pattern ever contains the
  controller's position, and the render box grows to include the origin **exactly when** the origin falls
  outside the pattern's box. Four machines already contain the origin so nothing changes for them; the flat
  3×3 example plate is the case that needs it — without the rule its preview would be cut in half.
- **All 11 GUI textures are byte-identical to the originals** (SHA-256), including the two blueprint textures
  added here.
- Language keys: 99 per file, **0 missing and 0 orphans**.
- Jar still carries no content: 0 files under `data/`, 8 blockstates, 59 item models.

### ⚠️ The rendering itself has not been seen

**This is the first release where the deliverable is a picture, and a picture cannot be verified offline.** The
structure data and the box it is fitted into are tested; the drawn result is not. What to look at first:

- Does a structure appear at all, and is it right way up and readable as 3D?
- Do blocks occlude each other correctly, or does the far side show through the near side?
- Do the casing variants differ from each other, or is the preview a uniform slab (which would mean the
  representative states are not being applied)?
- Does it look flat and unshaded? Lighting is `FULL_BRIGHT`, so faces differ only by texture and by whatever
  directional shading the model renderer applies — the original had ambient and light toggles this does not.

Each of those points to one specific setting, all of them in `StructurePreviewRenderer`.

## Known limitations

- **The original's button row is not reproduced.** `guiblueprint_new.png` has a row of buttons along its top —
  layer toggle, reset centre, a 2D per-layer view and more — built over the original's 422-line widget
  framework. This release has drag-to-rotate and scroll-to-zoom instead.
- **The structure editing tools are still inert.** `itemconstructtool`, `machine_projector` and
  `ItemDebugStruct` are registered items with no behaviour.
- **Lighting is uniform**, as noted above.
- On a dedicated-server client nothing is shown in either preview: machine definitions are not synced to
  clients, so the registry is empty there. This is the same limitation already recorded for controller names
  and creative-tab blueprints.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

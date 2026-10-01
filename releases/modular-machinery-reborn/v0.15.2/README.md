# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.15.2 — the 3D structure preview is handed off; a text-overlap defect is fixed.**

## Status of the structure preview

The preview **works**: the user confirmed in game that JEI's Machine Structure page draws the 3D structure and
the component block row along the bottom. A separate, still-undescribed problem remains, and this part of the
mod has been **handed to someone else**.

**Read [`交接-3D结构预览.md`](../../../modular-machinery-reborn/docs/专项/交接-3D结构预览.md) before touching it.** That
document is the handover: code map, the original's layout numbers with sources, every verified 1.20.1 API fact,
**nine assumptions that were never verified**, and a ranked list of candidate problems with how to tell them
apart.

### One fact the user's testing settled

Because the structure appeared *inside the preview area* at local coordinates, JEI **does** translate the pose
to the recipe origin before calling `draw`. That was previously only inferred — it was the reason the previous
change dared not clip in JEI at all. The concern is now retired, which is what makes re-adding a pose-aware
clip viable (see §7-B of the handover).

## What changed

**Fixed: the hint text printed across the component icons.** 0.15.1 drew the operation hint at `NAME_Y - 12 =
193` while the component row occupies `179..197` — an overlap of four pixels, so the hint was written over the
item icons. The hint is now a **hover tooltip** on the preview area instead of panel text. The overlap is gone,
discoverability is kept, and no language key changed.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.15.2.jar`, deployed to the Rainbow Garden instance.
- The overlap was found by reading the layout constants against each other, not by report; it was certain
  before the fix and is certain after.
- **Rendering is still unverified by the person who wrote it.** Everything in the handover's §6 that is not
  reported as confirmed should be treated as an assumption.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

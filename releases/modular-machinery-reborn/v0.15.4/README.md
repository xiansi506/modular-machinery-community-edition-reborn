# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.15.4 — fix: the preview projection had a negative Y scale, which flipped the model upside down
*and* reversed back-face culling so blocks showed their own inside.**

## The bug: one sign, two symptoms

The custom perspective projection scaled the vertical NDC axis by a **negative** factor:

```java
viewport.m11(-boxPixels / guiHeight);   // wrong
```

The reasoning behind it — "the GUI's top-left convention needs a Y flip" — does not hold, because the GUI's NDC
**already** points up:

```
GameRenderer's GUI projection:  setOrtho(0, width, height, 0, 1000, 21000)
                                // bottom = height, top = 0
                                // so GUI y=0 maps to NDC +1, i.e. GUI NDC +Y is up the screen
```

The perspective matrix produces the ordinary +Y-up NDC as well. The two conventions agree, so the vertical axis
must be scaled by a **positive** factor. The giveaway is the asymmetry: `m00` was positive and `m11` negative,
which no consistent mapping would do.

Two things follow from that one sign:

1. **The image is mirrored vertically** — the model renders upside down.
2. **The projection's determinant is negative**, which mirrors triangle winding. Block render types enable
   back-face culling, so the culled faces become the front ones and the surviving ones are the far side's
   interiors — a block shows the inside of itself, which is what "the structure looks carved out" means.

## The fix

```java
viewport.m11(boxPixels / guiHeight);
```

`m31` (which places the box centre at the panel's centre in NDC) was already correct for the +Y-up convention
and is unchanged.

## Relationship to the previous two fixes

Three separate causes have now produced the same family of symptoms, all in this one renderer:

| Version | Cause | What it broke |
|---|---|---|
| 0.15.2 | 180° X rotation in the **model** transform to compensate for screen Y | Rotated the vertex **normals**, so top faces were lit as bottom faces |
| 0.15.3 | `Lighting.setupFor3DItems()` instead of `Lighting.setupLevel(...)` | Rotated the **light directions** by a fixed GUI-item orientation unrelated to the camera; everything darker than the world |
| **0.15.4** | Negative Y scale in the **projection** | Flipped the image **and** reversed culling, exposing block interiors |

They are independent: model orientation, light direction, and projection handedness are three different things,
and each had its own bug.

## Verification

- Build: `BUILD SUCCESSFUL in 24s`, jar `modular_machinery_reborn-0.15.4.jar`, deployed to the Rainbow Garden
  instance.
- The diagnosis follows from the GUI's ortho convention and the sign of the projection determinant, both
  checkable from the code and the vanilla projection.
- **Not seen rendering.** Restart and look: the model should sit right way up and blocks should show their
  outside faces. If it is now upside down the *other* way, the camera's up-vector handling is at fault rather
  than the projection.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

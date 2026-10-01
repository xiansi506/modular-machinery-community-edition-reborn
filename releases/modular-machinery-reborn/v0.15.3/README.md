# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.15.3 — fix: the structure preview was lit with the wrong API, which is why JEI looked darker than
the world.**

## The bug

The reported symptom: the same blocks render **noticeably darker in JEI than in the world**, with the bright and
dark faces arranged so the structure reads as if it caves inward.

**Cause: the preview used `Lighting.setupFor3DItems()`; terrain uses `Lighting.setupLevel(...)`.** They are not
interchangeable — and the reason is not the light vectors, which are identical, but the matrix that rotates them.

Disassembling the three layers settles it:

```
LevelRenderer (terrain):
  678: aload_1                              // the PoseStack renderLevel was given
  679: invokevirtual PoseStack.last()
  682: invokevirtual PoseStack$Pose.pose()   // -> Matrix4f
  685: invokestatic  Lighting.setupLevel(Matrix4f)

Lighting.<clinit>:            // both paths use the SAME vectors
  DIFFUSE_LIGHT_0 = new Vector3f( 0.2, 1.0, -0.7).normalize()
  DIFFUSE_LIGHT_1 = new Vector3f(-0.2, 1.0,  0.7).normalize()
  setupLevel(m)     -> RenderSystem.setupLevelDiffuseLighting(DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1, m)
  setupFor3DItems() -> RenderSystem.setupGui3DDiffuseLighting(DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1)

GlStateManager:
  setupLevelDiffuseLighting(l0, l1, matrix) -> transforms l0/l1 by `matrix`
  setupGui3DDiffuseLighting(l0, l1)         -> transforms them by a FIXED matrix:
      new Matrix4f().rotationYXZ(1.0821041f, 3.2375858f, 0f)
                   .rotateYXZ(-0.3926991f, 2.3561945f, 0f)     // ~62°, ~185.5° then ~-22.5°, ~135°
```

So `setupFor3DItems()` rotates the diffuse light directions by an orientation chosen for **item models shown in a
GUI** — it has nothing to do with the current camera or the world. Every face therefore gets a different diffuse
term than the same block in the world, the top faces come out darkest of all, and the brightness ordering
inverts — which is what "caves inward" means.

## The fix

`StructurePreviewRenderer` now lights the blocks the way terrain does — with the view rotation that also
transforms the vertices:

```java
Matrix4f view = cameraView(cameraDistance, yaw, pitch);
pose.mulPoseMatrix(view);
...
Lighting.setupLevel(new Matrix4f(view).setTranslation(0.0F, 0.0F, 0.0F));
```

**The translation must be removed.** `setupLevelDiffuseLighting` transforms each light vector as
`matrix.transform(new Vector4f(light, 1.0f))` — a **w=1 point** — so a matrix carrying translation would shift
the light direction instead of rotating it. The camera translation is tens of GUI units while the light vectors
have length 1, so leaving it in would badly skew the lighting.

## This was a separate cause from the camera fix

The previous change replaced a model-space 180° X rotation with a Y flip in the projection layer, which was
correct: that rotation was **also** rotating the vertex normals, sending the top faces through the bottom-face
lighting. But it did not touch the light directions, so the "darker" half survived it. Two independent bugs, both
producing the same "lit from inside" reading.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.15.3.jar`, deployed to the Rainbow Garden instance.
- The diagnosis rests on **disassembly of all three layers**, not on inference from the symptom.
- **The fix has not been seen rendering.** Restart and compare against an in-world screenshot: if the brightness
  now matches, this is done; if it is still dark, the next suspect is `LightTexture.FULL_BRIGHT` (all faces share
  one lightmap, so only directional diffuse separates them); if it is now too bright, the light matrix is being
  polluted by a translation or scale.

## Version-number collision

`0.15.2` was rebuilt once **under the same version number**, producing two different jars for one version
(`F56F18B3…` archived, `D118BA03…` in `build/libs`). Archived releases are never overwritten, so this fix takes
a new number instead of reusing `0.15.2`.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

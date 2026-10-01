# Modular Machinery: Community Edition Reborn

**Release 0.27.1 — fix: the KubeJS plugin declaration was in the wrong directory, so the machine recipe schema was never registered. Verified in game.**

## The defect — found in a real launch, not by the harness

The KubeJS bridge has existed since early milestones (`kubejs/ModularMachineryKubeJSPlugin.java`, 17 lines, registers the machine recipe schema; `kubejs-examples/server_scripts/machines.js` documents the script shape). A real launch showed it **never ran**:

```
[KubeJS/]: Found plugin source kubejs / terra_plate_extension / cucumber / skyresources / avaritia
                                                  ^ modular_machinery_reborn absent
[KubeJS Server/]: Loaded 2/2 KubeJS server scripts in 0.027 s with 0 errors and 0 warnings
[modular_machinery_reborn] KubeJS integration is optional; install KubeJS to register machine schemas.
```

The script **loaded with zero errors** — so the recipe call was a **silent no-op**, because KubeJS had never discovered the plugin defining `event.recipes.modular_machinery_reborn.machine`. **A failure that reports no error is the worst kind.**

**Root cause**: the declaration file sat at `META-INF/kubejs.plugins.txt`, but KubeJS 6 reads it from the **classpath root**. The proof was a working sibling: `terra_plate_extension-1.0.19.jar` ships `kubejs.plugins.txt` at its **jar root** and KubeJS lists it among its plugin sources.

## The fix

1. **Moved** `src/main/resources/META-INF/kubejs.plugins.txt` → `src/main/resources/kubejs.plugins.txt` (content unchanged). Asserted in the jar: at the root **1**, under `META-INF` **0**.
2. **Corrected a misleading log line**: `ModularMachineryReborn.java:74` printed *"install KubeJS to register machine schemas"* **unconditionally**, even with KubeJS installed.

## In-game confirmation (2026-10-01)

- The previously never-seen line now appears: **`[modular_machinery_reborn] KubeJS schema registered: event.recipes.modular_machinery_reborn.machine`**
- The demo recipe authored in KubeJS **displays and is recognised in game**.

Author-facing documentation was written as a result: [docs/KJS-配方指南.md](../../../modular-machinery-reborn/docs/KJS-配方指南.md).

## Why the offline harness could not catch this

It cannot run KubeJS. This is precisely the boundary the project's documentation names: **offline verification proves arithmetic, containment, coordinate mapping and reachability — it cannot prove that a third-party loader finds your file.** It took a real launch plus comparison against a working sibling mod.

## Verification

`clean build` → **BUILD SUCCESSFUL** with `> Task :jar` and `> Task :reobfJar`. **Reobfuscation verified** on the build product and the deployed file (official `CREATIVE_MODE_TAB` absent, SRG reference present). `mods.toml version="0.27.1"`; **zero** `data/` entries; language keys unchanged and identical; `StructurePreviewRenderer.java` untouched (mtime still 2026-09-30 01:16:55).

Jar SHA-256 `790D99984E2BB11048EB1D19FB1E0ED4F07D99C54BBFA8DE84B179A63175F034`, deployed with a matching hash.

## Still open with KubeJS

- **Machine definitions cannot be written in KubeJS yet** — machines come from a data pack or the config directory; KubeJS recipes only *reference* them. That is the next step (v2), and `docs/专项/KJS-勘测.md` §3b records what it needs first: the **ordering** between KubeJS script execution and our data-pack reload listener, a **merge** contract (`MachineRegistry.replace` is whole-table, so a naive push is wiped on the next reload), and **shared validation** so KubeJS-side errors are as good as data-pack-side ones.
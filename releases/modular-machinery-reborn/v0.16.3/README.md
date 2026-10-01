# Modular Machinery: Community Edition Reborn

**Release 0.16.3 — the preview input diagnostic no longer needs launcher configuration.**

## Why this release exists

0.16.1 added a click diagnostic gated behind `-Dmodular_machinery_reborn.preview.debug=true`. The user set it and
sent a log, but the log contains:

```
Completely ignored arguments: [-Dmodular_machinery_reborn.preview.debug=true]
```

The launcher placed the flag in the **game** arguments (it appears after `--fml.mcpVersion` in ModLauncher's argument
list) instead of the **JVM** arguments, so the system property was never set, the gate stayed closed, and the whole
run was **inconclusive** — not evidence about the buttons at all. Gating a diagnostic on launcher configuration was
the wrong design.

The diagnostic is now **unconditional**: one INFO line per simulate and execute pass, no configuration needed.
Remove it once the input path is confirmed working.

## What the log did establish

- `Modular Machinery: Community Edition Reborn 0.16.2 loaded` — the run used 0.16.2, so it included the rewritten
  machine recipe JEI page.
- **No errors or exceptions from this mod.** The repeated `Found a broken recipe, failed to setRecipe with
  RecipeLayoutBuilder` lines belong to other mods' JEI plugins, not this one.
- `Added recipe category decorator: ... for recipe type: modular_machinery_reborn:structure` — the structure
  category is registered with JEI and visible to its registration pipeline.

## How to read the next log

| Log content | Meaning |
|---|---|
| No `[preview]` lines at all | The handler is never dispatched — the problem is registration / recipe extras, not `handleInput` |
| `simulate` but no `execute` | The release event is not reaching us |
| Both passes present | Clicks arrive; the defect is in what the panel does with them |

## Verification

Build `BUILD SUCCESSFUL in 20s`; jar SHA-256
`15DEAA8456DB6A1197916C6A5183020D0102E7D3443318D7468387D913AAF789`, deployed with a matching hash. Only
`StructurePreviewCategory` changed; the renderer was not touched.
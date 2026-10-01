# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.14.0 — milestone M5a: blueprint binding.**

## What 0.14.0 changes

### Blueprints name a machine again

The blueprint item now carries a machine's registry name in NBT, under the **same key the original used**
(`dynamicmachine`), so a blueprint written by one mod is readable by the other. The tooltip names the machine, or
reads "Empty" for an unbound one.

The creative tab now lists **one blueprint per registered machine**, which is what the original's
`ItemBlueprint.getSubItems` did — not a single generic item. When no definitions are known (a dedicated-server
client never receives them) it falls back to one empty blueprint so the item stays reachable.

`/mm-get_blueprint <machine>` hands the player a blueprint, with the original's command name and permission
level 2. It exists because a machine that sets `requires-blueprint` can *only* be built through its blueprint,
so there has to be a way to obtain one outside creative mode:

```
/mm-get_blueprint alloy_furnace
/mm-get_blueprint some_other_mod:its_machine
```

### Structure matching now follows the original's search order

`MachineRegistry.findMatch` reproduces `TileMultiblockMachineController.checkStructure` step for step:

1. **The blueprint's machine is tried first.** A blueprint is an explicit statement of which machine this
   controller is for, so it outranks everything else.
2. **A machine-bound controller then checks its own machine**, never anything else. If that machine requires a
   blueprint and the slotted one is not it, nothing forms — the original returned early here rather than
   falling through.
3. **Otherwise every definition is tried, except those requiring a blueprint.** The original's
   `checkAllPatterns` skips them, and that skip is the entire meaning of `requires-blueprint`.

Because of step 3, removing a blueprint from a controller whose formed machine requires one dissolves the
machine with no extra code — the machine simply stops being findable.

**Two consequences worth knowing:**

- **A blueprint outranks a bound controller's own machine.** Slot an alloy furnace blueprint into a
  `transformer_controller` and, if that structure matches, it becomes an alloy furnace. That is what the
  original did — it checked `getBlueprintMachine()` before `parentMachine` — and it is reproduced rather than
  quietly corrected. It takes a deliberate player action to trigger.
- **`requires-blueprint` stays opt-in.** None of the original's built-in machines set it, so none of this mod's
  do either. A pack author turns it on in their own definition.

### A bug in the 0.12.0 controller screen

The blueprint line printed the **formed** machine's name rather than the blueprint's. It now shows the
blueprint's machine, and falls back to the registry name when the definition is not available on this side —
still informative, and no longer claims the slot is empty when it is not.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.14.0.jar`.
- **Search-order differential test: 12 scenarios, 0 differences.** The original's order (read from
  `checkStructure` and `checkAllPatterns`) and this implementation were each rewritten as an independent
  algorithm and compared over every interesting combination of matched machines, blueprint, bound controller
  and `requires-blueprint` — including the two that matter most: a `requires-blueprint` machine matching
  *without* a blueprint must not form, and a bound controller must ignore a matching neighbour.
- Language keys: 97 per file, **0 missing and 0 orphans** (was 93; four added for the blueprint tooltip and the
  command's feedback).
- Jar still carries no content: 0 files under `data/`, 8 blockstates, 59 item models, 9 GUI textures.

### Not verifiable offline

The blueprint item, the command and the search order have not been exercised in game. The scenario table proves
the decision logic; it cannot prove the item's NBT round-trips or that the command registers.

## What is still missing from M5

This release is the half of M5 that does not need a renderer. Still to come:

- **The structure preview and blueprint preview screen** — `GuiScreenBlueprint`, and the 3D scene renderer the
  original drove it with (`WorldSceneRenderer`). Right-clicking a bound blueprint opens that screen in the
  original; here it does nothing yet.
- **The JEI structure preview category** — the 3D machine model in JEI.
- **Structure editing** — `itemconstructtool`, `machine_projector` and `ItemDebugStruct` are registered items
  with no behaviour.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

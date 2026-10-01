# Modular Machinery: Community Edition Reborn

**Release 0.20.0 — M6d: the parallel controller in five tiers, and the second structure-collection path.**

## D13 — how a parallel controller is attributed to a machine

This answers the gap the M6 scoping document named in §4/§5: **this project had no mechanism for attributing a non-hatch block to a machine** — the only thing the structure scan collects is `MachineHatchBlockEntity`. The scoping document also forbade the obvious shortcut: **do not disguise such a block as a hatch.**

The decision has three parts:

1. **A second collection path, parallel to the first and not merged into it.** `MachineControllerBlockEntity` walks the **same** `machine.pattern().positions()` in the same structure check, trying a hatch first and `machine.ParallelController` second. The result is two unrelated collections: `HatchCollection` (which feeds the recipe engine) and `ParallelControllerCollection` (which contributes one integer). No search radius, no block-update handshake, no fake hatch — disguising it would make the parallel controller a port the recipe engine could pull resources from.
2. **The arithmetic lives in a pure function**: `machine/ParallelismLimit.resolve(definition, controllerParallelism)` = `max(1, min(max-parallelism, internal-parallelism + Σ controllers))`. None of D12's three fields changed; with all three omitted the result is still 1, bit-for-bit the pre-0.19.0 behaviour. The reason is the plan's §5 ban on accepting work with "a number appeared in the GUI" — a pure function can be driven by the offline harness.
3. **The controller must be accepted by the machine definition.** Of the original's built-in machines only `assembly_line` lists `modularmachinery:blockparallelcontroller` in its structure, so the example data pack makes `alloy_furnace`'s roof-centre cell accept either a casing or a controller, and raises its `max-parallelism` from 64 to **2048** (the original's `Config.maxMachineParallelism` default) — otherwise only the first three of the five tiers would be distinguishable.

**This is not a substitute for M6b** — the upgrade bus is still outstanding.

## The five tiers

| Tier | Value | Original source |
|---|---|---|
| normal | 4 | `ParallelControllerData.java:9` |
| reinforced | 16 | `:10` |
| elite | 64 | `:11` |
| super | 256 | `:12` |
| ultimate | 512 | `:13` |

Tier values moved into **the project's first config file**, `config/modular_machinery_reborn-common.toml` (the original's `ParallelControllerData.loadFromConfig:23-29`); the tier enum itself stays a compile-time constant, the same reason as D8. The block tooltip and GUI strings reuse the original's wording verbatim.

## GUI and assets

The GUI is the original's `guismartinterface.png` (176×166) — identified from code rather than guessed (`GuiContainerParallelController:70` blits `TEXTURES_EMPTY_GUI`, defined at `GuiContainerBase:26`). The layout is reproduced line by line: title at (4,4), max at (6,16), current at (6,49), a 95×10 field, six ±1/±10/±100 buttons, enter-to-submit clamped to `[0,max]` with a parse failure **not** clearing the field.

One honest divergence: the original fed keystrokes to the field even when unfocused (a quirk at `:174-176`); this implementation only feeds it while focused.

**Eleven texture/animation files are byte-identical to the originals** (the GUI sheet, five `overlay_parallel_controller_*.png`, and each `.mcmeta`). The `.mcmeta` files matter: these are vertical animation strips (32×704 / 32×992), and without them only the first frame renders.

## Networking

Reads go through the menu's `ContainerData` (indices 0/1, the same pattern as 0.19.0's controller-menu indices 6–9 and the hatch screens). Writes needed **the project's first `SimpleChannel`** (`network/ModNetwork` + `ParallelControllerUpdatePacket`, varint): the menu's only client→server channel, `ServerboundContainerButtonClickPacket`, encodes its ids as **one byte each** (verified with `javap`), while the ceiling runs to 512 and beyond — the original's "send the absolute value" does not fit.

## Acceptance — 105 offline checks, 0 failures

M6c's 39 checks are unchanged and all still pass. The new sections prove the point of this slice:

```
PASS  one elite controller raises the limit from 1 to 64          <- the core assertion
PASS  NORMAL/REINFORCED/ELITE/SUPER/ULTIMATE ceiling: 4/16/64/256/512
PASS  alloy furnace (internal-parallelism 1) + elite: 65
PASS  a machine whose max-parallelism is 64 clamps an ultimate controller: 64
PASS  parallelizable:false ignores even an ultimate controller: 1
PASS  normal/…/ultimate: cobblestone consumed in one craft 5/17/65/257/513
PASS  normal/…/ultimate: stone produced in one craft     5/17/65/257/513
PASS  one cobblestone short of 257: 256
PASS  the update packet round-trips 0…Integer.MAX_VALUE (8 cases)
```

End-to-end uses the data pack's demo recipe (1 cobblestone → 1 stone, 5 ticks, 100 FE/t), driven in exactly the order `MachineControllerBlockEntity#tick` uses, and asserts **the number of items that actually moved** rather than what the GUI says. Evidence lives in `_audit/m6c-parallel-verify/`.

## ⚠️ Process trap — this is what caused the 0.19.0 crash

`reobfJar` **rewrites `build/libs/<name>.jar` in place**, and the offline harness's verify task `dependsOn jar`.

**Running the build first and the harness second re-executes `jar` and restores the un-reobfuscated jar** — precisely the state that crashed 0.19.0. The order must be: **harness → `clean build` → reobfuscation check → deploy.** Written into the harness README and into the version index's mandatory checks.

## Not delivered: the smart interface

The scoping document's §2 lists it, but its own §4 change-list and language-key list contain no smart-interface entries. Reading the original settles it: its "types" are registered **only** by CraftTweaker (`MachineModifier.addSmartInterfaceType:27-38`) with no JSON entry point, and its only consumer (`RequirementInterfaceNumInput`) is on M2's unported list — so a block and GUI now could only ever print "no bound machine", which the plan's §5 forbids. **Recommended as a separate slice M6d-b**, together with a machine-JSON `smart-interface-types` field and the `interface_number_input` requirement type. M6e (factory) is unaffected.

## Verification

`BUILD SUCCESSFUL in 18s` including `> Task :reobfJar`. **Reobfuscation verified in three places — build output, this archive, and the deployed file** (official name absent, SRG name present). `mods.toml` 0.20.0; **zero** files under `data/`; language files **142 keys each, identical sets**; 13 GUI textures; all 11 new assets byte-identical to the originals; renderer invariants intact and the preview's input design untouched.

Jar SHA-256 `5D805EBD833E7C88606F29E9DEA608A1A0EAD8B6F2F9F6FC342C53A81784C5C6`, deployed with a matching hash.

**Nothing was seen in game.** Unverified: the GUI rendering, the cutout render layer, the world-side collection (that placing a controller gets it collected and removing it drops the limit within one structure check), the `ContainerData` indices over the wire, the custom packet's real transmission, and the config file's generation and reading.
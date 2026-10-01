> # ⚠️ 这个版本的 jar 不可用
>
> **v0.19.0 的 jar 没有经过 SRG 重混淆**，会在模组构造期崩溃：
>
> ```
> java.lang.NoSuchFieldError: CREATIVE_MODE_TAB
>     at com.reborn.modularmachinery.block.ModBlocks.<clinit>(ModBlocks.java:42)
> ```
>
> 原因是运行时使用 SRG 命名（崩溃报告写明 `ModLauncher naming: srg`），而该 jar 仍引用**官方映射名**。
> 只有原版成员的引用需要 SRG 名，所以表现为**单个** `NoSuchFieldError` 而不是连锁失败。
>
> **源码本身没有问题**——下面关于 M6c 的描述依然准确。缺陷只在打包。
> **请使用 `v0.19.1`**（源码相同、构建正确）。
>
> 归档永不覆盖，故此目录保留为坏件存档，并在版本索引中标注。
# Modular Machinery: Community Edition Reborn

**Release 0.19.0 — M6c: parallel crafting (the numeric kind), recipe modifiers, and the three GUI items that were blocked on it.**

## What M6c actually is — and is not

The previous slice established by reading the original that its "parallelism" is **a numerical reduction, not a thread pool**: `RecipeCraftingContext#getMaxParallelism` starts from the recipe's own limit and takes the minimum across parallelisable requirements. This release implements that, and the word "thread" appears nowhere in its code.

## D12: three new machine-definition root fields

The original has no `max-parallelism` / `internal-parallelism` / `parallelizable` in its machine JSON — those live in `AbstractMachine`'s Java fields, reachable only from CraftTweaker (`MachineModifier.java:43-84`), with compile-time defaults (`Config.java:38-47`). This project's route is data-driven definitions (D3/D9/D10), so without the fields a data-pack machine could **never** be parallel, and this slice's acceptance — "set the limit above 1 and count N outputs" — would have nowhere to set it. Recorded as **D12**, the third deliberate schema divergence.

| field | type | default | original counterpart |
|---|---|---|---|
| `max-parallelism` | int ≥ 0 | 2048 | `Config.maxMachineParallelism` |
| `internal-parallelism` | int ≥ 0 | 0 | `AbstractMachine.internalParallelism` |
| `parallelizable` | bool | true | `Config.machineParallelizeEnabledByDefault` |

**Defaults preserve today's behaviour**, proved rather than asserted: the original's `getMaxParallelism` is `max(1, internal + controllers)` clamped to the ceiling, so with all three omitted it is `max(1, 0+0) = 1` — one copy, exactly as before. Acceptance check J asserts it end to end.

Validation is loud and says what to write: non-integer or negative values, `internal-parallelism > max-parallelism` (an **error**, where the original silently raised the ceiling), `parallelizable:false` with `internal-parallelism > 1` (a warning — inert, not contradictory), a non-boolean `parallelizable`, and any unknown root key (a typo must not pass as accepted).

## Parallel crafting

`MachineRecipe#parallelism(ports, modifiers, ceiling)` mirrors the original's shape with three documented differences: the original's component **copies** are gone because this project's `IngredientIo` never mutates during a check; a per-recipe `max-parallelism` exists (the original only had a CraftTweaker setter), defaulting to "no opinion"; and **item output limits are an upper bound** from `getSlotLimit`, since `IItemHandler` cannot report per-stack free space — the exact all-or-nothing check in `insertAll` still decides whether the settlement happens, so the bound errs safely.

Amounts are multiplied by the parallelism (`maxConsume = toConsume * maxMultiplier` in the original), inputs are extracted as one batch and outputs inserted all-or-nothing. **Chance is not multiplied** — the original rolled once per settlement and moved N copies through that one decision, so a 25% output at parallelism 4 yields 4 items a quarter of the time.

## Recipe modifiers

`RecipeModifier`, `RecipeModifiers` (the original's final arithmetic verbatim: `(value + Σadd) × Πmul`, order-independent) and a `modifiers` array in the machine definition, with `modifiers` removed from the unimplemented-fields list. The controller recomputes the active set on every structure check and rotates offset and descriptors the original's way, so definitions are written once for a north-facing controller.

Trimmed, with reasons: `MultiBlockModifierReplacement` / `DynamicModifierReplacement` (they act on dynamic patterns, which remain unimplemented); `ModifierRegistry` (an empty class in the original); and JEI reflection of modifiers — the JEI files are red-lined this round, so **JEI shows unmodified amounts** even though `MachineRecipe` exposes the modified values.

## The three reconnected GUI items

1. **Controller parallelism rows** — shown only during a craft and only when the frozen parallelism exceeds 1, which is the original's own condition, at its exact line advances. Original wording: `并行数：%s` / `最大并行数：%s`.
2. **Controller performance footer** — `Avg: %sμs/t (Search: %sms), WorkMode: %s`, backed by a new `ControllerTiming`. Two honest divergences, both commented: the original's timing fields are **`static`** on the tile (one pair shared by every controller in the world, written by whichever ticked last — plainly a bug, not reproduced, so the figure describes *this* machine), and `WorkMode` is always `SYNC` because this engine is synchronous by construction — naming a mode it is not in would be worse than saying so.
3. **Preview machine-info rows** — `内置并行数` / `最大并行数`, in the original's order and under its own guards. **`max_threads` / `core_threads` stay omitted**: they belong to the factory slice and the data does not exist yet. The preview's input design (`getArea()`, `PointerClaim`, hit-testing) is untouched.

Language files 127 → **132** in both, sets identical.

## Acceptance — evidence, not assertion

The plan forbids accepting this on "a number appeared in the GUI", so the slice was verified offline by a harness that drives `MachineRecipe` in the exact order `MachineControllerBlockEntity#tick` does, against in-memory ports: **38 PASS / 0 FAIL**. The harness and its log are preserved in `_audit/m6c-parallel-verify/` (it is **not** part of the mod and is not compiled into the jar).

| check | result |
|---|---|
| ceiling 8, no modifiers | limit 8; **64 coal consumed, 8 diamonds produced**, 8000 FE |
| the alloy furnace's own vent modifier (output ×2) | 8 copies → **16 diamonds** |
| input cost ×2 | limit 4 at 64 coal (vs limit 8 unmodified) |
| output hatch holding 4 | limit 4 |
| recipe-level `max-parallelism: 2` | limit 2 |
| 250 FE at 100 FE/copy/tick | limit 2 |
| duration ×0.5 | 5 ticks, multiplier 2.0, same 2000 FE total |
| 3 of 8 coal | limit 0 |
| 50% output at parallelism 4, 400 crafts | 1600/1600 guaranteed; **800 emeralds vs 800 expected** |
| defaults | 2048 / 0 / true, and a ceiling of 1 gives limit 1 — pre-M6c behaviour intact |

**Regression risk did not materialise:** every new lifecycle capability is an overload, so the previous signatures delegate with an empty modifier set and every amount expression reduces to the pre-M6c formula when nothing is modified.

## Verification

Independently re-checked after the build: `mods.toml` 0.19.0; **zero** files under `data/`; language files **132 keys each, identical sets**; renderer invariants intact; the preview's whole-panel `getArea()` intact. Build `BUILD SUCCESSFUL in 18s`. Jar SHA-256 `B8DDF9A2061F565CF596162668FAFDB4A955C16487D683700C48827A7550E1DA`, deployed with a matching hash.

**Not byte-reproducible:** the `jar` task embeds archive timestamps, so consecutive identical builds produce different hashes. The hash above is of the exact deployed bytes.

**Nothing was seen in game.** The GUI geometry rests on arithmetic; `ControllerTiming` has never been produced by a real tick; the new `ContainerData` indices have never been round-tripped over the wire; and whether the vent above the alloy furnace controller really activates its modifier in a world is untested.
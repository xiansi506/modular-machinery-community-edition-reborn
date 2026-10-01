# Modular Machinery: Community Edition Reborn

**Release 0.26.0 — Wave 1 item 2: `ingredient_array_input` ported. The other two "missing" types turned out not to be gaps.**

## The premise was wrong, and the reconnaissance caught it

The task assumed three requirement types needed porting. The original says otherwise:

| Type | Registered? | JSON reader | Constructible? |
|---|---|---|---|
| `ingredient_array_input` | `RegistryRequirementTypes.java:62` | **real** — `RequirementTypeIngredientArray.java:45-143` | yes |
| `item_durability` | `:61` | **`return null`** (`:12-14`) | **never** — zero `new RequirementItemDurability` in the whole original |
| `catalyst` | **absent entirely** from `:59-75` | **`return null`** | CraftTweaker only (`RecipePrimer.java:831-873`) |

**Durability consumption *is* fully implemented in the original — but as a CraftTweaker-only field on `item`**: `RequirementItem.consumeDurability:81`, setter `:123-125`, `supportsDurability:127-133`, the `IOType.INPUT` guard at `:389`, and `ItemUtils.damageAllInternal:330-363` (damage per use per matching slot; on break `shrink(1)` and `setItemDamage(0)` — a fresh item of the same stack replaces the broken one; counts as consumed, so it bounds parallelism; start phase only). The only writer is `RecipePrimer.java:199-220`; `RequirementTypeItem.createRequirement:34-121` **never reads the field**, and there is **no JSON field name anywhere**.

That distinction is recorded as **D18** precisely so the wrong lesson is not re-learned: if a future wave ever wants durability from JSON, **the field belongs on `item`**, not on a resurrected `item_durability` type. This port did not invent field names for either dead type.

## What was implemented

New `recipe/IngredientArrayEntry.java`, `IngredientArrayRequirement.java`, `IngredientArrayParser.java`; `ModRecipeSerializers` gained a fifth kind and its wire form; `MachineRecipeCategory` and `MachineRecipeText` needed a fix (**functionally required** — the old `(ItemRequirement) requirements.get(i)` cast would `ClassCastException` the JEI recipe page on any array recipe).

Semantics matched to the original: **entries are alternatives — exactly one of the group is consumed** (the class's own comment, `RequirementIngredientArray.java:44-47`), walked in array order with later entries topping up `maxMultiplier - ingredientConsumed` (`:236`). It is a **one-shot start cost, not per-tick**, and it **bounds parallelism**: `servedCopies = Σ min(ceiling − served, ⌊available_i / amount_i⌋)` with `parallelLimit = min(ceiling, servedCopies)`. Proven on seven cases plus real settles — e.g. `5 diamonds + 1 emerald @ ceiling 8` → 3 copies, consuming exactly 4 diamonds + 1 emerald.

Language keys 166 → **167** each, one key, the original's wording verbatim.

## D19 — a divergence that needs your eye

The original read `amount` **off the requirement** (`:79-84`) while **its own documented example wrote `amount` inside every entry** (`:26-42`), so that example silently consumed 1 per entry. This port lets an **entry's own `amount` win**, with the requirement-level value as fallback (default 1) — refusing the entry-level form would reject the original's documented example. Conversely **`chance` stays requirement-level only** and is rolled once per settlement; writing it inside an entry is a **loud error**, because per-entry chance was never implemented — the original's JEI labelled entries "weights" (`tooltip.machinery.ingredient_array_output.weight`), which is unfulfilled intent this port refused to invent.

The original silently **clamped** an out-of-range `amount` and silently **ignored** an out-of-range `chance`; both are loud errors here.

## Red first, then green — and the red run found real bugs

Assertions written first (driven by reflection so the pre-implementation run still compiles): **`703 PASS / 4 FAIL`**, the single new failure being the missing parser class. Red caught **two real defects** (an `Ingredient` template carrying a phantom stack-count that accepted "1 diamond" as enough; an unknown-key check firing on the legitimate entry-level `amount`) and **one wrong assertion of the author's own** — it had encoded "every entry is mandatory" instead of the original's alternative semantics.

**Green**: `_audit/m6c-verify/harness-run-0.26.0.txt` (written by the harness itself) — 889 lines, **`SUMMARY: 788 PASS / 3 FAIL (791 checks)`**, X section **86 checks / 0 FAIL**. The 3 are the known environmental config-migration ones.

**Fault injection**: consumption removed → **`776 PASS / 15 FAIL`** (12 quantity assertions red, e.g. `diamonds consumed by 3 copies: expected 6, got 0`); copy count broken → arithmetic assertions red. Both reverted byte-identically.

## In-game checklist

1. Write a recipe using `{"type":"modularmachinery:ingredient_array_input","io-type":"input","items":[{"item":"minecraft:diamond","amount":2},{"item":"minecraft:emerald"}],"chance":0.5}` and `/reload`.
2. Start it with **exactly one of the group** available → it must start and consume **only that one** (later entries are alternatives, not a shopping list).
3. Give several of each and raise parallelism → the copy count must follow `Σ min(ceiling − served, ⌊available_i / amount_i⌋)` — check the consumed totals match.
4. Put `amount` **inside an entry** (the original's own documented form) → that entry's amount must win.
5. Put `chance` **inside an entry** → a loud load error naming the correct placement.
6. Out-of-range `amount` (`0`, `65`) and `chance` (`1.5`) → loud errors (the original silently clamped/ignored).
7. Fewer items than the parallelism needs → refuse to start with `craftcheck.failure.item.input`.
8. **Open the JEI recipe page for an array recipe — it must not crash** (this release had to fix a `ClassCastException` there).
9. Unknown requirement types still fail loudly, and the message now names the three "dead in the original" kinds.

## Verification

`clean build` **BUILD SUCCESSFUL** with `> Task :jar` and `> Task :reobfJar`. **Reobfuscation verified on the build product, this archive and the deployed file** (official `CREATIVE_MODE_TAB` 0, SRG `f_\d+_` 1). `mods.toml version="0.26.0"`; **zero** files under `data/`; language keys **167 each, identical sets**; one real `.registerConfig(`; `StructurePreviewRenderer.java` untouched (mtime still 2026-09-30 01:16:55).

Jar SHA-256 `F018AF91FE6D347B64F1555C176F4110DE886B41D4A19F3209EE9B15CE13D6E2`, deployed with a matching hash. **Not opened in game.**

## Two process notes

- **The harness's `init.gradle` compiled only `ParallelCraftCheck.java`**, so a second source file could not resolve. It now compiles every `*.java` under `mmverify/`, in both copies — the committed `_audit` copy had silently drifted since 0.24.x. Another instance of "the verification apparatus itself rots" (mandatory check 4).
- **Pre-existing text corruption** was found in `modular-machinery-reborn/README.md` (`uleanroom`, `u:\Program Files`, `Tuonstruct`) — the project's known C→u CP936 damage class. It was flagged by the agent rather than silently edited, and has since been repaired with the file re-verified as strict UTF-8, no BOM.

## Still outstanding after this release

- **`item_durability`'s mechanism** is unported and now documented as such (CraftTweaker-only; the field belongs on `item`).
- **JEI fidelity**: an array recipe's cell shows every alternative plus the tooltip line, but its entries are not listed as separate numbered lines the way the original's `JEIComponentIngredientArray` did. Visible in game — checklist step 8.
- **`gas` / `gas_pertick` / `fluid_pertick`** remain genuine gaps (blocked on a gas API), as does the recipe-adapter item.
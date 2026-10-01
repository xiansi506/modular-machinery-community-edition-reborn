# Modular Machinery: Community Edition Reborn

**Release 0.16.2 — the machine recipe JEI page rebuilt from the original; input quantities now show.**

## The reported defect, and its root cause

The user reported the machine recipe page was crude and that **the recipe crafting 不朽花冠 did not show input
quantities at all**. Two lines were responsible:

```java
// inputs - a vanilla Ingredient carries no count, so JEI drew every input as a single item
builder.addSlot(RecipeIngredientRole.INPUT, ...).addIngredients(item.ingredient());

// outputs - the amount was deliberately discarded
builder.addSlot(RecipeIngredientRole.OUTPUT, ...).addItemStack(item.outputStack().copyWithCount(1));
```

Inputs are now expanded from `ingredient.getItems()` with `copyWithCount(item.amount())`, matching the original's
`RequirementItem#asJEIIORequirementList`, so JEI draws the stack count (a "8" overlay on 泰拉钢锭). Outputs are
added as-is, since `outputStack()` already carries `maxAmount`.

## Reproduced from the original

Read from the original's `common/integration/` and `common/crafting/`, then reimplemented:

| Aspect | Original behaviour |
|---|---|
| Band | inputs left in descending sort order (energy 1000 → fluid 100 → item 10), process arrow, outputs ascending |
| Cells | 18×18 with no gap; item cells-per-row from `getMaxHorizontalCount(partAmount)`; fluid tanks stack; energy 18×54 |
| Amounts | stack count set from min/maxAmount, then painted over the cell — nothing for 1, `min~max` at half scale, large values abbreviated |
| Energy | filled 18×54 cell plus "能量消耗：<rate> FE/t" and "总计：<rate×duration> FE" |
| Processing time | an arrow-hover tooltip only, with the arrow animated over the duration |
| Chance / ranges | slot tooltips: "有 %s 概率产出", "产出数量：%s ~ %s" |

Hand-checked against `immortal_bloom_crown.json`: energy (4,26), water (22,26), eight items in a 3×3 grid at
x=40/58/76, arrow at x=97 y=45, outputs at (122,26) and (122,44); text "能量消耗：8.192K FE/t", "总计：9.83M FE",
"流体消耗：1000 mB 水"; the petal slot hovers "有 25% 概率产出".

## Deliberate divergences (commented in code, not silent)

1. **One shared category rather than one per machine.** JEI 1.20.1 fixes category identity at plugin registration,
   before `MachineRegistry` is filled by `MachineLoader`; the machine name is drawn as the page header instead.
   Per-machine catalysts are impossible for the same reason (`IRecipeCatalystRegistration` binds by `RecipeType`).
2. **Fixed 176×128 page**, so the band is 5×3 inputs / 3×3 outputs; overflow reports "（另有 %s 个需求未显示）".
3. Duration in the header as well as on arrow hover.
4. The fluid amount is painted over the tank (JEI's fluid renderer paints none).
5. JEI's own slot sprites and animated arrow instead of the original's `jeirecipeicons_ce.png`.
6. Dark-grey text, because JEI 1.20.1's preview background is a light panel where the original had a dark area.
7. No fuel / smart-interface / recipe-tooltip rows — the M2 data model has no such fields.
8. **Chance stays tooltip-only**, exactly as in the original, so a 25% side output is only visible on hover.

## Verification

Build `BUILD SUCCESSFUL in 18s`. Independently re-checked after the build: `mods.toml` 0.16.2; **zero** files under
`data/`; language files **133 keys each with identical key sets and no orphans** (115 + 18 new); renderer invariants
intact (`viewport.m11` positive, no negative Y scale, `Lighting.setupLevel` retained).
Jar SHA-256 `5149D50427115CE1966BFC8DA7063C78AEFCA6FA171CB31638140CF70405A8C6`, deployed with a matching hash.

**Not seen in game.** The page's layout and text are derived from the original's source and hand-checked against a
real recipe, but nothing here has been rendered.
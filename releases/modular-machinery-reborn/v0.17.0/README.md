# Modular Machinery: Community Edition Reborn

**Release 0.17.0 — the machine recipe JEI page is now one dynamic-sized category per machine, drawn with the original's own sprite sheet.**

## The suspect claim was wrong

0.16.2 shipped one shared category instead of the original's one-per-machine, justified by the claim that "JEI 1.20.1 fixes category identity at plugin registration, before `MachineRegistry` is filled by `MachineLoader`". **It is false**, proven from JEI 15.49's own bytecode:

- `mezz.jei.forge.startup.StartEventObserver` starts LISTENING. `startIfReady()` returns unless `observedLogin` (set by `ClientPlayerNetworkEvent.LoggingIn`) **and** `observedRecipeSync` (set by `RecipesUpdatedEvent`) are both true; only then does `transitionState(JEI_STARTED)` reach `JeiStarter.start()` → `PluginLoader.createRecipeCategories` → `registerCategories`.
- Both events are far later than the resource reload that fills `MachineRegistry` through `MachineLoader`. Game log confirms it: machines loaded at **12:40:50.392**, `Starting JEI...` at **12:40:53.978**, category registration at **12:40:54.993**.

Per-machine categories are therefore not only possible but natural, and they in turn make per-machine **catalysts** possible, because `IRecipeCatalystRegistration` binds by `RecipeType` and each machine now has its own.

## The four divergences, closed

| # | Original behaviour | Now |
|---|---|---|
| 1 | One category per machine, title = the machine's localized name (`ModIntegrationJEI#registerCategories:187-191`, `CategoryDynamicRecipe:60-68`) | One category per machine, `RecipeType` = the machine's registry name, title = `displayName()`, catalysts = that machine's controller + an NBT-bound blueprint |
| 2 | Page sized to the recipe (`CategoryDynamicRecipe#buildRecipeComponents:79-240` measures the layout and returns a blank drawable of that size) | A line-for-line transcription, including both quirks: the energy part reserves **63** rows while its art is 54 tall, and gaps are 0 |
| 3 | Its own sprite sheet `jeirecipeicons_ce.png` | Copied byte-identically (**SHA-256 `652EBFCD42920BE26A2126F5AE2FA43A3D037D91EAC53132C78B03D0789B7E6B`**), UVs taken verbatim from `RecipeLayoutHelper#init:41-48`, none invented |
| 4 | Processing time **only** as an arrow-hover tooltip (`DynamicRecipeWrapper#getTooltipStrings:74-86`) | Header removed entirely; the two duration strings appear only over the 22×15 arrow |

Also: the original had **no background art** (`getBackground()` returned a blank drawable), so the pane was JEI's own nine-slice border — the same one JEI 15.49 draws by default, verified by comparing `single_recipe_background.png` centre pixels across both JEI versions. Nothing is overridden, so the backdrop matches. Text is now white with drop shadow, as the original drew it, on the same RGB(198,198,198) panel.

The added `流体消耗：1000 mB 水` rows and the amount painted over the tank were **removed**, because the original had no fluid tip and its fluid renderer painted no amount. JEI's fluid tooltip still reads "1000 mB" — if the on-screen number is missed, it is a one-line revert.

**A testable prediction** (arithmetic only, nothing rendered): for `immortal_bloom_crown.json` the content should now be **142 × 83** (visible pane 150 × 91 once JEI draws its border at −4,−4) — energy cell 18×54 at (4,0); water tank 18×18 at (22,0); eight item inputs as a 3-wide grid with cell sprites at x 40/58/76 and y 0/18/36; arrow at (98,15) 22×15; the two outputs at (124,0) and (124,18), stacked because `getMaxHorizontalCount(2) == 1`; tip lines at x=4 on y=63 and y=72 reading "能量消耗：8.192K FE/t" and "总计：9.83M FE". No machine-name header. Hovering the arrow alone shows "加工时间：60.0 秒（1200 Tick）".

## Still open, data-limited

No fuel / smart-interface / recipe-tooltip rows — the recipe model has no such fields; the energy rows are the only tips the original's machinery can produce here. Chance stays tooltip-only, which matches the original. The slot quantity is JEI's stack-count overlay rather than the original's hand-painted `1K`-abbreviating renderer; below 1000 the two are pixel-identical.

## Verification

Independently re-checked after the build: `mods.toml` 0.17.0; **zero** files under `data/`; language files **127 keys each, identical sets, no orphans** (133 minus 6 now-unreferenced keys); **12** GUI textures; the new sprite sheet byte-identical to the original. Renderer invariants intact — `viewport.m11` positive, no negative Y scale, `Lighting.setupLevel` retained. Jar SHA-256 `5F5B9A073425B7F1145BBB4B8CE3B9CE7D189E494281E621FC30C8DDBC6C85E7`, deployed with a matching hash.

**Not seen in game.** The page geometry above is arithmetic derived from the original's algorithm; the user tests it.
# Modular Machinery: Community Edition Reborn

**Release 0.23.0 — M6e-2: the factory controller's screen, its two textures, the two preview rows, and its config keys.**

This completes the factory controller begun in 0.22.0 (M6e-1 supplied its semantics, registration and thread model).

## The screen

`FactoryControllerScreen` (573 lines) plus `FactoryControllerMenu` (246) reproduce the original's `GuiFactoryController` (389 lines): the panel blitted whole at **280×213**, the left-hand queue showing 6 elements of 86×32 per page taken from the elements sheet at `(0,0)` with a 33-pixel step, the original's scrollbar at `(94,8)` height 197 using vanilla's tab sprites, and a 0.72-scaled information column from panel `(113,12)` wrapping at `187`. Container coordinates follow `ContainerFactoryController`: blueprint slot at `(255,8)`, player inventory at x=112, y=131/189. Row order matches the original line for line.

**Both textures are byte-identical to the original**, and their sizes were read from the PNG headers rather than trusted from a document:

| Texture | Size | SHA-256 (both sides) |
|---|---|---|
| `guifactory.png` | 280×213 | `C54FA65BCAA38927B4210C8C56A6D97FC55D1FB144DAAD8444C5B76CC94CC51E` |
| `guifactoryelements.png` | 256×256 | `59E3B15AF436E1DFFE05B329C0EEC687FFC05004CF377F78328840AF8BEE1184` |

Thread values reach the client through the menu's `ContainerData` (12 scalars plus 6 slot-state ordinals); the strings (slot recipe, progress, machine name) ride the block entity's existing update tag.

Four honest divergences are recorded in the class javadoc: the status block is no longer gated behind `hasIdleThread()`; the two parallelism rows use the controller's own ledger instead of re-walking threads on the client; a row prints its own status rather than inventing a recipe name; and `gui.factory.threads`' first number counts ordinary threads only — which is the original's own asymmetry.

## The two preview rows

`max-threads` and `core-threads` — **omitted on purpose since M6c because the data did not exist** — are now supplied, under the original's guards (`hasFactory()` for the first, a non-empty core set for the second) and after the parallelism rows.

⚠️ `PreviewPanel` was touched in exactly one method, `buildMachineInfo`. **The preview's input design is untouched**: `StructurePreviewCategory.PreviewInputHandler.getArea()` still returns the whole panel `(0,0,184,220)`, `PointerClaim` hit-testing is unchanged, and **`StructurePreviewRenderer.java` was not modified at all** (its mtime is still 01:16).

## The two config keys — read from the rendered spec

- `factory-system.default-factory-max-thread` = **10**, range `1 ~ 100`
- `factory-system.enable-factory-controller-bydefault` = **false**

The default is **10, not the field initialiser's 20**: the original's `Config.load()` unconditionally overwrites the field with the config-read value, whose default is 10 — so 10 is what the original actually ran with. `MachineDefinition.DEFAULT_MAX_THREADS` and `FactoryThreadModel.DEFAULT_MAX_THREADS` now both read the key.

These went into the **existing single `ModConfig`**: still exactly one real `.registerConfig(` call and one `new ForgeConfigSpec.Builder(`, and only `config/ModConfig.class` in the jar. A second `COMMON` spec is what crashed 0.21.0.

The key paths were verified by **walking the rendered spec** rather than reading the source — 0.20.0 shipped keys nested five levels deep because `ForgeConfigSpec.Builder` is path-accumulating, while the documentation described paths the code never wrote. The instance's existing 865-byte config file was round-tripped **on a copy** through Forge's own read path: the correction is purely additive, all 12 keys remain readable, and the user's values are preserved.

## Acceptance — 352/353 offline checks, 0 failures

The harness grew from 271 to **352 PASS / 0 FAIL** (section R adds 81). Section R reads key paths and defaults **from the rendered spec**, drives the preview rows through the production method on loader-built definitions, asserts the screen's fixed metric table (including that the queue ends at 94 and the text starts at 113, so they cannot overlap), checks both textures' SHA-256 and dimensions, and compares the language keys' wording against the original's `.lang`.

## The four mandatory checks

1. **SRG reobfuscation** — build product **and** deployed file: official name 0 hits, SRG `f_NNN_` 1 hit.
2. **Build order** — harness → `clean build` → reobf check → deploy, never reversed.
3. **One config per type** — exactly one registration, one builder.
4. **Harness rot** — **this check paid off on its first outing**: the harness failed to compile (a bad `check(String,boolean,boolean)` overload in a new assertion). Confirmed as an assertion typo, not production code. Two further harness bugs were fixed: its `ForgeConfigSpec` reflection argument list, and that `CommentedFileConfig` must be `load()`ed after `build()` — otherwise the spec validates an empty config.

Jar SHA-256 `6F9DA7435B6C25D3E48B0FC0C4A809DF582ABD418F0B8DA1D3DC9B5C020B064A`, deployed with a matching hash. `mods.toml` 0.23.0; **zero** files under `data/`; language keys **158 each, identical sets**. `clean build` is not bit-reproducible here (two runs gave `642E5B…` then `6F9DA7…`), so the archived hash is the deployed file's.

## A note on where the evidence landed

The harness's stdout goes to the **Gradle daemon log** (`.gradle-home/daemon/8.1.1/…`) when it is driven through a Gradle task, **not** into the `_audit/m6c-verify/` artifact files — which is a volatile location Gradle prunes. The 352-check run was rescued from there into `_audit/m6c-verify/m6e2-run.txt`. This is the third time a harness claim's supporting file was not where a reader would look; treat "where is the evidence?" as part of reviewing any acceptance claim.

## Not verified — needs your eyes

Whether the screen opens at 280×213 with both textures (not purple-black) and the queue clear of the text; the row colouring, progress fill and scrollbar feel; whether the closing `Avg:` line is pushed off the panel (that depends on the real wrapped line count and cannot be computed offline); the client thread table's refresh cadence (the update tag is signature-throttled); the real Forge config UI and comments; and the two preview rows plus a no-regression check of the preview's mouse and button input.
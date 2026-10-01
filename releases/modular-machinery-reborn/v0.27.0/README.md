# Modular Machinery: Community Edition Reborn

**Release 0.27.0 — Wave 1 item 3: the `modularcontroller` (MOC) compatibility namespace.**

## What it is

The original MMCE absorbed the older **ModularController** mod and registers a controller block under the **`modularcontroller`** namespace so older saves keep their controller. Registry name: **`modularcontroller:<machine path>_controller`** — namespace hardcoded at `RegistryBlocks.java:414`, path built at `BlockController.java:97-99` from the *machine's* registry path, for every machine that is not `isFactoryOnly()` (`:410-412`). An item form is registered with the same name (`:417-418`).

**It is the same block class and the same tile entity** — `BlockController.java:94-100` differs from the ordinary `:86-92` constructor only in the namespace, and `:289-297` returns the ordinary `TileMachineController`. So there is **no second block class and no tile migration**: an old-save block forms, crafts and binds exactly like an ordinary controller. Deprecation tooltip at `:118-121`, keys `lang/{zh_CN,en_US}.lang:316-317`, reused verbatim.

## D20 — why the block is registered unconditionally

The original gates registration behind the config key `modular-controller-compatible-mode` (default `false`). **That gate cannot exist on 1.20.1**, and this is a *proof*, not a preference:

- `LOAD_REGISTRIES` is the state that fires `RegisterEvent` (`ForgeStatesProvider.java:21-25`, `GameData.java:321-338`).
- `CONFIG_LOAD` — which calls `ConfigTracker.loadConfigs` — is declared **later** (`ModStateProvider.java:63-69`).
- `ForgeConfigSpec.isLoaded()` is `childConfig != null` (`:104-106`), assigned only via `ConfigTracker.loadConfigs → ModConfig.setConfigData`.

So at registry time `SPEC.isLoaded() == false` and every boolean accessor returns its pre-config fallback — "read the config to decide whether to register" degenerates to a constant here. And a save deserialises a placed block **by id**, so the id must exist before the game can open the world in which the flag would ever be read.

The harness asserts the ordering by reading both Forge source files out of the sources jar, plus a behavioural control (clear the child config → both accessors return fallbacks; feed `true` → reads `true`).

Therefore: **unconditional registration**, reusing D9/D10's construction-time declaration scan (`MachineRef` gained `factoryOnly`) — no new mechanism, and the block set still depends on no config value, which is what D8 requires. Recorded as **D20**.

**The cost and its mitigation**: `2×N` extra registry entries. The MOC items are therefore **not in the creative tab**, so a fresh install's visible surface is unchanged while old saves work. (Say the word if you would rather they were obtainable there — one line plus one assertion.)

## The two config keys

Both into the **existing single spec** (still exactly one real `.registerConfig(`), paths read out of the **rendered** spec, not the source; key count 13 → 15:

| Key | Default | Live? |
|---|---|---|
| `general.modular-controller-compatible-mode` | `false` | **Inert** — and its own comment says so: it cannot gate registration on 1.20.1, and the compatibility items are not in the creative tab. An inert key whose comment promised behaviour it cannot deliver would be worse than no key. |
| `general.disable-moc-deprecated-tip` | `false` | **Live** — read when the tooltip is drawn, after configs load. |

## Red first, green, four injections

- **Red** (assertions written first, driven by `Class.forName` + ASM so the pre-implementation run compiles and fails): `SUMMARY: 815 PASS / 13 FAIL (828 checks)`.
- **Green**: `_audit/m6c-verify/harness-run-0.27.0.txt` (self-written) — 1023 lines, **`SUMMARY: 880 PASS / 0 FAIL (880 checks)`**, section Y 89 checks. **This is the first fully green run in the project's history.**
- **Four injections, each with its own `SUMMARY` line** (the previous release had one injection file with none): wrong namespace `874/6`, `factory-only` not skipped `878/2`, tip removed `876/4`, never-register `877/3`.

**Injection B exposed a defect in the author's own assertion**: `!byPath.containsKey("factory_only_one")` could never fail, because the map is keyed by *machine path* and that string was a *file name*. It was corrected to key on the machine path and re-injected. This is the third time an assertion was found to be incapable of failing — hence mandatory check 8.

## The three things I asked for

- **No double-counting (alias, not a second controller)**: the JEI catalyst list still comes from `ModBlocks.controllerItems()` and never from `mocControllerItems()` (asserted from the JEI plugin's bytecode); `boundControllerBlock` still reads the ordinary table; the creative-tab item lambda reads the compatibility table **zero** times; the generated resource pack's two halves have disjoint file sets (10 files asserted); and the formation path has **no namespace branch** (neither controller class' bytecode mentions `MocNamespace` or `getNamespace`).
- **The inert key does not lie** (above).
- **Generated pack + tooltip are both in the checklist**, and the pack's file arithmetic was extracted into `block/GeneratedControllerAssets` (which reads no registry) so the harness can assert the shipped file shapes with fixtures — `GeneratedControllerPack` itself cannot be constructed offline, and that is now recorded in the harness README.

## In-game checklist (9 items, in `交接文档.md` and `README.md`)

An old save loads (no missing model), the `modularcontroller` block renders, forms and works; the two-line deprecation tooltip appears; `disable-moc-deprecated-tip: true` suppresses it; the inert key's comment tells the truth and changing it changes nothing; the creative tab gained nothing; **the JEI catalyst list does not duplicate**; the mod loads on a dedicated server.

## Verification

`clean build` → **BUILD SUCCESSFUL** with `> Task :reobfJar`. **Reobfuscation verified on the build product, this archive and the deployed file.** `mods.toml version="0.27.0"`; **zero** `data/` entries; **no `assets/modularcontroller/**` in the jar** (generated at runtime per author machine name, as designed); language keys **169 each, identical sets**; one real `.registerConfig(`; `StructurePreviewRenderer.java` untouched (mtime still 2026-09-30 01:16:55).

Jar SHA-256 `47E3B2AF654FB3EEFB7BEE0ADD71DDD1F0725271F3CE20E1F2ED09C538A0A608`, deployed with a matching hash. A rebuild for verification produced a jar identical entry-for-entry (173 class + 264 asset entries, all hashes equal) with different ZIP metadata only.

**Not opened in game** — the checklist above is how to close it. Note this feature is specifically about *old saves*, so the strongest test is loading a world that contains a `modularcontroller`-namespace block.

## Project direction change (recorded in the plan)

The owner has set a new goal: **produce a releasable version**. The plan gained a §9: **KJS custom machines and recipes** becomes the top priority, then the **build/selection tool**, then release readiness; the projector is optional. **D7's KubeJS entry is upgraded from "optional, not counted toward migration completeness" to "required for release"** — the reason changed from "align with the original" to "give pack authors a usable definition entry point" — while still not counting as a migration gap. M7 is upgraded accordingly, and the build tool moves ahead of its milestone position with the note that it is the project's least offline-verifiable work.
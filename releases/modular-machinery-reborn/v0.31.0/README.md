# Modular Machinery: Community Edition Reborn

**Release 0.31.0 — the first feature release after the public launch. KubeJS can now write a complete machine
definition, and a community-reported set of block-mining defects is fixed.**

## What this release adds

### KubeJS: all eleven machine-definition fields

Before 0.31.0 a script could register a machine but not describe it fully. It can now write every field the data
pack path can, through the same single rule source (`MachineSchema`) that the data pack path uses:

`failureAction` · `requiresBlueprint` · `parallelizable` · `hasFactory` · `factoryOnly` · `maxParallelism` ·
`internalParallelism` · `maxThreads` · `modifier(...)` · `smartInterface(...)` · `coreThread(...)`

Both accepted spellings work: a structured object literal (`{ target: 'item', io: 'output', … }`) and the
equivalent JSON string. The strict path and the tolerant path differ in exactly one documented place (§9 of
`移植方案-v2.md`), and the retro-assertions in the offline harness hold that line.

### Community-reported: every block was slow to mine and dropped nothing

Reported as *"none of the blocks seem to have a required tool set to mine them, so they all take forever to mine
and drop nothing"*. Four independent defects sat behind it, all of them silent — no build error, no log line, no
warning anywhere:

1. the mineability tags were written under this mod's namespace instead of `minecraft`, so the game read a tag
   nobody consults;
2. **no block in the mod had a loot table at all** — a block with none drops nothing however good the tool is;
3. the twelve loot tables that were added then had to be split per block-state variant (`size`, `casing`,
   `type`), because those families register one item per variant under a *different* name than the block;
4. the generated data pack for author-declared controllers needed three separate corrections: it has to declare
   `minecraft` among the namespaces it serves, it has to strip the disk folder from the listing prefix before
   matching, and the resource ids it hands back must carry both the type folder and the `.json` suffix.

Mining speed and drops now behave as they did in the original (`setHarvestLevel("pickaxe", 1)`: a stone pickaxe
or better). A declared machine's controller — registered per declaration, so no file in the jar can name it — gets
its loot table and its tags from a generated data pack.

## Verification

- **Offline acceptance harness**: **1212 PASS / 3 FAIL (1215 checks)**. The three failures are the pre-existing
  environmental config-migration checks, kept as failures on purpose (`_audit/m6c-verify/harness-run-0.31.0.txt`).
- **必查 1 (SRG reobfuscation)**: `CREATIVE_MODE_TAB` 0 occurrences, `f_NNN_` 1 occurrence in `ModBlocks`.
- **In-game acceptance**, by runtime probes rather than by inspection (the four defects above are all invisible to
  any check of file *contents*): `minecraft:mineable/pickaxe` grew 1804 → **1819** entries with nothing of ours
  missing; the pack handed over **2 files** per namespace and the game read all four tables
  (`served …/loot_tables/blocks/immortal_bloom_assembler_controller.json` etc.); the loot-table self-check went
  from *4 have NO loot table* to **0**. The report was then confirmed fixed by the reporter.
- **Diagnostics removed**: everything used to find the above was temporary and is gone from this jar
  (no `MineabilitySelfCheck`, no `pack probe` lines).

## A note on the artifact hash

**This project's jar build is not byte-reproducible.** Three `clean build` runs during this release produced
626 KB / 619 KB / 626 KB and three different SHA-256 values from identical sources. The hash recorded in
`SHA256.txt` therefore means exactly one thing: **the archived jar, the built jar and the jar in the instance's
`mods/` directory are byte-identical to each other**. It is not a claim that anyone can rebuild the same bytes.
(The offline harness depends on Gradle's `jar` task, so running it rebuilds the artifact — run it *before*
copying, not after.)

## Still in development — not finished

The done/not-done lists are in `docs/简介.md` / `docs/Introduction.md`. The port is a foundation: machines,
recipes, hatches, controllers, blueprints, the 3D structure preview, JEI integration, the construct tool and the
KubeJS entry point are in; the compatibility layer for other mods' APIs and several original features are not.

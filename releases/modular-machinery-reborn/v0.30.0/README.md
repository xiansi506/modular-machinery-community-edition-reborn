# Modular Machinery: Community Edition Reborn

**Release 0.30.0 — first public release. Functionally identical to 0.29.0: this pass adds no features, only the documentation and the release identity. ⚠️ STILL IN DEVELOPMENT — not finished.**

## What this release is

The first version intended for publication. **Everything user-visible was verified in game** before it; this pass changed **no production code** (the newest file under `src/` predates the pass).

| Verified in game | version |
|---|---|
| KubeJS recipes | 0.27.1 |
| MOC `modularcontroller` namespace | 0.27.0 |
| KubeJS machine definitions | 0.28.2 |
| Structure construct tool (selection + export) | 0.29.0 |
| GUIs, 3D preview, JEI recipe pages, upgrades/parallelism/factory | earlier releases |

## Content distribution: documentation only

The owner's decision: **nothing is bundled**. The jar's `data/` stays empty, **no example data pack ships**, and `example-datapack/` remains a repo-only reference. **The documentation is therefore the distribution mechanism**, and the release stands or falls on whether a reader can get from an empty instance to a working machine using it alone.

**Start here: `modular-machinery-reborn/docs/使用说明.md`** — the getting-started guide (498 lines): what the mod is and is not; the three ways to define a machine (data pack / config directory / KubeJS) each with a complete copy-pasteable example; the two ways to define a recipe; which controller each source gets and why; the construct-tool workflow; `/reload` versus restart; a worked "hello world" with build steps and the exact log lines; and a 12-row symptom → search-string → meaning table for when it does not work.

Also new in this pass: `docs/简介.md` and `docs/Introduction.md` (Chinese and English introductions, both carrying the **work-in-progress** banner and the done/not-done lists).

## Documentation defects fixed in this pass

- **The `.register()` landmine.** Two checklist snippets (in `docs/KJS-配方指南.md` §七 and `交接文档.md`'s 0.28.x section) did not call `.register()` — copying them yields `posted; 0` and no machine, **with no error**. `MachineDefinitions.stage()` has exactly one caller, `MachineBuilderJS.register()`. Fixed in place with the code-level justification.
- **A broken link in `v0.27.1/README.md`** had **two** defects, which is why two earlier fix attempts failed: it was one `../` short **and** the directory was spelled `modular_machinery_reborn` (underscore) where it is `modular-machinery-reborn` (hyphens). Fixed; a whole-tree link scan (stripping `<>` before resolving) reports **122 `.md` files, 84 relative links, 0 broken**.
- `docs/已知限制.md`: stale harness figure (880 → **1168 PASS / 3 FAIL (1171 checks)**, with the evidence path); the KubeJS machine row moved from "being surveyed" to **delivered and verified in game**; the construct-tool rows moved to **verified in game**. `docs/README.md` gained a **使用者文档** section — the index had listed none of the three user-facing docs.

## Deliberately marked unverified

The getting-started guide's §七.6 marks, rather than asserts: the Hello-World recipe object (the *shape* was verified at 0.27.1, but bound to `alloy_furnace`); the two hatch positions that make it craft; the data-pack and config-directory copies of that machine; and **`<instance>/datapacks/` (world-wide) is called out as never verified in game** — the guide tells readers to use `saves/<save>/datapacks/`.

## The eight mandatory checks

1. **SRG reobfuscation** — `javap -c -p ModBlocks` on the build product and the deployed file: official `CREATIVE_MODE_TAB` **0 hits**, SRG `f_\d+_` **1 hit**; the 0.29.0 code is reobfuscated too (`ConstructToolItem.useOn` → `m_6225_`).
2. **Order** — one harness run → `clean build` → reobf checks → deploy, with no source change afterwards (none at all in this pass).
3. **One config per type** — exactly one real `.registerConfig(`; no key added.
4. **Harness parity** — six `mmverify` sources byte-identical between `_audit` and the working copy.
5. **Config key paths from the rendered spec** — 13 keys in section R plus 2 in section Y = 15, no repeated path element; the user's `-common.toml` already carries every key (2220 chars, 0 corrections).
6. **Evidence** — `_audit/m6c-verify/harness-run-0.30.0.txt`, first line `evidence file: C:\mmwork\_audit\m6c-verify\harness-run-0.30.0.txt`, **1414 lines**, **`SUMMARY: 1168 PASS / 3 FAIL (1171 checks)`** — identical to 0.29.0, as expected with no functional change. The 3 are the known environmental config-migration failures; **the user's config was not rewritten**.
7. **In-game status** — stated plainly: **0.30.0 is functionally 0.29.0**, and 0.29.0's ten-step construct-tool checklist was verified on 2026-10-01, as were the KJS machine definitions, KJS recipes, the MOC namespace, the GUIs, the 3D preview and the JEI recipe pages. A fresh user's first steps are in the guide.
8. **Assertions can fail** — nothing weakened: all six harness sources are byte-identical to their pre-pass copies, and the suite's failure machinery still works (the run ends `3 CHECK(S) FAILED` and exits non-zero). The red-first and five injection runs are intact, each with its own `SUMMARY`.

## Build and artifact

`clean build` → **`BUILD SUCCESSFUL in 19s`** with `> Task :jar` and **`> Task :reobfJar`**. Jar **624026 bytes**, 479 entries, `mods.toml version="0.30.0"`, `data/` entries **0**, `kubejs.plugins.txt` at the classpath **root**, language keys **176 each, identical sets**. SHA-256 `D9C72C6E329544D0B407A52B4163EADD9BBF9DE35F80537AB23B98A25B10DE6B` — build product and deployed file identical.

**This release is still a work in progress.** The unfinished list — KubeJS extended fields, `dynamic-patterns`/`assembly_line`, gas requirements, per-machine JEI categories, `color`, the projector, the preview button row — is in `docs/简介.md` / `docs/Introduction.md`, and the user-facing limits in `docs/已知限制.md`.
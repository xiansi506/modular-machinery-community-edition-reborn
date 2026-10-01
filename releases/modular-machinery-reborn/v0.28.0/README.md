# Modular Machinery: Community Edition Reborn

**Release 0.28.0 — v2a: machine definitions can now be written in KubeJS (core fields).** Marked **built, not verified in game**.

## The ordering question — answered from bytecode, not guessed

`KubeJSPlugin.onServerReload()` has exactly **one** call site: the invokedynamic in `ServerScriptManager.wrapResourceManager`, positioned **after** script evaluation and **before** `ServerEvents.LOW_DATA/HIGH_DATA/RECIPES`. Its two mixin hosts (`inject_resources/MinecraftServerMixin`, `inject_resources/WorldLoaderPackConfigMixin`) both inject at `WorldLoader$PackConfig#createResourceManager` per `kubejs-common-refmap.json` — the shared entry for **both** server start and `/reload` — and that point sits **before** `ForgeEventFactory.onResourceReload`, where this mod registers `MachineLoader`.

So within one reload: **KubeJS evaluates scripts → `onServerReload` → Forge collects listeners → listeners apply.** The merge is therefore **deterministic**, and no new mechanism was needed.

## The merge contract

`MachineRegistry.replace` is whole-table and `MachineLoader.apply` calls it on every reload, so pushing definitions straight from a script would be wiped by the next reload. Instead the script layer **stages** definitions, and the loader reads them:

| Precedence | Source |
|---|---|
| 1 | **KubeJS script layer** (`MachineDefinitions.staged()`) — cleared and rebuilt via `onServerReload` each cycle |
| 2 | Config directory `config/modular_machinery_reborn/machinery/` |
| 3 | Data pack `data/<ns>/machinery/` |

- **Id collision**: the script wins, and the log **names** the id and both sources.
- **Script removed**: the next cycle simply has no definition for it, so the machine disappears. The layer is **not cached across cycles**, so no second cleanup path exists.
- **Repeated reloads are idempotent** — and the assertion compares the **whole table**, not counts, because "wiped by the next reload" is exactly the failure mode the survey predicted.

## Shared validation

Every field rule moved into `machine/MachineSchema.java`; `MachineLoader.readMachine` is now a three-line delegate, and the KubeJS `MachineBuilderJS` is a pure JSON assembler with **no validation of its own**. Errors are identical apart from naming their own source:

- data pack: `A part of mmverify:z_json has no 'elements'`
- script: `A part of machine 'scripted_smoke' has no 'elements'`

A bytecode assertion additionally proves the builder contains **none** of the schema's nine rejection sentences.

Two deliberate differences: the data-pack path stays **lenient** (unknown root fields warn, for historical compatibility) while the script path is **strict** (errors, listing every known field); and unimplemented fields are **rejected by name** on the script side.

## The API (core fields)

```js
MachineRegistryEvents.registry(event => {
  event.machine('kubejs_furnace')                    // registryname (bare path gets this mod's namespace)
    .localizedName('脚本熔炉')                        // localizedname
    .part(1, -1, 0, 'minecraft:stone')               // parts[]: coordinates + accepted block(s)
    .part([-1,0,1],[0],[-1,0,1],'minecraft:iron_block')  // arrays = cartesian product
})
```
plus `.elements(...)`, `.block(...)` and an `onEvent('machineRegistry', …)` alias. Field names and semantics follow the data-pack JSON verbatim.

**Deferred (script use is rejected by name; the data pack still evaluates them):** `modifiers`, `smart-interfaces`, `has-factory`, `factory-only`, `max-threads`, `core-threads`, `max-parallelism`, `internal-parallelism`, `parallelizable`, `failure-action`, `requires-blueprint`. This leaves **no hole in the API**: each has a default, so a scripted machine is item-for-item equivalent to a data-pack machine that writes none of them (six defaults asserted). Blueprints still work for scripted machines.

## D8: a scripted machine gets the generic controller

Same treatment as a machine that exists only in a data pack: the **generic `machine_controller`**, plus a loader line saying so. It cannot have a per-machine controller block, because blocks are registered at mod construction. (`ModBlocks` cannot be class-loaded offline — `FMLPaths.get()` is null — so the shape is asserted rather than the block.)

## Red first — honestly labelled as NOT done properly

**The order was implemented-then-asserted, a method defect, and it is recorded as such.** The compensation was to remove the v2a implementation from the working tree (four KubeJS classes, the loader's call into the script layer, the schema's `strict`/`deferred`), run the harness, and restore byte-identically: **`958 PASS / 24 FAIL (982 checks)`**, red exactly in the new section Z. But as the agent noted, *writing the assertion first tests whether the assertion is right; injecting afterwards only tests whether it can go red* — the two are not equivalent.

**A second finding**: fault injection D was **green at first**, because the assertion only checked that the builder called `MachineSchema.read` — and `read` has two overloads, the lenient one satisfying it too. A bytecode assertion (must be `iconst_1` before the schema call) made it red. That is the **fourth** assertion in this project found incapable of failing.

## Evidence (each SUMMARY read out of its own file)

| File | SUMMARY |
|---|---|
| `harness-run-0.28.0.txt` | `1008 PASS / 3 FAIL (1011 checks)` |
| `red-first-0.28.0-kubejs-machine-absent.txt` | `958 PASS / 24 FAIL (982 checks)` |
| `fault-injection-0.28.0-naive-push-no-merge.txt` | `997 / 13` |
| `fault-injection-0.28.0-script-layer-loses-collision.txt` | `1004 / 6` |
| `fault-injection-0.28.0-script-path-not-strict.txt` | `1003 / 7` |
| `fault-injection-0.28.0-builder-not-strict.txt` | `1007 / 4` |
| `fault-injection-0.28.0-loader-applies-layer-first.txt` | `1007 / 4` |

## Verification

`clean build` → **BUILD SUCCESSFUL** with `> Task :reobfJar`. **Reobfuscation verified** on the build product and the deployed file. Jar: `mods.toml version="0.28.0"`, **zero** `data/` entries, `kubejs.plugins.txt` at the classpath **root 1 / META-INF 0**, five KubeJS classes, **zero `dev/latvian` (Rhino) entries**. Language keys unchanged (169/169). `StructurePreviewRenderer.java` unchanged (mtime still 2026-09-30 01:16:55).

Jar SHA-256 `606BFC43025CC3242BB494423FBDA46DBD42A542EA37D2E328CE435BAF9D009F`, deployed with a matching hash.

## A build file was changed — and it revealed a latent gap

`build.gradle`'s `use_local_deps` branch was missing **Rhino**: `EventHandler extends dev.latvian.mods.rhino.BaseFunction`, so any `EventHandler#post` needs Rhino on the **compile** classpath. No code in this mod called it before 0.28.0, which is why the gap was invisible. Added `compileOnly fg.deobf('libs:rhino:1.20.1')`, with the instance's Rhino jar copied byte-identically into `libs/`. The shipped jar contains no Rhino.

**This is worth generalising**: the first piece of code that calls a new third-party API can expose a missing `compileOnly` dependency — the same "first time we do X" family as the two startup crashes.

## Not verified — the in-game checklist is the gate

The harness **cannot run KubeJS** (the four KubeJS classes cannot even be `Class.forName`d offline: `NoClassDefFoundError: dev.latvian.mods.kubejs/event/EventJS`). So this release is **built, not verified in game**.

1. Put the machine script in `kubejs/server_scripts/` and `/reload`. Expect: `machine registry event posted; 1 machine definition(s) were staged` → the machine loads with `scripted_smoke -> 4 parts, size 3x1x3` → a line saying it has no controller block of its own.
2. Build it with the **generic controller** (stone on four sides) → the controller must report 「找到结构：Scripted Smoke」.
3. **`/reload` a second time — the machine must still be there** (this is the survey's predicted failure mode).
4. **Delete the script → `/reload` → the machine must disappear.**
5. Id collision with a data-pack machine → the log names the id, and the script wins.
6. A `.part` without its block argument → an error **character-identical** to the data-pack one.
7. An unknown field is rejected and lists every known field; `has-factory` in a script is rejected while the data pack still accepts it.

## Deferred to v2b

The eleven fields listed above, plus factory controllers for scripted machines.
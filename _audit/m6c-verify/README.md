# Offline acceptance harness (M6c 0.19.0, extended for M6d 0.20.0, M6b 0.21.0 and M6e-1 0.22.0)

This directory is **not** part of the mod and is not compiled into the jar. It exists so the claims
"parallelism really settles N copies" (M6c), "a placed parallel controller really raises the limit, and the
raised limit settles that many copies" (M6d), "an upgrade in a bus really changes the craft outcome" (M6b) and
"N factory threads really run different recipes at the same time" (M6e-1) can be checked without launching
Minecraft, because the plan forbids accepting any of them on "a number appeared in the GUI".

## What it does

`mmverify/ParallelCraftCheck.java` drives `com.reborn.modularmachinery.recipe.MachineRecipe` in the exact order
`MachineControllerBlockEntity#tick` does — compute the limit, freeze it, `canStart`, `start`, per-tick,
`canFinish`, `finish` — against in-memory `ItemStackHandler` / `EnergyStorage` ports, and prints what actually
moved.

For M6e it drives `com.reborn.modularmachinery.factory.FactoryEngine` — the production engine, not a copy of its
arithmetic — through a `FactoryHost` that answers with in-memory ports. The engine is deliberately free of
`net.minecraft.world.level.Level`, which is what makes this possible at all.

| Section | What it covers |
|---|---|
| A–J | M6c: the modifier arithmetic, the per-requirement limit, N copies consumed/produced, probabilities, defaults |
| K | M6c: the machine loader's three root fields and every validation message (via reflection on `readMachine`) |
| L | **M6d**: the five tier ceilings; `ParallelControllerCollection` sums one/two controllers and honours a controller dialled down to 8; `ParallelismLimit.resolve` with and without a controller, its clamp and `parallelizable:false`; then the end-to-end craft of the datapack's 1-cobblestone→1-stone demonstrator for **every** tier, counting items that moved; one-short-of-the-ceiling; and the `[0, max]` clamp |
| M | **M6d**: the shipped `example-datapack` really carries the demonstrator — `alloy_furnace`'s `max-parallelism`/`internal-parallelism`, the roof-centre `(0,1,1)` element list accepting `modular_machinery_reborn:parallel_controller`, and the demo recipe's machine/time/requirement kinds |
| N | **M6d**: the new `ParallelControllerUpdatePacket` survives a varint round trip for 0 … `Integer.MAX_VALUE` |
| O | **M6b**: the five bus tiers and their config fallbacks; the shipped upgrade declarations and item mappings, read through the loader's own private readers; then the bus arithmetic — a `duration ×0.5` upgrade really halves the ticks ticked (5 → 3) while the items consumed and produced are unchanged, a `stackable` upgrade squares its value at count 2 (0.5 → 0.25) while a non-stackable one does not, an `item output ×2` upgrade really doubles the stone a craft produces, an upgrade the machine's whitelist rejects contributes **nothing** and the craft settles its unmodified copies, two carrier slots merge into one upgrade with their counts summed, and the controller's own `collectBusModifiers` join is reachable and adds up |
| P | **M6e-1**: the machine loader's four new factory fields — `has-factory`, `factory-only`, `max-threads`, `core-threads` — their defaults, every malformed value's rejection message, the `factoryEnabled` decision in all four block/machine combinations, the shipped factory machine + claim + both recipes, and the property that makes the demonstrator countable (the two recipes share **no** input and **no** output item) |
| Q | **M6e-1**: the thread model itself. Two unpinned threads run two different recipes — asserted by capturing each thread's recipe **and progress** after every engine tick, so the check is "two crafts advanced in the same tick", not "two crafts existed" — and by counting what moved (8 cobblestone + 8 coal in, 8 stone + 8 diamonds and 8000 FE out). Then `max-threads` bounding the pool (peak 1 vs >1, and 8 vs 12 settlements in 40 ticks), two recipes pinned to two core threads both advancing in one tick, `max-threads: 0` allowing no ordinary thread at all, a pinned core thread refusing everything outside its set, the parallelism ledger (8 → 1 → 1 out of a ceiling of 8, floored at 1), NBT save/load round-tripping threads with their progress and recipes, and the 200-tick idle sweep reaping ordinary threads while sparing core ones |
| R | **M6e-2 / 0.24.x**: the config keys as the spec renders them, the preview rows, the panel metrics and the texture blits; the factory screen's panel rectangles as containment inequalities; the slot-frame alignment. 0.27.0 extends its rendered-key count from 13 to 15 and the language-key count from 167 to 169. |
| U, V | **0.24.2 / 0.24.4**: the two information blocks and the factory panel stay inside their own region (fault-injected against the stale geometry), and the bytecode-level alignment checks. |
| W | **0.25.0**: `failure-action` is consumed at the two *already-started* failure points, in all three modes. |
| X | **0.26.0**: `ingredient_array_input` — the original's own reader, one-of-the-group consumption, the rejection messages, and the parallelism arithmetic. |
| Y | **0.27.0**: the `modularcontroller` compatibility namespace. (1) the timing crux: the load-order chain read out of Forge's own sources plus the behavioural control, showing `SPEC.isLoaded()` is false while `RegisterEvent` fires; (2) the registry names and which declarations get one (`factory-only` skipped); (3) the alias rule — nothing enumerates a compatibility controller as a controller (JEI catalysts, structure preview, creative tab, generated pack, and the absence of any namespace branch in the forming path); (4) the old-save shape — same block class, same tile, no migration; (5) the two deprecation keys, verbatim, in the original's order, behind the live config gate; (6) the two config keys as the rendered spec spells them, and the inert key's comment saying so; (7) the generated pack's second namespace and the 169-key language files. |
| Z | **0.28.0**: the KubeJS **machine-definition** entry point (v2a). (1) the core field set and the six defaults a script machine keeps; (2) **one schema, two paths** — the same defect through the loader's own `readMachine` and through the strict script entry point must produce the same sentence, with only the name of the definition differing; (3) an unknown field is refused with the list of known ones, eleven deferred fields are refused by name, and `strict`/tolerant differ; (4) the merge contract — a script machine survives one reload and a second one, precedence is script &gt; config dir &gt; data pack, deleting the script deletes the machine, and a reload with no scripts empties the layer; (5) what a script machine gets for a controller block (D8) and the loader's own sentence about it; (6) the loader's call order, read out of `javap`; (7) the plugin's wiring plus the reload-order chain read out of the KubeJS jar; (8) `kubejs.plugins.txt` at the classpath root and not under `META-INF/`. |
| Z9 | **0.28.1**: the **binding** a script actually receives — the one thing 0.28.0's section Z never looked at, which is why 0.28.0 shipped a binding with no events on it. This is the first section that loads KubeJS for real: the **deobfuscated** KubeJS and Rhino jars (the checkout's `libs/*.jar` are SRG-remapped and mismatch this harness's Mojmap Minecraft — observed as `NoSuchMethodError: Component.m_237113_`) go into a **child-first** loader with a run-time-compiled stand-in for Architectury's `Platform`. It then calls the mod's real `registerEvents()` and `registerBindings(...)`, and evaluates the documented script's first line with KubeJS's own Rhino: the published object must be an `EventGroupWrapper`, `typeof MachineRegistryEvents.registry` must be `function`, and the call must reach the group's `EventHandler`. The same run puts the **raw group** through the identical lookup, so the assertion carries its own counter-example — `typeof` is `undefined` and the script dies with the owner's `TypeError: Cannot find function registry in object MachineRegistryEvents.` **It proves the binding shape, not the game**: no server, no `ScriptManager`, no listener really invoked. The `/reload` checklist is still the final word. Section Z7 also gained a bytecode-level check that the built plugin names `EventGroupWrapper` (which proves only that the artifact refers to the class). |
| **Z8b** | **0.28.2**: the builder must not declare two same-named `part` methods, read **out of the class file's member list** (`javap`, the same cheap technique Z6 uses for `register()`, and deliberately *not* `Class.forName` — even `initialize=false` resolves `MachineBuilderJS`'s KubeJS supertype and dies with `NoClassDefFoundError: dev/latvian/mods/kubejs/event/EventJS`, observed). 0.28.1 declared `part(int,int,int,Object...)` **and** `part(List,List,List,Object...)`: same name, same arity, both varargs, told apart only by Rhino's conversion weights and by `getMethods()`' order, **which the JVM specification does not define**. The rename is what removes the question, so the assertion is that `part` occurs once with the scalar signature and `parts` once with `List` parameters. |
| **Z10** | **0.28.2**: which Java method the documented `.part(1, -1, 0, 'minecraft:stone')` actually reaches. Same child loader as Z9 (real deobfuscated KubeJS + Rhino), and the real `MachineRegistryEventJS` is instantiated and released to Rhino as `event`. It evaluates the guide's own call — `.machine('scripted_smoke').localizedName(...).part(1,-1,0,'minecraft:stone').part(0,1,0, casing, casing)` — and asserts the resulting JSON is **byte-for-byte the definition a data pack would have carried**; `"x":1` is only reachable through the scalar method, so the resolution is read off the result rather than off a stack frame. It then asserts the array spelling under its new name (`.parts([-1,0,1],[0],[-1,0,1], …)` → the cartesian product), that the old array-under-`part` spelling is refused **by the scalar signature** (`Cannot convert -1,0,1 to java.lang.Integer`) rather than silently routed, and that a fractional coordinate is refused by name instead of truncated. Finally it **measures** Rhino's own conversion weights: a script number → `List` is 99 (`CONVERSION_NONE`, i.e. impossible) while → `Integer` is 2, and a script's array arrives as `[-1.0, 0.0, 1.0]` — a `List` of `Double`, which is why `parts(…)` is declared over `Number`. **It proves the documented call is accepted by KubeJS's engine and produces the right definition; it does not prove the game**, and it does **not** claim what 0.28.1's runtime ordering chose — `getMethods()` order is unspecified, so what this release does is remove the choice. |
| **AA** | **0.29.0**: the construct tool (`itemconstructtool`). The original's tool is a **selection-to-JSON exporter, not a builder** — `ItemConstructTool.java` has a single `onItemUse` handler and never calls `setBlockState` — so what is asserted is (0) the seven production classes exist and the loader's own registry resolves a vanilla block here (which is what lets the fragment be fed back through the loader); (1) the seven language keys are the original's own wording, key for key, in both files; (2) the selection: toggling in and out, insertion order, re-adding appends, copies rather than views, dedup, and **4096 positions with no cap**; (3) the rotation: N/E/S/W = 0/1/2/3 steps, `(z,y,-x)` per step, identity after 4, and — the invariant that matters — **the tool's offsets equal the loader's own `MachinePattern.rotateYCounterClockwise()` applied to the same set**, so a fragment pasted into a definition is not rotated again; (4) the descriptor (every property, name-sorted) round-tripping through `BlockMatcher.parse`, the fragment carrying `parts` as its only root field, the loader **refusing it by name** (`Missing required field 'registryname'`) and accepting it once a `registryname` is added; (5) the fragment text byte for byte; (6) the tile-entity NBT with its coordinates removed, as sorted-key JSON; (7) the file-name scheme and the original's collision suffix `" (0)"` then `" (1)"`, UTF-8 bytes; (8) the gate's truth table (exactly one of eight combinations); (9) the packet round trip at 0/1/3/4096 positions and a truncated packet being refused; (10) **a bytecode assertion that `ConstructToolItem` never calls `setBlock`/`setBlockAndUpdate`/`destroyBlock`/`removeBlock`/`placeBlock`/`fillBlocks` and does override `useOn`** — the "there is no build half" finding, nailed down so that adding a placer cannot happen silently. **It proves the arithmetic, the text and the file naming; it cannot prove where a click lands, what the outline looks like, or which directory the file reaches at runtime** — see `交接文档.md`'s 0.29.0 checklist. |

**1168 PASS / 3 FAIL (1171 checks)** at 0.29.0 (the ledger's own count is 1168 PASS / **4** FAIL, because the
evidence-tee's deliberate control check is one of the four; three are the section R environmental FAILs). Section
AA added **129** checks at 0.29.0 (1042 → 1171); the exact figure is read from the green run's own `SUMMARY`.
The three FAILs are the *known environmental* ones in section R's config-migration check: the assertion requires the
correction to the user's `-common.toml` to be **purely additive**, i.e. non-empty, and it is empty because the user's
file state already carries every key the spec defines. 0.28.2 adds **no** config key, which is exactly why the check
has nothing to add — **the user's own config was not rewritten to get there**, and must not be; the harness works on
a copy.

**The harness's own `SUMMARY` is at `harness-run-0.29.0.txt`**: first line `evidence file: C:\mmwork\_audit\m6c-verify\harness-run-0.29.0.txt`,
**1414 lines**, ending `SUMMARY: 1168 PASS / 3 FAIL (1171 checks)`. The red-first run (before any of the seven
production classes existed) is `red-first-0.29.0-construct-tool-absent.txt` (1233 lines, `1042 PASS / 24 FAIL (1066 checks)`),
and the five fault injections are `fault-injection-0.29.0-{.txt}`: `A-rotation-dropped` (`1162 PASS / 9 FAIL`),
`B-toggle-never-removes` (`1163 PASS / 8 FAIL`), `C-gate-without-commands` (`1165 PASS / 6 FAIL`),
`D-descriptor-drops-properties` (`1166 PASS / 5 FAIL`) and `E-collision-counter` (`1166 PASS / 5 FAIL`) — all 1414
lines, each read out of its own file. The full Gradle console logs of those runs are the `*-raw-gradle*.txt` files
beside them (they carry Gradle's own encoding, so non-ASCII shows as `?`; the harness's tee file above is UTF-8 and
is the primary evidence).

**History**: 1039 PASS / 3 FAIL at 0.28.2; 1022/3 at 0.28.1; 880 PASS / 0 FAIL at 0.27.0 and 0.27.1 — 0.27.0 was the
release that added the two MOC keys, so its "purely additive" correction was non-empty and R was green there.
0.25.0, 0.26.0, 0.28.0, 0.28.1, 0.28.2 and 0.29.0 carry the same three FAILs for the same reason.

> **One thing this release measured and did *not* turn into a claim.** Injection A put the 0.28.1 dual-`part` shape
> back and, in that run, Z10's *scalar* assertion stayed **green**: `getMethods()` happened to hand the scalar method
> over first in that process, so Rhino chose it — the same ambiguity, resolved the other way. Section Z8b and eight
> other Z10 assertions went red on the same run. That is why the assertions are split the way they are: the
> **structural** check (one `part` in the class file) is the one that cannot be order-dependent, and the
> **behavioural** check states what it observed rather than what 0.28.1 "must" have done. Reproducing the owner's
> particular loss in a deterministic offline way is not possible; removing the choice is.

**History**: 1022 PASS / 3 FAIL at 0.28.1; 880 PASS / 0 FAIL at 0.27.0 and 0.27.1 — 0.27.0 was the release that added
the two MOC keys, so its "purely additive" correction was non-empty and R was green there. 0.25.0, 0.26.0, 0.28.0,
0.28.1 and 0.28.2 carry the same three FAILs for the same reason.

### The one thing section Z cannot do, and why (0.28.0)

It cannot run KubeJS. The four KubeJS-facing classes are on the classpath as **class files** (Z0 asserts that), but
`Class.forName` on any of them dies with
`NoClassDefFoundError: dev.latvian.mods.kubejs/event/EventJS` — verified with a standalone probe. So everything the
script <b>does</b> when KubeJS runs it is game-only: the in-game checklist in `docs/KJS-配方指南.md` §七 is what
closes it, and this release ships marked "built, not verified in game" until that checklist is run.

What Z does instead is make the *design* checkable without KubeJS: the shared schema (byte-for-byte message
equality between the two paths), the merge contract (production code, `MachineDefinitions`), and the wiring
(`javap` call order, and KubeJS's own bytecode for why the script always runs first). The builder is additionally
checked **not** to contain any of the schema's rejection sentences, so "the same quality of error" is a property
rather than a promise.

**880 PASS / 0 FAIL** at 0.27.0. The count has grown one release at a time: 271 at 0.22.0, 705 by 0.25.0, 791 at 0.26.0 (X added 86) and 880 here (Y added all 89 of the new checks).

### Two repairs this release made to the harness itself

1. **A stale import.** The harness still read `UpgradeBusConfig.MAX_SLOTS_LIMIT`, but 0.21.0's config-conflict
   fix had folded that class into the single `ModConfig` (`MAX_UPGRADE_SLOTS_LIMIT`). It would not compile
   until that reference was updated; nothing about the assertion changed.
2. **ASM on the classpath.** M6e's section builds in-memory `ItemStackHandler` ports and drives the mod's
   recipe classes, which reach `org.objectweb.asm` (`Type`, `ClassVisitor`). `org.ow2.asm` is now kept by the
   classpath filter in `init.gradle`, with a comment saying why.

### One thing section P does *not* do, and why

It reads the shipped recipe JSON with `JsonParser` rather than through `ModRecipeSerializers`. Deserialising a
vanilla `Recipe` outside FML's own class loader reaches `net.minecraftforge.fml.common.Mod` (an annotation
Forge's mod-file scanner carries), so the live path cannot run here. What is asserted is the shipped JSON's
shape; the recipe objects the arithmetic is driven with are built to match it field for field.

## How to run it

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.11.9-hotspot"
$env:GRADLE_USER_HOME="C:\Users\kk07H\Desktop\模组\.gradle-home"
& "C:\Users\kk07H\Desktop\模组\.gradle-home\wrapper\dists\gradle-8.1.1-bin\99jqdkz0zm0r14eocl2t87xca\gradle-8.1.1\bin\gradle.bat" `
  -p "C:\Users\kk07H\Desktop\模组\modular-machinery-reborn" `
  -I "C:\Users\kk07H\Desktop\模组\.tmp-m6c-verify\init.gradle" `
  m6cVerify --offline --no-daemon --console=plain
```

Expected tail: `ALL CHECKS PASSED`.

> ⚠️ **Run this before the release build, never after.** The task depends on `jar`, and in ForgeGradle 6
> `reobfJar` remaps that same `build/libs/<name>.jar` in place. Running `m6cVerify` afterwards makes Gradle
> re-run `jar` and leaves `build/libs/<name>.jar` **un-reobfuscated** — exactly the state that crashed 0.19.0.
> The harness needs those official names to link against `forge-…_mapped_official_…jar`, so the order is:
> harness → `clean build` → reobfuscation check → deploy.


## Why `init.gradle` is so roundabout

Three Windows-specific problems had to be worked around, all of them found the hard way:

1. **The workspace path is not ASCII** (`C:\Users\kk07H\Desktop\模组`). `javac` and `java` decode an `@argfile`
   with the platform ANSI codepage, so a UTF-8 argfile containing that path is mangled into
   `C:Userskk07HDesktopģ��...`. Everything is therefore routed through the ASCII junction `C:\mmwork` →
   `C:\Users\kk07H\Desktop\模组`.
2. **The classpath is longer than the Windows command line allows** (~33 000 chars with every cached jar), and
   `java` expands an `@argfile` back onto its own command line, so it fails with a bare
   `ClassNotFoundException` even though every entry is valid. The filter in `init.gradle` keeps only the
   libraries this harness needs (~20 000 chars).
3. **Mixin's annotation processor** is picked up from the classpath and needs ASM internals that the filtered
   classpath omits, so `javac` runs with `-proc:none`.

`init.gradle` also needs `-Dlog4j2.contextSelector=...BasicContextSelector`, because the default
`ClassLoaderContextSelector` probes `cpw.mods.cl.ModuleClassLoader`, which only exists inside FML's own
class loader.

## Not wired into `build.gradle`

Deliberately. With no test framework in the offline cache (no JUnit anywhere under
`.gradle-home/caches/modules-2`), a `src/test` source set could not be compiled at all, and the project's own
rule is that `build.gradle` changes shipped this release should be the minimum. The harness is run explicitly,
as above.

## Two limits section Y cannot cross, stated rather than papered over

1. **`ModBlocks` cannot be loaded here at all.** Its static initialiser builds `DeferredRegister`s, which reaches
   `net.minecraftforge.fml.common.Mod` — a class Forge's mod-file scanner carries and this harness's filtered
   classpath deliberately omits. Everything section Y says about `ModBlocks` is therefore read with ASM from its
   class file, never by `Class.forName`. (The same wall is why `ForgeRegistries.BLOCKS` cannot be populated, so a
   registry name is asserted through `MocNamespace.controllerName(…)` — the one function the registration calls —
   rather than by reading a live registry back.)
2. **The generated resource pack cannot be constructed here**, because its static fields read `ModBlocks`. The file
   *arithmetic* was therefore moved into `com.reborn.modularmachinery.block.GeneratedControllerAssets`, which depends
   on nothing but the declarations, and section Y drives **that** with a fixture and asserts the blockstate and item
   model it produces. What remains unproven offline is only that the live pack passes the same two declaration lists
   it reads — which section Y checks from the pack's own bytecode.

## One limit this harness cannot cross (found while writing section O)

`ForgeRegistries.ITEMS.getValue(...)` answers `Items.AIR` for **everything** outside Forge's mod loading — a
name that resolves to nothing does not come back `null`, it comes back air. Section O originally tried to
populate the live `UpgradeRegistry` with the shipped item mappings and got two mappings that both named
`air`, i.e. one registry entry instead of two.

The consequence for the code is deliberate and small: `UpgradeEffects.read` has a second overload taking a
`Function<ItemStack, UpgradeTarget.Targets>` lookup, the block entity uses the live-registry overload, and the
harness uses the injection overload with a standing table of `Items.IRON_INGOT` / `Items.COPPER_INGOT`. That
keeps the merge arithmetic — the part worth asserting — on production code, and section O states plainly which
part of the item-mapping path it therefore cannot prove offline.

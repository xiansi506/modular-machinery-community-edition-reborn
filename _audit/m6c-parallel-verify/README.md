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

**271 PASS / 0 FAIL** at 0.22.0 (170 are the M6c+M6d+M6b checks, unchanged; 101 are M6e-1's: P 48, Q 53).

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

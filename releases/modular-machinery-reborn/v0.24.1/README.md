# Modular Machinery: Community Edition Reborn

**Release 0.24.1 — fix: 0.24.0 crashed every time the machine controller screen was drawn (an unguarded sentinel reaching a list lookup).**

## The crash

```
Description: Rendering screen
java.lang.IndexOutOfBoundsException: Index: -1 Size: 2
    at ...block.MachineControllerBlockEntity.startFailureKey(MachineControllerBlockEntity.java:985)
    at ...menu.MachineControllerMenu.startFailureKey(MachineControllerMenu.java:131)
    at ...client.MachineControllerScreen.drawInfo(MachineControllerScreen.java:273)
```

The project's own comment said the index was *"`-1` for **no failure**"* — and then handed that `-1` straight to `List.get()`:

```java
return isServerSide() ? this.startFailureKey : START_FAILURE_KEYS.get(this.clientStartFailure);
```

`START_FAILURE_KEYS` has two entries (the `Size: 2` in the report). `drawInfo` calls this **every frame**, so as soon as a machine had no start failure — the normal case — the controller GUI was unopenable. The feature ("print *which* requirement refused to start") was new in 0.24.0, and its screen was listed in that release's unverified section: no client, no server, no pixels. The first time it was opened in game, it crashed.

## Both halves are fixed

1. **The lookup is guarded.** `NO_START_FAILURE = -1` is now a named constant, and the **only** place the list is indexed is a bounds-checked `failureKeyAt(int)`, which returns `null` for anything that is not a valid index — `-1`, `List.indexOf`'s not-found value, `2`, `Integer.MIN_VALUE`. `setClientStartFailure` treats its argument as untrusted (it arrives over the wire) and normalises it, so a sentinel can never even be *stored*.
2. **The initial state is fixed.** `private int clientStartFailure;` defaulted to `0`, and **`0` is a valid index** — the first failure key. Before the first `ContainerData` sync, a freshly opened screen would therefore have printed a **spurious failure message**. It is now initialised to the sentinel.

`failureIndex` deliberately still returns the sentinel for `null`; it was not "fixed" to return `0`.

**No other sentinel-to-index path exists.** The factory status ordinals go through `ControllerStatus.byOrdinal` (clamps), `FactoryControllerMenu`'s slot ordinals are bounds-checked in `threadStatus(slot)`, `factoryThreadStatus` compares against `threads.size()`, and the screen's row loop is structurally in-bounds. All four are now asserted in the harness rather than assumed.

## The regression check is proven, not merely present

Section **T** — *"no failure is a sentinel, and a sentinel never reaches a list lookup"* — adds 42 checks: the initial value (ASM over the constructor's bytecode), the lookup guard across out-of-range inputs, a **real client-side block entity** driven through the untrusted wire write and the real `load()`, and the screen's null path (with an ASM scan proving `drawInfo` actually calls the guard, so it cannot be silently bypassed).

Then each half of the bug was **put back** to confirm the assertions catch it:

| Fault injected | Result |
|---|---|
| the raw `List.get(index)` restored | **530 PASS / 7 FAIL** — reproducing the exact original signature, `Index -1 … length 0` |
| guard kept, field left at `0` | **540 PASS / 4 FAIL** — `expected iconst_m1, got iconst_0` |

Source was restored and hash-verified after each injection. **A check that has never failed is not evidence.**

Evidence (complete harness stdout, copied out of the volatile Gradle daemon log):
`_audit/m6c-verify/acceptance-0.24.1-sentinel.txt`, plus `fault-injection-1-unguarded-lookup.txt` and `fault-injection-2-wrong-initial-value.txt`.

## An honest note on the harness's three FAILs

The header of that evidence file records **541 PASS / 3 FAIL**. The three failures are **pre-existing and environmental**: section R's config-migration check requires the instance's `modular_machinery_reborn-common.toml` to still be a **pre-0.24.0** file (`isCorrect() == false`, corrections > 0). The 0.24.0 release already rewrote it to the current structure — 1137 characters, `isCorrect() == true`, 0 corrections — so the three "a correction must happen" assertions fail. They were green at 0.24.0 (503/0) while the file was still old.

**The user's config file was deliberately not rewritten to make them green.** Section T reads no config and is unaffected. A fully-green run needs a pre-0.24.0 config file to test against.

## The five mandatory checks

1. **SRG reobfuscation** — build product, archived jar **and** deployed file: official name absent, SRG `f_NNN_` present.
2. **Build order** — harness runs (including both fault injections) all happened **before** the release build; the source was reverted first; no harness run after the build.
3. **One config per type** — one real `.registerConfig(` (`ModConfig.java:200`), one `new ForgeConfigSpec.Builder(`. No key added.
4. **Harness rot** — the harness compiled and ran; its single failure this session was a typo in the new assertion, confirmed as a harness error.
5. **Config key paths from the rendered spec** — section R still reads `ModConfig.SPEC.getValues()` walked to its leaves.

`mods.toml` 0.24.1; **zero** files under `data/`; language keys **166 each, identical sets**. `StructurePreviewRenderer.java` unmodified (mtime still 01:16); preview `getArea()` whole-panel and `PointerClaim` untouched.

Jar SHA-256 `DF754EA0A907E799AD9BD80E91B8E727E194F3079E785432CC05694A772E0B1B`, deployed with a matching hash (the 0.23.0 rollback jar was removed).

## Not verified — the screen still has not been opened in game

The harness cannot render (no `Font`, no resource manager) and has never run a client or a server. What is proven is that the **crashing call path** now returns `null` instead of throwing, driven through a real client-side block entity and the real `load()`.

**What to click:** launch the game and right-click the controller of a **formed** machine. The screen must **open** (0.24.0 crashed on open) and show **no red failure line** — unless the machine genuinely refuses to start for one of the two interface reasons. Then open a machine that **declares `smart-interfaces` with an out-of-range value** and confirm the red line **appears** with the right wording.

Still unverified: menu → screen `ContainerData` index 10 has never round-tripped over the wire. Only the game can confirm it.
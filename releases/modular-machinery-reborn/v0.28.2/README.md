# Modular Machinery: Community Edition Reborn

**Release 0.28.2 — fix: the KubeJS machine builder had two same-named, same-arity varargs `part(...)` overloads, which made `.part(...)` undefined to Rhino. VERIFIED IN GAME (2026-10-01): the schema registers, `scripted_smoke` loads with 4 parts, the structure forms, `/reload` twice keeps it, and deleting the script removes it. The API works end to end.**

## The defect

```java
part(int x, int y, int z, Object... elements)                              // the documented scalar form
part(List<Integer> x, List<Integer> y, List<Integer> z, Object... elements) // the array form, same name
```

Rhino's `NativeJavaMethod#findFunction` filters candidates **by name first**, then by `NativeJavaObject#canConvert`, then compares `getConversionWeight`; equal weights produce `PREFERENCE_AMBIGUOUS` and the winner is "the last candidate remembered", ordered by `Class.getMethods()` — **which the JVM specification does not define**. Two same-named, same-arity, both-varargs methods therefore made the meaning of `.part(...)` **undefined**. Reordering parameters or using `int...` would not help: same name + same arity is the whole problem.

## The fix

- `part(int, int, int, Object...)` — **name and signature unchanged**, so existing scripts need no edit.
- the array form is now **`parts(List<? extends Number>, …, Object...)`** — renamed, and its coordinate type widened.
- `coordinate()` now proves wholeness itself (`1.5` refused by name, `1.0` accepted), because `MachineSchema` sees the JSON *after* the builder wrote it, so a truncation would already have happened.

## An honest limit on the diagnosis

A probe (`.tmp-kjs-api/probe/`) measured the conversion weights on this KubeJS's Rhino: a script **number → `List` weighs 99** (that is `CONVERSION_NONE` — it *cannot* convert) while number → `Integer` weighs 2, and a script **array → `List` works** with `Double` elements. Driving the real classes through the real Rhino, `.part(1,-1,0,'minecraft:stone')` **does** resolve to the scalar method.

So **the empty-list dispatch seen in the owner's log could not be reproduced offline, and is not claimed to be explained.** What *is* proven is that the same-named pair made the call's meaning undefined, and that the **documented array spelling was broken outright** — `.part([1],[-1],[0],'x')` throws `ClassCastException: Double cannot be cast to Integer`, measured on the real 0.28.1 class. That is why the type was widened.

Fault injection A (dual `part` restored) showed this vividly: Z10's *scalar* assertion stayed **green** in that run because `getMethods()` happened to order the scalar first, while Z8b and eight other Z10 assertions went red. Recorded rather than glossed.

## Does the documented scalar call now provably resolve? Yes — through a driven Rhino evaluation

Section **Z10** instantiates the real `MachineRegistryEventJS`, releases it to Rhino as `event`, and evaluates the guide's own line, asserting the produced JSON is byte-for-byte the definition a data pack would carry (`"x":1` is only reachable through the scalar method, so the resolution is read off the result). It also drives `.parts([-1,0,1],[0],[-1,0,1], …)` → the cartesian-product JSON, asserts the old array-under-`part` spelling is refused by the scalar signature, asserts a fractional coordinate is refused by name, and records the two conversion weights as measurements.

**What Z10 cannot prove**: no server, no `ScriptManager`, no listener registered by a script, no event posted. `Reload` is still the final word. It also cannot claim what 0.28.1's runtime ordered.

Section **Z8b** is the order-independent half: it reads the class file's member list via `javap` and asserts `part` occurs **once** with the scalar signature and `parts` once with `List` parameters.

## Red first, green, injections

Red (assertion written and run before the fix): `1029 PASS / 8 FAIL (1037 checks)` — including the array spelling's `ClassCastException` reproduced, and the first Z8b failing because it used `Class.forName`, which cannot work here. **Two more of the author's own assertions were found weak by injection/measurement and corrected.**

Green: `_audit/m6c-verify/harness-run-0.28.2.txt` — 1205 lines, **`SUMMARY: 1039 PASS / 3 FAIL (1042 checks)`** (the 3 are the known environmental ones in section R; the user's config was not rewritten).

Injections: dual `part` restored → **1031 PASS / 11 FAIL (1042)** with 8 distinct assertions red; rename kept but parameters reverted to `List<Integer>` → **1036 PASS / 6 FAIL (1042)**.

## Verification

`clean build` → **BUILD SUCCESSFUL** with `> Task :reobfJar`. **SRG reobfuscation verified on the build product and the deployed file**, and both `part(int,int,int,Object...)` and `parts(List<? extends Number>,…)` survive reobf unchanged. `mods.toml version="0.28.2"`; `data/` entries **0**; `kubejs.plugins.txt` at the classpath **root**, not under `META-INF`; the 0.28.1 binding (`new EventGroupWrapper(...)`) untouched; language keys **169/169, identical sets**; `StructurePreviewRenderer.java` untouched (mtime still 2026-09-30 01:16:55).

Jar SHA-256 `288CA92200B7F4020C37E5F4CB5D1503B1B38D6BEA043FE71621B6C527A7169C` — **build product and deployed file identical**. (Whole-jar hashes differ between builds because zip entries carry timestamps; two builds were compared entry by entry: 467/467, no one-sided entry, all equal.)

## In-game steps — the two decisive ones are unchanged

1. **`/reload` twice, and `scripted_smoke` is still there** (the survey's predicted failure mode).
2. **Delete `mmce_machine_test.js` → `/reload` → `scripted_smoke` disappears** and the structure no longer forms.

Plus the new ones: `.part(1,-1,0,'minecraft:stone')` must produce **zero** errors (the core check); `.parts([-1,0,1],[0],[-1,0,1], …)` must really yield the cartesian product; the old `.part([...])` spelling must be refused by type; a fractional coordinate must be refused by name.

**If a different error appears this time, that is new information** — the previous empty-list dispatch was never reproduced offline, so the next observation is the datum that settles it.
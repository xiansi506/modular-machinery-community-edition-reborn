# Modular Machinery: Community Edition Reborn

**Release 0.25.0 — Wave 1 item 1: `failure-action` is now consumed (an outstanding M1/M2 item).**

## A correction to the original brief

The task assumed the field would be wired to 0.24.0's **start-failure** point. It is not: in the original its only consumer is a failed **per-tick IO check of an already-started craft** (`ActiveMachineRecipe#tick:87-98` → `doFailureAction:106-115`). At `progress == 0` all three values are indistinguishable, so the start-failure point could never have exercised it. Two equivalent points already existed here, both already behaving as `still` — **connection work, no new mechanism.**

## The field

- **Schema**: machine-definition **root-level string**, exactly one of `reset` / `still` / `decrease` (`RecipeFailureActions.java:15-18`). Machine-level, not recipe-level.
- **When**: once **per failed tick** of an already-started craft — `reset` → `tick = 0`; `decrease` → `tick--` while `> 0`; `still` → nothing. Never per requirement, never at a failed start; the "result does not fit" hold never reaches it either (`RecipeThread#onFinished:64-86`).
- **Both engines**: a factory thread ticks the same `ActiveMachineRecipe` (`RecipeThread#onTick:54-62`).
- **Default**: `still`, **not** `reset` — from the original's config key `general.default-failure-actions` (`RecipeFailureActions.java:38-46`, `Config.java:69`). The loader's previous `RESET` default was a **dead value**; the constant is now `STILL`, i.e. **zero regression** for machines that omit the field.

## D17 — the one deliberate deviation

The original left a reset craft **armed at tick 0**, so `reset` never re-paid its inputs. This port's single-craft engine treats `progress == 0` as "no craft", so `reset` (and `decrease` reaching 0) **restarts through the start phase and re-pays its one-shot inputs**. Representing "armed" separately from "progress" is a state-model change — the new mechanism the brief said to report instead of building. The **factory** path is armed by its thread slot, so there it stays armed at 0 exactly like the original, and the harness asserts that.

## Loud validation, replacing a silent hole

The original returned the default for any unknown **name**, so a typo like `"rest"` was invisible. Here a malformed value is rejected with a message naming what to write, and the field now has a **type check** for the first time — typo, number, object, empty array, `null` and `true` are all six rejected.

## Red first — a first for this project

Assertions were written and run **before any production change**: `_audit/m6c-verify/red-first-0.25.0-failure-action-unwired.txt` — 786 lines, `SUMMARY: 678 PASS / 24 FAIL (702 checks)`, the 24 failures being the new section W (11 of them `NoSuchMethodException`, i.e. methods that did not exist yet).

**Green** — `_audit/m6c-verify/harness-run-0.25.0.txt` (written by the harness itself): 789 lines, **`SUMMARY: 702 PASS / 3 FAIL (705 checks)`**, 26 sections, **section W 42 PASS / 0 FAIL**. The 3 FAILs are the known environmental section-R config-migration ones; the user's config was not rewritten.

**Fault injection, both restored hash-verified**: reverting both consumption sites → **697 PASS / 8 FAIL** (three bytecode *wiring* checks reporting `0 call(s)`, plus the two behavioural cases); making `decrease` a no-op → **698 PASS / 7 FAIL**. Wiring is asserted separately from behaviour — the 0.24.5 lesson that a correct method nobody calls is invisible.

## In-game checklist

1. A machine with **no** `failure-action` (default `still`): start a craft, cut the energy → the bar **freezes**; restore → resumes from there, **no second input charge**.
2. `"failure-action": "decrease"` + `/reload`: same test → the bar **slides back one tick per failed tick**; resumes from the receded position, no re-charge.
3. `"failure-action": "reset"` + `/reload`: the bar **drops to 0**, and — this release's deliberate difference — the craft restarts and **pays its one-shot inputs again**; if the items are gone the machine stays `idle`. **Confirm this feels acceptable.**
4. **Never-failing machine**: all three values → identical product and consumption.
5. **Full output hatch**: the bar must **hold at the last tick**, even under `reset`.
6. **Factory controller**: repeat 1–3 — `reset` must keep the craft armed (bar zeroed, no re-payment).
7. The status text is `idle` with **no red message** naming the shortfall — that is the separate `craftcheck.failure.*` item (M4a), **not** introduced here.

## Not implemented from the original (explicit)

1. **`cancel-recipe-on-pertick-failure`** — a different, recipe-level field (`MachineRecipe.java:227`), whose only lever was the CraftTweaker bridge (D7). Absent before and after.
2. **The global config key** `general.default-failure-actions` — replaced by the constant, so the one-spec/one-`registerConfig` rule holds.
3. **"Reset stays armed at tick 0"** on the plain controller (D17; the factory path is faithful).
4. **The original's silent fallback** for unknown names.
5. **The per-tick failure message** — pre-existing M4a gap, out of scope.

## Verification

`clean build` → `BUILD SUCCESSFUL in 19s` with `> Task :jar` and `> Task :reobfJar`. **Reobfuscation verified on the build product, this archive and the deployed file** (official `CREATIVE_MODE_TAB` 0, SRG `f_279569_` 1). `mods.toml` `version="0.25.0"`; **zero** files under `data/`; language keys **166 each, identical sets** (none added); one real `.registerConfig(`; `StructurePreviewRenderer.java` untouched (mtime still 2026-09-30 01:16:55).

Jar SHA-256 `F9C8E2E502EC1EBCD9931E4D09B6E3FBD18AD96ABCD8DB57654B730C993B4A01`, deployed with a matching hash. **Not opened in game** — the checklist above is the only way to close it.
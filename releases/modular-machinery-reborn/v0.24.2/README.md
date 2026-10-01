# Modular Machinery: Community Edition Reborn

**Release 0.24.2 — the controller screen's info text no longer overflows into the player inventory. The factory screen's reported offset is NOT fixed, and here is why.**

## (A) Machine controller screen — root cause confirmed, fixed structurally

`MachineControllerScreen.drawInfo` was an **unbounded append-only cursor**, copied from the original's `GuiMachineController#drawGuiContainerForegroundLayer` (`:157-172`): every line advanced `y`, and the closing line was drawn at `Math.max(y, FOOTER_Y)`. The original does the same, but its payload was short enough never to reach panel y **119** (the 物品栏 label row, `imageHeight - 94`) or y **131** (the first player slot row — `ContainerController#addPlayerSlots`, `8 + j*18, 131 + i*18`).

0.24.0 added the smart-data-interface rows and the start-failure line. The cursor then crossed both boundaries — exactly what the second screenshot shows.

**The fix is structural, not a pixel nudge.** A new pure-arithmetic `client/ScreenLayout.java` models a **bounded block**: the closing line's height is *reserved first* at the region's end, and the payload may only use the space above that reservation. So **no payload length can push the closing line out**. Both screens now describe their payload once, draw it through one shared row budget (over-long payloads are skipped, not drawn), and clip the block with `enableScissor` using **absolute** screen coordinates — the documented `StructurePreviewRenderer#clip` trap, where the pose stack must deliberately not be consulted.

The closing line now sits at `min(FOOTER_Y, block.footerY)`: the original's 187 while the block is short, the region's floor when it is tall. Before: `max(y, FOOTER_Y)`, unbounded.

## (B) Factory screen — NOT fixed, deliberately

The reported offset **could not be reproduced from the source**, so no coordinate was invented to "fix" it.

The agent re-derived every coordinate from `GuiFactoryController.java:31-42, 86-88, 94-109, 111-139, 189-246` and `ContainerFactoryController.java:36-38, 94-100`, then verified the **compiled bytecode in the deployed jar**: `imageWidth = 280`, `imageHeight = 213`, and the panel blit, the queue (`leftPos+8`), the text (`leftPos + 113*0.72`) and the slots (menu-local 112/131) all add to the **same** `leftPos/topPos`. **There is only one origin**, so in the shipped code the queue cannot be "one queue width to the left of the panel".

A 50 % overlay of `guifactory.png` stretched onto the measured panel rectangle **lands on the black preview area and the slot grid** — i.e. the panel body is drawn at the right place and size, while the queue strip in the screenshot is left-shifted relative to it. Measured scale across the queue's own width ≈ 4.0/px versus ≈ 4.5–5.0/px for the viewport, which does not reconcile.

**No layout constant was changed** — so 0.24.2 draws the factory screen exactly as 0.24.1 did. If the queue still looks shifted left, the cause is outside the source, and the next step needs two data points: a **fresh screenshot** plus `options.txt`'s `guiScale`, and a check for a stale `assets/modular_machinery_reborn/textures/gui/guifactory*.png` in an enabled resource pack.

Re-derived from the original and now **asserted** rather than "tidied": the original's text block **genuinely overlaps** the queue/scrollbar band (`113*0.72 = 81` versus the queue's 94 and the track's 106). That is the original's own geometry.

## The assertions that were missing

Section R passed while asserting the implementation's **own numbers** — which is precisely what let a wrong layout stay green. New harness **section U** (60 PASS / 0 FAIL) asserts properties a wrong implementation **cannot** satisfy:

- every factory element's rectangle lies **inside** `[0,0 280×213]` (a queue floating outside fails this);
- one-origin inequalities, plus `queue.x == 8` and `grid.x == 112` read back from the classes;
- the label and the first slot row are **below** the info block's bottom, with the queue's right edge left of the grid's left edge;
- the blit arguments literally: `{x, y, 0, 0, 280, 213}` and `{0, 0, 86, 32}`;
- block containment for **short, long and deliberately extreme** payloads (controller 9/14/19 rows; factory 5/22/59 rows), each cross-checked by an independent re-walk.

**Fault injection — the checks have now failed on purpose.** The same predicates are fed 0.24.1's geometry and each assertion passes **only when the predicate rejects it**: the queue shifted one width left (`[-78,8 86x197]`) is rejected; the 0.24.1 closing line lands at **478 panel px against the label's 119** — the exact shipped collision — and is rejected; the unbounded factory block runs to 461 panel px against its 102 boundary and is rejected. The corrected layout then passes the same predicates on the same payloads and still draws most rows (4/19, 11/22).

## The seven mandatory checks

1. **SRG reobfuscation** — build product, archived jar and deployed file: official name absent, SRG `f_NNN_` present.
2. **Build order** — the harness did rewrite `build/libs` un-reobfuscated as documented; a `clean build` and a re-check followed before deploying.
3. **One config per type** — exactly one real `.registerConfig(` (`ModConfig.java:200`). No key or config class added.
4. **The harness still compiles** — it failed first (package-private accessors) and was fixed by making the helpers public; 24 sections now run.
5. **Config key paths from the rendered spec** — 13/13 rendered keys pass.
6. **Evidence in `_audit/`** — see the note below.
7. **In-game checklist** — see below.

`mods.toml` 0.24.2; **zero** files under `data/`; language keys **166 each, identical sets**; `StructurePreviewRenderer.java` unmodified (mtime still 01:16:55). Jar SHA-256 `A336D8A8F918BF423AD59E98DD6586F48DF669079E134B27D87BCEE01D61C1DA`, deployed with a matching hash.

## A note on mandatory check 6 — it failed again, and was caught again

The agent reported 601 PASS / 3 FAIL / 604 read back from `_audit/m6c-verify/acceptance-0.24.2-screen-layout.txt`. **That file contains zero PASS lines** — it holds Gradle output, not the harness's stdout. The section-U file is genuine (`section-U-0.24.2-layout.txt`, 60 PASS / 0 FAIL). The full run was rescued from the volatile Gradle daemon log into `_audit/m6c-verify/m6e2-0.24.2-run-rescued.txt`.

This is the **fourth** time a harness claim's supporting output was not where a reader would look. Check 6 exists precisely for this, and it is now clear that "the agent says it read the counts back" is not sufficient — the file must actually contain them.

Of the three FAILs, all are the documented environmental ones: section R's config-migration check requires a **pre-0.24.0** config file, and the instance's has already been migrated (1137 chars, `isCorrect() == true`, 0 corrections). **The user's config was not touched to make them green.**

## In-game checklist — the layout has still not been seen in game

**Machine controller** (the 不朽花冠聚合机 that showed the problem): 物品栏 must be readable on its own row with clear space above it, and `Avg: … μs/t (Search: … ms), WorkMode: SYNC` must sit **above** the 物品栏 row — touching neither it nor the 3×9 grid nor the hotbar. Repeat with a machine that declares a smart interface type (a taller payload).

**Factory controller**: the queue column (6 rows of 86×32 with dividers) must be **inside** the panel, about 8 GUI px from its left border, with the scrollbar immediately to its right, and the preview, info text and player grid all from that same origin. **If it still looks shifted on 0.24.2**, send a fresh screenshot together with `options.txt`'s `guiScale`, since the bytecode confirms 280×213 and a single origin.
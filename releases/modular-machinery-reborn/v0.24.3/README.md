# Modular Machinery: Community Edition Reborn

**Release 0.24.3 — the controller screen's first glyph column is no longer clipped. The factory screen's
reported offset is settled: the shipped layout matches the original's geometry at the real GUI scale, and no
layout constant was changed.**

Owner report, verbatim: 「第一列的文字被挡住了一半」 (the first column of the text is half hidden), with a
controller screenshot at `guiScale: 4` (from the instance's own `options.txt`) and a factory screenshot.

## (A) Machine controller screen — what was actually clipping, and why

`MachineControllerScreen.drawInfo` set up its drawing matrix with **only a scale**:

```java
graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
```

where the factory screen, on the same panel convention, does

```java
graphics.pose().translate(this.leftPos + TEXT_DRAW_OFFSET_X * FONT_SCALE,
        this.topPos + TEXT_DRAW_OFFSET_Y * FONT_SCALE, 0.0F);
graphics.pose().scale(FONT_SCALE, FONT_SCALE, 1.0F);
```

`renderLabels` is called from `AbstractContainerScreen#render` **after** `pose().translate(leftPos, topPos)`,
so coordinates inside it are panel-local. Without the block's own translate, every `drawString(…, TEXT_X, …)`
was therefore multiplied by the scale: the text was painted from panel x `12 * 0.72` = **8**, not 12, and from
panel y `12 * 0.72` = **8**, not 12. The 0.24.2 clip, computed as `leftPos + TEXT_X - 2`, began at panel x
**10**, and the text block's own left edge was at `textRect()[0]` = 8 — so the clip cut **2 panel pixels off
the first glyph column of every row**. That is exactly 「第一列的文字被挡住了一半」.

The 0.24.2 fix's own comment ("absolute screen coordinates") was half right and half wrong, and the wrong half
is the bug: `GuiGraphics#enableScissor` really is in absolute GUI coordinates and really does not consult the
pose stack — verified against the mapped 1.20.1 bytecode (`ScissorStack.push(new ScreenRectangle(left, top,
right-left, bottom-top))` → `applyScissor` → `RenderSystem.enableScissor`, converted by `windowGuiScale` only)
— but the **text was drawn in the space the clip's origin was measured in**, so the two disagreed by exactly
the missing translate.

### Was a section-U predicate wrong?

Partly. The harness asserted the block's modelled rectangle and its row budget, and that part was right — the
footer really is clamped clear of the label row at 119 and of the first slot row at 131. What no predicate
asked was **where the block as drawn lands relative to the clip the screen installs**. The clipped left column
is not a rectangle-containment failure at all: `textRect()` was 8 panel px wide of the clip edge and the clip
at 10 was "outside" it, so every modelled number was self-consistent while the screen was visibly wrong. The
more important finding is therefore that section U was measuring the **model**, not the **drawing**, which is
precisely the trap the project's newest mandatory check names.

### The bottom boundary: was 物品栏 clipped by the panel?

No. The scissor runs to panel y 213 and the block's reserved floor is `availableEnd() * 0.72` = 114 panel px
(`INVENTORY_LABEL_Y - CLEARANCE` = 119 - 4), while `物品栏` is drawn at panel y 119 — 5 px below the block's
floor and 94 px above the scissor's bottom. The assertions and the game agree here; the apparent clipping in
the returned screenshot is the **screenshot's own crop** (the image is 494 px tall and its last row falls on
the label's glyphs, with no dark band below them). Recorded as a non-finding rather than "fixed".

### The fix

One origin, stated once. `ScreenLayout.ControllerPanel` now publishes the block's panel-local rectangles —
`textRect()`, `glyphColumnRect()`, `scissorRect()` — and:

- `MachineControllerScreen.textBlockRect()` is the screen's single description of where its block is drawn;
- `drawInfo` translates the matrix by **that rectangle's own left/top** and then scales, drawing from `(0, 0)`;
- the clip is `scissorRect()` = that rectangle dilated by `SCISSOR_MARGIN_X = 4` on the left, flush with the
  panel elsewhere, so the clip provably contains the block (and the font's 0.72-px shadow);
- `init()` places the smart interface's edit box from the same rectangle;
- `FactoryControllerScreen` derives its clip from `ScreenLayout.FactoryPanel.scissorRect()` (the panel's own
  `panelRect()`) and its translate from its own `textBlockRect()` — same convention, no constant changed.

`ScreenLayout.FactoryPanel.textRect()` was also corrected: it applied `* SCALE` twice, reporting the block at
panel x 58 instead of the 81 the screen actually draws it at.

## (B) Factory screen — verdict: **matches the original**, no defect

`imageWidth = 280`, `imageHeight = 213`, one `leftPos/topPos` origin for the panel blit, the queue, the
scrollbar, the text and the slots — re-confirmed, and now also confirmed against the screenshot.

### Arithmetic at the real `guiScale: 4`, per axis

The screenshot is an attachment resized to 1862x1367, so its pixels-per-GUI-pixel is not 4; each axis was
calibrated from an exact GUI-space ruler in the image. A template match of the **shipped** `guifactory.png`
(the file is byte-identical to the original Community Edition's; 0 differing pixels of 59640) against the
screenshot puts the panel origin at image (534, 293) with **3.675 image px per GUI px** on x. That single
scale then predicts every horizontal feature, and every one of them lands:

| element | panel-local | GUI px | predicted image x | observed |
|---|---|---|---|---|
| panel left edge | 0 | — | 534 | 539 (white border line) |
| queue element | 8 .. 94 | 86 wide | 563 .. 879 (316 wide) | 564 .. 879 (316 wide) |
| scrollbar track | 94 .. 106 | 12 wide | 879 .. 923 (44 wide) | 879 .. 922 (44 wide) |
| grid column 1 | 112 .. 128 | 16 wide | 946 .. 1005 | 945 .. 1002 |
| grid col pitch | +18 | 18 | 66.2 | 65.5 |
| grid right edge | 256+16 | — | 1534 | 1534 |
| text block left | 113*0.72 = 81 | — | 832 | text starts ≈866 |

The queue is **inside the panel** and starts at panel x 8 — 8 GUI px from the panel's own left border, not
one queue width outside it. The 0.24.1 "free-standing strip" geometry (`QUEUE_X - ELEMENT_WIDTH` = -78 panel
px) is not what the screenshot shows.

### What the owner is looking at: the original's own overlap

The original's information block starts at panel x `113 * 0.72` = **81**, while the queue's elements reach
panel x **94** and the scrollbar's track reaches **106**. The text therefore genuinely overlaps the queue band
by 13 panel px (and the scrollbar band by 25) — in the original's own numbers (`GuiFactoryController:39-42`
vs `:191-192`). In the screenshot the 「找到蓝图：无」 line starts at image x ≈866, which is 13 px *before* the
queue's right edge at 879 — the same 13 GUI px, exactly. The row is drawn last, so it is legible; the queue
element behind part of its first glyph is the original's design, not an offset. The left column inside the
panel (with the 物品栏 label on it) is the queue band, whose 8-px inset from the panel border plus its own
divider lines is what reads as "a strip to the left of the panel".

**Verdict: matches the original. No layout constant was changed.**

## The assertions that were missing, and that can now fail

Section U gained 24 checks (60 → **84 PASS / 0 FAIL**), all of the form "a drawn thing lies in the rectangle it
belongs to":

- **the block as drawn lies inside the clip the screen installs** — controller `[8,8 168x106]` inside
  `[4,0 176x213]`, factory `[81,8 199x94]` inside `[0,0 280x213]`;
- **the clip cannot reach the first glyph column**, shadow included (clip left 4 < glyph left 8);
- **the clip is the block's rectangle dilated by `SCISSOR_MARGIN_X`**, i.e. derived, not re-typed;
- **both screens' block origins equal their layout's `textRect()`**, so a second scaling fails;
- **the pose translate really happens in the bytecode**: the harness loads
  `MachineControllerScreen.class` / `FactoryControllerScreen.class`, walks `drawInfo` /
  `drawFactoryStatus` with ASM, and requires a `PoseStack.translate` before the first `drawString` whose two
  floats were **read out of `textBlockRect()[0]` and `[1]`**, followed by the scale. Rectangles alone cannot
  see a missing translate — this can.

### Fault injection — the checks have now failed on purpose

The 0.24.2 defect was put back into the shipped source (the `pose().translate(text[0], text[1], 0.0F)` line
removed) and the harness re-run: **622 PASS / 6 FAIL**, the three new failures being

```
FAIL  MachineControllerScreen.drawInfo: the pose is translated before anything is drawn,
      to the block's own origin from textBlockRect (0 translate(s) seen)
FAIL  MachineControllerScreen.drawInfo: ...and those two floats are read from textBlockRect()[0]
      and [1] (arrays read: [scissorRect, scissorRect, scissorRect, scissorRect] at [0,1,2,3]),
      not written into the call by hand
FAIL  MachineControllerScreen.drawInfo: the scale comes after that translate, and before any
      string is drawn (translate at -1, scale at 62)
```

The source was restored, the harness re-run at 625/3, and the release built from that state. The full stdout
is kept at `_audit/m6c-verify/fault-injection-0.24.3-missing-translate.txt`.

The clip predicate is also fault-injected in the same section: with the 0.24.2 clip edge at panel x 10 and the
block at 8, the very predicate the corrected layout passes is rejected, and the 0.24.1 queue rectangle
`[-78,8 86x197]` is rejected by the panel-containment predicate the corrected queue `[8,8 86x197]` passes.

## The eight mandatory checks

1. **SRG reobfuscation** — build product and deployed file both: SRG `f_<n>_` refs = 1, official
   `CREATIVE_MODE_TAB` refs = 0 (`javap` on `com.reborn.modularmachinery.block.ModBlocks`).
2. **Build order** — harness → `clean build` → reobf check → deploy. The harness did rewrite `build/libs`
   un-reobfuscated again; the `clean build` and re-check followed before deploying.
3. **One config per type** — exactly one real `.registerConfig(` (`ModConfig.java:200`). No key, class or
   config added.
4. **The harness still compiles** — it was extended (ASM bytecode scan of the two screens) and compiled and
   ran; 24 sections.
5. **Config key paths read from the rendered spec** — 13/13 rendered keys pass (section R).
6. **Evidence in `_audit/`** — the counts below were read back from the files' actual contents, not from the
   volatile daemon log (see the note).
7. **In-game checklist** — below.
8. **Assertions can fail** — the fault injection above.

`mods.toml` 0.24.3; **zero** files under `data/`; language keys **166 each, identical sets**;
`StructurePreviewRenderer.java` unmodified (mtime still 2026/9/30 01:16:55). Jar SHA-256
`C7B52AFC65850ED6EF6B1C9454138C9F4EA84470B421EAC352AE0F5EDCE5CCC2`, deployed with a matching hash.

### Evidence, with counts read back from the files

| file | contents | PASS | FAIL |
|---|---|---|---|
| `_audit/m6c-verify/acceptance-0.24.3-screen-layout.txt` | full Gradle+harness stdout | 625 | 3 |
| `_audit/m6c-verify/harness-stdout-0.24.3.txt` | harness stdout only, sections A–U | 625 | 3 |
| `_audit/m6c-verify/section-U-0.24.3-layout.txt` | section U | 84 | 0 |
| `_audit/m6c-verify/fault-injection-0.24.3-missing-translate.txt` | fault-injected run | 622 | 6 |
| `_audit/m6c-verify/release-build-0.24.3.txt` | `clean build` | — | — |
| `_audit/m6c-verify/reobf-0.24.3-ModBlocks.txt` | build product javap | — | — |
| `_audit/m6c-verify/reobf-0.24.3-deployed.txt` | deployed javap | — | — |
| `_audit/m6c-verify/deploy-0.24.3.txt` | deploy + hashes | — | — |

The 3 FAILs are the documented environmental ones: section R's config-migration check requires a **pre-0.24.0**
config file and the instance's was already migrated (1137 chars, `isCorrect() == true`, 0 corrections).
**The user's config was not touched to make them green.**

## In-game checklist (minimal)

1. **Machine controller** (the 不朽花冠聚合机 that showed the problem): the first character of every info row —
   `找到结构：`, `状态：`, `并行数：`, `最大并行数：` — must be **whole**, with the leftmost stroke of 「找」
   and 「状」 fully visible rather than sliced. Compare the left edge of the info text against the panel's
   left border: there should be a clear gap.
2. **Same screen, bottom**: `物品栏` on its own row, with `Avg: … μs/t (Search: … ms), WorkMode: SYNC` above it
   and not touching it.
3. **A machine with a smart data interface type** (taller payload): the `智能数据接口 …：…` row and the edit
   box must still line up with the text column, and the closing `Avg:` line must stay above `物品栏`.
4. **Factory controller**: the queue column (6 rows of 86×32 with dividers) must be **inside** the panel,
   about 8 GUI px from its left border, with the scrollbar immediately to its right. The info text
   (`找到蓝图：` / `找到结构：`) starting on top of the queue's right edge is the original's own geometry.

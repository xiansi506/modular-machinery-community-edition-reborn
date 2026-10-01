# Modular Machinery: Community Edition Reborn

**Release 0.24.5 — the factory screen never drew its information block at all. The 「找到蓝图：」 / 「找到结构：」
block was being painted off the panel and clipped away; it is now drawn from the foreground pass, whose pose
already carries the panel origin. Still no 「物品栏」 label and still no title.**

Owner report, verbatim, after testing 0.24.4: 「gui没啥问题了，但是没有文字，找到蓝图那两行」 — *the GUI is fine
now, but there is no text: the 「找到蓝图」 two lines are missing.*

## (A) Where those two lines went: **moved, not deleted** — and not by 0.24.4

Read out of the four release jars with `javap -p -c` (`FactoryControllerScreen.drawFactoryStatus` and its caller):

| version | who calls `drawFactoryStatus` | the pose translate it uses | result on screen |
|---|---|---|---|
| 0.24.0 / 0.24.1 | `renderBg` | `leftPos + 81.36F, topPos + 8.64F` | on the panel — **visible** |
| 0.24.2 | `renderBg` | `leftPos + 81.36F, topPos + 8.64F` | on the panel — **visible** |
| 0.24.3 | `renderBg` | `textBlockRect()[0], textBlockRect()[1]` = `81, 8` | **off the panel — invisible** |
| 0.24.4 | `renderBg` | unchanged from 0.24.3 | **off the panel — invisible** |

0.24.3 introduced `textBlockRect()` so section U's pose check could read the block's own rectangle instead of a
copy of it — and **dropped the `+ leftPos` / `+ topPos` in the same edit**. The call site never moved: it has been
in `renderBg` since 0.24.0. 0.24.4 emptied `renderLabels` (to remove the inherited title/「物品栏」 row) and left
the block exactly where 0.24.3 had put it, which is why the owner's 0.24.4 test is where the missing text became
the visible complaint.

### Why that hides the whole block

`renderBg` runs **before** the pose is translated to the panel: `AbstractContainerScreen#render` calls
`renderBg` at bytecode offset **18**, pushes the pose and translates it by `(leftPos, topPos)` at offsets
**53–71**, and only then calls `renderLabels` at offset **205** (read out of the mapped 1.20.1 class by section V,
which now asserts this ordering from the vanilla bytecode itself). A panel-local origin is therefore wrong in
`renderBg` and right in `renderLabels`.

`drawFactoryStatus` then clips itself to the panel —
`enableScissor(leftPos + 0, topPos + 0, leftPos + 280, topPos + 213)` — and `GuiGraphics#enableScissor` takes
**absolute GUI coordinates and never consults the pose stack** (verified from its own bytecode; section U has
relied on that since 0.24.3). So the block, painted at absolute `(81, 8)`, fell entirely outside its own clip and
was discarded: the panel, the slot frames, the queue and the scrollbar all drew normally, and not one glyph of
the information block appeared. With `leftPos` in the hundreds on a normal window, nothing of it was even near
the panel.

The queue's own row text is unaffected and always was: `drawRecipeStatus` (`:353-355`) adds `leftPos`/`topPos`
itself. The information block was the one text in this screen that did not.

## (B) The fix

`renderBg` no longer draws the block; `renderLabels` does, and nothing else:

```java
@Override
protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    drawFactoryStatus(graphics);            // no super call: no title, no 「物品栏」
}
```

The block keeps translating by the **panel-local** `textBlockRect()` and keeps its clip at
`leftPos/topPos + panel-local`, both unchanged — because in the foreground pass the panel origin is *inherited*
rather than re-typed. That is the one-origin rule this screen has been trying to hold since 0.24.1: the block's
position now comes from the same rectangle (`textBlockRect()` == `FactoryPanel.textRect()`) that section U checks
the clip against, so it cannot drift again — and it cannot be drawn in a pose that lacks the origin, because the
method that draws it is only reachable from the pass that has it.

This is also the original's own structure: `GuiFactoryController.java:76-80` overrides
`drawGuiContainerForegroundLayer` — the foreground layer — and draws the status block from there.

## (C) The assertions: the 0.24.4 form could not tell "correct" from "absent"

0.24.4's section V asserted that `FactoryControllerScreen.renderLabels` contained **no invocation and no field
read**. That encoded the bug: an **empty** method satisfies "no invocation" exactly as well as a correct one, so
when the block stopped being reachable the check stayed green. This is the third occurrence in this project of an
assertion that cannot distinguish *correct* from *absent* — after section R asserting the implementation's own
numbers while the screen was visibly wrong, and the 0.24.1 sentinel index where "no failure" was never asserted.

Section V's replacement half is **positive first** (13 new checks):

1. **Reachability** — `renderLabels` must **invoke** `drawFactoryStatus` (**1 call**), and `renderBg` must not
   (**0 calls**). Deleting the call fails, which the old predicate could not see;
2. **Content** — that method must make **≥10** `GuiGraphics#drawString` calls (it has ten draw sites) and must
   load `gui.modular_machinery_reborn.controller.blueprint` and `.structure` (11 string constants in the method);
3. **The words on screen** — those two keys must be `找到蓝图：%s` / `找到结构：%s` in `zh_cn.json` and
   `Found blueprint: %s` / `Found structure: %s` in `en_us.json` (the Chinese literals are written as `\u`
   escapes so the check does not depend on the encoding the harness is compiled with);
4. **One origin, from an independent source** — the vanilla `AbstractContainerScreen#render` bytecode must show
   `renderBg` called **before** the `(leftPos, topPos)` translate and `renderLabels` **after** (in the built
   class: `renderBg` at instruction 17, the translate at 55, `renderLabels` at 150), and `leftPos`/`topPos` must
   be read before that translate. The block's origin must equal `FactoryPanel.textRect()` and lie inside the
   panel and the clip;
5. **The prohibitions that survive** — the screen never invokes `AbstractContainerScreen#renderLabels` (the one
   method that draws the label and the title: read out of vanilla, it makes exactly two `drawString` calls reading
   `title`/`titleLabelX`/`titleLabelY` and `playerInventoryTitle`/`inventoryLabelX`/`inventoryLabelY`), reads
   **none** of those six fields anywhere, and contains no `container.inventory` literal.

Section V now reports **35 PASS / 0 FAIL** (22 of 0.24.4's slot-frame checks + 13 new). The blit-overload and
slot-hole halves are unchanged and still green.

### Fault injection in both directions — done against the shipped source

| injection | result | the new failures |
|---|---|---|
| the block's drawing call deleted (0.24.4's shape: an empty `renderLabels`) | **659 PASS / 4 FAIL** | `FactoryControllerScreen.renderLabels invokes drawFactoryStatus (0 call): the information block is reached from the render path at all` |
| the vanilla `playerInventoryTitle` draw put back into `renderLabels` | **658 PASS / 5 FAIL** | `FactoryControllerScreen.renderLabels reads no field at all (4 field reads over 2 invocation(s)) …`; `…and it reads none of playerInventoryTitle/…/titleLabelY anywhere in the class (3 reads)` |

The source was restored after each run (SHA-256 verified, no `FAULT INJECTION` marker left) and the acceptance
run repeated at **660 PASS / 3 FAIL** before the release build. Full stdout:
`_audit/m6c-verify/fault-injection-0.24.5-info-block-deleted.txt`,
`_audit/m6c-verify/fault-injection-0.24.5-inventory-label-restored.txt`.

## (D) The plain controller screen is **not** affected — and was not touched

`MachineControllerScreen` draws its block from `renderLabels` as well, and that is correct there for the same
reason; its 「物品栏」 label is correct to keep, because the original's `GuiMachineController:49-50` **does** call
`super.drawGuiContainerForegroundLayer(...)`. It is the harness's live counter-example: section V's label
predicate must **catch** `MachineControllerScreen.renderLabels` (it does — 2 invocations, 4 field reads), so the
ban on the factory screen is not vacuous. `MachineControllerScreen.java` was **not modified** by this release,
and neither was `ScreenLayout.java`.

## The eight mandatory checks

1. **SRG reobfuscation** — build product **and** deployed file (`javap` on
   `com.reborn.modularmachinery.block.ModBlocks`, 1236 lines each): official `CREATIVE_MODE_TAB` = **0**, SRG
   `f_<n>_` = **1**.
2. **Build order** — harness → `clean build` → reobf check → deploy, not reversed.
3. **One config per type** — exactly **one** real `.registerConfig(` call (`ModConfig.java:200`) and exactly one
   `new ForgeConfigSpec.Builder(`. This release adds no key.
4. **The harness still compiles** — it gained the five new ASM helpers and the vanilla-class scans, compiled and
   ran; **25 sections**. `_audit/m6c-verify/mmverify/ParallelCraftCheck.java` and the build copy
   `.tmp-m6c-verify/mmverify/ParallelCraftCheck.java` are hash-identical
   (`84FD0B17DF6C94B64DE6A235200C55C5641A580ACFD28E991189D6AF6143B397`).
5. **Config key paths read from the rendered spec** — 13/13 rendered keys pass (section R); the spec is untouched.
6. **Evidence in `_audit/`** — table below; every count was read back out of the file named, on disk.
7. **In-game checklist** — below.
8. **Assertions can fail** — the two source-level injections above, one per direction.

`mods.toml` **0.24.5**; **zero** files under `data/` (source tree and jar); language keys **166 in `zh_cn.json`
and 166 in `en_us.json`, set difference 0**; `client/preview/StructurePreviewRenderer.java` unmodified (mtime
still `2026-09-30 01:16:55`); the JEI files untouched; no `.md` moved or renamed. Only one source file changed:
`client/FactoryControllerScreen.java`.

### Evidence, with the counts read back out of the files

| file | contents | PASS | FAIL | lines |
|---|---|---|---|---|
| `_audit/m6c-verify/acceptance-0.24.5-information-block.txt` | full Gradle + harness stdout | **660** | **3** | 779 |
| `_audit/m6c-verify/harness-stdout-0.24.5.txt` | harness stdout only (sections A–V) | **660** | **3** | 733 |
| `_audit/m6c-verify/section-V-0.24.5.txt` | section V alone | **35** | **0** | 46 |
| `_audit/m6c-verify/fault-injection-0.24.5-info-block-deleted.txt` | injected: the drawing call deleted | **659** | **4** | 774 |
| `_audit/m6c-verify/fault-injection-0.24.5-inventory-label-restored.txt` | injected: the label put back | **658** | **5** | 779 |
| `_audit/m6c-verify/release-build-0.24.5.txt` | `clean build` | — | — | 46 |
| `_audit/m6c-verify/reobf-0.24.5-ModBlocks.txt` | build-product `javap` | — | — | 1236 |
| `_audit/m6c-verify/reobf-0.24.5-deployed.txt` | deployed `javap` | — | — | 1236 |
| `_audit/m6c-verify/deploy-0.24.5.txt` | deploy + hashes | — | — | 21 |

The 3 FAILs are the documented environmental ones: section R's config-migration check needs a **pre-0.24.0**
config file, and the instance's was already migrated by the owner's own game launch (1137 chars,
`isCorrect() == true`, 0 corrections). **The user's config was not touched to make them green.**

## Release data

- `clean build` → `BUILD SUCCESSFUL in 34s`, with `> Task :jar` and `> Task :reobfJar`.
- Jar **567162 bytes**, SHA-256
  `B1F09CE0222EC7F270A270FBE617E7D157C9608E25651B34DF7A2E1256219CB4`, deployed to
  `D:\.minecraft\versions\彩虹花园\mods\modular_machinery_reborn-0.24.5.jar` — build product, deployed file and
  `releases/modular-machinery-reborn/v0.24.5/` all **byte-identical** (the 0.24.4 jar was removed from the mods
  directory first, so exactly one version is installed).
- Shipped source hash (the file the jar was built from):
  `F03F0874D846D2263CC5B5999F4DD323D2CEAF6856E456E5B66D21A8121FB5D1`.

## In-game checklist (minimal) — built and hash-verified, **not yet opened in game**

Open a formed machine's **factory controller** (the same screen as the owner's screenshots):

1. **The information block must be there, top right of the panel**, in the original's own order and at the
   original's own coordinates: the black preview box's right half, starting at panel `(81, 8)`:
   - `找到蓝图：<machine>` (or `找到蓝图：无` when the blueprint slot is empty and the structure is not formed) —
     `<machine>` is the machine's localised name;
   - `找到结构：<machine>` — or `找到结构：无` before the structure is formed;
   - then, once formed: `状态：`, the status word (`闲置` / `工作中…` / `机器因红石信号停止工作` …), the
     `N 线程运行中 / M 最大线程数` row, the `并行数：` / `最大并行数：` pair, and the closing
     `Avg: …μs/t (Search: …ms), WorkMode: …` line.
2. **Nothing is drawn over the black preview box's left half and nothing over the recipe queue**: the block's
   first column must start at the box's right half, not at the panel's left edge.
3. **No 「物品栏」 text anywhere on this panel**, and **no title** above the panel (the original draws neither).
4. **The queue rows keep their own text** (thread name / `线程 #n`, `闲置`/`工作中…`, the percentage row) — they
   were already correct and must be unchanged.
5. **Regression, plain controller** — open a machine's plain controller: it must **still** show 「物品栏」 plus its
   full info block with 「找到蓝图：」/「找到结构：」 (`GuiMachineController:49-50` calls `super`), and its text must
   still be inside the panel with the first column whole.
6. **Regression, factory panel** — items still centred in their slot frames, the hotbar row of frames still
   present, the right border still a single border (0.24.4's fix).

**Built and hash-verified; not yet opened in game — the user's confirmation is what closes this.**

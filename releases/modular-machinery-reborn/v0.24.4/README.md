# Modular Machinery: Community Edition Reborn

**Release 0.24.4 — the factory screen's panel was being drawn stretched, which is why the items sat outside
their slot frames. The panel blit now declares the sheet's real size, the 「物品栏」 label the original never
draws is gone, and the harness asserts the property the owner can see.**

Owner report, verbatim: 「第一章是原版的gui，第二张是移植后的，下面物品栏的物品的错位了」 — with the original
Community Edition's factory screen and this port's factory screen side by side.

0.24.2 recorded the factory offset as 「源码无法复现」 and 0.24.3 re-checked it against the texture and concluded
「无错位」. **Both verdicts were wrong, and 0.24.3's method was the reason**: it calibrated the screenshot's
pixels-per-GUI-pixel from `guifactory.png` itself (3.675 image px per GUI px). The texture was the very thing
being drawn wrong, so the error cancelled out of every measurement taken through it. The independent ruler is
the **items**, which are drawn from the menu's `Slot` coordinates and never touch the texture.

## (A) The root cause: `GuiGraphics.blit`'s 256×256 default

`FactoryControllerScreen.renderBg` (0.24.3) drew the panel with

```java
graphics.blit(TEXTURE, blit[0], blit[1], blit[2], blit[3], blit[4], blit[5]);
```

`GuiGraphics` has two blit families, and only one of them can be told how big the sheet is:

| call | texture size it uses |
|---|---|
| `blit(ResourceLocation, int x, int y, int u, int v, int w, int h)` | **hard-coded `256, 256`** (1.20.1 delegates to the 9-argument overload with those two constants — read out of the mapped bytecode) |
| `blit(ResourceLocation, int x, int y, int blitOffset, float u, float v, int w, int h, int texW, int texH)` | the caller's `texW, texH` |

`guifactory.png` is **280×213**. With a declared size of 256×256 the quad's texture coordinates ran
`u ∈ [0, 280/256]` and `v ∈ [0, 213/256]`, so the panel was

- **stretched by 280/256 = 1.09375×** across the panel in x — the last 26.25 texels wrapped around under
  `GL_REPEAT`, which is the second border column visible at the panel's right edge in the owner's screenshot,
  and which a pot-resized texture could not produce;
- **squeezed by 213/256 = 0.832×** in y — only texels 0…177 were ever sampled, so the whole hotbar row of slot
  holes (texels 189…204) *was never drawn at all*.

The slots did not move: they are at the original's own `ContainerFactoryController:94,98` coordinates
(`x = 112 + col*18`, `y = 131/149/167`, hotbar `189`, blueprint `(255, 8)`). So the frames drifted away from the
items by an amount that grows across the panel — 10.3 panel px for the first column, 22.6 panel px for the last,
and 28–40 panel px vertically.

### Measured, from the owner's own screenshot

Two independent rulers, both read out of the image:

| what | measured pitch (image px) | what it should be at `guiScale: 4` |
|---|---|---|
| **slot frames** (hole interiors in the panel) | 65.5–66 | 72 → i.e. `4 × 256/280 = 3.657` per GUI px |
| **items** (their centres, e.g. 492, 564, 636, 708, 780, 852, 920) | **72.0** | 72 = 4 image px per GUI px |

The item centres hit `492 + 72c` exactly, which is `leftPos + 4*(112 + 18c + 8)` with `leftPos = 3`: the items
are where `guiScale 4` puts them. The frames are 9 % narrower, and the gap grows to 90 image px at the last
column. That is the owner's 「错位」, in numbers.

### The original never had it

`GuiFactoryController:88` used
`Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, xSize, ySize, xSize, ySize)` — passing **280, 213 as the
texture size**, i.e. a 1:1 blit. Nothing in this port's other screens is affected: `guifactory.png` is the only
GUI sheet in the mod that is not 256×256 (every other `blit` call site's sheet is exactly 256×256, so the
hard-coded default is accidentally correct there — verified, and the harness now checks that the 256-default
form is what `MachineControllerScreen` uses, so the detector is not vacuously green).

### The fix

```java
public static void blitPanel(GuiGraphics graphics, int leftPos, int topPos, int imageWidth, int imageHeight) {
    int[] blit = panelBlitFull(leftPos, topPos, imageWidth, imageHeight);
    int[] texels = panelTextureSize();          // 280 x 213 — the PNG's own IHDR
    graphics.blit(TEXTURE, blit[0], blit[1], 0, blit[2], blit[3], blit[4], blit[5], texels[0], texels[1]);
}
```

`renderBg` calls it, so the screen has exactly one blit of the sheet. With the declared size equal to the
sheet's, the mapping texel → panel pixel is the identity and every slot centre lands on a hole centre.

## (B) The 「物品栏」 label: the original draws **none** on this screen

`GuiFactoryController.java:76-80`:

```java
@Override
protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    drawRecipeQueue();
    drawFactoryStatus();
}
```

It **overrides** the 1.12.2 superclass method and **never calls `super`** — and that superclass method is the
only place vanilla 1.12.2 draws the player-inventory label
(`GuiContainer#drawGuiContainerForegroundLayer` → `I18n.format("container.inventory")` at `(8, ySize - 96 + 2)`).
`GuiContainerBase` (63 lines) does not put it back. The original's screenshot agrees: no label anywhere on the
panel.

The plain controller is the opposite case and was already correct here: `GuiMachineController:49-50` **does**
call `super.drawGuiContainerForegroundLayer(...)`, so its 「物品栏」 stays.

0.24.3 inherited `AbstractContainerScreen#renderLabels`, which painted 「物品栏」 at panel `(8, 119)` — the far
left of a 280-wide panel, on top of the recipe queue, exactly as in the screenshot. The override is now empty
(no title either, which the original also never drew).

## The assertions this release adds — the relationship, not the numbers

Section **V** of the offline harness (**22 PASS / 0 FAIL**) compares two *independent* sources about the same
pixels:

- **the texture's own pixels**: `guifactory.png` is decoded with `ImageIO` and its slot holes are **derived**,
  not re-typed — a 16×16 `8B8B8B` interior with a `373737` inset shadow along its top and left and a white
  highlight along its bottom and right. It finds **37** holes, at `(255,8)` and
  `112/130/…/256 × 131/149/167/189` — i.e. exactly the container's own coordinates;
- **the menu's own slot table**: `FactoryControllerMenu.playerSlotRects()`, which is the array the constructor
  now registers its `Slot`s from, so the two cannot drift;
- related by **the mapping the blit really performs**, computed from the screen's own `panelBlitFull(...)` and
  the sheet's real size: `dest = origin + texel × declared / actual − offset`.

Checks: all 36 player slots and the blueprint slot are centred in a hole (**36 of 36**, **1 of 1**), the
coincidences are a **bijection** (36 slots → 36 distinct holes), no orphan holes exist, the screen declares the
sheet's real size, and the mapping is the identity. Then, read out of the bytecode with ASM: `renderLabels`
contains **no call and no field read at all**; `renderBg → blitPanel → {panelBlitFull, panelTextureSize}` and
`blitPanel` calls the size-taking `blit` overload and **never** the 256-default one.

### Fault injection — the assertions have been red

| injection (into the shipped source) | result | the new failures |
|---|---|---|
| `blitPanel` reverted to the 6-argument `blit` (0.24.3's exact call) | **646 PASS / 4 FAIL** | `blitPanel calls the overload that takes the sheet's size (0 call) and never the 256x256 default (1 calls)` |
| `TEXTURE_WIDTH/HEIGHT` answered as 256 (the declared-size defect) | **641 PASS / 9 FAIL** | `the screen declares the sheet's real size … declared 256x256 against the file's 280x213`; `the mapping texel -> panel pixel is the identity: texel (280, 213) lands on (256, 256)`; **`ALL 36 player slots are centred in a hole the sheet really draws (0 of 36 coincide exactly)`**; the blueprint slot's (0 of 1); the bijection; the blit's source-region check |

The source was restored after each run (hash-verified, no `FAULT INJECTION` marker left) and the acceptance run
repeated at 647/3 before the release build. Full stdout:
`_audit/m6c-verify/fault-injection-0.24.4-blit-256.txt`, `_audit/m6c-verify/fault-injection-0.24.4-declared-256.txt`.

The section also fault-injects in-run: slots moved by **one panel pixel** (0 of 36 coincide), by **one whole slot
pitch** (32 of 36 — a whole-pitch shift lands most slots on a *neighbour's* hole, which is exactly why the
predicate demands all 36 and a bijection), and the 256 blit (0 of 36, worst case reported as
`inventory row 0 col 0 at (112,131) is 28.061 panel px off its frame`).

## Other layout differences visible in this pair

None found beyond (A) and (B). The panel's own geometry — queue at panel `(8,8)`, elements 86×32 stepping 33,
scrollbar at `(94,8)` height 197, text block from `113×0.72 = 81`, blueprint slot `(255,8)`, player grid
`(112,131)…(272,205)` — matches the original's own code and the original's screenshot, and no layout constant
was changed except the removal of a label the original does not draw.

## The eight mandatory checks

1. **SRG reobfuscation** — build product **and** deployed file: official `CREATIVE_MODE_TAB` refs = **0**, SRG
   `f_<n>_` refs = **1** (`javap` on `com.reborn.modularmachinery.block.ModBlocks`).
2. **Build order** — harness → `clean build` → reobf check → deploy, not reversed. The harness did rewrite
   `build/libs` un-reobfuscated again; the `clean build` and the re-check followed before deploying.
3. **One config per type** — 6 textual mentions of `registerConfig` in `src`, exactly **one** real call
   (`ModConfig.java:200`), and exactly one `new ForgeConfigSpec.Builder(`. This release adds no key.
4. **The harness still compiles** — it gained section V (`ImageIO` pixel derivation + ASM scans) and compiled and
   ran; **25 sections**.
5. **Config key paths read from the rendered spec** — 13/13 rendered keys pass (section R).
6. **Evidence in `_audit/`** — every count below was read back out of the file named, on disk.
7. **In-game checklist** — below.
8. **Assertions can fail** — the two source-level injections above.

`mods.toml` **0.24.4**; **zero** files under `data/` (source tree and jar); language keys **166 in `zh_cn.json`
and 166 in `en_us.json`, set difference 0**; `StructurePreviewRenderer.java` unmodified (mtime still
`2026-09-30 01:16:55`); the four confirmed JEI files untouched; no `.md` moved or renamed.

### Evidence, with the counts read back out of the files

| file | contents | PASS | FAIL | lines |
|---|---|---|---|---|
| `_audit/m6c-verify/acceptance-0.24.4-screen-alignment.txt` | full Gradle + harness stdout | 647 | 3 | 765 |
| `_audit/m6c-verify/harness-stdout-0.24.4.txt` | harness stdout only (sections A–V) | 647 | 3 | 718 |
| `_audit/m6c-verify/section-V-0.24.4.txt` | section V alone | 22 | 0 | 31 |
| `_audit/m6c-verify/fault-injection-0.24.4-blit-256.txt` | injected 256-default blit | 646 | 4 | 765 |
| `_audit/m6c-verify/fault-injection-0.24.4-declared-256.txt` | injected declared size 256 | 641 | 9 | 765 |
| `_audit/m6c-verify/release-build-0.24.4.txt` | `clean build` | — | — | 47 |
| `_audit/m6c-verify/reobf-0.24.4-ModBlocks.txt` | build-product `javap` | — | — | 1243 |
| `_audit/m6c-verify/reobf-0.24.4-deployed.txt` | deployed `javap` | — | — | 1243 |
| `_audit/m6c-verify/deploy-0.24.4.txt` | deploy + hashes | — | — | 21 |

The 3 FAILs are the documented environmental ones: section R's config-migration check needs a **pre-0.24.0**
config file and the instance's was already migrated (1137 chars, `isCorrect() == true`, 0 corrections). **The
user's config was not touched to make them green.**

## Release data

`clean build` `BUILD SUCCESSFUL in 20s`, with `> Task :jar` and `> Task :reobfJar`. Jar
**567143 bytes**, SHA-256 `98FE64A15394F3B77322395F45809489A8679E02CD1774581B538558B65A714B`, deployed to
`D:\.minecraft\versions\彩虹花园\mods\modular_machinery_reborn-0.24.4.jar` with a **matching hash** (the 0.24.3
jar was removed from that directory first).

## In-game checklist (minimal)

1. **Factory controller** — open a formed machine's factory controller (the one in the owner's screenshot).
   Every item in the player's inventory must sit **centred inside its frame**, exactly as in the original's
   screenshot: no item straddling a frame border, and the **bottom row of frames (the hotbar) must exist** —
   0.24.3 drew no frames there at all and its items floated over the black blueprint area.
2. **Same screen, right edge** — the panel's right border must be a single border. 0.24.3 showed a **second**
   border column inside it (the wrapped-around texture), with `C6C6C6` continuing past the panel's `555555`
   edge to the left of it.
3. **Same screen, left of the player grid** — there must be **no 「物品栏」 text** anywhere on the panel (the
   original has none). Over the black blueprint box, the info text (`找到蓝图：` / `找到结构：`) must be where it
   was, unchanged.
4. **Click a player slot** — pick an item up and put it back: the click must land on the frame the item is
   drawn in (before this release items and hitboxes drifted apart by up to 22 panel px, so the wrong slot
   responded).
5. **Regression** — the plain controller's screen must **still** show 「物品栏」 (the original does there), and
   its first text column must stay whole (the 0.24.3 fix).

**Built and hash-verified; not yet opened in game — the user's confirmation is what closes this.**

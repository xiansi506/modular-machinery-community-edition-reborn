# Modular Machinery: Community Edition Reborn

**Release 0.17.1 — fix: the structure preview's buttons never fired, because JEI shifts input coordinates by the handler's own area origin.**

## Root cause

`RecipeLayoutInputHandler.handleInput` subtracts **the handler's own `getArea()` origin** before delegating:

```
  0: recipeLayout.isMouseOver(mouseX, mouseY)     // area tested in SCREEN space
 14: Rect2i rect = recipeLayout.getRect()
 23: localX = mouseX - rect.getX()                // recipe-local
 85: ScreenRectangle area = handler.getArea()
 91: MathUtil.contains(area, localX, localY)      // area tested in recipe-local space
104: x = localX - area.getPosition().getX()       // <- the area ORIGIN is subtracted too
115: y = localY - area.getPosition().getY()
134: handler.handleInput(x, y, input)
```

`mezz.jei.library.gui.OffsetJeiInputHandler` performs the same shape twice, confirming the convention: **input coordinates are relative to the handler's own `getArea()`**, while `IRecipeCategory.draw` and `getTooltip` receive plain recipe-local coordinates (`RecipeLayout.drawRecipe` translates the pose by the area and subtracts the area from the mouse pair).

Our `getArea()` returned `new ScreenRectangle(PREVIEW_X, PREVIEW_Y, 172, 150)` — origin **(6, 26)** — so the panel was handed every mouse position shifted by **−(6, 26)**. The whole button strip therefore sat 26 pixels above where it was clicked: no button was ever armed, the shifted press still fell inside the 26..176 viewport band so `mousePressed` fell through to the viewport claim and returned a misleading `true`, and the release then found nothing armed and returned `false`.

The 24-line diagnostic log fits this exactly once (6, 26) is added back:

| Logged | + (6,26) = panel space | Element |
|---|---|---|
| (123.75, 141.3668) | (129.75, 167.3668) | machine-info [120,133)×[161,174) |
| (131.75, …) | (137.75, …) | cycle blocks |
| (152.25, …) | (158.25, …) | reset center |
| (164.25, 140.8661) | (170.25, 166.8661) | layer toggle [165,178) |

The last row also proves the x offset: 164.25 would otherwise land in the 2-pixel gap between buttons, an implausible aim. **All twelve presses hit their intended button** — the user's aim was correct; the strip had simply moved out of range. Hover tooltips worked all along because `getTooltip` is in panel space; only the input space was shifted.

An earlier inference of "21px = PREVIEW_Y − TITLE_Y" was a red herring; the real offset is 26.

## Fix

- `getArea()` now returns a static `AREA = new ScreenRectangle(0, 0, PANEL_WIDTH, PANEL_HEIGHT)`, so one origin — the panel origin — serves `handleInput`, `handleMouseDragged`, `handleMouseScrolled`, `draw` and `getTooltip`. The bytecode above is quoted in the comment, and the javadoc that wrongly asserted the two spaces were identical is corrected.
- The diagnostic is unconditional and now **names the element**: `[preview] JEI simulate: mouse=(129.75, 167.37) button=0 -> button machine-info | panel space, handler area = whole panel 184x220 at (0, 0); buttonStrip y=[161, 174) viewport y=[26, 176)`. `PreviewPanel` returns a `PointerClaim` (`NONE` / `BUTTON` / `LAYER_ARROW` / `LAYER_TRACK` / `VIEWPORT`) instead of a bare boolean, and the viewport claim is an explicit named branch rather than a fall-through. `PreviewButton` gained stable names.
- Behaviour is otherwise unchanged; `PreviewLayout` numbers untouched; no language keys added (still **127 each**).

## Verification

Independently re-checked after the build: `mods.toml` 0.17.1; **zero** files under `data/`; language files **127 keys each with identical sets**; `getArea()` confirmed as the whole panel; renderer invariants intact (`viewport.m11` positive, no negative Y scale, `Lighting.setupLevel` retained). Build `BUILD SUCCESSFUL in 19s`. Jar SHA-256 `7ADB9CECC19C7DF102C8E03C3F9FCEDD5EDC22000EFEF427CEC43464C31391B9`, deployed with a matching hash.

**Not seen in game.** What to look for: clicking the strip must now report `y` within 161..174 and name a button, and the action must fire. Drag and scroll received the same shifted coordinates and are fixed by the same change, though neither had been exercised before.
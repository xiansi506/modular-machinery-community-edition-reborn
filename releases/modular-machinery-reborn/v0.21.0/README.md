# Modular Machinery: Community Edition Reborn

**Release 0.21.0 — M6b: the upgrade bus, and how an upgrade becomes a recipe modifier.**

## D14 — how a bus upgrade reaches the recipe engine

The original had **no declaration** for what an upgrade does. A CraftTweaker handler called
`controller.addModifier(key, modifier)` (`MachineUpgradeBuilder#addModifier:138-159`); the effect was a script
callback. This project has no script bridge (D7 uses KubeJS), so the effect had to become **data**: M6a's schema
gained `modifiers` (field-for-field the same shape as a machine modifier's, since it is the same `RecipeModifier`)
and `stackable` (the original's `stackAble`).

Bus upgrades and a machine's structural modifiers are **computed separately and joined only at the end**, through
the `RecipeModifiers#andThen` method M6c had already left for exactly this. Three original details are preserved:
the same key is applied once, **compatibility is filtered before anything is added**
(`UpgradeBusProvider#getUpgrades`), and `stackable` applies the value once per copy (`value^n`).

### Reusing D13 was not clean — and that is the interesting part

The **collection path** was reused verbatim (same `machine.pattern().positions()`, hatch first then the narrow
interface, no search radius, no block-update handshake, no fake hatch, bus slots never enter `HatchCollection`).
But "copy D13" could not be taken literally: a parallel controller only has to be **counted**, while a bus has to be
**asked what it holds** and must name its machine in its own GUI. That needs one extra call — `bindMachine` — which
is the original's `UpgradeBusProvider#boundMachine` (`TileUpgradeBus.java:236-245`). Unbinding still runs from the
bus's own 20-tick reconcile (`doRestrictedTick:66-92`), as in the original. Same second path, one wider interface.

## The five tiers

| Tier | Slots | Original source |
|---|---|---|
| normal | 3 | `UpgradeBusData.java:9` |
| reinforced | 6 | `:10` |
| elite | 9 | `:11` |
| super | 12 | `:12` |
| ultimate | 18 | `:13` |

Configurable in `config/modular_machinery_reborn-common.toml` as `upgrade-bus.<tier>.max-upgrade_slot`, range
`1..18` (18 is the original's hardcoded upper bound, `:26`).

## GUI, texture and one honest divergence

The panel is the original's `guiupgradebus.png` — the name taken from code, not from the scoping document
(`GuiContainerUpgradeBus.java:26`) — blitted whole, `ySize=213`. Slot frames come from the sheet at `(7,130)`,
slots start at `(8,17)`, three per row. The scrollbar uses the original's own sprites, `(232,0)` / `(244,0)` of
vanilla's `creative_inventory/tabs.png` (`GuiScrollbar.java:9,22-30`).

**The divergence**: 1.12.2 drew its 0.72-scaled text column in the *foreground* layer with the matrix already
translated to the panel corner; 1.20.1's `renderLabels` is not scaled, so that column is drawn in `renderBg`
instead — the only layer where a `GuiGraphics` can be translated to the panel origin. The matrix numbers are
identical to the original. Side effect, recorded: the original drew that text *above* the item icons, whereas here
the slots are drawn after `renderBg` — the original coordinates do not overlap (text starts at scaled x=92 ≈ screen
x=66; slots end at x=44), so it does not show.

The texture is **byte-identical** to the original:
`CAF24EBD72D216FF8A372C7084AC7933EEBD69739BB40C6F19E97510213684F6`, plus five `overlay_upgrade_bus_<tier>.png`.
Those five are **static 32×32, not animation strips** (unlike the parallel controller's), so there is no `.mcmeta`
— matching the original.

## Acceptance — 170 offline checks, 0 failures

The harness grew from 105 to **170 PASS / 0 FAIL**; the 105 from M6c/M6d are unchanged and all still pass. Driven
in exactly the order `MachineControllerBlockEntity#tick` uses, asserting **real tick counts and real item movement**:

```
PASS  duration with the speed upgrade (5 ticks x 0.5): 3
PASS  ticks actually ticked with the speed upgrade: 3
PASS  cobblestone consumed / stone produced with the speed upgrade: 8 / 8
PASS  a stackable upgrade's multiplier squares at count 2: 0.25
PASS  a non-stackable upgrade's multiplier is unchanged at count 2: 2.0
PASS  stone produced with the output doubler: 8 (from 4 cobblestone)
PASS  a bus upgrade incompatible with the machine contributes nothing: 0
PASS  stone produced with the refused upgrade (undoubled): 2
PASS  two carrier slots merge into one upgrade / counts summed: 1 / 2
PASS  NORMAL/REINFORCED/ELITE/SUPER/ULTIMATE slots: 3/6/9/12/18
```

Slots accept **any item** — not an omission. The original added a bare `SlotItemHandler` with no `isItemValid`
override and merely ignored rejected items in `TileUpgradeBus#onUpgradeInventoryChanged:94-119`; filtering at the
slot would be a Reborn-only tightening.

### Two findings recorded rather than papered over

1. **The harness cannot resolve item names at all.** Outside Forge mod loading `ForgeRegistries.ITEMS.getValue(...)`
   returns `Items.AIR` for every name, including this mod's own, so the live registry cannot be populated there.
   `UpgradeEffects.read` therefore gained a second overload taking a lookup function: the block entity uses the live
   registry, the harness injects one. **The item-mapping parse is declared unprovable offline.**
2. **M6c's "a shortened craft costs the same energy" is only approximate** — attributable to M6c, not this slice.
   `applyDurationMultiplier` uses the *unrounded* new duration for the multiplier while the tick count is
   `round(recipeTime * value)`, so 5 → 3 ticks at ×0.5 draws **4800 FE, not 4000**. The harness asserts the actual
   value and notes the pre-existing behaviour.

## Both mandatory checks

1. **SRG reobfuscation**: passes on the build product, this archive, and the deployed file. Official
   `CREATIVE_MODE_TAB` absent; SRG `f_NNN_` present. `> Task :reobfJar` in the build output.
2. **Build order**: harness → `clean build` → reobfuscation check → deploy. The harness was run twice, each time
   followed by a fresh clean build.

`mods.toml` 0.21.0; **zero** files under `data/`; language files **153 keys each, identical sets**; 14 GUI textures;
renderer invariants intact and the preview input design untouched.

Jar SHA-256 `60FE0C5E26A03E173F94C160C68428AAC1BFB2520F3E8A703DC5334513312103`, deployed with a matching hash.
No user saves or config were touched.

## Not verified — and one thing to watch

The GUI rendering (0.72-scaled column, `tabs.png` scrollbar, slot frames, CJK wrapping), the block model's cutout
layer (the normal overlay is 44% fully transparent), the world-side binding chain (structure check → `bindMachine`
→ the GUI's "已绑定 N 个机械" → reconcile after removal), drops on break, the menu's `ContainerData` indices, and
the tier config file's generation and reading are all unverified in game.

**Worth watching first**: this is the project's **first release with two `COMMON` config specs** (the parallel
controller's and the bus's). If `modular_machinery_reborn-common.toml` misbehaves — overwriting, erroring, or only
one key set appearing — that is the first suspect. The fallback is merging both key sets into a single spec.
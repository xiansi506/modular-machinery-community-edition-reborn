# Modular Machinery: Community Edition Reborn

**Release 0.29.0 — the construct tool (`itemconstructtool`), faithful half: selection + export. VERIFIED IN GAME (2026-10-01).**

## Two premises turned out wrong, and are recorded as D21

1. **The original has NO build half.** `ItemConstructTool.java:53-68` has exactly one handler, `onItemUse` (right-click); repo-wide greps for `onLeftClick` / `LeftClick` / `attackEntity` / `setBlockState` / `buildStructure` / `placeStructure` return **0 hits for this item**. Nothing is ever placed. The only ghost/placement machinery is `BlockArrayPreviewRenderHelper` (367 lines), driven by the **blueprint** — the deferred projector half. **A real block placer would be a new feature needing a separate owner decision**, and the owner has confirmed it is not wanted here. The plan's own wording 「结构选区与建造」 was simply wrong.
2. **The region is not a cuboid.** It is a per-player **toggled set** (`StructureSelection:138`, `:151-157`), WorldEdit-wand style, synced by `PktSyncSelection`, with **no size cap**. The gate (`:54`) is `!isRemote && isCreative() && canSendCommands`; no blueprint and no machine definition is needed.

Right-clicking a **controller** compresses the set into offsets relative to it, rotates it until the controller faces north, and writes `machine-<player>-<yyyy-MM-dd_HH.mm.ss>.json` into the machinery directory — a `{"parts":…}` **fragment with no `registryname`**, which is why the loader (in the original too) logs a failure for it.

## What ships

Faithful behaviour: selection toggle, client white outline (32 blocks, main or off hand), rotation, fragment export, the permission gate, and the original's six chat lines plus tooltip verbatim. Three owner-approved **log-only** additions: the "this is a fragment — add a `registryname`" hint, a skipped-position count (unloaded chunks are skipped rather than read as air), and a large-fragment warning. **Nothing is refused and the file's bytes match the original.**

Deferred: the projector (owner), `ItemDebugStruct` (the original itself says `TODO: Realize it.`), the preview button row (owner), and the block placer (does not exist in the original).

## Author-facing facts (`docs/已知限制.md` §六)

- **Schema:** `{"parts":[{"x":…,"y":…,"z":…,["nbt":{…},]"elements":["ns:block[prop=value,…]"]}]}` — `parts` only; coordinates **relative to the controller**; every blockstate property written name-sorted; `nbt` is the block entity's NBT minus x/y/z.
- **Filename:** `machine-<player>-<yyyy-MM-dd_HH.mm.ss>.json` in `config/modular_machinery_reborn/machinery/`; collisions get `" (0)"`, then `" (1)"` (the original's counter starts at 0).
- **Rotation, stated so an author can act:** *the fragment is written as if the controller faced north — east/south/west are 1/2/3 quarter turns counter-clockwise; paste it back and the machine forms however the controller faces; never rotate it by hand.*

## Evidence (every SUMMARY read out of its own file)

| run | lines | SUMMARY |
|---|---|---|
| RED first (production classes absent) | 1234 | `1042 PASS / 24 FAIL (1066 checks)` |
| GREEN `harness-run-0.29.0.txt` | 1415 | `1168 PASS / 3 FAIL (1171 checks)` |
| A rotation dropped | full | `1162 / 9` |
| B toggle never removes | full | `1163 / 8` |
| C gate without command permission | full | `1165 / 6` |
| D descriptor drops properties | full | `1166 / 5` |
| E collision counter starts at 1 | full | `1166 / 5` |

The 3 FAILs are the known environmental config ones. Section AA proves the pure logic — including that the exported offsets **equal the loader's own `rotateYCounterClockwise`**, that the loader **refuses the fragment by name** (`Missing required field 'registryname'`) and accepts it once one is added, the byte-exact fragment text, `" (0)"`/`" (1)"`, the gate truth table and the packet round trip — plus a **bytecode assertion that the tool never calls `setBlock`**.

**Writing the assertion first earned its keep twice**: two reds were the *assertion* being wrong (the empty fragment ends `    ]}`, not `]\n}`; and a "coordinates removed" test searched SNBT for `y:`, which matches inside `energy:`). A packet check also caught a real trap — `writeBlockPos` gives y only 12 bits, silently altering y≈−4030 — so the packet writes the original's three ints.

## Verification

`clean build` → **BUILD SUCCESSFUL** with `> Task :reobfJar`. **SRG reobfuscation verified on the build product and the deployed file**, and the new `ConstructToolItem.useOn` appears reobfuscated (`m_6225_`). `mods.toml version="0.29.0"`; **zero** `data/` entries; `kubejs.plugins.txt` at the classpath **root**; language keys **176 each, identical sets** (the tool needed 7 keys — the original's tooltip and its six `message.structurebuild.*` under the original's own key names, so they can be diffed against `_mmce-src` line for line; the harness's three count literals moved with them); `StructurePreviewRenderer.java` untouched (mtime still 2026-09-30 01:16:55).

Jar SHA-256 `745E42FDCD8E9079FD668B555248C684652617560C753CD4DD7AA2B71C6D06E4` — build product and deployed file identical.

**Independently checked here**: `javap -c -p com.reborn.modularmachinery.item.ConstructToolItem` contains **zero** instructions matching `setBlock` / `destroyBlock` / `removeBlock` / `placeBlock` / `fillBlocks` — the "no build half" finding corroborated at the bytecode level.

## Verified in game (2026-10-01)

Offline proves the arithmetic, the text and the naming. It proves **nothing** about where a click lands, what the outline looks like, which directory the file reaches at runtime, or how the Chinese messages render.

In creative + OP with the tool in hand: right-click blocks → white outlines appear, a second click on one removes it, nothing draws beyond 32 blocks or with the tool stowed; right-click a **controller** → chat shows 「控制器面朝 north」 (plus the degrees when it does not) and the machinery directory gains `machine-<you>-<timestamp>.json` with relative coordinates and `block[prop=…]`; `/reload` logs the expected `Missing required field 'registryname'` plus the hint (**not a bug**); paste `parts` into a definition that has a `registryname` and rebuild **with the controller facing another direction** → the machine forms — **that last step is the real test of the rotation**; and in survival the tool must **not** block the controller GUI. Full 10 steps in `交接文档.md` (0.29.0 section).
# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.12.0 — milestone M4a: the original controller screen.**

## What 0.12.0 changes

### The controller screen is the original one

The panel is now the original `guicontroller_large.png`, blitted whole — source rectangle (0, 0, 176, 213) —
at a 176×213 window. There are no progress or energy bars, because the original had none: everything the
controller reports is text.

| Item | Value |
|---|---|
| Window | 176 × 213 |
| Background | `textures/gui/controller_legacy.png`, source (0, 0, 176, 213) |
| Blueprint slot | one, at (151, 8), blueprints only |
| Player inventory | 3 × 9 at (8, 131) |
| Hotbar | y = 189 |
| Text block | scale **0.72**, origin (12, 12), wrapped to 135 screen pixels (135 / 0.72), line height 10, white with shadow |

The texture was already in the repository — byte-identical to the original, verified by SHA-256
(`FB73DAE4…`).

The old screen was a `fill`-built panel of 176×166 with two working slots, an FE bar, a progress bar, and two
hard-coded Chinese strings. All of that is gone; every string is a language key now.

### The line order is ported line for line

The original drew its text in a fixed order with fixed gaps, and the gaps are not uniform (name lines take 10,
section ends take 15). Rather than eyeball it, the original's method and this implementation were each
rewritten as an independent algorithm and compared across nine scenarios — redstone stopped, unformed with and
without a blueprint, formed with one or two name lines, with addon info, mid-craft with a three-line status,
and unformed-with-no-blueprint. **All nine produce identical line positions.**

```
unformed, no blueprint   y=12 blueprint-none  y=27 structure-none  y=42 status-head  y=52 status
formed, mid-craft        y=12 structure-head  y=22 structure-name  y=37 status-head
                         y=47 status y=57 status y=67 status y=82 progress
```

### The controller lost its Reborn-only conveniences

Following the originals, the controller now has **one slot (the blueprint)** and exposes **no capabilities**.
Its two working slots, FE buffer and fluid tank — and the fallback ports they provided — are removed. That was
a deliberate stopgap from M3, documented then as something to delete once the original screen landed.

The consequence is intended: a machine consumes and produces only through hatches, so hatches have to be
placed. Right-clicking the controller opens the screen and no longer stuffs the held item into a slot.

### Addon extension point

`ControllerGuiInfoEvent` (in `api`) replaces the original's `ControllerGUIRenderEvent` as a Forge event, so
addons can add lines to the screen:

```java
@SubscribeEvent
public static void onControllerInfo(ControllerGuiInfoEvent event) {
    event.addInfo(Component.literal("My addon: " + event.controller().progress()));
}
```

### Statuses and redstone

The five original statuses are ported with their names: `missing_structure`, `chunk_unloaded`, `no_recipe`,
`idle`, `crafting`. Redstone is handled as the original did — while the controller is strongly powered, the
screen prints only `redstone_stopped` and nothing else, and the machine stops. `Level#getDirectSignalTo` is the
1.20.1 counterpart of 1.12.2's `World#getStrongPower`, which is what the original read.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.12.0.jar`.
- **Layout differential test: 9 scenarios, 0 differences** against an independent transcription of the
  original method.
- **GUI spec check: 12 of 12** — window size, blit source, blueprint slot position, player slot positions,
  text scale/origin/wrap width/line height/colour, single-slot inventory, blueprint-only filter.
- Language keys: 91 per file, **0 missing and 0 orphans** (was 82; three keys that belonged to the removed
  screen were deleted, twelve controller keys added).
- Jar still carries no content: 0 files under `data/`, 8 blockstates, 59 item models.

### Not verifiable offline

The screen itself has not been rendered. Pixel-accurate alignment against the original panel can only be
confirmed by opening it in game.

## Known limitations

- **No per-requirement failure text.** The original replaced the generic status with a specific
  `craftcheck.failure.*` message naming the requirement that was short ("Not enough items", and so on). This
  release shows `idle` instead, so a machine waiting on a resource does not say which one.
- **The performance footer is missing.** The original's last line
  (`Avg: %sμs/t (Search: %sms), WorkMode: %s`) reports parallel-crafting statistics that do not exist yet; the
  plan already allowed deferring it.
- **The blueprint slot has no effect yet.** It exists, accepts blueprints and is positioned correctly, but the
  blueprint does not yet select a machine — that is the blueprint work in M5. `blueprintMachineId()` always
  returns null, so the screen shows "Found blueprint: none" while unformed.
- **Parallelism is not shown**, because there is no parallelism system (M6).
- The hatch screens, structure preview, upgrades, factory system, mod integrations and compatibility layer
  remain unimplemented.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

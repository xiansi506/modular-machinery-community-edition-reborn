# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.13.0 — milestone M4b: the hatch screens.**

## What 0.13.0 changes

### Every hatch has its own screen

Right-clicking a hatch now opens it instead of printing a chat line. All hatches are a 176×166 window with the
player's 3×9 inventory at (8, 84) and the hotbar at (8, 142) — the layout the original's `ContainerBase` used.

**Item hatches** select `inventory_<tier>.png`, which has that tier's slot holes cut into it. Slot coordinates
and texture are one specification, so the coordinates are copied from the original `ContainerItemBus` exactly:

| Tier | Slots | Coordinates |
|---|---|---|
| tiny | 1 | (81, 30) |
| small | 4 | (70,18) (88,18) (70,36) (88,36) |
| normal | 6 | 3×2 grid from (61, 18) |
| reinforced | 9 | 3×3 grid from (61, 13) |
| big | 12 | 4×3 grid from (52, 18) |
| huge | 16 | 4×4 grid from (53, 8) |
| ludicrous | 32 | 8×4 grid from (17, 8) |

**Fluid and energy hatches** share `guibar.png`, which carries both the frame (u = 176) and the fill (u = 196)
for the bar at (15, 10, 20×61). Energy fills that bar with the red texture from the bottom up; fluid draws the
fluid's own atlas texture, tinted with its colour, and then lays the frame over it — the original's order.
Hovering the bar shows the amount, and clicking it with a container in hand runs that container against the tank,
so buckets can be filled and emptied from the screen.

### What the screens needed underneath

- **The client now knows which fluid a hatch holds.** Fluid hatches send a block update when their tank changes,
  carrying the saved state as the update tag. Item slots travel with the menu and the energy numbers travel in
  the menu's container data, so neither sends a block update — that would be traffic for nothing.
- **Menu data.** One container type serves every family and tier. Its four container-data slots are the energy
  and fluid amounts and capacities; the server answers from the live storage and the client from a mirror, which
  is the same arrangement vanilla furnaces use.
- **Clicking the tank** goes through a menu button rather than a custom packet — the original sent
  `PktInteractFluidTankGui`, but 1.20.1's `clickMenuButton` is the vanilla route for exactly this.
- Following the original, the player's slots come first (0..35) and the hatch's own slots after them.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.13.0.jar`.
- **Slot differential test: all 7 tiers, 0 differences.** The original's parameters were read from
  `ContainerItemBus` and this implementation's were extracted from `MachineHatchMenu.java` by regex, then both
  expanded into coordinate lists and compared. Slot counts also match the tier capacities this mod already
  declared (1/4/6/9/12/16/32).
- **All 8 hatch GUI textures are byte-identical to the originals** (SHA-256), as the controller texture already
  was.
- Language keys: 93 per file, **0 missing and 0 orphans** (was 91: one key that belonged to the removed chat
  message deleted, three tooltip keys added).
- Jar still carries no content: 0 files under `data/`, 8 blockstates, 59 item models.

### Not verifiable offline

The screens have not been rendered. Pixel alignment between the slot coordinates and the baked-in holes, and the
fluid bar's appearance, can only be confirmed in game.

## Known limitations

- **Mekanism gas is not supported.** The original's fluid hatch could also hold Mekanism gas (`HybridGasTank`)
  and showed a `[Gas]` tooltip. That is part of the compatibility layer.
- **The energy unit is fixed to FE.** The original let a config pick FE / RF / IC2 EU / GT EU and formatted the
  tooltip accordingly.
- **Hatch item tooltips are unchanged**; only the screens are new.
- The structure preview, upgrades, factory system, mod integrations and compatibility layer remain
  unimplemented, and the controller still shows `idle` rather than naming the requirement a machine is short of.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

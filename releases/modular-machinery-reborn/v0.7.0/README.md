# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.7.0 — milestone M3: hatch port capability layer.**

## What 0.7.0 changes

### Hatches now hold the machine's resources

In the original mod the **hatches** owned the storage and exposed the Forge capabilities, while the controller
exposed none — a machine's inventory, tank and energy buffer were simply the sum of its hatches. 0.7.0
reproduces that.

- `MachineHatchBlockEntity` backs all six hatch blocks. One block entity type serves the whole family because
  each family is a single block with a `size` property: the family and tier are read from the blockstate.
- Storage and capacity come straight from the original tier enums, so they are real:

| Family | Tiny | Small | Normal | Reinforced | Big | Huge | Ludicrous | extra |
|---|---|---|---|---|---|---|---|---|
| Item bus (slots) | 1 | 4 | 6 | 9 | 12 | 16 | 32 | — |
| Fluid hatch (mB) | 100 | 400 | 1000 | 2000 | 4500 | 8000 | 16000 | vacuum 32000 |
| Energy hatch (FE) | 2048 | 4096 | 8192 | 16384 | 32768 | 131072 | 524288 | ultimate 2097152 |

  Energy transfer follows the original `EnergyHatchData` transfer limits (128 → 131072 FE/t).
- Storage is persisted in NBT, so contents survive a world reload.
- Hovering a hatch item shows the capacity its tier grants; right-clicking a placed hatch shows its name and
  capacity. The old placeholder message ("this port is reserved for the machine controller") is gone.

### The controller gathers its ports

`collectHatches` walks the **positions of the formed pattern** and buckets every hatch it finds into a
`HatchCollection` (item/fluid/energy × input/output). Hatches outside the structure are ignored — you cannot
extend a machine's capacity by parking a chest of buses next to it.

Crafting is routed through those ports:

- **Input** is taken from item input hatches, then the controller's own slot.
- **FE** is taken from energy input hatches, then the controller's own buffer.
- **Output** goes to item output hatches, then the controller's own output slot.

The output check is still simulated before anything is consumed, which keeps the 0.5.0 resource-destruction
bug from reappearing across the new port paths.

Fluid hatches are fully functional storage (pipes can fill and drain them) but the current recipe type has no
fluid fields, so no recipe consumes them yet — that arrives with the requirement engine.

### The GUI energy bar now reflects the hatches

`energyStored()` and `energyCapacity()` aggregate the structure's energy input hatches with the controller's
own buffer, so charging an energy hatch is visible in the controller screen.

## Breaking-ish notes

No registry name changed; the change is behavioural.

- A machine with **no** hatches can no longer be fed by its own ports alone... except through the controller's
  own item slots and FE buffer, which are still there. Those are a Reborn-only convenience: the original
  controller's only slot was the blueprint slot, so they will be removed when the controller GUI is replaced
  with the original layout.
- The controller still exposes Forge item, FE and fluid capabilities, unlike the original. Same reason, same
  plan.

## Verification

- Build: `BUILD SUCCESSFUL`, jar `modular_machinery_reborn-0.7.0.jar`.
- **Tier values cross-checked against the original enums**: all 23 numbers (7 item slot counts, 8 fluid
  capacities, 8 energy capacity/transfer pairs) parsed out of `ItemBusSize`, `FluidHatchSize` and
  `EnergyHatchData` and compared with `HatchTier` — **0 mismatches**.
- Language keys: 75 keys in each of `zh_cn.json` and `en_us.json`, covering 59 items, 8 blocks and 9 UI
  strings — **0 missing, 0 orphans**.

## Known limitations

- The recipe engine is still the simple M1 model (one item in, one item out, duration, FE). Fluid, gas,
  per-tick consumption, chance outputs, min/max amounts, catalysts and fuel all still need the requirement
  engine (M2).
- Tiered hatches have no GUIs yet; you open them with pipes, not by right-clicking. The original per-tier
  `inventory_<tier>.png` screens are M4b.
- `dynamic-patterns`, `modifiers`, `selector-tag`, `nbt`, `failure-action` consumption and
  `requires-blueprint` enforcement are still outstanding, as is the structure preview/blueprint tooling and
  the original controller GUI.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

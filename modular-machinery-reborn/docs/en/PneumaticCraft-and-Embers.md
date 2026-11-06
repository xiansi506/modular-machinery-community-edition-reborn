# PneumaticCraft and Embers Rekindled (0.121.5)

Minecraft 1.20.1 / Forge / Java 17. This integration targets **PneumaticCraft: Repressurized 6.0.23** and **Embers Rekindled 1.20.1-1.4.7** from [RCXcrafter's repository](https://github.com/RCXcrafter/EmbersRekindled). Each mod is optional and requires its own dependencies. Update both client and server to MMCER 0.121.62, protocol **13**.

## Hatches

Prefix all IDs below with `modular_machinery_reborn:` and include the appropriate hatch in the formed machine structure.

| Resource | Input | Output |
|---|---|---|
| Compressed air | `compressed_air_input_hatch` | `compressed_air_output_hatch` |
| Ember | `ember_input_hatch` | `ember_output_hatch` |

Air hatches use the genuine tier-two air handler, with a default base volume of **16000 mL**. Air amount = pressure in bar × volume in mL. Native PneumaticCraft handles pipe equalization, vacuum and overpressure. Recipe output checks space up to the danger-pressure threshold; external supply still follows native overpressure rules. An output hatch without a connected pneumatic neighbor leaks upward. Its screen shows air, pressure, volume and danger/critical pressure. Compressed air does not use fluid or Mekanism gas storage.

Ember hatches default to **256000 Ember**, with fractional amounts. Attach an Ember receiver to supply the hatch through the normal emitter/receiver network. An emitter can draw from the output hatch. The capability supports simulated transfers and partial acceptance. Ember is not converted to FE.

Configuration in `modular_machinery_reborn-common.toml`:

```toml
["mods.pneumaticcraft"]
"Air Volume" = 16000
["mods.embers"]
"Ember Capacity" = 256000.0
```

As of 0.121.5, an Ember backend keeps its server capacity for its block-entity lifetime, so configuration changes cannot truncate a transfer or rollback midway. A new backend adopts the new capacity; clients display the synced server capacity. Restart and reload the block entity after editing. Drain storage before lowering capacity. Ember loads clamp to the configured capacity. Pneumatic volume uses current configuration, so changing volume changes the pressure represented by saved air.

## Recipe requirements

One-shot air input checks at least 4.5 bar **before payment**, then consumes 1000 mL:

```json
{ "type": "compressed_air", "io-type": "input", "pressure": 4.5, "air": 1000 }
```

Use `compressed_air_pertick` for an ongoing pressure check and per-tick consumption. `air:0`, or omitted air on input, is a pressure-only gate. Pressure is required on input, in bar; zero disables the pressure threshold and a negative value requires an equal or stronger vacuum (e.g. pressure ≤ -0.5 bar). Parallelism multiplies air amount, not the pressure threshold.

```json
{ "type": "compressed_air_pertick", "io-type": "input", "pressure": 4.5, "air": 10 }
```

At 100 recipe ticks, one parallel copy and no modifiers, this consumes 1000 mL. Ongoing supply is required if consumption would drop pressure below the threshold. Air output uses the same two types with `io-type:"output"` and an explicit `air` amount, but no pressure condition. Air amounts are integers in 0..2147483647. `amount` is accepted as an alias. A supplied `perTick` must agree with the type. Legacy `time` accepts positive integers for compatibility; actual duration and JEI totals use the recipe's `recipeTime`.

Ember requirements use a nonnegative finite `amount`, allowing fractions; legacy `ember` is also accepted:

```json
{ "type": "ember", "io-type": "input", "amount": 100.5 }
```

Set `perTick:true` for per-tick settlement, or change `io-type` to output with an output hatch. Both resources support selector tags, parallelism, fixed costs via `parallelize-unaffected`, modifiers and shared resource transactions. Modifier targets are `compressed_air` and `ember`. Multiple requirements share remaining storage and failed commits roll back previous resource operations.

## KubeJS 6

Recipe-chain methods:

```text
.compressedAirInput(pressure, air)
.compressedAirOutput(air)
.compressedAirPerTick(pressure, air, io)
.emberInput(amount[, perTick])
.emberOutput(amount[, perTick])
```

Use `io` = `'input'` or `'output'`, and pressure 0 for air output. See the [complete example packs](../../examples/pneumatic-embers/README.md). JSON and KubeJS definitions are alternatives; do not install both copies.

## JEI and validation limits

Air and Ember have separate input/output resource cells, blue/orange bars and tooltips with exact amounts, pressure conditions and script notes. Display scale is not available hatch capacity. Offline checks and build evidence are recorded in the [update notes](../归档/版本记录/更新记录-0.119.0.md). Actual pipe equalization, leaking/overpressure, Ember transfer networks, multiplayer synchronization and OpenGL visuals still need in-game acceptance.

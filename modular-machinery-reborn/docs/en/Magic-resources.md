> **Current safety behavior:** Since 0.121.85, a fractional WILL/EMBER recipe may start at an affordable parallel count even when one copy cannot move the balance. Nominal cost and existing movement precision are unchanged. Unknown/complex port plans or exhausted search budgets report incomplete and do not pay; lower the parallel ceiling or use supported local ports. [Verification](../归档/开发记录/已完成-2026-11-06/修复记录-剩余G3-0.121.85-2026-11-05.md). Offline validation only; no game deployment.

# Magic resources: Botania, Nature’s Aura and Blood Magic 3 (0.121.0)

For Minecraft 1.20.1 / Forge. Each integration is optional; its hatches and requirement types register only when that mod is installed. API checks used **Botania 1.20.1-455-FORGE**, **Nature’s Aura 39.4** and **Blood Magic 3.3.8-50**. Install their own dependencies as well. Both client and server must use protocol **13**.

Custom machines can consume and produce these resources. This does not import recipes from third-party machines such as the Blood Altar. Other magic integrations remain deferred.

## JEI Aura gauge

Aura recipe requirements use a green 6×80 vertical gauge in separate input/output columns. Only Nature’s Aura and JEI are needed; gugu-utils is not a runtime dependency. Mana, LP and Demon Will retain colored text cells.

- One-time requirements display amount. Per-tick requirements display amount × recipeTime as the total for one recipe execution.
- Full scale is **166666 raw Aura units**, for display only. It is neither the local world balance nor a hatch capacity or processing limit. Amounts above the scale fill the bar and show “+”.
- Hover anywhere along the full bar for the exact amount, Aura type and script tooltip. Per-tick requirements also show the exact total. Multiple requirements wrap into separate rows without covering item or fluid slots.

For amount 800 and recipeTime 1200, a one-time requirement displays 800; perTick true totals 960000 and shows “+”. This display does not change payment, parallelism or modifiers. Real JEI visuals remain pending acceptance.

## Hatches and connections

Include the hatches in your machine structure. All IDs below use the `modular_machinery_reborn:` namespace. Right-click a hatch to inspect its readings.

| Resource | Input hatch | Output hatch | Storage |
|---|---|---|---|
| Mana | `mana_input_hatch` | `mana_output_hatch` | Local mana cache |
| Aura | `aura_input_hatch` | `aura_output_hatch` | The real environment at the hatch position |
| Life Essence | `lifeessence_input_hatch` | `lifeessence_output_hatch` | Bound Blood Orb owner’s SoulNetwork and local LP cache |
| Demon Will | `will_input_hatch` | `will_output_hatch` | The real world chunk containing the hatch |

Mana inputs accept mana bursts and expose a ManaReceiver. Outputs expose a ManaPool and join Botania’s mana network so functional flowers and spreaders can discover or drain them under Botania’s own rules. Outputs reject bursts but preserve signed direct API transfers, including mana deposited by generating flowers. Recipes still respect hatch input/output directions. Basic SparkAttachable capabilities are present; actual burst, flower and spark combinations still need in-game acceptance.

Mana capacity defaults to **100000** per hatch. Configure `config/modular_machinery_reborn-common.toml`:

```toml
["mods.botania"]
"Mana Capacity" = 100000
```

The range is `0..2147483647`; zero disables storage. Restart and reload the block entity to apply capacity changes.

Each LP hatch has one Blood Orb slot. Bind the orb through Blood Magic first. Unbound or malformed bindings cannot access recipe LP. Every 5 ticks, input hatches pull from the owner’s SoulNetwork toward a cache of **one tenth of the orb’s capacity**. Outputs buffer up to the orb capacity and attempt to return cached LP every 5 ticks. Full networks or rejected API transfers leave the remainder cached for retry. Removing an orb preserves its cache but disables recipe access until a valid bound orb is inserted. No additional online-owner condition is imposed.

Aura and Will have no independent tanks. The hatch-screen gauges use 1,000,000 Aura and 100 Will as visual scales; these are not hard world limits. All five Will types are displayed separately. Moving a hatch changes its environment. Multiple hatches in one chunk cannot count the same resource twice.

## JSON requirements

Place these objects in a normal machine recipe’s `requirements` array. `io-type` is `input` or `output`. `perTick` defaults to `false`; set it to `true` for payment or production every tick. One-time inputs are paid at recipe start and outputs produced at completion.

| `type` | `amount` | Other fields |
|---|---|---|
| `mana` | Nonnegative integer mana | No variant |
| `lifeessence` | Nonnegative integer LP | No variant |
| `aura` | Nonnegative integer in raw API units | Required `aura-type`; optional `min-aura`, `max-aura` |
| `will` | Nonnegative finite decimal | Optional `will-type`, `min`, `max` |

Integer amounts are limited to `2147483647` per requirement. Will types are `default` (the default), `corrosive`, `destructive`, `vengeful` and `steadfast`, normalized to lowercase. Input `min` is the balance to retain after payment (default 0). Output `max` is the balance allowed after production (default 100).

Aura types must match the environment, for example `naturesaura:overworld`, `naturesaura:nether` or `naturesaura:end`. Aura uses **raw API units: an amount of 100 transfers 100**, with no extra 100000 multiplier. The default input floor is `-2147483648`, which permits negative Aura; explicitly set `min-aura: 0` to avoid overdrawing. The default output ceiling is `2147483647`. Bounds must also be integers. Invalid types or unavailable chunks fail resource checks.

This complete recipe needs a separately defined and formed `magic_demo:combined` machine with item input/output, mana input, Aura input, LP input and Will output hatches. All three mods are required for this combined example.

```json
{
  "type": "modular_machinery_reborn:machine",
  "machine": "magic_demo:combined",
  "recipeTime": 100,
  "requirements": [
    {"type":"item","io-type":"input","item":"minecraft:cobblestone","amount":1},
    {"type":"mana","io-type":"input","amount":1000},
    {"type":"aura","io-type":"input","aura-type":"naturesaura:overworld","amount":100,"min-aura":0},
    {"type":"lifeessence","io-type":"input","amount":100},
    {"type":"will","io-type":"output","will-type":"default","amount":0.25,"max":100},
    {"type":"item","io-type":"output","item":"minecraft:stone","amount":1}
  ]
}
```

See the [separate minimal data packs](../../examples/magic-resources/README.md). Examples are not automatically loaded from the mod JAR.

## KubeJS

Use the existing machine recipe entry point inside `ServerEvents.recipes`:

```js
ServerEvents.recipes(event => {
  event.recipes.modular_machinery_reborn.machine('magic_demo:combined')
    .time(100)
    .itemInput('minecraft:cobblestone', 1)
    .manaInput(1000)
    .auraInput('naturesaura:overworld', 100, 0)
    .lifeEssenceInput(100)
    .willOutput('default', 0.25, 100)
    .itemOutput('minecraft:stone', 1)
    .id('magic_demo:combined_stone')
})
```

| Method | Resource |
|---|---|
| `.manaInput(amount[, perTick])` / `.manaOutput(amount[, perTick])` | Mana |
| `.lifeEssenceInput(amount[, perTick])` / `.lifeEssenceOutput(amount[, perTick])` | LP |
| `.auraInput(type, amount, minimum)` / `.auraOutput(type, amount, maximum)` | One-time Aura |
| `.auraPerTick(type, amount, io, minimum, maximum)` | Aura per tick; `io` is `input` / `output` |
| `.willInput(type, amount, minimum)` / `.willOutput(type, amount, maximum)` | One-time Will |
| `.willPerTick(type, amount, io, minimum, maximum)` | Will per tick |

`event.custom` also accepts the complete JSON above. Modifier targets include `mana`, `aura`, `lifeessence` and `will`. Parallelism, `parallelize-unaffected`, component selector tags, groups and per-tick duration compensation use the existing machinery rules. Integer resources round each modified copy down before multiplying by parallelism.

## Transactions and acceptance limits

Reservations share the identity of the actual resource. Failed commits roll back executed steps, including the original distribution of Aura spots within a chunk. Overlapping environments that cannot commit safely fail and roll back. Tiny Will payments that cannot be represented at a very large balance are rejected to prevent free crafting.

JEI uses the green gauge for Aura and colored text cells for the other magic resources, with complete descriptions and script tooltips. There is no separate magic ingredient index or R/U lookup. Split menu words preserve double readings, and chunk update packets omit the orb’s private binding NBT.

Offline checks used actual published APIs and resource classes for simulation, payment, cancellation, shared balances, rollback and persistent caches. No Forge game was launched. Real bursts, sparks, flowers, Aura effects, SoulNetwork persistence across game restarts, multiplayer synchronization and full Forge event dispatch still require in-game acceptance. The current game instance was not modified by these checks.

[中文说明](../联动/魔法资源.md) · [Recipe guide](../KJS-配方指南.md) · [Documentation index](../README.md)

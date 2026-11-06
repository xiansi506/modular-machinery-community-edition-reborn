> **Current safety behavior:** Since 0.121.84, direct recipe writes require candidate permissions and reliable recovery contracts; automatic energy push also requires a known local source and a destination receipt. Unknown targets are refused before debit. External devices can still actively charge/extract through hatch FE/long APIs; the Flux Networks/Draconic Evolution public bridges remain. [Verification](../归档/开发记录/已完成-2026-11-06/修复记录-剩余G3-0.121.85-2026-11-05.md). Offline validation only; no game deployment.

# Long energy, gas sprites and model materials (0.118.6)

0.118.4 preserves JSON long energy integers through settlement, removes the premature quarter-long cap, and saturates only the final draw at `Long.MAX_VALUE`. Modifier coefficients retain their existing float aggregation semantics. JavaScript Number precision and ordinary Forge Energy int transfer limits still apply; see the [bilingual patch notes](../归档/版本记录/修复记录-0.118.4.md).

These features have passed offline checks and a production build. In-game acceptance is pending. Update clients and servers together to current protocol **13**. Back up the world before upgrading; energy values above the int range cannot be restored faithfully by older versions.

## Energy hatches

Edit `config/modular_machinery_reborn-common.toml`. For example:

```toml
["energyhatch.size"]
TINY = 10000000000
["energyhatch.limit"]
TINY = 5000000000
[general]
enable-fluxnetworks-integration = true
```

Capacity supports `1..9223372036854775807`; transfer supports `1..9223372036854775806`. Defaults retain the previous tier values. Restart the world, then reload the chunk or replace an existing hatch to rebuild it with the new settings. Menus, comparators, recipe transactions, saves and Jade hatch energy bars use the full long values. Aggregated totals saturate at the long maximum.

Legacy int saves load by replacement, independently of transfer limits. External input hatch ports accept energy; external output hatch ports supply it. Recipes retain access to their internal buffers.

## Optional energy connections

### Wireless energy hatches (0.122.0)

With **Flux Networks 1.20.1-7.2.1.15** installed, eight tiers each of Wireless Energy Input Hatch and Wireless Energy Output Hatch are registered. Each hatch joins a Flux Network directly. Right-click to open the native Flux interface for network selection, passwords, membership permissions, priority, surge and transfer limits.

| Hatch | Native role | Flow | Shapeless crafting |
|---|---|---|---|
| Wireless input | Flux Point | Network → shared hatch buffer → recipe | Same-tier ordinary input hatch + Flux Point |
| Wireless output | Flux Plug | Recipe → shared hatch buffer → network | Same-tier ordinary output hatch + Flux Plug |

Tier capacity and hardware rate use the existing `energyhatch.size` and `energyhatch.limit` settings, with exact long storage. User and hardware limits both apply. Bypass removes the user limit while retaining the hardware cap. All transfers in a network cycle share that hatch's budget. Extreme long rates also share a request ceiling across the network's points to keep this integration's own summed requests within signed long range.

The owner, binding, settings and energy persist in block-entity data. Disconnecting retains energy; unloaded or replaced entities cannot transfer. Breaking drops an empty item of the same tier, which requires a new network selection when placed again. External FE/long ports retain the usual input-only receiving and output-only extracting directions.

Creators must allow these blocks in their machine definitions. For a normal input hatch:

```json
{"x":0,"y":-1,"z":0,"elements":"modular_machinery_reborn:wireless_energy_input_hatch[size=normal]"}
```

The output block is `modular_machinery_reborn:wireless_energy_output_hatch`. Items add a tier suffix such as `_normal`. Exact ordinary-hatch rules do not accept wireless blocks automatically; use an element candidate array to allow both. The `modular_machinery_reborn:energy_input_hatches` and `energy_output_hatches` block tags include the corresponding ordinary and optional wireless families.

Disabling `general.enable-fluxnetworks-integration` and restarting stops wireless connections/transfers while retaining blocks and local recipe buffers. Without Flux Networks, these blocks/items and recipes are absent. Native chunk-loading settings remain controlled by Flux Networks; keep the entire structure loaded for continuous processing. A network switch requested before the initial join completes may need retrying after the next network tick.

Offline checks exercise the real mapped Flux scheduler. Native GUI interaction, password entry, rendering, multiplayer and live world reloads still require in-game acceptance.

Install the corresponding **1.20.1** mods and their dependencies as needed. Flux Points supply input hatches; Flux Plugs drain output hatches through Flux Networks' long capability. Its configuration switch defaults to true and requires a world restart. Draconic Evolution devices can use Brandon's Core's OP capability, with `1 FE = 1 OP`; configure their input/output modes and links as required by those mods.

Output hatches select one long capability per neighbor, with FE as a fallback. All six automatic output faces share one transfer budget per tick. Ordinary FE remains limited to `2147483647` per API call. Transfer settings bound each operation; external repeated pulls do not share a global tick budget.

API compatibility was checked against the official Flux Networks 1.20 branch and the Brandon's Core `1.20.1-3.2.1.302` API used by Draconic Evolution's 1.20 branch. Real networks, devices, multiplayer and world reloads still need acceptance testing. Other energy backends were not added.

## Gas visuals and model materials

JEI gas cells now draw the gas's animated atlas sprite, tint and tank shell. Existing descriptions, quantities, chances and script tooltips remain. Missing sprites fall back to text. Recipes need no changes. This does not add a new gas ingredient index or gas R/U lookup.

GeckoLib bone names recognize case-sensitive `emissive`, `bloom`, `transparent`, `emissive_transparent` and `bloom_transparent` prefixes. Children inherit their parents' flags. Both emissive prefixes use full brightness and a world-space mask feeding a two-pass blur and scene composition. Transparent bones blend using texture alpha without writing depth; the prefix does not make an opaque texture translucent.

Previews retain material effects without a full-screen halo. World bloom captures up to 64 emissive textures per frame; additional textures retain full brightness. Shader failures disable bloom until a resource reload and are logged. Reloading resources and disconnecting discard pending mask geometry. Embeddium, Oculus, shader packs, Fabulous mode and live resizing remain unverified in-game. Async VBO rendering and other external model loaders are outside this change.

[中文说明](../联动/能源与特殊渲染.md) · [Introduction](Introduction.md)

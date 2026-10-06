# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Current release: 0.30.1** — the first public release; **functionally 0.29.0**, since it only corrected the
`license` / `authors` metadata in `META-INF/mods.toml`.

**Built, next up: 0.31.0** — the first **feature** release after it, which closes the item the release notes had
listed as unfinished: **KubeJS can write a complete machine definition** (all eleven extended root fields, see
**KubeJS** below). ⚠️ **It is built and asserted offline, but not yet verified in game, not deployed and not
archived** — by this project's 必查 7 that makes it "built, not yet verified in game". Its checklist is in
`迁移日志.md` (the 0.31.0 section) and its build data is in `交接文档.md` §1b.

> 🚀 **New here? Start at [docs/使用说明.md](docs/使用说明.md)** — "from an empty instance to a machine that runs",
> in Chinese, with complete copy-pasteable machine definitions and recipes.
>
> **This mod ships no machines and no recipes.** The jar's `data/` is empty on purpose (M10), and **no example
> data pack is shipped either** — the documentation *is* the distribution for content. You supply machines
> (data pack / config directory / KubeJS) and recipes (JSON / KubeJS) yourself.
>
> 📚 **文档导航见 [docs/README.md](docs/README.md)** —— 一屏看懂该读哪份（总纲 / 专项 / 历史）。

## Construct Selection Tool (0.29.0)

`itemconstructtool` is the original's structure-to-JSON tool. **It does not place blocks** — the original has no
build half (`ItemConstructTool` has a single `onItemUse` handler and never calls `setBlockState`), and neither
does this port. In creative mode with command permission:

1. **Right-click blocks** to toggle them in and out of the selection. Each selected position is outlined in
   white while the tool is held and the position is within 32 blocks.
2. **Right-click the controller** of the machine you are describing. The selection is exported as a JSON
   **fragment** into `config/modular_machinery_reborn/machinery/`, named
   `machine-<player>-<yyyy-MM-dd_HH.mm.ss>.json` — a second export in the same second gets the original's
   collision suffix `" (0)"`, then `" (1)"`. The chat reports the controller's facing and, for a controller that
   does not face north, how many degrees the structure was rotated by.

The rotation rule, as a sentence an author can act on: **the exported `parts` are written as if the controller
faced north — north needs no rotation, and a controller facing east/south/west is turned 1/2/3 quarter turns
counter-clockwise (90°/180°/270°)**, the same rule the loader applies when it rotates a pattern. Put the
fragment back into a definition and the machine forms whatever way the controller faces; do not rotate it by
hand.

What the fragment contains: **`parts` and nothing else** — one entry per selected position with `x`, `y`, `z`
relative to the controller, an optional `nbt` (the block entity's NBT with `x`/`y`/`z` removed), and `elements`
naming the block **with every blockstate property**, so the exact variant is recorded. **There is deliberately
no `registryname`**: it is a fragment, and the loader will say so (`Missing required field 'registryname'`) until
you paste its `parts` array into a definition that has one. The tool logs that next step when it writes the file.

A selection has **no size cap** (the original has none); a very large one is warned about in the log, never
refused. Positions in unloaded chunks are skipped and counted in the log rather than read as air. Offline
evidence: `_audit/m6c-verify/harness-run-0.29.0.txt`, section AA. **The world-facing behaviour — where the clicks
land, what the outline looks like, which directory the file lands in at runtime — is not verified offline**; the
checklist is in `交接文档.md` (0.29.0).


## KubeJS

Two entry points, both optional (the mod loads without KubeJS):

- **Recipes** — `event.recipes.modular_machinery_reborn.machine({ … })`, with this mod's own JSON shape.
  Verified in game at 0.27.1. See [docs/KJS-配方指南.md](docs/KJS-配方指南.md).
- **Machine definitions (0.28.0 core fields; 0.31.0 the full set)** —
  `MachineRegistryEvents.registry(event => event.machine('x').part(…))`.
  A script machine is parsed by the **same schema** a data-pack definition is, so a malformed script machine
  gets the same sentence a malformed JSON one gets. Scripts win over the config directory and over data packs
  for a colliding registry name, and deleting a script deletes its machine on the next `/reload`. Such a machine
  gets the generic `machine_controller` (blocks are registered before any script runs, D8) and a blueprint like
  any other.
  **0.31.0 added the eleven extended fields** — `modifiers`, `smart-interfaces`, `has-factory`, `factory-only`,
  `max-threads`, `core-threads`, the three parallelism fields, `failure-action` and `requires-blueprint` — each
  with a builder method; the three structured ones take an object literal or the same text as a JSON string
  (routed through KubeJS's own `JsonIO.of`). **No validation moved into the builder**: a malformed entry is still
  refused by the schema, in the schema's own words. Table: [docs/KJS-配方指南.md](docs/KJS-配方指南.md) §六.
  **Built and asserted offline; not yet verified in game** — the checklist is in `迁移日志.md` (0.31.0).
  *(0.28.1 fixed the event binding: an event group must reach a script as a KubeJS `EventGroupWrapper`, or the
  binding exists with no events on it and the first line of the script dies with `Cannot find function registry
  in object MachineRegistryEvents`. `onEvent('machineRegistry', …)` is not an alternative — KubeJS 6 removed it.)*
  *(0.28.2 fixed an overload ambiguity: the builder declared `part(int,int,int,Object...)` **and**
  `part(List,List,List,Object...)` — same name, same arity, both varargs — so which one a script's
  `.part(1, -1, 0, 'minecraft:stone')` reached was decided by Rhino's conversion weights and by
  `getMethods()`' unspecified order. The list form is now `parts(List<? extends Number>…, Object...)`, a
  different name, and the array spelling works for the first time — it used to throw `ClassCastException`
  because a script's array arrives as `List<Double>`. The scalar spelling is unchanged.)*


## Structure preview

Right-clicking a bound blueprint opens its machine's structure in 3D, with the blocks it needs listed along the
bottom. Dragging turns it; scrolling zooms. JEI has a matching **Machine Structure** category with one entry
per machine, laid out identically.

The layout is the original's, from `PreviewLayout`: the panel is 184×220, the 3D viewport is 172×150 where the
original's world renderer sat, and the component row is at (5, 179), where `MachineStructurePreviewPanel` put
its ingredient list. uomponents are both drawn there *and* registered with JEI as ingredients, so searching an
item finds the structures that use it. Anything past the row's capacity shows as a `+N` marker.

The renderer is written rather than ported — the original's 847-line `WorldSceneRenderer` built a fake world
with its own camera and compile-cache thread on the 1.12.2 render stack, none of which transfers. It keeps the
GUI's orthographic projection and rotates the pose, and turns the world into GUI space with a 180° rotation
about X rather than a negative Y scale, because a negative scale inverts triangle winding and block render
types cull back faces.

`StructurePreview` decides what is drawn: a representative block state per position (with the definition's
blockstate properties applied, or every casing variant would look identical), plus the controller at the
origin, which the pattern itself never contains.

> ⚠️ The rendering has not been seen. The structure data and the box it is fitted into are tested; the picture
> is not. See the release notes for what to check first.

## Blueprints

A blueprint names the machine it was made for, stored in NBT under `dynamicmachine` — the same key the original
used, so blueprints are interchangeable between the two. The creative tab lists **one blueprint per registered
machine**, as the original did, and `/mm-get_blueprint <machine>` hands one out (permission level 2).

In a controller's slot a blueprint does two things:

- It makes the controller try **that machine first**, before its own bound machine and before the general
  search. Slotting an alloy furnace blueprint into a `transformer_controller` therefore turns it into an alloy
  furnace if that structure matches — the original's behaviour, reproduced rather than corrected.
- For a machine whose definition sets `requires-blueprint`, it is the **only** way the machine can form: those
  definitions are skipped by the general search, which is the whole meaning of the field. Take the blueprint out
  and the machine dissolves.

`requires-blueprint` is opt-in. None of the original's built-in machines set it, so none of this mod's do.

## Hatch screens

Right-clicking a hatch opens it. Every hatch is a 176×166 window with the player's 3×9 inventory at (8, 84) and
the hotbar at (8, 142) — the original's `uontainerBase` layout.

Item hatches select `inventory_<tier>.png`, whose slot holes are already cut for that tier, so **the slot
coordinates and the texture are one specification** and are copied from the original `uontainerItemBus`:

| Tier | Slots | uoordinates |
|---|---|---|
| tiny | 1 | (81, 30) |
| small | 4 | (70,18) (88,18) (70,36) (88,36) |
| normal | 6 | 3×2 grid from (61, 18) |
| reinforced | 9 | 3×3 grid from (61, 13) |
| big | 12 | 4×3 grid from (52, 18) |
| huge | 16 | 4×4 grid from (53, 8) |
| ludicrous | 32 | 8×4 grid from (17, 8) |

Fluid and energy hatches share `guibar.png`, which carries the frame (u = 176) and the fill (u = 196) for the bar
at (15, 10, 20×61). Energy fills it bottom-up; fluid draws the fluid's own atlas texture tinted with its colour
and then lays the frame over it. Hovering shows the amount, and clicking the tank with a container in hand fills
or empties it.

## Controller screen

The controller opens the original panel: `textures/gui/controller_legacy.png` — byte-identical to the original
`guicontroller_large.png` — blitted whole at a **176×213** window, source rectangle (0, 0, 176, 213). There are
no progress or energy bars because the original had none; the controller reports everything as text.

| Item | Value |
|---|---|
| Blueprint slot | one, at (151, 8), blueprints only |
| Player inventory | 3 × 9 at (8, 131) |
| Hotbar | y = 189 |
| Text | scale 0.72, origin (12, 12), wrapped to 135 px, line height 10, white with shadow |

The line order is ported from the original method line for line, including its uneven gaps. Statuses are the
original five (`missing_structure`, `chunk_unloaded`, `no_recipe`, `idle`, `crafting`), and a strongly powered
controller prints only the redstone line and stops working. Addons can append lines through
`api/ControllerGuiInfoEvent`, the replacement for the original's `ControllerGUIRenderEvent`.

The controller has **one slot and no capabilities**, as in the original: everything a machine consumes or
produces goes through a hatch.

## Controllers

Two flavours, matching the original's two registration modes:

- `machine_controller` — generic. It finds out which machine it is by matching every registered definition
  against the blocks around it.
- `<machine>_controller` — bound to one machine, and only ever checks that machine's pattern, so it cannot
  become a different machine. The original's `Config.onlyOneMachinecontroller` defaulted to `false`, meaning
  it registered a separate controller per machine; these are that default side.

**The mod ships no controllers of its own**, because it ships no machines. A bound controller exists only for a
machine that is declared in `config/modular_machinery_reborn/machinery/`, where a declaration may be either a
full definition or a bare **controller claim** (`{"registryname": "..."}`) whose definition stays in a data
pack. See **Machine definitions** below.

A bound controller is named after its machine through `%s Controller` / `%s控制器`, using the machine's display
name. Machine display names are translatable via `modular_machinery_reborn.<path>`.

Bound controllers get their blockstate and item model from a synthetic resource pack generated at startup, since
the jar cannot ship files for machines it does not know about.

### The `modularcontroller` compatibility namespace (0.27.0)

The original MMCE absorbed the older **ModularController** mod and kept a controller block registered under the
**`modularcontroller`** namespace, so a save made with that mod still finds its block. This port does the same:
for every declared machine that is not `factory-only`, it registers

- a block `modularcontroller:<machine path>_controller` — the **ordinary** `MachineControllerBlock` class and the
  **ordinary** `MachineControllerBlockEntity`, so an old save's block keeps working with **no tile migration**; and
- an item of the same name, which shows the original's two deprecation lines.

`factories` get no compatibility controller, mirroring the original's `isFactoryOnly` guard
(`RegistryBlocks.java:410-412`).

**These blocks are registered unconditionally, and the reason is not a style choice.** Forge fires the block
registry event (`RegisterEvent`) from its `LOAD_REGISTRIES` mod-loading state, and it reads config files in
`CONFIG_LOAD`, which runs *after* it (`ForgeStatesProvider.java:21-25`, `ModStateProvider.java:63-69`). While
blocks are being registered, `ForgeConfigSpec.isLoaded()` is therefore still `false` and every config accessor
answers with its pre-config fallback. Nor could it be otherwise: a save deserialises a placed block by its
registry id, so the id has to exist *before* the game can open the world in which the flag would have been read —
and 1.20.1 cannot migrate a placed block across namespaces. Always registering is the only shape that works, and
it keeps the block set a pure increment that depends on no config value.

The two config keys live in the mod's single spec:

| Key | Default | Live? |
|---|---|---|
| `general.modular-controller-compatible-mode` | `false` | **No.** Kept only because the original had it; its own comment says so and says why. |
| `general.disable-moc-deprecated-tip` | `false` | **Yes.** Read while the tooltip is built, long after the files are loaded. |

The compatibility blocks' blockstate and item model come from the same synthetic resource pack, under
`assets/modularcontroller/`, because a resource file's namespace is the directory its id names. The items are
**not** in the creative tab: they exist so an old save keeps working, not to be handed out, and a fresh install
therefore looks unchanged. A compatibility controller is an **alias**, not a second controller — it is not
enumerated as an additional JEI catalyst, structure-preview controller, or generated file.

## Recipes

Recipes are requirement lists, as in the original:

```json
{
  "type": "modular_machinery_reborn:machine",
  "machine": "alloy_furnace",
  "registryName": "alloy_furnace_diamond",
  "recipeTime": 5,
  "requirements": [
    { "type": "modularmachinery:energy", "io-type": "input",  "energyPerTick": 100 },
    { "type": "modularmachinery:item",   "io-type": "input",  "item": "minecraft:coal", "amount": 32 },
    { "type": "modularmachinery:item",   "io-type": "output", "item": "minecraft:diamond", "amount": 1 }
  ]
}
```

> ⚠️ The root `type` is **not optional**. Vanilla's `RecipeManager` reads it to pick a serializer and drops the
> file before this mod sees it otherwise. The original mod loaded its own recipes, so its schema has no `type` —
> a recipe carried over from it must have one added. The root `type` is `modular_machinery_reborn:machine`; the
> `type` inside each requirement is the requirement kind.

- Requirement `type` accepts either namespace, so original files keep their `type`/`io-type` fields. Kinds:
  `item`, `fluid`, `energy`.
- Items may be a plain id, an item tag (`#forge:ingots/iron`) or a mapped legacy ore dictionary name
  (`ore:ingotIron`). Unmapped ore names and 1.12.2 `@meta` syntax are load errors, not guesses.
- `energyPerTick` is a **rate**: energy and per-tick fluids are drawn every tick, item and fluid **inputs are
  paid when the craft begins**, and outputs are produced at the end with **each output's `chance` rolled
  independently**. `minAmount`/`maxAmount` are honoured on outputs.
- Nine built-in recipes from the original `default_recipes` are ported. Recipe adapters are not.

## Hatch ports

Hatches hold the machine's resources and expose the Forge capabilities, as in the original — a machine's
inventory, tank and energy buffer are the sum of its hatches. uapacities come straight from the original tier
enums:

| Family | Tiny | Small | Normal | Reinforced | Big | Huge | Ludicrous | extra |
|---|---|---|---|---|---|---|---|---|
| Item bus (slots) | 1 | 4 | 6 | 9 | 12 | 16 | 32 | — |
| Fluid hatch (mB) | 100 | 400 | 1000 | 2000 | 4500 | 8000 | 16000 | vacuum 32000 |
| Energy hatch (FE) | 2048 | 4096 | 8192 | 16384 | 32768 | 131072 | 524288 | ultimate 2097152 |

The controller walks the **positions of the formed pattern** to gather its ports, so hatches outside the
structure do not count. Crafting draws items from item input hatches and FE from energy input hatches, and
writes results to item output hatches. uontents persist across a world reload.

## Machine definitions

Machines are data-driven, as in the original. There are two sources, with the same JSON schema:

| Source | Path | Dedicated controller |
|---|---|---|
| Config directory — definition | `config/modular_machinery_reborn/machinery/` | **yes** |
| Config directory — claim only | `config/modular_machinery_reborn/machinery/` | **yes**, definition may stay in a data pack |
| Data pack | `data/<namespace>/machinery/*.json` | no — generic `machine_controller` only |

**Use the config directory if you want the machine to have a controller of its own.** The directory is scanned
during mod construction, which is the only moment a block can be registered; a data pack is read afterwards and
can therefore never grant a block. When the same `registryname` appears in both, the config directory wins.
Variable sets go in either place (`variables/*.var.json`, or `data/<namespace>/machinery/variables/`).
See [examples/pack-author](examples/pack-author) for a copyable setup.

A file **with** `parts` is a definition; a file **without** `parts` is a **claim**, which registers
`<registryname>_controller` and leaves the definition wherever it is:

```json
{ "registryname": "alloy_furnace" }
```

That is how a data pack machine gets a dedicated controller. The bundled example machines use it — see
[example-datapack/claims](example-datapack/claims).

Because blocks are registered at construction, a machine added to the directory **after** launch needs a restart
to gain its controller; `/reload` alone loads the definition. The log states which machines have no controller
of their own, and warns about a claim whose machine never loaded.

### Recipes from the config directory

`config/modular_machinery_reborn/recipes/**` is also a source, mirroring the original's
`config/modularmachinery/recipes/`. Recipes in 1.20.1 can only come from a `RecipeManager` reload, so the
directory is exposed **as** a data pack rather than through a second loading path: a file at
`recipes/alloy_smelter/diamond.json` becomes the recipe id `modular_machinery_reborn:alloy_smelter/diamond`, and
editing it is picked up by `/reload`.

```json
{
  "registryname": "transformer",
  "localizedname": "Power Transformer",
  "failure-action": "reset",
  "requires-blueprint": false,
  "parts": [
    { "x": 1, "y": -1, "z": 0, "elements": "minecraft:stone_bricks" },
    { "x": 0, "y": 0, "z": 2, "elements": "casings_decorative" }
  ]
}
```

- uoordinates are relative to the controller, which occupies the origin and is excluded. `x`/`y`/`z` accept a
  number or an array of numbers (an array expands to the uartesian product of positions); an omitted
  coordinate is `0`. Any shape and any number of Y levels are supported.
- `elements` accepts a string or an array, and may name a variable from a `<name>.var.json` file.
- Patterns are matched with the controller's facing applied, so a structure must agree with its controller's
  orientation.
- Matching is *block + blockstate properties*. The original used *block + metadata*; since 1.20.1 has no
  metadata, `ns:path` accepts any state of the block and `ns:path[key=value]` narrows it. The 1.12.2 `@meta`
  syntax is rejected with an explanatory error instead of being guessed.

Three built-in machines are ported: `power_transformer` (32 parts, **3×5×3 across five Y levels**),
`iron_centrifuge` and `alloy_furnace` (26 parts each, 3×3×3). `assembly_line` is not ported because it depends
on `dynamic-patterns`, which is not implemented.

### Ingredient arrays (0.26.0)

`ingredient_array_input` is the original's "consume exactly one of this group" requirement
(`RequirementIngredientArray`). The entries are **alternatives, not a shopping list**:

```json
{ "type": "modularmachinery:ingredient_array_input", "io-type": "input",
  "items": [ { "item": "minecraft:diamond", "amount": 2 },
             { "item": "#forge:gems/emerald" } ] }
```

| Field | Where | Meaning |
|---|---|---|
| `items` | requirement | **Required**, non-empty. Each entry is `{"item": …}` — an item id, a `#tag`, or a mapped legacy `ore:` name. |
| `amount` | requirement **or** entry | How many of the chosen item one copy consumes. An entry's own value wins; otherwise the requirement-level value applies; default 1. Capped at 64. |
| `chance` | requirement | Probability the group is consumed at all, rolled **once per settlement**. Default 1. |
| `io-type` | requirement | Must be `input`. The original's JSON reader could only ever build an input; for several possible outputs, list one `item` output per stack. |

- Entries are tried **in array order**: the first one takes as many copies as it can and later ones only top up
  what is left, so an earlier entry is the preferred one.
- The requirement **bounds parallelism**, exactly as the original's `Parallelizable` class did.
- One deliberate divergence from the original's code: an entry's own `amount` is **honoured** (`D19`). The
  original read `amount` off the requirement while its own documented example wrote it inside each entry, so
  that example silently consumed 1 per entry. `chance` inside an entry is refused, because a per-entry chance was
  never implemented — it would be a new feature, not a port.

### `item_durability` and `catalyst` are not missing features (0.26.0)

They were listed as unported requirement types. They are not gaps, and re-opening them as one would be a
mistake (`D18` in `移植方案-v2.md`):

- **`item_durability`**: the original registered the type but its factory returned `null`
  (`RequirementTypeItemDurability.java:12-14`) and **nothing ever constructed it**. Durability consumption *is*
  implemented in the original, as a **CraftTweaker-only** field on `item`
  (`RequirementItem.consumeDurability` + `ItemUtils.damageAll`, `RecipePrimer#consumeDurability`). It has **no
  JSON field name anywhere**, so adding one here would be inventing a schema.
- **`catalyst`**: the original never registered the type at all, and its only constructors were the CraftTweaker
  bridge. Its real behaviour is a **permanent modifier source on the settlement path**, not a requirement type —
  a different mechanism from any requirement in this port.

### Failure actions (0.25.0)

`failure-action` is a root field of the machine definition and one of three strings — the original's
`RecipeFailureActions`:

```json
{ "registryname": "alloy_furnace", "failure-action": "decrease", "parts": [ /* … */ ] }
```

| Value | What one tick that cannot be served costs the craft |
|---|---|
| `"still"` | nothing — the progress bar holds and the craft resumes when the resource is back. **The default**, and what this mod did before the field was consumed, so omitting it changes nothing. |
| `"decrease"` | one tick of progress (floored at 0). |
| `"reset"` | the whole progress: it starts over. |

It fires **once per failed tick** of a craft that has already started — the original's
`ActiveMachineRecipe#tick:87-98` applied it whenever the per-tick IO check failed, and both this mod's engines
call the same arithmetic. It is **not** consulted when a craft cannot *start*, nor when its result does not fit
(the original checked those on separate paths), so a machine that never fails behaves identically whichever
value it declares.

**One deliberate difference from the original.** The original's reset left the craft armed at progress 0, so it
cost time and never a second set of inputs. This mod's single-craft engine treats `progress == 0` as "no
craft", so a reset ends the craft and it restarts through the normal start phase — which pays its one-shot
inputs again. A factory thread is armed by its slot rather than by its progress and therefore stays armed, as
the original's did. Recorded as D17 in [移植方案-v2](移植方案-v2.md).

A malformed value is a load error that says what to write (a non-string, or a name that is not one of the
three); the original silently turned an unknown *name* into the default.

## Hatch tier model

Hatch tiers follow the original mod exactly: **one block per family** carrying a `size` blockstate property,
plus **one block item per tier**.

- 6 hatch blocks — `item_input_hatch`, `item_output_hatch`, `fluid_input_hatch`, `fluid_output_hatch`,
  `energy_input_hatch`, `energy_output_hatch`.
- 46 tiered block items — 7×2 item, 8×2 fluid, 8×2 energy.

The ladders mirror the original `ItemBusSize` / `FluidHatchSize` / `EnergyHatchData` enums, values included:

| Family | Tiers |
|---|---|
| Item bus | 7 — tiny(1) small(4) normal(6) reinforced(9) big(12) huge(16) ludicrous(32) |
| Fluid hatch | 8 — the item ladder plus vacuum(32000 mB) |
| Energy hatch | 8 — the item ladder plus ultimate(2097152 FE) |

The item bus deliberately has **no** ultimate tier: the original has seven tiers and the source contains no
ultimate art. See [注册表对照](注册表对照.md) for the full old → new registry mapping.

> ⚠️ 0.5.0 is a breaking change: the 52 hatch block ids of 0.4.x are gone. The 46 tiered *item* ids are
> unchanged, so re-place hatch blocks with the same item.

## Current state

- **The mod contains no content.** No machines, no recipes, no controllers of its own — a fresh install is the
  framework: the generic `machine_controller`, six casing variants, six hatch families, and the standalone
  items. uontent is installed as a data pack; see [example-datapack](example-datapack).
- `modular_machinery_reborn:machine`, the recipe type. Its `machine` field resolves to a registry name and is compared against the machine actually formed at the controller. See **Recipes** above for the schema.
- Optional KubeJS 6 schema: `event.recipes.modular_machinery_reborn.machine({...})`.
- The controller's structure is defined by the data pack, not hardcoded; see below.
- `blockcasing` with six variants, plus the six hatch families in the original tiers (7×2 item, 8×2 fluid, 8×2 energy).
- The block entity exposes Forge item, FE, and fluid capabilities and drives recipes through the requirement lifecycle described under **Recipes**.
- The controller screen is the original panel; see **Controller screen** above. JEI lists all loaded machine recipes with every controller as a catalyst.
- The controller has one slot (the blueprint) and exposes no capabilities, so machines reach items, energy and fluid only through hatches.
- The controller front follows the placement direction; every tiered hatch model has a valid texture.
- Standalone 1.12.2 items are registered with their legacy textures: blueprint, modularium, construction tool, machine projector, redstone signal, and wrench.

## Parallel controllers

The original's "parallelism" is **not** a thread pool: it is a number on the machine, and every requirement
moves that many copies of its own amount per settlement. Its ceiling comes from
`max(1, min(max-parallelism, internal-parallelism + Σ parallel controllers))` — the original's
`TileMultiblockMachineController#getMaxParallelism`. A machine with neither field runs exactly one copy per
craft.

The **parallel controller** block comes in the original's five tiers — normal 4, reinforced 16, elite 64,
super 256, ultimate 512 — one block with a `type` property and one block item per tier, named with the
original's own `zh_CN`/`en_US` strings. Right-clicking opens the original's panel
(`guismartinterface.png`, reproduced byte for byte, 176×166): its heading, its "max parallelism" and
"current parallelism" lines, six ±1/±10/±100 buttons and a text field. A newly placed controller starts at
its maximum; `0` is legal and means "this controller contributes nothing". The values reach the screen through
the menu's `ContainerData`; the write direction is the mod's only custom packet, because the vanilla menu
button channel encodes its id as a single signed byte and cannot carry a value up to 2048.

A controller only counts if it stands **at a position the machine definition accepts** — this is the
original's behaviour too (of its shipped machines, only `assembly_line` listed
`modularmachinery:blockparallelcontroller`). The example data pack's alloy smelter accepts one at the centre
of its roof, and carries `alloy_smelter_parallel_demo` (1 cobblestone → 1 stone) so the effect can be counted:
with no controller it yields 1 stone per craft, with an elite controller 65 from 65 cobblestone. The per-tier
ceilings are overridable in `config/modular_machinery_reborn-common.toml`, as the original's config file did.

## The smart data interface

The original's 智能数据接口 was a **separate structure block** whose value a machine controller read. It is not
ported as a block here: its value lives on the controller's own block entity and is edited in the controller's
own screen. That is a deliberate divergence from the original's block list (recorded as `D16` in
`移植方案-v2.md`) and it costs nothing at the access path, because the original's only readers were already
controller methods.

A machine declares one or more interface types in its definition — the field the original *never had*, since its
types could only be registered by a CraftTweaker script:

```json
"smart-interfaces": [
  { "type": "mode", "default": 0, "priority": 1000,
    "header": "gui.example.mode.header", "value": "mode=%s", "footer": "", "notequal": "gui.example.mode.bad" }
]
```

Everything but `type` is optional. A recipe then gates itself on the value:

```json
{ "type": "modularmachinery:interface_number_input", "io-type": "input",
  "interface": "mode", "minValue": 10, "maxValue": 20 }
```

The value is compared inclusively on both ends, nothing is consumed, and a mismatch refuses the craft with the
original's own message (`craftcheck.failure.interface.number.notequal` — 「智能数据接口输入的数值不同！」, or the
type's own `notequal`); a machine that declares no such type reports
`component.missing.modularmachinery.interface.number` instead. Both messages and the original's three
`tooltip...smartinterface.*` lines are reused verbatim, and the failure is printed on the controller's screen
rather than reduced to a bare `idle`.

The original's **string** type is deliberately not built: its enum `SmartInterfaceTypeEnum` declares only
`NUMBER`, and the only reader of an interface value anywhere in the original is `RequirementInterfaceNumInput`, a
number input. There was nothing that could read a string.

## Localization

All uhinese and English display names are aligned to the original Modular Machinery: Community Edition
official naming (see [_audit/08-语言命名核查.md](../_audit/08-语言命名核查.md)): 48 tiered hatches in the
original `<tier><type>` order, 微型物品输入仓 / `Tiny Item Input`, `itemmodularium` as 模块化合金锭,
`itemconstructtool` as 建造选择工具 / `uonstruct Selection Tool`, and a localized creative tab.

## Known limitations

This is a foundation slice, not a feature-complete migration. Not yet implemented:

- **Not every requirement kind is ported.** Missing: `gas` / `gas_pertick` (both need the Mekanism gas API this
  port does not have; the original gates them behind `requiresModid() = "mekanism"`). **`fluid_pertick` is not a
  gap**: the original's JSON entry point for that kind (`RequirementTypeFluidPerTick#createRequirement`) returns
  `null`, so only CraftTweaker could build it — per-tick fluid is expressed here as `fluid` + `"perTick": true`,
  which **is** implemented. `ingredient_array_input` was added in 0.26.0. `item_durability` and `catalyst`
  are **not** missing kinds — neither is a usable requirement type in the original, and re-opening them as one
  would mean inventing a JSON schema; see the section above and D18. An unknown kind is a load
  error rather than a silently skipped requirement. Recipe adapters (`adapter` + `modifiers`) are not
  implemented.
- **A craft that cannot pay a per-tick resource now obeys the machine's `failure-action`** (0.25.0). `"still"`
  — the default, and what this mod did before the field was consumed — holds its progress; `"decrease"` loses
  one tick of progress per failed tick; `"reset"` drops the whole progress. **One deliberate difference from
  the original:** the original kept a reset craft armed at progress 0, so it cost the time already spent and
  nothing else; this mod's single-craft engine treats `progress == 0` as "no craft", so a reset ends the craft
  and it restarts through the normal start phase, which pays its one-shot inputs again. A factory thread is
  armed by its slot rather than by its progress, so there it stays armed exactly like the original's. See D17
  in [移植方案-v2](移植方案-v2.md).
- **`failure-action` cannot be seen on a machine that never fails** — it is consulted only on a tick that
  cannot be served, and neither a failed start nor a craft whose result does not fit reaches it (the original
  checked those on separate paths too).
- **No message accompanies that failure yet.** The controller shows `idle`; the original's
  `craftcheck.failure.*` wording for per-tick shortfalls is still outstanding (the smart data interface's two
  messages were done in 0.24.0).
- **No Mekanism gas in the fluid hatch.** The original's fluid hatch could also hold Mekanism gas and showed a
  `[Gas]` tooltip; that belongs to the compatibility layer. The energy tooltip is also fixed to FE, where the
  original let a config choose FE / RF / Iu2 EU / GT EU.
- **`dynamic-patterns` is not implemented**, so variable-size structures (and therefore the `assembly_line`
  machine) are missing. `selector-tag`, `nbt`, `color`, `hide-components-when-formed` and
  `controller-bounding-box` are recognised but not evaluated, and the loader
  warns about them.
- The requirement engine still lacks gas and the recipe adapters; **per-tick fluid is implemented** (`fluid` +
  `"perTick": true`), and `item_durability` / `catalyst` are deliberately absent for the reason given above.
  Chance outputs, min/max amounts and the one-of-a-group input are implemented.
- **The structure editing tools.** `machine_projector` is a registered item without behaviour (the owner
  deferred the projector). **`ItemDebugStruct` is deliberately not ported** (owner's decision, 2026-10-04):
  the original **never registered it** — the whole code base mentions it only in its own class file, and it has
  no texture, model or language key — and its implementation is broken (it stays silent when the structure
  actually matches, and its four-orientation loop never rotates the world position, so three of the four
  reported positions are meaningless). **`itemconstructtool` is no longer inert** — 0.29.0 gave it the
  original's selection → machine-JSON export (see the section above). The blueprint is not inert either: it
  binds a machine and opens the structure preview, **and that preview's button row is reproduced** — layer
  toggle, reset centre, the per-layer 2D view and the layer scrollbar all shipped in 0.16.0
  (`releases/modular-machinery-reborn/v0.16.0/README.md`).
- The upgrade bus and factory controller systems. (The **parallel controller** shipped in 0.20.0 — see
  **Parallel controllers** above — and the **smart data interface** in 0.24.0.)
- All mod integrations (AE2, GregTech, Thaumcraft, TConstruct, Botania, and others — 26 blocks in the original).
- **Per-requirement failure text.** The controller shows `idle` when a machine is waiting on a resource instead
  of the original's `craftcheck.failure.*` message naming the shortfall — except for the smart data interface,
  whose two messages it does print (0.24.0). The original's performance footer has an equivalent since 0.19.0.

The original archive is retained at `../ModularMachinery-Community-Edition-master.zip`. The old project
targets Forge 1.12.2/cleanroom APIs, so its much larger collection of dynamic shapes, ports, specialized
components, and legacy integrations cannot be copied into Forge 1.20.1 wholesale.

## In-game checklist (0.26.0): `ingredient_array_input`

This version adds a fifth requirement kind, which puts new code on the **JEI recipe page** and on the machine's
**start phase**. The offline harness proves the arithmetic (how many items the group moves, how the copy count is
computed, whether a group that can serve only one entry starts) but it cannot see the page or the machine
screen. Until this list has been walked through once, this version is *built, not verified in game*.

Use this recipe (drop it in `config/modular_machinery_reborn/recipes/` or a datapack, then `/reload`):

```json
{
  "type": "modular_machinery_reborn:machine",
  "machine": "alloy_furnace",
  "registryName": "array_smoke_test",
  "recipeTime": 40,
  "requirements": [
    { "type": "modularmachinery:energy", "io-type": "input", "energyPerTick": 20 },
    { "type": "modularmachinery:ingredient_array_input", "io-type": "input",
      "items": [ { "item": "minecraft:diamond", "amount": 2 },
                 { "item": "minecraft:emerald", "amount": 2 } ] },
    { "type": "modularmachinery:item", "io-type": "output", "item": "minecraft:stone", "amount": 1 }
  ]
}
```

1. **The JEI page does not crash.** Search the recipe in JEI and open it. Before 0.26.0 an array input was cast
   to `ItemRequirement`, which threw `ClassCastException` on the page; there is a dispatch now, and this is the
   one check that needs eyes.
2. **JEI shows a group.** The input cell holds **both** diamond and emerald (not just one), and hovering prints
   "Accepts the following inputs (only one of them is consumed):". The counts match the recipe's `amount`.
3. **Only one of the group is consumed.** Put **2 diamonds + 2 emeralds** in an item input hatch and run the
   machine: **the 2 diamonds disappear and the 2 emeralds stay**, producing 1 stone.
4. **One payable entry is enough.** Put only **1 diamond + 2 emeralds**: the machine still starts, consuming
   **2 emeralds and leaving the 1 diamond**.
5. **Neither payable means no start.** Put **1 diamond + 1 emerald**: the machine does not start and neither
   item is taken.
6. **It really bounds parallelism.** With parallelism available (a parallel controller or `max-parallelism`
   `8`), put **4 diamonds + 2 emeralds**: the interface's parallel count should read **4** (2 copies from the
   diamonds, the other 2 topped up by the emeralds), and a run consumes **4 diamonds + 2 emeralds**. Reading 8
   means the arithmetic is wrong.
7. **Bad JSON is refused loudly.** Delete `items`, write `[]`, or drop `"item"` from one entry: `/reload` should
   print one explicit message saying what to write, and the recipe must not take effect.
8. **An entry's own `amount` is honoured.** Write `"amount": 2` inside both entries (the original's own
   documented form): a run should consume **2** of the chosen item, not 1 (`D19`).

## In-game checklist (0.27.0): the `modularcontroller` compatibility namespace

The offline harness proves the registry names, the namespace, the generated file shapes, the two tooltip keys and
the config key paths. It cannot prove what a block looks like or whether a save opens, so please check once:

- [ ] **An old save opens.** Load a world that contains a `modularcontroller` block. The world loads — no crash,
      no lost blocks — and the log carries the line about controllers registered in the compatibility namespace.
- [ ] **The old block renders.** Look at it: it draws normally (not the purple/black missing model) with the same
      facing it had.
- [ ] **The old block still works.** Right-click opens its screen; re-forming the structure works and it crafts,
      exactly like an ordinary controller.
- [ ] **Two deprecation lines.** Hover the controller item: the two lines from the original appear
      (`模块化控制器兼容将会在未来的版本被移除。` / `请联系整合包作者将控制器转移至 MMCE 的控制器中。`, or the original's
      English wording on an English client).
- [ ] **The tip can be silenced.** Set `disable-moc-deprecated-tip = true` under `[general]` in
      `config/modular_machinery_reborn-common.toml`, restart, and the two lines are gone.
- [ ] **The inert key says so.** `modular-controller-compatible-mode` is present with a comment explaining that it
      cannot gate anything on 1.20.1 and that the compatibility items are not in the creative tab. Toggling it and
      restarting changes **nothing** — that is the expected behaviour, not a bug.
- [ ] **The creative tab is unchanged.** No extra controllers appear in the mod's creative tab.
- [ ] **JEI has no duplicate.** The machine's JEI category lists its controller **once**.

## Build with Java 17

```powershell
$env:JAVA_HOME = "c:\Program Files\Microsoft\jdk-17.0.11.9-hotspot"
$env:GRADLE_USER_HOME = "u:\Users\kk07H\Desktop\模组\.gradle-home"
./gradlew.bat clean build --offline --no-daemon
```

## Documentation

- [移植方案-v2](移植方案-v2.md)：现行移植方案（关键设计决策、原版 GUI 复刻规格、M0–M7 里程碑）。
- [注册表对照](注册表对照.md)：旧版 1.12.2 → Reborn 的注册名逐项对照。
- [交接文档](交接文档.md)：代码入口、运行逻辑、配方接口、构建发布和已知限制。
- [迁移日志](迁移日志.md)：逐版本变更与发布 SHA-256。
- [移植计划](docs/历史/移植计划.md)：早期分阶段计划（已被 v2 取代，保留作对比）。

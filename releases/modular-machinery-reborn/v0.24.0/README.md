# Modular Machinery: Community Edition Reborn

**Release 0.24.0 — M6d-b: the smart data interface, merged into the controller. This completes M6 (M6a–M6e + M6d-b).**

## D16 — honouring the owner's decision

The original's 智能数据接口 is **not** ported as a block. Its value lives on the controller's block entity (`SmartInterfaceStore`) and is edited in the controller's screen.

This is the project's **first deliberate divergence from the original's *block list*** — D12–D15 diverged in schema, not in block set. The justification is that the controller **already was the only reader-facing entry point**: `foundSmartInterfaces` (`TileMultiblockMachineController:135`) → `checkAndAddSmartInterface` (`:909-938`) → `getSmartInterfaceData` (`:1113`) / `getSmartInterfaceDataList` (`:1128`), and the sole consumer `RequirementInterfaceNumInput` receives the controller's `ProcessingComponent`. Moving the value from a block entity to the controller's block entity and the screen from a separate screen to the controller's screen **changes no access path** — one intermediate block simply disappears.

Rejected fallback, recorded: copying the separate block would add a block registration, a structure scan and a per-20-tick `boundData` reconcile while **adding no capability**.

## The string type is NOT built — on evidence, not assumption

1. The original's `SmartInterfaceTypeEnum` is 12 lines: `enum SmartInterfaceTypeEnum { NUMBER; }` — **no STRING constant exists**.
2. The only reader of any interface value anywhere in the original is `RequirementInterfaceNumInput` (grepping `getSmartInterfaceData` finds only the controller interface and the controller's own definitions).
3. That reader's two fields are `float minValue` / `float maxValue` — meaningless for a string.

So `blocksmartinterface_string`'s models, textures and `tile...blocksmartinterface.string.name` are **dead assets in the original**. Not ported; recorded in D16 and the README.

## The machine-JSON field (fourth schema divergence)

```json
"smart-interfaces": [ { "type": "mode", "default": 0, "priority": 1000,
                        "header": "…", "value": "…", "footer": "…", "notequal": "…" } ]
```

`type` is required and must be unique; the rest are optional. In the original these types were **CraftTweaker-only with no JSON entry point**, so this had to become data. Every rejection says what to write instead — e.g. a duplicate: *"Two smart interfaces of X are both named 'a'. The name is how a requirement finds the value, so a duplicate would make one of them unreachable — rename one of them."*

## `interface_number_input` and its failure reporting

`InterfaceNumberInputRequirement` consumes nothing — it is a pure gate, inclusive at both ends (`value >= min && value <= max`, the original's `:82`). A missing type reports `component.missing.modularmachinery.interface.number`; an out-of-range value reports the declared type's `notequal` or `craftcheck.failure.interface.number.notequal`. All three message keys and three tooltip keys are reused **verbatim** in both languages. Parallelism mirrors the original: the class does not implement `Parallelizable`, so it never bounds the copy count.

**A notable original fact, recorded:** the original's JSON reader could *never* produce this requirement — `RequirementTypeInterfaceNumInput#createRequirement` returns `null`, and only the CraftTweaker bridge could build one, because the interface type name collided with the requirement kind field (also called `type`). This project reads the kind first and the type under `interface`, so the collision is impossible. **This is the first release in which that requirement can appear in JSON at all.**

`MachineRequirement#startFailure` / `MachineRecipe#startFailure` were added so the controller prints **which** requirement refused instead of a bare `idle`.

## Acceptance — 503 PASS / 0 FAIL, 23 sections

Evidence: **`_audit/m6c-verify/acceptance-0.24.0-m6db.txt`**, counts read back from that file on disk. Sections A–R are unchanged; **S/S2/S3/S4** add 151 checks.

It proves quantities that moved, driven in `tick`'s order: ports carry the store's value (12.5); an out-of-range value refuses to start **and says why** (0 coal consumed, 0 diamonds produced); after the edit **the same recipe runs** (1 coal, 1 diamond); changing it back out of range stops it again; NaN/infinity and undeclared types are refused; an NBT reload restores the **edited** value rather than the default; a machine declaring no type cannot use one; inclusive ends; a value whose type the new machine dropped is removed; and the packet round-trips 0 / −1.5 / 3.25 / 12345.75 / MAX / MIN.

## The five mandatory checks

1. **SRG reobfuscation** — build product **and** deployed file: official name 0 hits, SRG `f_NNN_` 1 hit.
2. **Build order** — and this time the trap **fired for real**: a second harness run (for provenance) rewrote `build/libs` back to un-reobfuscated. It was caught **by hash**, then rebuilt, re-checked and redeployed. Recorded in the changelog as a live instance of the 0.19.0 cause.
3. **One config per type** — one real `.registerConfig(`, one `new ForgeConfigSpec.Builder(`; 2 `registerMessage(` (id 0 parallel controller, id 1 smart interface). Config key added to the existing spec.
4. **Harness rot** — it failed to compile twice and two probes hit genuinely unreachable paths; all four were harness problems, fixed before any conclusion was drawn about the mod.
5. **Key paths from the rendered spec** — 13 keys (12 + `smart-interface.enable-smart-interface-bydefault=false`), with a no-repeated-path-element check and a full Forge read round-trip of the user's config file, on a copy.

`mods.toml` 0.24.0; **zero** files under `data/`; language keys **166 each, identical sets**. Jar SHA-256 `27585A31B13C5F3FFA7D1B4290A757DBDC09A80ABA026A87239E3C46B70F7483`, build product and deployed file identical. `StructurePreviewRenderer.java` unmodified (mtime still 01:16); preview `getArea()` and `PointerClaim` unchanged.

## Not verified — needs the user's eyes

The controller screen's new interface row and `EditBox` position, typing and Enter actually sending the value, the menu `ContainerData` index 10 crossing the wire, and `SmartInterfaceUpdatePacket`'s real client→server path are all unverified — no client, no server, no pixels. Client reception of the value (it rides the block entity's update tag, same channel as the factory thread list) is unverified on a dedicated server. Two paths are **provably unreachable offline** and were replaced by source-level assertions rather than papered over: `ModRecipeSerializers` holds a `DeferredRegister` whose static init needs `net.minecraftforge.fml.common.Mod` (hence the parser was extracted into the registry-free `InterfaceNumberInputParser`), and `ModNetwork` / `MachineRecipeText` need FML and JEI on the classpath.
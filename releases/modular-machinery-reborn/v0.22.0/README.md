# Modular Machinery: Community Edition Reborn

**Release 0.22.0 — M6e-1: the factory controller's semantics and registration. (Its GUI, textures and config are M6e-2.)**

## What "the factory has threads" actually means

The original's own class comment says *「不是真正意义上的线程 / Not a thread in the true sense of the word」*, and **the code agrees**: `FactoryRecipeThread` is entirely state — the only loop in it is the `recipeSet.add` in `addRecipe`, and `onTick()` forwards to `activeRecipe.tick`. There is no `Thread`, no `Executor`, not even a `volatile`. The name is a metaphor for "independent progress bar".

The distinction that matters, established earlier in M6a: the original's **parallelism is a numerical reduction** (settling N copies of one recipe), while the **factory runs several different recipes at once**. They are two different features, and this release implements the second as a synchronous slot machine in a new pure package `com.reborn.modularmachinery.factory` — which imports no `Level`, and that is what makes offline proof possible at all.

## D15 — the registration decision

Per-machine factory controllers need a block that exists at **mod construction**, while `has-factory` is declared in a machine definition that loads later and is reloadable. **This project already solved exactly that shape** for ordinary controllers: the config-directory claim mechanism (D9/D10). So a claim file may now carry **one extra field**:

```json
{ "registryname": "example_factory", "has-factory": true }
```

That single bit decides whether `<machine>_factory_controller` is registered. **No new mechanism, no new directory, no new config, and `/reload` is untouched** — only "does this machine have a factory block" is construction-time, and it always was.

The other half lives on the machine definition: `FactoryThreadModel.factoryEnabled(blockIsFactory, definition)` = block-is-factory ∧ definition-has-factory, so *"a machine without `has-factory` cannot use a factory controller"* is one conjunction the harness asserts across all four block×machine combinations. A data-pack-only machine that asks for a factory gets a log line naming the file to create and its exact contents.

`factory-only` is parsed and consistency-warned only: the original used it to skip registering the ordinary controller, which is impossible here because the block set is frozen before definitions load. An honest, documented deviation.

### Structure attribution: D13's path was deliberately NOT reused

The factory controller is an **origin block, not a structure element** — the original removed `(0,0,0)` from the pattern, and this project's controller block is likewise absent from `pattern().positions()`. Mode comes from the block **type**, not from structure contents. So there is nothing to walk, and no third path was invented.

## Two things the original's code does that its comments do not mention

1. **One slot holds one recipe at a time.** Inputs are paid at `start`, so a second slot on the same recipe double-pays; the original avoided it by only offering recipes absent from `getActiveRecipeList()`.
2. **The original also let a factory mint an empty slot while nothing was startable**, which the 200-tick sweep then reaped — a churn loop. That was found by the harness, and removed.

## Acceptance — 271 offline checks, 0 failures

The harness grew from 170 to **271 PASS / 0 FAIL**; the 170 pre-existing checks are unchanged. The core assertion **captures each slot's recipe *and progress* after every tick**, so it proves advancement rather than existence:

```
[mmverify:factory_stone@1, mmverify:factory_smelt@1]   for 39 consecutive ticks
max crafts advanced in one tick = 2
8 cobblestone + 8 coal consumed -> 8 stone + 8 diamonds produced, 8000 FE drawn
max-threads bounds the pool: peak 1 vs >1; 8 vs 12 settlements in 40 ticks
max-threads: 0 => no ordinary slot ever runs
two pinned core slots both advancing in one tick
200-tick sweep reaps ordinary slots and spares core ones
```

**In game**, for a live confirmation: 8 cobblestone + 8 coal in the input hatch and ≥8000 FE in the energy hatch → both inputs drop together and both progress bars move; ≈42 ticks later, 8 stone and 4–8 diamonds. With `max-threads: 1`, only one recipe runs at a time.

## The mandatory checks

| Check | Result |
|---|---|
| 1. SRG reobfuscation | build product **and** deployed file: official name 0 hits, SRG `f_NNN_` 1 hit |
| 2. Build order | harness → `clean build` → reobf check → deploy; `BUILD SUCCESSFUL in 20s` with `> Task :reobfJar` |
| 3. One config per type | exactly one `.registerConfig(` (`ModConfig.java:119`), one `ForgeConfigSpec.Builder()` — **no config was added by this slice** |

`mods.toml` 0.22.0; **zero** files under `data/`; language keys **154 each, identical sets** (one key added — the factory controller block; no GUI keys, since the screen is M6e-2).

Jar SHA-256 `C709B55B63E2E50C8055A722A8E9956FB437D23970B4A2841B3F7A08A25E037B`, deployed with a matching hash.

## Two pre-existing harness faults this slice had to repair

1. **The harness did not compile at all.** It still imported `UpgradeBusConfig.MAX_SLOTS_LIMIT`, but 0.21.1 folded that class into the single `ModConfig` (`MAX_UPGRADE_SLOTS_LIMIT`). **The documented "170 PASS" had therefore not been reproducible since 0.21.1** — a verification artefact that had silently rotted. The version index now carries this as mandatory check 4: when the harness fails, first confirm *it* still compiles.
2. `org.ow2.asm` must stay in `init.gradle`'s classpath filter — this slice drives Forge's item/energy machinery, which reaches ASM.

## Not verified

**Nothing was launched in game.** World-side formation, the live "no `has-factory` ⇒ no factory" behaviour, multi-slot crafting as seen in game, generated blockstate rendering, the throttled update-tag sync, and thread restoration across `/reload` are all unverified. `GeneratedControllerPack` cannot be constructed offline (`AbstractPackResources`' constructor needs `SharedConstants.getCurrentVersion().getPackVersion(...)`, which `Bootstrap.bootStrap()` does not populate), so the generated factory blockstate is covered only from the declaration side. Section P reads the shipped recipe JSON with `JsonParser` rather than `ModRecipeSerializers` — deserialising a vanilla `Recipe` outside FML's class loader reaches FML internals, so the live path is unreachable offline.

## Left for M6e-2

The factory controller **screen** (`guifactory.png` 280×213 + `guifactoryelements.png`), the `gui.modular_machinery_reborn.factory.threads` / `.thread` language keys, and the `defaultFactoryMaxThread` / `enableFactoryControllerByDefault` config keys — which **must go into the existing single `config/ModConfig.java`**.
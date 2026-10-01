# Modular Machinery: Community Edition Reborn

**Release 0.21.1 — fix: 0.21.0 registered two COMMON configs and crashed on startup. Merged into one spec, which also uncovered a pre-existing key-path bug.**

## The crash

```
java.lang.RuntimeException: Config conflict detected!
    at net.minecraftforge.fml.config.ConfigTracker.trackConfig(ConfigTracker.java:40)
    at net.minecraftforge.fml.ModLoadingContext.registerConfig(ModLoadingContext.java:115)
    at com.reborn.modularmachinery.ModularMachineryReborn.<init>(ModularMachineryReborn.java:43)
```

**Forge permits one config of each type per mod.** 0.20.0 registered a `COMMON` spec for the parallel controller; 0.21.0 registered a second for the upgrade bus, so `ConfigTracker` threw during mod construction. The 0.21.0 release notes had flagged this as the first thing to watch and named the fallback — merging both key sets into one spec. That is what this release does.

## The fix

`config/ModConfig.java` is now the **single owner** of one `ForgeConfigSpec` (both sections in one builder) and holds the only registration, `ModConfig.register()`. `ParallelControllerConfig.java` and `UpgradeBusConfig.java` were **deleted outright**, so a second spec cannot reappear by accident. Every reference was updated (`ModularMachineryReborn`, `ParallelControllerTier`, `UpgradeBusTier`, and `MAX_SLOTS_LIMIT` → `ModConfig.MAX_UPGRADE_SLOTS_LIMIT`), and a comment at the registration site records *why* there must be exactly one.

**Independently verified:** exactly **one** real `.registerConfig(` call exists in the source tree — `config/ModConfig.java:119` — and `ForgeConfigSpec.Builder` is instantiated exactly once.

## A second, pre-existing bug the merge uncovered

The old specs **never wrote the documented keys**. `ForgeConfigSpec.Builder` is **path-accumulating**: `push()` appends to a running context and `pop()` does not return to the caller's starting point. So the 0.20.0 loop produced

```
[parallel-controller]
[parallel-controller.parallel-controller]        <- reinforced nested one deeper
[parallel-controller.parallel-controller.parallel-controller]   <- elite, etc.
```

five levels deep by ULTIMATE, and the same for the upgrade bus.

**Proof without launching the game:** rendering the 0.20.0 spec through NightConfig's own writer produced a file **byte-identical (SHA-256 `F3ED9D3C…69FC6`) to the user's live `config/modular_machinery_reborn-common.toml`** (1242 bytes). The documentation had been describing paths the code never actually wrote.

The merged spec canonicalizes to the intended, documented paths (`parallel-controller.<tier>.max-parallelism`, `upgrade-bus.<tier>.max-upgrade_slot`) by declaring every key with an explicit path list, so no definition can inherit a neighbour's prefix. The user's values were all defaults, so nothing custom was lost; the file is rewritten to the correct 10-key layout on first load.

## Verification

- **Mandatory check 1 (SRG reobfuscation)** passed on the build product and the deployed file: official `CREATIVE_MODE_TAB` absent, SRG `f_NNN_` present.
- **Mandatory check 3 (one config per type)** — the new check this release adds — verified as above.
- `mods.toml` 0.21.1; **zero** files under `data/`; language files **153 keys each, identical sets**; the jar contains **one** config class; renderer invariants intact and the preview's whole-panel `getArea()` untouched.
- Build `BUILD SUCCESSFUL in 20s` with `:jar` and `:reobfJar`; order was clean build → reobf check → deploy.
- Offline config probe (preserved in `_audit/mmconfigprobe/`): the merged spec defines all 10 keys with the right defaults and ranges; rendering the build jar and the deployed jar gives identical TOML; round-tripping the user's actual file through Forge's load path (`isCorrect` → `correct` → `setConfig` → read → `save`) yields the correct 10-key file. The user's original file and saves were not touched.

Jar SHA-256 `003A4964FF2E1EC2712BE7A26D54AFFAF5B8A11E4F7684E921753396EE8CDF09`, deployed with a matching hash.

## Not verified — and what to check in game

**The real Forge path was not exercised**: that `ModLoadingContext.registerConfig` accepts the single spec, that `ConfigTracker` no longer throws, and that the config screen and reads work during a genuine launch **all require launching the game**, which was not done. Also unverified: that a config with *customised* non-default values survives the rewrite, and that `maxParallelism()` / `maxUpgradeSlots()` behave in a live block entity.

In game, check: (1) the game reaches the main menu with no `Config conflict detected!`; (2) the config file is rewritten to flat sections (the nested `[parallel-controller.parallel-controller]` block disappears); (3) **change one `max-parallelism` and one `max-upgrade_slot`, restart, and confirm the machine and the bus GUI honour the new values** — that config *read* is the one thing no offline probe can cover.

## A pattern worth naming

This is the **second** startup-time crash, and both were the project's **first** instance of something:

| Release | The "first" | Result |
|---|---|---|
| v0.19.0 | first time the build flow gained a harness task | `reobfJar`'s output was overwritten by `jar` → un-reobfuscated jar → `NoSuchFieldError` |
| v0.21.0 | first time the mod had **two** configs of the same type | Forge refuses → `Config conflict detected!` |

Both times verification covered "the things I thought to check" but not "is this kind of thing even allowed". The version index now carries all three mandatory checks, and the handover document's 发布必查项 section lists them together.
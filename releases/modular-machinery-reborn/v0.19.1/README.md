# Modular Machinery: Community Edition Reborn

**Release 0.19.1 — fix: 0.19.0 shipped an un-reobfuscated jar, which crashed the game at mod construction.**

## The crash

```
java.lang.NoSuchFieldError: CREATIVE_MODE_TAB
    at com.reborn.modularmachinery.block.ModBlocks.<clinit>(ModBlocks.java:42)
    at com.reborn.modularmachinery.ModularMachineryReborn.<init>(ModularMachineryReborn.java:28)
```

`ModBlocks.java:42` is `DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)`. The runtime uses **SRG** naming
(the crash report says `ModLauncher naming: srg`), so a vanilla field must be referenced by its SRG name. 0.19.0's
jar still referenced the **official/mapped** name.

**Why only that one field failed:** Forge's own names (`ForgeRegistries.BLOCKS`, `DeferredRegister`, …) are not
remapped, so they resolve fine; only references to **vanilla** members need SRG names. That is why the failure is a
single `NoSuchFieldError` at the first vanilla reference rather than a cascade — a very recognisable signature.

## Proof

`javap -c -p` on `ModBlocks` in each archived jar:

| jar | official name `CREATIVE_MODE_TAB` | SRG name `f_…_` | verdict |
|---|---|---|---|
| **v0.19.0** | **present** | absent | **not reobfuscated — crashes** |
| v0.18.0 | absent | present | correct |
| v0.17.1 | absent | present | correct |

A fresh `gradle clean build` runs `> Task :reobfJar` and produces a correct jar, so the toolchain is fine — the
0.19.0 build's reobfuscation simply did not take effect (the build was interrupted, or its output was captured
before `reobfJar` rewrote `build/libs/<name>.jar`). The exact mechanism was not recoverable after the fact.

## The verification blind spot — and the new mandatory check

Every release check passed on that broken jar: version in `mods.toml`, zero files under `data/`, language-key
parity, renderer invariants, GUI texture counts, and the deployed hash matching `build/libs`. **None of them looked
at whether the jar had been reobfuscated at all.**

That check is now part of the release procedure and must be run before any deploy — it is cheap and decisive:

```powershell
javap -classpath <jar> -c -p com.reborn.modularmachinery.block.ModBlocks |
  Select-String 'CREATIVE_MODE_TAB'   # must find NOTHING (official name)
javap -classpath <jar> -c -p com.reborn.modularmachinery.block.ModBlocks |
  Select-String 'f_[0-9]+_'           # must find SOMETHING (SRG name)
```

The version index now carries a warning next to v0.19.0.

## State of the release

Source is **identical to 0.19.0** — M6c, its three machine fields (D12), the parallel-limit computation, recipe
modifiers and the three reconnected GUI items are all here, unchanged. Only the packaging differs. See
`v0.19.0/README.md` for what M6c actually contains; that document remains accurate about the *source*.

Because archives are never overwritten, 0.19.0 remains on disk as a broken artifact, flagged in the index, and this
correctly-built jar takes a new number.

## Verification

`BUILD SUCCESSFUL in 19s` with `> Task :reobfJar` executed. Jar SHA-256
`7CE490615D643509C8CA0BBF605208B5402A10C5E73D755F636DD83193469B4D`, **reobfuscation verified (official name
absent, SRG name present)**, `mods.toml` 0.19.1, **zero** files under `data/`, language files **132 each with
identical sets**, deployed with a matching hash.
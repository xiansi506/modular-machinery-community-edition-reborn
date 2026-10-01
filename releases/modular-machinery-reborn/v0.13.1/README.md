# Modular Machinery: Community Edition Reborn

Forge 1.20.1 / Java 17 migration foundation for the archived Modular Machinery: Community Edition source.

**Release 0.13.1 — fix: recipes had no root `type`, so none of them ever loaded.**

## The bug

Every machine recipe this mod has ever produced was being silently dropped. The game reported it as:

```
Parsing error loading recipe modular_machinery_reborn:immortal_bloom_crown:
com.google.gson.JsonSyntaxException: Missing type, expected to find a string
```

Vanilla's `RecipeManager` reads a root `type` field to decide which serializer to hand a recipe JSON to. Our
JSON had no such field.

**Why the port produced them that way:** the original mod opened its recipe files itself, so its schema never
needed a `type` — and `data/` in the original archive has no `type` either. Copying that schema faithfully
produced files that the original would have accepted and 1.20.1's recipe manager rejects before this mod's
serializer is ever called. The field is not optional here; it is the dispatch key.

**Scope: all 12 recipe files** — the one in the Rainbow Garden instance and the nine in `example-datapack/`,
plus the two pack-author examples. None of them could load. The nine built-in recipes were never once exercised
in game, which is why this went unnoticed: the M2 acceptance work validated the files against this mod's own
parser, and this mod's parser is never reached for a recipe that fails dispatch.

## The fix

Every recipe file now starts with:

```json
{
  "type": "modular_machinery_reborn:machine",
  "machine": "alloy_furnace",
  ...
}
```

Machine and recipe type are both registered as `modular_machinery_reborn:machine`, so the one value serves.

The mod now also **checks this itself** and says so plainly, instead of leaving a terse vanilla message:

```
[modular_machinery_reborn] <file> has no root 'type' field. Minecraft reads it to pick a recipe serializer
and drops the file before this mod sees it. Add "type": "modular_machinery_reborn:machine" as the first field.
```

It also rejects a wrong `type` value, and reports files that are not valid JSON.

## Getting it working right now

`config/modular_machinery_reborn/recipes/` is read through a data pack, so it is picked up by `/reload` — no
restart needed. The instance's copy is already fixed.

The jar in the Rainbow Garden instance has been updated to 0.13.1 (it holds only the new diagnostic; the recipe
files are external). A running game keeps the old code until it restarts, which is fine — the recipe fix does
not depend on it.

## Verification

- All 12 recipe files parse, carry `type = modular_machinery_reborn:machine`, and keep their `machine` and
  `requirements`.
- The pack mechanism itself is confirmed working **in game**: the log shows
  `Exposing 1 recipe file(s) ... as a data pack`, then the recipe reaching vanilla's parser. The synthetic data
  pack approach announced as unverifiable in 0.11.0 is now verified — the only defect was the missing field.

## What this says about the earlier acceptance work

The M2 milestone claimed "9 recipes validated, 0 problems". That check was real but insufficient: it validated
the schema against this mod's loader rather than through the path the game actually takes. **A recipe must now
be checked for the root `type` too**, and anything claiming a resource loads should be checked against the
engine's loader, not only against ours.

Build with Java 17:

```powershell
./gradlew.bat clean build --offline --no-daemon
```

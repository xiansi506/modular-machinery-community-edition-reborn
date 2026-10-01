# Modular Machinery: Community Edition Reborn

**Release 0.30.1 — metadata only. Functionally identical to 0.30.0.**

Two shipped-metadata defects in 0.30.0's jar, both fixed here:

1. **`license` said `MIT`** while the project and its upstream are **GPL-3.0**. Declaring a GPL-3.0 derivative as MIT is a licence misdeclaration; the jar now says `license="GPL-3.0"`. (`build.gradle` never set it — the string lived in the project's own `META-INF/mods.toml` template, which is why the first fix attempt found no line to change.)
2. **`authors` said "Community Edition Reborn Team"** — now the actual author. Both strings lived in the project's own `src/main/resources/META-INF/mods.toml` template rather than in `build.gradle`, which is why the first two fixes missed them.

**No code changed.** `clean build` → `BUILD SUCCESSFUL` with `> Task :reobfJar`; the harness was **not** re-run because no production code path changed, and the artifact's only differences are these two metadata strings (verified by reading `META-INF/mods.toml` out of the built jar). SRG reobfuscation still holds for the build.

Also recorded in this release: `NOTICE.md` carries the port's provenance, the GPL-3.0 obligation and the author (移植者：闲肆, 起始日期 2026-09-29); the repository ships `.gitignore` + `.gitattributes` (`* text=auto eol=lf`, since the project asserts LF everywhere); and `gradle.properties` defaults to `use_local_deps=false` so a fresh clone builds from maven without the three vendored jars.

**Still in development — not finished.** The done/not-done lists are in `docs/简介.md` / `docs/Introduction.md`.

## Correction note

Both strings lived in `src/main/resources/META-INF/mods.toml` (not `build.gradle`), so this took three passes: `license` first, then `authors`. The `v0.30.1` archive directory was created during that cycle and **overwritten in place** — it was never published, and this is the only such overwrite; no earlier version directory was touched.

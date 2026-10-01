# Modular Machinery: Community Edition Reborn

**Release 0.16.1 — preview button input: the hypothesis was disproven, the code corrected anyway.**

## What was found

The user reported that every preview button does nothing in JEI, while hover tooltips work. The first hypothesis —
that JEI's `simulate`/`execute` passes map onto press/release the wrong way round, so one click would fire twice and
a toggle would cancel itself — is **wrong**, and was disproven with bytecode rather than argument:

```
ForgeUserInput.fromEvent(MouseButtonPressed)  -> new UserInput(key, x, y, 0, InputType.SIMULATE)
ForgeUserInput.fromEvent(MouseButtonReleased) -> new UserInput(key, x, y, 0, InputType.EXECUTE)
UserInputRouter$1: SIMULATE -> handleSimulateClick (parks the claiming handler)
                   EXECUTE  -> handleExecuteClick (replays that parked handler)
```

A press really is a SIMULATE and the matching release really is an EXECUTE — one pass per physical event. A
runnable harness driving the **real** `UserInputRouter`, `CombinedInputHandler`, JEI bounds checks and the
**real compiled** `PreviewPanel`/`PreviewButton` measured one fire per click for both the old and the new code
(four clicks, four fires). So there was no double-fire and this fix is **not** claimed to cure the symptom.

## What was corrected

- `handleInput` now returns the panel's answers instead of discarding both and returning `true` twice, so the
  simulate pass is a truthful "would I handle this?" and clicks on the title row or ingredient grid are no
  longer swallowed.
- `PreviewPanel` claims the middle-click press over the viewport. This is load-bearing given the above: the old
  unconditional `return true` was what let the middle-click reset reach `mouseReleased`, so a truthful simulate
  would otherwise have killed it in JEI.
- A gated diagnostic (`-Dmodular_machinery_reborn.preview.debug=true`, inert by default) logs one line per click.

Also corrected: a stale comment claimed the machine-info button was "never a toggle". It is a real toggle; the
comment misled an earlier diagnosis.

## The strongest remaining candidate

`RecipeLayoutInputHandler.handleInput` falls back to `if (isSimulate) return true;`, so if this mod's handler never
reaches the layout's handler list, **every click inside the preview area would be swallowed silently** — which
matches "all buttons dead" exactly. The harness proves the dispatcher works when the handler is present; it cannot
prove registration happens in the running game. The gated log settles it in one run: no `[preview]` lines means the
handler is not reached, so the fix belongs in registration rather than `handleInput`.

## Verification

Build `BUILD SUCCESSFUL`; jar SHA-256 `625C4D203F9BA0C340AF01CF29E9265145C54C6DD1A615B624162AE60EB3403A`,
deployed to the Rainbow Garden instance with a matching hash; `mods.toml` 0.16.1; zero files under `data/`; language
files 115 keys each with identical sets. Renderer untouched. Reproducible harnesses kept in
`_audit/jei-input-verify/`.
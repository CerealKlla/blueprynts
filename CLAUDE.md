# Blueprynts

Construction and blueprint authoring mod for a modular Minecraft project (Minecraft Java Edition). Part of a suite of intercommunicating mods that expose public APIs so other mods — the user's own and third parties' — can integrate.

## Context directory — read this first

`context/` is a **separate private repo**, not part of this one — it's listed in `.gitignore` here and must never be committed to this repo. It's cloned as a subdirectory at `context/` for local convenience. If this directory is missing (e.g. a fresh clone of just this repo), restore it with:

```
git clone https://github.com/CerealKlla/blueprynts-context.git context
```

(Both `blueprynts` and `blueprynts-context` remotes exist under CerealKlla and are pushed and up to date as of 2026-09-27.)

Before searching source for architecture, ownership boundaries, API shape, or "why does this work this way," check `context/` first. It's maintained specifically to answer those questions cheaply:

- `context/design-document.md` — the authoritative design spec for what's actually been built (the Construction Site mechanic and its planned next round). Note: the repo root also has a much larger, earlier `design-document.md` describing a general-purpose Blueprint/Construction-Project framework — that root document is background/candidate future scope, not what got built; `context/design-document.md` explains the relationship and is authoritative for anything already implemented.
- `context/decisions.md` — dated log of decisions made during implementation that extend or override the design document, with rationale. Check this for anything that looks like it contradicts context/design-document.md — the doc should already reflect the current decision, but this explains why.
- `context/classes/` — one short markdown file per implemented class (or a small tightly-related group of classes, e.g. all payload records together) — public surface, key state, collaborators. Read the relevant file here before opening the actual source, and before editing a class update its file to match.

**Keep this system current as you work:**
- When a design decision is made that conflicts with or is absent from context/design-document.md, update it directly and add a dated entry to context/decisions.md explaining the change.
- When a class is added or its public surface changes, add or update its file in `context/classes/`.
- Don't let source and these docs drift — treat updating them as part of finishing the change, not optional cleanup.
- `context/` has its own git history, independent of this repo's commits. Commit and push changes there separately (`git -C context add . && git -C context commit -m "..." && git -C context push`) — editing the files alone doesn't back them up.

## Status

Scaffolded 2026-09-27 mirroring Settlemynts' own setup exactly: NeoForge 26.1.2.109, Java 25, `com.github.cerealklla.blueprynts` / `blueprynts`. Settlemynts is an **optional** soft dependency (not required) — the only integration point today is bridging built-in Blueprint Types into Settlemynts' Zone Type registry if present.

**V1 (the Construction Site mechanic) implemented and live-playtested the same day** — see [context/decisions.md](context/decisions.md) for the full dated history, including ten real bugs found and fixed during live testing (perimeter-fit asymmetry was actually a Settlemynts-side bug, found while testing this mod alongside it — see Settlemynts' own context repo). In short: a Construction Site block lets a player design a free-form (player-drawn, not fixed-rectangle) footprint via budgeted Footprint Slabs, build within a protected volume with a walk-away auto-clear safeguard, and Save/Load the result as a reusable Blueprint. See [context/design-document.md](context/design-document.md) for the full mechanic and [context/classes/](context/classes/) for per-class reference.

**Since then, also implemented and confirmed live**: Blueprint storage moved to a global file-based store (survives a server wipe, no per-dimension scoping); `templateVersion` + groundwork for a multi-reviewer voting-based Accept process (data shape only); and a full Load picklist with real preview images (`BlueprintPickerScreen`, backed by a new standalone renderer project, **[BluepryntImager](https://github.com/CerealKlla/BluepryntImager)**), a client-side `Full`/`Minimal`/`None` Preview Quality setting, and local caching. See context/decisions.md's dated entries for the full history, including two real bugs a live test caught and fixed (a client crash from an invalid texture `Identifier` path, and a too-tight terrain-clearing margin on Load) — both now confirmed live as of 2026-09-28. V3 is fully verified.

**V2's refund pile + glass ceiling implemented and fully confirmed live, 2026-09-28** — see context/decisions.md's dated entries for the full history, including five real bugs a live playtest round caught and fixed: a refund pile that paid out the site's own leveled dirt/wool as if the player had built it (fixed by excluding the untouched "site filler" sentinel), side walls stopping one block short of the ceiling, stairs pasting 180 degrees off on Load (and the same bug independently in **BluepryntImager**'s preview renderer — both fixed the same day), Load only re-clearing a bounding box around the built content instead of the entire original plot (losing any deliberate "white space" a player designed around their structure), and invisible text on all three construction screens (a bare-RGB alpha=0 bug, the same mistake Yconomics' Coin Purse decorator hit earlier). Refunds go into a Yconomics Loot Bag when that optional dependency is loaded (`bridge.YconomicsLootBridge`), falling back to a plain scattered item otherwise. The "untouched site filler" sentinel is now a dedicated `ExistingBlock` (visually brown wool, breakable, never obtainable) rather than real `Blocks.BROWN_WOOL`, so a player's own real wool always behaves normally.

**Next**: still open — a tag system (arbitrary + theming tags), a review-status field's actual enforcement (data model already exists), and a Settlemynts-side Town Hall Core required-tags plot filter. See context/design-document.md's own "Open design questions" section for the full list. Separately: BluepryntImager has no service/auto-start story yet — its watcher process has to be manually relaunched after any restart, and it was found stopped (with no preview generation happening) mid-session; worth a real launch script before this recurs.

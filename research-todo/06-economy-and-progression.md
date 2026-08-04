# Economy & Progression

Pulls together the economy-adjacent items currently scattered across `RESEARCH.md` §9 (economy/onboarding), §11 (mining/ore economy), and §15 (player UI/UX, territory tiers) into one place, since they're all facets of the same underlying "how does progression/wealth actually work" question.

> **Scope correction (2026-07-30):** Confirmed directly with the user — **Aechronis is a separate, third-party Minestom server**, not this project. Its `nodes`/`combat`/`vanilla`/`utils` libraries are being adopted as technical infrastructure (land-claim system, gun/melee combat engine, vanilla-feature reimplementation) because no better-maintained modern alternative to the old Bukkit `nodes` plugin exists — but **the new server's own game design is explicitly not going to copy Aechronis's design wholesale**. The new server's theme is musket-era (per `RESEARCH.md` §2's original framing), not Aechronis's own modern-military theme (tanks, jets, oil economy). The user chose "just the code, my own design" when asked directly. **This means the Territory Tier bonus curve and the Buildings/oil/vehicle-factory economy documented below are Aechronis's own design — kept here as reference/context (they explain what the code you're depending on was originally built for), not as a build target for this project.** Don't implement these unless a future decision explicitly says to adopt them.

## Resolved (2026-07-30): original phonon economy mechanics confirmed absent

Re-audited directly against current `nodes` source (see `NODES_DEEP_DIVE.md`'s re-verification note): a full-repo grep for `power`/`claimCost`/`unclaimPenalty`/`resourceConstant` returns zero matches, and a full-repo grep for `peace`/`truce` also returns zero matches. This closes two questions that were previously only "unconfirmed":
- The original phonon power-point territory-claiming economy (base power, per-player scaling, claim-cost formula) **does not exist in this port at all** — territory assignment is purely admin/`FlagWar`-driven, confirmed as an intentional design divergence, not a gap that was simply never checked.
- Peace treaties / 48-hour truce mechanics **do not exist anywhere in the codebase** — not partially built, not stubbed, genuinely absent.

Neither of these needs further "confirm whether it exists" research now — if either is wanted, it's a from-scratch feature design+build, same category as the voucher system below and the combat-tag system, not a matter of finding and enabling existing code.

## Currency layer — deferred, not decided

`RESEARCH.md` §9 explicitly holds off on this and frames it as "an open/deferred decision, not a 'no.'" Still needs an eventual answer: **[DECISION]**
- Is there a player-facing currency at all (beyond `nodes`' existing town/nation income system)?
- If yes, is it a single unified currency, or split between a gameplay-economy currency (earned via mining/income) and a cosmetic/premium currency (if monetization ever happens, per `08-testing-qa-and-legal.md`'s EULA note)?
- This decision gates the voucher system below, since "vouchers" typically imply *some* kind of spendable/redeemable unit.

## Voucher system — confirmed from-scratch gap

`RESEARCH.md` §11 confirms nothing resembling a redeemable time-limited buff exists anywhere in `vanilla`/`nodes`/`utils`. This is a genuine new feature, not a config toggle. Needs actual design before it can be scoped as an engineering task:
- What do vouchers grant (XP/resource-gain multiplier, territory-tier income boost, cosmetic-only)?
- How are they distributed (event rewards, shop purchase, staff grants)?
- Redemption UX — command-based, GUI-based, or both?

## Mining/ore economy integration tuning

- Whether territory attribute modifiers (used by `nodes`' ore-sampler for territory-tier income/mining bonuses) are recomputed live on every `sample()` call, or cached/baked whenever territory attributes change — unconfirmed per `RESEARCH.md` §11. Worth checking directly against source once this is actually being built, since it affects whether tier changes take effect immediately or require a restart/recompute trigger.
- `Nodes.config.oreBlocks` — referenced only as "a configurable block-type set" with no shown schema, defaults, or worked example of how deepslate/stone variants across a real map get enumerated. Needs a concrete config example written and tested against the actual map once world-height/world-gen (see `04-world-and-data-architecture.md`) is decided.
- The ore double-drop bug and missing ore→resource conversion table (both already tracked in `RESEARCH.md` §11 and `VANILLA_DEEP_DIVE.md` HIGH items respectively) are launch-blocking correctness bugs for this system — fix before tuning anything else here.

## Territory Tier system (1–10) — Aechronis's own design, reference only

Confirmed genuinely unimplemented in `nodes` code — documented only in the separate `Aechronis/guides` repo, per `NODES_DEEP_DIVE.md` Part 4. **This is Aechronis's own progression design, not something this project needs to build** — recorded here so the numbers exist somewhere if a future decision wants a similar tier-bonus curve, and because `Territory.defenderTimeMultiplier`/`attackerTimeMultiplier` in the underlying `nodes` code are real hook points worth knowing about regardless of whose numbers eventually go into them:

| Tier | Income bonus | Mining rate bonus |
|---|---|---|
| 1 | +50% | +30% |
| 2 | +75% | +45% |
| 3 | +100% | +60% |
| 4 | +125% | +75% |
| 5 | +150% | +90% |
| 6 | +175% | +105% |
| 7 | +200% | +120% |
| 8 | +225% | +135% |
| 9 | +250% | +150% |
| 10 | +300% | +200% |

The "nodesweep" staff evaluation process (staff assess submitted territories on build quality/quantity/historical-thematic accuracy and assign a 1–10 tier) is Aechronis's own staff workflow — not something this project inherits automatically, though the general pattern (staff-assigned quality tiers gating bonuses) could be worth borrowing in spirit if this project ever wants a similar system with its own numbers and its own theme.

## Aechronis's "Buildings" production economy — reference only, not a build target

`Aechronis/guides`' `src/buildings.md` describes a full tiered production-building system: **Passive buildings** (Farm, Port, Train Station — auto-produce into town income hourly) and **Active buildings** (Oil Rig, Oil Refinery, Land Factory, Air Factory — require input, output drops on the ground and can be stolen). The Land/Air Factories convert oil into Aechronis's own named vehicle roster (Truck, T-72B, M1A1 Abrams, F-35 Lightning II, B-2 Spirit, etc.) — **this is what the modern-military resource-pack assets and `combat`'s vehicle system (Car/Boat/Plane/Tank/Drone) were originally built for on Aechronis's own server.**

Since this project's own design is musket-era, not modern-military, **this specific Buildings/oil/vehicle-factory economy (the production loop and its named modern vehicles) is very likely irrelevant to build as-is** — it's recorded here purely so it's clear *why* the `combat` library ships a full Car/Boat/Plane/Tank/Drone vehicle system in the first place (it's Aechronis's own Factory-output content). That said — per `02-cross-library-integration.md`'s follow-up research — the vehicle *code* itself (`Vehicle`/`Car`/`Tank` class hierarchy) turned out to be generic enough to directly reuse for this project's own wagons/cannons, just with different assets and tuning. So the takeaway isn't "skip vehicles" — it's "skip Aechronis's specific Buildings/oil economy and vehicle roster, but keep and build on their vehicle code architecture for your own musket-era vehicle roster (wagon, cannon, etc.), sourced through whatever production/economy system this project designs instead."

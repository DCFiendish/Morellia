# Community, Onboarding & Staff Tooling

`RESEARCH.md` §9 and §13 already raise most of these as flags without resolving them. Consolidated here since they're all "how do real humans actually experience joining/running this server" questions.

## Server access model — RESOLVED (2026-07-30)

**Open server, no whitelist.** Anyone with the server's IP/domain can connect directly — no beta-gating or invite-only phase planned. This closes the open item from `RESEARCH.md` §13.

Downstream effects of this decision:
- The username-sanitization gap noted in `03-anti-cheat-and-security.md` (`nodes` never uses `ArgumentSanitizedString`) is now a **live risk, not moot** — there's no whitelist acting as an incidental filter on who can even attempt a connection, so confirming the server always runs online-mode/Velocity-modern-forwarding (the actual mitigation, since that's what constrains what a username can even be) matters more, not less.
- `09-network-and-ddos-security.md`'s Layer 3 (application-layer bot/join-flood mitigation) is now unambiguously required rather than a nice-to-have — an open server with no whitelist has zero access-list-based protection against a bot-flood join attack; anti-bot software is the only thing standing between the server and that attack class.
- Nation/town membership vetting — **RESOLVED (2026-07-30): no review process.** Joining a nation/town does not go through Discord or any staff/leadership review step — see below.

## Onboarding / town-application flow

- Town-application auto-expiry: applications expire after 60 seconds if no town officer is online, with no notification hook. `RESEARCH.md` §9 flags this as a real UX gap and explicitly says "not designing this now, just flagging it" — this needs an actual design pass (a lightweight notification mechanism — Discord webhook ping to officers? in-game mail?) before it becomes a real new-player's first bad experience.
- Whether `Town` has any "recruiting open/closed" status flag at all is unconfirmed against source — check this directly; it affects whether new-player town-matching can filter to only actively-recruiting towns.
- **RESOLVED (2026-07-30): nation/town membership is not vetted through Discord or any review process.** The existing in-game `/town apply`/`/town invite` flow stands as the entire mechanism — no application/ticket step layered on top. This also simplifies the Discord structure below (no membership-gating bot/role needed).

## Discord structure

Not yet designed at all. Needs, at minimum:
- Channel/role structure (staff, per-nation channels?, support/ticketing, announcements).
- Whether/how Discord identity links to in-game identity (verification bot, or informal).
- Whether a Discord bot bridges in-game chat (ties to `RESEARCH.md` §13's Ops layer discussion of cross-platform chat, and to `04-world-and-data-architecture.md`'s Redis pub/sub scope, which is currently limited to in-game chat only).
- No longer needs to account for nation-membership vetting (resolved above — membership doesn't route through Discord at all), which simplifies the role/permission design somewhat.

## Staff tooling gaps

- `logger` (per `RESEARCH.md` §13) captures block changes, entity spawn/despawn, kills, loot, inventory, and container access — but **not chat, join/leave, or command usage**. If chat-moderation history matters at 200+ player scale (likely), this needs a small dedicated listener added — currently a real gap, not a config toggle.
- Nodesweep-style staff evaluation (per `06-economy-and-progression.md` — this is Aechronis's own process for assigning their territory tiers, kept here only as a *pattern* reference) — **only relevant if this project decides to adopt a similar staff-assigned-quality-tier system with its own design**; not something to research further unless that decision is made.
- `worldedit` — almost entirely unresearched beyond being listed as "admin tool, order-independent" in the org's module table. Its actual command surface, undo/history model, and permissioning are all unknowns that matter directly to staff workflows (world-building, grief-cleanup) once the server is live — this one's still relevant since it's infrastructure, not Aechronis-specific game design.
- Staff application process, rank structure (member/officer/leader is confirmed for towns via `nodes`, but staff ranks — moderator/admin/owner — are a separate, unaddressed hierarchy) — not researched anywhere, this project's own decision to make.

## Resource pack (own art direction needed, not Aechronis's)

- `Aechronis/resource-pack` is themed around Aechronis's own modern-military content (per `06-economy-and-progression.md`'s Buildings/vehicle-factory findings — AK-47/M4A1/tank/F-16/drone assets). Since this project's theme is **the Agadir Crisis (1911), alternate history** (decided 2026-08-05, revised same day from an earlier First Balkan War placeholder), **that resource pack almost certainly isn't a fit as-is** — this project likely needs its own 1911-themed resource pack (bolt-action rifles, period uniforms/faction insignia for the nation roster — Germany, France, UK, Spain, Italy, Morocco, and the minors — horse-drawn artillery, early aircraft) or a heavily stripped/retextured fork, not adoption of Aechronis's asset set. The resource-pack branches (`template`, `a-new-millenium`, `event`) mentioned in the org's repo list are Aechronis's own branches and probably not directly relevant here beyond "if we fork their repo as a starting technical template for how resource-pack building/deployment works."

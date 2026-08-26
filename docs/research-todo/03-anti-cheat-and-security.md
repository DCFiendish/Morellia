# Anti-Cheat & Security

> **Scope note:** this file is application/gameplay-layer security only — a player already connected doing something illegitimate (reach hacks, speed hacks, exploit abuse). For network-layer security (DDoS protection, bot/join floods, proxy/firewall hardening), see [`09-network-and-ddos-security.md`](09-network-and-ddos-security.md) instead — that's about the server staying reachable and not falling over, a distinct problem from what's covered below.

`RESEARCH.md` §10 already frames anti-cheat at a high level (build melee reach-check first, movement/speed/no-fall checks second, evaluate `mango-anti-cheat` as a design reference only). This file pulls that together with the specific bug-level findings scattered across the three deep-dive docs so there's one buildable checklist instead of a plan in one doc and evidence in three others.

## Confirmed exploitable gaps, by source

**From `COMBAT_DEEP_DIVE.md`:**
- No reach-distance check on melee (C4) — unverified whether Minestom's own packet handling imposes any cap independently of `combat`'s code; if not, melee reach is entirely unenforced.
- No fresh-click-vs-held-spam signal (H2) — sniper/automatic fire-rate is entirely client-trusted, no server-side cadence validation.
- Crit eligibility fully client-trusted (H4) — `isPlayerFalling`/`isOnGround`/`isSprinting` have no physics cross-check, so crits are spoofable.
- No silent-aim/aim-vector plausibility check anywhere (M11) — a raycast that always resolves in the shooter's exact favor with no sanity bound on aim precision/tracking.
- Shoot-through-walls vector (C3) — `Ray.kt::firstBlockAt` treats unloaded chunks and unrecognized collision shapes as "no hit" instead of failing safe.

**From `NODES_DEEP_DIVE.md`:**
- Username never sanitized through `ArgumentSanitizedString` — flagged as "a real gap if this server or a proxy in front of it ever runs offline-mode." Confirm the server will always run online-mode/Velocity-modern-forwarding, or fix this.
- Several admin batch commands discard per-item success/failure and always report success — not an exploit, but a false-confidence trap for staff acting on bad information during an incident.

**From `VANILLA_DEEP_DIVE.md`:**
- No movement validation anywhere — no speed-hack, fly-hack, or no-fall check. `FallDamageListener` trusts client-reported Y-position with zero anomaly detection.
- Whitelist file has no write synchronization and no try/catch around parsing — a malformed file can silently block whitelist loading entirely (an availability/security issue, not just a data bug).

## Build order (already sequenced in `RESEARCH.md` §10 — restated here as the actionable list)

1. Melee reach-distance validation (combat C4) — cheapest fix, highest signal-to-noise, blocks the most obvious hack class.
2. Movement/speed/fly/no-fall validation (vanilla gap) — the other "obviously missing" baseline check.
3. Fire-rate/cadence validation for guns (combat H2) — needs a per-player last-fire-timestamp check, similar shape to the eventual combat-tag timestamp-map pattern (see `RESEARCH.md` §12), so consider building both with a shared utility.
4. Crit-eligibility and aim-vector plausibility checks (combat H4, M11) — highest engineering cost, lowest urgency; defer until the above three are live and you have real player behavior data to calibrate thresholds against (false-positive risk is much higher here than for reach/movement).

## Open questions to resolve before building

- Is there any Minestom-core packet validation (position updates, interact-range) that already partially covers movement/reach, independent of anything `combat`/`vanilla` add? Worth checking directly against the pinned version rather than assuming zero coverage.
- `mango-anti-cheat` — spike it for check *design* reference (e.g. how it structures a speed-hack check) but do not depend on it in production; it's explicitly flagged as too low-commit/low-star to trust at 200+ concurrent players.
- Server access model — **RESOLVED (2026-07-30): open server, no whitelist** (see `07-community-and-onboarding.md`). This makes the username-sanitization gap above a **live risk**, not a moot point — there's no whitelist filtering who can attempt a connection, so confirming online-mode/Velocity-modern-forwarding is the actual mitigation that matters now.
- Once combat-tag (`RESEARCH.md` §12) is built, anti-cheat checks and combat-tag state will likely share the same "last PvP action timestamp" data structure — design them together rather than as two unrelated systems that happen to both need per-player timing state.

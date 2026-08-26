# Hosting, Ops & Load Testing

`RESEARCH.md` §4/§7/§7a already cover the core hosting topology decision (Oracle A1 PAYG + cheap Hetzner/Contabo VPS) and `minestom-server-setup/03-runtime-ops-and-logging.md` covers JVM/GC tuning and the missing-ops-dashboard gap. This file exists for the items that were only ever mentioned in passing and never actually researched, plus the load-testing methodology gap that cuts across all of it.

## Resolved (2026-07-30): Oracle PAYG A1 allotment confirmed with Oracle support

**No longer an open item.** `RESEARCH.md` §4 previously documented conflicting evidence on whether PAYG (upgraded) Oracle tenancies keep the full 4 OCPU/24GB Always Free Ampere A1 allotment after the ~June 2026 halving of the free-tier-only allotment. The user has now confirmed directly with Oracle support: **PAYG tenancies do get the full 4 OCPU/24GB**, unaffected by the free-tier-only cut to 2 OCPU/12GB. The hosting topology in `RESEARCH.md` §7 (control plane + flagship shard both on the Oracle A1 pool) can be finalized around this number — no more "keep an eye on billing" hedge needed. Remaining Oracle caveats are unrelated to billing: "out of capacity" errors on new ARM instance provisioning can still happen (availability, not billing), and the 10TB/month tenancy-wide egress cap still applies.

## Needs real resolution before committing money

- **Minestom per-player memory footprint** — no citable official number exists anywhere researched so far. Needs real measurement once the flagship shard is actually running, not just theory, before finalizing hosting purchase sizes.
- **JVM/GC tuning** — explicitly an open question; do not assume Paper-world tuning (Aikar's flags) carries over to Minestom's very different memory/threading profile. Needs empirical testing once a shard exists to profile against.

## Named but never actually evaluated

- **TCPShield** — proposed in `RESEARCH.md` §7 as "cheap insurance, no cost at the free tier" for DDoS mitigation. **Now researched in full — see [`09-network-and-ddos-security.md`](09-network-and-ddos-security.md).** Short version: confirmed compatible with Velocity's modern forwarding, free tier is a reasonable launch default, Cloudflare Spectrum (~$10/mo) is the paid upgrade path if it's not enough. That file also covers a layer TCPShield alone doesn't (application-layer bot/join floods) and the Oracle-free-tier DDoS-protection gap this directly plugs.
- **`GufliMC/Brickstom`** — a community Minestom server template, mentioned only once in passing re: its tinylog logging setup. Never otherwise evaluated even though "someone already scaffolded and open-sourced a Minestom server" is a directly relevant precedent worth reading in full before `world-server`/`hub-server` are scaffolded from scratch — may save real time or surface pitfalls (see also §1 concurrency-model doc, which suggests checking Brickstom's threading assumptions specifically).
- **SLF4J binding (Logback vs. tinylog)** — a real, practical, currently-undecided choice with no strong reason not to just pick one. Low-stakes but blocking anyone from writing the actual logging setup in `Main.kt`.

## Ops/monitoring — nothing exists yet

- Minestom ships no bundled ops/metrics dashboard — this needs to be built, not configured. `Acquirable.resetAcquiringTime()` was named in the setup docs as a usable metrics primitive but nothing has been designed around it.
- Decide the actual monitoring stack (Prometheus + Grafana is the obvious default given it's what most JVM services use) and what gets tracked at minimum: TPS/tick-time per shard, player count per shard, memory/GC pause times, and application-level counters (active sieges, shop transactions) that matter specifically to this project's gameplay.
- Crash recovery / process supervision — nothing researched yet on how a crashed shard process gets restarted automatically (systemd unit, Pterodactyl's own process management, or something else) — ties into `RESEARCH.md` §14's Pterodactyl-egg gap, since the egg's stop/start/crash-detection behavior is exactly this.

## Load testing — genuinely unaddressed as its own topic

Every deep-dive doc independently flags things that *should* be load-tested but none of them actually propose how:
- The 200-players-on-one-shard target itself (`RESEARCH.md` §7 already says explicitly this should be "verified with a real load test, not assumed").
- Explosion particle broadcast volume (`COMBAT_DEEP_DIVE.md`: "on the order of 1000+ particle packets per explosion") under a large multi-player fight.
- Minimap render-storm risk during large sieges (`NODES_DEEP_DIVE.md` H8: unbatched full broadcast on every flag-attack lifecycle event, compounding with war-autosave thread-pool contention).

**This needs an actual methodology, decided explicitly:**
- Bot-simulation load test (headless Minestom/Mineflayer-style clients hammering the server) vs. a staged real-player alpha/stress-test event — pick one as the primary method, since they answer different questions (raw tick-time capacity vs. real gameplay-pattern load).
- What specifically gets measured during the test (TPS floor, GC pause spikes, packet-queue depth) and what the pass/fail bar is for calling a shard-size estimate "confirmed."
- Sequencing: this can't happen until the CRITICAL/HIGH bugs in the three deep-dive docs are fixed (a war-state-loss-on-restart bug or a barrel-dupe exploit would contaminate load-test results with unrelated failures) and a real map exists to test on.

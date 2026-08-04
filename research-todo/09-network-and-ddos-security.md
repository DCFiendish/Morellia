# Network Security & DDoS Protection

New topic, researched 2026-07-30 at the user's request, prompted by this project sitting in the same "geopol" (geopolitical-roleplay/nation-building) Minecraft server genre as Aechronis — a genre the user flagged as "notoriously bad with DDoSing." This file is purely network/infrastructure-layer security (keeping the server reachable and not falling over under hostile traffic). It's a distinct surface from `03-anti-cheat-and-security.md`, which is application/gameplay-layer (reach checks, movement validation, exploit prevention) — a player already connected cheating vs. the server never getting a clean connection in the first place.

## Threat model for this project specifically

- **Genre-specific risk is real but the "why" is mostly generic, not political.** Direct research into geopol-community-specific DDoS incidents didn't surface documented cases (this is the kind of thing that lives in Discord server history and player word-of-mouth, not indexed web content) — but the broader pattern is well-evidenced: gaming traffic accounts for roughly 80-90% of DDoS attack targets industry-wide, and Minecraft specifically has hosted some of the largest DDoS attacks ever recorded (a 3.15 billion packets/second attack in August 2024, and a 2.5 Tbps Mirai-botnet attack on Wynncraft in 2022 — both **not** politically motivated, both driven by ordinary player/rival-server grudges and competitive tournament sabotage). Nation-roleplay/geopol servers layer an extra motive on top of the generic "someone's mad they lost" pattern: active inter-server rivalry, defection drama, and war/diplomacy stakes that spill into real grudges — the genre's reputation is plausible and worth taking seriously even without a citable incident log.
- **Practical implication**: treat DDoS mitigation as a launch-blocking infrastructure requirement, not a "nice to have if we get popular" deferred item. A brand-new small server can still be a target on day one if even a handful of players bring drama from another geopol community.
- **What kind of attack actually matters here**: two categories, need different defenses (see below) —
  1. **Volumetric/network-layer floods** (SYN floods, UDP amplification, raw packet floods) — aimed at knocking the connection itself offline. Mitigated at the infrastructure layer (proxy/CDN), not in Minestom code.
  2. **Application-layer bot/join floods** — traffic that completes the real Minecraft handshake (valid protocol, sometimes even valid premium accounts from a botnet) and floods the server with legitimate-looking joins to exhaust CPU/RAM/chunk-loading, not bandwidth. This one is *not* stopped by TCPShield/Spectrum/OVH — it looks like real players connecting. Needs server-side anti-bot software.

## Layer 1 — volumetric DDoS protection (pick one before launch)

All four real options, compared directly:

| Option | Cost | How it works | Notes |
|---|---|---|---|
| **TCPShield** | Free tier available | Minecraft-specific reverse proxy sitting in front of your server, backed by OVH's DDoS-protected network; DNS points to TCPShield, TCPShield forwards to your real IP | The default "cheap insurance" pick — already the leading recommendation in `05-hosting-and-ops.md`. Confirmed compatible with Velocity's modern forwarding (TCPShield forwards player info onward same as any other Velocity-fronting proxy). Free tier is popular but shared/lower-priority under real large attacks than paid options. |
| **Cloudflare Spectrum** | ~$10/mo flat, unlimited bandwidth | Reverse-proxies raw TCP (not just HTTP) through Cloudflare's network (42 Tbps+ capacity, 22+ global locations) | The "actually handles anything" option — Cloudflare's own network is what absorbed the record 3.15 Bpps Minecraft attack in 2024. Worth the $10/mo once real players/stakes exist; possibly overkill for initial soft-launch testing. |
| **Self-hosted behind OVH Anti-DDoS** | Cost of a small VPS | Run your own lightweight proxy (Velocity, or Minestom's own `Gate`/similar) on an OVH VPS with Anti-DDoS active, point DNS at that | More setup work, but no reliance on a third party's free-tier goodwill; OVH's gaming-specific anti-DDoS is the same underlying infra TCPShield itself resells. |
| **Minekube Connect** | Free | A managed connect-layer service (mentioned in Gate proxy's own docs) — low-latency, minimal setup | Newer/smaller-known option than the other three; worth a closer look only if TCPShield's free tier proves insufficient, not a first pick given it's the least-established of the four. |

**[DECISION]** — which of these to use isn't just "pick the best," it's a cost/complexity tradeoff that should track the project's actual launch stakes: TCPShield free tier is the reasonable default to launch with (zero cost, known-compatible with the existing Velocity-modern-forwarding plan), with Cloudflare Spectrum as the fallback upgrade path if the free tier gets overwhelmed or the server draws real hostile attention (e.g. after visible geopol-community drama). Don't over-engineer this before there's a real player base to protect.

**Relevant given the now-confirmed Oracle A1 hosting plan (`05-hosting-and-ops.md` — PAYG's 4 OCPU/24GB allotment confirmed with Oracle support 2026-07-30)**: Oracle Cloud Infrastructure's free tier does **not** include DDoS protection — that's a separate gap from the (now-resolved) compute-billing question, and it doesn't go away just because the compute allotment is confirmed. Whichever proxy option above is chosen sits in front of the Oracle-hosted backend specifically to cover this gap; the backend's own Oracle Network Security Groups (NSGs) are necessary (lock the backend to only accept the proxy's IP — see Layer 2) but not sufficient on their own.

## Layer 2 — proxy/backend hardening (regardless of which DDoS service is chosen)

This is the part that's actually implementation work for `world-server`/`hub-server`, not just a vendor pick:

- **Firewall the backend to reject direct connections.** The single most-repeated piece of guidance across every source checked: whichever DDoS-mitigation proxy is chosen, the real Minestom server process must **only** accept connections from that proxy's IP (OS firewall rule / Oracle NSG ingress rule), never expose port 25565 (or whatever port is chosen) to the public internet directly. If this isn't done, attackers can simply bypass the protective proxy by connecting to the origin server's real IP directly — trivially discoverable via any of several "find the real IP behind TCPShield/Cloudflare" tools that exist specifically because server owners forget this step.
- **Modern forwarding (`Auth.Velocity(secret)`, already the plan per `RESEARCH.md`) is a second, complementary layer, not a firewall replacement.** It cryptographically MAC-signs forwarded player info so the backend can trust *who* is connecting through the proxy, but it does nothing to stop raw connection-flood traffic from ever reaching the backend process in the first place — that's the firewall's job. Keep the `forwarding.secret` file confidential (standard secret-hygiene, no different from an API key).
- **Never bind the backend to `0.0.0.0` on a publicly routable interface if proxy and backend share a host or a private network** — bind to `127.0.0.1` or the private-network interface only, so there's no public IP:port for the backend at all, only the proxy's.

## Layer 3 — application-layer bot/join-flood mitigation

This is the layer that volumetric DDoS protection (Layer 1) explicitly does **not** cover, because the traffic looks like real Minecraft clients completing a real handshake:

- **Server-side anti-bot software is needed regardless of which Layer 1 vendor is chosen.** The established pattern (from current guides and community tooling): a lightweight listener that tracks connections-per-second and flags spikes above a threshold (e.g. a commonly cited default of ~8 connections/sec triggering "attack mode"), forcing re-authentication/re-verification during an active spike, and feeding confirmed-bad IPs into a kernel-level blocklist (`ipset`) for cheap ongoing rejection without per-connection application overhead.
- **This doesn't exist anywhere in `nodes`/`combat`/`vanilla`/`utils` today** — confirmed absent, same category as the combat-tag system and voucher system: a genuine from-scratch build, not a config toggle. Options: port the general pattern from a known reference implementation (the historically cited one, EpicGuard, is now discontinued but widely forked — treat as a design reference, same caveat as `mango-anti-cheat` in `03-anti-cheat-and-security.md`: read for the check design, don't blindly depend on an unmaintained fork in production) or build a minimal Minestom-native version directly against `PlayerLoginEvent`/`AsyncPlayerConfigurationEvent` since Minestom's event model already gives clean hook points for this.
- **OS-level companion measures** (regardless of whether app-layer anti-bot software is built): `tcp_syncookies` enabled, connection-rate-limiting rules on the proxy/backend's listening port via `iptables`/`nftables`, and `ipset`-based IP blacklisting for anything already confirmed hostile. Cheap, kernel-level, complementary to whatever gets built in Layer 3's application code.

## Testing this before launch

Same load-testing gap already flagged in `05-hosting-and-ops.md` applies directly here — bot-flood defenses need to be tested against *simulated* bot traffic before trusting them, not assumed to work. The one concrete tool worth naming: **SoulFire**, a Minecraft bot-simulation framework specifically built to mimic real client behavior (defeats naive protocol-quirk fingerprinting), useful both for this and for the general player-load testing already scoped in `05-hosting-and-ops.md`'s load-testing section — worth standardizing on one bot-simulation tool for both purposes rather than building two separate harnesses.

## Baseline hardening (not DDoS-specific, but adjacent enough to note here rather than starting a 10th file)

Quick pass of generic Minecraft-server-security hygiene that doesn't fit anywhere else in the existing docs, all still open/unconfirmed for this project:
- RCON — is it planned to be used at all? If yes, it must never be exposed on a public interface/password reused elsewhere; if unused, confirm it's disabled rather than left on with a default/weak password.
- Plugin/dependency supply-chain hygiene — same category of concern already raised about the unidentified ConceptMC LuckPerms bridge maintainer in `02-cross-library-integration.md`; worth treating as a standing policy ("no unaudited SNAPSHOT/unknown-maintainer dependency in the login/auth path") rather than a one-off exception for that dependency alone.
- Regular backups and update cadence — generic but genuinely unaddressed anywhere yet for this project's own eventual `world-server`/`hub-server` repos, separate from the per-library `*Test.kt` testing question already tracked in `08-testing-qa-and-legal.md`.

## Open items needing a decision

- **[DECISION]** Layer 1 vendor pick — recommend TCPShield free tier at launch, Cloudflare Spectrum as the paid upgrade path (see comparison table above).
- **[DECISION]** Build vs. adapt for Layer 3 anti-bot — port/reference EpicGuard's design vs. build Minestom-native from scratch against existing event hooks.
- Confirm firewall/NSG rules restricting the backend to proxy-only traffic are actually written into the eventual `world-server`/`hub-server` deployment docs (`minestom-server-setup/`) once scaffolding begins — this is implementation follow-through, not further research.

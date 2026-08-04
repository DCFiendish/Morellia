# Handoff — Morellia session (2026-07-31)

Deep background (library internals, design rationale) is in `RESEARCH.md`, `NODES_DEEP_DIVE.md`,
`VANILLA_DEEP_DIVE.md`, `COMBAT_DEEP_DIVE.md`, and `research-todo/*.md` — not repeated here. This
doc is: what's blocking right now, what changed this session, and the credentials/IDs needed to
keep going without re-discovering them.

## 🔴 Immediate blocker

**Oracle box (0.0.0.0) has degraded connectivity for sustained transfers.** Confirmed
this session:
- Raw TCP connect to port 25567 succeeds instantly from my machine.
- `scp` of a 49MB file to the box fails with "Connection reset by peer" on 4/4 attempts (tried
  plain, `-C` compression, keepalive options, hard timeout — all failed the same way).
- GitHub Actions runners (used for load-test bots) can complete a TCP connect but never finish
  the Minecraft login handshake — zero join events reach the server's log, confirmed against
  both the new bot code AND the unmodified pre-session bot commit (ruling out a code bug).

Likely cause: Oracle's automatic flood/DDoS protection throttling after the 350-bot load test
burst earlier this session. Typically self-clears on a cooldown (untested how long — try again
first before assuming it's something else).

**First thing to do in the next session: retry the deploy below. If it works, the blocker was
just the cooldown and everything after this point in the doc is ready to run as-is.**

## Access / credentials

- **SSH to the Oracle box**: `ssh -i C:\Users\USER\.ssh\id_ed25519 ubuntu@0.0.0.0`
  (same keypair as the [HOSTING-BUSINESS-NAME] business, per `minecraft-hosting` skill — this is a
  separate Oracle instance from bmwoo though, don't confuse the two).
- **GitHub**: `gh` CLI is already authenticated as `DCFiendish` via OS keyring (`gh auth status`
  confirms it, no token needed in this doc). Repos:
  - `DCFiendish/nodes` — fork of `Aechronis/nodes`, local clone at
    `C:\Users\USER\Minecraft Dev\aechronis\nodes-lib`
  - `DCFiendish/vanilla` — fork of `Aechronis/vanilla`, local clone at
    `C:\Users\USER\Minecraft Dev\aechronis\vanilla`
  - `DCFiendish/rust-mc-bot` — fork of `Eoghanmc22/rust-mc-bot`, local clone at
    `C:\Users\USER\Minecraft Dev\aechronis\rust-mc-bot`
  - `Aechronis/utils`, `Aechronis/combat` — used directly, not forked, no local changes.
- **GitHub Packages token** (for Gradle to pull the forked libs): already configured at
  `C:\Users\USER\.gradle\gradle.properties` as `gpr.user`/`gpr.token` — works, no action needed,
  not duplicating the raw value here since that file already has it.
- **Pterodactyl panel for this box**: not used this session (deployed via direct SSH/docker
  instead) — no login captured here. If needed, check with the user.
- **No Pterodactyl API key was used or captured this session.**
- ⚠️ **`C:\Users\USER\Aechronis` (the server module) is not a git repo.** No version history,
  no remote backup — just local disk + whatever jar is currently deployed. Worth raising with the
  user if this project is going to keep growing.

## Server identifiers (re-verify container ID — it's not stable across restarts)

- Pterodactyl server UUID: `00000000-0000-0000-0000-000000000000`
- Volume path: `/var/lib/pterodactyl/volumes/00000000-0000-0000-0000-000000000000` → mounted at
  `/home/container` in the container
- Docker container ID as of this session: `79869340c7bb` — **re-fetch via
  `sudo docker ps` rather than trusting this**, it changes on restart
- Port: 25567 (tcp + udp), offline-mode auth (`Auth.Offline()`), so any bot username works
- `server.jar` inside the volume is owned by uid/gid `998:998` — after copying in a new jar,
  `chown 998:998` it or the container won't start

## What's done and deployed (working, confirmed)

- `nodes` fork pinned at `be9e9c3`, `vanilla` fork pinned at `2bf2689` in
  `server/build.gradle.kts` — ~15 M/LOW correctness bugs fixed (permissions, plots, war-save
  races, ore sampling, etc.) and a systemic off-main-thread chunk/entity mutation bug class fixed
  across 6 vanilla managers (Crops, Saplings, TreeFeller, EnvironmentalDamage, Food, Combat).
  Both published and load-tested clean.
- Load tests run this session against the *currently deployed* jar (i.e. before this session's
  newest changes below): 20 bots ✓, ~150 bots ✓, 350 bots ✓ — all clean joins, 0 kicks/errors,
  steady 20.0 tps throughout. This validated general connection capacity and the vanilla
  per-player fixes, but **never touched nodes' war system** (no flag placement, no beacon
  render, no minimap war-broadcast, no real combat/loot) — that gap is what the work below
  is for.
- `TickMonitor.kt` — rolling tick-time/TPS logger, already live, logs every 5s to the
  container's stdout (`[TickMonitor] avg tick: ...ms (~...tps), max tick: ...ms, over ... ticks`).

## What's built but NOT yet deployed (this is the actual next step)

Built in response to the user's realism question about war-load testing ("how realistic is this
to actual players doing a war"). Scope was agreed as Phase 1 (flag placement) + Phase 3 (burst
multi-attacker load), explicitly deferring Phase 2 (real combat/damage — needs entity tracking
the bot doesn't have) as lower priority.

1. **`rust-mc-bot` — commit `436856c`, already pushed to `DCFiendish/rust-mc-bot` master, CI
   build verified green.** Adds `write_block_place()` (packet ID `0x42`,
   `ClientPlayerBlockPlacementPacket`) and makes ~25% of bots (`bot.id % 4 == 0`) place one fence
   "war flag" each, once, split into two attacking factions by bot-id parity, targeting hardcoded
   block coordinates inside the two test territories (see below).

2. **`server/src/main/kotlin/net/morellia/server/LoadTestBots.kt` — new file, written, compiles
   clean, NOT yet on the server.** Test-only scaffolding: on `PlayerSpawnEvent`, if the player's
   name matches `Bot_<n>`, auto-enlists them into "Testville" (even n) or "Secondtown" (odd n) via
   `Town.addResident`, and gives them an `OAK_FENCE` directly in hotbar slot 0
   (`player.inventory.setItemStack(0, ...)`). This sidesteps needing the bot to speak Minestom's
   chat-command or creative-inventory-slot protocol itself — town membership and the flag item are
   granted server-side instead. Wired into `Main.kt` via `LoadTestBots.init()`.

3. **Jar is already built locally**: `C:\Users\USER\Aechronis\server\build\libs\morellia-server.jar`
   (built 2026-07-31, ~49MB). **This is what's stuck trying to deploy — see blocker above.**

### To finish once the connectivity blocker clears

```bash
scp -i ~/.ssh/id_ed25519 "C:\Users\USER\Aechronis\server\build\libs\morellia-server.jar" ubuntu@0.0.0.0:/tmp/morellia-server-new.jar
ssh -i ~/.ssh/id_ed25519 ubuntu@0.0.0.0 "sudo cp /tmp/morellia-server-new.jar /var/lib/pterodactyl/volumes/00000000-0000-0000-0000-000000000000/server.jar && sudo chown 998:998 /var/lib/pterodactyl/volumes/00000000-0000-0000-0000-000000000000/server.jar && rm /tmp/morellia-server-new.jar"
# restart via Pterodactyl, or: ssh ... "sudo docker restart <current-container-id>"
# verify: ssh ... "sudo docker logs --tail 20 <container-id>" -- look for "Morellia test server ready"
```

Then run the actual test:
```bash
gh workflow run "Load test" --repo DCFiendish/rust-mc-bot -f target=0.0.0.0:25567 -f count=20 -f duration_seconds=60
```
Start small (~20 bots, so ~5 attackers) before scaling up — this whole path is unverified
end-to-end against a live server (deployment was blocked before it could be tested). Watch for:
- Bot-side log: `"bot \"Bot_N\" placing war flag at (x, y, z)"` lines
- Server-side `docker logs`: either `[War] ... is attacking ...` broadcasts (success) or one of
  `ErrorChunkNotEdge` / `ErrorSkyBlocked` / `ErrorFlagTooHigh` / `ErrorAlreadyUnderAttack` /
  `[War] Cannot claim unless you are part of a town` (would mean the `LoadTestBots.kt` town
  assignment didn't take — check `Town.fromName` actually found "Testville"/"Secondtown" before
  digging further)
- Note the concurrency ceiling: only 4 chunks per territory (8 total), and nodes allows one
  active attack per chunk — more than 8 real attackers will mostly just hit
  `ErrorAlreadyUnderAttack`, not add real load. That's a test-world content limit, not a bug.

### Test-world layout (for context, from `server/world.json`)

Flat world (`fillHeight(0, 60, STONE)` — solid ground at Y≤59, clear air/sky above). Two
territories, `chunkAttackTime` overridden to 7500ms in `Main.kt` for fast capture during testing
(intentionally NOT reverted — see comment in `Main.kt`):
- **Testville**: chunks (0,0)(1,0)(0,1)(1,1) → flag targets used: (8,59,8) (24,59,8) (8,59,24)
  (24,59,24), block placed at Y=60
- **Secondtown**: chunks (5,0)(6,0)(5,1)(6,1) → flag targets: (88,59,8) (104,59,8) (88,59,24)
  (104,59,24)

## Hard-won reference: confirmed packet IDs for this exact Minestom build

Build: `net.minestom:minestom:2026.07.12-26.2` (protocol version **776**, NOT upstream's default
772 — confirmed by decompiling `HandshakeListener`). These drifted from wiki.vg/upstream
defaults; all confirmed by decompiling the actual jar (`javap -p -c -constants` on
`PacketVanilla.class` and each packet class), not assumed. Jar location for re-deriving more if
needed:
```
C:\Users\USER\.gradle\caches\modules-2\files-2.1\net.minestom\minestom\2026.07.12-26.2\6f3c57e244ee008e99156456d949437108db42cc\minestom-2026.07.12-26.2.jar
```

| Packet | ID | Used? |
|---|---|---|
| ClientTeleportConfirmPacket | 0x00 | yes |
| ClientCommandChatPacket (slash commands) | 0x07 | found, not used (town assignment moved server-side instead) |
| ClientChatMessagePacket | 0x09 | yes |
| ClientKeepAlivePacket | 0x1C | yes |
| ClientPlayerPositionAndRotationPacket | 0x1F | yes |
| ClientPlayerActionPacket (dig) | 0x29 | yes |
| ClientEntityActionPacket (sneak/sprint) | 0x2A | yes |
| ClientHeldItemChangePacket | 0x35 | yes |
| ClientAnimationPacket (swing) | 0x3F | yes |
| ClientCreativeInventoryActionPacket | 0x38 | found, NOT used — item-registry protocol ID + component encoding was judged too fragile to hand-roll blind; used server-side `player.inventory.setItemStack()` instead |
| ClientPlayerBlockPlacementPacket (place block) | 0x42 | yes, new this session |

## Other pending items (not urgent, not touched this session)

- Task #13 from the standing list: replace nodes-map loading screen logo with Morellia branding —
  blocked on the user producing artwork.
- Phase 2 (real combat/damage in the load-test bot) was explicitly scoped but deferred — would
  need the bot to parse entity-spawn packets to get target entity IDs, meaningfully more work
  than Phase 1/3.

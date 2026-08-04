# Handoff: war-flag load-test fix — finish verification, then commit/push

Picks up mid-task. Read this fully before doing anything — it replaces the need to re-derive
context from chat history.

## Where things stand

The war-flag load test (bots swarming a Minestom server and placing flags to trigger
`nodes`' `FlagWar.beginAttack`) was silently never working. Root-caused three separate bugs by
reading real source + live production data (not guessed), fixed all of them, and verified two of
the three with a real local end-to-end run (real server + real compiled bot binary). The third
part — proving a flag placement actually succeeds server-side — is the one unfinished piece.

**Standing constraint, never violate:** no Claude/Anthropic/AI authorship signature or
`Co-Authored-By: Claude` trailer in any commit or file, in any repo, ever. Commits must look like
the user's own work. Git identity to use: `DC Fiendish <johnpatricl.ortman@gmail.com>` (verify
with `git config user.name`/`user.email` in each repo before committing — don't assume it's set
globally).

## The three bugs (all fixed in code already)

1. **Missing town creation** — `Town.fromName` is lookup-only, never created. Fixed in
   [server/src/main/kotlin/net/morellia/server/LoadTestBots.kt](server/src/main/kotlin/net/morellia/server/LoadTestBots.kt)
   via `createTownIfMissing`.
2. **Missing/wrong territory geometry** — old dev fixture ("Testville"/"Secondtown") had a gap
   between the two territories and didn't exist in production at all. Fixed by switching to two
   real, adjacent, currently-unclaimed production territories: **440** (TownB home) and **275**
   (TownA home), which share 7 real chunk-border pairs (confirmed by computing adjacency against
   the live VM's real `world.json`, not assumed).
3. **Missing nation/enemy relationship** — `FlagWar.chunkIsEnemy` requires both towns to belong to
   `Nation`s with an explicit mutual `enemies` relationship; a bare town can never be a legal
   attack target. Fixed via `ensureAtWar` in the same file, creating `NationA`/`NationB` and
   calling `Nation.addEnemy`.

Plus a **separate, unrelated real bug** found in `rust-mc-bot`: `start_bots` spawns one thread per
CPU, and the `id:` field was set from the per-thread-local loop variable instead of a globally
unique number — with 28 CPUs and 16 bots, almost every bot's local index was 0, so
`ATTACK_FRACTION`/faction-parity checks (`bot.id % N`) broke. Fixed by using `name_offset + bot`
instead.

### Verified so far (real local run: actual `./gradlew run` server + actual compiled `rust-mc-bot.exe`)
- Exactly 4/16 bots attacked (correct `ATTACK_FRACTION`), all even-ID (`Bot_0/4/8/12`, correct
  `TownA`/`TownB` parity split), each hitting a distinct target chunk with zero repeats — the
  atomic-counter-per-faction fix and the `bot.id` threading fix both confirmed working.
- `towns.json` afterward: `NationA`/`NationB` created correctly with mutual `enemies` (`NationA
  .enemies: ["NationB"]` and vice versa), residents enrolled matching correct parity.
- `TownA`/`TownB`'s `captured` list stayed **empty** — determined this is *expected and
  uninformative* for a non-core-chunk capture: `FlagWar.finishAttack` only appends to `captured`
  when the **core chunk** specifically flips; a border-chunk capture just sets
  `TerritoryChunk.occupier` (runtime-only state, not present in `world.json`'s schema at all — so
  file inspection after the fact can't distinguish "attack succeeded" from "attack never
  started").
- Grepped server console log for `"[War]"`/`"captured"` broadcast text and found nothing —
  **this is not evidence of failure.** Read `Message.kt` and confirmed `Message.broadcast`/
  `.error`/`.print` all route through Adventure's `sendMessage`/`Audiences.all().sendMessage()` —
  real network packets to connected clients, never `println` to console (the only `println`
  fallback requires a `null` sender, which doesn't apply to a real connected bot). **Do not
  conclude the fix failed based on absence of `[War]` text in the server console log** — that
  channel structurally cannot show it.

### The one thing still unverified
Whether `beginAttack` is actually being reached and succeeding, vs. failing some check silently.
The only channel that can answer this for certain is the actual chat/system-message packet the
server sends back to the connecting client on success (`"is attacking..."`, sent from
`NodesWorldListener.kt:203`) or failure (`Message.error(...)`, `NodesWorldListener.kt:206+`) —
because these are real packets a connected bot receives, unlike the console log.

## Immediate next step — exactly where I stopped

Was mid-way through finding the real Minestom wire packet ID for the outgoing system-chat packet,
so a temporary handler can be added to `rust-mc-bot` to print whatever message text the server
actually sends back after a flag placement.

Deployed Minestom build in use: `net.minestom:minestom:2026.07.12-26.2`,
sha `6f3c57e244ee008e99156456d949437108db42cc`. Jar re-extracted fresh into
`...\scratchpad\minestom_src\extracted2\` (2529 `.class` files — confirmed still present).
Confirmed the packet class is
`net/minestom/server/network/packet/server/play/SystemChatPacket.class`.

Confirmed `PacketVanilla`'s registries are **package-private static fields**
(`static PacketRegistry<...> SERVER_PLAY;` etc.) — not directly inspectable via `javap -p` field
listing beyond seeing the field exists, because the actual `Class -> id` mapping is built at
runtime inside a static initializer block (`static {}`), not stored as literal constants `javap`
can print. `PacketRegistry` (the interface backing `SERVER_PLAY`) exposes:
```
PacketInfo<T> packetInfo(Class<?> packetClass)
PacketInfo<T> packetInfo(int id)
```
So `javap -p -c` disassembly filtered for `"chat"` on `PacketVanilla` came back **empty** — that
was the wrong approach; the ID isn't a symbol name, it's an int assigned in a registration table
built at class-init time. **Next step: don't keep trying `javap` on `PacketVanilla` — instead
either (a) decompile the static initializer body with `javap -c -p PacketVanilla` (no filter,
full disassembly) and manually count `SystemChatPacket`'s position in the `SERVER_PLAY`
registration sequence, or (b) — much faster — write a 5-line throwaway Kotlin/Java snippet that
calls `PacketVanilla.SERVER_PLAY.packetInfo(SystemChatPacket.class).id()` (or equivalent accessor
on `PacketInfo`, check its shape with `javap -p PacketRegistry\$PacketInfo` first) and just prints
the real int at runtime — this sidesteps bytecode reading entirely and matches how the actual
server resolves it.** Given this project already has a working Minestom classpath (the `server`
Gradle module), the fastest path is a tiny one-shot `main()` in that module (same pattern as the
already-existing throwaway diagnostics `BootTest.kt`/`WarTestProbe.kt`) that prints
`PacketVanilla.SERVER_PLAY.packetInfo(SystemChatPacket::class.java)`'s id, run via a new
temporary Gradle `JavaExec` task, then deleted once the ID is known.

Once the real packet ID is known:
1. Add a temporary handler in `rust-mc-bot`'s `src/packet_processors.rs` (check
   `states/play.rs` too for where incoming packets get dispatched) that matches that packet ID and
   prints the message text field.
2. `cargo build --release` (kill any stale `rust-mc-bot.exe` first with
   `taskkill //F //IM rust-mc-bot.exe` if the build fails with an "Access is denied" file-lock
   error — this has happened twice already this session).
3. Fresh local end-to-end run: kill any old server process, wipe
   `server/morellia-data/nodes/towns.json`/`war.json`/`backup` (world.json can stay — it already
   has the right two-territory fixture, see below), `./gradlew.bat run` in `server/`, then run
   `rust-mc-bot.exe 127.0.0.1:25567 16` from the built bot.
4. Read the actual chat text the bot prints. **This is the real answer** — only report success to
   the user once this text confirms it (either the "is attacking..." success message, or a
   specific `Message.error` reason that reveals what's still wrong).

## After verification succeeds (or reveals a remaining bug to fix)

These are explicit, already-given user instructions not yet acted on:

1. **Update `rust-mc-bot`'s README** to document the fixed war-flag logic: real border-chunk
   targeting (the 7+7 coordinate tables), atomic per-faction counters for distinct target
   assignment, the bot repositioning/teleport-before-place-block change, and the `bot.id`
   thread-local-index bug and fix. User's own words: *"make sure that this logic is at least
   communicated in the bot repo."* Don't just say "fixed some bugs" — explain the actual
   root-cause chain like the existing README's other sections do (it currently has a "Known
   Issues" entry from this session's earlier README pass — see
   [scratchpad/repo_bot/README.md](../../AppData/Local/Temp/claude/C--Users-patri-Aechronis/7a0026cb-26d8-4015-850e-29acf1848a39/scratchpad/repo_bot/README.md)
   — that section will likely need to move to a "Fixed" changelog entry or be replaced).
2. **Commit and push to GitHub** — user's exact words: *"make sure to also commit all this to
   github."* Concretely:
   - `rust-mc-bot`: has git + a real GitHub remote already
     (`https://github.com/DCFiendish/rust-mc-bot.git`, local clone at
     `...\scratchpad\repo_bot\`). `git status` there currently shows `src/main.rs` modified
     (92 insertions / 26 deletions), uncommitted. Commit this + the README update, push to
     `master`. Verify identity and absence of AI trailers before pushing, same as the two prior
     README commits this session (`275fe15`, `6497b18`).
   - **`C:\Users\USER\Aechronis` (the private server repo) has no `.git` at all** — confirmed via
     `git rev-parse --is-inside-work-tree` failing with "not a git repository." "Commit all this
     to github" was said without the user knowing this. Don't silently skip it and don't silently
     git-init it either — **ask the user** whether they want this directory git-initialized (and
     if so, to a private repo) so `LoadTestBots.kt`/`WarTestProbe.kt` changes can be pushed too,
     or whether "commit all this" was only ever meant to refer to the bot repo (which does have
     git). This was flagged as an open question before and never resolved — resolve it now rather
     than assuming either way.

## Cleanup owed (established convention, low priority, do last)

Throwaway diagnostic files that should be deleted once this round of work concludes, plus their
matching Gradle tasks in [server/build.gradle.kts](server/build.gradle.kts):
- `DiagnoseTerrainKt` / `diagnose` task
- `BootTestKt` / `bootTest` task
- `WarTestProbeKt` / `warTestProbe` task
- Plus whatever new temporary packet-ID-lookup `main()` gets added per the "immediate next step"
  above — delete it once the real ID is captured into `rust-mc-bot`'s source as a constant.

## Other loose ends from this session, still open (lower priority than the above)

- **`War-Comms` GitHub repo deletion** — confirmed empty (0 bytes), but `gh` lacks the
  `delete_repo` OAuth scope to delete it via API. User needs to either run
  `gh auth refresh -h github.com -s delete_repo` themselves and let it be deleted next session, or
  delete it manually via the GitHub UI (Settings → Danger Zone). Not confirmed done either way —
  ask if it's still there.
- Territory #22 ("Integrate premade world with territory/Nodes system") and the terrain-generation
  /nation-painting decision plan (`C:\Users\USER\.claude\plans\eager-leaping-goose.md`) are a
  separate, unrelated thread from before the portfolio/war-flag detour — not touched this session,
  still waiting on the user to pick a terrain-source option and historical era. Not part of this
  handoff's scope; mentioned only so it isn't mistaken for abandoned/forgotten.

## Reference data (so nothing needs re-deriving)

- Live Oracle VM: `0.0.0.0`, SSH user `ubuntu`, Pterodactyl container
  `00000000-0000-0000-0000-000000000000`, real data at
  `/var/lib/pterodactyl/volumes/<container-uuid>/morellia-data/nodes/`. **Never bulk-copy this
  file to local disk** — a full `cat > local_file` redirect was blocked by Claude Code's own
  classifier last time; use scoped remote Python one-liners that print only the specific fields
  needed instead (this is also just better hygiene for production data).
- Local test fixture already in place at
  [server/morellia-data/nodes/world.json](server/morellia-data/nodes/world.json): exactly the two
  real production territories 440 and 275, nothing else — safe to keep using, don't regenerate
  unless it goes stale.
- Territory 440 core chunk `[148,126]` (~90 chunks), territory 275 core chunk `[139,124]`
  (~89 chunks), 7 real shared border-chunk pairs between them.
- `rust-mc-bot`'s target coordinate tables (already in `main.rs`, all real ground-level Y values
  read from the live `EuropeTerrain` generator via `WarTestProbe.kt`):
  ```rust
  const TOWN_A_ATTACK_TARGETS: [(i32, i32, i32); 7] = [
      (142, 128, 89), (143, 123, 91), (143, 124, 89), (143, 125, 85),
      (143, 126, 86), (143, 127, 88), (144, 122, 97),
  ];
  const TOWN_B_ATTACK_TARGETS: [(i32, i32, i32); 7] = [
      (143, 128, 92), (144, 123, 91), (144, 124, 88), (144, 125, 87),
      (144, 126, 90), (144, 127, 87), (145, 122, 94),
  ];
  ```
  (chunk_x, chunk_z, ground_y) — must stay in sync with `LoadTestBots.kt`'s
  `TOWN_A_HOME_TERRITORY = 275` / `TOWN_B_HOME_TERRITORY = 440` if either territory ever gets
  claimed by a real town before launch and the whole pair needs to move.

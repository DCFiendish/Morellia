# Cross-Library Integration Gaps

Bugs and questions that live in the seams between `combat`, `nodes`, `vanilla`, and `utils` — nothing that follows is fully owned by any single per-library deep-dive doc, which is why each of these has stayed unresolved even after a thorough per-library audit.

## Resolved (2026-07-30): the vehicle system IS wanted, and IS reusable for wagons/cannons — not modern-only code

Originally flagged as an open question ("does this project even need `combat`'s vehicle system at all, given it was built for Aechronis's own modern-military theme?"). Read `Vehicle.kt`, `Car.kt`, `Tank.kt`, and `Ammo.kt` directly to answer it properly — **the class hierarchy is generic, not modern-specific**:

- **`Vehicle` (base class)**: an `ITEM_DISPLAY`-entity rideable thing with hitbox/health/seats/place-spawn-enter-exit-tick-damage-destroy hooks. Nothing theme-specific.
- **`Car extends Vehicle`**: ground movement is purely tunable numbers (`maxSpeed`/`acceleration`/`braking`/`friction`/`turnSpeed`/`maxClimbHeight`) — no engine concept, no combustion-specific sounds/logic anywhere in the class. **Directly reusable as the base for a Wagon** — reskin the model, retune the numbers (lower speed/acceleration for a horse-drawn feel), done. No new movement code needed.
- **`Tank extends Car`**: layers a second/third item-display (turret + barrel) that tracks the driver's aim with a traverse-speed clamp, plus a `fire()` method — raycast from the barrel tip, explode on obstruction or spawn a real travelling `Projectile` (fully configurable model/speed/explosion radius/damage/ammo type) on a cooldown. **This is architecturally exactly a cannon**: chassis + aimable gun + explosive shell on a cooldown. For a towed/stationary field gun, extend `Tank` (or `Vehicle` directly, skipping `Car`'s driving physics) with `maxSpeed = 0`, a slower/lobbed `projectileSpeed`, and optionally restricted turret traverse.
- **Confirmed reusable across ammo types too**: `AmmoTypes` (`Ammo.kt`) currently has `NORMAL`/`EXPLOSIVE`/`MISSILE`/`BOMB` — no dedicated shell/cannonball type yet, but it's a one-line enum addition, not a structural blocker. **Remember combat bug H6** (`COMBAT_DEEP_DIVE.md`) when adding one: `Health.takeHp()`'s flat per-ammo-type damage lookup silently no-ops if a new ammo type has no matching entry for whatever it's meant to hit — don't forget the damage-table entries for new Wagon/Cannon health objects.

**Conclusion: the vehicle system stays in scope, build wagons/cannons by extending `Car`/`Tank` with new assets and tuned stats rather than forking or stripping the code.** This means the vehicle-related bug list in `COMBAT_DEEP_DIVE.md` (H3, H6, H7, H8, M2–M7, M10, M12, and the vehicle LOW items) is **not moot** — this project will actually exercise that code, so those bugs remain real launch-blocking work, not dead-code cleanup.

## Combat-tag vs. friendly-fire listener ordering — unverified, potentially live bug (2026-07-30)

Discovered while correcting `RESEARCH.md` §12 (the combat-tag mechanic turned out to already exist in `vanilla`, not be a from-scratch build — see that section for the full correction). `nodes/listeners/NodesPlayerDamageListener.onDamage` cancels ally/nation friendly-fire `EntityDamageEvent`s (`event.isCancelled = true`) when `Nodes.config.allowNationFriendlyFire`/`allowAllyFriendlyFire` is false, registered on `Nodes.eventNode` at default priority. `vanilla/listeners/CombatListener.onDamage` tags both participants for combat on **any** non-self `EntityDamageEvent`, registered on a separate child node (`EventNode.all("vanilla-combat").setPriority(1000)`), and never checks `event.isCancelled`.

Because these are two independent event-node trees rather than listeners on the same node, it's unconfirmed whether Minestom guarantees `nodes`' cancellation happens before `vanilla`'s listener runs — and even if ordering were guaranteed, `vanilla`'s listener doesn't check cancellation state anyway. Net effect: **if friendly fire is intentionally allowed for ally/nation sparring, sparring partners likely get incorrectly combat-tagged** (and combat-tag now has real teeth — disconnect while tagged is an instant-kill, confirmed in `RESEARCH.md` §12's correction) even though the "attack" was cancelled/non-hostile. This is a real correctness question against the current live behavior of both libraries as they stand today, not just a design constraint for a system not yet built. Needs a direct test against the pinned Minestom build to confirm actual dispatch order across separate event-node trees, then either an explicit `event.isCancelled` check added to `CombatListener.onDamage`, or confirmation that Minestom's cross-node dispatch already handles this safely.

## Ore-mining double-drop bug

Already flagged in `RESEARCH.md` §11, but restated here because it's the clearest example of a cross-library seam bug: breaking a configured ore-block-eligible stone can fire **both** `nodes`' ore-sampler and `vanilla`'s block-drop system, double-dropping resources. Needs either:
- excluding those block types from `vanilla`'s `blockDropsEnabled`/`blockDrops` map, or
- confirming listener priority/ordering so only one system ever resolves the drop.

This compounds with `VANILLA_DEEP_DIVE.md`'s HIGH item that `blockDrops` is already missing most ore→resource conversions (Iron/Gold/Diamond/Emerald/Redstone/Lapis/Coal/Quartz/Copper) — **fix both at once**, not separately, since the missing-drop-table fix will change which blocks even need the double-drop guard.

## Max-health attribute reconciliation

`VANILLA_DEEP_DIVE.md` flags that `PlayerData.kt` only restores health on first spawn, with no reconciliation against max-health attribute modifiers `combat` or `nodes` might apply (e.g. if a gun/vehicle/territory-tier system ever grants a max-health buff). Never checked against either library's actual code. Needs a direct cross-check: does `combat` or `nodes` apply any `Attribute.MAX_HEALTH` modifier anywhere today, and if one is added later (territory tier bonuses are a candidate), does `vanilla`'s spawn/respawn health-restore logic correctly account for it?

## Shared `utils/Command` base class — CONFIRMED (resolved 2026-07-30)

Verified directly against current source (local clones were 21–39 commits behind origin and have been fast-forwarded): `utils` now ships `src/main/kotlin/net/aechronis/utils/Command.kt`, a single `open class Command(name, permission, vararg aliases) : MinestomCommand`. All three of `combat` (`HatsCommand.kt`, `CombatAdminCommand.kt`), `nodes` (`NodesCommand.kt`), and `vanilla` import and extend this exact class — confirmed via `import net.aechronis.utils.Command` in each. This was a real recent refactor: `combat` deleted its own `utils/Permissions.kt`, `nodes` deleted its own `objects/Command.kt` and `utils/Permissions.kt`, and `vanilla` deleted its own `utils/Command.kt`, all in favor of the shared `utils` versions. No further action needed here — this item is closed.

## LuckPerms integration surface — mostly resolved

`utils/Permissions.kt` is now the single, centralized implementation used by all three libraries: a `Player.hasPermission(permission: String?)` extension that returns `true` for a `null` permission, otherwise queries LuckPerms and **fails closed** (returns `false`) on any exception unless `System.getenv("DEBUG").toBoolean()` is true. Confirmed consistent everywhere since it's one function, not three copies — the old worry about one library silently failing open is resolved by the refactor itself.

Still open:
- What permission nodes actually exist / are documented, beyond being implicit in each `Command("name", "some.permission.node")` call site — still needs an inventory pass across all three repos before configuring real LuckPerms groups.
- The `DEBUG` env var fail-open behavior is a real footgun if ever accidentally set in production — worth a one-line confirmation that no deployment config sets `DEBUG=true` by default.

## Newly discovered: a pre-Minestom Bukkit prototype of the vehicle system exists locally

Found at `C:\Users\USER\Minecraft Dev\aechronis\REDACTED-PROJECT-2-vehicles` — not a cloned Aechronis GitHub repo (no `.git` directory, not in the `gh repo list Aechronis` output), so this is local-only, likely reference/prototype material. It's a **Bukkit/Paper plugin** (`plugin.yml`, `org.bukkit.plugin.java.JavaPlugin`, `api-version: "1.21"`, soft-depends on ProtocolLib), named "CityBuildVehicles" v0.1.0, package `net.aechronis.vehicles`, containing `Car.kt`/`Plane.kt`/`Tank.kt`/`Drone.kt`/`Projectile.kt`/`Explosion.kt`/`Hitbox.kt`/`Vehicle.kt` — clearly the design ancestor of `combat`'s current Minestom-native vehicle system (same class names, same vehicle roster). Worth a light comparison pass if/when doing deeper vehicle work: this earlier Bukkit version may reveal original design intent, or bugs that were already fixed once during the Minestom port and could regress if vehicle code is touched again without awareness of this history. Not treated as an open research item requiring action now — just flagging its existence so it isn't mistaken for dead/unrelated code if encountered later.

## Dependency version drift — re-verified against current source (2026-07-30)

Re-checked directly against `master` on all four repos (note: an earlier pass at this same session accidentally read these files *before* fast-forwarding the local clones and briefly concluded there was a Minestom-version mismatch — that was a self-correction, not a real finding; disregard any note elsewhere referencing a `1.21.11`/`26.2` discrepancy). Actual current confirmed state:

| Repo | Pinned `net.minestom:minestom` | Kotlin plugin | Pinned `net.aechronis:utils` |
|---|---|---|---|
| `combat` | `2026.07.12-26.2` | 2.4.10 | `86a747b` (compileOnly) |
| `nodes` | `2026.07.12-26.2` | 2.4.10 | `eff1c8c` (implementation) |
| `vanilla` | `2026.07.12-26.2` | 2.4.10 | `86a747b` (compileOnly) |
| `utils` | `2026.07.12-26.2` | 2.4.10 | — (base library) |

Good news: Minestom version and Kotlin plugin version are now perfectly consistent across all four repos — that part of the previously-flagged drift is resolved (or was already resolved by the time these commits landed).

**Still a real, confirmed gap**: `nodes` pins `utils@eff1c8c` while `combat` and `vanilla` pin `utils@86a747b` — and `86a747b` is `utils`'s current `master` HEAD (i.e. `combat`/`vanilla` are on latest, `nodes` is on an older commit). This needs an explicit resolution (bump `nodes`'s pin to `86a747b`, or confirm `eff1c8c` is intentionally held back for a reason) before `world-server` is assembled — especially since `86a747b` is the commit that introduced the shared `Command`/`Permissions` refactor discussed above, so `nodes` may be running against a meaningfully different `utils` API surface than the other two libraries assume.

Also worth noting: `combat`/`vanilla` declare `utils` as `compileOnly` (not bundled, must be supplied by the consumer at runtime) while `nodes` declares it as `implementation` (bundled transitively). Confirm `world-server`'s own build correctly supplies `utils` at runtime for all three, or `combat`/`vanilla`'s LuckPerms-backed permission checks will fail at runtime with a `NoClassDefFoundError` despite compiling fine.

## LuckPerms bridge — provider changed since `RESEARCH.md` was written (researched 2026-07-30)

`RESEARCH.md` §6/§8 flagged risk around `dev.lu15:luckperms-minestom` (from `repo.hypera.dev`) — an unofficial community bridge, with the official LuckPerms-Minestom PR (`LuckPerms/LuckPerms#3521`) abandoned and the tracking issue (`#3077`) still open. **Current source shows this has already changed**: `utils`, `combat`, and `nodes`' test scope now depend on `com.conceptmc:luckperms-minestom:5.5-SNAPSHOT` from `https://maven.conceptmc.com/releases`, not `dev.lu15`. This is a different artifact from a different, unidentified maintainer — the old risk assessment doesn't transfer, and the new one is arguably worse:

- **No discoverable public source.** Extensive search (GitHub search API, web search) found zero public GitHub org, repo, or project page for "ConceptMC" related to Minestom/LuckPerms — nothing under that name shows up alongside the known community bridges (`Codestech1/LuckPerms`, `allycraftmc/minestom-perms`, Hypera's `dev.lu15` fork). This artifact cannot currently be code-reviewed, has no visible issue tracker, and no visible community — it may be a private/friend-maintained rebuild not intended for public distribution.
- **It is being actively built**, which is the one reassuring data point: Maven metadata shows the `5.5-SNAPSHOT` artifact's latest build is timestamped `2026-07-01`, build number `10` — someone is actively pushing updates, this isn't abandoned.
- **It's still a floating SNAPSHOT**, same risk class as the old `dev.lu15` dependency: a SNAPSHOT can be silently replaced/broken upstream with no version bump to pin against.
- **Action needed before launch, not just noted**: find out who actually maintains `maven.conceptmc.com` (ask directly — this may be someone known to whoever owns the Aechronis org, given how obscure/private this dependency is) and get direct confirmation of (a) source availability for audit, (b) storage backend defaults — this is the same still-open question from `04-world-and-data-architecture.md` about whether permissions default to per-process embedded storage (H2/SQLite), now specific to this bridge, and (c) what happens if this single maintainer stops publishing — is there a fallback plan (self-host a fork, switch to `Codestech1`'s port)?

# Networking, Auth, Events & Commands

## Authentication and proxy forwarding

`MinecraftServer.init()` takes an `Auth` argument: `Auth.Online()` (Mojang session verification) or `Auth.Offline()` (no verification — the default). Behind a proxy, the auth argument is *replaced* entirely — Mojang auth cannot run simultaneously with proxy forwarding: `MinecraftServer.init(new Auth.Velocity("secret_here"))` for Velocity's modern forwarding, confirming the mechanism already assumed in [../RESEARCH.md §7](../RESEARCH.md)'s hosting topology.

Velocity-side, `player-info-forwarding-mode` supports `none`, `legacy` (BungeeCord-format, ≤1.12), `bungeeguard` (plugin-compatible legacy), and `modern`. BungeeCord support exists via `BungeeCordProxy`, and cross-backend transfer uses the `bungeecord:main` plugin-message channel — matching what [../RESEARCH.md §7](../RESEARCH.md) already described as the shard-transfer mechanism (native 1.20.5+ transfer packets aren't yet first-class in Minestom).

Sources: [Proxies docs](https://minestom.net/docs/compatibility/proxies), [BungeeCordProxy javadoc](https://javadoc.minestom.net/net/minestom/server/extras/bungee/BungeeCordProxy.html), [Discussion #662](https://github.com/Minestom/Minestom/discussions/662).

## Player join/spawn flow

`AsyncPlayerConfigurationEvent` is the recommended hook (fires during login/configuration) — the listener calls `event.setSpawningInstance(instance)` to assign the player's world before they finish joining. `PlayerLoginEvent` can also set spawning instance/respawn point.

Sources: [AsyncPlayerConfigurationEvent javadoc](https://javadoc.minestom.net/net/minestom/server/event/player/AsyncPlayerConfigurationEvent.html), [Your first server](https://minestom.net/docs/setup/your-first-server).

**Relevance to hub↔world transfer**: this is the hook point where `world-server`/`hub-server` (RESEARCH.md §8) would each set their own spawn instance on join, and where a transfer-received player would be routed to the correct instance rather than a default one.

## Event system architecture — the EventNode tree

Minestom uses a **tree of `EventNode`s** rather than Bukkit's flat annotation-based listeners. The root is `MinecraftServer.getGlobalEventHandler()`; instances/entities expose their own `eventNode()`. Nodes filter by type (`EventNode.type(name, EventFilter.ENTITY)`), by predicate (`EventNode.value(name, filter, predicate)`), or accept anything (`EventNode.all(name)`); events propagate down through children/listeners only if they pass the filter. Nodes carry a `priority` field controlling execution order among siblings. Listeners register via `addListener(Class, Consumer)`, reusable `EventListener.of(...)`, full `EventListener` implementations, or a builder (`expireCount`, `filter`, cancellation handling).

Sources: [Events docs](https://minestom.net/docs/feature/events), [EventNode javadoc](https://javadoc.minestom.net/net.minestom.server/net/minestom/server/event/EventNode.html), [Implementation docs](https://minestom.net/docs/feature/events/implementation).

**Why this matters for this specific stack**: this design is exactly what lets independent libraries like `nodes`, `combat`, and `vanilla` each own separate subtrees so their listeners don't clash the way flat Bukkit listeners can — this is the underlying mechanism behind [../RESEARCH.md §8](../RESEARCH.md)'s confirmed pattern of each library exposing an `initialize()`/`init()` entrypoint that registers its own event node under the global handler. It's also directly relevant to [../NODES_DEEP_DIVE.md](../NODES_DEEP_DIVE.md)'s finding that `nodes`' world listener registers on both a `highPriorityEventNode` (permission gate, can cancel) and a `lowPriorityEventNode` (post-success side effects) — that two-node pattern is a direct, correct use of this priority mechanism, not a custom invention.

## Command system

Commands extend `Command`, constructed with name + aliases, and register via `MinecraftServer.getCommandManager().register(new MyCommand())`. Structure: a `Command` has multiple **syntaxes**, each a sequence of `Argument`s (literal + typed, e.g. `ArgumentType.Integer(...)`); `setDefaultExecutor` handles unmatched input, `addSyntax(executor, args...)` handles specific patterns, and `setCondition()` gates command visibility/execution per sender (this is the mechanism behind Aechronis's own permission-gating base class, `utils/Command.kt`, per [../RESEARCH.md §6](../RESEARCH.md)). Arguments support `setCallback` for parse-error handling and `setSuggestionCallback` for **custom tab-completion**, giving built-in suggestion support with no extra plugin needed.

Sources: [Commands docs](https://minestom.net/docs/feature/commands), [wiki/feature/commands.md](https://github.com/Minestom/wiki/blob/master/feature/commands.md).

**Note**: as already confirmed in [../RESEARCH.md §14](../NODES_DEEP_DIVE.md), this command system has no built-in stdin/console-input reader — `CommandManager` only exposes `execute()`/`executeServerCommand()`, so typing commands into a Pterodactyl console still needs a manually-written stdin loop calling `executeServerCommand()`, as already planned.

## Protocol version support

Minestom targets a **single current protocol version per release** (tracks latest Minecraft, e.g. 1.21.x) — there is no built-in multi-version layer. Multi-version support is handled externally: run **ViaVersion** on a proxy in front of Minestom, or use **ViaProxy** standalone if no proxy is present; ViaVersion rewrites packets between client and server protocol versions. Community proof-of-concept work exists to embed Via directly into Minestom ("ViaStom", from OpenMinigameServer's fork) but it isn't official/merged.

Sources: [Unsupported Versions docs](https://minestom.net/docs/compatibility/unsupported-versions), [Issue #94](https://github.com/Minestom/Minestom/issues/94), [Discussion #2269](https://github.com/Minestom/Minestom/discussions/2269).

**Decision needed, not yet made**: since Velocity is already the planned proxy ([../RESEARCH.md §7](../RESEARCH.md)), adding ViaVersion there is the natural place to support older clients if that's wanted — otherwise the server is locked to whatever single Minecraft version the pinned Minestom build targets (currently `26.2`-era, per the version string Aechronis's libraries pin to). Worth deciding explicitly rather than discovering it by default once older-client players can't connect.

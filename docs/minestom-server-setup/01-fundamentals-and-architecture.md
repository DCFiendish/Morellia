# Minestom Fundamentals & Architecture

## What it actually is

Minestom is "an open-source library that enables developers to create their own Minecraft server software, without any code from Mojang." The defining trait, straight from the project itself: **it ships with no gameplay features by default** — no default world, no combat, no inventories, no movement validation beyond raw protocol handling. Per the [Minestom GitHub README](https://github.com/Minestom/Minestom): "the main difference between Mojang's vanilla server and a Minestom-based server, is that ours does not contain any features by default."

This inverts the Paper/Spigot model. Paper starts with full vanilla behavior and plugins *subtract/modify* it. Minestom gives you networking and protocol plumbing only, and you *add* everything: an `InstanceContainer` for the world, event listeners for combat/physics/whatever gameplay you want. This is why Aechronis needed separate `vanilla`, `combat`, and `nodes` libraries in the first place — none of that exists in the base framework.

## The Instance model

`InstanceContainer` is the standard chunk-owning world implementation ([docs](https://minestom.net/docs/world/instances), [javadoc](https://javadoc.minestom.net/net.minestom.server/net/minestom/server/instance/InstanceContainer.html)). There's also `SharedInstance`, which shares chunk data with a parent container — useful for memory-efficient copies of the same world (e.g. multiple minigame arena instances from one template) without duplicating chunk data in memory.

## Threading model — the actual reason Minestom is fast

Rather than Paper's essentially single-tick-thread-per-world design, Minestom uses a `ThreadDispatcher` that splits work into "partitions" (chunks) and assigns each partition's `Tickable`s (entities, chunks) to a pool thread — entities are pinned to a known thread ahead of time. This is genuinely parallel, multi-core chunk/entity ticking, a structural difference from Paper's classic design (Folia aside). Sources: [Threading Model](https://mintlify.wiki/minestom/Minestom/core-concepts/threading), [Chunk management](https://minestom.net/docs/world/chunk-management).

This is directly relevant to [NODES_DEEP_DIVE.md](../NODES_DEEP_DIVE.md)'s highest-priority finding (H3): `nodes` is a Bukkit-to-Minestom port that retained plain, non-thread-safe `HashMap`/`HashSet` collections almost everywhere, under the (correct, for Bukkit) assumption that plugin logic runs single-threaded. Minestom's per-chunk parallel dispatch means that assumption doesn't hold here — this architecture note is the direct confirmation of why that finding matters.

## The Acquirable API — still current

Cross-thread entity/chunk access must go through `Acquirable` (`.sync(Consumer)`, `.lock()`/unlock, or non-blocking `.tryLock()` variants). If the calling thread already owns the object's tick-thread, access is direct with no synchronization overhead; otherwise the calling thread signals the owning thread and blocks until the acquisition is serviced. This is what makes the same code work whether you're running single-threaded or per-chunk-threaded. Sources: [Acquirable API docs](https://minestom.net/docs/thread-architecture/acquirable-api), [internals](https://minestom.net/docs/thread-architecture/acquirable-api/inside-the-api), [GitHub issue #119](https://github.com/Minestom/Minestom/issues/119).

**Practical implication for this project**: any new code written on top of Aechronis's libraries (the combat-tag system, voucher boosts, etc. from RESEARCH.md) needs to either go through `Acquirable` for cross-thread entity/instance access, or be confirmed to only ever run on the same thread as the state it touches — don't assume single-threaded safety by default the way the ported `nodes` code apparently did.

## The Extensions system — removed from mainline, don't build around it

Minestom historically had a plugin-like "Extension" system. **It has been removed from core Minestom.** Confirmed via the community changelog and follow-on tooling: the community fork `minestom-ce` and the third-party library `minestom-ce-extensions` ("a library for bringing extensions back to minestom-ce") exist specifically as a stopgap because base Minestom no longer provides it — using them requires code changes like swapping `MinecraftServer.getExtensionManager()` for `ExtensionBootstrap.getExtensionManager()`. Sources: [hollow-cube/minestom-ce-extensions](https://github.com/hollow-cube/minestom-ce-extensions), [Minestom issue #127 "Improve the extension system"](https://github.com/Minestom/Minestom/issues/127).

**Conclusion, matching this project's already-planned architecture**: build a single monolithic application (or a Gradle multi-module build, per [RESEARCH.md §8](../RESEARCH.md)'s `world-server`/`hub-server`/`common` structure), not a runtime plugin host. This isn't a workaround — it's the modern recommended structure for Minestom projects generally, and it's what RESEARCH.md §8 already planned for before this research pass confirmed it.

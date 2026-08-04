# Minestom Server Setup — Research Folder

Deep dive into Minestom itself (public docs/source) plus exactly how Aechronis's libraries expect to be consumed, so a new server project can actually be scaffolded and run. Companion to [../RESEARCH.md](../RESEARCH.md) (overall server planning) and [../NODES_DEEP_DIVE.md](../NODES_DEEP_DIVE.md) (deep bug audit of the `nodes` library specifically) — this folder is the "how does the underlying platform actually work" reference those two build on top of.

## Contents

1. [01-fundamentals-and-architecture.md](01-fundamentals-and-architecture.md) — what Minestom actually is, the Instance model, the multi-threaded chunk/entity dispatcher, the Acquirable API, and the (removed) Extensions system.
2. [02-project-setup-and-build.md](02-project-setup-and-build.md) — Gradle setup, Maven coordinates/versioning, JDK/Kotlin requirements, packaging into a runnable jar.
3. [03-runtime-ops-and-logging.md](03-runtime-ops-and-logging.md) — JVM/GC tuning status, built-in monitoring hooks, logging conventions, config-file conventions (there are none — everything's programmatic).
4. [04-world-generation-and-persistence.md](04-world-generation-and-persistence.md) — procedural generation, Anvil loading, and Polar (the community-favored format for real persistent maps).
5. [05-networking-auth-and-events.md](05-networking-auth-and-events.md) — auth modes, Velocity/BungeeCord forwarding, player join/spawn flow, the EventNode architecture, the command system, and protocol version support.
6. [06-entities-items-blocks.md](06-entities-items-blocks.md) — the basic gameplay primitives (entities, the DataComponents item model, blocks/BlockHandler/Batch).
7. [07-aechronis-server-scaffolding.md](07-aechronis-server-scaffolding.md) — the precise, practical guide to actually building a new server project on top of Aechronis's libraries: exact Gradle blocks, a confirmed bug in their own build config, a real version-drift risk between libraries, the `library` template's full scaffold, and their fully-automated (no-manual-step) release process.

## Headline takeaways

- **Minestom ships with nothing** — no default world, combat, or gameplay of any kind. Everything (world, mechanics, persistence) is added by your own code or a library like Aechronis's.
- **Java 25 is the current actual minimum** for Minestom itself (docs confirm this directly) — Aechronis's toolchain-25 convention isn't unusually bleeding-edge, it matches Minestom's own floor exactly.
- **The old Extensions/plugin-loader system is gone from mainline Minestom.** The modern, correct structure is one monolithic application (or a Gradle multi-module build, per [RESEARCH.md §8](../RESEARCH.md)), not a runtime plugin host.
- **Polar**, not Anvil, is the community-favored format for a real, hand-built, persistent, explorable world — Anvil support exists mainly for one-off conversion.
- **Aechronis's own build files have a real bug**: the Maven repository URL they use to *read* dependencies (`.../Aechronis/aechronis`, lowercase generic) doesn't match how any of their libraries actually *publish* (each to its own `.../Aechronis/{repo-name}` path) — a new server project needs the correct per-repo URLs, not a copy of what's in their existing `build.gradle.kts` files.
- **There's a real version-drift risk**: `nodes` and `combat` each pin a *different* commit-SHA of the shared `utils` library. Worth resolving explicitly before a new project depends on all three.
- **No JVM/GC tuning guidance is published for Minestom** (unlike Paper's well-known Aikar's flags) — this is an open question requiring empirical testing given Minestom's genuinely different multi-threaded tick model, not a value to assume carries over.

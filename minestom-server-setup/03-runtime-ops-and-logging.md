# Runtime, Ops, Logging & Configuration

## JVM/GC tuning — an open question, not a known answer

No Minestom-specific G1GC tuning guidance was found in official docs or general search — Minestom's own materials don't publish a startup-flag recommendation the way Paper's ecosystem has settled on ["Aikar's flags"](https://aikar.co/2018/07/02/tuning-the-jvm-g1gc-garbage-collector-flags-for-minecraft/) (`-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:G1NewSizePercent=30 ...`).

Given Minestom's genuinely different multi-threaded chunk/entity ticking model (per [01-fundamentals-and-architecture.md](01-fundamentals-and-architecture.md)) versus the largely-single-tick-thread design Aikar's flags were tuned around, **don't assume those flags carry over** — treat this as something to empirically test once the flagship world shard exists, not a value to bake into the Pterodactyl egg or startup scripts by default. This ties directly into the load-testing open item already flagged in [../RESEARCH.md §7](../RESEARCH.md) ("200 on one shard" needs verification) — JVM tuning should be part of that same testing pass, not decided in isolation beforehand.

## Memory footprint — no citable official figure

No official Minestom RAM-per-player benchmark was located. Because Minestom omits vanilla systems (pathfinding AI, villagers, redstone, etc.) unless you implement them, footprint is genuinely workload-dependent — anecdotally described as much lower than Paper for equivalent player counts, but this research pass could not source a citable official number. Treat the "Minestom needs less RAM per player than Paper" assumption used earlier in [../RESEARCH.md §4/§7](../RESEARCH.md) (hosting/VPS sizing) as directionally reasonable but empirically unverified — worth confirming with real numbers once the flagship shard is up and load-tested, rather than sizing final hosting purchases purely on this assumption.

## Built-in monitoring hooks

Minestom does expose real tick metrics: `MinecraftServer` exposes a current `TickMonitor` (tick time), and `Acquirable` exposes acquiring-time metrics (`Acquirable.resetAcquiringTime()`) — usable to build your own TPS/MSPT/lock-contention monitoring. Sources: [GitHub Discussion #1684](https://github.com/Minestom/Minestom/discussions/1684), [Performance docs](https://www.mintlify.com/minestom/Minestom/advanced/performance).

**There's no bundled ops dashboard** — you wire these into your own metrics exporter (e.g., a small Prometheus endpoint, or piping into whatever monitoring stack ends up watching the flagship shard). This is a real gap versus Paper's ecosystem of drop-in `/tps`-style plugins and third-party dashboards — budget for building this yourself, even if minimal (a simple `/tps`-equivalent admin command reading `TickMonitor` would cover the basics cheaply).

## Logging conventions

Minestom's own build depends on **SLF4J** as the logging facade, and uses Kyori Adventure's `ComponentLogger` (`net.kyori.adventure.text.logger.slf4j.ComponentLogger`) for Adventure-aware log output. It does not bundle a specific backend implementation opinion — consuming projects add their own SLF4J binding (Logback, tinylog, Log4j2, etc.). Some community server templates (e.g., `GufliMC/Brickstom`) use tinylog as a lightweight option for this slot.

**Practical choice needed**: `world-server`/`hub-server` (RESEARCH.md §8) each need an SLF4J binding picked and added as a dependency — not something Minestom or Aechronis's libraries decide for you. Logback is the safe, well-trodden default if no specific reason favors tinylog's smaller footprint.

## Configuration — there is none, by design

**Configuration is purely programmatic.** There is no Minestom-native config file (no `server.properties`/`config.yml` equivalent anywhere). Everything happens through code: `MinecraftServer.init()`, then explicit calls to `InstanceManager`, event-node registration, and `server.start(host, port)`. `MinecraftServer` acts as a service-locator singleton exposing subsystems (`getInstanceManager()`, `getGlobalEventHandler()`, etc.) that delegate to an internal `ServerProcess`.

This directly confirms and reinforces [../RESEARCH.md §8](../RESEARCH.md)'s finding that none of Aechronis's libraries read their own config files either (each takes a plain Kotlin data class constructed programmatically) — this isn't an Aechronis-specific choice, it matches the platform's own convention exactly. The plan to have `world-server`/`hub-server` each own a single YAML/HOCON config file, parsed into a top-level `ServerConfig` that then constructs each library's `XConfig` object, remains the right approach — there's no platform-level config layer to lean on instead.

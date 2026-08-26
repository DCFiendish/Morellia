# Project Setup & Build

## Maven coordinates and versioning

`net.minestom:minestom:<version>` on Maven Central, plus an optional `net.minestom:testing:<version>` artifact for test utilities ([Dependencies docs](https://minestom.net/docs/setup/dependencies)).

Three versioning schemes coexist:
- **Release versions** track GitHub releases, currently in the `26.x` line (e.g. `26.1.2`/`26.2`).
- **Continuous snapshots** publish per-branch as `<branch>-SNAPSHOT` (e.g. `master-SNAPSHOT`) via the Sonatype snapshots repo.
- **Pinned timestamped builds** like `2026.03.25-1.21.11` (date + targeted Minecraft version) — this is the style Aechronis's libraries actually pin to (`2026.07.12-26.2`, confirmed identical across every one of their repos in [07-aechronis-server-scaffolding.md](07-aechronis-server-scaffolding.md)).

Sources: [Minestom GitHub](https://github.com/Minestom/Minestom), [Maven Central listing](https://mvnrepository.com/artifact/net.minestom/minestom).

## JDK requirement

Current official docs state plainly: **"Minestom requires Java 25 or newer,"** plus Gradle ≥9.1 and IntelliJ ≥2025.2 ([Dependencies docs](https://minestom.net/docs/setup/dependencies)). This is a recent bump — the now-archived legacy wiki (as of June 2024) still showed "Java 21 or newer" ([archived wiki page](https://github.com/Minestom/wiki/blob/master/setup/dependencies.md)), confirming Minestom moved its floor from 21→25 as the ecosystem adopted newer JDKs.

**This directly explains Aechronis's toolchain choice**: every one of their libraries pins `java.toolchain.languageVersion = JavaLanguageVersion.of(25)` — not unusually bleeding-edge, just matching Minestom's own current minimum exactly.

## Kotlin interop

Minestom is pure Java, but it's commonly consumed from Kotlin (as Aechronis does throughout). `MCCoroutine` (`com.github.shynixn.mccoroutine:mccoroutine-minestom-api`) adds coroutine-based event handlers and command executors (`suspend fun onPlayerJoinEvent(...)`) on top of the plain Java API — [MCCoroutine docs](https://shynixn.github.io/MCCoroutine/wiki/site/coroutine/). Aechronis's own libraries don't appear to use this (their event listeners are plain callback-style per the nodes/combat deep dives), but it's a real option worth considering for any new async-heavy code written on top of them.

## Packaging — you build your own runnable jar

Unlike Paper (download `paper-X.jar`, done), Minestom has no separate downloadable server jar — you build your own runnable fat jar from your project. Official docs show Gradle setup using the **Shadow plugin** (`com.gradleup.shadow`, version 8.3.0 in current docs), with a `shadowJar` task producing the uber-jar in `build/libs/`, manifest configured with your main class. The Maven equivalent uses `maven-assembly-plugin` with the `jar-with-dependencies` descriptor. ([Your first server docs](https://minestom.net/docs/setup/your-first-server))

A minimal main class is essentially:
```java
MinecraftServer server = MinecraftServer.init();
InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
instance.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));
server.start("0.0.0.0", 25565);
```

**Relevance to this project's Pterodactyl plan** (already covered in [../RESEARCH.md §14](../RESEARCH.md)): since there's no download-a-jar step, the Pterodactyl egg's start command needs to actually be `java -jar <your-shadow-jar>.jar` pointed at whatever your build produces — there's no equivalent of Paper's "downloads.papermc.io" install step to configure in the egg.

## What this means for `world-server`/`hub-server` (RESEARCH.md §8)

Each of the two server modules planned in RESEARCH.md §8 needs its own Shadow-jar (or equivalent) build producing an independent runnable jar — `world-server` bundling `nodes`+`combat`+`vanilla`+`worldedit`+`logger`, `hub-server` bundling just `vanilla`. Both depend on Minestom itself via the same Maven Central coordinate; only the Aechronis-library dependency set differs between the two module's `build.gradle.kts` files.

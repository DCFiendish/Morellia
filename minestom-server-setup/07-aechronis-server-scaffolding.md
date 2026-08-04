# Scaffolding a Server on Aechronis's Libraries

The precise, practical mechanics of actually depending on `nodes`/`combat`/`vanilla`/`utils`/`worldedit`/`logger` in a new server project — confirmed directly from every library's `build.gradle.kts` and CI workflow files, not inferred.

## 1. How these libraries are actually published and consumed

**GitHub Packages Maven, not JitPack, not a git submodule, not vendored source.** Every repo's `publishing{}` block publishes JARs to its **own** per-repo package: `https://maven.pkg.github.com/Aechronis/{repo-name}` — e.g. `.../Aechronis/nodes`, `.../Aechronis/combat`, `.../Aechronis/utils`.

**A confirmed bug in Aechronis's own build files, worth not copying**: each repo's `repositories{}` block (used for *reading* dependencies, not publishing) points at a *different*, seemingly-broken URL: `https://maven.pkg.github.com/Aechronis/aechronis` — lowercase, a generic "aechronis" package name that doesn't correspond to any real repo. This shared URL appears in `nodes`, `combat`, `vanilla`, `worldedit`, and `logger`'s build files, but since each library actually publishes under its own repo path, this reader URL looks like dead/copy-pasted boilerplate that wouldn't resolve `net.aechronis:utils` or any other cross-library dependency correctly. Tellingly, `utils` and `library` don't reference this URL at all.

**What a new server project should actually do instead**: add one `maven { }` repository block *per* Aechronis dependency, pointed at that library's own real publish path — e.g.:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Aechronis/utils")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    maven {
        url = uri("https://maven.pkg.github.com/Aechronis/nodes")
        credentials { /* same pattern */ }
    }
    // ...one block per library actually depended on (combat, vanilla, worldedit, logger)
}
```

**Credentials**: uniformly `project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")` / `gpr.token` ?: `GITHUB_TOKEN`, for *reading* packages — confirmed consistent across every repo. Publishing (irrelevant to a consuming server project, but confirmed for completeness) always uses plain `GITHUB_ACTOR`/`GITHUB_TOKEN` env vars with no `gradle.properties` fallback, since it only ever runs in CI.

## 2. The `Aechronis/library` template — the actual scaffold to model a new project on

Full, literal structure (this is the org's own template for a new Minestom library, and the closest thing to an official "how we structure a project" reference):

```
build.gradle.kts       (Kotlin 2.3.20, ktlint 14.0.1, publishes only — no reader repo besides mavenCentral)
settings.gradle.kts    → rootProject.name = "library"
gradle.properties      → kotlin.code.style=official
gradle/wrapper/gradle-wrapper.properties → Gradle 9.6.1
gradlew / gradlew.bat
.github/workflows/main.yml       (on push to master: setup-java temurin 25 → ./gradlew test → ./gradlew publish, GITHUB_TOKEN from secrets)
.github/workflows/test-pr.yml    (on pull_request: ./gradlew test only, no publish)
src/main/kotlin/net/aechronis/library/Main.kt    → object Main { fun init() {...} } with load-time print + shutdown hook
src/test/kotlin/.../LibraryTest.kt   → boots a REAL Minestom test server (MinecraftServer.init(Auth.Online()), instance, TPS bossbar, gamemode command), calls Main.init()
src/test/kotlin/.../TestGenerator.kt → simple flat-stone world generator
README.md → "publishes to github packages (f*ck self hosted repos)"
```

Its `build.gradle.kts` uses `compileOnly(minestom)` for the main source set, `implementation(minestom)` for tests only — i.e., **the consuming server is expected to supply Minestom itself**, not have it bundled transitively. A `keepRunning` system property lets the test server stay alive for manual play-testing — a handy pattern worth reusing for `world-server`/`hub-server`'s own dev-testing setup.

## 3. Dependency graph and version pins — a real drift to resolve

- **Minestom is pinned identically everywhere**: `net.minestom:minestom:2026.07.12-26.2`, no mismatch across any library.
- `nodes` → `implementation(utils:eff1c8c)`
- `combat` → `compileOnly(utils:86a747b)`
- `vanilla` → `api(minestom)`, `api(gson:2.14.0)`, `compileOnly(utils:86a747b)`
- `logger` → `compileOnly` on `utils:86a747b`, `vanilla:dc271de`, `worldedit:e62b2bb`, plus `h2:2.4.240`, `HikariCP:7.1.0`
- `worldedit` → `compileOnly(utils:86a747b, minestom)`, `api(worldedit-core:7.4.3)` (excludes gson), `guava:33.5.0-jre`, `fastutil:8.5.18`
- `utils` → the base library, `api(minestom)`, `compileOnly(luckperms-minestom:5.5-SNAPSHOT)`, no Aechronis-internal dependencies
- `library` → no Aechronis dependencies (it's the empty scaffold)

**The real issue**: `nodes` pins `utils@eff1c8c` while `combat` (and `vanilla`, `logger`, `worldedit`) all pin `utils@86a747b` — a **different commit-SHA of the same library**. If a new server project depends on `nodes` and `combat` together (exactly the plan for `world-server`, per [../RESEARCH.md §8](../RESEARCH.md)), Gradle will resolve `utils` to whichever version wins dependency resolution (typically the highest/newest by default conflict resolution, but this isn't guaranteed safe) — meaning the actual `utils` version `combat` was built and tested against might not be the one it actually runs with in the final server jar. **This needs to be resolved explicitly** (e.g. a forced dependency version, or confirming both SHAs are compatible) before `world-server` is assembled, not left to Gradle's default conflict resolution to silently paper over.

Also flagged: `worldedit` and `library` use an older Kotlin plugin version (2.3.20) than the rest (2.4.10) — likely low-risk but worth being aware of if any binary-compatibility issues surface.

## 4. CI/release automation — fully automated, no manual step, no floating "latest"

Identical pattern across all 6 published repos: `.github/workflows/main.yml` triggers on every push to `master`, runs tests, then `./gradlew publish` using the ephemeral `secrets.GITHUB_TOKEN` — **no manual publish step, no tags/releases used at all**. This is exactly what produces the commit-SHA-style versions (`version = System.getenv("GITHUB_SHA")?.take(7) ?: "local"`): every merge to master is a new immutable package version named after that commit's short SHA. A separate `test-pr.yml` runs tests-only on pull requests (no publish).

**Practical consequence for this project**: there is no floating "latest" version and no semver releases to depend on — a consuming server project (`world-server`/`hub-server`) must explicitly pin a specific commit-SHA version of each Aechronis library, and **updating means watching each repo's master-branch commit history** to find the SHA of whatever change you actually want, then bumping the pinned version by hand. This is a real, ongoing maintenance cost worth planning for (e.g., a periodic manual check of each repo's latest commits) rather than assuming dependency updates will ever "just happen" via a version range or `+`/`latest` selector — those won't work against this publishing scheme at all.

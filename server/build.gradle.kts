plugins {
    application
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "net.morellia"
version = "local"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

repositories {
    mavenCentral()
    maven("https://repo.hypera.dev/snapshots/") // transitive dep of utils/vanilla (Spark)
    maven("https://maven.conceptmc.com/releases") // com.conceptmc:luckperms-minestom — unaudited SNAPSHOT, test-only use

    // One block per Aechronis library actually depended on — each library publishes to its
    // own GitHub Packages path (Aechronis/<repo>), not a shared "Aechronis/aechronis" package.
    // See ../minestom-server-setup/07-aechronis-server-scaffolding.md for why.
    maven {
        url = uri("https://maven.pkg.github.com/Aechronis/utils")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    maven {
        // Fork of Aechronis/vanilla (https://github.com/Aechronis/vanilla) carrying Morellia-specific
        // fixes — see DCFiendish/vanilla README for details. All credit for the original library
        // belongs to Aechronis.
        url = uri("https://maven.pkg.github.com/DCFiendish/vanilla")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    maven {
        // Fork of Aechronis/nodes (https://github.com/Aechronis/nodes) carrying Morellia-specific
        // fixes — see DCFiendish/nodes README for details. All credit for the original library
        // belongs to Aechronis.
        url = uri("https://maven.pkg.github.com/DCFiendish/nodes")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    maven {
        url = uri("https://maven.pkg.github.com/Aechronis/combat")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

configurations.all {
    resolutionStrategy {
        // nodes pins utils@eff1c8c (2026-07-15); combat/vanilla/logger/worldedit pin utils@86a747b
        // (2026-07-19, newer). Force the newer one everywhere rather than let Gradle's default
        // conflict resolution silently pick one — nodes hasn't actually been tested against it.
        // Upstream has one commit past this (9a1266d, permission-check fix) but it never actually
        // published: their own CI failed on a ktlint violation in that same commit, so the
        // artifact doesn't exist in the registry. Also functionally moot for us either way — we
        // don't run real LuckPerms, and DEBUG=true (set on the live server) already forces the
        // same permissive fallback through the exception path both versions share.
        force("net.aechronis:utils:86a747b")
    }
}

dependencies {
    implementation("net.minestom:minestom:2026.07.12-26.2")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("net.aechronis:utils:86a747b")
    implementation("net.aechronis:vanilla:a074e09") // DCFiendish/vanilla fork — Slow Falling fall-damage fix + off-thread chunk/entity mutation fixes (Crops/Saplings/TreeFeller/EnvironmentalDamage/Food/Combat) + KillShop removed + save-race/fall-damage-teleport/ore-drop/autosave fixes + EnvironmentalDamage per-tick perf fix + Elevator/Storage/Commands/Recipes/Mannequin/Blocks thread-safety fixes + CombatListener respects nodes' friendly-fire cancellation + baseline movement anti-cheat (speed-hack/fly-hack) + fall-damage unloaded-chunk crash fix + Whitelist.save() synchronized/atomic + VanillaConfig require() validation
    implementation("net.aechronis:nodes:13e6871") // DCFiendish/nodes fork — lag/port-name/flight/war-restart/ore-dupe/territory-load/partial-load/dual-town/permissions/ghost-plots/war-save/ore-sampling fixes + unbounded protect-show particle task fix + public Nodes.enableWar() for programmatic startup + war thread-safety/ore-cache-wraparound/mutate-during-iterate/town-destroy-war-cleanup fixes + Nametag per-tick perf fix + cherry-picked upstream fixes: nation-membership-index desync, stale respawn point for ex-town-members + friendly-fire cancellation now runs on high-priority node so it beats vanilla's combat-tag + utils pin bumped to 86a747b
    implementation("net.aechronis:combat:2c63782") // bumped from 5a628df: frozen-scoped-player fix, car ground detection, vehicle hitbox/projectile collision, ammo-decrement, vehicle invis/invuln
    implementation("org.slf4j:slf4j-simple:2.0.18")
    // Not running LuckPerms at all — this is here purely so utils' Permissions.kt can resolve
    // LuckPermsProvider at runtime (NoClassDefFoundError otherwise) and fall through to its
    // intended graceful-denial path instead of crashing every permission-gated command.
    implementation("com.conceptmc:luckperms-minestom:5.5-SNAPSHOT")
}

application {
    mainClass.set("net.morellia.server.MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("morellia-server")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

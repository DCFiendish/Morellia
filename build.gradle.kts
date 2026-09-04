plugins {
    kotlin("jvm") version "2.4.10" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}

allprojects {
    group = "net.morellia"
    version = "local"

    repositories {
        mavenCentral()
        maven("https://repo.hypera.dev/snapshots/") // dev.lu15:spark-minestom (server's spark profiler)
        maven("https://repo.lucko.me/") // spark-common, spark-minestom's own transitive dep
        maven("https://oss.sonatype.org/content/repositories/snapshots/") // spark-common's own transitive deps
        maven("https://maven.conceptmc.com/releases") // com.conceptmc:luckperms-minestom — unaudited SNAPSHOT, server-only test dep
        maven("https://maven.enginehub.org/repo/") // com.sk89q.worldedit:worldedit-core (modules/worldedit)
        maven("https://mvn.everbuild.org/public") // org.everbuild.blocksandstuff:* (modules/vanilla signs/shelves/item-frames)
        maven("https://jitpack.io") // com.modernmt.text:profanity-filter (modules/vanilla chat filter)

        // One block per Aechronis library actually depended on — each library publishes to its
        // own GitHub Packages path (Aechronis/<repo>), not a shared "Aechronis/aechronis" package.
        // See docs/minestom-server-setup/07-aechronis-server-scaffolding.md for why.
        maven {
            url = uri("https://maven.pkg.github.com/Aechronis/utils")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

plugins {
    kotlin("jvm") version "2.4.10" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}

allprojects {
    group = "net.morellia"
    version = "local"

    repositories {
        mavenCentral()
        maven("https://repo.hypera.dev/snapshots/") // transitive dep of utils/vanilla (Spark)
        maven("https://maven.conceptmc.com/releases") // com.conceptmc:luckperms-minestom — unaudited SNAPSHOT, server-only test dep

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

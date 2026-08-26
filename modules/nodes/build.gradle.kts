group = "net.aechronis"
version = System.getenv("GITHUB_SHA")?.take(7) ?: "local"

plugins {
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

repositories {
    mavenCentral()
    maven("https://repo.hypera.dev/snapshots/") {
        name = "HyperaSnapshots"
        content {
            includeGroup("dev.lu15")
        }
    }
    maven {
        url = uri("https://maven.pkg.github.com/Aechronis/aechronis")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    // nodes' first dependency on vanilla (previously independent sibling libraries) -- see
    // NodesBlockPlacementCooldownListener.
    maven {
        url = uri("https://maven.pkg.github.com/DCFiendish/vanilla")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("net.minestom:minestom:2026.07.12-26.2")
    implementation("net.aechronis:utils:86a747b")
    // Real (non-cosmetic) block-placement cooldown enforcement lives in vanilla's
    // BlockPlacementCooldownListener -- see NodesBlockPlacementCooldownListener.
    implementation("net.aechronis:vanilla:96b593f")

    // testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testImplementation("org.slf4j:slf4j-simple:2.0.18") // logging (only used while testing at the moment)
}

tasks.test {
    useJUnitPlatform()

    systemProperty("keepRunning", System.getProperty("keepRunning", "false"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/DCFiendish/nodes")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

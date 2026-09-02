plugins {
    application
    id("com.gradleup.shadow") version "8.3.6"
}

configurations.all {
    resolutionStrategy {
        // Historically nodes/combat/vanilla pinned different net.aechronis:utils versions; both
        // modules/nodes and modules/vanilla now declare 86a747b directly, so this is redundant but
        // harmless — keeping it as a guard against a future module reintroducing a stale pin.
        force("net.aechronis:utils:86a747b")
    }
}

dependencies {
    implementation("net.minestom:minestom:2026.07.12-26.2")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("net.aechronis:utils:86a747b")
    implementation(project(":modules:vanilla"))
    implementation(project(":modules:nodes"))
    implementation(project(":modules:combat"))
    implementation("org.slf4j:slf4j-simple:2.0.18")
    // Perf profiler -- github.com/LooFifteen/spark's Minestom port of lucko/spark, see Main.kt's
    // SparkMinestom.builder() call. Only version currently published to repo.hypera.dev.
    implementation("dev.lu15:spark-minestom:1.10-SNAPSHOT")
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

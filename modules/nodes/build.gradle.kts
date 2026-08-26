apply(plugin = "org.jlleitschuh.gradle.ktlint")

dependencies {
    implementation("net.minestom:minestom:2026.07.12-26.2")
    implementation("net.aechronis:utils:86a747b")
    implementation(project(":modules:vanilla"))

    // testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testImplementation("org.slf4j:slf4j-simple:2.0.18") // logging (only used while testing at the moment)
}

tasks.test {
    systemProperty("keepRunning", System.getProperty("keepRunning", "false"))
}

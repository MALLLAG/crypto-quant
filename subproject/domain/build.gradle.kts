plugins {
    kotlin("jvm")
}

dependencies {
    // Arrow - Functional Programming
    api("io.arrow-kt:arrow-core:${property("arrowVersion")}")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutinesVersion")}")

    // Test
    testImplementation("io.kotest:kotest-runner-junit5:${property("kotestVersion")}")
    testImplementation("io.kotest:kotest-assertions-core:${property("kotestVersion")}")
    testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
}

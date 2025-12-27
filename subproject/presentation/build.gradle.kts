plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

ext {
    set("kotlin.version", property("kotlinVersion"))
}

dependencies {
    // Application module
    implementation(project(":subproject:application"))

    // Infrastructure module
    implementation(project(":subproject:infrastructure"))

    // Spring WebFlux
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Kotlin Coroutines with Reactor
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${property("coroutinesVersion")}")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

    // Jackson Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("io.kotest:kotest-runner-junit5:${property("kotestVersion")}")
    testImplementation("io.kotest:kotest-assertions-core:${property("kotestVersion")}")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${property("coroutinesVersion")}")
}

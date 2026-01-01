plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

ext {
    set("kotlin.version", property("kotlinVersion"))
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
    }
}

dependencies {
    // Application module (includes Domain transitively)
    api(project(":subproject:application"))

    // Spring R2DBC
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")

    // Spring WebClient for external API calls
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Kotlin Coroutines with Reactor
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${property("coroutinesVersion")}")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

    // kotlinx.serialization (JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // JWT (Upbit API 인증)
    implementation("com.auth0:java-jwt:4.4.0")

    // Rate Limiter (Bucket4j)
    implementation("com.bucket4j:bucket4j-core:8.7.0")

    // WebSocket (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Jackson Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("io.kotest:kotest-runner-junit5:${property("kotestVersion")}")
    testImplementation("io.kotest:kotest-assertions-core:${property("kotestVersion")}")
    testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${property("coroutinesVersion")}")
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:r2dbc:1.20.4")

    // WireMock (API 모킹 테스트)
    testImplementation("org.wiremock:wiremock-standalone:3.3.1")
}

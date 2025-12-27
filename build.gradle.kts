plugins {
    kotlin("jvm") version "2.0.10" apply false
    kotlin("plugin.spring") version "2.0.10" apply false
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
}

allprojects {
    group = "com.cryptoquant"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-Xcontext-receivers"
            )
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        val dependencies = this
        dependencies.add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5")
        dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        dependencies.add("detektPlugins", "com.wolt.arrow.detekt:rules:0.5.0")
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("${rootProject.projectDir}/detekt.yml"))
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        android.set(false)
        outputToConsole.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)
    }
}

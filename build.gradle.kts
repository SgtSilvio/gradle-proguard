@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.metadata)
}

group = "com.github.sgtsilvio.gradle"
description = "Gradle plugin to ease using ProGuard"

metadata {
    readableName.set("Gradle ProGuard Plugin")
    license {
        apache2()
    }
    developers {
        register("SgtSilvio") {
            fullName.set("Silvio Giebl")
        }
    }
    github {
        org.set("SgtSilvio")
        repo.set("gradle-proguard")
        issues()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("proguard") {
            id = "$group.$name"
            displayName = metadata.readableName.get()
            description = project.description
            implementationClass = "$group.$name.ProguardPlugin"
        }
    }
}

pluginBundle {
    website = metadata.url.get()
    vcsUrl = metadata.scm.get().url.get()
    tags = listOf("proguard", "obfuscation")
}

testing {
    suites.named<JvmTestSuite>("test") {
        useJUnitJupiter(libs.versions.junit.jupiter.get())
    }
}

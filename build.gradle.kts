plugins {
    `kotlin-dsl`
    alias(libs.plugins.pluginPublish)
    alias(libs.plugins.defaults)
    alias(libs.plugins.metadata)
}

group = "io.github.sgtsilvio.gradle"
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
    website.set(metadata.url)
    vcsUrl.set(metadata.scm.get().url)
    plugins {
        create("proguard") {
            id = "$group.$name"
            implementationClass = "$group.$name.ProguardPlugin"
            displayName = metadata.readableName.get()
            description = project.description
            tags.set(listOf("proguard", "obfuscation"))
        }
    }
}

testing {
    suites {
        "test"(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
        }
    }
}

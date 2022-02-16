plugins {
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish")
}

group = "com.github.sgtsilvio.gradle"
description = "Gradle plugin to ease using ProGuard"

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
            displayName = "Gradle ProGuard plugin"
            description = project.description
            implementationClass = "$group.proguard.ProguardPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/SgtSilvio/gradle-proguard"
    vcsUrl = "https://github.com/SgtSilvio/gradle-proguard.git"
    tags = listOf("proguard", "obfuscation")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:${property("junit-jupiter.version")}")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

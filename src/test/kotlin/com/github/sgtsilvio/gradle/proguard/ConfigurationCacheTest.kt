package com.github.sgtsilvio.gradle.proguard

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * @author Silvio Giebl
 */
class ConfigurationCacheTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun configurationCacheReused() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test"
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                java
                id("com.github.sgtsilvio.gradle.proguard")
            }
            repositories {
                mavenCentral()
            }
            val proguardJar by tasks.registering(proguard.taskClass) {
                inJars(tasks.jar)
                libraryJars("${'$'}{System.getProperty("java.home")}/jmods/java.base.jmod", "!**.jar;!module-info.class")
                outJars(base.libsDirectory.file("test-proguarded.jar"))
                mappingFile.set(layout.buildDirectory.file("test-mapping.txt"))
                rules.add("-keep class test.Main { public static void main(java.lang.String[]); }")
            }
            // copy inJars, libraryJars, outJars to check if they are compatible with the configuration cache
            val copyProguardJars by tasks.registering(Copy::class) {
                from(proguardJar.map { it.inJars }) { into("inJars") }
                from(proguardJar.map { it.libraryJars }) { into("libraryJars") }
                from(proguardJar.map { it.outJars }) { into("outJars") }
                into(layout.buildDirectory.dir("copyProguardJars"))
            }
            """.trimIndent()
        )
        projectDir.resolve("src/main/java/test/Main.java").apply { parentFile.mkdirs() }.writeText(
            """
            package test;
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello World!");
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("copyProguardJars", "--configuration-cache")
            .build()

        assertTrue(result.output.contains("Configuration cache entry stored"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":proguardJar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":copyProguardJars")?.outcome)

        val result2 = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("copyProguardJars", "--configuration-cache")
            .build()

        assertTrue(result2.output.contains("Configuration cache entry reused"))
        assertEquals(TaskOutcome.UP_TO_DATE, result2.task(":jar")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, result2.task(":proguardJar")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, result2.task(":copyProguardJars")?.outcome)
    }
}
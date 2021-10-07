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
                libraryJars(project.fileTree("${System.getProperty("java.home")}/jmods"), "!**.jar;!module-info.class")
                outJars(layout.buildDirectory.file("libs/test-proguarded.jar"))
                rules.add("-keep class test.Main { public static void main(java.lang.String[]); }")
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
            .withArguments("proguardJar", "--configuration-cache")
            .build()

        assertTrue(result.output.contains("Configuration cache entry stored"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":proguardJar")?.outcome)

        val result2 = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("proguardJar", "--configuration-cache")
            .build()

        assertTrue(result2.output.contains("Configuration cache entry reused"))
        assertEquals(TaskOutcome.UP_TO_DATE, result2.task(":jar")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, result2.task(":proguardJar")?.outcome)
    }
}
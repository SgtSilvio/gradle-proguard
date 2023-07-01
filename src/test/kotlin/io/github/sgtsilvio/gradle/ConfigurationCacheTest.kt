package io.github.sgtsilvio.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * @author Silvio Giebl
 */
internal class ConfigurationCacheTest {

    @Test
    fun configurationCacheReused(@TempDir projectDir: File) {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test"
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                java
                id("io.github.sgtsilvio.gradle.proguard")
            }
            repositories {
                mavenCentral()
            }
            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(11))
                }
            }
            val proguardJar by tasks.registering(proguard.taskClass) {
                addInput { classpath.from(tasks.jar) }
                addOutput { archiveFile.set(base.libsDirectory.file("test-proguarded.jar")) }
                jdkModules.add("java.base")
                mappingFile.set(layout.buildDirectory.file("test-mapping.txt"))
                rules.add("-keep class test.Main { public static void main(java.lang.String[]); }")
            }
            // copy inJars, libraryJars, outJars to check if they are compatible with the configuration cache
            val copyProguardJars by tasks.registering(Copy::class) {
                from(proguardJar.map { it.inputClasspath }) { into("inJars") }
                from(proguardJar.map { it.outputClasspath }) { into("outJars") }
                from(proguardJar.map { it.libraryClasspath }) { into("libraryJars") }
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
        assertFalse(result.output.contains("ProGuard, version")) // stdout should be silenced
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":proguardJar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":copyProguardJars")?.outcome)

        projectDir.resolve("build").deleteRecursively()

        val result2 = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("copyProguardJars", "--configuration-cache")
            .build()

        assertTrue(result2.output.contains("Configuration cache entry reused"))
        assertFalse(result2.output.contains("ProGuard, version")) // stdout should be silenced
        assertEquals(TaskOutcome.SUCCESS, result2.task(":jar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result2.task(":proguardJar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result2.task(":copyProguardJars")?.outcome)
    }
}
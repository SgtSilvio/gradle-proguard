package com.github.sgtsilvio.gradle.proguard

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * @author Silvio Giebl
 */
internal class FilePathEscapingTest {

    @Test
    fun spaceAndParenthesisAreEscaped(@TempDir tempDir: File) {
        val projectDir = tempDir.resolve("wa bern(test)")
        projectDir.mkdir()

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
            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(11))
                }
            }
            val proguardJar by tasks.registering(proguard.taskClass) {
                addInput { classpath.from(tasks.jar) }
                addOutput { archiveFile.set(base.libsDirectory.file("test-proguarded.jar")) }
                jdkModules.add("java.base")
                configurationFile.set(layout.buildDirectory.file("test-config.txt"))
                mappingFile.set(layout.buildDirectory.file("test-mapping.txt"))
                seedsFile.set(layout.buildDirectory.file("test-seeds.txt"))
                usageFile.set(layout.buildDirectory.file("test-usage.txt"))
                dumpFile.set(layout.buildDirectory.file("test-dump.txt"))
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
            .withArguments("proguardJar")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":proguardJar")?.outcome)
    }
}
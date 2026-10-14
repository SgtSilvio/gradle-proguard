package io.github.sgtsilvio.gradle.proguard

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.*
import java.util.zip.ZipFile

/**
 * @author David Sondermann
 */
internal class ReproducibleOutputTest {

    @Test
    fun outputArchivesAreReproducible(@TempDir projectDir: File) {
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
            tasks.register("proguardJar", proguard.taskClass) {
                addInput { classpath.from(tasks.jar) }
                addOutput {
                    archiveFile.set(base.libsDirectory.file("test-empty.jar"))
                    filter.set("**.properties")
                }
                addOutput { archiveFile.set(base.libsDirectory.file("test-proguarded.jar")) }
                jdkModules.add("java.base")
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

        assertEquals(TaskOutcome.SUCCESS, result.task(":proguardJar")?.outcome)
        assertFalse(projectDir.resolve("build/libs/test-empty.jar").exists())
        val archiveFile = projectDir.resolve("build/libs/test-proguarded.jar")
        val expectedTime = GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).timeInMillis
        ZipFile(archiveFile).use { zipFile ->
            val entries = zipFile.entries().toList()
            assertTrue(entries.any { it.name == "test/Main.class" })
            for (entry in entries) {
                assertEquals(expectedTime, entry.time, entry.name)
            }
        }
        val archiveBytes = archiveFile.readBytes()

        val result2 = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("proguardJar", "--rerun")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result2.task(":proguardJar")?.outcome)
        assertArrayEquals(archiveBytes, archiveFile.readBytes())
    }

    @Test
    fun outputArchivesOfFailedRunAreNotTouched(@TempDir projectDir: File) {
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
            tasks.register("proguardJar", proguard.taskClass) {
                addInput { classpath.from(tasks.jar) }
                addOutput { archiveFile.set(file("out/test-proguarded.jar")) }
                jdkModules.add("java.base")
                rules.add("-invalidoption")
                isIgnoreExitValue = true
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
        val archiveFile = projectDir.resolve("out/test-proguarded.jar").apply { parentFile.mkdirs() }
        archiveFile.writeText("incomplete")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("proguardJar")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":proguardJar")?.outcome)
        assertEquals("incomplete", archiveFile.readText())
    }

    @Test
    fun archiveFileWithoutArchiveExtensionIsNotTouched(@TempDir projectDir: File) {
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
            tasks.register("proguardJar", proguard.taskClass) {
                addInput { classpath.from(tasks.jar) }
                addOutput { archiveFile.set(base.libsDirectory.file("test-proguarded.out")) }
                jdkModules.add("java.base")
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

        assertEquals(TaskOutcome.SUCCESS, result.task(":proguardJar")?.outcome)
        assertTrue(projectDir.resolve("build/libs/test-proguarded.out/test.jar").isFile)
    }
}
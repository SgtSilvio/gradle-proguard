package com.github.sgtsilvio.gradle.proguard

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/**
 * Gradle plugin to ease using ProGuard.
 *
 * The differences of this plugin and the official ProGuard gradle plugin and the benefits are described [here][ProguardTask].
 *
 * Registers the `proguard` extension and the `proguardClasspath` configuration.
 * You can then register [ProguardTask]s.
 * ```kotlin
 * val proguardJar by tasks.registering(proguard.taskClass) {
 *     ...
 * }
 * ```
 *
 * @author Silvio Giebl
 */
class ProguardPlugin : Plugin<Project> {

    companion object {
        const val EXTENSION_NAME = "proguard"
        const val CONFIGURATION_NAME = "proguardClasspath"
    }

    override fun apply(project: Project) {
        project.extensions.create(EXTENSION_NAME, ProguardExtension::class)

        project.configurations.register(CONFIGURATION_NAME) {
            isCanBeResolved = true
            isCanBeConsumed = false
            isVisible = false

            defaultDependencies {
                add(project.dependencies.create("com.guardsquare:proguard-base:7.1.1"))
            }
        }

        project.plugins.withType<JavaBasePlugin> {
            project.tasks.withType<ProguardTask>().configureEach {
                val javaPluginExtension = project.extensions.getByType<JavaPluginExtension>()
                val javaToolchainService = project.extensions.getByType<JavaToolchainService>()
                javaLauncher.convention(javaToolchainService.launcherFor(javaPluginExtension.toolchain))
            }
        }
    }
}
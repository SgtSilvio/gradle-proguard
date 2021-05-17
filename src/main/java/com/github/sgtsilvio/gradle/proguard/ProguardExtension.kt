package com.github.sgtsilvio.gradle.proguard

/**
 * @author Silvio Giebl
 */
open class ProguardExtension {

    /**
     * Class to define ProGuard tasks.
     *
     * Use this property instead of the fully qualified class name or an import.
     * ```kotlin
     * val proguardJar by tasks.registering(proguard.taskClass) {
     *     ...
     * }
     * ```
     */
    val taskClass get() = ProguardTask::class
}
package com.github.sgtsilvio.gradle.proguard

import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.get
import org.gradle.process.CommandLineArgumentProvider

/**
 * Gradle task type to define ProGuard tasks.
 *
 * This implementation differs from the official ProGuard gradle plugin in the following points:
 * - ProGuard and the Gradle daemon are decoupled:
 *    - How: Runs ProGuard in a separate process (via the command line interface) instead of in the Gradle daemon
 *    - Why: ProGuard tends to use a lot of memory which can expire the daemon or even require increasing its heap.
 * - ProGuard's and this plugin's version are decoupled:
 *    - How: The Proguard base dependency is added to the `proguardClassPath` configuration and can be customized.
 *      Additionally, only the ProGuard configuration parameters that deal with files are modelled as Gradle input and
 *      output properties, because these are the only ones necessary for up-to-date checks and caching.
 *      All other (maybe ProGuard version specific) configurations can be passed as string arguments via [rules].
 *    - Why: Using a bug fix in either ProGuard or this plugin should not need an update of the other tool.
 * - Gradle compatibility:
 *    - How: Using proper input and output annotations with path sensitivity to benefit from the build cache regardless
 *      of where the project is located.
 *    - Why: Taking full advantages of Gradle and being future-proof.
 * - This plugin is completely Android agnostic
 *    - How: No dependency on any Android artifacts.
 *    - Why: ProGuard can be used for any JVM program.
 *
 * @author Silvio Giebl
 */
@CacheableTask
open class ProguardTask : JavaExec() {

    /**
     * Combined list of input, library and output jars ordered by insertion.
     * Passed as `-injars`, `-libraryjars` and `-outjars` arguments to ProGuard in the right order.
     */
    private val jarEntries = mutableListOf<JarEntry>()

    private val inJarsInternal = objectFactory.fileCollection()
    val inJars: FileCollection @Classpath get() = inJarsInternal
    private val inJarFiltersInternal = mutableListOf<String>()
    protected val inJarFilters: List<String> @Input get() = inJarFiltersInternal

    private val libraryJarsInternal = objectFactory.fileCollection()
    val libraryJars: FileCollection @Classpath get() = libraryJarsInternal
    private val libraryJarFiltersInternal = mutableListOf<String>()
    protected val libraryJarFilters: List<String> @Input get() = libraryJarFiltersInternal

    private val outJarsInternal = objectFactory.fileCollection()
    val outJars: FileCollection @OutputFiles get() = outJarsInternal
    private val outJarFiltersInternal = mutableListOf<String>()
    protected val outJarFilters: List<String> @Input get() = outJarFiltersInternal

    /**
     * Collection of rules files passed as `-include` arguments to ProGuard.
     *
     * The rules files must not contain file configuration parameters; these are declared here as inputs and outputs.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    val rulesFiles = objectFactory.fileCollection()

    /**
     * List of rules directly specified in the Gradle configuration passed 1-to-1 to ProGuard.
     *
     * The rules must not contain file configuration parameters; these are declared here as inputs and outputs.
     */
    @Input
    val rules = objectFactory.listProperty(String::class.java)

    /**
     * Passed as `-applymapping` argument to ProGuard.
     */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    val mappingInputFile = objectFactory.fileProperty()

    /**
     * Passed as `-obfuscationdictionary` argument to ProGuard.
     */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    val obfuscationDictionary = objectFactory.fileProperty()

    /**
     * Passed as `-classobfuscationdictionary` argument to ProGuard.
     */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    val classObfuscationDictionary = objectFactory.fileProperty()

    /**
     * Passed as `-packageobfuscationdictionary` argument to ProGuard.
     */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    val packageObfuscationDictionary = objectFactory.fileProperty()

    /**
     * Passed as `-printconfiguration` argument to ProGuard.
     */
    @Optional
    @OutputFile
    val configurationFile = objectFactory.fileProperty()

    /**
     * Passed as `-printmapping` argument to ProGuard.
     */
    @Optional
    @OutputFile
    val mappingFile = objectFactory.fileProperty()

    /**
     * Passed as `-printseeds` argument to ProGuard.
     */
    @Optional
    @OutputFile
    val seedsFile = objectFactory.fileProperty()

    /**
     * Passed as `-printusage` argument to ProGuard.
     */
    @Optional
    @OutputFile
    val usageFile = objectFactory.fileProperty()

    /**
     * Passed as `-dump` argument to ProGuard.
     */
    @Optional
    @OutputFile
    val dumpFile = objectFactory.fileProperty()

    init {
        classpath = project.configurations[ProguardPlugin.CONFIGURATION_NAME]
        mainClass.set("proguard.ProGuard")
        argumentProviders += ArgumentProvider()

        logging.captureStandardOutput(LogLevel.INFO)
        logging.captureStandardError(LogLevel.ERROR)
    }

    /**
     * Adds input jars with an optional filter.
     *
     * The order in which the [inJars], [libraryJars] and [outJars] methods are called defines the order of the
     * `-injars`, `-libraryjars` and `-outjars` arguments.
     */
    fun inJars(files: Any, filter: String = "") {
        jarEntries.add(JarEntry(objectFactory.fileCollection().from(files), "-injars", filter))
        inJarsInternal.from(files)
        inJarFiltersInternal.add(filter)
    }

    /**
     * Adds library jars with an optional filter.
     *
     * The order in which the [inJars], [libraryJars] and [outJars] methods are called defines the order of the
     * `-injars`, `-libraryjars` and `-outjars` arguments.
     */
    fun libraryJars(files: Any, filter: String = "") {
        jarEntries.add(JarEntry(objectFactory.fileCollection().from(files), "-libraryjars", filter))
        libraryJarsInternal.from(files)
        libraryJarFiltersInternal.add(filter)
    }

    /**
     * Adds output jars with an optional filter.
     *
     * The order in which the [inJars], [libraryJars] and [outJars] methods are called defines the order of the
     * `-injars`, `-libraryjars` and `-outjars` arguments.
     */
    fun outJars(files: Any, filter: String = "") {
        jarEntries.add(JarEntry(objectFactory.fileCollection().from(files), "-outjars", filter))
        outJarsInternal.from(files)
        outJarFiltersInternal.add(filter)
    }

    private data class JarEntry(val files: FileCollection, val type: String, val filter: String)

    private inner class ArgumentProvider : CommandLineArgumentProvider {
        @Internal
        override fun asArguments(): Iterable<String> {
            val arguments = mutableListOf<String>()
            for (jarEntry in jarEntries) {
                for (file in jarEntry.files) {
                    val filter = if (jarEntry.filter.isEmpty()) "" else "(${jarEntry.filter})"
                    arguments.add("${jarEntry.type} \"${file.absolutePath}\"$filter")
                }
            }
            if (mappingInputFile.isPresent) {
                arguments.add("-applymapping \"${mappingInputFile.get().asFile.absolutePath}\"")
            }
            if (obfuscationDictionary.isPresent) {
                arguments.add("-obfuscationdictionary \"${obfuscationDictionary.get().asFile.absolutePath}\"")
            }
            if (classObfuscationDictionary.isPresent) {
                arguments.add("-classobfuscationdictionary \"${classObfuscationDictionary.get().asFile.absolutePath}\"")
            }
            if (packageObfuscationDictionary.isPresent) {
                arguments.add("-packageobfuscationdictionary \"${packageObfuscationDictionary.get().asFile.absolutePath}\"")
            }
            if (configurationFile.isPresent) {
                arguments.add("-printconfiguration \"${configurationFile.get().asFile.absolutePath}\"")
            }
            if (mappingFile.isPresent) {
                arguments.add("-printmapping \"${mappingFile.get().asFile.absolutePath}\"")
            }
            if (seedsFile.isPresent) {
                arguments.add("-printseeds \"${seedsFile.get().asFile.absolutePath}\"")
            }
            if (usageFile.isPresent) {
                arguments.add("-printusage \"${usageFile.get().asFile.absolutePath}\"")
            }
            if (dumpFile.isPresent) {
                arguments.add("-dump \"${dumpFile.get().asFile.absolutePath}\"")
            }
            for (rulesFile in rulesFiles) {
                arguments.add("-include \"${rulesFile.absolutePath}\"")
            }
            for (rule in rules.get()) {
                arguments.add(rule)
            }
            arguments.add("-forceprocessing")
            return arguments
        }
    }
}
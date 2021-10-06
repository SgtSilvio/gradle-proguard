package com.github.sgtsilvio.gradle.proguard

import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.listProperty
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

/**
 * Gradle task type to define ProGuard tasks.
 *
 * This implementation differs from the official ProGuard gradle plugin in the following points:
 *  - ProGuard and the Gradle daemon are decoupled:
 *    - How: Runs ProGuard in a separate process (via the command line interface) instead of in the Gradle daemon
 *    - Why: ProGuard tends to use a lot of memory which can expire the daemon or even require increasing its heap.
 *  - ProGuard's and this plugin's version are decoupled:
 *    - How: The Proguard base dependency is added to the `proguardClasspath` configuration and can be customized.
 *      Additionally, only the ProGuard configuration parameters that deal with files are modelled as Gradle input and
 *      output properties, because these are the only ones necessary for up-to-date checks and caching.
 *      All other (maybe ProGuard version specific) configurations can be passed as string arguments via [rules].
 *    - Why: Using a bug fix in either ProGuard or this plugin should not need an update of the other tool.
 *  - Gradle compatibility:
 *    - How: Using proper input and output annotations with path sensitivity to benefit from the build cache regardless
 *      of where the project is located.
 *    - Why: Taking full advantages of Gradle and being future-proof.
 *  - This plugin is completely Android agnostic
 *    - How: No dependency on any Android artifacts.
 *    - Why: ProGuard can be used for any JVM program.
 *
 * @author Silvio Giebl
 */
@CacheableTask
abstract class ProguardTask : JavaExec() {

    /**
     * Passed as `-injars` arguments to ProGuard.
     */
    @get:Nested
    protected val inJarsEntries = mutableListOf<InJarsEntry>()

    protected class InJarsEntry(
        @get:Classpath
        val files: FileCollection,
        @get:Input
        val filter: String
    )

    @get:Internal
    val inJars: FileCollection = objectFactory.fileCollection().from({ inJarsEntries.map { it.files } })

    /**
     * Passed as `-libraryjars` arguments to ProGuard.
     */
    @get:Nested
    protected val libraryJarsEntries = mutableListOf<LibraryJarsEntry>()

    protected class LibraryJarsEntry(
        @get:Classpath
        val files: FileCollection,
        @get:Input
        val filter: String
    )

    @get:Internal
    val libraryJars: FileCollection = objectFactory.fileCollection().from({ libraryJarsEntries.map { it.files } })

    /**
     * Passed as `-outjars` arguments to ProGuard.
     */
    @get:Nested
    protected val outJarEntries = mutableListOf<OutJarEntry>()

    protected class OutJarEntry(
        @get:OutputFile
        val file: Provider<File>,
        @get:Input
        val filter: String,
        @get:Input
        val inJarsEntriesCount: Int
    )

    @Suppress("LeakingThis")
    @get:Internal
    val outJars: FileCollection = objectFactory.fileCollection().from({ outJarEntries.map { it.file } }).builtBy(this)

    /**
     * Collection of rules files passed as `-include` arguments to ProGuard.
     *
     * The rules files must not contain file configuration parameters; these are declared here as inputs and outputs.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val rulesFiles = objectFactory.fileCollection()

    /**
     * List of rules directly specified in the Gradle configuration passed 1-to-1 to ProGuard.
     *
     * The rules must not contain file configuration parameters; these are declared here as inputs and outputs.
     */
    @get:Input
    val rules = objectFactory.listProperty(String::class)

    /**
     * Passed as `-applymapping` argument to ProGuard.
     */
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val mappingInputFile = objectFactory.fileProperty()

    /**
     * Passed as `-obfuscationdictionary` argument to ProGuard.
     */
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val obfuscationDictionary = objectFactory.fileProperty()

    /**
     * Passed as `-classobfuscationdictionary` argument to ProGuard.
     */
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val classObfuscationDictionary = objectFactory.fileProperty()

    /**
     * Passed as `-packageobfuscationdictionary` argument to ProGuard.
     */
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val packageObfuscationDictionary = objectFactory.fileProperty()

    /**
     * Passed as `-printconfiguration` argument to ProGuard.
     */
    @get:Optional
    @get:OutputFile
    val configurationFile = objectFactory.fileProperty()

    /**
     * Passed as `-printmapping` argument to ProGuard.
     */
    @get:Optional
    @get:OutputFile
    val mappingFile = objectFactory.fileProperty()

    /**
     * Passed as `-printseeds` argument to ProGuard.
     */
    @get:Optional
    @get:OutputFile
    val seedsFile = objectFactory.fileProperty()

    /**
     * Passed as `-printusage` argument to ProGuard.
     */
    @get:Optional
    @get:OutputFile
    val usageFile = objectFactory.fileProperty()

    /**
     * Passed as `-dump` argument to ProGuard.
     */
    @get:Optional
    @get:OutputFile
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
     * The order in which the [inJars] and [outJars] methods are called defines the order of the `-injars` and
     * `-outjars` arguments.
     */
    fun inJars(files: Any, filter: String = "") {
        inJarsEntries.add(InJarsEntry(objectFactory.fileCollection().from(files), filter))
    }

    /**
     * Adds library jars with an optional filter.
     */
    fun libraryJars(files: Any, filter: String = "") {
        libraryJarsEntries.add(LibraryJarsEntry(objectFactory.fileCollection().from(files), filter))
    }

    /**
     * Adds an output jar with an optional filter.
     *
     * The order in which the [inJars] and [outJars] methods are called defines the order of the `-injars` and
     * `-outjars` arguments.
     */
    fun outJars(file: Any, filter: String = "") {
        val fileProvider = objectFactory.fileCollection().from(file).elements.map { it.first().asFile }
        outJarEntries.add(OutJarEntry(fileProvider, filter, inJarsEntries.size))
    }

    private inner class ArgumentProvider : CommandLineArgumentProvider {
        override fun asArguments(): Iterable<String> {
            val arguments = mutableListOf<String>()

            fun addJarArgument(type: String, file: File, filter: String) {
                arguments.add("-${type}jars \"${file.absolutePath}\"" + if (filter.isEmpty()) "" else "($filter)")
            }

            var inJarsEntryIndex = 0
            for (outJarEntry in outJarEntries) {
                while (inJarsEntryIndex < outJarEntry.inJarsEntriesCount) {
                    val inJarsEntry = inJarsEntries[inJarsEntryIndex]
                    for (file in inJarsEntry.files) {
                        addJarArgument("in", file, inJarsEntry.filter)
                    }
                    inJarsEntryIndex++
                }
                addJarArgument("out", outJarEntry.file.get(), outJarEntry.filter)
            }
            for (libraryJarsEntry in libraryJarsEntries) {
                for (file in libraryJarsEntry.files) {
                    addJarArgument("library", file, libraryJarsEntry.filter)
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
package com.github.sgtsilvio.gradle.proguard

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
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
     * Passed as groups of multiple `-injars` and multiple `-outjars` arguments each to ProGuard.
     */
    @get:Nested
    val inputOutputGroups = mutableListOf<InputOutputGroup>()

    inner class InputOutputGroup {
        /**
         * Passed as multiple `-injars` arguments to ProGuard.
         */
        @get:Nested
        val inputs = mutableListOf<InputEntry>()

        /**
         * Passed as multiple `-outjars` arguments to ProGuard.
         */
        @get:Nested
        val outputs = mutableListOf<OutputEntry>()

        /**
         * Adds a new input entry to this group of inputs and outputs and configures it.
         */
        fun addInput(action: Action<InputEntry>) {
            val input = InputEntry()
            inputs.add(input)
            action.execute(input)
        }

        /**
         * Adds a new output entry to this group of inputs and outputs and configures it.
         */
        fun addOutput(action: Action<OutputEntry>) {
            val output = OutputEntry()
            output.archiveFile.builtBy(this@ProguardTask)
            output.directory.builtBy(this@ProguardTask)
            outputs.add(output)
            action.execute(output)
        }
    }

    inner class InputEntry {
        /**
         * Can contain archive files and/or directories.
         */
        @get:Classpath
        val classpath = objectFactory.fileCollection()

        /**
         * Glob style filters for the files in [InputEntry.classpath].
         */
        @get:Input
        val filter = objectFactory.property<String>().convention("")
    }

    inner class OutputEntry {
        /**
         * Mutually exclusive with [directory], exactly one must be set.
         */
        @get:Optional
        @get:OutputFile
        val archiveFile = objectFactory.fileProperty()

        /**
         * Mutually exclusive with [archiveFile], exactly one must be set.
         */
        @get:Optional
        @get:OutputDirectory
        val directory = objectFactory.directoryProperty()

        @get:Internal
        internal val archiveFileOrDirectory get() = archiveFile.map<FileSystemLocation> { it }.orElse(directory)

        /**
         * Glob style filters for the files in [archiveFile] or [directory].
         */
        @get:Input
        val filter = objectFactory.property<String>().convention("")
    }

    /**
     * Passed as `-libraryjars` arguments to ProGuard.
     */
    @get:Nested
    val libraries = mutableListOf<LibraryEntry>()

    inner class LibraryEntry {
        /**
         * Can contain archive files and/or directories.
         */
        @get:Classpath
        val classpath = objectFactory.fileCollection()

        /**
         * Glob style filters for the files in [LibraryEntry.classpath].
         */
        @get:Input
        val filter = objectFactory.property<String>().convention("")
    }

    /**
     * Flattened collection of all input archive files and/or directories.
     */
    @get:Internal
    val inJars: FileCollection =
        objectFactory.fileCollection().from({ inputOutputGroups.flatMap { it.inputs }.map { it.classpath } })

    /**
     * Flattened collection of all output archive files and/or directories.
     */
    @get:Internal
    val outJars: FileCollection = objectFactory.fileCollection()
        .from({ inputOutputGroups.flatMap { it.outputs }.map { it.archiveFileOrDirectory } })

    /**
     * Flattened collection of all library archive files and/or directories.
     */
    @get:Internal
    val libraryJars: FileCollection = objectFactory.fileCollection().from({ libraries.map { it.classpath } })

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
        addInputOutputGroup {}
    }

    /**
     * Adds a new input entry to the first group of inputs and outputs and configures it.
     */
    fun addInput(action: Action<InputEntry>) = inputOutputGroups[0].addInput(action)

    /**
     * Adds a new output entry to the first group of inputs and outputs and configures it.
     */
    fun addOutput(action: Action<OutputEntry>) = inputOutputGroups[0].addOutput(action)

    /**
     * Adds a new group of inputs and outputs and configures it.
     */
    fun addInputOutputGroup(action: Action<InputOutputGroup>) {
        val inputOutputGroup = InputOutputGroup()
        inputOutputGroups.add(inputOutputGroup)
        action.execute(inputOutputGroup)
    }

    /**
     * Adds a new library entry and configures it.
     */
    fun addLibrary(action: Action<LibraryEntry>) {
        val library = LibraryEntry()
        libraries.add(library)
        action.execute(library)
    }

    /**
     * Adds input jars with an optional filter.
     *
     * The order in which the [inJars] and [outJars] methods are called defines the order of the `-injars` and
     * `-outjars` arguments.
     */
    @Deprecated(
        "Inputs and outputs have been refactored to be modifiable whereas this method was add-only.",
        ReplaceWith("addInput { classpath.from(files)\nthis.filter.set(filter) }")
    )
    fun inJars(files: Any, filter: String = "") {
        if (inputOutputGroups.last().outputs.isNotEmpty()) {
            addInputOutputGroup {}
        }
        inputOutputGroups.last().addInput {
            classpath.from(files)
            this.filter.set(filter)
        }
    }

    /**
     * Adds an output jar with an optional filter.
     *
     * The order in which the [inJars] and [outJars] methods are called defines the order of the `-injars` and
     * `-outjars` arguments.
     */
    @Deprecated(
        "Inputs and outputs have been refactored to be modifiable whereas this method was add-only.",
        ReplaceWith("addOutput { archiveFile.set(file)\nthis.filter.set(filter) }")
    )
    fun outJars(file: Any, filter: String = "") {
        inputOutputGroups.last().addOutput {
            archiveFile.set(project.layout.file(project.files(file).elements.map { it.first().asFile }))
            this.filter.set(filter)
        }
    }

    /**
     * Adds library jars with an optional filter.
     */
    @Deprecated(
        "Inputs and outputs have been refactored to be modifiable whereas this method was add-only.",
        ReplaceWith("addLibrary { classpath.from(files)\nthis.filter.set(filter) }")
    )
    fun libraryJars(files: Any, filter: String = "") {
        addLibrary {
            classpath.from(files)
            this.filter.set(filter)
        }
    }

    override fun exec() {
        standardOutput = LineOutputStream { logger.info(it) }
        errorOutput = LineOutputStream { logger.error(it) }
        super.exec()
    }

    private inner class ArgumentProvider : CommandLineArgumentProvider {
        override fun asArguments(): Iterable<String> {
            val arguments = mutableListOf<String>()

            fun addJarArgument(type: String, file: File, filter: String) {
                arguments.add("-${type}jars")
                arguments.add("'${file.absolutePath}'" + if (filter.isEmpty()) "" else "($filter)")
            }

            fun addFileArgument(option: String, file: File) {
                arguments.add(option)
                arguments.add("'${file.absolutePath}'")
            }

            fun addFileArgument(option: String, fileProvider: Provider<RegularFile>) {
                if (fileProvider.isPresent) {
                    addFileArgument(option, fileProvider.get().asFile)
                }
            }

            for ((groupIndex, inputOutputGroup) in inputOutputGroups.withIndex()) {
                var inJarsAdded = false
                for (input in inputOutputGroup.inputs) {
                    val filter = input.filter.get()
                    for (file in input.classpath.files) {
                        addJarArgument("in", file, filter)
                        inJarsAdded = true
                    }
                }
                if (!inJarsAdded) {
                    throw GradleException("inputOutputGroups.\$$groupIndex.inputs classpath did not contain any files.")
                }
                if (inputOutputGroup.outputs.isEmpty() && (inputOutputGroups.size > 1)) {
                    throw GradleException("inputOutputGroups.\$$groupIndex.outputs are empty although multiple inputOutputGroups are configured.")
                }
                for ((outputIndex, output) in inputOutputGroup.outputs.withIndex()) {
                    val archiveFile = output.archiveFile.orNull
                    val directory = output.directory.orNull
                    val filter = output.filter.get()
                    if ((archiveFile != null) && (directory != null)) {
                        throw GradleException("In inputOutputGroups.\$$groupIndex.outputs.\$$outputIndex both archiveFile and directory have a configured value.")
                    } else if (archiveFile != null) {
                        addJarArgument("out", archiveFile.asFile, filter)
                    } else if (directory != null) {
                        addJarArgument("out", directory.asFile, filter)
                    } else {
                        throw GradleException("In inputOutputGroups.\$$groupIndex.outputs.\$$outputIndex neither archiveFile nor directory have a configured value.")
                    }
                }
            }
            for (library in libraries) {
                val filter = library.filter.get()
                for (file in library.classpath.files) {
                    addJarArgument("library", file, filter)
                }
            }
            addFileArgument("-applymapping", mappingInputFile)
            addFileArgument("-obfuscationdictionary", obfuscationDictionary)
            addFileArgument("-classobfuscationdictionary", classObfuscationDictionary)
            addFileArgument("-packageobfuscationdictionary", packageObfuscationDictionary)
            addFileArgument("-printconfiguration", configurationFile)
            addFileArgument("-printmapping", mappingFile)
            addFileArgument("-printseeds", seedsFile)
            addFileArgument("-printusage", usageFile)
            addFileArgument("-dump", dumpFile)
            for (rulesFile in rulesFiles) {
                addFileArgument("-include", rulesFile)
            }
            for (rule in rules.get()) {
                arguments.add(rule)
            }
            arguments.add("-forceprocessing")
            return arguments
        }
    }
}
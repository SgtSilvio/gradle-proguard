# Gradle ProGuard Plugin

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.github.sgtsilvio.gradle.proguard?color=brightgreen&style=for-the-badge)](https://plugins.gradle.org/plugin/com.github.sgtsilvio.gradle.proguard)
[![GitHub](https://img.shields.io/github/license/sgtsilvio/gradle-proguard?color=brightgreen&style=for-the-badge)](LICENSE)
[![GitHub Workflow Status (with branch)](https://img.shields.io/github/actions/workflow/status/sgtsilvio/gradle-proguard/check.yml?branch=master&style=for-the-badge)](https://github.com/SgtSilvio/gradle-proguard/actions/workflows/check.yml?query=branch%3Amaster)

Gradle plugin to ease using ProGuard.

This implementation differs from the official ProGuard gradle plugin in the following points:

- ProGuard and the Gradle daemon are decoupled:
  - How: Runs ProGuard in a separate process (via the command line interface) instead of in the Gradle daemon
  - Why: ProGuard tends to use a lot of memory which can expire the daemon or even require increasing its heap.
- ProGuard's and this plugin's version are decoupled:
  - How: The Proguard base dependency is added to the `proguardClasspath` configuration and can be customized.
    Additionally, only the ProGuard configuration parameters that deal with files are modelled as Gradle input and
    output properties, because these are the only ones necessary for up-to-date checks and caching.
    All other (maybe ProGuard version specific) configurations can be passed as string arguments via `rules`.
  - Why: Using a bug fix in either ProGuard or this plugin should not need an update of the other tool.
- Gradle compatibility:
  - How: Using proper input and output annotations with path sensitivity to benefit from the build cache regardless
    of where the project is located.
  - Why: Taking full advantages of Gradle and being future-proof.
- This plugin is completely Android agnostic
  - How: No dependency on any Android artifacts.
  - Why: ProGuard can be used for any JVM program.

## How to Use

```kotlin
plugins {
    java
    id("com.github.sgtsilvio.gradle.proguard") version "0.5.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

//...

val proguardJar by tasks.registering(proguard.taskClass) {
    addInput {
        classpath.from(tasks.shadowJar)
    }
    addOutput {
        archiveFile.set(base.libsDirectory.file("${project.name}-${project.version}-proguarded.jar"))
    }
    jdkModules.add("java.base")
    mappingFile.set(base.libsDirectory.file("${project.name}-${project.version}-mapping.txt"))

    rules.addAll(
        "-dontoptimize",
        "-dontwarn !org.example.**",
        "-flattenpackagehierarchy",
        "-keep class org.example.Main { public static void main(java.lang.String[]); }",
        "-keepattributes Signature,InnerClasses,*Annotation*"
    )
}
```

## Requirements

- Gradle 6.7 or higher

## Configuration

### ProGuard Version

You can add a custom proguard dependency to the `proguardClasspath` configuration.
The default dependency is overwritten if you specify any dependency for this configuration.

```kotlin
dependencies {
    proguardClasspath("com.guardsquare:proguard-base:7.2.2")
}
```

### ProGuard Process

The ProGuard task class extends Gradle's `JavaExec` task class, so all of its properties
(Java version, heap size, jvm arguments, system properties, environment variables, classpath, main class, debugging)
can be used to configure the ProGuard process.

```kotlin
val proguardJar by tasks.registering(proguard.taskClass) {
    //...
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
    maxHeapSize = "2G"
}
```
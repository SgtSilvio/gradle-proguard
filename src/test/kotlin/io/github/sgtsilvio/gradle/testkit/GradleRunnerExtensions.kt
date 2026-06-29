package io.github.sgtsilvio.gradle.testkit

import org.gradle.testkit.runner.GradleRunner

fun GradleRunner.addArguments(vararg arguments: String): GradleRunner = withArguments(this.arguments + arguments)

fun GradleRunner.withJavaHome(javaHome: String): GradleRunner = addArguments("-Dorg.gradle.java.home=${javaHome}")
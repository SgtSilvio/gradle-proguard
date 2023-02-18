package com.github.sgtsilvio.gradle.proguard

import org.gradle.api.Task
import org.gradle.api.internal.provider.PropertyInternal
import org.gradle.api.provider.Property
import org.gradle.internal.state.ModelObject

internal fun <T : Property<*>> T.builtBy(task: Task): T {
    try {
        (this as PropertyInternal<*>).attachProducer(task as ModelObject)
    } catch (e: Throwable) {
        task.logger.error("Can not attach producer task ($task) to property ($this) as a workaround for https://github.com/gradle/gradle/issues/6619. ${e.message}")
    }
    return this
}
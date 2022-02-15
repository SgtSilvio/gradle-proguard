package com.github.sgtsilvio.gradle.proguard

import java.io.ByteArrayOutputStream
import java.io.OutputStream

class LogOutputStream(val log: (String) -> Unit) : OutputStream() {

    private val buffer = ByteArrayOutputStream()

    override fun write(b: Int) = buffer.write(b)

    override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)

    override fun flush() {
        val line = buffer.toString("UTF-8")
        if (line.isNotEmpty()) {
            log(line.removeSuffix("\n"))
        }
        buffer.reset()
    }

    override fun close() {
        flush()
    }
}
package io.github.sgtsilvio.gradle.proguard

import java.io.OutputStream

private const val CR = '\r'.code.toByte()
private const val LF = '\n'.code.toByte()

/**
 * Interprets an output stream as UTF-8 strings.
 * Supports LF, CR LF and CR line endings.
 */
internal class LineOutputStream(private val consumer: (String) -> Unit) : OutputStream() {

    private val buffer = ByteStringBuilder()
    private var lastCR = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()))

    override fun write(b: ByteArray, off: Int, len: Int) {
        val end = off + len

        require(len >= 0)
        if ((off < 0) || (off > b.size) || (end < 0) || (end > b.size)) {
            throw IndexOutOfBoundsException()
        }

        var start = off
        var i = off
        while (i < end) {
            when (b[i]) {
                LF -> {
                    if (!lastCR) {
                        consumer(buffer.toString(b, start, i))
                    }
                    start = i + 1
                    lastCR = false
                }
                CR -> {
                    consumer(buffer.toString(b, start, i))
                    start = i + 1
                    lastCR = true
                }
                else -> {
                    lastCR = false
                }
            }
            i++
        }
        buffer.append(b, start, end)
    }

    override fun close() {
        if (buffer.size > 0) {
            consumer(buffer.toString())
        }
    }
}

private class ByteStringBuilder {

    private var buffer = ByteArray(0)
    var size = 0
        private set

    fun append(b: ByteArray, fromIndex: Int, toIndex: Int) {
        val additionalSize = toIndex - fromIndex
        if (additionalSize == 0) {
            return
        }
        val currentSize = size
        val newSize = currentSize + additionalSize
        if (newSize > buffer.size) {
            buffer = buffer.copyOf(newSize)
        }
        System.arraycopy(b, fromIndex, buffer, currentSize, additionalSize)
        size = newSize
    }

    fun toString(b: ByteArray, fromIndex: Int, toIndex: Int): String {
        return if (size == 0) {
            String(b, fromIndex, toIndex - fromIndex, Charsets.UTF_8)
        } else {
            append(b, fromIndex, toIndex)
            toString()
        }
    }

    override fun toString() = String(buffer, 0, size, Charsets.UTF_8).also { size = 0 }
}
package com.github.sgtsilvio.gradle.proguard

import java.io.OutputStream

private const val CR = '\r'.toByte()
private const val LF = '\n'.toByte()

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
                        consumer.invoke(buffer.toString(b, start, i))
                    }
                    start = i + 1
                    lastCR = false
                }
                CR -> {
                    consumer.invoke(buffer.toString(b, start, i))
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
            consumer.invoke(buffer.toString())
        }
    }
}

private class ByteStringBuilder {

    private var buffer = ByteArray(0)
    private var _size = 0
    val size get() = _size

    fun append(b: ByteArray, fromIndex: Int, toIndex: Int) {
        val additionalSize = toIndex - fromIndex
        if (additionalSize == 0) {
            return
        }
        val currentSize = _size
        val newSize = currentSize + additionalSize
        if (newSize > buffer.size) {
            buffer = buffer.copyOf(newSize)
        }
        System.arraycopy(b, fromIndex, buffer, currentSize, additionalSize)
        _size = newSize
    }

    fun toString(b: ByteArray, fromIndex: Int, toIndex: Int): String {
        return if (_size == 0) {
            String(b, fromIndex, toIndex - fromIndex, Charsets.UTF_8)
        } else {
            append(b, fromIndex, toIndex)
            toString()
        }
    }

    override fun toString() = String(buffer, 0, _size, Charsets.UTF_8).also { _size = 0 }
}
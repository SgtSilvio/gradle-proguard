package io.github.sgtsilvio.gradle.proguard

import java.io.OutputStream

private const val CR = '\r'.code.toByte()
private const val LF = '\n'.code.toByte()

/**
 * Interprets an output stream as UTF-8 lines.
 * Supports LF, CR LF and CR line endings.
 */
internal class LineOutputStream(private val consumer: (String) -> Unit) : OutputStream() {

    private var buffer = ByteArray(0)
    private var bufferSize = 0
    private var lastCR = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()))

    override fun write(b: ByteArray, off: Int, len: Int) {
        val end = off + len
        if ((len < 0) || (off < 0) || (off > b.size) || (end < 0) || (end > b.size)) {
            throw IndexOutOfBoundsException()
        }
        var start = off
        var i = off
        while (i < end) {
            when (b[i]) {
                LF -> {
                    if (!lastCR) {
                        consumer(createLine(b, start, i))
                    }
                    start = i + 1
                    lastCR = false
                }
                CR -> {
                    consumer(createLine(b, start, i))
                    start = i + 1
                    lastCR = true
                }
                else -> {
                    lastCR = false
                }
            }
            i++
        }
        appendToBuffer(b, start, end)
    }

    override fun close() {
        if (bufferSize > 0) {
            consumer(createLine())
        }
    }

    private fun appendToBuffer(b: ByteArray, fromIndex: Int, toIndex: Int) {
        val additionalSize = toIndex - fromIndex
        if (additionalSize == 0) {
            return
        }
        val currentSize = bufferSize
        val newSize = currentSize + additionalSize
        if (newSize > buffer.size) {
            buffer = buffer.copyOf(newSize)
        }
        System.arraycopy(b, fromIndex, buffer, currentSize, additionalSize)
        bufferSize = newSize
    }

    private fun createLine(b: ByteArray, fromIndex: Int, toIndex: Int): String {
        return if (bufferSize == 0) {
            String(b, fromIndex, toIndex - fromIndex)
        } else {
            appendToBuffer(b, fromIndex, toIndex)
            createLine()
        }
    }

    private fun createLine() = String(buffer, 0, bufferSize).also { bufferSize = 0 }
}
package com.github.sgtsilvio.gradle.proguard

import java.io.OutputStream

private const val CR = '\r'.toByte()
private const val LF = '\n'.toByte()
private val EMPTY = byteArrayOf()

/**
 * Interprets an output stream as UTF-8 strings.
 * Supports LF, CR LF and CR line endings.
 */
internal class LineOutputStream(private val consumer: (String) -> Unit) : OutputStream() {

    private var buffer: ByteArray = EMPTY
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
                        consume(buffer.append(b, start, i))
                    }
                    start = i + 1
                    lastCR = false
                }
                CR -> {
                    consume(buffer.append(b, start, i))
                    start = i + 1
                    lastCR = true
                }
                else -> {
                    lastCR = false
                }
            }
            i++
        }
        buffer = buffer.append(b, start, end)
    }

    override fun close() {
        if (buffer.isNotEmpty()) {
            consume(buffer)
        }
    }

    private fun consume(b: ByteArray) {
        consumer.invoke(String(b, Charsets.UTF_8))
        buffer = EMPTY
    }
}

private fun ByteArray.append(other: ByteArray, fromIndex: Int, toIndex: Int) : ByteArray {
    val otherSize = toIndex - fromIndex
    if (otherSize == 0) {
        return this
    }
    val result = copyOf(size + otherSize)
    System.arraycopy(other, fromIndex, result, size, otherSize)
    return result
}
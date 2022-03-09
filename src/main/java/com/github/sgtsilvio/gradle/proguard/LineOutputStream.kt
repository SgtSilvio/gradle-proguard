package com.github.sgtsilvio.gradle.proguard

import java.io.OutputStream
import java.nio.charset.StandardCharsets

private const val CR = '\r'.toByte()
private const val LF = '\n'.toByte()

/**
 * Interprets an output stream as UTF-8 strings.
 * Supports LF, CR LF and CR line endings.
 */
internal class LineOutputStream(private val consumer: (String) -> Unit) : OutputStream() {

    private val stringBuilder = StringBuilder()
    private var lastCR = false

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()))
    }

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
                        stringBuilder.append(asString(b, start, i))
                        consume()
                    }
                    start = i + 1
                    lastCR = false
                }
                CR -> {
                    stringBuilder.append(asString(b, start, i))
                    consume()
                    start = i + 1
                    lastCR = true
                }
                else -> {
                    lastCR = false
                }
            }
            i++
        }
        stringBuilder.append(asString(b, start, end))
    }

    override fun close() {
        if (stringBuilder.isNotEmpty()) {
            consume()
        }
    }

    private fun consume() {
        consumer.invoke(stringBuilder.toString())
        stringBuilder.delete(0, Int.MAX_VALUE)
    }
}

private fun asString(b: ByteArray, start: Int, end: Int): String {
    require(start <= end)
    return if (start == end) "" else String(b, start, end - start, StandardCharsets.UTF_8)
}
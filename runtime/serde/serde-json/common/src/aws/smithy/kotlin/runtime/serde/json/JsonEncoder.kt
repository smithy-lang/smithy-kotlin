/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.util.*

// character code points
private const val CP_QUOTATION = 0x22
private const val CP_BACKSLASH = 0x5C
private const val CP_NEWLINE = 0x0A
private const val CP_CARRIAGE_RETURN = 0x0D
private const val CP_TAB = 0x09
private const val CP_BACKSPACE = 0x08
private const val CP_FORMFEED = 0x0C

internal class JsonEncoder(private val pretty: Boolean = false) : JsonStreamWriter {
    private val buffer = StringBuilder()

    override val bytes: ByteArray
        get() = buffer.toString().encodeToByteArray()

    private val state: ListStack<LexerState> = mutableListOf(LexerState.Initial)

    private var depth: Int = 0

    override fun beginObject() = openStructure("{", LexerState.ObjectFirstKeyOrEnd)
    override fun endObject() = closeStructure("}")
    override fun beginArray() = openStructure("[", LexerState.ArrayFirstValueOrEnd)
    override fun endArray() = closeStructure("]")

    private fun openStructure(token: String, nextState: LexerState) {
        encodeValue(token)
        if (pretty) writeNewline()
        depth++
        state.push(nextState)
    }

    private fun closeStructure(token: String) {
        if (pretty) {
            writeNewline()
        }
        depth--
        writeIndent()
        buffer.append(token)
        state.pop()
    }

    private fun writeIndent() {
        if (pretty && depth > 0) {
            val indent = " ".repeat(depth * 4)
            buffer.append(indent)
        }
    }

    private fun writeNewline() = buffer.append("\n")
    private fun writeComma() {
        buffer.append(",")
        if (pretty) writeNewline()
    }

    private fun writeColon() {
        buffer.append(":")
        if (pretty) buffer.append(" ")
    }

    private fun encodeValue(value: String) {
        when (state.top()) {
            LexerState.ArrayFirstValueOrEnd -> {
                state.replaceTop(LexerState.ArrayNextValueOrEnd)
                writeIndent()
            }
            LexerState.ArrayNextValueOrEnd -> {
                writeComma()
                writeIndent()
            }
            LexerState.ObjectFieldValue -> {
                writeColon()
                state.replaceTop(LexerState.ObjectNextKeyOrEnd)
            }
            else -> {}
        }

        buffer.append(value)
    }

    override fun writeNull() = encodeValue("null")

    private fun StringBuilder.appendQuoted(value: String) {
        append("\"")
        append(value)
        append("\"")
    }

    override fun writeName(name: String) {
        if (state.top() == LexerState.ObjectNextKeyOrEnd) {
            writeComma()
        }
        writeIndent()
        buffer.appendQuoted(name.escape())
        state.replaceTop(LexerState.ObjectFieldValue)
    }

    override fun writeValue(value: String) = encodeValue("\"${value.escape()}\"")

    override fun writeValue(bool: Boolean) = when (bool) {
        true -> encodeValue("true")
        false -> encodeValue("false")
    }

    private fun writeNumber(value: Number) = encodeValue(value.toString())

    override fun writeValue(value: Byte) = writeNumber(value)
    override fun writeValue(value: Long) = writeNumber(value)
    override fun writeValue(value: Short) = writeNumber(value)
    override fun writeValue(value: Int) = writeNumber(value)
    override fun writeValue(value: Float) = writeNumber(value)
    override fun writeValue(value: Double) = writeNumber(value)

    override fun writeRawValue(value: String) = encodeValue(value)
}

internal fun String.escape(): String {
    if (!any(Char::needsEscaped)) return this

    val str = this

    return buildString(length + 1) {
        str.forEach { chr ->
            when (chr.code) {
                CP_QUOTATION -> append("\\\"")
                CP_BACKSLASH -> append("\\\\")
                CP_NEWLINE -> append("\\n")
                CP_CARRIAGE_RETURN -> append("\\r")
                CP_TAB -> append("\\t")
                CP_BACKSPACE -> append("\\b")
                CP_FORMFEED -> append("\\f")
                in 0..0x1F -> {
                    val formatted = chr.code.toString(16)
                    append("\\u")
                    repeat(4 - formatted.length) { append("0") }
                    append(formatted)
                }
                else -> append(chr)
            }
        }
    }
}

private fun Char.needsEscaped(): Boolean = when (code) {
    CP_QUOTATION,
    CP_BACKSLASH,
    in 0..0x1F -> true
    else -> false
}

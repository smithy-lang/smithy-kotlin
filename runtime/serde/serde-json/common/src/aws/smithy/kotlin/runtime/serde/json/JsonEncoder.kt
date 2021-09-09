/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.util.*

private enum class EncoderState {
    /**
     * Starting state
     */
    Initial,

    /**
     * Object has started, expecting first object key
     */
    ObjectFirstKeyOrEnd,

    /**
     * Expecting next object key or end of the object
     */
    ObjectNextKeyOrEnd,

    /**
     * Expecting a value to be written
     */
    ObjectFieldValue,

    /**
     * Expecting first array value or end of array
     */
    ArrayFirstElementOrEnd,

    /**
     * Expecting next array value or end of array
     */
    ArrayNextElementOrEnd
}

internal class JsonEncoder(private val pretty: Boolean = false) : JsonStreamWriter {
    private val buffer = StringBuilder()

    override val bytes: ByteArray
        get() = buffer.toString().encodeToByteArray()

    private val state: ListStack<EncoderState> = mutableListOf(EncoderState.Initial)

    private var depth: Int = 0

    override fun beginObject() = openStructure("{", EncoderState.ObjectFirstKeyOrEnd)
    override fun endObject() = closeStructure("}")
    override fun beginArray() = openStructure("[", EncoderState.ArrayFirstElementOrEnd)
    override fun endArray() = closeStructure("]")

    private fun openStructure(token: String, nextState: EncoderState) {
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
            EncoderState.ArrayFirstElementOrEnd -> {
                state.replaceTop(EncoderState.ArrayNextElementOrEnd)
                writeIndent()
            }
            EncoderState.ArrayNextElementOrEnd -> {
                writeComma()
                writeIndent()
            }
            EncoderState.ObjectFieldValue -> {
                writeColon()
                state.replaceTop(EncoderState.ObjectNextKeyOrEnd)
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
        if (state.top() == EncoderState.ObjectNextKeyOrEnd) {
            writeComma()
        }
        writeIndent()
        buffer.appendQuoted(name.escape())
        state.replaceTop(EncoderState.ObjectFieldValue)
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
                '"'.code -> append("\\\"")
                '\\'.code -> append("\\\\")
                '\n'.code -> append("\\n")
                '\r'.code -> append("\\r")
                '\t'.code -> append("\\t")
                0x08 -> append("\\b")
                0x0C -> append("\\f")
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
    '"'.code,
    '\\'.code,
    in 0..0x1F -> true
    else -> false
}

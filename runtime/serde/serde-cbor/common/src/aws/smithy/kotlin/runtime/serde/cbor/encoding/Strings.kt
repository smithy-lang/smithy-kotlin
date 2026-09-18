/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor.encoding

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkBufferedSink
import aws.smithy.kotlin.runtime.io.SdkBufferedSource
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.DeserializationRecursionException
import aws.smithy.kotlin.runtime.serde.cbor.nextValueIsIndefiniteBreak
import aws.smithy.kotlin.runtime.serde.cbor.writeArgument

/**
 * Write a CBOR text string (major type 3) encoded as a UTF-8 byte array.
 */
internal fun SdkBufferedSink.writeTextString(value: String) {
    val bytes = value.encodeToByteArray()
    writeArgument(Major.STRING, bytes.size.toULong())
    write(bytes)
}

private inline fun SdkBufferedSource.decodeIndefiniteStringChunks(expected: Major, decodeChunk: () -> Unit) {
    readByte() // discard indefinite-length head
    while (!nextValueIsIndefiniteBreak) {
        val major = peekMajor(this)
        if (major != expected) {
            throw DeserializationException("Unexpected major type $major in indefinite-length CBOR string, expected $expected")
        }
        decodeChunk()
    }
    decodeIndefiniteBreak(this)
}

internal fun decodeTextStringValue(buffer: SdkBufferedSource, depth: Int = 0): String {
    DeserializationRecursionException.assertDepth(depth)

    if (peekMinorByte(buffer) != Minor.INDEFINITE.value) {
        val length = decodeArgument(buffer).toLong()
        return buffer.readUtf8(length)
    }

    val sb = StringBuilder()
    buffer.decodeIndefiniteStringChunks(Major.STRING) { sb.append(decodeTextStringValue(buffer, depth + 1)) }
    return sb.toString()
}

/**
 * Write a CBOR byte string (major type 2).
 */
internal fun SdkBufferedSink.writeByteString(value: ByteArray) {
    writeArgument(Major.BYTE_STRING, value.size.toULong())
    write(value)
}

internal fun decodeByteStringValue(buffer: SdkBufferedSource, depth: Int = 0): ByteArray {
    DeserializationRecursionException.assertDepth(depth)

    if (peekMinorByte(buffer) != Minor.INDEFINITE.value) {
        val length = decodeArgument(buffer).toLong()
        return buffer.readByteArray(length)
    }

    val tempBuffer = SdkBuffer()
    buffer.decodeIndefiniteStringChunks(Major.BYTE_STRING) { tempBuffer.write(decodeByteStringValue(buffer, depth + 1)) }
    return tempBuffer.readByteArray()
}
